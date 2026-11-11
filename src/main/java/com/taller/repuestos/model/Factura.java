package com.taller.repuestos.model;

import java.util.ArrayList;
import java.util.List;

/** Una venta completa, lista para mostrarla, imprimirla, anularla o devolver parte de ella. */
public class Factura {
    public static final String EMITIDA = "EMITIDA";
    public static final String ANULADA = "ANULADA";
    public static final String CREDITO = "Crédito";

    private int id;
    private String fecha;
    private String vendedor;
    private Integer clienteId;
    private String clienteNombre;
    private String clienteIdentificacion;
    private String metodoPago;
    private String estado;
    private double subtotal;
    private double descuento;
    private String tipoDescuento;
    private double descuentoValor;
    private double impuesto;
    private double total;
    private Double montoRecibido;
    private Double cambio;
    private String fechaLimite;
    private Integer cajaId;
    private String motivoAnulacion;
    private String anuladaPor;
    private double abonado;
    private double devueltoTotal;
    private double devueltoSaldo;
    private final List<LineaFactura> lineas = new ArrayList<>();

    public Factura() {}

    public int getId() { return id; }
    public String getFecha() { return fecha; }
    public String getVendedor() { return vendedor; }
    public Integer getClienteId() { return clienteId; }
    public String getClienteNombre() { return clienteNombre; }
    public String getClienteIdentificacion() { return clienteIdentificacion; }
    public String getMetodoPago() { return metodoPago; }
    public String getEstado() { return estado; }
    public double getSubtotal() { return subtotal; }
    public double getDescuento() { return descuento; }
    public String getTipoDescuento() { return tipoDescuento; }
    public double getDescuentoValor() { return descuentoValor; }
    public double getImpuesto() { return impuesto; }
    public double getTotal() { return total; }
    public Double getMontoRecibido() { return montoRecibido; }
    public Double getCambio() { return cambio; }
    public String getFechaLimite() { return fechaLimite; }
    public Integer getCajaId() { return cajaId; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public String getAnuladaPor() { return anuladaPor; }
    public double getAbonado() { return abonado; }
    public double getDevueltoTotal() { return devueltoTotal; }
    public double getDevueltoSaldo() { return devueltoSaldo; }
    public List<LineaFactura> getLineas() { return lineas; }

    public boolean isAnulada() { return ANULADA.equals(estado); }
    public boolean isCredito() { return CREDITO.equals(metodoPago); }

    /** Lo que todavía debe el cliente por esta venta (0 si no fue a crédito o ya la pagó). */
    public double getSaldoPendiente() {
        if (!isCredito() || isAnulada()) return 0;
        return Math.max(0, Math.round((total - abonado - devueltoSaldo) * 100.0) / 100.0);
    }

    /** Lo máximo que todavía se puede devolver en dinero de esta factura. */
    public double getPendienteDevolver() {
        return Math.max(0, Math.round((total - devueltoTotal) * 100.0) / 100.0);
    }

    public void setId(int v) { id = v; }
    public void setFecha(String v) { fecha = v; }
    public void setVendedor(String v) { vendedor = v; }
    public void setClienteId(Integer v) { clienteId = v; }
    public void setClienteNombre(String v) { clienteNombre = v; }
    public void setClienteIdentificacion(String v) { clienteIdentificacion = v; }
    public void setMetodoPago(String v) { metodoPago = v; }
    public void setEstado(String v) { estado = v; }
    public void setSubtotal(double v) { subtotal = v; }
    public void setDescuento(double v) { descuento = v; }
    public void setTipoDescuento(String v) { tipoDescuento = v; }
    public void setDescuentoValor(double v) { descuentoValor = v; }
    public void setImpuesto(double v) { impuesto = v; }
    public void setTotal(double v) { total = v; }
    public void setMontoRecibido(Double v) { montoRecibido = v; }
    public void setCambio(Double v) { cambio = v; }
    public void setFechaLimite(String v) { fechaLimite = v; }
    public void setCajaId(Integer v) { cajaId = v; }
    public void setMotivoAnulacion(String v) { motivoAnulacion = v; }
    public void setAnuladaPor(String v) { anuladaPor = v; }
    public void setAbonado(double v) { abonado = v; }
    public void setDevueltoTotal(double v) { devueltoTotal = v; }
    public void setDevueltoSaldo(double v) { devueltoSaldo = v; }
}
