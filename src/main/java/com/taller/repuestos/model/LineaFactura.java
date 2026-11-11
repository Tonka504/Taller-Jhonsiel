package com.taller.repuestos.model;

/** Una línea de una factura ya emitida (con lo que ya se devolvió de ella). */
public class LineaFactura {
    private final int id;
    private final int repuestoId;
    private final String descripcion;
    private final int cantidad;
    private final double precioUnitario;
    private final double descuentoPct;
    private final double descuento;
    private final double subtotal;
    private final int cantidadDevuelta;

    public LineaFactura(int id, int repuestoId, String descripcion, int cantidad, double precioUnitario,
                        double descuentoPct, double descuento, double subtotal, int cantidadDevuelta) {
        this.id = id;
        this.repuestoId = repuestoId;
        this.descripcion = descripcion;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.descuentoPct = descuentoPct;
        this.descuento = descuento;
        this.subtotal = subtotal;
        this.cantidadDevuelta = cantidadDevuelta;
    }

    public int getId() { return id; }
    public int getRepuestoId() { return repuestoId; }
    public String getDescripcion() { return descripcion; }
    public int getCantidad() { return cantidad; }
    public double getPrecioUnitario() { return precioUnitario; }
    public double getDescuentoPct() { return descuentoPct; }
    public double getDescuento() { return descuento; }
    public double getSubtotal() { return subtotal; }
    public int getCantidadDevuelta() { return cantidadDevuelta; }

    /** Unidades que todavía se pueden devolver. */
    public int getDisponibleDevolver() { return cantidad - cantidadDevuelta; }
}
