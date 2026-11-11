package com.taller.repuestos.model;

import java.util.ArrayList;
import java.util.List;

/** Devolución (nota de crédito) de parte de una factura. */
public class Devolucion {
    public static final String EFECTIVO = "EFECTIVO";
    public static final String TARJETA = "TARJETA";
    public static final String TRANSFERENCIA = "TRANSFERENCIA";
    public static final String SALDO = "SALDO";

    /** Producto devuelto dentro de una devolución. */
    public static class Linea {
        public final int detalleId;
        public final int repuestoId;
        public final String descripcion;
        public final int cantidad;
        public final double monto;

        public Linea(int detalleId, int repuestoId, String descripcion, int cantidad, double monto) {
            this.detalleId = detalleId;
            this.repuestoId = repuestoId;
            this.descripcion = descripcion;
            this.cantidad = cantidad;
            this.monto = monto;
        }
    }

    private int id;
    private int ventaId;
    private String fecha;
    private String usuario;
    private String motivo;
    private double montoTotal;
    private double montoSaldo;
    private String formaReembolso;
    private Integer cajaId;
    private final List<Linea> lineas = new ArrayList<>();

    public Devolucion() {}

    public int getId() { return id; }
    public int getVentaId() { return ventaId; }
    public String getFecha() { return fecha; }
    public String getUsuario() { return usuario; }
    public String getMotivo() { return motivo; }
    public double getMontoTotal() { return montoTotal; }
    /** Parte que se descontó de lo que el cliente debía (ventas a crédito). */
    public double getMontoSaldo() { return montoSaldo; }
    /** Parte que se devolvió en dinero. */
    public double getMontoReembolsado() { return Math.round((montoTotal - montoSaldo) * 100.0) / 100.0; }
    public String getFormaReembolso() { return formaReembolso; }
    public Integer getCajaId() { return cajaId; }
    public List<Linea> getLineas() { return lineas; }

    public void setId(int v) { id = v; }
    public void setVentaId(int v) { ventaId = v; }
    public void setFecha(String v) { fecha = v; }
    public void setUsuario(String v) { usuario = v; }
    public void setMotivo(String v) { motivo = v; }
    public void setMontoTotal(double v) { montoTotal = v; }
    public void setMontoSaldo(double v) { montoSaldo = v; }
    public void setFormaReembolso(String v) { formaReembolso = v; }
    public void setCajaId(Integer v) { cajaId = v; }
}
