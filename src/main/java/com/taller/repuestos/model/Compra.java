package com.taller.repuestos.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Compra / orden de compra a un proveedor.
 * Flujo: PENDIENTE (orden creada) → PARCIAL (llegó una parte) → RECIBIDA (llegó todo), o ANULADA.
 * Al recibir mercadería aumenta el inventario y se actualiza el costo de los productos.
 */
public class Compra {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String PARCIAL = "PARCIAL";
    public static final String RECIBIDA = "RECIBIDA";
    public static final String ANULADA = "ANULADA";

    public static final List<String> CONDICIONES = List.of("Contado", "Crédito");

    /** Una línea de la compra. */
    public static class Linea {
        public int id;
        public int repuestoId;
        public String codigo;
        public String descripcion;
        public int cantidad;
        public int cantidadRecibida;
        public int cantidadDevuelta;
        public double costoUnitario;
        public double descuento; // monto total de descuento de la línea

        public int getId() { return id; }
        public int getRepuestoId() { return repuestoId; }
        public String getCodigo() { return codigo; }
        public String getDescripcion() { return descripcion; }
        public int getCantidad() { return cantidad; }
        public int getCantidadRecibida() { return cantidadRecibida; }
        public int getCantidadDevuelta() { return cantidadDevuelta; }
        public double getCostoUnitario() { return costoUnitario; }
        public double getDescuento() { return descuento; }

        public double getBruto() { return redondear(cantidad * costoUnitario); }
        public double getSubtotal() { return redondear(getBruto() - descuento); }
        /** Costo por unidad ya con el descuento de la línea (es el que pasa al producto). */
        public double getCostoNeto() { return cantidad <= 0 ? costoUnitario : redondear(getSubtotal() / cantidad); }
        public int getPendiente() { return Math.max(0, cantidad - cantidadRecibida); }
        /** Unidades que todavía se pueden devolver al proveedor. */
        public int getDevolvible() { return Math.max(0, cantidadRecibida - cantidadDevuelta); }
    }

    private int id;
    private int proveedorId;
    private String proveedorNombre;
    private String fecha;
    private String documentoProveedor;
    private String estado = PENDIENTE;
    private String condicionPago = "Contado";
    private String fechaVencimiento;
    private double subtotal;
    private double descuento;
    private double impuestoPct;
    private double impuesto;
    private double total;
    private String usuario;
    private String fechaRecepcion;
    private String observaciones;
    private String motivoAnulacion;
    private String anuladaPor;
    private double pagado;
    private double devuelto;
    private final List<Linea> lineas = new ArrayList<>();

    /** Recalcula subtotal, descuento, impuesto y total a partir de las líneas y el % de impuesto. */
    public void recalcular() {
        double bruto = 0, desc = 0;
        for (Linea l : lineas) {
            bruto += l.getBruto();
            desc += l.descuento;
        }
        subtotal = redondear(bruto);
        descuento = redondear(desc);
        impuesto = redondear((subtotal - descuento) * impuestoPct / 100.0);
        total = redondear(subtotal - descuento + impuesto);
    }

    public boolean isAnulada() { return ANULADA.equals(estado); }

    /** Lo que se le debe al proveedor: total menos pagos y devoluciones (las órdenes sin recibir no generan deuda). */
    public double getSaldo() {
        if (isAnulada() || PENDIENTE.equals(estado)) return 0;
        return Math.max(0, redondear(total - pagado - devuelto));
    }

    public String getEstadoEtiqueta() { return etiquetaEstado(estado); }

    public static String etiquetaEstado(String estado) {
        if (estado == null) return "";
        return switch (estado) {
            case PENDIENTE -> "Pendiente";
            case PARCIAL -> "Recibida parcial";
            case RECIBIDA -> "Recibida";
            case ANULADA -> "Anulada";
            default -> estado;
        };
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getProveedorId() { return proveedorId; }
    public void setProveedorId(int proveedorId) { this.proveedorId = proveedorId; }
    public String getProveedorNombre() { return proveedorNombre; }
    public void setProveedorNombre(String proveedorNombre) { this.proveedorNombre = proveedorNombre; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public String getDocumentoProveedor() { return documentoProveedor; }
    public void setDocumentoProveedor(String documentoProveedor) { this.documentoProveedor = documentoProveedor; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getCondicionPago() { return condicionPago; }
    public void setCondicionPago(String condicionPago) { this.condicionPago = condicionPago; }
    public String getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(String fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public double getDescuento() { return descuento; }
    public void setDescuento(double descuento) { this.descuento = descuento; }
    public double getImpuestoPct() { return impuestoPct; }
    public void setImpuestoPct(double impuestoPct) { this.impuestoPct = impuestoPct; }
    public double getImpuesto() { return impuesto; }
    public void setImpuesto(double impuesto) { this.impuesto = impuesto; }
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getFechaRecepcion() { return fechaRecepcion; }
    public void setFechaRecepcion(String fechaRecepcion) { this.fechaRecepcion = fechaRecepcion; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public void setMotivoAnulacion(String motivoAnulacion) { this.motivoAnulacion = motivoAnulacion; }
    public String getAnuladaPor() { return anuladaPor; }
    public void setAnuladaPor(String anuladaPor) { this.anuladaPor = anuladaPor; }
    public double getPagado() { return pagado; }
    public void setPagado(double pagado) { this.pagado = pagado; }
    public double getDevuelto() { return devuelto; }
    public void setDevuelto(double devuelto) { this.devuelto = devuelto; }
    public List<Linea> getLineas() { return lineas; }

    private static double redondear(double v) { return Math.round(v * 100.0) / 100.0; }
}
