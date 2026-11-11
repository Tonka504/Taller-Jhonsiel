package com.taller.repuestos.service;

import com.taller.repuestos.dao.CatalogoDAO;
import com.taller.repuestos.dao.MovimientoInventarioDAO;
import com.taller.repuestos.dao.RepuestoDAO;
import com.taller.repuestos.model.MovimientoInventario;
import com.taller.repuestos.util.Sesion;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.util.Formato;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reglas del inventario: validaciones, permisos y auditoría de cada cambio. */
public class InventarioService {

    public static final List<String> UNIDADES = List.of("Unidad", "Par", "Juego", "Kit", "Litro", "Galón", "Metro", "Caja");

    /** Tipos de ajuste que una persona puede registrar a mano. */
    public static final List<MovimientoInventario.Tipo> TIPOS_AJUSTE = List.of(
            MovimientoInventario.Tipo.AJUSTE_ENTRADA, MovimientoInventario.Tipo.AJUSTE_SALIDA,
            MovimientoInventario.Tipo.CORRECCION);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RepuestoDAO dao = new RepuestoDAO();
    private final MovimientoInventarioDAO kardex = new MovimientoInventarioDAO();
    private final CatalogoService catalogo;
    private final PermisoService permisos = PermisoService.get();

    public InventarioService(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    public void crear(Repuesto r) throws ReglaNegocioException {
        permisos.exigir(Permiso.INVENTARIO_EDITAR);
        normalizar(r);
        validar(r);
        verificarDuplicados(r, 0);
        if (!dao.guardar(r)) {
            throw new ReglaNegocioException("No se pudo guardar el producto.");
        }
        registrarEnCatalogo(r);
        if (r.getStock() > 0) {
            kardex.registrarSuelto(r.getId(), ahora(), MovimientoInventario.Tipo.INICIAL, r.getStock(),
                    "Existencia inicial al registrar el producto", Sesion.nombreActual());
        }
        AuditoriaService.registrar(AuditoriaService.INVENTARIO, "Creó el producto", identificar(r), null, resumen(r));
    }

    /**
     * Guarda los cambios de un producto.
     * @param original el producto como estaba antes de editar
     * @param nuevo    el producto con los cambios (mismo id)
     */
    public void actualizar(Repuesto original, Repuesto nuevo) throws ReglaNegocioException {
        permisos.exigir(Permiso.INVENTARIO_EDITAR);
        normalizar(nuevo);
        validar(nuevo);
        verificarDuplicados(nuevo, original.getId());

        if (original.getCosto() != nuevo.getCosto() || original.getPrecio() != nuevo.getPrecio()
                || original.getPrecioMayorista() != nuevo.getPrecioMayorista()) {
            permisos.exigir(Permiso.PRECIOS_MODIFICAR);
        }
        boolean cambiaStock = original.getStock() != nuevo.getStock();
        if (cambiaStock) {
            permisos.exigir(Permiso.STOCK_AJUSTAR);
        }
        String[] cambios = diferencias(original, nuevo);
        // El stock nunca se escribe junto con los datos: si cambió, se aplica como corrección en el kardex
        // (así queda registrado quién lo cambió y no se pisan ventas hechas mientras se editaba)
        if (!dao.actualizar(nuevo, false)) {
            throw new ReglaNegocioException("No se pudo actualizar el producto.");
        }
        if (cambiaStock && kardex.fijarStock(nuevo.getId(), nuevo.getStock(), MovimientoInventario.Tipo.CORRECCION,
                ahora(), "Corrección desde la edición del producto", Sesion.nombreActual()) == null) {
            throw new ReglaNegocioException("Los datos se guardaron, pero no se pudo corregir la existencia.");
        }
        registrarEnCatalogo(nuevo);
        if (!cambios[0].isEmpty() || !cambios[1].isEmpty()) {
            AuditoriaService.registrar(AuditoriaService.INVENTARIO, "Modificó el producto", identificar(original),
                    cambios[0], cambios[1]);
        }
    }

    public void eliminar(Repuesto r) throws ReglaNegocioException {
        permisos.exigir(Permiso.INVENTARIO_ELIMINAR);
        if (dao.tieneMovimientos(r.getId())) {
            throw new ReglaNegocioException("Este producto ya tiene ventas o reabastecimientos registrados y no se puede eliminar. "
                    + "Si ya no lo vendes, déjalo con existencia 0.");
        }
        if (!dao.eliminar(r.getId())) {
            throw new ReglaNegocioException("No se pudo eliminar el producto.");
        }
        AuditoriaService.registrar(AuditoriaService.INVENTARIO, "Eliminó el producto", identificar(r), resumen(r), null);
    }

    public void reabastecer(Repuesto r, int cantidad) throws ReglaNegocioException {
        permisos.exigir(Permiso.STOCK_AJUSTAR);
        if (cantidad <= 0) {
            throw new ReglaNegocioException("La cantidad a agregar debe ser mayor que cero.");
        }
        int nuevoStock = r.getStock() + cantidad;
        if (!dao.reabastecerStock(r.getId(), cantidad, nuevoStock)) {
            throw new ReglaNegocioException("No se pudo actualizar el stock.");
        }
        AuditoriaService.registrar(AuditoriaService.INVENTARIO, "Reabasteció existencias", identificar(r),
                "Stock: " + r.getStock(), "Stock: " + nuevoStock + " (+" + cantidad + ")");
    }

    /**
     * Ajuste manual de existencias con motivo obligatorio.
     * @param cantidad para entradas y salidas: unidades a sumar o restar (siempre positiva);
     *                 para CORRECCION: la existencia real contada
     * @return la diferencia aplicada al stock
     */
    public int ajustar(Repuesto r, MovimientoInventario.Tipo tipo, int cantidad, String motivo) throws ReglaNegocioException {
        permisos.exigir(Permiso.STOCK_AJUSTAR);
        if (tipo == null || !TIPOS_AJUSTE.contains(tipo)) {
            throw new ReglaNegocioException("Elige el tipo de ajuste.");
        }
        String motivoLimpio = motivo == null ? "" : motivo.trim();
        if (motivoLimpio.isEmpty()) {
            throw new ReglaNegocioException("Escribe el motivo del ajuste (por ejemplo: pieza dañada, conteo físico...).");
        }
        if (cantidad < 0) {
            throw new ReglaNegocioException("No se pueden registrar cantidades negativas.");
        }
        int diferencia;
        if (tipo == MovimientoInventario.Tipo.CORRECCION) {
            Integer d = kardex.fijarStock(r.getId(), cantidad, tipo, ahora(), motivoLimpio, Sesion.nombreActual());
            if (d == null) throw new ReglaNegocioException("No se pudo corregir la existencia.");
            diferencia = d;
        } else {
            if (cantidad == 0) throw new ReglaNegocioException("La cantidad debe ser mayor que cero.");
            diferencia = tipo == MovimientoInventario.Tipo.AJUSTE_ENTRADA ? cantidad : -cantidad;
            if (!kardex.ajustar(r.getId(), diferencia, tipo, ahora(), null, motivoLimpio, Sesion.nombreActual())) {
                throw new ReglaNegocioException("No hay existencias suficientes para esa salida (stock actual: " + r.getStock() + ").");
            }
        }
        AuditoriaService.registrar(AuditoriaService.INVENTARIO, tipo.getEtiqueta(), identificar(r),
                "Stock: " + r.getStock(), "Stock: " + (r.getStock() + diferencia) + " (" + (diferencia >= 0 ? "+" : "") + diferencia
                        + "). Motivo: " + motivoLimpio);
        return diferencia;
    }

    /** Movimientos del kardex según los filtros. */
    public List<MovimientoInventario> movimientos(MovimientoInventarioDAO.Filtro filtro) {
        return kardex.buscar(filtro);
    }

    private static String ahora() {
        return LocalDateTime.now().format(FORMATO_FECHA);
    }

    // ------------------------------------------------------------------

    /** Deja los textos sin espacios de más y con la unidad por defecto. */
    private static void normalizar(Repuesto r) {
        r.setCodigo(nz(r.getCodigo()).trim());
        r.setDescripcion(nz(r.getDescripcion()).trim());
        r.setMarca(nz(r.getMarca()).trim());
        r.setCategoria(nz(r.getCategoria()).trim());
        r.setAplicacion(nz(r.getAplicacion()).trim());
        r.setUbicacion(nz(r.getUbicacion()).trim());
        r.setProveedor(nz(r.getProveedor()).trim());
        r.setCodigoBarras(nz(r.getCodigoBarras()).trim());
        if (r.getUnidad() == null || r.getUnidad().trim().isEmpty()) r.setUnidad("Unidad");
    }

    private static void validar(Repuesto r) throws ReglaNegocioException {
        if (r.getCodigo().isEmpty()) {
            throw new ReglaNegocioException("El código del producto es obligatorio.");
        }
        if (r.getDescripcion().isEmpty()) {
            throw new ReglaNegocioException("La descripción del producto es obligatoria.");
        }
        if (!esMontoValido(r.getCosto())) {
            throw new ReglaNegocioException("El costo no puede ser negativo.");
        }
        if (!esMontoValido(r.getPrecio())) {
            throw new ReglaNegocioException("El precio de venta no puede ser negativo.");
        }
        if (!esMontoValido(r.getPrecioMayorista())) {
            throw new ReglaNegocioException("El precio mayorista no puede ser negativo.");
        }
        if (r.getStock() < 0) {
            throw new ReglaNegocioException("La existencia no puede ser negativa.");
        }
        if (r.getStockMinimo() < 0) {
            throw new ReglaNegocioException("El stock mínimo no puede ser negativo.");
        }
        if (r.getStockMaximo() < 0) {
            throw new ReglaNegocioException("El stock máximo no puede ser negativo.");
        }
        if (r.getStockMaximo() > 0 && r.getStockMaximo() < r.getStockMinimo()) {
            throw new ReglaNegocioException("El stock máximo no puede ser menor que el stock mínimo.");
        }
        if (!UNIDADES.contains(r.getUnidad())) {
            throw new ReglaNegocioException("Elige una unidad de medida de la lista.");
        }
        if (r.getMarca().length() > 60 || r.getCategoria().length() > 60) {
            throw new ReglaNegocioException("La marca y la categoría pueden tener hasta 60 caracteres.");
        }
    }

    /** Código y código de barras no se pueden repetir en otro producto. */
    private void verificarDuplicados(Repuesto r, int idActual) throws ReglaNegocioException {
        if (dao.existeCodigo(r.getCodigo(), idActual)) {
            throw new ReglaNegocioException("Ya existe otro producto con el código \"" + r.getCodigo() + "\".");
        }
        if (!r.getCodigoBarras().isEmpty() && dao.existeCodigoBarras(r.getCodigoBarras(), idActual)) {
            throw new ReglaNegocioException("Ya existe otro producto con el código de barras \"" + r.getCodigoBarras() + "\".");
        }
    }

    /** Si el producto usa una marca o categoría que todavía no está en el catálogo, la agrega. */
    private void registrarEnCatalogo(Repuesto r) {
        catalogo.asegurar(CatalogoDAO.Tipo.MARCA, r.getMarca());
        catalogo.asegurar(CatalogoDAO.Tipo.CATEGORIA, r.getCategoria());
        if (r.getProveedor() != null && !r.getProveedor().trim().isEmpty() && r.getProveedor().trim().length() <= 80) {
            new com.taller.repuestos.dao.ProveedorDAO().asegurar(r.getProveedor().trim());
        }
    }

    private static boolean esMontoValido(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && v >= 0;
    }

    private static String identificar(Repuesto r) {
        return r.getCodigo() + " - " + r.getDescripcion();
    }

    private static String resumen(Repuesto r) {
        return "Código: " + r.getCodigo() + "; descripción: " + r.getDescripcion()
                + "; marca: " + nz(r.getMarca()) + "; categoría: " + nz(r.getCategoria())
                + "; ubicación: " + nz(r.getUbicacion())
                + "; costo: " + Formato.moneda(r.getCosto()) + "; precio: " + Formato.moneda(r.getPrecio())
                + "; stock: " + r.getStock();
    }

    /** Devuelve [campos antes, campos después], solo con lo que cambió. */
    static String[] diferencias(Repuesto a, Repuesto b) {
        List<String> antes = new ArrayList<>();
        List<String> despues = new ArrayList<>();
        comparar("código", a.getCodigo(), b.getCodigo(), antes, despues);
        comparar("código de barras", a.getCodigoBarras(), b.getCodigoBarras(), antes, despues);
        comparar("descripción", a.getDescripcion(), b.getDescripcion(), antes, despues);
        comparar("marca", a.getMarca(), b.getMarca(), antes, despues);
        comparar("categoría", a.getCategoria(), b.getCategoria(), antes, despues);
        comparar("aplicación", a.getAplicacion(), b.getAplicacion(), antes, despues);
        comparar("ubicación", a.getUbicacion(), b.getUbicacion(), antes, despues);
        comparar("proveedor", a.getProveedor(), b.getProveedor(), antes, despues);
        comparar("unidad", a.getUnidad(), b.getUnidad(), antes, despues);
        comparar("fotografía", a.getFoto(), b.getFoto(), antes, despues);
        if (a.getCosto() != b.getCosto()) {
            antes.add("costo: " + Formato.moneda(a.getCosto()));
            despues.add("costo: " + Formato.moneda(b.getCosto()));
        }
        if (a.getPrecio() != b.getPrecio()) {
            antes.add("precio: " + Formato.moneda(a.getPrecio()));
            despues.add("precio: " + Formato.moneda(b.getPrecio()));
        }
        if (a.getPrecioMayorista() != b.getPrecioMayorista()) {
            antes.add("precio mayorista: " + Formato.moneda(a.getPrecioMayorista()));
            despues.add("precio mayorista: " + Formato.moneda(b.getPrecioMayorista()));
        }
        if (a.getStock() != b.getStock()) {
            antes.add("stock: " + a.getStock());
            despues.add("stock: " + b.getStock());
        }
        if (a.getStockMinimo() != b.getStockMinimo()) {
            antes.add("stock mínimo: " + a.getStockMinimo());
            despues.add("stock mínimo: " + b.getStockMinimo());
        }
        if (a.getStockMaximo() != b.getStockMaximo()) {
            antes.add("stock máximo: " + a.getStockMaximo());
            despues.add("stock máximo: " + b.getStockMaximo());
        }
        return new String[]{String.join("; ", antes), String.join("; ", despues)};
    }

    private static void comparar(String campo, String a, String b, List<String> antes, List<String> despues) {
        if (!Objects.equals(nz(a).trim(), nz(b).trim())) {
            antes.add(campo + ": " + nz(a));
            despues.add(campo + ": " + nz(b));
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
