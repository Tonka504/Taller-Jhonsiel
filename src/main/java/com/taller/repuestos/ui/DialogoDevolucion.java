package com.taller.repuestos.ui;

import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Devolucion;
import com.taller.repuestos.model.Factura;
import com.taller.repuestos.model.LineaFactura;
import com.taller.repuestos.service.DevolucionService;
import com.taller.repuestos.util.Formato;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/** Selección de lo que el cliente devuelve de una factura. */
public final class DialogoDevolucion {

    private DialogoDevolucion() {}

    /** Devuelve la devolución registrada, o null si se canceló. */
    public static Devolucion mostrar(Factura f, DevolucionService servicio) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Devolución");
        dialogo.setHeaderText("Devolución de la factura " + Formato.numeroFactura(f.getId())
                + " (total " + Formato.moneda(f.getTotal()) + ")");

        ButtonType btnRegistrar = new ButtonType("Registrar devolución", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(btnRegistrar, ButtonType.CANCEL);

        Map<Integer, Spinner<Integer>> spinners = new LinkedHashMap<>();
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        grid.add(new Label("Producto"), 0, 0);
        grid.add(new Label("Vendidas"), 1, 0);
        grid.add(new Label("Ya devueltas"), 2, 0);
        grid.add(new Label("Devolver"), 3, 0);

        int fila = 1;
        for (LineaFactura l : f.getLineas()) {
            grid.add(new Label(l.getDescripcion()), 0, fila);
            grid.add(new Label(String.valueOf(l.getCantidad())), 1, fila);
            grid.add(new Label(String.valueOf(l.getCantidadDevuelta())), 2, fila);
            if (l.getDisponibleDevolver() > 0) {
                Spinner<Integer> spinner = new Spinner<>(0, l.getDisponibleDevolver(), 0);
                spinner.setPrefWidth(90);
                spinners.put(l.getId(), spinner);
                grid.add(spinner, 3, fila);
            } else {
                grid.add(new Label("-"), 3, fila);
            }
            fila++;
        }

        TextField txtMotivo = new TextField();
        txtMotivo.setPromptText("Motivo de la devolución (obligatorio)");
        Label lblResumen = new Label("Elige las cantidades a devolver.");
        lblResumen.setWrapText(true);
        lblResumen.getStyleClass().add("metric-value");

        Runnable actualizar = () -> {
            Map<Integer, Integer> cantidades = leerCantidades(spinners);
            double monto = servicio.calcularMonto(f, cantidades);
            lblResumen.setText(monto > 0
                    ? "Se devolverán " + Formato.moneda(monto) + ". " + servicio.describirReembolso(f, monto)
                    : "Elige las cantidades a devolver.");
        };
        for (Spinner<Integer> s : spinners.values()) {
            s.valueProperty().addListener((obs, anterior, nuevo) -> actualizar.run());
        }

        VBox contenido = new VBox(12, grid, txtMotivo, lblResumen);
        contenido.setPadding(new Insets(12));
        contenido.setPrefWidth(520);
        dialogo.getDialogPane().setContent(contenido);

        Alertas.estilizar(dialogo);
        Button okBtn = (Button) dialogo.getDialogPane().lookupButton(btnRegistrar);
        okBtn.getStyleClass().add("primary-button");

        final Devolucion[] resultado = new Devolucion[1];
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                resultado[0] = servicio.registrar(f.getId(), leerCantidades(spinners), txtMotivo.getText());
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo registrar la devolución", ex.getMessage());
                ev.consume();
            }
        });

        dialogo.showAndWait();
        return resultado[0];
    }

    private static Map<Integer, Integer> leerCantidades(Map<Integer, Spinner<Integer>> spinners) {
        Map<Integer, Integer> cantidades = new LinkedHashMap<>();
        for (Map.Entry<Integer, Spinner<Integer>> e : spinners.entrySet()) {
            Integer valor = e.getValue().getValue();
            cantidades.put(e.getKey(), valor == null ? 0 : valor);
        }
        return cantidades;
    }
}
