package com.taller.repuestos.ui;

import com.taller.repuestos.dao.CatalogoDAO;
import com.taller.repuestos.model.Cliente;
import com.taller.repuestos.model.OrdenTrabajo;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Vehiculo;
import com.taller.repuestos.service.CatalogoService;
import com.taller.repuestos.service.ClienteService;
import com.taller.repuestos.service.OrdenTrabajoService;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.service.VehiculoService;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Iconos;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Vehículos de los clientes con su historial de órdenes, servicios y repuestos. */
public class VistaVehiculos {

    private final VehiculoService servicio;
    private final ClienteService clientes;
    private final CatalogoService catalogo;
    private final OrdenTrabajoService ordenes;
    private final Consumer<Vehiculo> nuevaOrden; // abre el taller con una orden para este vehículo
    private final PermisoService permisos = PermisoService.get();

    private TableView<Vehiculo> tabla;
    private TextField txtBuscar;
    private ComboBox<Object> comboCliente;
    private CheckBox chkInactivos;
    private Label lblFicha;
    private TableView<OrdenTrabajo> tablaOrdenes;
    private TableView<String[]> tablaRepuestos;

    public VistaVehiculos(VehiculoService servicio, ClienteService clientes, CatalogoService catalogo,
                          OrdenTrabajoService ordenes, Consumer<Vehiculo> nuevaOrden) {
        this.servicio = servicio;
        this.clientes = clientes;
        this.catalogo = catalogo;
        this.ordenes = ordenes;
        this.nuevaOrden = nuevaOrden;
    }

    @SuppressWarnings("unchecked")
    public Node crear() {
        txtBuscar = new TextField();
        txtBuscar.setPromptText("🔍 Placa, marca, modelo, VIN o cliente");
        txtBuscar.textProperty().addListener((o, a, n) -> cargar());
        comboCliente = new ComboBox<>();
        comboCliente.getItems().add("Todos los clientes");
        comboCliente.getItems().addAll(clientes.listar());
        comboCliente.getSelectionModel().selectFirst();
        comboCliente.setOnAction(e -> cargar());
        chkInactivos = new CheckBox("Ver inactivos");
        chkInactivos.setOnAction(e -> cargar());

        tabla = Componentes.tabla("No hay vehículos registrados");
        tabla.getColumns().addAll(
                Componentes.col("Placa", 85, v -> Componentes.nz(v.getPlaca())),
                Componentes.col("Vehículo", 170, v -> v.getMarca() + " " + v.getModelo() + (v.getAnio() == null ? "" : " " + v.getAnio())),
                Componentes.col("Motor", 80, v -> Componentes.nz(v.getMotor())),
                Componentes.col("Cliente", 150, v -> Componentes.nz(v.getClienteNombre())),
                Componentes.col("Km", 75, v -> v.getKilometraje() == null ? "" : String.format("%,d", v.getKilometraje())),
                Componentes.colEstado("Estado", 90, v -> v.isActivo() ? "Activo" : "Inactivo", v -> v.isActivo() ? "ok" : "neutro"));
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> mostrar(n));
        tabla.setOnMouseClicked(e -> { if (e.getClickCount() == 2) editar(); });

        boolean editar = permisos.tiene(Permiso.VEHICULOS_EDITAR);
        Button btnNuevo = Componentes.boton("Nuevo vehículo", "primary-button", Iconos.AUTO, this::nuevo);
        Button btnEditar = Componentes.boton("Editar", "secondary-button", null, this::editar);
        btnNuevo.setVisible(editar);
        btnNuevo.setManaged(editar);
        btnEditar.setVisible(editar);
        btnEditar.setManaged(editar);
        btnEditar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());

        VBox izquierda = new VBox(10,
                Componentes.fila(Componentes.titulo("Vehículos"), Componentes.espaciador(), btnNuevo),
                Componentes.ayuda("Cada cliente puede tener varios vehículos. Selecciona uno para ver sus órdenes de trabajo, "
                        + "servicios y repuestos instalados."),
                Componentes.fila(comboCliente, chkInactivos), txtBuscar, tabla, Componentes.fila(btnEditar));
        izquierda.setPadding(new Insets(16));

        // ---- Historial ----
        lblFicha = new Label("Selecciona un vehículo");
        lblFicha.getStyleClass().add("metric-value");
        lblFicha.setWrapText(true);
        tablaOrdenes = Componentes.tabla("Sin órdenes de trabajo");
        tablaOrdenes.getColumns().addAll(
                Componentes.col("Orden", 85, o -> Formato.numeroOrden(o.getId())),
                Componentes.col("Ingreso", 110, o -> Componentes.fechaCorta(o.getFechaIngreso())),
                Componentes.colEstado("Estado", 110, OrdenTrabajo::getEstadoEtiqueta, VistaOrdenes::varianteEstado),
                Componentes.col("Km", 75, o -> o.getKilometraje() == null ? "" : String.format("%,d", o.getKilometraje())),
                Componentes.col("Trabajo", 200, o -> Componentes.nz(o.getProblema())),
                Componentes.colMoneda("Total", 95, OrdenTrabajo::getTotal));
        tablaRepuestos = Componentes.tabla("Sin repuestos instalados");
        tablaRepuestos.getColumns().addAll(
                Componentes.col("Fecha", 110, r -> r[0]),
                Componentes.col("Orden", 85, r -> r[1]),
                Componentes.col("Código", 90, r -> r[2]),
                Componentes.col("Repuesto / servicio", 220, r -> r[3]),
                Componentes.col("Cant.", 55, r -> r[4]));
        Button btnOrden = Componentes.boton("Nueva orden de trabajo", "primary-button", Iconos.DESTORNILLADOR, () -> {
            Vehiculo v = tabla.getSelectionModel().getSelectedItem();
            if (v != null) nuevaOrden.accept(v);
        });
        btnOrden.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        btnOrden.setVisible(permisos.tiene(Permiso.ORDENES_EDITAR));
        btnOrden.setManaged(btnOrden.isVisible());

        VBox derecha = new VBox(10, Componentes.seccion("Historial del vehículo"), lblFicha, btnOrden,
                Componentes.seccion("Órdenes de trabajo"), tablaOrdenes,
                Componentes.seccion("Repuestos y servicios realizados"), tablaRepuestos);
        derecha.setPadding(new Insets(16));

        SplitPane split = new SplitPane(izquierda, derecha);
        split.setDividerPositions(0.52);
        cargar();
        return split;
    }

    private void cargar() {
        Integer clienteId = comboCliente.getValue() instanceof Cliente c ? c.getId() : null;
        tabla.setItems(FXCollections.observableArrayList(servicio.buscar(clienteId, txtBuscar.getText(), !chkInactivos.isSelected())));
    }

    private void mostrar(Vehiculo v) {
        if (v == null) {
            lblFicha.setText("Selecciona un vehículo");
            tablaOrdenes.getItems().clear();
            tablaRepuestos.getItems().clear();
            return;
        }
        lblFicha.setText(v.getDescripcion() + "\nDueño: " + Componentes.nz(v.getClienteNombre())
                + (v.getVin() != null ? "   ·   VIN: " + v.getVin() : "")
                + (v.getCombustible() != null ? "   ·   " + v.getCombustible() : "")
                + (v.getTransmision() != null ? "   ·   " + v.getTransmision() : "")
                + (v.getColor() != null ? "   ·   Color: " + v.getColor() : "")
                + (v.getKilometraje() != null ? "\nÚltimo kilometraje: " + String.format("%,d", v.getKilometraje()) + " km" : ""));
        if (!permisos.tiene(Permiso.ORDENES_VER)) return;
        List<OrdenTrabajo> lista = ordenes.listar(null, v.getId(), null, null, null);
        tablaOrdenes.setItems(FXCollections.observableArrayList(lista));
        List<String[]> filas = new ArrayList<>();
        for (OrdenTrabajo resumen : lista) {
            if (OrdenTrabajo.CANCELADO.equals(resumen.getEstado())) continue;
            OrdenTrabajo o = ordenes.obtener(resumen.getId());
            String fecha = Componentes.fechaCorta(o.getFechaIngreso());
            String num = Formato.numeroOrden(o.getId());
            for (OrdenTrabajo.Servicio s : o.getServicios()) filas.add(new String[]{fecha, num, "Servicio", s.descripcion, "1"});
            for (OrdenTrabajo.RepuestoUsado r : o.getRepuestos()) {
                filas.add(new String[]{fecha, num, Componentes.nz(r.codigo), r.descripcion, String.valueOf(r.cantidad)});
            }
        }
        tablaRepuestos.setItems(FXCollections.observableArrayList(filas));
    }

    private void nuevo() {
        Cliente fijo = comboCliente.getValue() instanceof Cliente c ? c : null;
        Vehiculo v = DialogoVehiculo.mostrar(servicio, clientes.listar(), catalogo.nombres(CatalogoDAO.Tipo.MARCA), null, fijo);
        if (v != null) seleccionarTras(v.getId());
    }

    private void editar() {
        Vehiculo sel = tabla.getSelectionModel().getSelectedItem();
        if (sel == null || !permisos.tiene(Permiso.VEHICULOS_EDITAR)) return;
        Vehiculo v = DialogoVehiculo.mostrar(servicio, clientes.listar(), catalogo.nombres(CatalogoDAO.Tipo.MARCA),
                servicio.obtener(sel.getId()), null);
        if (v != null) seleccionarTras(v.getId());
    }

    private void seleccionarTras(int id) {
        cargar();
        for (Vehiculo x : tabla.getItems()) {
            if (x.getId() == id) { tabla.getSelectionModel().select(x); break; }
        }
    }
}
