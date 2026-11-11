package com.taller.repuestos.model;

/** Un vehículo con el que es compatible un repuesto (marca, modelo, rango de años, motor...). */
public class Compatibilidad {
    private final int id;
    private final int repuestoId;
    private final String marcaVehiculo;
    private final String modelo;
    private final Integer anioDesde;   // null = sin límite inferior
    private final Integer anioHasta;   // null = sin límite superior
    private final String motor;
    private final String combustible;
    private final String transmision;
    private final String observaciones;

    public Compatibilidad(int id, int repuestoId, String marcaVehiculo, String modelo, Integer anioDesde,
                          Integer anioHasta, String motor, String combustible, String transmision, String observaciones) {
        this.id = id;
        this.repuestoId = repuestoId;
        this.marcaVehiculo = marcaVehiculo;
        this.modelo = modelo;
        this.anioDesde = anioDesde;
        this.anioHasta = anioHasta;
        this.motor = motor;
        this.combustible = combustible;
        this.transmision = transmision;
        this.observaciones = observaciones;
    }

    public int getId() { return id; }
    public int getRepuestoId() { return repuestoId; }
    public String getMarcaVehiculo() { return marcaVehiculo; }
    public String getModelo() { return modelo; }
    public Integer getAnioDesde() { return anioDesde; }
    public Integer getAnioHasta() { return anioHasta; }
    public String getMotor() { return motor; }
    public String getCombustible() { return combustible; }
    public String getTransmision() { return transmision; }
    public String getObservaciones() { return observaciones; }

    /** true si el año está dentro del rango de esta compatibilidad (un límite vacío no restringe). */
    public boolean cubreAnio(int anio) {
        return (anioDesde == null || anio >= anioDesde) && (anioHasta == null || anio <= anioHasta);
    }

    /** Años en texto: "2005–2008", "desde 2005", "hasta 2008" o "todos". */
    public String getAnios() {
        if (anioDesde == null && anioHasta == null) return "todos";
        if (anioDesde != null && anioHasta != null) {
            return anioDesde.equals(anioHasta) ? String.valueOf(anioDesde) : anioDesde + "–" + anioHasta;
        }
        return anioDesde != null ? "desde " + anioDesde : "hasta " + anioHasta;
    }

    /** Texto corto para listados. Ejemplo: "Toyota Hilux 2005–2008 2.7L". */
    public String getResumen() {
        StringBuilder sb = new StringBuilder();
        sb.append(nz(marcaVehiculo)).append(' ').append(nz(modelo));
        if (anioDesde != null || anioHasta != null) sb.append(' ').append(getAnios());
        if (!nz(motor).isEmpty()) sb.append(' ').append(motor.trim());
        return sb.toString().trim();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
