package com.taller.repuestos.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.net.URL;

/** Mensajes y diálogos con el mismo aspecto en todo el sistema. */
public final class Alertas {

    private static Window propietario;

    private Alertas() {}

    /** Ventana principal: los mensajes se muestran encima de ella. */
    public static void setPropietario(Window ventana) {
        propietario = ventana;
    }

    public static void advertencia(String titulo, String mensaje) {
        mostrar(Alert.AlertType.WARNING, titulo, mensaje);
    }

    public static void info(String titulo, String mensaje) {
        mostrar(Alert.AlertType.INFORMATION, titulo, mensaje);
    }

    public static void error(String titulo, String mensaje) {
        mostrar(Alert.AlertType.ERROR, titulo, mensaje);
    }

    /** Pregunta Aceptar / Cancelar. Devuelve true si el usuario acepta. */
    public static boolean confirmar(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, mensaje, ButtonType.OK, ButtonType.CANCEL);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        estilizar(alerta);
        return alerta.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /** Pide un texto corto al usuario. Devuelve null si cancela. */
    public static String pedirTexto(String titulo, String mensaje, String valorInicial) {
        Dialog<String> dialogo = new Dialog<>();
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(mensaje);
        ButtonType aceptar = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(aceptar, ButtonType.CANCEL);

        TextField campo = new TextField(valorInicial == null ? "" : valorInicial);
        campo.setPrefWidth(340);
        VBox contenido = new VBox(campo);
        contenido.setPadding(new Insets(15));
        dialogo.getDialogPane().setContent(contenido);
        estilizar(dialogo);
        Platform.runLater(campo::requestFocus);

        dialogo.setResultConverter(boton -> boton == aceptar ? campo.getText() : null);
        return dialogo.showAndWait().orElse(null);
    }

    /** Aplica la hoja de estilos del sistema y la ventana principal como dueña del diálogo. */
    public static void estilizar(Dialog<?> dialogo) {
        URL hoja = Alertas.class.getResource("/styles.css");
        if (hoja != null) {
            dialogo.getDialogPane().getStylesheets().add(hoja.toExternalForm());
        }
        if (propietario != null) {
            dialogo.initOwner(propietario);
        }
    }

    private static void mostrar(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        estilizar(alerta);
        alerta.showAndWait();
    }
}
