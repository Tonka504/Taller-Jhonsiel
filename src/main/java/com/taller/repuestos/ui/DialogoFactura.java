package com.taller.repuestos.ui;

import com.taller.repuestos.model.Devolucion;
import com.taller.repuestos.model.Factura;
import com.taller.repuestos.util.Archivos;
import com.taller.repuestos.util.FacturaPDF;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.TicketTexto;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Muestra el ticket de una factura o de una devolución, con opción de guardarlo en PDF. */
public final class DialogoFactura {

    private interface Generador {
        File generar() throws IOException;
    }

    private DialogoFactura() {}

    public static void mostrar(Factura f, List<Devolucion> devoluciones) {
        abrir("Factura " + Formato.numeroFactura(f.getId()), TicketTexto.factura(f, devoluciones),
                () -> FacturaPDF.generarFactura(f, devoluciones));
    }

    public static void mostrarDevolucion(Factura f, Devolucion d) {
        abrir("Devolución " + Formato.numeroDevolucion(d.getId()), TicketTexto.devolucion(f, d),
                () -> FacturaPDF.generarDevolucion(f, d));
    }

    private static void abrir(String titulo, String texto, Generador generador) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(titulo);
        dialogo.setHeaderText("Comprobante");

        ButtonType btnPdf = new ButtonType("Guardar PDF", ButtonBar.ButtonData.APPLY);
        dialogo.getDialogPane().getButtonTypes().addAll(btnPdf, ButtonType.CLOSE);

        TextArea area = new TextArea();
        area.setText(texto);
        area.setEditable(false);
        area.setPrefSize(440, 520);
        area.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 12px;"); // el ticket se alinea por columnas
        dialogo.getDialogPane().setContent(area);

        Alertas.estilizar(dialogo);
        Button botonPdf = (Button) dialogo.getDialogPane().lookupButton(btnPdf);
        botonPdf.getStyleClass().add("primary-button");

        // Guardar el PDF no cierra el comprobante
        botonPdf.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume();
            try {
                File archivo = generador.generar();
                Alertas.info("PDF guardado", "Se guardó en:\n" + archivo.getAbsolutePath());
                Archivos.abrir(archivo);
            } catch (IOException | RuntimeException ex) {
                ex.printStackTrace();
                Alertas.error("No se pudo crear el PDF", "Detalle: " + ex.getMessage());
            }
        });

        dialogo.showAndWait();
    }
}
