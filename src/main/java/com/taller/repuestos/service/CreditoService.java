package com.taller.repuestos.service;

import com.taller.repuestos.dao.VentaDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Factura;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Sesion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Cobro de abonos a las ventas hechas a crédito. */
public class CreditoService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final java.util.List<String> METODOS = java.util.List.of("Efectivo", "Tarjeta", "Transferencia");

    private final VentaDAO ventaDAO = new VentaDAO();
    private final CajaService cajaService;
    private final PermisoService permisos = PermisoService.get();

    public CreditoService(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    /** Registra un abono y devuelve el saldo que queda por cobrar de esa venta. */
    public double registrarAbono(int ventaId, double monto, String metodo) throws ReglaNegocioException {
        permisos.exigir(Permiso.COBROS_REGISTRAR);
        if (Double.isNaN(monto) || Double.isInfinite(monto) || monto <= 0) {
            throw new ReglaNegocioException("El abono debe ser mayor que cero.");
        }
        if (metodo == null || !METODOS.contains(metodo)) {
            throw new ReglaNegocioException("Elige cómo pagó el cliente.");
        }
        Factura f = ventaDAO.obtenerFactura(ventaId);
        if (f == null || !f.isCredito() || f.isAnulada()) {
            throw new ReglaNegocioException("Esta venta no es un crédito vigente.");
        }
        double saldo = f.getSaldoPendiente();
        double montoFinal = Math.round(monto * 100.0) / 100.0;
        if (montoFinal > saldo + 0.005) {
            throw new ReglaNegocioException("El abono (" + Formato.moneda(montoFinal) + ") es mayor que el saldo pendiente ("
                    + Formato.moneda(saldo) + ").");
        }
        Integer cajaId = cajaService.idCajaAbierta();
        if (cajaId == null && ConfiguracionService.get().exigirCajaAbierta()) {
            throw new ReglaNegocioException("La caja está cerrada. Ábrela para registrar el cobro.");
        }
        String fecha = LocalDateTime.now().format(FORMATO_FECHA);
        if (!ventaDAO.registrarAbono(ventaId, fecha, montoFinal, metodo, Sesion.nombreActual(), cajaId)) {
            throw new ReglaNegocioException("No se pudo registrar el abono.");
        }
        double nuevoSaldo = Math.round((saldo - montoFinal) * 100.0) / 100.0;
        AuditoriaService.registrar(AuditoriaService.CLIENTES, "Registró un abono", Formato.numeroFactura(ventaId),
                "Saldo: " + Formato.moneda(saldo), "Abono " + Formato.moneda(montoFinal) + " (" + metodo + "); saldo: " + Formato.moneda(nuevoSaldo));
        return nuevoSaldo;
    }
}
