package com.taller.repuestos.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Orden de trabajo del taller: cliente + vehículo + diagnóstico + servicios (mano de obra) + repuestos.
 * Los repuestos salen del inventario al "confirmar consumo"; al facturar se genera una venta ligada a la orden.
 */
public class OrdenTrabajo {

    public static final String RECIBIDO = "RECIBIDO";
    public static final String DIAGNOSTICO = "DIAGNOSTICO";
    public static final String EN_REPARACION = "EN_REPARACION";
    public static final String LISTO = "LISTO";
    public static final String ENTREGADO = "ENTREGADO";
    public static final String CANCELADO = "CANCELADO";

    /** Estados en los que la orden sigue en el taller y se puede trabajar. */
    public static final List<String> ESTADOS_ABIERTOS = List.of(RECIBIDO, DIAGNOSTICO, EN_REPARACION, LISTO);
    public static final List<String> ESTADOS = List.of(RECIBIDO, DIAGNOSTICO, EN_REPARACION, LISTO, ENTREGADO, CANCELADO);

    public static String etiqueta(String estado) {
        if (estado == null) return "";
        return switch (estado) {
            case RECIBIDO -> "Recibido";
            case DIAGNOSTICO -> "Diagnóstico";
            case EN_REPARACION -> "En reparación";
            case LISTO -> "Listo";
            case ENTREGADO -> "Entregado";
            case CANCELADO -> "Cancelado";
            default -> estado;
        };
    }

    /** Mano de obra o servicio. */
    public static class Servicio {
        public int id;
        public String descripcion;
        public String mecanico;
        public double horas;
        public double precio;

        public int getId() { return id; }
        public String getDescripcion() { return descripcion; }
        public String getMecanico() { return mecanico; }
        public double getHoras() { return horas; }
        public double getPrecio() { return precio; }
    }

    /** Repuesto usado en la orden. */
    public static class RepuestoUsado {
        public int id;
        public int repuestoId;
        public String codigo;
        public String descripcion;
        public int cantidad;
        public double precioUnitario;
        public double costoUnitario;
        public boolean consumido;

        public int getId() { return id; }
        public String getCodigo() { return codigo; }
        public String getDescripcion() { return descripcion; }
        public int getCantidad() { return cantidad; }
        public double getPrecioUnitario() { return precioUnitario; }
        public boolean isConsumido() { return consumido; }
        public double getSubtotal() { return Math.round(cantidad * precioUnitario * 100.0) / 100.0; }
    }

    private int id;
    private int clienteId;
    private String clienteNombre;
    private int vehiculoId;
    private String vehiculoDescripcion;
    private String fechaIngreso;
    private String fechaPromesa;
    private String fechaEntrega;
    private String estado = RECIBIDO;
    private Integer kilometraje;
    private String problema;
    private String diagnostico;
    private String observaciones;
    private String mecanico;
    private String usuario;
    private Integer ventaId;
    private double totalCalculado; // para listados (sin cargar las líneas)
    private final List<Servicio> servicios = new ArrayList<>();
    private final List<RepuestoUsado> repuestos = new ArrayList<>();

    public double getTotalServicios() {
        double t = 0;
        for (Servicio s : servicios) t += s.precio;
        return Math.round(t * 100.0) / 100.0;
    }

    public double getTotalRepuestos() {
        double t = 0;
        for (RepuestoUsado r : repuestos) t += r.getSubtotal();
        return Math.round(t * 100.0) / 100.0;
    }

    /** Total antes de impuestos (el impuesto se calcula al facturar, igual que en el punto de venta). */
    public double getTotal() {
        if (servicios.isEmpty() && repuestos.isEmpty()) return totalCalculado;
        return Math.round((getTotalServicios() + getTotalRepuestos()) * 100.0) / 100.0;
    }

    public boolean isAbierta() { return ESTADOS_ABIERTOS.contains(estado); }
    public boolean isFacturada() { return ventaId != null; }
    /** Se pueden agregar o quitar servicios y repuestos. */
    public boolean isEditable() { return isAbierta() && !isFacturada(); }
    public boolean tieneRepuestosSinConsumir() {
        for (RepuestoUsado r : repuestos) if (!r.consumido) return true;
        return false;
    }

    public String getEstadoEtiqueta() { return etiqueta(estado); }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getClienteId() { return clienteId; }
    public void setClienteId(int clienteId) { this.clienteId = clienteId; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
    public int getVehiculoId() { return vehiculoId; }
    public void setVehiculoId(int vehiculoId) { this.vehiculoId = vehiculoId; }
    public String getVehiculoDescripcion() { return vehiculoDescripcion; }
    public void setVehiculoDescripcion(String v) { this.vehiculoDescripcion = v; }
    public String getFechaIngreso() { return fechaIngreso; }
    public void setFechaIngreso(String fechaIngreso) { this.fechaIngreso = fechaIngreso; }
    public String getFechaPromesa() { return fechaPromesa; }
    public void setFechaPromesa(String fechaPromesa) { this.fechaPromesa = fechaPromesa; }
    public String getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(String fechaEntrega) { this.fechaEntrega = fechaEntrega; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Integer getKilometraje() { return kilometraje; }
    public void setKilometraje(Integer kilometraje) { this.kilometraje = kilometraje; }
    public String getProblema() { return problema; }
    public void setProblema(String problema) { this.problema = problema; }
    public String getDiagnostico() { return diagnostico; }
    public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public String getMecanico() { return mecanico; }
    public void setMecanico(String mecanico) { this.mecanico = mecanico; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public Integer getVentaId() { return ventaId; }
    public void setVentaId(Integer ventaId) { this.ventaId = ventaId; }
    public void setTotalCalculado(double totalCalculado) { this.totalCalculado = totalCalculado; }
    public List<Servicio> getServicios() { return servicios; }
    public List<RepuestoUsado> getRepuestos() { return repuestos; }
}
