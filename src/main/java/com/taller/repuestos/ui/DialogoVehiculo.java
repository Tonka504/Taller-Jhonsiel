package com.taller.repuestos.ui;

import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Cliente;
import com.taller.repuestos.model.Vehiculo;
import com.taller.repuestos.service.CompatibilidadService;
import com.taller.repuestos.service.VehiculoService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.List;

/** Alta o edición de un vehículo de cliente. */
public final class DialogoVehiculo {

    private DialogoVehiculo() {}

    /**
     * @param v          vehículo a editar, o null para uno nuevo
     * @param clienteFijo si no es null, el vehículo nuevo queda a nombre de este cliente
     * @return el vehículo guardado, o null si se canceló
     */
    public static Vehiculo mostrar(VehiculoService servicio, List<Cliente> clientes, List<String> marcas,
                                   Vehiculo v, Cliente clienteFijo) {
        Vehiculo editado = v == null ? new Vehiculo() : v;
        Dialog<Vehiculo> d = new Dialog<>();
        d.setTitle(v == null ? "Nuevo vehículo" : "Editar vehículo");
        d.setHeaderText(v == null ? "Registrar vehículo de cliente" : v.getDescripcion());
        ButtonType ok = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        ComboBox<Cliente> comboCliente = new ComboBox<>();
        for (Cliente c : clientes) if (c.isActivo() || (v != null && c.getId() == v.getClienteId())) comboCliente.getItems().add(c);
        comboCliente.setPromptText("Dueño del vehículo");
        int idCliente = v != null ? v.getClienteId() : clienteFijo != null ? clienteFijo.getId() : 0;
        for (Cliente c : comboCliente.getItems()) if (c.getId() == idCliente) comboCliente.setValue(c);

        ComboBox<String> comboMarca = new ComboBox<>(FXCollections.observableArrayList(marcas));
        comboMarca.setEditable(true);
        comboMarca.getEditor().setText(Componentes.nz(editado.getMarca()));
        TextField txtModelo = new TextField(Componentes.nz(editado.getModelo()));
        txtModelo.setPromptText("Hilux, Corolla, Frontier...");
        TextField txtAnio = new TextField(editado.getAnio() == null ? "" : String.valueOf(editado.getAnio()));
        TextField txtPlaca = new TextField(Componentes.nz(editado.getPlaca()));
        TextField txtVin = new TextField(Componentes.nz(editado.getVin()));
        txtVin.setPromptText("VIN / número de chasis");
        TextField txtMotor = new TextField(Componentes.nz(editado.getMotor()));
        txtMotor.setPromptText("Ej: 2.7L 2TR-FE");
        TextField txtCilindrada = new TextField(Componentes.nz(editado.getCilindrada()));
        txtCilindrada.setPromptText("Ej: 2700 cc");
        ComboBox<String> comboComb = new ComboBox<>(FXCollections.observableArrayList(CompatibilidadService.COMBUSTIBLES));
        comboComb.setValue(editado.getCombustible());
        ComboBox<String> comboTrans = new ComboBox<>(FXCollections.observableArrayList(CompatibilidadService.TRANSMISIONES));
        comboTrans.setValue(editado.getTransmision());
        TextField txtColor = new TextField(Componentes.nz(editado.getColor()));
        TextField txtKm = new TextField(editado.getKilometraje() == null ? "" : String.valueOf(editado.getKilometraje()));
        TextField txtObs = new TextField(Componentes.nz(editado.getObservaciones()));
        CheckBox chkActivo = new CheckBox("Activo");
        chkActivo.setSelected(editado.isActivo());

        GridPane g = Componentes.formulario();
        int f = 0;
        f = Componentes.campo(g, f, "Cliente:", comboCliente);
        f = Componentes.campo(g, f, "Marca:", comboMarca);
        f = Componentes.campo(g, f, "Modelo:", txtModelo);
        f = Componentes.campo(g, f, "Año:", txtAnio);
        f = Componentes.campo(g, f, "Placa:", txtPlaca);
        f = Componentes.campo(g, f, "VIN / chasis:", txtVin);
        f = Componentes.campo(g, f, "Motor:", txtMotor);
        f = Componentes.campo(g, f, "Cilindrada:", txtCilindrada);
        f = Componentes.campo(g, f, "Combustible:", comboComb);
        f = Componentes.campo(g, f, "Transmisión:", comboTrans);
        f = Componentes.campo(g, f, "Color:", txtColor);
        f = Componentes.campo(g, f, "Kilometraje:", txtKm);
        f = Componentes.campo(g, f, "Observaciones:", txtObs);
        g.add(chkActivo, 1, f);
        VBox contenido = new VBox(g);
        contenido.setPrefWidth(460);
        d.getDialogPane().setContent(contenido);
        Alertas.estilizar(d);
        Platform.runLater(comboMarca::requestFocus);

        Button b = (Button) d.getDialogPane().lookupButton(ok);
        b.getStyleClass().add("primary-button");
        b.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                editado.setClienteId(comboCliente.getValue() == null ? 0 : comboCliente.getValue().getId());
                editado.setMarca(comboMarca.getEditor().getText());
                editado.setModelo(txtModelo.getText());
                editado.setAnio(Componentes.entero(txtAnio.getText()));
                editado.setPlaca(txtPlaca.getText());
                editado.setVin(txtVin.getText());
                editado.setMotor(vacio(txtMotor.getText()));
                editado.setCilindrada(vacio(txtCilindrada.getText()));
                editado.setCombustible(comboComb.getValue());
                editado.setTransmision(comboTrans.getValue());
                editado.setColor(vacio(txtColor.getText()));
                editado.setKilometraje(Componentes.entero(txtKm.getText().replace(",", "")));
                editado.setObservaciones(vacio(txtObs.getText()));
                editado.setActivo(chkActivo.isSelected());
                servicio.guardar(editado);
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Dato inválido", "El año y el kilometraje deben ser números enteros.");
                ev.consume();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo guardar", ex.getMessage());
                ev.consume();
            }
        });
        d.setResultConverter(x -> x == ok ? editado : null);
        return d.showAndWait().orElse(null);
    }

    private static String vacio(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
