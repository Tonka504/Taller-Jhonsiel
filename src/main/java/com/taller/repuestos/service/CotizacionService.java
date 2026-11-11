package com.taller.repuestos.service;

import com.taller.repuestos.dao.CotizacionDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Cliente;
import com.taller.repuestos.model.Cotizacion;
import com.taller.repuestos.model.ItemCarrito;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Rol;
import com.taller.repuestos.model.Usuario;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Sesion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Cotizaciones: se arman desde el carrito del punto de venta y se convierten en venta cuando el cliente acepta. */
public class CotizacionService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CotizacionDAO dao = new CotizacionDAO();
    private final PermisoService permisos = PermisoService.get();
    private final ConfiguracionService config = ConfiguracionService.get();

    public List<Cotizacion> listar(LocalDate desde, LocalDate hasta, String texto) {
        return dao.listar(desde == null ? null : desde.toString(), hasta == null ? null : hasta.toString(), texto);
    }

    public Cotizacion obtener(int id) { return dao.obtener(id); }

    /**
     * Guarda el carrito como cotización. Usa los mismos cálculos (descuentos e impuesto) que una venta.
     * @param nombreLibre nombre del interesado cuando no es un cliente registrado (opcional)
     */
    public Cotizacion crear(List<ItemCarrito> lineas, Cliente cliente, String nombreLibre, String tipoDescuento,
                            double valorDescuento, String observaciones) throws ReglaNegocioException {
        permisos.exigir(Permiso.POS_USAR);
        if (lineas == null || lineas.isEmpty()) throw new ReglaNegocioException("Agrega productos al carrito para cotizar.");
        boolean hayDescuento = valorDescuento > 0;
        for (ItemCarrito i : lineas) {
            if (i.getCantidad() <= 0) throw new ReglaNegocioException("Hay un producto con cantidad inválida.");
            if (i.getDescuentoPct() > 0) hayDescuento = true;
        }
        String tipo = valorDescuento > 0 ? tipoDescuento : null;
        CalculoVenta.Resultado calc;
        try {
            calc = CalculoVenta.calcular(lineas, tipo, tipo == null ? 0 : valorDescuento, config.impuestoPorcentaje(), config.impuestoIncluido());
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(e.getMessage());
        }
        if (hayDescuento) {
            permisos.exigir(Permiso.DESCUENTOS_APLICAR);
            Usuario u = Sesion.getUsuario();
            boolean admin = u != null && Rol.ADMIN.name().equals(u.getRol());
            double max = config.descuentoMaximo();
            if (!admin) {
                for (ItemCarrito i : lineas) {
                    if (i.getDescuentoPct() > max + 0.0001) throw new ReglaNegocioException("El descuento máximo permitido es " + max + "%.");
                }
                if (calc.getDescuentoGeneralPct() > max + 0.0001) {
                    throw new ReglaNegocioException("El descuento general máximo permitido es " + max + "%.");
                }
            }
        }
        if (calc.total <= 0) throw new ReglaNegocioException("El total de la cotización debe ser mayor que cero.");

        Cotizacion c = new Cotizacion();
        c.setFecha(LocalDateTime.now().format(FORMATO_FECHA));
        c.setFechaVencimiento(LocalDate.now().plusDays(config.getEntero(ConfiguracionService.COTIZACION_DIAS, 1, 365)).toString());
        if (cliente != null) {
            c.setClienteId(cliente.getId());
            c.setClienteNombre(cliente.getNombre());
        } else if (nombreLibre != null && !nombreLibre.isBlank()) {
            c.setClienteNombre(nombreLibre.trim());
        }
        c.setVendedor(Sesion.nombreActual());
        c.setSubtotal(calc.subtotal);
        c.setDescuento(calc.descuentoGeneral);
        c.setTipoDescuento(tipo);
        c.setDescuentoValor(tipo == null ? 0 : valorDescuento);
        c.setImpuesto(calc.impuesto);
        c.setTotal(calc.total);
        c.setObservaciones(observaciones == null || observaciones.isBlank() ? null : observaciones.trim());
        for (ItemCarrito i : lineas) {
            Cotizacion.Linea l = new Cotizacion.Linea();
            l.repuestoId = i.getRepuesto().getId();
            l.descripcion = i.getRepuesto().getDescripcion();
            l.cantidad = i.getCantidad();
            l.precioUnitario = i.getPrecioUnitario();
            l.descuentoPct = i.getDescuentoPct();
            l.subtotal = i.getSubtotal();
            c.getLineas().add(l);
        }
        int id = dao.crear(c);
        if (id <= 0) throw new ReglaNegocioException("No se pudo guardar la cotización.");
        AuditoriaService.registrar(AuditoriaService.VENTAS, "Creó la cotización", Formato.numeroCotizacion(id), null,
                "Cliente: " + c.getClienteMostrar() + "; total: " + Formato.moneda(c.getTotal()) + "; válida hasta " + c.getFechaVencimiento());
        return dao.obtener(id);
    }

    public void anular(Cotizacion c, String motivo) throws ReglaNegocioException {
        permisos.exigir(Permiso.POS_USAR);
        if (motivo == null || motivo.isBlank()) throw new ReglaNegocioException("Escribe el motivo de la anulación.");
        if (!c.isPendiente()) throw new ReglaNegocioException("Solo se pueden anular cotizaciones pendientes.");
        if (!dao.cambiarEstado(c.getId(), Cotizacion.ANULADA, null, "[Anulada: " + motivo.trim() + "]")) {
            throw new ReglaNegocioException("No se pudo anular la cotización.");
        }
        AuditoriaService.registrar(AuditoriaService.VENTAS, "Anuló la cotización", Formato.numeroCotizacion(c.getId()),
                "Pendiente", "Anulada. Motivo: " + motivo.trim());
    }

    /** Marca la cotización como vendida (se llama al cobrar en el punto de venta). */
    public void marcarVendida(int cotizacionId, int ventaId) {
        if (dao.cambiarEstado(cotizacionId, Cotizacion.CONVERTIDA, ventaId, null)) {
            AuditoriaService.registrar(AuditoriaService.VENTAS, "Convirtió la cotización en venta", Formato.numeroCotizacion(cotizacionId),
                    "Pendiente", "Factura " + Formato.numeroFactura(ventaId));
        }
    }
}
