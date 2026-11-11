package com.taller.repuestos.model;

/** Un pago o abono guardado en la base de datos. */
public class PagoRegistro {
    private final int id;
    private final int ventaId;
    private final String fecha;
    private final double monto;
    private final String metodo;
    private final String tipo;
    private final String usuario;

    public PagoRegistro(int id, int ventaId, String fecha, double monto, String metodo, String tipo, String usuario) {
        this.id = id;
        this.ventaId = ventaId;
        this.fecha = fecha;
        this.monto = monto;
        this.metodo = metodo;
        this.tipo = tipo;
        this.usuario = usuario;
    }

    public int getId() { return id; }
    public int getVentaId() { return ventaId; }
    public String getFecha() { return fecha; }
    public double getMonto() { return monto; }
    public String getMetodo() { return metodo; }
    public String getTipo() { return tipo; }
    public String getUsuario() { return usuario; }
}
