package com.taller.repuestos.model;

public class Usuario {
    private int id;
    private String username;
    private String password; // Nunca es la contraseña real: es el hash (PBKDF2) guardado en la base de datos
    private String nombreCompleto;
    private String rol; // "ADMIN" o "VENDEDOR"
    private boolean activo;

    public Usuario(int id, String username, String password, String nombreCompleto, String rol, boolean activo) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
        this.activo = activo;
    }

    public Usuario(int id, String username, String password, String nombreCompleto, String rol) {
        this(id, username, password, nombreCompleto, rol, true);
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getRol() { return rol; }
    public boolean isActivo() { return activo; }

    /** Texto para mostrar en la tabla de usuarios. */
    public String getEstado() { return activo ? "Activo" : "Inactivo"; }
}
