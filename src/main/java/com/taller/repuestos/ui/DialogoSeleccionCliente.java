package com.taller.repuestos.ui;

import com.taller.repuestos.model.Cliente;
import com.taller.repuestos.service.ClienteService;
import com.taller.repuestos.util.Formato;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

/** Búsqueda y selección de un cliente para la venta. */
public final class DialogoSeleccionCliente {

    /** Valor especial: la venta es para "consumidor final" (sin cliente registrado). */
    public static final Cliente CONSUMIDOR_FINAL =
            new Cliente(0, "Consumidor final", null, null, null, null, "Minorista", true, 0, null);

    private DialogoSeleccionCliente() {}

    /** Devuelve el cliente elegido, CONSUMIDOR_FINAL, o null si se cancela. */
    @SuppressWarnings("unchecked")
    public static Cliente mostrar(ClienteService servicio) {
        Dialog<Cliente> dialogo = new Dialog<>();
        dialogo.setTitle("Elegir cliente");
        dialogo.setHeaderText("Busca un cliente por nombre, identificación o teléfono");

        ButtonType btnElegir = new ButtonType("Seleccionar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnFinal = new ButtonType("Consumidor final", ButtonBar.ButtonData.OTHER);
        dialogo.getDialogPane().getButtonTypes().addAll(btnElegir, btnFinal, ButtonType.CANCEL);

        ObservableList<Cliente> activos = FXCollections.observableArrayList();
        for (Cliente c : servicio.listar()) {
            if (c.isActivo()) activos.add(c);
        }
        FilteredList<Cliente> filtrados = new FilteredList<>(activos, c -> true);

        TextField txtBuscar = new TextField();
        txtBuscar.setPromptText("Buscar...");
        txtBuscar.textProperty().addListener((obs, anterior, nuevo) -> {
            String q = nuevo == null ? "" : nuevo.trim().toLowerCase();
            filtrados.setPredicate(c -> q.isEmpty()
                    || contiene(c.getNombre(), q) || contiene(c.getIdentificacion(), q) || contiene(c.getTelefono(), q));
        });

        TableView<Cliente> tabla = new TableView<>();
        TableColumn<Cliente, String> colNombre = new TableColumn<>("Nombre");
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colNombre.setPrefWidth(200);
        TableColumn<Cliente, String> colIdent = new TableColumn<>("Identificación");
        colIdent.setCellValueFactory(new PropertyValueFactory<>("identificacion"));
        colIdent.setPrefWidth(130);
        TableColumn<Cliente, String> colTel = new TableColumn<>("Teléfono");
        colTel.setCellValueFactory(new PropertyValueFactory<>("telefono"));
        colTel.setPrefWidth(110);
        TableColumn<Cliente, String> colCredito = new TableColumn<>("Crédito disponible");
        colCredito.setCellValueFactory(c -> new SimpleStringProperty(Formato.moneda(c.getValue().getCreditoDisponible())));
        colCredito.setPrefWidth(130);
        tabla.getColumns().addAll(colNombre, colIdent, colTel, colCredito);
        tabla.setItems(filtrados);
        tabla.setPrefSize(600, 320);

        VBox contenido = new VBox(10, txtBuscar, tabla);
        contenido.setPadding(new Insets(10));
        dialogo.getDialogPane().setContent(contenido);

        Alertas.estilizar(dialogo);
        Button okBtn = (Button) dialogo.getDialogPane().lookupButton(btnElegir);
        okBtn.getStyleClass().add("primary-button");
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            if (tabla.getSelectionModel().getSelectedItem() == null) {
                Alertas.advertencia("Sin selección", "Selecciona un cliente de la lista, o elige \"Consumidor final\".");
                ev.consume();
            }
        });

        dialogo.setResultConverter(boton -> {
            if (boton == btnElegir) return tabla.getSelectionModel().getSelectedItem();
            if (boton == btnFinal) return CONSUMIDOR_FINAL;
            return null;
        });
        return dialogo.showAndWait().orElse(null);
    }

    private static boolean contiene(String texto, String q) {
        return texto != null && texto.toLowerCase().contains(q);
    }
}
