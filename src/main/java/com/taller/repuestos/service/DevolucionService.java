package com.taller.repuestos.service;

import com.taller.repuestos.dao.DevolucionDAO;
import com.taller.repuestos.dao.VentaDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Devolucion;
import com.taller.repuestos.model.Factura;
import com.taller.repuestos.model.LineaFactura;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Sesion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Devoluciones de parte de una factura: validan las cantidades, reintegran el inventario y ajustan caja o deuda. */
public class DevolucionService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VentaDAO ventaDAO = new VentaDAO();
    private final DevolucionDAO devolucionDAO = new DevolucionDAO();
    private final CajaService cajaService;
    private final PermisoService permisos = PermisoService.get();

    public DevolucionService(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    /** Cuánto se devolvería (en dinero) por las cantidades indicadas. Sirve para mostrarlo antes de confirmar. */
    public double calcularMonto(Factura f, Map<Integer, Integer> cantidadesPorLinea) {
        // La relación total/subtotal reparte proporcionalmente el descuento general y el impuesto
        double razon = f.getSubtotal() > 0 ? f.getTotal() / f.getSubtotal() : 1.0;
        double suma = 0;
        for (LineaFactura l : f.getLineas()) {
            Integer cantidad = cantidadesPorLinea.get(l.getId());
            if (cantidad == null || cantidad <= 0 || l.getCantidad() <= 0) continue;
            double neto = l.getSubtotal() * cantidad / l.getCantidad();
            suma += Math.round(neto * razon * 100.0) / 100.0;
        }
        suma = Math.round(suma * 100.0) / 100.0;
        return Math.min(suma, f.getPendienteDevolver()); // nunca más de lo cobrado en la factura
    }

    /** Explica, antes de confirmar, qué pasará con el dinero de la devolución. */
    public String describirReembolso(Factura f, double monto) {
        if (monto <= 0) return "";
        if (f.isCredito()) {
            double paraSaldo = Math.min(monto, f.getSaldoPendiente());
            double resto = Math.round((monto - paraSaldo) * 100.0) / 100.0;
            String texto = Formato.moneda(paraSaldo) + " se descuentan de la deuda del cliente";
            return resto > 0.005 ? texto + " y " + Formato.moneda(resto) + " se devuelven en efectivo." : texto + ".";
        }
        if ("Tarjeta".equals(f.getMetodoPago())) return Formato.moneda(monto) + " se reembolsan a la tarjeta del cliente.";
        if ("Transferencia".equals(f.getMetodoPago())) return Formato.moneda(monto) + " se reembolsan por transferencia.";
        return Formato.moneda(monto) + " se devuelven en efectivo (salen de la caja).";
    }

    public Devolucion registrar(int ventaId, Map<Integer, Integer> cantidadesPorLinea, String motivo) throws ReglaNegocioException {
        permisos.exigir(Permiso.DEVOLUCIONES_REALIZAR);
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new ReglaNegocioException("Escribe el motivo de la devolución.");
        }
        Factura f = ventaDAO.obtenerFactura(ventaId);
        if (f == null) throw new ReglaNegocioException("La factura ya no existe.");
        if (f.isAnulada()) throw new ReglaNegocioException("No se puede devolver nada de una factura anulada.");

        Devolucion d = new Devolucion();
        d.setVentaId(ventaId);
        d.setUsuario(Sesion.nombreActual());
        d.setFecha(LocalDateTime.now().format(FORMATO_FECHA));
        d.setMotivo(motivo.trim());

        boolean alguna = false;
        for (LineaFactura l : f.getLineas()) {
            Integer cantidad = cantidadesPorLinea.get(l.getId());
            if (cantidad == null || cantidad == 0) continue;
            if (cantidad < 0) throw new ReglaNegocioException("No se pueden devolver cantidades negativas.");
            if (cantidad > l.getDisponibleDevolver()) {
                throw new ReglaNegocioException("De \"" + l.getDescripcion() + "\" solo se pueden devolver "
                        + l.getDisponibleDevolver() + " unidad(es) (vendidas " + l.getCantidad()
                        + ", ya devueltas " + l.getCantidadDevuelta() + ").");
            }
            alguna = true;
            Map<Integer, Integer> solo = Map.of(l.getId(), cantidad);
            d.getLineas().add(new Devolucion.Linea(l.getId(), l.getRepuestoId(), l.getDescripcion(), cantidad, calcularMonto(f, solo)));
        }
        if (!alguna) throw new ReglaNegocioException("Elige al menos un producto y la cantidad a devolver.");

        double monto = calcularMonto(f, cantidadesPorLinea);
        if (monto <= 0) throw new ReglaNegocioException("El monto de la devolución debe ser mayor que cero.");
        d.setMontoTotal(monto);

        // Ventas a crédito: primero se descuenta de la deuda; si ya pagó, el resto se le devuelve en efectivo
        double paraSaldo = 0;
        String forma;
        if (f.isCredito()) {
            paraSaldo = Math.min(monto, f.getSaldoPendiente());
            forma = monto - paraSaldo > 0.005 ? Devolucion.EFECTIVO : Devolucion.SALDO;
        } else if ("Tarjeta".equals(f.getMetodoPago())) {
            forma = Devolucion.TARJETA;
        } else if ("Transferencia".equals(f.getMetodoPago())) {
            forma = Devolucion.TRANSFERENCIA;
        } else {
            forma = Devolucion.EFECTIVO;
        }
        d.setMontoSaldo(Math.round(paraSaldo * 100.0) / 100.0);
        d.setFormaReembolso(forma);

        // Si hay que devolver efectivo, tiene que haber caja abierta con dinero suficiente
        if (Devolucion.EFECTIVO.equals(forma)) {
            CajaService.ResumenCaja caja = cajaService.resumenActual();
            if (caja == null) {
                throw new ReglaNegocioException("Esta devolución se paga en efectivo y la caja está cerrada. Abre la caja primero.");
            }
            if (d.getMontoReembolsado() > caja.efectivoEsperado + 0.005) {
                throw new ReglaNegocioException("No hay suficiente efectivo en la caja para esta devolución. Disponible: "
                        + Formato.moneda(caja.efectivoEsperado) + ".");
            }
            d.setCajaId(caja.caja.getId());
        }

        int id = devolucionDAO.registrar(d, Formato::numeroDevolucion);
        if (id == DevolucionDAO.ERROR_CANTIDAD) {
            throw new ReglaNegocioException("Las cantidades cambiaron (otra devolución se registró antes). Vuelve a abrir la factura.");
        }
        if (id <= 0) throw new ReglaNegocioException("No se pudo registrar la devolución.");
        d.setId(id);

        AuditoriaService.registrar(AuditoriaService.VENTAS, "Devolución registrada", Formato.numeroDevolucion(id),
                "Factura " + Formato.numeroFactura(ventaId),
                "Monto: " + Formato.moneda(monto) + "; " + resumirLineas(d.getLineas()) + "; motivo: " + d.getMotivo());
        return d;
    }

    public List<Devolucion> obtenerPorVenta(int ventaId) {
        return devolucionDAO.obtenerPorVenta(ventaId);
    }

    private static String resumirLineas(List<Devolucion.Linea> lineas) {
        StringBuilder sb = new StringBuilder();
        for (Devolucion.Linea l : lineas) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(l.cantidad).append(" x ").append(l.descripcion);
        }
        return sb.toString();
    }
}
