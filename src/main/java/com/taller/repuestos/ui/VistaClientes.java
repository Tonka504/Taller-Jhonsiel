package com.taller.repuestos.ui;

import com.taller.repuestos.dao.VentaDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Cliente;
import com.taller.repuestos.model.Factura;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.service.ClienteService;
import com.taller.repuestos.service.CreditoService;
import com.taller.repuestos.service.DevolucionService;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.service.VentaService;
import com.taller.repuestos.util.Formato;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Clientes: datos, crédito autorizado, deuda pendiente, abonos e historial de compras. */
public class VistaClientes {

    private final ClienteService clienteService;
    private final VentaService ventaService;
    private final CreditoService creditoService;
    private final DevolucionService devolucionService;
    private final PermisoService permisos = PermisoService.get();

    private final ObservableList<Cliente> datos = FXCollections.observableArrayList();
    private final ObservableList<VentaDAO.CreditoPendiente> creditos = FXCollections.observableArrayList();
    private final ObservableList<VentaDAO.Venta> compras = FXCollections.observableArrayList();

    private TableView<Cliente> tabla;
    private Cliente seleccionado;

    private TextField txtNombre;
    private TextField txtIdentificacion;
    private TextField txtTelefono;
    private TextField txtDireccion;
    private TextField txtCorreo;
    private ComboBox<String> comboTipo;
    private TextField txtLimite;
    private TextField txtNotas;
    private Label lblCuenta;
    private Button btnEstado;

    public VistaClientes(ClienteService clienteService, VentaService ventaService, CreditoService creditoService,
                         DevolucionService devolucionService) {
        this.clienteService = clienteService;
        this.ventaService = ventaService;
        this.creditoService = creditoService;
        this.devolucionService = devolucionService;
    }

    @SuppressWarnings("unchecked")
    public Node crear() {
        boolean puedeEditar = permisos.tiene(Permiso.CLIENTES_EDITAR);

        Label titulo = new Label("Clientes");
        titulo.getStyleClass().add("dashboard-title");

        // ---------------- Lista de clientes ----------------
        TextField txtBuscar = new TextField();
        txtBuscar.setPromptText("Buscar por nombre, identificación o teléfono...");
        FilteredList<Cliente> filtrados = new FilteredList<>(datos, c -> true);
        txtBuscar.textProperty().addListener((obs, anterior, nuevo) -> {
            String q = nuevo == null ? "" : nuevo.trim().toLowerCase();
            filtrados.setPredicate(c -> q.isEmpty() || contiene(c.getNombre(), q)
                    || contiene(c.getIdentificacion(), q) || contiene(c.getTelefono(), q));
        });

        tabla = new TableView<>();
        TableColumn<Cliente, String> colNombre = new TableColumn<>("Nombre");
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colNombre.setPrefWidth(190);
        TableColumn<Cliente, String> colIdent = new TableColumn<>("Identificación");
        colIdent.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIdentificacion()));
        colIdent.setPrefWidth(120);
        TableColumn<Cliente, String> colTel = new TableColumn<>("Teléfono");
        colTel.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTelefono()));
        colTel.setPrefWidth(100);
        TableColumn<Cliente, String> colSaldo = new TableColumn<>("Debe");
        colSaldo.setCellValueFactory(c -> new SimpleStringProperty(Formato.moneda(c.getValue().getSaldoPendiente())));
        colSaldo.setPrefWidth(100);
        TableColumn<Cliente, String> colEstado = new TableColumn<>("Estado");
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado()));
        colEstado.setPrefWidth(80);
        tabla.getColumns().addAll(colNombre, colIdent, colTel, colSaldo, colEstado);
        tabla.setItems(filtrados);
        tabla.setPlaceholder(new Label("Aún no hay clientes registrados"));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo != null) seleccionar(nuevo);
        });

        VBox panelLista = new VBox(10, txtBuscar, tabla);
        panelLista.setPrefWidth(620);
        HBox.setHgrow(panelLista, Priority.ALWAYS);

        // ---------------- Formulario ----------------
        txtNombre = new TextField();
        txtIdentificacion = new TextField();
        txtTelefono = new TextField();
        txtDireccion = new TextField();
        txtCorreo = new TextField();
        comboTipo = new ComboBox<>();
        comboTipo.getItems().addAll(Cliente.TIPOS);
        comboTipo.setValue(Cliente.TIPOS.get(0));
        txtLimite = new TextField("0");
        txtNotas = new TextField();

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int f = 0;
        form.add(new Label("Nombre:"), 0, f);          form.add(txtNombre, 1, f++);
        form.add(new Label("Identificación:"), 0, f);  form.add(txtIdentificacion, 1, f++);
        form.add(new Label("Teléfono:"), 0, f);        form.add(txtTelefono, 1, f++);
        form.add(new Label("Dirección:"), 0, f);       form.add(txtDireccion, 1, f++);
        form.add(new Label("Correo:"), 0, f);          form.add(txtCorreo, 1, f++);
        form.add(new Label("Tipo:"), 0, f);            form.add(comboTipo, 1, f++);
        form.add(new Label("Límite de crédito:"), 0, f); form.add(txtLimite, 1, f++);
        form.add(new Label("Notas:"), 0, f);           form.add(txtNotas, 1, f++);

        Button btnNuevo = new Button("Nuevo");
        btnNuevo.getStyleClass().add("secondary-button");
        Button btnGuardar = new Button("Guardar");
        btnGuardar.getStyleClass().add("primary-button");
        btnEstado = new Button("Desactivar");
        btnEstado.getStyleClass().add("danger-button");
        btnEstado.setDisable(true);
        HBox botonesForm = new HBox(8, btnNuevo, btnGuardar, btnEstado);

        for (Node n : new Node[]{txtNombre, txtIdentificacion, txtTelefono, txtDireccion, txtCorreo, comboTipo, txtLimite, txtNotas}) {
            n.setDisable(!puedeEditar);
        }
        btnNuevo.setVisible(puedeEditar);
        btnNuevo.setManaged(puedeEditar);
        btnGuardar.setVisible(puedeEditar);
        btnGuardar.setManaged(puedeEditar);
        btnEstado.setVisible(puedeEditar);
        btnEstado.setManaged(puedeEditar);

        Label tituloForm = new Label("Datos del cliente");
        tituloForm.getStyleClass().add("section-title");
        VBox panelForm = new VBox(10, tituloForm, form, botonesForm);
        panelForm.getStyleClass().add("panel-card");

        // ---------------- Cuenta de crédito ----------------
        lblCuenta = new Label("Selecciona un cliente para ver su cuenta.");
        lblCuenta.getStyleClass().add("metric-value");
        lblCuenta.setWrapText(true);

        TableView<VentaDAO.CreditoPendiente> tablaCreditos = new TableView<>();
        TableColumn<VentaDAO.CreditoPendiente, String> ccFactura = new TableColumn<>("Factura");
        ccFactura.setCellValueFactory(c -> new SimpleStringProperty(Formato.numeroFactura(c.getValue().getVentaId())));
        ccFactura.setPrefWidth(100);
        TableColumn<VentaDAO.CreditoPendiente, String> ccVence = new TableColumn<>("Vence");
        ccVence.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaLimite()));
        ccVence.setPrefWidth(95);
        TableColumn<VentaDAO.CreditoPendiente, String> ccTotal = new TableColumn<>("Total");
        ccTotal.setCellValueFactory(c -> new SimpleStringProperty(Formato.moneda(c.getValue().getTotal())));
        ccTotal.setPrefWidth(90);
        TableColumn<VentaDAO.CreditoPendiente, String> ccSaldo = new TableColumn<>("Saldo");
        ccSaldo.setCellValueFactory(c -> new SimpleStringProperty(Formato.moneda(c.getValue().getSaldo())));
        ccSaldo.setPrefWidth(90);
        tablaCreditos.getColumns().addAll(ccFactura, ccVence, ccTotal, ccSaldo);
        tablaCreditos.setItems(creditos);
        tablaCreditos.setPlaceholder(new Label("Sin créditos pendientes"));
        tablaCreditos.setPrefHeight(140);

        Button btnAbono = new Button("Registrar abono");
        btnAbono.getStyleClass().add("primary-button");
        btnAbono.setVisible(permisos.tiene(Permiso.COBROS_REGISTRAR));
        btnAbono.setManaged(permisos.tiene(Permiso.COBROS_REGISTRAR));
        btnAbono.disableProperty().bind(tablaCreditos.getSelectionModel().selectedItemProperty().isNull());

        TableView<VentaDAO.Venta> tablaCompras = new TableView<>();
        TableColumn<VentaDAO.Venta, String> cpFactura = new TableColumn<>("Factura");
        cpFactura.setCellValueFactory(c -> new SimpleStringProperty(Formato.numeroFactura(c.getValue().getId())));
        cpFactura.setPrefWidth(100);
        TableColumn<VentaDAO.Venta, String> cpFecha = new TableColumn<>("Fecha");
        cpFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFecha()));
        cpFecha.setPrefWidth(140);
        TableColumn<VentaDAO.Venta, String> cpPago = new TableColumn<>("Pago");
        cpPago.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMetodoPago()));
        cpPago.setPrefWidth(90);
        TableColumn<VentaDAO.Venta, String> cpTotal = new TableColumn<>("Total");
        cpTotal.setCellValueFactory(c -> new SimpleStringProperty(Formato.moneda(c.getValue().getTotal())));
        cpTotal.setPrefWidth(90);
        TableColumn<VentaDAO.Venta, String> cpEstado = new TableColumn<>("Estado");
        cpEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isAnulada() ? "Anulada" : "Emitida"));
        cpEstado.setPrefWidth(80);
        tablaCompras.getColumns().addAll(cpFactura, cpFecha, cpPago, cpTotal, cpEstado);
        tablaCompras.setItems(compras);
        tablaCompras.setPlaceholder(new Label("Este cliente aún no tiene compras"));
        tablaCompras.setPrefHeight(170);

        Button btnVerFactura = new Button("Ver factura");
        btnVerFactura.getStyleClass().add("secondary-button");
        btnVerFactura.setVisible(permisos.tiene(Permiso.HISTORIAL_VER));
        btnVerFactura.setManaged(permisos.tiene(Permiso.HISTORIAL_VER));
        btnVerFactura.disableProperty().bind(tablaCompras.getSelectionModel().selectedItemProperty().isNull());

        Label tituloCuenta = new Label("Cuenta y compras");
        tituloCuenta.getStyleClass().add("section-title");
        Label lblCreditos = new Label("Créditos pendientes de cobro");
        lblCreditos.getStyleClass().add("metric-label");
        Label lblCompras = new Label("Historial de compras");
        lblCompras.getStyleClass().add("metric-label");
        VBox panelCuenta = new VBox(8, tituloCuenta, lblCuenta, lblCreditos, tablaCreditos, btnAbono,
                lblCompras, tablaCompras, btnVerFactura);
        panelCuenta.getStyleClass().add("panel-card");

        VBox panelDerecho = new VBox(14, panelForm, panelCuenta);
        panelDerecho.setPrefWidth(440);

        // ---------------- Acciones ----------------
        btnNuevo.setOnAction(e -> {
            tabla.getSelectionModel().clearSelection();
            limpiarFormulario();
        });

        btnGuardar.setOnAction(e -> guardar());

        btnEstado.setOnAction(e -> {
            if (seleccionado == null) return;
            boolean activar = !seleccionado.isActivo();
            if (!activar && !Alertas.confirmar("Desactivar cliente",
                    "¿Desactivar a " + seleccionado.getNombre() + "? No podrá usarse en nuevas ventas.")) {
                return;
            }
            try {
                clienteService.cambiarEstado(seleccionado, activar);
                int id = seleccionado.getId();
                recargar(id);
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo cambiar el estado", ex.getMessage());
            }
        });

        btnAbono.setOnAction(e -> {
            VentaDAO.CreditoPendiente credito = tablaCreditos.getSelectionModel().getSelectedItem();
            if (credito == null || seleccionado == null) return;
            if (DialogoAbono.mostrar(seleccionado.getNombre(), credito, creditoService)) {
                recargar(seleccionado.getId());
            }
        });

        btnVerFactura.setOnAction(e -> {
            VentaDAO.Venta v = tablaCompras.getSelectionModel().getSelectedItem();
            if (v == null) return;
            Factura factura = ventaService.obtenerFactura(v.getId());
            if (factura != null) DialogoFactura.mostrar(factura, devolucionService.obtenerPorVenta(v.getId()));
        });

        HBox cuerpo = new HBox(16, panelLista, panelDerecho);
        VBox contenido = new VBox(14, titulo, cuerpo);
        contenido.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dashboard-scroll");

        recargar(-1);
        return scroll;
    }

    /** Vuelve a leer los clientes y deja seleccionado el indicado (o ninguno si id < 0). */
    private void recargar(int idASeleccionar) {
        datos.setAll(clienteService.listar());
        if (idASeleccionar < 0) {
            seleccionado = null;
            limpiarFormulario();
            return;
        }
        for (Cliente c : datos) {
            if (c.getId() == idASeleccionar) {
                tabla.getSelectionModel().select(c);
                seleccionar(c);
                return;
            }
        }
    }

    private void seleccionar(Cliente c) {
        seleccionado = c;
        txtNombre.setText(c.getNombre());
        txtIdentificacion.setText(c.getIdentificacion() == null ? "" : c.getIdentificacion());
        txtTelefono.setText(c.getTelefono() == null ? "" : c.getTelefono());
        txtDireccion.setText(c.getDireccion() == null ? "" : c.getDireccion());
        txtCorreo.setText(c.getCorreo() == null ? "" : c.getCorreo());
        comboTipo.setValue(c.getTipo());
        txtLimite.setText(String.format("%.2f", c.getLimiteCredito()));
        txtNotas.setText(c.getNotas() == null ? "" : c.getNotas());
        btnEstado.setText(c.isActivo() ? "Desactivar" : "Activar");
        btnEstado.setDisable(false);

        lblCuenta.setText("Límite de crédito: " + Formato.moneda(c.getLimiteCredito())
                + "   |   Debe: " + Formato.moneda(c.getSaldoPendiente())
                + "   |   Disponible: " + Formato.moneda(c.getCreditoDisponible()));
        creditos.setAll(clienteService.creditosPendientes(c.getId()));
        compras.setAll(clienteService.compras(c.getId()));
    }

    private void limpiarFormulario() {
        seleccionado = null;
        txtNombre.clear();
        txtIdentificacion.clear();
        txtTelefono.clear();
        txtDireccion.clear();
        txtCorreo.clear();
        comboTipo.setValue(Cliente.TIPOS.get(0));
        txtLimite.setText("0");
        txtNotas.clear();
        btnEstado.setText("Desactivar");
        btnEstado.setDisable(true);
        lblCuenta.setText("Selecciona un cliente para ver su cuenta.");
        creditos.clear();
        compras.clear();
    }

    private void guardar() {
        double limite;
        try {
            limite = Formato.leerMonto(txtLimite.getText());
        } catch (NumberFormatException ex) {
            Alertas.advertencia("Límite inválido", "Escribe el límite de crédito con números (usa 0 si no tiene crédito).");
            return;
        }
        int id = seleccionado == null ? 0 : seleccionado.getId();
        boolean activo = seleccionado == null || seleccionado.isActivo();
        Cliente nuevo = new Cliente(id, txtNombre.getText().trim(), vacioANull(txtIdentificacion.getText()),
                vacioANull(txtTelefono.getText()), vacioANull(txtDireccion.getText()), vacioANull(txtCorreo.getText()),
                comboTipo.getValue(), activo, limite, vacioANull(txtNotas.getText()));
        try {
            if (seleccionado == null) {
                Cliente creado = clienteService.crear(nuevo);
                recargar(creado.getId());
                Alertas.info("Cliente guardado", "El cliente \"" + creado.getNombre() + "\" se registró correctamente.");
            } else {
                clienteService.actualizar(seleccionado, nuevo);
                recargar(id);
                Alertas.info("Cliente actualizado", "Los datos del cliente se guardaron.");
            }
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo guardar el cliente", ex.getMessage());
        }
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.trim().isEmpty() ? null : texto.trim();
    }

    private static boolean contiene(String texto, String q) {
        return texto != null && texto.toLowerCase().contains(q);
    }
}
