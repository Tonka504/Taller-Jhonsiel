package com.taller.repuestos.service;

import com.taller.repuestos.dao.CajaDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Caja;
import com.taller.repuestos.model.MovimientoCaja;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Sesion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Reglas de la caja: apertura, movimientos de efectivo y cierre. */
public class CajaService {

    /** Foto de los números de un turno de caja en este momento. */
    public static class ResumenCaja {
        public final Caja caja;
        public final double ventasEfectivo;
        public final double ventasTarjeta;
        public final double ventasTransferencia;
        public final double ventasCredito;
        public final int cantidadVentas;
        public final double abonosEfectivo;
        public final double entradas;
        public final double retiros;
        public final double gastos;
        public final double devoluciones;
        public final double efectivoEsperado;

        /**
         * @param ventas     [efectivo, tarjeta, transferencia, cantidad, crédito]
         * @param abonos     [efectivo, tarjeta, transferencia]
         * @param movimientos [entradas, retiros, gastos, devoluciones en efectivo]
         */
        ResumenCaja(Caja caja, double[] ventas, double[] abonos, double[] movimientos) {
            this.caja = caja;
            this.ventasEfectivo = redondear(ventas[0]);
            this.ventasTarjeta = redondear(ventas[1]);
            this.ventasTransferencia = redondear(ventas[2]);
            this.cantidadVentas = (int) ventas[3];
            this.ventasCredito = redondear(ventas[4]);
            this.abonosEfectivo = redondear(abonos[0]);
            this.entradas = redondear(movimientos[0]);
            this.retiros = redondear(movimientos[1]);
            this.gastos = redondear(movimientos[2]);
            this.devoluciones = redondear(movimientos[3]);
            // Efectivo que debería haber: fondo + ventas y abonos en efectivo + entradas - salidas - devoluciones en efectivo
            this.efectivoEsperado = redondear(caja.getMontoInicial() + ventasEfectivo + abonosEfectivo
                    + entradas - retiros - gastos - devoluciones);
        }

        /** Todo lo vendido en el turno (incluye lo vendido a crédito). */
        public double getTotalVentas() {
            return redondear(ventasEfectivo + ventasTarjeta + ventasTransferencia + ventasCredito);
        }
    }

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CajaDAO dao = new CajaDAO();
    private final PermisoService permisos = PermisoService.get();

    public Caja cajaAbierta() {
        return dao.obtenerCajaAbierta();
    }

    /** Id de la caja abierta, o null si no hay ninguna. */
    public Integer idCajaAbierta() {
        Caja c = cajaAbierta();
        return c == null ? null : c.getId();
    }

    /** true si se puede vender ahora (hay caja abierta, o la configuración no la exige). */
    public boolean puedeVender() {
        return !ConfiguracionService.get().exigirCajaAbierta() || cajaAbierta() != null;
    }

    public CajaDAO.Apertura abrir(double montoInicial) throws ReglaNegocioException {
        permisos.exigir(Permiso.CAJA_ABRIR);
        if (Double.isNaN(montoInicial) || Double.isInfinite(montoInicial) || montoInicial < 0) {
            throw new ReglaNegocioException("El fondo inicial no puede ser negativo.");
        }
        Caja abierta = cajaAbierta();
        if (abierta != null) {
            throw new ReglaNegocioException("Ya hay una caja abierta (abierta por "
                    + abierta.getUsuarioApertura() + " el " + abierta.getFechaApertura() + ").");
        }
        double monto = redondear(montoInicial);
        CajaDAO.Apertura apertura = dao.abrir(Sesion.nombreActual(), monto, ahora());
        if (apertura == null) {
            throw new ReglaNegocioException("No se pudo abrir la caja.");
        }
        String detalle = "Fondo inicial: " + Formato.moneda(monto);
        if (apertura.ventasIncluidas > 0) {
            detalle += "; ventas de hoy sin caja incluidas: " + apertura.ventasIncluidas;
        }
        AuditoriaService.registrar(AuditoriaService.CAJA, "Apertura de caja", "Caja N° " + apertura.cajaId, null, detalle);
        return apertura;
    }

    public ResumenCaja resumen(Caja caja) {
        return new ResumenCaja(caja, dao.totalesVentas(caja.getId()), dao.totalesAbonos(caja.getId()),
                dao.totalesMovimientos(caja.getId()));
    }

    /** Resumen de la caja abierta, o null si no hay ninguna. */
    public ResumenCaja resumenActual() {
        Caja c = cajaAbierta();
        return c == null ? null : resumen(c);
    }

    public void registrarMovimiento(MovimientoCaja.Tipo tipo, double monto, String motivo, String documento)
            throws ReglaNegocioException {
        permisos.exigir(Permiso.CAJA_MOVIMIENTOS);
        Caja caja = cajaAbierta();
        if (caja == null) {
            throw new ReglaNegocioException("No hay una caja abierta. Abre la caja para registrar movimientos.");
        }
        if (tipo == null || !tipo.isManual()) {
            throw new ReglaNegocioException("Elige el tipo de movimiento.");
        }
        if (Double.isNaN(monto) || Double.isInfinite(monto) || monto <= 0) {
            throw new ReglaNegocioException("El monto debe ser mayor que cero.");
        }
        String motivoLimpio = motivo == null ? "" : motivo.trim();
        if (motivoLimpio.isEmpty()) {
            throw new ReglaNegocioException("Escribe el motivo del movimiento.");
        }
        double montoFinal = redondear(monto);
        if (!tipo.isIngreso()) {
            double disponible = resumen(caja).efectivoEsperado;
            if (montoFinal > disponible) {
                throw new ReglaNegocioException("No hay suficiente efectivo en la caja. Disponible: " + Formato.moneda(disponible) + ".");
            }
        }
        String doc = documento == null || documento.trim().isEmpty() ? null : documento.trim();
        if (!dao.registrarMovimiento(caja.getId(), ahora(), Sesion.nombreActual(), tipo.name(), montoFinal, motivoLimpio, doc)) {
            throw new ReglaNegocioException("No se pudo registrar el movimiento.");
        }
        AuditoriaService.registrar(AuditoriaService.CAJA, tipo.getEtiqueta(), "Caja N° " + caja.getId(), null,
                Formato.moneda(montoFinal) + " - " + motivoLimpio + (doc != null ? " (doc. " + doc + ")" : ""));
    }

    /** Cierra la caja abierta y devuelve el turno ya cerrado, con todos sus totales. */
    public Caja cerrar(double efectivoContado, String observaciones) throws ReglaNegocioException {
        permisos.exigir(Permiso.CAJA_CERRAR);
        Caja caja = cajaAbierta();
        if (caja == null) {
            throw new ReglaNegocioException("No hay una caja abierta para cerrar.");
        }
        if (Double.isNaN(efectivoContado) || Double.isInfinite(efectivoContado) || efectivoContado < 0) {
            throw new ReglaNegocioException("El efectivo contado no puede ser negativo.");
        }
        ResumenCaja r = resumen(caja);
        double contado = redondear(efectivoContado);

        caja.setUsuarioCierre(Sesion.nombreActual());
        caja.setFechaCierre(ahora());
        caja.setTotalEfectivo(r.ventasEfectivo);
        caja.setTotalTarjeta(r.ventasTarjeta);
        caja.setTotalTransferencia(r.ventasTransferencia);
        caja.setTotalSistema(r.getTotalVentas());
        caja.setEntradas(r.entradas);
        caja.setRetiros(r.retiros);
        caja.setGastos(r.gastos);
        caja.setTotalCredito(r.ventasCredito);
        caja.setAbonosEfectivo(r.abonosEfectivo);
        caja.setDevoluciones(r.devoluciones);
        caja.setEfectivoEsperado(r.efectivoEsperado);
        caja.setEfectivoContado(contado);
        caja.setDiferencia(redondear(contado - r.efectivoEsperado));
        caja.setObservaciones(observaciones == null || observaciones.trim().isEmpty() ? null : observaciones.trim());

        if (!dao.cerrar(caja)) {
            throw new ReglaNegocioException("No se pudo cerrar la caja. Puede que ya esté cerrada.");
        }
        caja.setEstado(Caja.CERRADA);
        AuditoriaService.registrar(AuditoriaService.CAJA, "Cierre de caja", "Caja N° " + caja.getId(),
                "Esperado: " + Formato.moneda(r.efectivoEsperado),
                "Contado: " + Formato.moneda(contado) + "; diferencia: " + Formato.moneda(caja.getDiferencia()));
        return caja;
    }

    public List<MovimientoCaja> movimientos(int cajaId) {
        return dao.obtenerMovimientos(cajaId);
    }

    public List<Caja> historial(int limite) {
        return dao.obtenerHistorial(limite);
    }

    private static String ahora() {
        return LocalDateTime.now().format(FORMATO_FECHA);
    }

    static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
