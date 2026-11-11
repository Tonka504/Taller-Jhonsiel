package com.taller.repuestos.ui;

import com.taller.repuestos.dao.ProveedorDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Compra;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Proveedor;
import com.taller.repuestos.service.CompraService;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.service.ProveedorService;
import com.taller.repuestos.util.Formato;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Proveedores: datos de contacto, historial de compras, productos suministrados y saldo pendiente. */
public class VistaProveedores {

    private final ProveedorService servicio;
    private final CompraService compras;
    private final PermisoService permisos = PermisoService.get();

    private final ObservableList<Proveedor> datos = FXCollections.observableArrayList();
    private TableView<Proveedor> tabla;
    private Proveedor seleccionado;

    private TextField txtNombre, txtEmpresa, txtRtn, txtTelefono, txtCorreo, txtContacto, txtDireccion;
    private TextArea txtObs;
    private CheckBox chkActivo;
    private Label lblTitulo, lblResumen;
    private TableView<Compra> tablaCompras;
    private TableView<ProveedorDAO.ProductoSuministrado> tablaProductos;

    public VistaProveedores(ProveedorService servicio, CompraService compras) {
        this.servicio = servicio;
        this.compras = compras;
    }

    @SuppressWarnings("unchecked")
    public Node crear() {
        TextField txtBuscar = new TextField();
        txtBuscar.setPromptText("🔍 Buscar por nombre, empresa, RTN, contacto o teléfono");
        FilteredList<Proveedor> filtrados = new FilteredList<>(datos, p -> true);
        txtBuscar.textProperty().addListener((o, a, n) -> {
            String q = n == null ? "" : n.trim().toLowerCase();
            filtrados.setPredicate(p -> q.isEmpty() || (p.getNombre() + " " + Componentes.nz(p.getEmpresa()) + " "
                    + Componentes.nz(p.getIdentificacionFiscal()) + " " + Componentes.nz(p.getContacto()) + " "
                    + Componentes.nz(p.getTelefono())).toLowerCase().contains(q));
        });

        tabla = Componentes.tabla("No hay proveedores registrados");
        tabla.getColumns().addAll(
                Componentes.col("Proveedor", 170, Proveedor::getNombre),
                Componentes.col("Teléfono", 100, p -> Componentes.nz(p.getTelefono())),
                Componentes.col("Contacto", 120, p -> Componentes.nz(p.getContacto())),
                Componentes.colEntero("Compras", 70, Proveedor::getCantidadCompras),
                Componentes.colMoneda("Total comprado", 120, Proveedor::getTotalComprado),
                Componentes.colMoneda("Saldo pendiente", 120, Proveedor::getSaldoPendiente),
                Componentes.colEstado("Estado", 85, Proveedor::getEstado, p -> p.isActivo() ? "ok" : "neutro"));
        tabla.setItems(filtrados);
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> { if (n != null) seleccionar(n); });

        VBox izquierda = new VBox(10, Componentes.titulo("Proveedores"),
                Componentes.ayuda("Registra a quién le compras. El saldo pendiente es lo recibido que todavía no se ha pagado."),
                txtBuscar, tabla);
        izquierda.setPadding(new Insets(16));

        SplitPane split = new SplitPane(izquierda, crearDetalle());
        split.setDividerPositions(0.55);
        recargar(0);
        return split;
    }

    @SuppressWarnings("unchecked")
    private Node crearDetalle() {
        boolean editar = permisos.tiene(Permiso.PROVEEDORES_EDITAR);
        txtNombre = new TextField();
        txtEmpresa = new TextField();
        txtRtn = new TextField();
        txtRtn.setPromptText("RTN u otra identificación fiscal");
        txtTelefono = new TextField();
        txtCorreo = new TextField();
        txtContacto = new TextField();
        txtContacto.setPromptText("Persona de contacto / vendedor");
        txtDireccion = new TextField();
        txtObs = new TextArea();
        txtObs.setPrefRowCount(2);
        chkActivo = new CheckBox("Proveedor activo");
        chkActivo.setSelected(true);

        GridPane g = Componentes.formulario();
        int f = 0;
        f = Componentes.campo(g, f, "Nombre:", txtNombre);
        f = Componentes.campo(g, f, "Empresa:", txtEmpresa);
        f = Componentes.campo(g, f, "Identificación fiscal:", txtRtn);
        f = Componentes.campo(g, f, "Teléfono:", txtTelefono);
        f = Componentes.campo(g, f, "Correo:", txtCorreo);
        f = Componentes.campo(g, f, "Contacto:", txtContacto);
        f = Componentes.campo(g, f, "Dirección:", txtDireccion);
        f = Componentes.campo(g, f, "Observaciones:", txtObs);
        g.add(chkActivo, 1, f);

        Button btnNuevo = Componentes.boton("Nuevo", "secondary-button", null, this::limpiar);
        Button btnGuardar = Componentes.boton("Guardar", "primary-button", null, this::guardar);
        btnGuardar.setDisable(!editar);
        btnNuevo.setDisable(!editar);

        lblTitulo = Componentes.seccion("Nuevo proveedor");
        lblResumen = new Label();
        lblResumen.getStyleClass().add("metric-value");

        tablaCompras = Componentes.tabla("Sin compras");
        tablaCompras.getColumns().addAll(
                Componentes.col("Número", 90, c -> Formato.numeroCompra(c.getId())),
                Componentes.col("Fecha", 110, c -> Componentes.fechaCorta(c.getFecha())),
                Componentes.colEstado("Estado", 110, Compra::getEstadoEtiqueta, VistaCompras::varianteEstado),
                Componentes.colMoneda("Total", 100, Compra::getTotal),
                Componentes.colMoneda("Saldo", 100, Compra::getSaldo));
        tablaProductos = Componentes.tabla("Sin productos recibidos");
        tablaProductos.getColumns().addAll(
                Componentes.col("Código", 90, ProveedorDAO.ProductoSuministrado::getCodigo),
                Componentes.col("Producto", 180, ProveedorDAO.ProductoSuministrado::getDescripcion),
                Componentes.colEntero("Unidades", 75, ProveedorDAO.ProductoSuministrado::getUnidades),
                Componentes.colMoneda("Último costo", 100, ProveedorDAO.ProductoSuministrado::getUltimoCosto),
                Componentes.col("Última compra", 100, p -> Componentes.fechaCorta(p.getUltimaFecha())));

        TabPane tabs = new TabPane(new Tab("Historial de compras", tablaCompras), new Tab("Productos suministrados", tablaProductos));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setPrefHeight(260);
        tabs.setVisible(permisos.tiene(Permiso.COMPRAS_VER));
        tabs.setManaged(tabs.isVisible());

        VBox detalle = new VBox(12, Componentes.panel(lblTitulo, g, new HBox(8, btnNuevo, btnGuardar)), lblResumen, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return Componentes.scroll(detalle);
    }

    private void recargar(int idSeleccionar) {
        datos.setAll(servicio.listar());
        if (idSeleccionar > 0) {
            for (Proveedor p : datos) {
                if (p.getId() == idSeleccionar) {
                    tabla.getSelectionModel().select(p);
                    break;
                }
            }
        }
    }

    private void seleccionar(Proveedor p) {
        seleccionado = p;
        lblTitulo.setText("Editar: " + p.getNombre());
        txtNombre.setText(p.getNombre());
        txtEmpresa.setText(Componentes.nz(p.getEmpresa()));
        txtRtn.setText(Componentes.nz(p.getIdentificacionFiscal()));
        txtTelefono.setText(Componentes.nz(p.getTelefono()));
        txtCorreo.setText(Componentes.nz(p.getCorreo()));
        txtContacto.setText(Componentes.nz(p.getContacto()));
        txtDireccion.setText(Componentes.nz(p.getDireccion()));
        txtObs.setText(Componentes.nz(p.getObservaciones()));
        chkActivo.setSelected(p.isActivo());
        lblResumen.setText("Total comprado: " + Formato.moneda(p.getTotalComprado())
                + "    ·    Saldo pendiente: " + Formato.moneda(p.getSaldoPendiente()));
        if (permisos.tiene(Permiso.COMPRAS_VER)) {
            tablaCompras.setItems(FXCollections.observableArrayList(compras.listar(p.getId(), null, null, null)));
            tablaProductos.setItems(FXCollections.observableArrayList(servicio.productosSuministrados(p.getId())));
        }
    }

    private void limpiar() {
        seleccionado = null;
        tabla.getSelectionModel().clearSelection();
        lblTitulo.setText("Nuevo proveedor");
        for (TextField t : new TextField[]{txtNombre, txtEmpresa, txtRtn, txtTelefono, txtCorreo, txtContacto, txtDireccion}) t.clear();
        txtObs.clear();
        chkActivo.setSelected(true);
        lblResumen.setText("");
        tablaCompras.getItems().clear();
        tablaProductos.getItems().clear();
        txtNombre.requestFocus();
    }

    private void guardar() {
        Proveedor p = new Proveedor();
        p.setNombre(txtNombre.getText());
        p.setEmpresa(txtEmpresa.getText());
        p.setIdentificacionFiscal(txtRtn.getText());
        p.setTelefono(txtTelefono.getText());
        p.setCorreo(txtCorreo.getText());
        p.setContacto(txtContacto.getText());
        p.setDireccion(txtDireccion.getText());
        p.setObservaciones(txtObs.getText());
        p.setActivo(chkActivo.isSelected());
        try {
            if (seleccionado == null) {
                servicio.crear(p);
                Alertas.info("Proveedor guardado", "Se registró a " + p.getNombre() + ".");
            } else {
                servicio.actualizar(seleccionado, p);
                Alertas.info("Proveedor actualizado", "Los cambios se guardaron.");
            }
            recargar(p.getId());
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo guardar", ex.getMessage());
        }
    }
}
