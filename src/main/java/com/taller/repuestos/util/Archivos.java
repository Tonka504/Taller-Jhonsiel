package com.taller.repuestos.util;

import java.io.File;

/** Utilidades para archivos generados por el sistema (PDF de facturas, etiquetas, órdenes de compra). */
public final class Archivos {

    private Archivos() {}

    /** Abre el archivo con el visor del sistema. Si no se puede, no pasa nada: el archivo ya quedó guardado. */
    public static void abrir(File archivo) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(archivo);
            }
        } catch (Exception ignorada) {
            // sin visor disponible
        }
    }
}
