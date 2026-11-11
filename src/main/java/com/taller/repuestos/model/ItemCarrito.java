package com.taller.repuestos.model;

/** Una línea del carrito de venta: un repuesto, la cantidad y un descuento opcional (en %). */
public class ItemCarrito {
    private final Repuesto repuesto;
    private int cantidad;
    private double descuentoPct;
    private double precioUnitario;

    public ItemCarrito(Repuesto repuesto, int cantidad) {
        this.repuesto = repuesto;
        this.cantidad = cantidad;
        this.precioUnitario = repuesto.getPrecio();
    }

    /** Precio al que se vende esta línea (puede ser el mayorista según el cliente). */
    public double getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(double precioUnitario) { this.precioUnitario = precioUnitario; }

    public Repuesto getRepuesto() { return repuesto; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public double getDescuentoPct() { return descuentoPct; }
    public void setDescuentoPct(double descuentoPct) { this.descuentoPct = descuentoPct; }

    /** Precio por cantidad, sin descuento. */
    public double getBruto() { return redondear(precioUnitario * cantidad); }

    /** Monto del descuento de esta línea. */
    public double getDescuentoMonto() { return redondear(getBruto() * descuentoPct / 100.0); }

    /** Total de la línea ya con su descuento. */
    public double getSubtotal() { return redondear(getBruto() - getDescuentoMonto()); }

    private static double redondear(double v) { return Math.round(v * 100.0) / 100.0; }
}
