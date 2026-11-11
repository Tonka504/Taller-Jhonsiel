package com.taller.repuestos.service;

import com.taller.repuestos.dao.ReporteDAO;
import com.taller.repuestos.model.MovimientoCaja;
import com.taller.repuestos.model.MovimientoInventario;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.util.Formato;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Catálogo de reportes: cada reporte define sus columnas, los filtros que usa, su consulta y (si aplica)
 * un gráfico. Agregar un reporte nuevo es agregar una definición en {@link #definir()}.
 */
public class ReporteService {

    public enum TipoColumna { TEXTO, ENTERO, MONEDA, PORCENTAJE }

    public enum TipoGrafico { NINGUNO, BARRAS, LINEA, PASTEL }

    public enum Filtro { FECHAS, USUARIO, CATEGORIA, MARCA, CLIENTE, PROVEEDOR, PRODUCTO }

    /** Columna del reporte; "total" indica si se suma en la fila de totales. */
    public record Columna(String titulo, TipoColumna tipo, boolean total) {}

    /** Valores elegidos en la pantalla (null = sin filtrar). */
    public static class Filtros {
        public LocalDate desde;
        public LocalDate hasta;
        public String usuario;
        public String categoria;
        public String marca;
        public String producto;
        public Integer clienteId;
        public Integer proveedorId;
    }

    public static class Definicion {
        public final String grupo;
        public final String nombre;
        public final String descripcion;
        public final Permiso permiso;
        public final List<Columna> columnas = new ArrayList<>();
        public final Set<Filtro> filtros = EnumSet.noneOf(Filtro.class);
        public TipoGrafico grafico = TipoGrafico.NINGUNO;
        public int colEtiqueta;
        public int colValor;
        Function<Filtros, Consulta> armar;
        Consumer<Object[]> post; // ajusta cada fila (por ejemplo, número de documento o etiqueta legible)

        Definicion(String grupo, String nombre, String descripcion, Permiso permiso) {
            this.grupo = grupo;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.permiso = permiso;
        }

        Definicion col(String titulo, TipoColumna tipo, boolean total) {
            columnas.add(new Columna(titulo, tipo, total));
            return this;
        }

        Definicion filtros(Filtro... f) {
            filtros.addAll(List.of(f));
            return this;
        }

        Definicion grafico(TipoGrafico g, int etiqueta, int valor) {
            grafico = g;
            colEtiqueta = etiqueta;
            colValor = valor;
            return this;
        }

        @Override
        public String toString() { return nombre; }
    }

    public static class Resultado {
        public final Definicion definicion;
        public final List<Object[]> filas;
        /** Total por columna (null si la columna no se suma). */
        public final Double[] totales;

        Resultado(Definicion d, List<Object[]> filas) {
            this.definicion = d;
            this.filas = filas;
            this.totales = new Double[d.columnas.size()];
            for (int i = 0; i < d.columnas.size(); i++) {
                if (!d.columnas.get(i).total()) continue;
                double t = 0;
                for (Object[] f : filas) if (f[i] instanceof Number n) t += n.doubleValue();
                totales[i] = Math.round(t * 100.0) / 100.0;
            }
        }
    }

    /** Consulta con sus parámetros, armada paso a paso. */
    static class Consulta {
        final StringBuilder sql = new StringBuilder();
        final List<Object> params = new ArrayList<>();

        Consulta add(String texto, Object... ps) {
            sql.append(texto);
            params.addAll(List.of(ps));
            return this;
        }
    }

    private final ReporteDAO dao = new ReporteDAO();
    private final PermisoService permisos = PermisoService.get();
    private final List<Definicion> definiciones = new ArrayList<>();

    public ReporteService() {
        definir();
    }

    /** Reportes que el usuario de la sesión puede ver. */
    public List<Definicion> disponibles() {
        List<Definicion> lista = new ArrayList<>();
        for (Definicion d : definiciones) if (permisos.tiene(d.permiso)) lista.add(d);
        return lista;
    }

    public Resultado ejecutar(Definicion d, Filtros f) {
        if (!permisos.tiene(d.permiso)) return new Resultado(d, List.of());
        Consulta c = d.armar.apply(f);
        List<Object[]> filas = dao.filas(c.sql.toString(), c.params);
        if (d.post != null) for (Object[] fila : filas) d.post.accept(fila);
        // Tipos uniformes por columna (SQLite puede devolver Integer o Long en la misma columna)
        for (Object[] fila : filas) {
            for (int i = 0; i < fila.length && i < d.columnas.size(); i++) {
                Object v = fila[i];
                if (v == null) continue;
                switch (d.columnas.get(i).tipo()) {
                    case ENTERO -> fila[i] = v instanceof Number n ? (Object) n.longValue() : v;
                    case MONEDA, PORCENTAJE -> fila[i] = v instanceof Number n ? (Object) n.doubleValue() : v;
                    default -> fila[i] = String.valueOf(v);
                }
            }
        }
        return new Resultado(d, filas);
    }

    // --- Listas para los filtros ---
    public List<String> usuariosConVentas() {
        List<String> l = new ArrayList<>();
        for (Object[] f : dao.filas("SELECT DISTINCT vendedor FROM ventas WHERE vendedor IS NOT NULL ORDER BY vendedor")) l.add(String.valueOf(f[0]));
        return l;
    }

    // ------------------------------------------------------------------
    // FILTROS COMUNES
    // ------------------------------------------------------------------

    private static void fechas(Consulta q, Filtros f, String columna) {
        if (f.desde != null) q.add(" AND " + columna + " >= ?", f.desde.toString());
        if (f.hasta != null) q.add(" AND " + columna + " < date(?, '+1 day')", f.hasta.toString());
    }

    /** Ventas emitidas (no anuladas) dentro del período, del vendedor y cliente elegidos. */
    private static void ventas(Consulta q, Filtros f, String v) {
        q.add(" AND " + v + ".estado = 'EMITIDA'");
        fechas(q, f, v + ".fecha");
        if (f.usuario != null) q.add(" AND " + v + ".vendedor = ?", f.usuario);
        if (f.clienteId != null) q.add(" AND " + v + ".cliente_id = ?", f.clienteId);
    }

    private static void producto(Consulta q, Filtros f, String r) {
        if (f.categoria != null) q.add(" AND " + r + ".categoria = ? COLLATE NOCASE", f.categoria);
        if (f.marca != null) q.add(" AND " + r + ".marca = ? COLLATE NOCASE", f.marca);
        if (f.proveedorId != null) q.add(" AND " + r + ".proveedor = (SELECT nombre FROM proveedores WHERE id = ?) COLLATE NOCASE", f.proveedorId);
        if (f.producto != null && !f.producto.isBlank()) {
            String like = "%" + f.producto.trim() + "%";
            q.add(" AND (" + r + ".codigo LIKE ? OR " + r + ".descripcion LIKE ?)", like, like);
        }
    }

    // ------------------------------------------------------------------
    // DEFINICIONES
    // ------------------------------------------------------------------

    private Definicion nuevo(String grupo, String nombre, String descripcion, Permiso permiso) {
        Definicion d = new Definicion(grupo, nombre, descripcion, permiso);
        definiciones.add(d);
        return d;
    }

    private void definir() {
        // ======================= VENTAS =======================
        ventasPorPeriodo("Ventas por día", "date(v.fecha)", "Día");
        ventasPorPeriodo("Ventas por semana", "strftime('%Y', v.fecha) || ' - semana ' || strftime('%W', v.fecha)", "Semana");
        ventasPorPeriodo("Ventas por mes", "strftime('%Y-%m', v.fecha)", "Mes");

        Definicion d = nuevo("Ventas", "Ventas por usuario", "Facturas y total vendido por cada vendedor.", Permiso.REPORTES_VER)
                .col("Vendedor", TipoColumna.TEXTO, false).col("Facturas", TipoColumna.ENTERO, true)
                .col("Total", TipoColumna.MONEDA, true).col("Ticket promedio", TipoColumna.MONEDA, false)
                .filtros(Filtro.FECHAS, Filtro.CLIENTE).grafico(TipoGrafico.BARRAS, 0, 2);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT COALESCE(v.vendedor, '—'), COUNT(*), ROUND(SUM(v.total), 2), ROUND(AVG(v.total), 2) FROM ventas v WHERE 1 = 1");
            ventas(q, f, "v");
            return q.add(" GROUP BY v.vendedor ORDER BY 3 DESC");
        };

        d = nuevo("Ventas", "Ventas por producto", "Unidades e importe vendido por producto (importe de línea, antes del descuento general).", Permiso.REPORTES_VER)
                .col("Código", TipoColumna.TEXTO, false).col("Producto", TipoColumna.TEXTO, false)
                .col("Unidades", TipoColumna.ENTERO, true).col("Importe", TipoColumna.MONEDA, true)
                .filtros(Filtro.FECHAS, Filtro.USUARIO, Filtro.CLIENTE, Filtro.CATEGORIA, Filtro.MARCA, Filtro.PRODUCTO)
                .grafico(TipoGrafico.BARRAS, 1, 3);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT COALESCE(r.codigo, 'SERV'), d.descripcion, SUM(d.cantidad), ROUND(SUM(d.subtotal), 2) "
                    + "FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id LEFT JOIN repuestos r ON r.id = d.repuesto_id WHERE 1 = 1");
            ventas(q, f, "v");
            producto(q, f, "r");
            return q.add(" GROUP BY d.repuesto_id, CASE WHEN d.repuesto_id = 0 THEN d.descripcion END ORDER BY 4 DESC");
        };

        ventasPorAtributo("Ventas por categoría", "categoria", "Categoría", TipoGrafico.PASTEL);
        ventasPorAtributo("Ventas por marca", "marca", "Marca", TipoGrafico.BARRAS);

        d = nuevo("Ventas", "Ventas por cliente", "Facturas, total y última compra de cada cliente.", Permiso.REPORTES_VER)
                .col("Cliente", TipoColumna.TEXTO, false).col("Facturas", TipoColumna.ENTERO, true)
                .col("Total", TipoColumna.MONEDA, true).col("Última compra", TipoColumna.TEXTO, false)
                .filtros(Filtro.FECHAS, Filtro.USUARIO).grafico(TipoGrafico.BARRAS, 0, 2);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT COALESCE(c.nombre, 'Consumidor final'), COUNT(*), ROUND(SUM(v.total), 2), MAX(v.fecha) "
                    + "FROM ventas v LEFT JOIN clientes c ON c.id = v.cliente_id WHERE 1 = 1");
            ventas(q, f, "v");
            return q.add(" GROUP BY v.cliente_id ORDER BY 3 DESC");
        };

        d = nuevo("Ventas", "Ventas por forma de pago", "Total facturado por efectivo, tarjeta, transferencia y crédito.", Permiso.REPORTES_VER)
                .col("Forma de pago", TipoColumna.TEXTO, false).col("Facturas", TipoColumna.ENTERO, true)
                .col("Total", TipoColumna.MONEDA, true).filtros(Filtro.FECHAS, Filtro.USUARIO, Filtro.CLIENTE)
                .grafico(TipoGrafico.PASTEL, 0, 2);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT v.metodo_pago, COUNT(*), ROUND(SUM(v.total), 2) FROM ventas v WHERE 1 = 1");
            ventas(q, f, "v");
            return q.add(" GROUP BY v.metodo_pago ORDER BY 3 DESC");
        };

        d = nuevo("Ventas", "Devoluciones de clientes", "Notas de crédito emitidas en el período.", Permiso.REPORTES_VER)
                .col("Fecha", TipoColumna.TEXTO, false).col("Devolución", TipoColumna.TEXTO, false)
                .col("Factura", TipoColumna.TEXTO, false).col("Motivo", TipoColumna.TEXTO, false)
                .col("Reembolso", TipoColumna.TEXTO, false).col("Usuario", TipoColumna.TEXTO, false)
                .col("Monto", TipoColumna.MONEDA, true).filtros(Filtro.FECHAS);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT dv.fecha, dv.id, dv.venta_id, dv.motivo, dv.forma_reembolso, dv.usuario, dv.monto_total "
                    + "FROM devoluciones dv WHERE 1 = 1");
            fechas(q, f, "dv.fecha");
            return q.add(" ORDER BY dv.fecha DESC");
        };
        d.post = fila -> {
            fila[1] = Formato.numeroDevolucion(((Number) fila[1]).intValue());
            fila[2] = Formato.numeroFactura(((Number) fila[2]).intValue());
        };

        // ======================= INVENTARIO =======================
        d = nuevo("Inventario", "Existencias", "Existencia actual y valor a costo de cada producto.", Permiso.REPORTES_VER)
                .col("Código", TipoColumna.TEXTO, false).col("Producto", TipoColumna.TEXTO, false)
                .col("Categoría", TipoColumna.TEXTO, false).col("Marca", TipoColumna.TEXTO, false)
                .col("Ubicación", TipoColumna.TEXTO, false).col("Stock", TipoColumna.ENTERO, true)
                .col("Mínimo", TipoColumna.ENTERO, false).col("Costo", TipoColumna.MONEDA, false)
                .col("Precio", TipoColumna.MONEDA, false).col("Valor a costo", TipoColumna.MONEDA, true)
                .filtros(Filtro.CATEGORIA, Filtro.MARCA, Filtro.PROVEEDOR, Filtro.PRODUCTO);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT r.codigo, r.descripcion, r.categoria, r.marca, r.ubicacion, r.stock, r.stock_minimo, "
                    + "r.costo, r.precio, ROUND(r.stock * r.costo, 2) FROM repuestos r WHERE 1 = 1");
            producto(q, f, "r");
            return q.add(" ORDER BY r.descripcion COLLATE NOCASE");
        };

        d = nuevo("Inventario", "Stock bajo", "Productos en o por debajo del mínimo, con la cantidad sugerida a pedir.", Permiso.REPORTES_VER)
                .col("Código", TipoColumna.TEXTO, false).col("Producto", TipoColumna.TEXTO, false)
                .col("Proveedor", TipoColumna.TEXTO, false).col("Stock", TipoColumna.ENTERO, false)
                .col("Mínimo", TipoColumna.ENTERO, false).col("Sugerido pedir", TipoColumna.ENTERO, true)
                .col("Costo estimado", TipoColumna.MONEDA, true)
                .filtros(Filtro.CATEGORIA, Filtro.MARCA, Filtro.PROVEEDOR, Filtro.PRODUCTO);
        d.armar = f -> {
            String pedir = "MAX(1, (CASE WHEN r.stock_maximo > 0 THEN r.stock_maximo ELSE r.stock_minimo * 2 END) - r.stock)";
            Consulta q = new Consulta().add("SELECT r.codigo, r.descripcion, r.proveedor, r.stock, r.stock_minimo, " + pedir + ", "
                    + "ROUND(" + pedir + " * r.costo, 2) FROM repuestos r WHERE r.stock <= r.stock_minimo AND r.stock > 0");
            producto(q, f, "r");
            return q.add(" ORDER BY (r.stock_minimo - r.stock) DESC");
        };

        d = nuevo("Inventario", "Productos agotados", "Productos sin existencia y la fecha de su última salida.", Permiso.REPORTES_VER)
                .col("Código", TipoColumna.TEXTO, false).col("Producto", TipoColumna.TEXTO, false)
                .col("Categoría", TipoColumna.TEXTO, false).col("Proveedor", TipoColumna.TEXTO, false)
                .col("Mínimo", TipoColumna.ENTERO, false).col("Última salida", TipoColumna.TEXTO, false)
                .filtros(Filtro.CATEGORIA, Filtro.MARCA, Filtro.PROVEEDOR, Filtro.PRODUCTO);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT r.codigo, r.descripcion, r.categoria, r.proveedor, r.stock_minimo, "
                    + "(SELECT MAX(m.fecha) FROM movimientos_inventario m WHERE m.repuesto_id = r.id AND m.cantidad < 0) "
                    + "FROM repuestos r WHERE r.stock <= 0");
            producto(q, f, "r");
            return q.add(" ORDER BY r.descripcion COLLATE NOCASE");
        };

        d = nuevo("Inventario", "Productos más vendidos", "Los 30 productos con más unidades vendidas en el período.", Permiso.REPORTES_VER)
                .col("Código", TipoColumna.TEXTO, false).col("Producto", TipoColumna.TEXTO, false)
                .col("Unidades", TipoColumna.ENTERO, true).col("Importe", TipoColumna.MONEDA, true)
                .col("Stock actual", TipoColumna.ENTERO, false)
                .filtros(Filtro.FECHAS, Filtro.CATEGORIA, Filtro.MARCA, Filtro.PRODUCTO).grafico(TipoGrafico.BARRAS, 1, 2);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT r.codigo, r.descripcion, SUM(d.cantidad), ROUND(SUM(d.subtotal), 2), r.stock "
                    + "FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id JOIN repuestos r ON r.id = d.repuesto_id WHERE 1 = 1");
            ventas(q, f, "v");
            producto(q, f, "r");
            return q.add(" GROUP BY d.repuesto_id ORDER BY 3 DESC LIMIT 30");
        };

        d = nuevo("Inventario", "Productos sin movimiento", "Productos con existencia que no se vendieron ni usaron en el taller durante el período.", Permiso.REPORTES_VER)
                .col("Código", TipoColumna.TEXTO, false).col("Producto", TipoColumna.TEXTO, false)
                .col("Stock", TipoColumna.ENTERO, true).col("Valor a costo", TipoColumna.MONEDA, true)
                .col("Última salida", TipoColumna.TEXTO, false)
                .filtros(Filtro.FECHAS, Filtro.CATEGORIA, Filtro.MARCA, Filtro.PROVEEDOR, Filtro.PRODUCTO);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT r.codigo, r.descripcion, r.stock, ROUND(r.stock * r.costo, 2), "
                    + "(SELECT MAX(m.fecha) FROM movimientos_inventario m WHERE m.repuesto_id = r.id AND m.tipo IN ('VENTA', 'ORDEN_TRABAJO')) "
                    + "FROM repuestos r WHERE r.stock > 0 AND NOT EXISTS (SELECT 1 FROM movimientos_inventario m "
                    + "WHERE m.repuesto_id = r.id AND m.tipo IN ('VENTA', 'ORDEN_TRABAJO')");
            fechas(q, f, "m.fecha");
            q.add(")");
            producto(q, f, "r");
            return q.add(" ORDER BY 4 DESC");
        };

        d = nuevo("Inventario", "Valor del inventario", "Unidades y valor del inventario por categoría, a costo y a precio de venta.", Permiso.REPORTES_VER)
                .col("Categoría", TipoColumna.TEXTO, false).col("Productos", TipoColumna.ENTERO, true)
                .col("Unidades", TipoColumna.ENTERO, true).col("Valor a costo", TipoColumna.MONEDA, true)
                .col("Valor a precio de venta", TipoColumna.MONEDA, true)
                .filtros(Filtro.MARCA, Filtro.PROVEEDOR).grafico(TipoGrafico.PASTEL, 0, 3);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT COALESCE(NULLIF(r.categoria, ''), 'Sin categoría'), COUNT(*), SUM(r.stock), "
                    + "ROUND(SUM(r.stock * r.costo), 2), ROUND(SUM(r.stock * r.precio), 2) FROM repuestos r WHERE r.stock > 0");
            producto(q, f, "r");
            return q.add(" GROUP BY 1 ORDER BY 4 DESC");
        };

        d = nuevo("Inventario", "Movimientos de inventario", "Resumen de entradas y salidas por tipo de movimiento (el detalle está en Kardex).", Permiso.REPORTES_VER)
                .col("Tipo", TipoColumna.TEXTO, false).col("Movimientos", TipoColumna.ENTERO, true)
                .col("Entradas", TipoColumna.ENTERO, true).col("Salidas", TipoColumna.ENTERO, true)
                .filtros(Filtro.FECHAS, Filtro.PRODUCTO, Filtro.CATEGORIA, Filtro.MARCA).grafico(TipoGrafico.BARRAS, 0, 1);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT m.tipo, COUNT(*), SUM(CASE WHEN m.cantidad > 0 THEN m.cantidad ELSE 0 END), "
                    + "SUM(CASE WHEN m.cantidad < 0 THEN -m.cantidad ELSE 0 END) FROM movimientos_inventario m "
                    + "JOIN repuestos r ON r.id = m.repuesto_id WHERE 1 = 1");
            fechas(q, f, "m.fecha");
            producto(q, f, "r");
            return q.add(" GROUP BY m.tipo ORDER BY 2 DESC");
        };
        d.post = fila -> fila[0] = MovimientoInventario.Tipo.etiquetaDe(String.valueOf(fila[0]));

        // ======================= FINANZAS =======================
        gananciaPorPeriodo("Ganancia estimada por día", "date(v.fecha)", "Día");
        gananciaPorPeriodo("Ganancia estimada por mes", "strftime('%Y-%m', v.fecha)", "Mes");

        d = nuevo("Finanzas", "Rentabilidad por producto", "Importe, costo y ganancia de cada producto vendido.", Permiso.REPORTES_FINANZAS)
                .col("Código", TipoColumna.TEXTO, false).col("Producto", TipoColumna.TEXTO, false)
                .col("Unidades", TipoColumna.ENTERO, true).col("Importe", TipoColumna.MONEDA, true)
                .col("Costo", TipoColumna.MONEDA, true).col("Ganancia", TipoColumna.MONEDA, true)
                .col("Margen", TipoColumna.PORCENTAJE, false)
                .filtros(Filtro.FECHAS, Filtro.USUARIO, Filtro.CATEGORIA, Filtro.MARCA, Filtro.PRODUCTO);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT codigo, descripcion, unidades, importe, costo, ROUND(importe - costo, 2), "
                    + "CASE WHEN importe > 0 THEN ROUND((importe - costo) * 100.0 / importe, 1) END FROM ("
                    + "SELECT COALESCE(r.codigo, 'SERV') AS codigo, d.descripcion AS descripcion, SUM(d.cantidad) AS unidades, "
                    + "ROUND(SUM(d.subtotal), 2) AS importe, ROUND(SUM(d.costo_unitario * d.cantidad), 2) AS costo "
                    + "FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id LEFT JOIN repuestos r ON r.id = d.repuesto_id WHERE 1 = 1");
            ventas(q, f, "v");
            producto(q, f, "r");
            return q.add(" GROUP BY d.repuesto_id, CASE WHEN d.repuesto_id = 0 THEN d.descripcion END) ORDER BY 6 DESC");
        };

        d = nuevo("Finanzas", "Ingresos por forma de pago", "Dinero cobrado (ventas de contado y abonos a créditos) por forma de pago.", Permiso.REPORTES_FINANZAS)
                .col("Forma de pago", TipoColumna.TEXTO, false).col("Cobro de ventas", TipoColumna.MONEDA, true)
                .col("Abonos a créditos", TipoColumna.MONEDA, true).col("Total cobrado", TipoColumna.MONEDA, true)
                .filtros(Filtro.FECHAS, Filtro.USUARIO).grafico(TipoGrafico.PASTEL, 0, 3);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT p.metodo, ROUND(SUM(CASE WHEN p.tipo = 'PAGO' THEN p.monto ELSE 0 END), 2), "
                    + "ROUND(SUM(CASE WHEN p.tipo = 'ABONO' THEN p.monto ELSE 0 END), 2), ROUND(SUM(p.monto), 2) "
                    + "FROM pagos p JOIN ventas v ON v.id = p.venta_id WHERE v.estado = 'EMITIDA'");
            fechas(q, f, "p.fecha");
            if (f.usuario != null) q.add(" AND p.usuario = ?", f.usuario);
            return q.add(" GROUP BY p.metodo ORDER BY 4 DESC");
        };

        d = nuevo("Finanzas", "Gastos y salidas de caja", "Gastos, retiros, pagos a proveedores y devoluciones en efectivo.", Permiso.REPORTES_FINANZAS)
                .col("Fecha", TipoColumna.TEXTO, false).col("Tipo", TipoColumna.TEXTO, false)
                .col("Motivo", TipoColumna.TEXTO, false).col("Documento", TipoColumna.TEXTO, false)
                .col("Usuario", TipoColumna.TEXTO, false).col("Monto", TipoColumna.MONEDA, true)
                .filtros(Filtro.FECHAS, Filtro.USUARIO);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT mc.fecha, mc.tipo, mc.motivo, mc.documento, mc.usuario, mc.monto FROM movimientos_caja mc "
                    + "WHERE mc.tipo IN ('GASTO', 'RETIRO', 'PAGO_PROVEEDOR', 'DEVOLUCION')");
            fechas(q, f, "mc.fecha");
            if (f.usuario != null) q.add(" AND mc.usuario = ?", f.usuario);
            return q.add(" ORDER BY mc.fecha DESC");
        };
        d.post = fila -> {
            try {
                fila[1] = MovimientoCaja.Tipo.valueOf(String.valueOf(fila[1])).getEtiqueta();
            } catch (IllegalArgumentException ignorada) {
                // se deja el nombre tal cual
            }
        };

        d = nuevo("Finanzas", "Flujo de caja por turno", "Resumen de cada turno de caja: fondo, ventas por forma de pago, salidas y cuadre.", Permiso.REPORTES_FINANZAS)
                .col("Caja", TipoColumna.TEXTO, false).col("Apertura", TipoColumna.TEXTO, false)
                .col("Cierre", TipoColumna.TEXTO, false).col("Usuario", TipoColumna.TEXTO, false)
                .col("Fondo", TipoColumna.MONEDA, false).col("Efectivo", TipoColumna.MONEDA, true)
                .col("Tarjeta", TipoColumna.MONEDA, true).col("Transferencia", TipoColumna.MONEDA, true)
                .col("Entradas", TipoColumna.MONEDA, true).col("Salidas", TipoColumna.MONEDA, true)
                .col("Esperado", TipoColumna.MONEDA, false).col("Contado", TipoColumna.MONEDA, false)
                .col("Diferencia", TipoColumna.MONEDA, true).filtros(Filtro.FECHAS);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT 'N° ' || c.id, c.fecha_apertura, COALESCE(c.fecha_cierre, 'Abierta'), c.usuario_apertura, "
                    + "c.monto_inicial, c.total_efectivo, c.total_tarjeta, c.total_transferencia, c.entradas, "
                    + "COALESCE(c.retiros, 0) + COALESCE(c.gastos, 0) + COALESCE(c.devoluciones, 0), c.efectivo_esperado, "
                    + "c.efectivo_contado, c.diferencia FROM cajas c WHERE 1 = 1");
            fechas(q, f, "c.fecha_apertura");
            return q.add(" ORDER BY c.id DESC");
        };

        // ======================= COMPRAS =======================
        d = nuevo("Compras", "Compras por proveedor", "Total comprado (recibido), pagado, devuelto y saldo por proveedor.", Permiso.COMPRAS_VER)
                .col("Proveedor", TipoColumna.TEXTO, false).col("Compras", TipoColumna.ENTERO, true)
                .col("Total", TipoColumna.MONEDA, true).col("Pagado", TipoColumna.MONEDA, true)
                .col("Devuelto", TipoColumna.MONEDA, true).col("Saldo", TipoColumna.MONEDA, true)
                .filtros(Filtro.FECHAS, Filtro.PROVEEDOR).grafico(TipoGrafico.BARRAS, 0, 2);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT proveedor, compras, total, pagado, devuelto, ROUND(total - pagado - devuelto, 2) FROM ("
                    + "SELECT p.nombre AS proveedor, COUNT(*) AS compras, ROUND(SUM(c.total), 2) AS total, "
                    + "ROUND(SUM(COALESCE((SELECT SUM(pp.monto) FROM pagos_proveedor pp WHERE pp.compra_id = c.id), 0)), 2) AS pagado, "
                    + "ROUND(SUM(COALESCE((SELECT SUM(dp.total) FROM devoluciones_proveedor dp WHERE dp.compra_id = c.id), 0)), 2) AS devuelto "
                    + "FROM compras c JOIN proveedores p ON p.id = c.proveedor_id WHERE c.estado IN ('PARCIAL', 'RECIBIDA')");
            fechas(q, f, "c.fecha");
            if (f.proveedorId != null) q.add(" AND c.proveedor_id = ?", f.proveedorId);
            return q.add(" GROUP BY c.proveedor_id) ORDER BY 3 DESC");
        };

        d = nuevo("Compras", "Compras por período", "Órdenes y total comprado por mes.", Permiso.COMPRAS_VER)
                .col("Mes", TipoColumna.TEXTO, false).col("Compras", TipoColumna.ENTERO, true)
                .col("Total", TipoColumna.MONEDA, true).filtros(Filtro.FECHAS, Filtro.PROVEEDOR)
                .grafico(TipoGrafico.LINEA, 0, 2);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT strftime('%Y-%m', c.fecha), COUNT(*), ROUND(SUM(c.total), 2) FROM compras c "
                    + "WHERE c.estado <> 'ANULADA'");
            fechas(q, f, "c.fecha");
            if (f.proveedorId != null) q.add(" AND c.proveedor_id = ?", f.proveedorId);
            return q.add(" GROUP BY 1 ORDER BY 1");
        };

        d = nuevo("Compras", "Productos comprados", "Unidades recibidas, costo promedio e importe por producto.", Permiso.COMPRAS_VER)
                .col("Código", TipoColumna.TEXTO, false).col("Producto", TipoColumna.TEXTO, false)
                .col("Unidades recibidas", TipoColumna.ENTERO, true).col("Costo promedio", TipoColumna.MONEDA, false)
                .col("Importe", TipoColumna.MONEDA, true)
                .filtros(Filtro.FECHAS, Filtro.PROVEEDOR, Filtro.CATEGORIA, Filtro.MARCA, Filtro.PRODUCTO);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT r.codigo, r.descripcion, SUM(dc.cantidad_recibida), "
                    + "ROUND(SUM(dc.subtotal) / NULLIF(SUM(dc.cantidad), 0), 2), "
                    + "ROUND(SUM(dc.subtotal * dc.cantidad_recibida * 1.0 / dc.cantidad), 2) "
                    + "FROM detalle_compras dc JOIN compras c ON c.id = dc.compra_id JOIN repuestos r ON r.id = dc.repuesto_id "
                    + "WHERE c.estado <> 'ANULADA' AND dc.cantidad_recibida > 0");
            fechas(q, f, "c.fecha");
            if (f.proveedorId != null) q.add(" AND c.proveedor_id = ?", f.proveedorId);
            Filtros sinProveedor = copiaSinProveedor(f);
            producto(q, sinProveedor, "r");
            return q.add(" GROUP BY dc.repuesto_id ORDER BY 5 DESC");
        };

        d = nuevo("Compras", "Historial de costos", "Cambios de costo de los productos (al recibir compras).", Permiso.COMPRAS_VER)
                .col("Fecha", TipoColumna.TEXTO, false).col("Código", TipoColumna.TEXTO, false)
                .col("Producto", TipoColumna.TEXTO, false).col("Costo anterior", TipoColumna.MONEDA, false)
                .col("Costo nuevo", TipoColumna.MONEDA, false).col("Variación", TipoColumna.PORCENTAJE, false)
                .col("Origen", TipoColumna.TEXTO, false).col("Usuario", TipoColumna.TEXTO, false)
                .filtros(Filtro.FECHAS, Filtro.PRODUCTO, Filtro.CATEGORIA, Filtro.MARCA);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT h.fecha, r.codigo, r.descripcion, h.costo_anterior, h.costo_nuevo, "
                    + "CASE WHEN h.costo_anterior > 0 THEN ROUND((h.costo_nuevo - h.costo_anterior) * 100.0 / h.costo_anterior, 1) END, "
                    + "h.origen, h.usuario FROM historial_costos h JOIN repuestos r ON r.id = h.repuesto_id WHERE 1 = 1");
            fechas(q, f, "h.fecha");
            producto(q, f, "r");
            return q.add(" ORDER BY h.fecha DESC");
        };

        // ======================= TALLER =======================
        d = nuevo("Taller", "Órdenes por mecánico", "Órdenes recibidas, entregadas y monto de mano de obra y repuestos por mecánico.", Permiso.ORDENES_VER)
                .col("Mecánico", TipoColumna.TEXTO, false).col("Órdenes", TipoColumna.ENTERO, true)
                .col("En taller", TipoColumna.ENTERO, true).col("Entregadas", TipoColumna.ENTERO, true)
                .col("Canceladas", TipoColumna.ENTERO, true).col("Mano de obra", TipoColumna.MONEDA, true)
                .col("Repuestos", TipoColumna.MONEDA, true).filtros(Filtro.FECHAS).grafico(TipoGrafico.BARRAS, 0, 5);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT COALESCE(o.mecanico, 'Sin asignar'), COUNT(*), "
                    + "SUM(CASE WHEN o.estado IN ('RECIBIDO', 'DIAGNOSTICO', 'EN_REPARACION', 'LISTO') THEN 1 ELSE 0 END), "
                    + "SUM(CASE WHEN o.estado = 'ENTREGADO' THEN 1 ELSE 0 END), SUM(CASE WHEN o.estado = 'CANCELADO' THEN 1 ELSE 0 END), "
                    + "ROUND(SUM(CASE WHEN o.estado <> 'CANCELADO' THEN COALESCE((SELECT SUM(s.precio) FROM orden_servicios s WHERE s.orden_id = o.id), 0) ELSE 0 END), 2), "
                    + "ROUND(SUM(CASE WHEN o.estado <> 'CANCELADO' THEN COALESCE((SELECT SUM(r.cantidad * r.precio_unitario) FROM orden_repuestos r WHERE r.orden_id = o.id), 0) ELSE 0 END), 2) "
                    + "FROM ordenes_trabajo o WHERE 1 = 1");
            fechas(q, f, "o.fecha_ingreso");
            return q.add(" GROUP BY 1 ORDER BY 2 DESC");
        };

        d = nuevo("Taller", "Servicios más realizados", "Servicios y mano de obra más frecuentes en el período.", Permiso.ORDENES_VER)
                .col("Servicio", TipoColumna.TEXTO, false).col("Veces", TipoColumna.ENTERO, true)
                .col("Total", TipoColumna.MONEDA, true).col("Precio promedio", TipoColumna.MONEDA, false)
                .filtros(Filtro.FECHAS).grafico(TipoGrafico.BARRAS, 0, 1);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT s.descripcion, COUNT(*), ROUND(SUM(s.precio), 2), ROUND(AVG(s.precio), 2) "
                    + "FROM orden_servicios s JOIN ordenes_trabajo o ON o.id = s.orden_id WHERE o.estado <> 'CANCELADO'");
            fechas(q, f, "o.fecha_ingreso");
            return q.add(" GROUP BY s.descripcion COLLATE NOCASE ORDER BY 2 DESC LIMIT 50");
        };
    }

    private void ventasPorPeriodo(String nombre, String expresion, String etiqueta) {
        Definicion d = nuevo("Ventas", nombre, "Facturas emitidas (sin anuladas), impuesto, total y ticket promedio.", Permiso.REPORTES_VER)
                .col(etiqueta, TipoColumna.TEXTO, false).col("Facturas", TipoColumna.ENTERO, true)
                .col("Impuesto", TipoColumna.MONEDA, true).col("Total", TipoColumna.MONEDA, true)
                .col("Ticket promedio", TipoColumna.MONEDA, false)
                .filtros(Filtro.FECHAS, Filtro.USUARIO, Filtro.CLIENTE).grafico(TipoGrafico.LINEA, 0, 3);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT " + expresion + ", COUNT(*), ROUND(SUM(v.impuesto), 2), ROUND(SUM(v.total), 2), "
                    + "ROUND(AVG(v.total), 2) FROM ventas v WHERE 1 = 1");
            ventas(q, f, "v");
            return q.add(" GROUP BY 1 ORDER BY 1");
        };
    }

    private void ventasPorAtributo(String nombre, String columna, String etiqueta, TipoGrafico grafico) {
        Definicion d = nuevo("Ventas", nombre, "Unidades e importe vendido agrupado por " + etiqueta.toLowerCase() + " del producto.", Permiso.REPORTES_VER)
                .col(etiqueta, TipoColumna.TEXTO, false).col("Unidades", TipoColumna.ENTERO, true)
                .col("Importe", TipoColumna.MONEDA, true)
                .filtros(Filtro.FECHAS, Filtro.USUARIO, Filtro.CLIENTE).grafico(grafico, 0, 2);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT COALESCE(NULLIF(r." + columna + ", ''), CASE WHEN d.repuesto_id = 0 THEN 'Servicios' "
                    + "ELSE 'Sin " + etiqueta.toLowerCase() + "' END), SUM(d.cantidad), ROUND(SUM(d.subtotal), 2) "
                    + "FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id LEFT JOIN repuestos r ON r.id = d.repuesto_id WHERE 1 = 1");
            ventas(q, f, "v");
            return q.add(" GROUP BY 1 ORDER BY 3 DESC");
        };
    }

    private void gananciaPorPeriodo(String nombre, String expresion, String etiqueta) {
        Definicion d = nuevo("Finanzas", nombre, "Ventas sin impuesto menos el costo de lo vendido (estimado con el costo al momento de la venta).",
                Permiso.REPORTES_FINANZAS)
                .col(etiqueta, TipoColumna.TEXTO, false).col("Ventas sin impuesto", TipoColumna.MONEDA, true)
                .col("Costo de lo vendido", TipoColumna.MONEDA, true).col("Ganancia", TipoColumna.MONEDA, true)
                .col("Margen", TipoColumna.PORCENTAJE, false)
                .filtros(Filtro.FECHAS, Filtro.USUARIO, Filtro.CLIENTE).grafico(TipoGrafico.LINEA, 0, 3);
        d.armar = f -> {
            Consulta q = new Consulta().add("SELECT periodo, neto, costo, ROUND(neto - costo, 2), "
                    + "CASE WHEN neto > 0 THEN ROUND((neto - costo) * 100.0 / neto, 1) END FROM ("
                    + "SELECT " + expresion + " AS periodo, ROUND(SUM(v.total - v.impuesto), 2) AS neto, "
                    + "ROUND(SUM(COALESCE((SELECT SUM(d.costo_unitario * d.cantidad) FROM venta_detalle d WHERE d.venta_id = v.id), 0)), 2) AS costo "
                    + "FROM ventas v WHERE 1 = 1");
            ventas(q, f, "v");
            return q.add(" GROUP BY 1) ORDER BY 1");
        };
    }

    private static Filtros copiaSinProveedor(Filtros f) {
        Filtros c = new Filtros();
        c.categoria = f.categoria;
        c.marca = f.marca;
        c.producto = f.producto;
        return c;
    }
}
