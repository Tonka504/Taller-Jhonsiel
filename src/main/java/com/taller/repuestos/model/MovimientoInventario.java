package com.taller.repuestos.model;

/**
 * Un movimiento del kardex: cada vez que cambia la existencia de un producto queda una fila
 * (ventas, compras, devoluciones, ajustes, consumo en órdenes de trabajo...).
 * La cantidad lleva signo: positiva si entra mercadería, negativa si sale.
 */
public class MovimientoInventario {

    public enum Tipo {
        INICIAL("Inventario inicial"),
        ENTRADA("Entrada / reabastecimiento"),
        COMPRA("Recepción de compra"),
        VENTA("Venta"),
        ANULACION("Anulación de venta"),
        DEVOLUCION_CLIENTE("Devolución de cliente"),
        DEVOLUCION_PROVEEDOR("Devolución a proveedor"),
        AJUSTE_ENTRADA("Ajuste (entrada)"),
        AJUSTE_SALIDA("Ajuste (salida)"),
        CORRECCION("Corrección de conteo"),
        ORDEN_TRABAJO("Consumo en orden de trabajo"),
        ORDEN_REINTEGRO("Reintegro de orden de trabajo");

        private final String etiqueta;

        Tipo(String etiqueta) { this.etiqueta = etiqueta; }

        public String getEtiqueta() { return etiqueta; }

        @Override
        public String toString() { return etiqueta; }

        /** Etiqueta legible a partir del nombre guardado en la base de datos. */
        public static String etiquetaDe(String nombre) {
            try {
                return valueOf(nombre).etiqueta;
            } catch (IllegalArgumentException | NullPointerException e) {
                return nombre;
            }
        }
    }

    private final int id;
    private final int repuestoId;
    private final String codigo;
    private final String descripcion;
    private final String fecha;
    private final String tipo;
    private final int cantidad;
    private final Integer stockResultante;
    private final Double costoUnitario;
    private final String documento;
    private final String motivo;
    private final String usuario;

    public MovimientoInventario(int id, int repuestoId, String codigo, String descripcion, String fecha, String tipo,
                                int cantidad, Integer stockResultante, Double costoUnitario,
                                String documento, String motivo, String usuario) {
        this.id = id;
        this.repuestoId = repuestoId;
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.fecha = fecha;
        this.tipo = tipo;
        this.cantidad = cantidad;
        this.stockResultante = stockResultante;
        this.costoUnitario = costoUnitario;
        this.documento = documento;
        this.motivo = motivo;
        this.usuario = usuario;
    }

    public int getId() { return id; }
    public int getRepuestoId() { return repuestoId; }
    public String getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public String getFecha() { return fecha; }
    public String getTipo() { return tipo; }
    public String getTipoEtiqueta() { return Tipo.etiquetaDe(tipo); }
    public int getCantidad() { return cantidad; }
    public Integer getStockResultante() { return stockResultante; }
    public Double getCostoUnitario() { return costoUnitario; }
    public String getDocumento() { return documento; }
    public String getMotivo() { return motivo; }
    public String getUsuario() { return usuario; }
    public boolean isEntrada() { return cantidad > 0; }
}
