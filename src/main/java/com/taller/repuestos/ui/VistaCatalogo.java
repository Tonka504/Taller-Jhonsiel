package com.taller.repuestos.ui;

import com.taller.repuestos.dao.CatalogoDAO.Item;
import com.taller.repuestos.dao.CatalogoDAO.Tipo;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.service.CatalogoService;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Administración del catálogo: categorías y marcas. */
public class VistaCatalogo {

    private final CatalogoService servicio;

    public VistaCatalogo(CatalogoService servicio) {
        this.servicio = servicio;
    }

    public Node crear() {
        Label titulo = new Label("Catálogo de repuestos");
        titulo.getStyleClass().add("dashboard-title");
        Label ayuda = new Label("Aquí administras las categorías y marcas que aparecen al registrar productos y vehículos. "
                + "Si renombras una, se actualiza en todos los productos que la usan. Las que ya se usan no se pueden eliminar, solo desactivar.");
        ayuda.getStyleClass().add("hint-label");
        ayuda.setWrapText(true);

        VBox categorias = panel(Tipo.CATEGORIA, "Categorías");
        VBox marcas = panel(Tipo.MARCA, "Marcas");
        HBox.setHgrow(categorias, Priority.ALWAYS);
        HBox.setHgrow(marcas, Priority.ALWAYS);

        VBox contenido = new VBox(14, titulo, ayuda, new HBox(16, categorias, marcas));
        contenido.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dashboard-scroll");
        return scroll;
    }

    @SuppressWarnings("unchecked")
    private VBox panel(Tipo tipo, String nombrePlural) {
        String singular = tipo == Tipo.CATEGORIA ? "categoría" : "marca";

        Label titulo = new Label(nombrePlural);
        titulo.getStyleClass().add("section-title");

        ObservableList<Item> datos = FXCollections.observableArrayList(servicio.listar(tipo));
        TableView<Item> tabla = new TableView<>();
        TableColumn<Item, String> colNombre = new TableColumn<>("Nombre");
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colNombre.setPrefWidth(180);
        TableColumn<Item, Integer> colUsos = new TableColumn<>("En uso");
        colUsos.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getUsos()).asObject());
        colUsos.setPrefWidth(70);
        TableColumn<Item, String> colEstado = new TableColumn<>("Estado");
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
        colEstado.setPrefWidth(80);
        tabla.getColumns().addAll(colNombre, colUsos, colEstado);
        tabla.setItems(datos);
        tabla.setPlaceholder(new Label("Sin elementos"));
        tabla.setPrefHeight(360);

        Runnable refrescar = () -> datos.setAll(servicio.listar(tipo));

        TextField txtNuevo = new TextField();
        txtNuevo.setPromptText("Nueva " + singular);
        HBox.setHgrow(txtNuevo, Priority.ALWAYS);
        Button btnAgregar = new Button("Agregar");
        btnAgregar.getStyleClass().add("primary-button");
        Runnable agregar = () -> {
            try {
                servicio.crear(tipo, txtNuevo.getText());
                txtNuevo.clear();
                refrescar.run();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo agregar", ex.getMessage());
            }
        };
        btnAgregar.setOnAction(e -> agregar.run());
        txtNuevo.setOnAction(e -> agregar.run());

        Button btnRenombrar = new Button("Renombrar");
        btnRenombrar.getStyleClass().add("secondary-button");
        Button btnEstado = new Button("Activar / Desactivar");
        btnEstado.getStyleClass().add("secondary-button");
        Button btnEliminar = new Button("Eliminar");
        btnEliminar.getStyleClass().add("danger-button");

        MultipleSelectionModel<Item> seleccion = tabla.getSelectionModel();
        btnRenombrar.disableProperty().bind(seleccion.selectedItemProperty().isNull());
        btnEstado.disableProperty().bind(seleccion.selectedItemProperty().isNull());
        btnEliminar.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            Item i = seleccion.getSelectedItem();
            return i == null || i.getUsos() > 0;
        }, seleccion.selectedItemProperty()));

        btnRenombrar.setOnAction(e -> {
            Item item = seleccion.getSelectedItem();
            if (item == null) return;
            String nuevo = Alertas.pedirTexto("Renombrar " + singular,
                    "Nuevo nombre para \"" + item.getNombre() + "\" (" + item.getUsos() + " en uso):", item.getNombre());
            if (nuevo == null) return;
            try {
                servicio.renombrar(tipo, item, nuevo);
                refrescar.run();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo renombrar", ex.getMessage());
            }
        });

        btnEstado.setOnAction(e -> {
            Item item = seleccion.getSelectedItem();
            if (item == null) return;
            try {
                servicio.cambiarEstado(tipo, item, !item.isActivo());
                refrescar.run();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo cambiar el estado", ex.getMessage());
            }
        });

        btnEliminar.setOnAction(e -> {
            Item item = seleccion.getSelectedItem();
            if (item == null) return;
            if (!Alertas.confirmar("Eliminar " + singular, "¿Eliminar \"" + item.getNombre() + "\"?")) return;
            try {
                servicio.eliminar(tipo, item);
                refrescar.run();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo eliminar", ex.getMessage());
            }
        });

        VBox panel = new VBox(10, titulo, new HBox(8, txtNuevo, btnAgregar), tabla,
                new HBox(8, btnRenombrar, btnEstado, btnEliminar));
        panel.getStyleClass().add("panel-card");
        return panel;
    }
}
