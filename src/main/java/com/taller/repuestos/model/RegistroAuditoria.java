package com.taller.repuestos.model;

/** Una línea del historial de auditoría: quién hizo qué, cuándo y sobre qué registro. */
public class RegistroAuditoria {
    private int id;
    private String fecha;
    private String usuario;
    private String modulo;
    private String accion;
    private String registro;
    private String valorAnterior;
    private String valorNuevo;

    public RegistroAuditoria(int id, String fecha, String usuario, String modulo, String accion,
                             String registro, String valorAnterior, String valorNuevo) {
        this.id = id;
        this.fecha = fecha;
        this.usuario = usuario;
        this.modulo = modulo;
        this.accion = accion;
        this.registro = registro;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
    }

    public int getId() { return id; }
    public String getFecha() { return fecha; }
    public String getUsuario() { return usuario; }
    public String getModulo() { return modulo; }
    public String getAccion() { return accion; }
    public String getRegistro() { return registro; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNuevo() { return valorNuevo; }
}
