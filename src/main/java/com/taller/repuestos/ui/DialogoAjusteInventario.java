package com.taller.repuestos.ui;

import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.MovimientoInventario;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.service.InventarioService;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Entrada, salida o corrección manual de existencias, siempre con motivo (queda en el kardex y la auditoría). */
public final class DialogoAjusteInventario {

    private DialogoAjusteInventario() {}

    /** Devuelve true si se registró el ajuste. */
    public static boolean mostrar(InventarioService servicio, Repuesto r) {
        Dialog<Boolean> d = new Dialog<>();
        d.setTitle("Ajuste de inventario");
        d.setHeaderText(r.getCodigo() + " · " + r.getDescripcion() + "\nExistencia actual: " + r.getStock() + " " + r.getUnidad());
        ButtonType registrar = new ButtonType("Registrar ajuste", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(registrar, ButtonType.CANCEL);

        ComboBox<MovimientoInventario.Tipo> comboTipo = new ComboBox<>();
        comboTipo.getItems().addAll(InventarioService.TIPOS_AJUSTE);
        comboTipo.setValue(MovimientoInventario.Tipo.AJUSTE_SALIDA);
        TextField txtCantidad = new TextField();
        txtCantidad.setPromptText("Unidades");
        Label lblCantidad = new Label("Cantidad a restar:");
        TextField txtMotivo = new TextField();
        txtMotivo.setPromptText("Ej: pieza dañada, conteo físico, muestra, extravío...");
        Label lblResultado = new Label();
        lblResultado.getStyleClass().add("metric-value");

        Runnable actualizar = () -> {
            MovimientoInventario.Tipo t = comboTipo.getValue();
            lblCantidad.setText(t == MovimientoInventario.Tipo.CORRECCION ? "Existencia contada:"
                    : t == MovimientoInventario.Tipo.AJUSTE_ENTRADA ? "Cantidad a sumar:" : "Cantidad a restar:");
            try {
                int c = Integer.parseInt(txtCantidad.getText().trim());
                int resultado = t == MovimientoInventario.Tipo.CORRECCION ? c
                        : t == MovimientoInventario.Tipo.AJUSTE_ENTRADA ? r.getStock() + c : r.getStock() - c;
                lblResultado.setText("Quedarán " + resultado + " unidades (" + (resultado - r.getStock() >= 0 ? "+" : "")
                        + (resultado - r.getStock()) + ")");
                lblResultado.getStyleClass().removeAll("valor-ok", "valor-alerta");
                lblResultado.getStyleClass().add(resultado < 0 ? "valor-alerta" : "valor-ok");
            } catch (NumberFormatException e) {
                lblResultado.setText("");
            }
        };
        comboTipo.setOnAction(e -> actualizar.run());
        txtCantidad.textProperty().addListener((o, a, n) -> actualizar.run());

        GridPane grid = Componentes.formulario();
        int f = 0;
        f = Componentes.campo(grid, f, "Tipo de ajuste:", comboTipo);
        grid.add(lblCantidad, 0, f);
        grid.add(txtCantidad, 1, f++);
        Componentes.campo(grid, f, "Motivo:", txtMotivo);
        VBox contenido = new VBox(12, grid, lblResultado);
        contenido.setPrefWidth(440);
        d.getDialogPane().setContent(contenido);
        Alertas.estilizar(d);
        Platform.runLater(txtCantidad::requestFocus);

        Button btn = (Button) d.getDialogPane().lookupButton(registrar);
        btn.getStyleClass().add("primary-button");
        btn.addEventFilter(ActionEvent.ACTION, ev -> {
            int cantidad;
            try {
                cantidad = Integer.parseInt(txtCantidad.getText().trim());
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Cantidad inválida", "Escribe la cantidad como número entero.");
                ev.consume();
                return;
            }
            try {
                servicio.ajustar(r, comboTipo.getValue(), cantidad, txtMotivo.getText());
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo registrar el ajuste", ex.getMessage());
                ev.consume();
            }
        });
        d.setResultConverter(b -> b == registrar);
        return d.showAndWait().orElse(false);
    }
}
