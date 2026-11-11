package com.taller.repuestos.model;

/** Un turno de caja: se abre con un fondo inicial y se cierra contando el efectivo. */
public class Caja {
    public static final String ABIERTA = "ABIERTA";
    public static final String CERRADA = "CERRADA";

    private int id;
    private String estado;
    private String usuarioApertura;
    private String fechaApertura;
    private double montoInicial;
    private String usuarioCierre;
    private String fechaCierre;
    private double totalEfectivo;
    private double totalTarjeta;
    private double totalTransferencia;
    private double totalSistema;
    private double entradas;
    private double retiros;
    private double gastos;
    private double totalCredito;
    private double abonosEfectivo;
    private double devoluciones;
    private double efectivoEsperado;
    private double efectivoContado;
    private double diferencia;
    private String observaciones;

    public Caja() {}

    public int getId() { return id; }
    public String getEstado() { return estado; }
    public String getUsuarioApertura() { return usuarioApertura; }
    public String getFechaApertura() { return fechaApertura; }
    public double getMontoInicial() { return montoInicial; }
    public String getUsuarioCierre() { return usuarioCierre; }
    public String getFechaCierre() { return fechaCierre; }
    public double getTotalEfectivo() { return totalEfectivo; }
    public double getTotalTarjeta() { return totalTarjeta; }
    public double getTotalTransferencia() { return totalTransferencia; }
    public double getTotalSistema() { return totalSistema; }
    public double getEntradas() { return entradas; }
    public double getRetiros() { return retiros; }
    public double getGastos() { return gastos; }
    public double getTotalCredito() { return totalCredito; }
    public double getAbonosEfectivo() { return abonosEfectivo; }
    public double getDevoluciones() { return devoluciones; }
    public double getEfectivoEsperado() { return efectivoEsperado; }
    public double getEfectivoContado() { return efectivoContado; }
    public double getDiferencia() { return diferencia; }
    public String getObservaciones() { return observaciones; }
    public boolean isAbierta() { return ABIERTA.equals(estado); }

    public void setId(int id) { this.id = id; }
    public void setEstado(String estado) { this.estado = estado; }
    public void setUsuarioApertura(String v) { this.usuarioApertura = v; }
    public void setFechaApertura(String v) { this.fechaApertura = v; }
    public void setMontoInicial(double v) { this.montoInicial = v; }
    public void setUsuarioCierre(String v) { this.usuarioCierre = v; }
    public void setFechaCierre(String v) { this.fechaCierre = v; }
    public void setTotalEfectivo(double v) { this.totalEfectivo = v; }
    public void setTotalTarjeta(double v) { this.totalTarjeta = v; }
    public void setTotalTransferencia(double v) { this.totalTransferencia = v; }
    public void setTotalSistema(double v) { this.totalSistema = v; }
    public void setEntradas(double v) { this.entradas = v; }
    public void setRetiros(double v) { this.retiros = v; }
    public void setGastos(double v) { this.gastos = v; }
    public void setTotalCredito(double v) { this.totalCredito = v; }
    public void setAbonosEfectivo(double v) { this.abonosEfectivo = v; }
    public void setDevoluciones(double v) { this.devoluciones = v; }
    public void setEfectivoEsperado(double v) { this.efectivoEsperado = v; }
    public void setEfectivoContado(double v) { this.efectivoContado = v; }
    public void setDiferencia(double v) { this.diferencia = v; }
    public void setObservaciones(String v) { this.observaciones = v; }
}
