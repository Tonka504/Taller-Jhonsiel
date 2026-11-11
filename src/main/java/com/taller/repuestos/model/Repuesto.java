package com.taller.repuestos.model;

public class Repuesto {
    private int id;
    private String codigo;
    private String descripcion;
    private String marca;
    private String aplicacion; // Texto libre sobre a qué se aplica (la compatibilidad estructurada está en la tabla de compatibilidades)
    private double costo;
    private double precio;
    private int stock;
    private int ventasContador; // Contador para productos más vendidos
    private int stockMinimo;    // Debajo de este número, se considera "stock bajo"
    private String proveedor;   // A quién se le compra este producto
    private String categoria;       // Motor, Frenos, Filtros... (catálogo de categorías)
    private String codigoBarras;    // Lo que lee el lector (si está vacío se usa el código)
    private String ubicacion;       // Dónde está en la bodega: "Estante A-03"
    private double precioMayorista; // 0 = sin precio mayorista
    private int stockMaximo;        // 0 = sin máximo definido
    private String unidad = "Unidad";
    private String foto;            // nombre del archivo en la carpeta "fotos" (null = sin foto)

    /** Constructor original (compatibilidad con código existente): usa stockMinimo=5 y proveedor vacío por defecto. */
    public Repuesto(int id, String codigo, String descripcion, String marca, String aplicacion, double costo, double precio, int stock) {
        this(id, codigo, descripcion, marca, aplicacion, costo, precio, stock, 5, "");
    }

    public Repuesto(int id, String codigo, String descripcion, String marca, String aplicacion, double costo, double precio, int stock, int stockMinimo, String proveedor) {
        this.id = id;
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.marca = marca;
        this.aplicacion = aplicacion;
        this.costo = costo;
        this.precio = precio;
        this.stock = stock;
        this.ventasContador = 0;
        this.stockMinimo = stockMinimo;
        this.proveedor = proveedor;
    }

    // --- Getters y Setters ---
    public int getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public String getMarca() { return marca; }
    public String getAplicacion() { return aplicacion; }
    public double getCosto() { return costo; }
    public double getPrecio() { return precio; }
    public int getStock() { return stock; }
    public int getVentasContador() { return ventasContador; }
    public int getStockMinimo() { return stockMinimo; }
    public String getProveedor() { return proveedor; }

    public void setId(int id) { this.id = id; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public void setMarca(String marca) { this.marca = marca; }
    public void setAplicacion(String aplicacion) { this.aplicacion = aplicacion; }
    public void setCosto(double costo) { this.costo = costo; }
    public void setPrecio(double precio) { this.precio = precio; }
    public void setStock(int stock) { this.stock = stock; }
    public void setVentasContador(int ventasContador) { this.ventasContador = ventasContador; }
    public void setStockMinimo(int stockMinimo) { this.stockMinimo = stockMinimo; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }
    public void incrementarVentas(int cantidad) { this.ventasContador += cantidad; }

    public String getCategoria() { return categoria; }
    public String getCodigoBarras() { return codigoBarras; }
    public String getUbicacion() { return ubicacion; }
    public double getPrecioMayorista() { return precioMayorista; }
    public int getStockMaximo() { return stockMaximo; }
    public String getUnidad() { return unidad; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
    public void setPrecioMayorista(double precioMayorista) { this.precioMayorista = precioMayorista; }
    public void setStockMaximo(int stockMaximo) { this.stockMaximo = stockMaximo; }
    public void setUnidad(String unidad) { this.unidad = unidad; }
    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }

    /** Lo que debe leer el lector de códigos de este producto: el código de barras si lo tiene, si no el código. */
    public String getCodigoParaLector() {
        return codigoBarras != null && !codigoBarras.trim().isEmpty() ? codigoBarras.trim() : codigo;
    }

    /** true si el stock actual está en o por debajo del mínimo configurado. */
    public boolean isStockBajo() {
        return stock <= stockMinimo;
    }
}
