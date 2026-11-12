package com.taller.repuestos.util;

import javafx.scene.Group;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

/**
 * Iconos vectoriales (SVG) para la interfaz. Todos están dibujados en una
 * cuadrícula de 24x24 y se pintan desde el CSS con la clase ".icono"
 * (por ejemplo: ".tool-button:hover .icono { -fx-fill: #E84A5F; }").
 */
public final class Iconos {

    private Iconos() {}

    /** Llave inglesa (identidad del sistema). */
    public static final String LLAVE =
        "M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7"
        + "C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0"
        + "l2.3-2.3c.5-.4.5-1.1.1-1.4z";

    /** Engranaje. */
    public static final String ENGRANAJE =
        "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58"
        + "c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96"
        + "c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84"
        + "c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96"
        + "c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58"
        + "c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61"
        + "l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54"
        + "c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94"
        + "l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58z"
        + "M12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";

    /** Tuerca hexagonal (repuestos). */
    public static final String TUERCA =
        "M12 2L21 7V17L12 22L3 17V7Z"
        + "M12 8.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z";

    /** Caja de herramientas (inventario). */
    public static final String CAJA =
        "M20 6h-4V4c0-1.11-.89-2-2-2h-4c-1.11 0-2 .89-2 2v2H4c-1.11 0-1.99.89-1.99 2"
        + "L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2z"
        + "M10 4h4v2h-4z";

    /** Factura / recibo. */
    public static final String FACTURA =
        "M18 17H6v-2h12v2zm0-4H6v-2h12v2zm0-4H6V7h12v2z"
        + "M3 22l1.5-1.5L6 22l1.5-1.5L9 22l1.5-1.5L12 22l1.5-1.5L15 22l1.5-1.5L18 22"
        + "l1.5-1.5L21 22V2l-1.5 1.5L18 2l-1.5 1.5L15 2l-1.5 1.5L12 2l-1.5 1.5L9 2"
        + "L7.5 3.5 6 2 4.5 3.5 3 2v20z";

    /** Portapapeles (órdenes / historial). */
    public static final String PORTAPAPELES =
        "M19 3h-4.18C14.4 1.84 13.3 1 12 1c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v14"
        + "c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"
        + "m-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1z"
        + "m2 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z";

    /** Gráfica de barras (reportes). */
    public static final String GRAFICA =
        "M5 9.2h3V19H5V9.2zM10.6 5h2.8v14h-2.8V5zm5.6 8H19v6h-2.8v-6z";

    /** Persona (usuario). */
    public static final String USUARIO =
        "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4z"
        + "m0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";

    /** Lupa (buscador). */
    public static final String BUSCAR =
        "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5"
        + "S5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5z"
        + "m-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";

    /** Grupo de personas (clientes). */
    public static final String CLIENTES =
        "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0"
        + "c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5"
        + "V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45"
        + "V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z";

    /** Billetera (caja / dinero). */
    public static final String BILLETERA =
        "M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9"
        + "c-1.11 0-2 .9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5"
        + "s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z";

    /** Reloj con flecha (auditoría / historial de acciones). */
    public static final String HISTORIAL =
        "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7"
        + "-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21"
        + "c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z";

    /** Carrito (compras). */
    public static final String CARRITO =
        "M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45"
        + "c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45"
        + "c.75 0 1.41-.41 1.75-1.03l3.58-6.49A1.003 1.003 0 0020 4H5.21l-.94-2H1z"
        + "m16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z";

    /** Camión (proveedores). */
    public static final String CAMION =
        "M20 8h-3V4H3c-1.1 0-2 .9-2 2v11h2c0 1.66 1.34 3 3 3s3-1.34 3-3h6c0 1.66 1.34 3 3 3s3-1.34 3-3h2v-5l-3-4z"
        + "M6 18.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"
        + "m13.5-9l1.96 2.5H17V9.5h2.5zm-1.5 9c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z";

    /** Automóvil (vehículos). */
    public static final String AUTO =
        "M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1"
        + "c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99z"
        + "M6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16z"
        + "m11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z";

    /** Marca de verificación (operación exitosa). */
    public static final String CHECK = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";

    /** Mosaico de tarjetas (vista de productos en cuadrícula). */
    public static final String MOSAICO = "M4 4h7v7H4zm9 0h7v7h-7zM4 13h7v7H4zm9 0h7v7h-7z";

    /** Lista (vista de productos en tabla). */
    public static final String LISTA = "M3 5h18v2H3zm0 6h18v2H3zm0 6h18v2H3z";

    /** Triángulo de advertencia (alertas). */
    public static final String ALERTA = "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z";

    /** Panel de mosaicos (dashboard). */
    public static final String PANEL = "M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z";

    /** Flechas arriba/abajo (movimientos de inventario / kardex). */
    public static final String MOVIMIENTOS =
        "M16 17.01V10h-2v7.01h-3L15 21l4-3.99h-3zM9 3L5 6.99h3V14h2V6.99h3L9 3z";

    /** Destornillador (órdenes de trabajo del taller). */
    public static final String DESTORNILLADOR =
        "M20.7 3.3a1 1 0 0 0-1.4 0l-4.6 4.6-1-1-1.4 1.4 1 1L5.4 17.2 4 21l3.8-1.4 7.9-7.9 1 1 1.4-1.4-1-1 4.6-4.6"
        + "a1 1 0 0 0 0-1.4z";

    /**
     * Crea un icono listo para usar como "graphic" de un Button, Label, etc.
     * @param contenido path SVG (una de las constantes de esta clase)
     * @param tamano    tamaño en píxeles (el icono base mide 24x24)
     */
    public static Group crear(String contenido, double tamano) {
        SVGPath path = new SVGPath();
        path.setContent(contenido);
        path.setFillRule(FillRule.EVEN_ODD);
        path.getStyleClass().add("icono");
        double escala = tamano / 24.0;
        path.setScaleX(escala);
        path.setScaleY(escala);
        // El Group hace que el layout tome en cuenta la escala aplicada
        return new Group(path);
    }
}
