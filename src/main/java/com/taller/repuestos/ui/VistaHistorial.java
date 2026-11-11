package com.taller.repuestos.ui;

import com.taller.repuestos.dao.VentaDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Devolucion;
import com.taller.repuestos.model.Factura;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.service.DevolucionService;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.service.VentaService;
import com.taller.repuestos.util.Formato;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Historial de facturas: consultar, reimprimir, anular y registrar devoluciones. */
public class VistaHistorial {

    private final VentaService ventaService;
    private final DevolucionService devolucionService;
    private final Runnable alCambiarInventario;
    private final PermisoService permisos = PermisoService.get();

    /** @param alCambiarInventario se ejecuta cuando una anulación o devolución cambia las existencias */
    public VistaHistorial(VentaService ventaService, DevolucionService devolucionService, Runnable alCambiarInventario) {
        this.ventaService = ventaService;
        this.devolucionService = devolucionService;
        this.alCambiarInventario = alCambiarInventario;
    }

    @SuppressWarnings("unchecked")
    public Node crear() {
        Label titulo = new Label("Historial de facturas");
        titulo.getStyleClass().add("section-title");

        TextField txtBuscar = new TextField();
        txtBuscar.setPromptText("Buscar por número, cliente, vendedor o forma de pago...");
        HBox.setHgrow(txtBuscar, Priority.ALWAYS);

        ObservableList<VentaDAO.Venta> datos = FXCollections.observableArrayList(ventaService.listar());
        FilteredList<VentaDAO.Venta> filtradas = new FilteredList<>(datos, v -> true);
        txtBuscar.textProperty().addListener((obs, anterior, nuevo) -> {
            String q = nuevo == null ? "" : nuevo.trim().toLowerCase();
            filtradas.setPredicate(v -> q.isEmpty()
                    || Formato.numeroFactura(v.getId()).toLowerCase().contains(q)
                    || nombreCliente(v).toLowerCase().contains(q)
                    || (v.getVendedor() != null && v.getVendedor().toLowerCase().contains(q))
                    || (v.getMetodoPago() != null && v.getMetodoPago().toLowerCase().contains(q)));
        });

        TableView<VentaDAO.Venta> tabla = new TableView<>();
        TableColumn<VentaDAO.Venta, String> colNumero = new TableColumn<>("Factura");
        colNumero.setCellValueFactory(c -> new SimpleStringProperty(Formato.numeroFactura(c.getValue().getId())));
        colNumero.setPrefWidth(110);
        TableColumn<VentaDAO.Venta, String> colFecha = new TableColumn<>("Fecha");
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFecha()));
        colFecha.setPrefWidth(150);
        TableColumn<VentaDAO.Venta, String> colCliente = new TableColumn<>("Cliente");
        colCliente.setCellValueFactory(c -> new SimpleStringProperty(nombreCliente(c.getValue())));
        colCliente.setPrefWidth(180);
        TableColumn<VentaDAO.Venta, String> colVendedor = new TableColumn<>("Atendido por");
        colVendedor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVendedor()));
        colVendedor.setPrefWidth(150);
        TableColumn<VentaDAO.Venta, String> colPago = new TableColumn<>("Pago");
        colPago.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMetodoPago()));
        colPago.setPrefWidth(110);
        TableColumn<VentaDAO.Venta, Double> colTotal = new TableColumn<>("Total");
        colTotal.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().getTotal()).asObject());
        colTotal.setCellFactory(c -> new TableCell<VentaDAO.Venta, Double>() {
            @Override
            protected void updateItem(Double valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : Formato.moneda(valor));
            }
        });
        colTotal.setPrefWidth(110);
        TableColumn<VentaDAO.Venta, String> colEstado = new TableColumn<>("Estado");
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isAnulada() ? "Anulada" : "Emitida"));
        colEstado.setPrefWidth(90);
        tabla.getColumns().addAll(colNumero, colFecha, colCliente, colVendedor, colPago, colTotal, colEstado);
        tabla.setItems(filtradas);
        tabla.setPlaceholder(new Label("Aún no hay facturas registradas"));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Button btnVer = new Button("Ver / Reimprimir");
        btnVer.getStyleClass().add("primary-button");
        Button btnDevolucion = new Button("Devolución");
        btnDevolucion.getStyleClass().add("secondary-button");
        Button btnAnular = new Button("Anular factura");
        btnAnular.getStyleClass().add("danger-button");

        MultipleSelectionModel<VentaDAO.Venta> seleccion = tabla.getSelectionModel();
        btnVer.disableProperty().bind(seleccion.selectedItemProperty().isNull());
        btnDevolucion.setVisible(permisos.tiene(Permiso.DEVOLUCIONES_REALIZAR));
        btnDevolucion.setManaged(permisos.tiene(Permiso.DEVOLUCIONES_REALIZAR));
        btnAnular.setVisible(permisos.tiene(Permiso.VENTAS_ANULAR));
        btnAnular.setManaged(permisos.tiene(Permiso.VENTAS_ANULAR));
        // Una factura anulada no se puede anular otra vez ni devolver
        btnDevolucion.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            VentaDAO.Venta v = seleccion.getSelectedItem();
            return v == null || v.isAnulada();
        }, seleccion.selectedItemProperty()));
        btnAnular.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            VentaDAO.Venta v = seleccion.getSelectedItem();
            return v == null || v.isAnulada();
        }, seleccion.selectedItemProperty()));

        Runnable refrescar = () -> datos.setAll(ventaService.listar());

        btnVer.setOnAction(e -> {
            VentaDAO.Venta v = seleccion.getSelectedItem();
            if (v == null) return;
            Factura f = ventaService.obtenerFactura(v.getId());
            if (f == null) {
                Alertas.advertencia("Factura no encontrada", "No se pudo cargar la factura.");
                return;
            }
            DialogoFactura.mostrar(f, devolucionService.obtenerPorVenta(v.getId()));
        });

        btnDevolucion.setOnAction(e -> {
            VentaDAO.Venta v = seleccion.getSelectedItem();
            if (v == null) return;
            Factura f = ventaService.obtenerFactura(v.getId());
            if (f == null) {
                Alertas.advertencia("Factura no encontrada", "No se pudo cargar la factura.");
                return;
            }
            Devolucion d = DialogoDevolucion.mostrar(f, devolucionService);
            if (d != null) {
                refrescar.run();
                alCambiarInventario.run();
                DialogoFactura.mostrarDevolucion(ventaService.obtenerFactura(v.getId()), d);
            }
        });

        btnAnular.setOnAction(e -> {
            VentaDAO.Venta v = seleccion.getSelectedItem();
            if (v == null) return;
            String numero = Formato.numeroFactura(v.getId());
            String motivo = Alertas.pedirTexto("Anular factura", "Motivo de la anulación de " + numero + ":", "");
            if (motivo == null) return;
            if (!Alertas.confirmar("Anular factura", "¿Anular la factura " + numero + " por " + Formato.moneda(v.getTotal())
                    + "? Los productos volverán al inventario. Esta acción no se puede deshacer.")) {
                return;
            }
            try {
                ventaService.anular(v.getId(), motivo);
                refrescar.run();
                alCambiarInventario.run();
                Alertas.info("Factura anulada", "La factura " + numero + " quedó anulada y el inventario se actualizó.");
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo anular", ex.getMessage());
            }
        });

        HBox barra = new HBox(10, txtBuscar);
        barra.setAlignment(Pos.CENTER_LEFT);
        HBox botones = new HBox(10, btnVer, btnDevolucion, btnAnular);
        botones.setAlignment(Pos.CENTER_LEFT);

        VBox contenido = new VBox(12, titulo, barra, tabla, botones);
        contenido.setPadding(new Insets(16));
        return contenido;
    }

    private static String nombreCliente(VentaDAO.Venta v) {
        return v.getCliente() == null ? "Consumidor final" : v.getCliente();
    }
}
