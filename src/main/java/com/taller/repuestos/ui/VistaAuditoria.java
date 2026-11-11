package com.taller.repuestos.ui;

import com.taller.repuestos.model.RegistroAuditoria;
import com.taller.repuestos.service.AuditoriaService;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/** Consulta del historial de auditoría con filtros por fecha, usuario, módulo y texto. */
public class VistaAuditoria {

    private static final int LIMITE = 500;
    private static final String TODOS = "Todos";

    @SuppressWarnings("unchecked")
    public Node crear() {
        Label titulo = new Label("Auditoría");
        titulo.getStyleClass().add("section-title");
        Label subtitulo = new Label("Registro de las acciones importantes: quién las hizo, cuándo y sobre qué.");
        subtitulo.getStyleClass().add("hint-label");

        DatePicker dpDesde = new DatePicker();
        dpDesde.setPromptText("Desde");
        DatePicker dpHasta = new DatePicker();
        dpHasta.setPromptText("Hasta");

        ComboBox<String> comboUsuario = new ComboBox<>();
        comboUsuario.getItems().add(TODOS);
        comboUsuario.getItems().addAll(AuditoriaService.usuariosRegistrados());
        comboUsuario.setValue(TODOS);

        ComboBox<String> comboModulo = new ComboBox<>();
        comboModulo.getItems().add(TODOS);
        comboModulo.getItems().addAll(AuditoriaService.modulosRegistrados());
        comboModulo.setValue(TODOS);

        TextField txtTexto = new TextField();
        txtTexto.setPromptText("Buscar texto (acción, producto, factura...)");
        HBox.setHgrow(txtTexto, Priority.ALWAYS);

        Button btnBuscar = new Button("Buscar");
        btnBuscar.getStyleClass().add("primary-button");
        Button btnLimpiar = new Button("Limpiar filtros");
        btnLimpiar.getStyleClass().add("secondary-button");

        TableView<RegistroAuditoria> tabla = new TableView<>();
        tabla.getColumns().addAll(
                columna("Fecha", "fecha", 150),
                columna("Usuario", "usuario", 140),
                columna("Módulo", "modulo", 100),
                columna("Acción", "accion", 170),
                columna("Registro", "registro", 190),
                columna("Antes", "valorAnterior", 220),
                columna("Después", "valorNuevo", 220));
        tabla.setPlaceholder(new Label("No hay registros con esos filtros"));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Label lblConteo = new Label();
        lblConteo.getStyleClass().add("hint-label");

        Runnable buscar = () -> {
            String usuario = TODOS.equals(comboUsuario.getValue()) ? null : comboUsuario.getValue();
            String modulo = TODOS.equals(comboModulo.getValue()) ? null : comboModulo.getValue();
            LocalDate d = dpDesde.getValue();
            LocalDate h = dpHasta.getValue();
            List<RegistroAuditoria> resultados = AuditoriaService.buscar(
                    d == null ? null : d.toString(), h == null ? null : h.toString(),
                    usuario, modulo, txtTexto.getText(), LIMITE);
            tabla.setItems(FXCollections.observableArrayList(resultados));
            lblConteo.setText(resultados.size() >= LIMITE
                    ? "Se muestran los " + LIMITE + " registros más recientes. Usa los filtros para acotar la búsqueda."
                    : resultados.size() + " registro(s)");
        };

        btnBuscar.setOnAction(e -> buscar.run());
        txtTexto.setOnAction(e -> buscar.run());
        btnLimpiar.setOnAction(e -> {
            dpDesde.setValue(null);
            dpHasta.setValue(null);
            comboUsuario.setValue(TODOS);
            comboModulo.setValue(TODOS);
            txtTexto.clear();
            buscar.run();
        });

        HBox filtros = new HBox(10, dpDesde, dpHasta, comboUsuario, comboModulo, txtTexto, btnBuscar, btnLimpiar);
        filtros.setAlignment(Pos.CENTER_LEFT);

        buscar.run();

        VBox contenido = new VBox(12, new VBox(2, titulo, subtitulo), filtros, tabla, lblConteo);
        contenido.setPadding(new Insets(16));
        return contenido;
    }

    private static TableColumn<RegistroAuditoria, String> columna(String titulo, String propiedad, double ancho) {
        TableColumn<RegistroAuditoria, String> col = new TableColumn<>(titulo);
        col.setCellValueFactory(new PropertyValueFactory<>(propiedad));
        col.setPrefWidth(ancho);
        return col;
    }
}
