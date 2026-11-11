package com.taller.repuestos.model;

import java.util.List;

public class Cliente {
    public static final List<String> TIPOS = List.of("Minorista", "Mayorista", "Taller / Empresa");

    private final int id;
    private final String nombre;
    private final String identificacion;
    private final String telefono;
    private final String direccion;
    private final String correo;
    private final String tipo;
    private final boolean activo;
    private final double limiteCredito;
    private final String notas;
    private double saldoPendiente;

    public Cliente(int id, String nombre, String identificacion, String telefono, String direccion,
                   String correo, String tipo, boolean activo, double limiteCredito, String notas) {
        this.id = id;
        this.nombre = nombre;
        this.identificacion = identificacion;
        this.telefono = telefono;
        this.direccion = direccion;
        this.correo = correo;
        this.tipo = tipo;
        this.activo = activo;
        this.limiteCredito = limiteCredito;
        this.notas = notas;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public String getIdentificacion() { return identificacion; }
    public String getTelefono() { return telefono; }
    public String getDireccion() { return direccion; }
    public String getCorreo() { return correo; }
    public String getTipo() { return tipo; }
    public boolean isActivo() { return activo; }
    public double getLimiteCredito() { return limiteCredito; }
    public String getNotas() { return notas; }
    public double getSaldoPendiente() { return saldoPendiente; }
    public void setSaldoPendiente(double saldoPendiente) { this.saldoPendiente = saldoPendiente; }

    public String getEstado() { return activo ? "Activo" : "Inactivo"; }

    /** Crédito que todavía puede usar (límite menos lo que debe). */
    public double getCreditoDisponible() { return Math.max(0, limiteCredito - saldoPendiente); }

    @Override
    public String toString() { return nombre; }
}
