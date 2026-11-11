package com.taller.repuestos.service;

import com.taller.repuestos.dao.ReporteDAO;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Datos resumidos para el Dashboard: indicadores, series para gráficos y alertas. */
public class DashboardService {

    /** Indicadores del negocio en este momento. */
    public static class Resumen {
        public int ventasHoy;
        public double totalHoy;
        public int ventasMes;
        public double totalMes;
        public double gananciaMes;
        public int unidadesMes;
        public int productosDisponibles;
        public int productosTotal;
        public int stockBajo;
        public int agotados;
        public int clientesActivos;
        public double valorInventario;
        public int creditosPendientes;
        public double saldoCreditos;
        public int creditosVencidos;
        public double saldoVencido;
        public int comprasPendientes;
        public double saldoProveedores;
        public int ordenesAbiertas;
        public int ordenesListas;
    }

    /** Punto de una serie: etiqueta y valor. */
    public record Punto(String etiqueta, double valor) {}

    private static final String SALDO_CREDITO = """
        SELECT v.id, v.fecha_limite,
            v.total
              - COALESCE((SELECT SUM(p.monto) FROM pagos p WHERE p.venta_id = v.id AND p.tipo = 'ABONO'), 0)
              - COALESCE((SELECT SUM(d.monto_saldo) FROM devoluciones d WHERE d.venta_id = v.id), 0) AS saldo
        FROM ventas v WHERE v.metodo_pago = 'Crédito' AND v.estado = 'EMITIDA'
    """;

    private final ReporteDAO dao = new ReporteDAO();

    public Resumen resumen() {
        Resumen r = new Resumen();
        LocalDate hoy = LocalDate.now();
        String inicioMes = hoy.withDayOfMonth(1).toString();
        String manana = hoy.plusDays(1).toString();

        List<Object[]> h = dao.filas("SELECT COUNT(*), COALESCE(SUM(total), 0) FROM ventas WHERE estado = 'EMITIDA' AND fecha >= ? AND fecha < ?",
                hoy.toString(), manana);
        r.ventasHoy = entero(h.get(0)[0]);
        r.totalHoy = num(h.get(0)[1]);
        List<Object[]> m = dao.filas("SELECT COUNT(*), COALESCE(SUM(total), 0), COALESCE(SUM(total - impuesto), 0) FROM ventas "
                + "WHERE estado = 'EMITIDA' AND fecha >= ? AND fecha < ?", inicioMes, manana);
        r.ventasMes = entero(m.get(0)[0]);
        r.totalMes = num(m.get(0)[1]);
        double netoMes = num(m.get(0)[2]);
        r.unidadesMes = (int) dao.numero("SELECT COALESCE(SUM(d.cantidad), 0) FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id "
                + "WHERE v.estado = 'EMITIDA' AND v.fecha >= ? AND v.fecha < ? AND d.repuesto_id > 0", inicioMes, manana);
        double costoMes = dao.numero("SELECT COALESCE(SUM(d.costo_unitario * d.cantidad), 0) FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id "
                + "WHERE v.estado = 'EMITIDA' AND v.fecha >= ? AND v.fecha < ?", inicioMes, manana);
        r.gananciaMes = Math.round((netoMes - costoMes) * 100.0) / 100.0;

        List<Object[]> inv = dao.filas("SELECT COUNT(*), SUM(CASE WHEN stock > 0 THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN stock > 0 AND stock <= stock_minimo THEN 1 ELSE 0 END), SUM(CASE WHEN stock <= 0 THEN 1 ELSE 0 END), "
                + "COALESCE(SUM(CASE WHEN stock > 0 THEN stock * costo ELSE 0 END), 0) FROM repuestos");
        r.productosTotal = entero(inv.get(0)[0]);
        r.productosDisponibles = entero(inv.get(0)[1]);
        r.stockBajo = entero(inv.get(0)[2]);
        r.agotados = entero(inv.get(0)[3]);
        r.valorInventario = num(inv.get(0)[4]);
        r.clientesActivos = (int) dao.numero("SELECT COUNT(*) FROM clientes WHERE activo = 1");

        for (Object[] c : dao.filas(SALDO_CREDITO)) {
            double saldo = num(c[2]);
            if (saldo <= 0.005) continue;
            r.creditosPendientes++;
            r.saldoCreditos += saldo;
            String limite = c[1] == null ? null : c[1].toString();
            if (limite != null && limite.compareTo(hoy.toString()) < 0) {
                r.creditosVencidos++;
                r.saldoVencido += saldo;
            }
        }
        r.comprasPendientes = (int) dao.numero("SELECT COUNT(*) FROM compras WHERE estado IN ('PENDIENTE', 'PARCIAL')");
        r.saldoProveedores = Math.max(0, dao.numero("SELECT COALESCE(SUM(c.total "
                + "- COALESCE((SELECT SUM(pp.monto) FROM pagos_proveedor pp WHERE pp.compra_id = c.id), 0) "
                + "- COALESCE((SELECT SUM(dp.total) FROM devoluciones_proveedor dp WHERE dp.compra_id = c.id), 0)), 0) "
                + "FROM compras c WHERE c.estado IN ('PARCIAL', 'RECIBIDA')"));
        r.ordenesAbiertas = (int) dao.numero("SELECT COUNT(*) FROM ordenes_trabajo WHERE estado IN ('RECIBIDO', 'DIAGNOSTICO', 'EN_REPARACION', 'LISTO')");
        r.ordenesListas = (int) dao.numero("SELECT COUNT(*) FROM ordenes_trabajo WHERE estado = 'LISTO'");
        return r;
    }

    /** Ventas de los últimos N días (incluye los días sin ventas en 0). */
    public List<Punto> ventasPorDia(int dias) {
        LocalDate hoy = LocalDate.now();
        LocalDate inicio = hoy.minusDays(dias - 1L);
        Map<String, Double> mapa = new LinkedHashMap<>();
        DateTimeFormatter etiqueta = DateTimeFormatter.ofPattern("dd/MM");
        for (LocalDate d = inicio; !d.isAfter(hoy); d = d.plusDays(1)) mapa.put(d.toString(), 0.0);
        for (Object[] f : dao.filas("SELECT date(fecha), SUM(total) FROM ventas WHERE estado = 'EMITIDA' AND fecha >= ? GROUP BY 1",
                inicio.toString())) {
            mapa.computeIfPresent(String.valueOf(f[0]), (k, v) -> num(f[1]));
        }
        List<Punto> lista = new ArrayList<>();
        for (Map.Entry<String, Double> e : mapa.entrySet()) lista.add(new Punto(LocalDate.parse(e.getKey()).format(etiqueta), e.getValue()));
        return lista;
    }

    /** Ventas de las últimas N semanas (lunes a domingo). */
    public List<Punto> ventasPorSemana(int semanas) {
        LocalDate lunes = LocalDate.now().with(DayOfWeek.MONDAY);
        List<Punto> lista = new ArrayList<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM");
        WeekFields wf = WeekFields.of(Locale.getDefault());
        for (int i = semanas - 1; i >= 0; i--) {
            LocalDate ini = lunes.minusWeeks(i);
            double total = dao.numero("SELECT COALESCE(SUM(total), 0) FROM ventas WHERE estado = 'EMITIDA' AND fecha >= ? AND fecha < ?",
                    ini.toString(), ini.plusDays(7).toString());
            lista.add(new Punto("S" + ini.get(wf.weekOfWeekBasedYear()) + " (" + ini.format(f) + ")", total));
        }
        return lista;
    }

    /** Ventas de los últimos N meses. */
    public List<Punto> ventasPorMes(int meses) {
        LocalDate primero = LocalDate.now().withDayOfMonth(1);
        List<Punto> lista = new ArrayList<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("MMM yy", new Locale("es"));
        for (int i = meses - 1; i >= 0; i--) {
            LocalDate ini = primero.minusMonths(i);
            double total = dao.numero("SELECT COALESCE(SUM(total), 0) FROM ventas WHERE estado = 'EMITIDA' AND fecha >= ? AND fecha < ?",
                    ini.toString(), ini.plusMonths(1).toString());
            lista.add(new Punto(ini.format(f), total));
        }
        return lista;
    }

    /** Productos más vendidos (unidades) desde la fecha indicada. */
    public List<Punto> topProductos(LocalDate desde, int limite) {
        List<Punto> lista = new ArrayList<>();
        for (Object[] f : dao.filas("SELECT d.descripcion, SUM(d.cantidad) FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id "
                + "WHERE v.estado = 'EMITIDA' AND v.fecha >= ? AND d.repuesto_id > 0 GROUP BY d.repuesto_id ORDER BY 2 DESC LIMIT ?",
                desde.toString(), limite)) {
            lista.add(new Punto(String.valueOf(f[0]), num(f[1])));
        }
        return lista;
    }

    /** Importe vendido por categoría desde la fecha indicada. */
    public List<Punto> ventasPorCategoria(LocalDate desde) {
        List<Punto> lista = new ArrayList<>();
        for (Object[] f : dao.filas("SELECT COALESCE(NULLIF(r.categoria, ''), CASE WHEN d.repuesto_id = 0 THEN 'Servicios' ELSE 'Sin categoría' END), "
                + "SUM(d.subtotal) FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id LEFT JOIN repuestos r ON r.id = d.repuesto_id "
                + "WHERE v.estado = 'EMITIDA' AND v.fecha >= ? GROUP BY 1 ORDER BY 2 DESC", desde.toString())) {
            lista.add(new Punto(String.valueOf(f[0]), num(f[1])));
        }
        return lista;
    }

    /** [código, descripción, stock, mínimo] de los productos más urgentes de reponer. */
    public List<Object[]> stockCritico(int limite) {
        return dao.filas("SELECT codigo, descripcion, stock, stock_minimo FROM repuestos WHERE stock <= stock_minimo "
                + "ORDER BY stock, (stock_minimo - stock) DESC LIMIT ?", limite);
    }

    /** [id, fecha, cliente, vendedor, método, total, estado] de las últimas ventas. */
    public List<Object[]> ultimasVentas(int limite) {
        return dao.filas("SELECT v.id, v.fecha, COALESCE(c.nombre, 'Consumidor final'), v.vendedor, v.metodo_pago, v.total, v.estado "
                + "FROM ventas v LEFT JOIN clientes c ON c.id = v.cliente_id ORDER BY v.id DESC LIMIT ?", limite);
    }

    /** [id, fecha, proveedor, estado, total] de las últimas compras. */
    public List<Object[]> comprasRecientes(int limite) {
        return dao.filas("SELECT c.id, c.fecha, p.nombre, c.estado, c.total FROM compras c LEFT JOIN proveedores p ON p.id = c.proveedor_id "
                + "ORDER BY c.id DESC LIMIT ?", limite);
    }

    private static double num(Object o) { return o instanceof Number n ? n.doubleValue() : 0; }

    private static int entero(Object o) { return o instanceof Number n ? n.intValue() : 0; }
}
