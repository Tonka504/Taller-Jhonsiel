package com.taller.repuestos.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cotización (presupuesto) para un cliente. No mueve inventario ni caja: cuando el cliente acepta,
 * se carga en el punto de venta y al cobrarla queda CONVERTIDA (ligada a su factura).
 */
public class Cotizacion {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String CONVERTIDA = "CONVERTIDA";
    public static final String ANULADA = "ANULADA";

    public static class Linea {
        public int id;
        public int repuestoId;
        public String codigo;
        public String descripcion;
        public int cantidad;
        public double precioUnitario;
        public double descuentoPct;
        public double subtotal;

        public String getCodigo() { return codigo; }
        public String getDescripcion() { return descripcion; }
        public int getCantidad() { return cantidad; }
        public double getPrecioUnitario() { return precioUnitario; }
        public double getDescuentoPct() { return descuentoPct; }
        public double getSubtotal() { return subtotal; }
    }

    private int id;
    private String fecha;
    private String fechaVencimiento;
    private Integer clienteId;
    private String clienteNombre;
    private String vendedor;
    private double subtotal;
    private double descuento;
    private String tipoDescuento;
    private double descuentoValor;
    private double impuesto;
    private double total;
    private String estado = PENDIENTE;
    private Integer ventaId;
    private String observaciones;
    private final List<Linea> lineas = new ArrayList<>();

    /** Pendiente pero con la fecha de validez ya pasada. */
    public boolean isVencida() {
        return PENDIENTE.equals(estado) && fechaVencimiento != null
                && fechaVencimiento.compareTo(LocalDate.now().toString()) < 0;
    }

    public boolean isPendiente() { return PENDIENTE.equals(estado); }

    public String getEstadoEtiqueta() {
        if (isVencida()) return "Vencida";
        return switch (estado) {
            case PENDIENTE -> "Pendiente";
            case CONVERTIDA -> "Vendida";
            case ANULADA -> "Anulada";
            default -> estado;
        };
    }

    public String getClienteMostrar() {
        return clienteNombre == null || clienteNombre.isBlank() ? "Consumidor final" : clienteNombre;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public String getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(String fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
    public Integer getClienteId() { return clienteId; }
    public void setClienteId(Integer clienteId) { this.clienteId = clienteId; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
    public String getVendedor() { return vendedor; }
    public void setVendedor(String vendedor) { this.vendedor = vendedor; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public double getDescuento() { return descuento; }
    public void setDescuento(double descuento) { this.descuento = descuento; }
    public String getTipoDescuento() { return tipoDescuento; }
    public void setTipoDescuento(String tipoDescuento) { this.tipoDescuento = tipoDescuento; }
    public double getDescuentoValor() { return descuentoValor; }
    public void setDescuentoValor(double descuentoValor) { this.descuentoValor = descuentoValor; }
    public double getImpuesto() { return impuesto; }
    public void setImpuesto(double impuesto) { this.impuesto = impuesto; }
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Integer getVentaId() { return ventaId; }
    public void setVentaId(Integer ventaId) { this.ventaId = ventaId; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public List<Linea> getLineas() { return lineas; }
}
