package com.taller.repuestos.util;

import com.taller.repuestos.model.Usuario;

/** Usuario que tiene la sesión iniciada. Los servicios lo usan para la auditoría y los permisos. */
public final class Sesion {
    private static Usuario usuario;

    private Sesion() {}

    public static void iniciar(Usuario u) { usuario = u; }
    public static void cerrar() { usuario = null; }
    public static Usuario getUsuario() { return usuario; }

    /** Nombre para mostrar en registros y auditoría. */
    public static String nombreActual() {
        return usuario == null ? "Sistema" : usuario.getNombreCompleto();
    }
}
