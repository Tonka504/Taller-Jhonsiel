package com.taller.repuestos.model;

/** Proveedor de repuestos. El total comprado y el saldo se calculan con sus compras. */
public class Proveedor {
    private int id;
    private String nombre;
    private String empresa;
    private String identificacionFiscal;
    private String telefono;
    private String direccion;
    private String correo;
    private String contacto;
    private boolean activo = true;
    private String observaciones;

    // Calculados al consultar
    private double totalComprado;
    private double saldoPendiente;
    private int cantidadCompras;

    public Proveedor() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }
    public String getIdentificacionFiscal() { return identificacionFiscal; }
    public void setIdentificacionFiscal(String v) { this.identificacionFiscal = v; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public double getTotalComprado() { return totalComprado; }
    public void setTotalComprado(double totalComprado) { this.totalComprado = totalComprado; }
    public double getSaldoPendiente() { return saldoPendiente; }
    public void setSaldoPendiente(double saldoPendiente) { this.saldoPendiente = saldoPendiente; }
    public int getCantidadCompras() { return cantidadCompras; }
    public void setCantidadCompras(int cantidadCompras) { this.cantidadCompras = cantidadCompras; }

    public String getEstado() { return activo ? "Activo" : "Inactivo"; }

    @Override
    public String toString() { return nombre; }
}
