package com.taller.repuestos.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Fotografías de productos. Se copian a la carpeta "fotos" junto a la base de datos y en el producto
 * solo se guarda el nombre del archivo (así los respaldos y el traslado del sistema siguen funcionando).
 */
public final class Fotos {

    public static final long TAMANO_MAXIMO = 5L * 1024 * 1024; // 5 MB

    private static final String CARPETA = System.getProperty("user.dir") + File.separator + "fotos";

    private Fotos() {}

    /** true si la extensión es de una imagen que JavaFX puede mostrar. */
    public static boolean esImagen(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    /**
     * Copia la imagen elegida a la carpeta de fotos con un nombre basado en el código del producto.
     * @return el nombre del archivo guardado
     */
    public static String guardar(File origen, String codigo) throws IOException {
        if (!origen.isFile()) throw new IOException("El archivo no existe.");
        if (!esImagen(origen)) throw new IOException("Elige una imagen PNG, JPG, GIF o BMP.");
        if (origen.length() > TAMANO_MAXIMO) throw new IOException("La imagen pesa más de 5 MB. Elige una más liviana.");
        File carpeta = new File(CARPETA);
        if (!carpeta.exists() && !carpeta.mkdirs()) throw new IOException("No se pudo crear la carpeta " + carpeta.getAbsolutePath());
        String nombre = origen.getName();
        String ext = nombre.substring(nombre.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        String base = (codigo == null || codigo.isBlank() ? "producto" : codigo.trim()).replaceAll("[^A-Za-z0-9_-]", "_");
        String destino = base + "_" + System.currentTimeMillis() + ext;
        Files.copy(origen.toPath(), new File(carpeta, destino).toPath(), StandardCopyOption.REPLACE_EXISTING);
        return destino;
    }

    /** Archivo de la foto, o null si el producto no tiene o el archivo ya no existe. */
    public static File archivo(String nombre) {
        if (nombre == null || nombre.isBlank()) return null;
        File f = new File(CARPETA, nombre);
        return f.isFile() ? f : null;
    }
}
