package com.taller.repuestos.ui;

import com.taller.repuestos.model.Compatibilidad;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.service.BuscadorRepuestos;
import com.taller.repuestos.service.CompatibilidadService;
import com.taller.repuestos.util.Formato;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Buscador de repuestos. Se puede escribir una frase ("Toyota Hilux 2007 filtro") o elegir
 * el vehículo en cascada: Marca → Modelo → Año → Motor.
 */
public class VistaBuscador {

    private static final String TODOS = "";

    private final ObservableList<Repuesto> productos;
    private final CompatibilidadService compatibilidadService;

    private Map<Integer, List<Compatibilidad>> compat;
    private final ObservableList<Repuesto> resultados = FXCollections.observableArrayList();

    private TextField txtBuscar;
    private ComboBox<String> comboMarca;
    private ComboBox<String> comboModelo;
    private TextField txtAnio;
    private ComboBox<String> comboMotor;
    private Label lblConteo;
    private Label lblDetalle;
    private boolean actualizando;

    public VistaBuscador(ObservableList<Repuesto> productos, CompatibilidadService compatibilidadService) {
        this.productos = productos;
        this.compatibilidadService = compatibilidadService;
    }

    @SuppressWarnings("unchecked")
    public Node crear() {
        compat = compatibilidadService.porRepuesto();

        Label titulo = new Label("Buscar repuestos");
        titulo.getStyleClass().add("dashboard-title");

        txtBuscar = new TextField();
        txtBuscar.setPromptText("🔍 Escribe lo que buscas: código, nombre, marca, categoría, ubicación o vehículo (ej: Toyota Hilux 2007 filtro)");
        HBox.setHgrow(txtBuscar, Priority.ALWAYS);
        txtBuscar.textProperty().addListener((obs, anterior, nuevo) -> buscar());

        // ---------------- Filtro por vehículo en cascada ----------------
        comboMarca = new ComboBox<>();
        comboMarca.setPromptText("Marca del vehículo");
        comboMarca.getItems().add(TODOS);
        comboMarca.getItems().addAll(CompatibilidadService.marcasDeVehiculos(compat));
        comboMarca.setOnAction(e -> {
            if (actualizando) return;
            llenarModelos();
            buscar();
        });

        comboModelo = new ComboBox<>();
        comboModelo.setPromptText("Modelo");
        comboModelo.setOnAction(e -> {
            if (actualizando) return;
            llenarMotores();
            buscar();
        });

        txtAnio = new TextField();
        txtAnio.setPromptText("Año");
        txtAnio.setPrefWidth(80);
        txtAnio.textProperty().addListener((obs, anterior, nuevo) -> buscar());

        comboMotor = new ComboBox<>();
        comboMotor.setPromptText("Motor");
        comboMotor.setOnAction(e -> {
            if (!actualizando) buscar();
        });

        Button btnLimpiar = new Button("Limpiar");
        btnLimpiar.getStyleClass().add("secondary-button");
        btnLimpiar.setOnAction(e -> {
            actualizando = true;
            txtBuscar.clear();
            txtAnio.clear();
            comboMarca.setValue(null);
            comboModelo.getItems().clear();
            comboModelo.setValue(null);
            comboMotor.getItems().clear();
            comboMotor.setValue(null);
            actualizando = false;
            buscar();
        });

        HBox filtros = new HBox(10, comboMarca, comboModelo, txtAnio, comboMotor, btnLimpiar);
        filtros.setAlignment(Pos.CENTER_LEFT);

        // ---------------- Resultados ----------------
        TableView<Repuesto> tabla = new TableView<>();
        tabla.getColumns().add(Componentes.colFoto(Repuesto::getFoto));
        tabla.getColumns().addAll(
                columna("Código", 90, r -> r.getCodigo()),
                columna("Descripción", 200, r -> r.getDescripcion()),
                columna("Marca", 85, r -> r.getMarca()),
                columna("Categoría", 100, r -> r.getCategoria()),
                columna("Compatibilidad", 260, r -> BuscadorRepuestos.resumenCompatibilidad(compat.get(r.getId()))),
                columna("Ubicación", 90, r -> r.getUbicacion()),
                columna("Precio", 85, r -> Formato.moneda(r.getPrecio())));
        TableColumn<Repuesto, Integer> colStock = new TableColumn<>("Stock");
        colStock.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getStock()).asObject());
        colStock.setPrefWidth(60);
        tabla.getColumns().add(colStock);
        tabla.setItems(resultados);
        tabla.setPlaceholder(new Label("No se encontraron repuestos con esa búsqueda"));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        lblConteo = new Label();
        lblConteo.getStyleClass().add("hint-label");

        lblDetalle = new Label("Selecciona un repuesto para ver su detalle y todos los vehículos compatibles.");
        lblDetalle.setWrapText(true);
        FotoProducto foto = new FotoProducto(130);
        HBox.setHgrow(lblDetalle, Priority.ALWAYS);
        HBox panelDetalle = new HBox(16, foto, lblDetalle);
        panelDetalle.setAlignment(Pos.TOP_LEFT);
        panelDetalle.getStyleClass().add("panel-card");
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo != null) {
                lblDetalle.setText(detalleDe(nuevo));
                foto.mostrar(nuevo.getFoto());
            }
        });

        VBox contenido = new VBox(12, titulo, txtBuscar, filtros, lblConteo, tabla, panelDetalle);
        contenido.setPadding(new Insets(16));

        buscar();
        return contenido;
    }

    // ------------------------------------------------------------------

    private void buscar() {
        if (txtBuscar == null) return;
        Integer anio = null;
        String textoAnio = txtAnio.getText() == null ? "" : txtAnio.getText().trim();
        if (textoAnio.length() == 4) {
            try {
                anio = Integer.valueOf(textoAnio);
            } catch (NumberFormatException e) {
                anio = null; // todavía está escribiendo
            }
        }
        BuscadorRepuestos.Consulta consulta = BuscadorRepuestos.Consulta.de(txtBuscar.getText())
                .conVehiculo(valor(comboMarca), valor(comboModelo), anio, valor(comboMotor));
        List<Repuesto> encontrados = BuscadorRepuestos.buscar(productos, compat, consulta);
        resultados.setAll(encontrados);
        lblConteo.setText(consulta.estaVacia()
                ? "Mostrando todos los repuestos (" + encontrados.size() + ")"
                : encontrados.size() + " repuesto(s) encontrado(s)");
    }

    private void llenarModelos() {
        actualizando = true;
        comboModelo.getItems().setAll(CompatibilidadService.modelosDe(compat, valor(comboMarca)));
        comboModelo.setValue(null);
        comboMotor.getItems().clear();
        comboMotor.setValue(null);
        actualizando = false;
    }

    private void llenarMotores() {
        actualizando = true;
        comboMotor.getItems().setAll(CompatibilidadService.motoresDe(compat, valor(comboMarca), valor(comboModelo)));
        comboMotor.setValue(null);
        actualizando = false;
    }

    private String detalleDe(Repuesto r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.getCodigo()).append(" - ").append(r.getDescripcion()).append("\n");
        sb.append("Marca: ").append(nz(r.getMarca())).append("   |   Categoría: ").append(nz(r.getCategoria()))
          .append("   |   Ubicación: ").append(nz(r.getUbicacion())).append("\n");
        sb.append("Precio: ").append(Formato.moneda(r.getPrecio()));
        if (r.getPrecioMayorista() > 0) sb.append("   |   Mayorista: ").append(Formato.moneda(r.getPrecioMayorista()));
        sb.append("   |   Existencia: ").append(r.getStock()).append(" ").append(nz(r.getUnidad()).toLowerCase()).append("\n");
        if (!nz(r.getCodigoBarras()).isEmpty()) sb.append("Código de barras: ").append(r.getCodigoBarras()).append("\n");
        List<Compatibilidad> lista = compat.getOrDefault(r.getId(), new ArrayList<>());
        if (lista.isEmpty()) {
            sb.append("Sin vehículos compatibles registrados.");
        } else {
            sb.append("Compatible con:");
            for (Compatibilidad c : lista) {
                sb.append("\n  • ").append(c.getResumen());
                if (!nz(c.getCombustible()).isEmpty()) sb.append(", ").append(c.getCombustible());
                if (!nz(c.getTransmision()).isEmpty()) sb.append(", ").append(c.getTransmision());
                if (!nz(c.getObservaciones()).isEmpty()) sb.append(" (").append(c.getObservaciones()).append(")");
            }
        }
        return sb.toString();
    }

    private interface Extractor {
        String de(Repuesto r);
    }

    private static TableColumn<Repuesto, String> columna(String titulo, double ancho, Extractor extractor) {
        TableColumn<Repuesto, String> col = new TableColumn<>(titulo);
        col.setCellValueFactory(c -> new SimpleStringProperty(extractor.de(c.getValue())));
        col.setPrefWidth(ancho);
        return col;
    }

    private static String valor(ComboBox<String> combo) {
        String v = combo.getValue();
        return v == null ? "" : v.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
