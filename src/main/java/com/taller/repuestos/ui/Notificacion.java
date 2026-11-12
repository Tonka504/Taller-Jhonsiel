package com.taller.repuestos.ui;

import com.taller.repuestos.util.Iconos;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Notificaciones discretas (toast) en la esquina inferior derecha de la ventana principal:
 * confirman una operación sin interrumpir con una ventana que haya que cerrar.
 */
public final class Notificacion {

    private static VBox capa;

    private Notificacion() {}

    /**
     * Crea la capa de notificaciones encima del contenido principal.
     * @return el nodo raíz que debe usarse en la escena (contenido + capa)
     */
    public static StackPane instalar(javafx.scene.Parent contenido) {
        capa = new VBox(10);
        capa.setAlignment(Pos.BOTTOM_RIGHT);
        capa.setPadding(new Insets(0, 22, 22, 0));
        capa.setPickOnBounds(false);
        capa.setMouseTransparent(false);
        StackPane raiz = new StackPane(contenido, capa);
        StackPane.setAlignment(capa, Pos.BOTTOM_RIGHT);
        capa.setMaxSize(440, Double.MAX_VALUE);
        return raiz;
    }

    /** true si la ventana principal ya está lista para mostrar notificaciones. */
    public static boolean disponible() {
        return capa != null && capa.getScene() != null && capa.getScene().getWindow() != null
                && capa.getScene().getWindow().isShowing();
    }

    public static void exito(String titulo, String texto) {
        mostrar(titulo, texto, false);
    }

    public static void error(String titulo, String texto) {
        mostrar(titulo, texto, true);
    }

    private static void mostrar(String titulo, String texto, boolean error) {
        if (!disponible()) return;
        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("notificacion-titulo");
        Label lblTexto = new Label(texto == null ? "" : texto);
        lblTexto.getStyleClass().add("notificacion-texto");
        lblTexto.setWrapText(true);
        lblTexto.setMaxWidth(340);
        VBox textos = new VBox(3, lblTitulo, lblTexto);
        HBox toast = new HBox(12, Iconos.crear(error ? Iconos.ALERTA : Iconos.CHECK, 22), textos);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().add("notificacion");
        if (error) toast.getStyleClass().add("error");
        toast.setMaxWidth(420);
        toast.setOpacity(0);

        capa.getChildren().add(toast);
        if (capa.getChildren().size() > 4) capa.getChildren().remove(0);

        FadeTransition entra = new FadeTransition(Duration.millis(200), toast);
        entra.setToValue(1);
        TranslateTransition sube = new TranslateTransition(Duration.millis(220), toast);
        sube.setFromX(40);
        sube.setToX(0);
        int largo = (titulo + (texto == null ? "" : texto)).length();
        PauseTransition espera = new PauseTransition(Duration.millis(Math.min(9000, 3000 + largo * 35L)));
        FadeTransition sale = new FadeTransition(Duration.millis(260), toast);
        sale.setToValue(0);
        SequentialTransition anim = new SequentialTransition(new ParallelTransition(entra, sube), espera, sale);
        anim.setOnFinished(e -> capa.getChildren().remove(toast));
        toast.setOnMouseClicked(e -> {
            anim.stop();
            capa.getChildren().remove(toast);
        });
        anim.play();
    }
}
