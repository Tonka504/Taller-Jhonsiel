package com.taller.repuestos.model;

public class DetalleVenta {
    private Repuesto repuesto;
    private int cantidad;
    private double subtotal;

    public DetalleVenta(Repuesto repuesto, int cantidad) {
        this.repuesto = repuesto;
        this.cantidad = cantidad;
        this.subtotal = repuesto.getPrecio() * cantidad;
    }

    public Repuesto getRepuesto() { return repuesto; }
    public String getCodigo() { return repuesto.getCodigo(); }
    public String getDescripcion() { return repuesto.getDescripcion(); }
    public double getPrecioUnitario() { return repuesto.getPrecio(); }
    public int getCantidad() { return cantidad; }
    public double getSubtotal() { return subtotal; }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
        this.subtotal = this.repuesto.getPrecio() * cantidad;
    }
}