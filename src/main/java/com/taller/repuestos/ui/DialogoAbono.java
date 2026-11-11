package com.taller.repuestos.ui;

import com.taller.repuestos.dao.VentaDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.service.CreditoService;
import com.taller.repuestos.util.Formato;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/** Registro de un abono a una venta a crédito. */
public final class DialogoAbono {

    private DialogoAbono() {}

    /** Devuelve true si se registró el abono. */
    public static boolean mostrar(String clienteNombre, VentaDAO.CreditoPendiente credito, CreditoService servicio) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Registrar abono");
        dialogo.setHeaderText("Abono de " + clienteNombre + " a la factura " + Formato.numeroFactura(credito.getVentaId())
                + "\nSaldo pendiente: " + Formato.moneda(credito.getSaldo()));

        ButtonType btnRegistrar = new ButtonType("Registrar abono", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(btnRegistrar, ButtonType.CANCEL);

        TextField txtMonto = new TextField(String.format("%.2f", credito.getSaldo()));
        ComboBox<String> comboMetodo = new ComboBox<>();
        comboMetodo.getItems().addAll("Efectivo", "Tarjeta", "Transferencia");
        comboMetodo.setValue("Efectivo");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));
        grid.add(new Label("Monto del abono:"), 0, 0);
        grid.add(txtMonto, 1, 0);
        grid.add(new Label("Forma de pago:"), 0, 1);
        grid.add(comboMetodo, 1, 1);
        dialogo.getDialogPane().setContent(grid);

        Alertas.estilizar(dialogo);
        Button okBtn = (Button) dialogo.getDialogPane().lookupButton(btnRegistrar);
        okBtn.getStyleClass().add("primary-button");

        final double[] saldoRestante = {-1};
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            double monto;
            try {
                monto = Formato.leerMonto(txtMonto.getText());
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Monto inválido", "Escribe el monto del abono con números (ej: 250.00).");
                ev.consume();
                return;
            }
            try {
                saldoRestante[0] = servicio.registrarAbono(credito.getVentaId(), monto, comboMetodo.getValue());
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo registrar el abono", ex.getMessage());
                ev.consume();
            }
        });

        boolean registrado = dialogo.showAndWait().map(b -> b == btnRegistrar).orElse(false);
        if (registrado && saldoRestante[0] >= 0) {
            Alertas.info("Abono registrado", saldoRestante[0] <= 0.005
                    ? "La factura quedó totalmente pagada."
                    : "Saldo pendiente de la factura: " + Formato.moneda(saldoRestante[0]));
        }
        return registrado;
    }
}
