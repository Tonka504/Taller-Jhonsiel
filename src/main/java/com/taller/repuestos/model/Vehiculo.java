package com.taller.repuestos.model;

/** Vehículo de un cliente. Un cliente puede tener varios. */
public class Vehiculo {
    private int id;
    private int clienteId;
    private String clienteNombre;
    private String marca;
    private String modelo;
    private Integer anio;
    private String placa;
    private String vin;
    private String motor;
    private String cilindrada;
    private String combustible;
    private String transmision;
    private String color;
    private Integer kilometraje;
    private String observaciones;
    private boolean activo = true;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getClienteId() { return clienteId; }
    public void setClienteId(int clienteId) { this.clienteId = clienteId; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public Integer getAnio() { return anio; }
    public void setAnio(Integer anio) { this.anio = anio; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }
    public String getMotor() { return motor; }
    public void setMotor(String motor) { this.motor = motor; }
    public String getCilindrada() { return cilindrada; }
    public void setCilindrada(String cilindrada) { this.cilindrada = cilindrada; }
    public String getCombustible() { return combustible; }
    public void setCombustible(String combustible) { this.combustible = combustible; }
    public String getTransmision() { return transmision; }
    public void setTransmision(String transmision) { this.transmision = transmision; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Integer getKilometraje() { return kilometraje; }
    public void setKilometraje(Integer kilometraje) { this.kilometraje = kilometraje; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    /** "Toyota Hilux 2007 · HAA-1234" */
    public String getDescripcion() {
        StringBuilder sb = new StringBuilder();
        sb.append(nz(marca)).append(' ').append(nz(modelo));
        if (anio != null) sb.append(' ').append(anio);
        if (motor != null && !motor.isEmpty()) sb.append(' ').append(motor);
        if (placa != null && !placa.isEmpty()) sb.append(" · ").append(placa);
        return sb.toString().trim();
    }

    @Override
    public String toString() { return getDescripcion(); }

    private static String nz(String s) { return s == null ? "" : s; }
}
