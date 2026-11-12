package com.taller.repuestos.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.net.URL;

/**
 * Identidad visual de JOHNZIEL: logotipo del negocio, logotipo tipográfico y textos de marca.
 * Las imágenes viven en /img (resources) y se cargan una sola vez.
 */
public final class Marca {

    public static final String NOMBRE = "JOHNZIEL";
    public static final String LEMA = "Automotive Performance & Parts Management";

    private static Image logoChico;
    private static Image logoGrande;

    private Marca() {}

    /** Logotipo circular del negocio, para el menú y la pantalla de acceso. */
    public static ImageView logo(double tamano) {
        Image img = tamano <= 96 ? logoChico() : logoGrande();
        ImageView v = new ImageView(img);
        v.setFitWidth(tamano);
        v.setFitHeight(tamano);
        v.setPreserveRatio(true);
        v.setSmooth(true);
        v.getStyleClass().add("marca-logo");
        return v;
    }

    /** Icono de las ventanas (barra de tareas). */
    public static Image icono() {
        return logoChico();
    }

    /** Logotipo tipográfico: JOHNZIEL con la franja roja deportiva debajo. */
    public static Node logotipo(String claseTexto, double anchoFranja) {
        Label texto = new Label(NOMBRE);
        texto.getStyleClass().add(claseTexto);
        Region franja = new Region();
        franja.getStyleClass().add("marca-franja");
        franja.setPrefWidth(anchoFranja);
        franja.setMaxWidth(anchoFranja);
        VBox v = new VBox(3, texto, franja);
        v.setAlignment(Pos.CENTER_LEFT);
        return v;
    }

    private static Image logoChico() {
        if (logoChico == null) logoChico = cargar("/img/johnziel-logo-128.png");
        return logoChico;
    }

    private static Image logoGrande() {
        if (logoGrande == null) logoGrande = cargar("/img/johnziel-logo-320.png");
        return logoGrande;
    }

    private static Image cargar(String ruta) {
        URL url = Marca.class.getResource(ruta);
        return url == null ? null : new Image(url.toExternalForm(), true);
    }
}
