package com.taller.repuestos.model;

import java.util.ArrayList;
import java.util.List;

/** Roles del sistema. En la base de datos se guarda el nombre (ADMIN, VENDEDOR...). */
public enum Rol {
    ADMIN("Administrador"),
    VENDEDOR("Vendedor"),
    CAJERO("Cajero"),
    BODEGUERO("Bodeguero"),
    MECANICO("Mecánico");

    private final String etiqueta;

    Rol(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }

    public static boolean esValido(String texto) {
        if (texto == null) return false;
        for (Rol r : values()) if (r.name().equals(texto)) return true;
        return false;
    }

    /** Nombres de todos los roles, en el orden en que se muestran en los combos. */
    public static List<String> nombres() {
        List<String> lista = new ArrayList<>();
        for (Rol r : values()) lista.add(r.name());
        return lista;
    }
}
