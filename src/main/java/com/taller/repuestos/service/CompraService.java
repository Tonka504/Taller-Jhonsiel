package com.taller.repuestos.service;

import com.taller.repuestos.dao.CompraDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Compra;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Proveedor;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Sesion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compras: Proveedor → Orden de compra → Recepción → Inventario.
 * Al recibir, el inventario sube y el costo del producto se actualiza (con historial de costos).
 */
public class CompraService {

    public static final List<String> METODOS_PAGO = List.of("Efectivo (desde caja)", "Transferencia", "Cheque", "Tarjeta", "Efectivo (fuera de caja)");

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CompraDAO dao = new CompraDAO();
    private final ProveedorService proveedores;
    private final CajaService cajaService;
    private final PermisoService permisos = PermisoService.get();

    public CompraService(ProveedorService proveedores, CajaService cajaService) {
        this.proveedores = proveedores;
        this.cajaService = cajaService;
    }

    public List<Compra> listar(Integer proveedorId, String estado, LocalDate desde, LocalDate hasta) {
        return dao.listar(proveedorId, estado, desde == null ? null : desde.toString(), hasta == null ? null : hasta.toString());
    }

    public Compra obtener(int id) { return dao.obtener(id); }

    public List<CompraDAO.Pago> pagos(int compraId) { return dao.pagos(compraId); }

    public int contarPendientes() { return dao.contarPendientes(); }

    /** Crea la orden de compra (queda PENDIENTE hasta recibir la mercadería). */
    public Compra crear(Compra c) throws ReglaNegocioException {
        permisos.exigir(Permiso.COMPRAS_CREAR);
        Proveedor p = c.getProveedorId() > 0 ? proveedores.obtener(c.getProveedorId()) : null;
        if (p == null) throw new ReglaNegocioException("Elige el proveedor.");
        if (!p.isActivo()) throw new ReglaNegocioException("El proveedor \"" + p.getNombre() + "\" está inactivo.");
        if (c.getLineas().isEmpty()) throw new ReglaNegocioException("Agrega al menos un producto a la compra.");
        Set<Integer> vistos = new HashSet<>();
        for (Compra.Linea l : c.getLineas()) {
            if (l.cantidad <= 0) throw new ReglaNegocioException("La cantidad de \"" + l.descripcion + "\" debe ser mayor que cero.");
            if (l.costoUnitario < 0 || Double.isNaN(l.costoUnitario)) {
                throw new ReglaNegocioException("El costo de \"" + l.descripcion + "\" no puede ser negativo.");
            }
            if (l.descuento < 0 || l.descuento > l.getBruto()) {
                throw new ReglaNegocioException("El descuento de \"" + l.descripcion + "\" no puede ser negativo ni mayor que el importe de la línea.");
            }
            if (!vistos.add(l.repuestoId)) {
                throw new ReglaNegocioException("\"" + l.descripcion + "\" está repetido: suma las cantidades en una sola línea.");
            }
        }
        if (c.getImpuestoPct() < 0 || c.getImpuestoPct() > 100) {
            throw new ReglaNegocioException("El impuesto debe estar entre 0 y 100 %.");
        }
        if (!Compra.CONDICIONES.contains(c.getCondicionPago())) c.setCondicionPago("Contado");
        if ("Crédito".equals(c.getCondicionPago()) && c.getFechaVencimiento() == null) {
            throw new ReglaNegocioException("Indica la fecha de vencimiento del crédito con el proveedor.");
        }
        c.recalcular();
        c.setFecha(ahora());
        c.setUsuario(Sesion.nombreActual());
        int id = dao.crear(c);
        if (id <= 0) throw new ReglaNegocioException("No se pudo guardar la compra.");
        c.setId(id);
        AuditoriaService.registrar(AuditoriaService.COMPRAS, "Creó la orden de compra", Formato.numeroCompra(id), null,
                "Proveedor: " + p.getNombre() + "; productos: " + c.getLineas().size() + "; total: " + Formato.moneda(c.getTotal()));
        return dao.obtener(id);
    }

    /**
     * Registra la mercadería que llegó.
     * @param cantidades id de la línea → unidades recibidas ahora
     * @param actualizarCosto si true, el costo de cada producto pasa a ser el de esta compra (queda historial)
     */
    public void recibir(Compra c, Map<Integer, Integer> cantidades, boolean actualizarCosto) throws ReglaNegocioException {
        permisos.exigir(Permiso.COMPRAS_RECIBIR);
        if (actualizarCosto) permisos.exigir(Permiso.PRECIOS_MODIFICAR);
        Compra actual = dao.obtener(c.getId());
        if (actual == null) throw new ReglaNegocioException("La compra ya no existe.");
        if (actual.isAnulada() || Compra.RECIBIDA.equals(actual.getEstado())) {
            throw new ReglaNegocioException("Esta compra está " + actual.getEstadoEtiqueta().toLowerCase() + ": no hay nada por recibir.");
        }
        int total = 0;
        StringBuilder detalle = new StringBuilder();
        for (Compra.Linea l : actual.getLineas()) {
            Integer cant = cantidades.get(l.id);
            if (cant == null || cant == 0) continue;
            if (cant < 0) throw new ReglaNegocioException("No se pueden recibir cantidades negativas.");
            if (cant > l.getPendiente()) {
                throw new ReglaNegocioException("De \"" + l.descripcion + "\" solo faltan " + l.getPendiente() + " unidades por recibir.");
            }
            total += cant;
            if (detalle.length() > 0) detalle.append(", ");
            detalle.append(l.codigo).append(" x").append(cant);
        }
        if (total == 0) throw new ReglaNegocioException("Indica cuántas unidades llegaron de al menos un producto.");
        String doc = Formato.numeroCompra(actual.getId());
        int r = dao.recibir(actual.getId(), cantidades, actualizarCosto, ahora(), Sesion.nombreActual(), doc, actual.getProveedorNombre());
        if (r == CompraDAO.ERROR_CANTIDAD) throw new ReglaNegocioException("Las cantidades cambiaron mientras recibías. Vuelve a abrir la compra.");
        if (r <= 0) throw new ReglaNegocioException("No se pudo registrar la recepción.");
        AuditoriaService.registrar(AuditoriaService.COMPRAS, "Recibió mercadería", doc, null,
                detalle + (actualizarCosto ? "; costos actualizados" : "; costos sin cambio"));
    }

    public void anular(Compra c, String motivo) throws ReglaNegocioException {
        permisos.exigir(Permiso.COMPRAS_ANULAR);
        if (motivo == null || motivo.trim().isEmpty()) throw new ReglaNegocioException("Escribe el motivo de la anulación.");
        if (!Compra.PENDIENTE.equals(c.getEstado())) {
            throw new ReglaNegocioException("Solo se pueden anular compras pendientes. Si ya llegó mercadería, usa una devolución al proveedor.");
        }
        if (!dao.anular(c.getId(), Sesion.nombreActual(), ahora(), motivo.trim())) {
            throw new ReglaNegocioException("No se pudo anular: la compra ya tiene mercadería recibida o pagos registrados.");
        }
        AuditoriaService.registrar(AuditoriaService.COMPRAS, "Anuló la compra", Formato.numeroCompra(c.getId()),
                "Total: " + Formato.moneda(c.getTotal()), "Anulada. Motivo: " + motivo.trim());
    }

    public void registrarPago(Compra c, double monto, String metodo, String referencia) throws ReglaNegocioException {
        permisos.exigir(Permiso.COMPRAS_PAGAR);
        Compra actual = dao.obtener(c.getId());
        if (actual == null) throw new ReglaNegocioException("La compra ya no existe.");
        if (actual.isAnulada()) throw new ReglaNegocioException("La compra está anulada.");
        if (Compra.PENDIENTE.equals(actual.getEstado())) {
            throw new ReglaNegocioException("Primero registra la recepción de la mercadería; luego podrás pagarla.");
        }
        if (Double.isNaN(monto) || monto <= 0) throw new ReglaNegocioException("El monto debe ser mayor que cero.");
        monto = Math.round(monto * 100.0) / 100.0;
        if (monto > actual.getSaldo() + 0.005) {
            throw new ReglaNegocioException("El pago supera el saldo pendiente (" + Formato.moneda(actual.getSaldo()) + ").");
        }
        if (!METODOS_PAGO.contains(metodo)) throw new ReglaNegocioException("Elige la forma de pago.");
        Integer cajaId = null;
        if (metodo.equals(METODOS_PAGO.get(0))) {
            permisos.exigir(Permiso.CAJA_MOVIMIENTOS);
            CajaService.ResumenCaja r = cajaService.resumenActual();
            if (r == null) throw new ReglaNegocioException("La caja está cerrada. Ábrela o elige otra forma de pago.");
            if (monto > r.efectivoEsperado + 0.005) {
                throw new ReglaNegocioException("No hay suficiente efectivo en la caja. Disponible: " + Formato.moneda(r.efectivoEsperado) + ".");
            }
            cajaId = r.caja.getId();
        }
        String doc = Formato.numeroCompra(actual.getId());
        if (!dao.registrarPago(actual.getId(), ahora(), monto, metodo, referencia == null ? null : referencia.trim(),
                Sesion.nombreActual(), cajaId, doc)) {
            throw new ReglaNegocioException("No se pudo registrar el pago.");
        }
        AuditoriaService.registrar(AuditoriaService.COMPRAS, "Pagó a proveedor", doc,
                "Saldo: " + Formato.moneda(actual.getSaldo()),
                "Pago: " + Formato.moneda(monto) + " (" + metodo + "); proveedor: " + actual.getProveedorNombre());
    }

    /** Devuelve mercadería al proveedor (sale del inventario y baja la deuda). */
    public void devolver(Compra c, Map<Integer, Integer> cantidades, String motivo) throws ReglaNegocioException {
        permisos.exigir(Permiso.COMPRAS_ANULAR);
        if (motivo == null || motivo.trim().isEmpty()) throw new ReglaNegocioException("Escribe el motivo de la devolución.");
        Compra actual = dao.obtener(c.getId());
        if (actual == null) throw new ReglaNegocioException("La compra ya no existe.");
        boolean alguna = false;
        StringBuilder detalle = new StringBuilder();
        for (Compra.Linea l : actual.getLineas()) {
            Integer cant = cantidades.get(l.id);
            if (cant == null || cant == 0) continue;
            if (cant < 0) throw new ReglaNegocioException("No se pueden devolver cantidades negativas.");
            if (cant > l.getDevolvible()) {
                throw new ReglaNegocioException("De \"" + l.descripcion + "\" solo se pueden devolver " + l.getDevolvible() + " unidades.");
            }
            alguna = true;
            if (detalle.length() > 0) detalle.append(", ");
            detalle.append(l.codigo).append(" x").append(cant);
        }
        if (!alguna) throw new ReglaNegocioException("Indica cuántas unidades devuelves de al menos un producto.");
        String doc = Formato.numeroCompra(actual.getId());
        int r = dao.devolver(actual.getId(), cantidades, motivo.trim(), ahora(), Sesion.nombreActual(), doc, actual.getImpuestoPct());
        if (r == CompraDAO.ERROR_STOCK) throw new ReglaNegocioException("No hay existencias suficientes para devolver esas unidades.");
        if (r == CompraDAO.ERROR_CANTIDAD) throw new ReglaNegocioException("Las cantidades cambiaron. Vuelve a abrir la compra.");
        if (r <= 0) throw new ReglaNegocioException("No se pudo registrar la devolución.");
        AuditoriaService.registrar(AuditoriaService.COMPRAS, "Devolvió mercadería al proveedor", doc, null,
                detalle + "; motivo: " + motivo.trim());
    }

    private static String ahora() {
        return LocalDateTime.now().format(FORMATO_FECHA);
    }
}
