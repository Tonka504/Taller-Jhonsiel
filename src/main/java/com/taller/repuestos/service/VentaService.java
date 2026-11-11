package com.taller.repuestos.service;

import com.taller.repuestos.dao.ClienteDAO;
import com.taller.repuestos.dao.VentaDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Cliente;
import com.taller.repuestos.model.Factura;
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

/** Proceso de venta: valida, calcula, registra, descuenta inventario y deja constancia en la auditoría. */
public class VentaService {

    public static final List<String> METODOS_PAGO = List.of("Efectivo", "Tarjeta", "Transferencia", VentaDAO.METODO_CREDITO);

    /** Lo que el punto de venta le entrega al servicio para procesar una venta. */
    public static class Solicitud {
        public List<ItemCarrito> lineas;
        public Cliente cliente;            // null = consumidor final
        public String metodoPago;
        public String tipoDescuento;       // null, CalculoVenta.PORCENTAJE o CalculoVenta.MONTO
        public double valorDescuento;
        public Double montoRecibido;       // solo para pago en efectivo
        public LocalDate fechaLimite;      // solo para crédito
        /** Orden de trabajo que se factura (sus repuestos ya salieron del inventario al consumirse). */
        public Integer ordenId;
    }

    /** Resultado de una venta ya registrada. */
    public static class Resultado {
        public final Factura factura;
        public final double cambio;

        Resultado(Factura factura, double cambio) {
            this.factura = factura;
            this.cambio = cambio;
        }
    }

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VentaDAO ventaDAO = new VentaDAO();
    private final ClienteDAO clienteDAO = new ClienteDAO();
    private final CajaService cajaService;
    private final PermisoService permisos = PermisoService.get();
    private final ConfiguracionService config = ConfiguracionService.get();

    public VentaService(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    public Resultado procesar(Solicitud s) throws ReglaNegocioException {
        permisos.exigir(s.ordenId == null ? Permiso.POS_USAR : Permiso.ORDENES_FACTURAR);

        if (s.lineas == null || s.lineas.isEmpty()) {
            throw new ReglaNegocioException("Agrega al menos un producto al carrito para procesar la venta.");
        }
        if (s.metodoPago == null || !METODOS_PAGO.contains(s.metodoPago)) {
            throw new ReglaNegocioException("Elige un método de pago válido.");
        }
        int unidades = 0;
        boolean hayDescuentoLinea = false;
        for (ItemCarrito item : s.lineas) {
            if (item.getCantidad() <= 0) {
                throw new ReglaNegocioException("Hay un producto con cantidad inválida en el carrito.");
            }
            if (item.getDescuentoPct() > 0) hayDescuentoLinea = true;
            unidades += item.getCantidad();
        }

        // Descuento general: si el valor es 0 se ignora
        String tipoDescuento = s.valorDescuento > 0 ? s.tipoDescuento : null;
        double valorDescuento = tipoDescuento == null ? 0 : s.valorDescuento;

        CalculoVenta.Resultado calculo;
        try {
            calculo = CalculoVenta.calcular(s.lineas, tipoDescuento, valorDescuento,
                    config.impuestoPorcentaje(), config.impuestoIncluido());
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException(e.getMessage());
        }

        // Descuentos: exigen permiso y respetan el máximo configurado (el administrador no tiene tope)
        if (hayDescuentoLinea || tipoDescuento != null) {
            permisos.exigir(Permiso.DESCUENTOS_APLICAR);
            Usuario u = Sesion.getUsuario();
            boolean esAdmin = u != null && Rol.ADMIN.name().equals(u.getRol());
            if (!esAdmin) {
                double maximo = config.descuentoMaximo();
                for (ItemCarrito item : s.lineas) {
                    if (item.getDescuentoPct() > maximo + 0.0001) {
                        throw new ReglaNegocioException("El descuento máximo permitido es " + trimDecimales(maximo) + "%.");
                    }
                }
                if (calculo.getDescuentoGeneralPct() > maximo + 0.0001) {
                    throw new ReglaNegocioException("El descuento general máximo permitido es " + trimDecimales(maximo) + "%.");
                }
            }
        }

        if (calculo.total <= 0) {
            throw new ReglaNegocioException("El total de la venta debe ser mayor que cero.");
        }

        // Cliente
        Cliente cliente = null;
        if (s.cliente != null) {
            cliente = clienteDAO.obtenerPorId(s.cliente.getId());
            if (cliente == null) throw new ReglaNegocioException("El cliente elegido ya no existe.");
            if (!cliente.isActivo()) throw new ReglaNegocioException("El cliente \"" + cliente.getNombre() + "\" está inactivo.");
        }

        // Forma de pago
        Double recibido = null;
        Double cambio = null;
        String fechaLimite = null;
        if ("Efectivo".equals(s.metodoPago)) {
            if (s.montoRecibido == null || Double.isNaN(s.montoRecibido) || s.montoRecibido + 0.005 < calculo.total) {
                throw new ReglaNegocioException("El monto recibido es menor que el total (" + Formato.moneda(calculo.total) + ").");
            }
            recibido = Math.round(s.montoRecibido * 100.0) / 100.0;
            cambio = Math.round((recibido - calculo.total) * 100.0) / 100.0;
        } else if (VentaDAO.METODO_CREDITO.equals(s.metodoPago)) {
            if (cliente == null) {
                throw new ReglaNegocioException("Para vender a crédito debes elegir un cliente.");
            }
            if (cliente.getLimiteCredito() <= 0) {
                throw new ReglaNegocioException("El cliente \"" + cliente.getNombre() + "\" no tiene crédito autorizado.");
            }
            if (cliente.getSaldoPendiente() + calculo.total > cliente.getLimiteCredito() + 0.005) {
                throw new ReglaNegocioException("La venta excede el crédito del cliente. Disponible: "
                        + Formato.moneda(cliente.getCreditoDisponible()) + " (límite " + Formato.moneda(cliente.getLimiteCredito())
                        + ", debe " + Formato.moneda(cliente.getSaldoPendiente()) + ").");
            }
            if (s.fechaLimite == null || s.fechaLimite.isBefore(LocalDate.now())) {
                throw new ReglaNegocioException("Indica una fecha límite de pago que no sea anterior a hoy.");
            }
            fechaLimite = s.fechaLimite.toString();
        }

        // Caja
        Integer cajaId = cajaService.idCajaAbierta();
        if (cajaId == null && config.exigirCajaAbierta()) {
            throw new ReglaNegocioException("La caja está cerrada. Ábrela en el módulo Caja (o pídele a un cajero o administrador que lo haga) para poder vender.");
        }

        VentaDAO.DatosVenta d = new VentaDAO.DatosVenta();
        d.fecha = LocalDateTime.now().format(FORMATO_FECHA);
        d.vendedor = Sesion.nombreActual();
        d.clienteId = cliente == null ? null : cliente.getId();
        d.metodoPago = s.metodoPago;
        d.lineas = s.lineas;
        d.subtotal = calculo.subtotal;
        d.descuento = calculo.descuentoGeneral;
        d.tipoDescuento = tipoDescuento;
        d.descuentoValor = valorDescuento;
        d.impuesto = calculo.impuesto;
        d.total = calculo.total;
        d.montoRecibido = recibido;
        d.cambio = cambio;
        d.fechaLimite = fechaLimite;
        d.cajaId = cajaId;
        d.ordenId = s.ordenId;
        d.descontarStock = s.ordenId == null;

        int ventaId = ventaDAO.registrarVenta(d);
        if (ventaId == VentaDAO.ERROR_STOCK) {
            throw new ReglaNegocioException("Alguno de los productos ya no tiene existencias suficientes. Revisa el carrito y vuelve a intentar.");
        }
        if (ventaId <= 0) {
            throw new ReglaNegocioException("No se pudo registrar la venta en la base de datos.");
        }

        String detalle = "Total: " + Formato.moneda(calculo.total) + "; pago: " + s.metodoPago + "; unidades: " + unidades
                + (cliente != null ? "; cliente: " + cliente.getNombre() : "")
                + (calculo.descuentoGeneral > 0 || hayDescuentoLinea ? "; con descuento" : "");
        AuditoriaService.registrar(AuditoriaService.VENTAS, "Venta registrada", Formato.numeroFactura(ventaId), null, detalle);

        return new Resultado(ventaDAO.obtenerFactura(ventaId), cambio == null ? 0 : cambio);
    }

    public List<VentaDAO.Venta> listar() {
        return ventaDAO.obtenerTodasLasVentas();
    }

    public Factura obtenerFactura(int ventaId) {
        return ventaDAO.obtenerFactura(ventaId);
    }

    /**
     * Anula una factura y devuelve sus piezas al inventario.
     * Solo se puede mientras su turno de caja siga abierto; después se corrige con una devolución.
     */
    public void anular(int ventaId, String motivo) throws ReglaNegocioException {
        permisos.exigir(Permiso.VENTAS_ANULAR);
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new ReglaNegocioException("Escribe el motivo de la anulación.");
        }
        Factura f = ventaDAO.obtenerFactura(ventaId);
        if (f == null) throw new ReglaNegocioException("La factura ya no existe.");
        if (f.isAnulada()) throw new ReglaNegocioException("Esta factura ya está anulada.");
        if (f.getDevueltoTotal() > 0) {
            throw new ReglaNegocioException("Esta factura tiene devoluciones registradas y no se puede anular.");
        }
        if (f.getAbonado() > 0) {
            throw new ReglaNegocioException("Esta factura tiene abonos cobrados y no se puede anular.");
        }
        Integer orden = new com.taller.repuestos.dao.OrdenTrabajoDAO().ordenDeVenta(ventaId);
        if (orden != null) {
            throw new ReglaNegocioException("Esta factura corresponde a la orden de trabajo " + Formato.numeroOrden(orden)
                    + " (los repuestos ya se instalaron). Para corregirla usa una devolución.");
        }
        if (f.getCajaId() != null) {
            Integer abierta = cajaService.idCajaAbierta();
            if (abierta == null || !abierta.equals(f.getCajaId())) {
                throw new ReglaNegocioException("Esta factura pertenece a un turno de caja que ya se cerró. Para corregirla usa una devolución.");
            }
        }
        String fecha = LocalDateTime.now().format(FORMATO_FECHA);
        if (!ventaDAO.anular(ventaId, Sesion.nombreActual(), fecha, motivo.trim())) {
            throw new ReglaNegocioException("No se pudo anular la factura.");
        }
        AuditoriaService.registrar(AuditoriaService.VENTAS, "Anuló la factura", Formato.numeroFactura(ventaId),
                "Total: " + Formato.moneda(f.getTotal()) + "; pago: " + f.getMetodoPago(), "Anulada. Motivo: " + motivo.trim());
    }

    private static String trimDecimales(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
