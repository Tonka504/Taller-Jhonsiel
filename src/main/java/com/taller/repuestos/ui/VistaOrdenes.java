package com.taller.repuestos.ui;

import com.taller.repuestos.dao.CatalogoDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Cliente;
import com.taller.repuestos.model.Compatibilidad;
import com.taller.repuestos.model.ItemCarrito;
import com.taller.repuestos.model.OrdenTrabajo;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.model.Vehiculo;
import com.taller.repuestos.service.CalculoVenta;
import com.taller.repuestos.service.CatalogoService;
import com.taller.repuestos.service.ClienteService;
import com.taller.repuestos.service.ConfiguracionService;
import com.taller.repuestos.service.OrdenTrabajoService;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.service.VehiculoService;
import com.taller.repuestos.service.VentaService;
import com.taller.repuestos.util.Archivos;
import com.taller.repuestos.util.DocumentoPDF;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Iconos;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Taller: órdenes de trabajo. A la izquierda la lista (filtrable por estado); a la derecha la orden
 * seleccionada con su diagnóstico, servicios, repuestos y acciones (consumir, facturar, entregar, cancelar).
 */
public class VistaOrdenes {

    private final OrdenTrabajoService servicio;
    private final VehiculoService vehiculos;
    private final ClienteService clientes;
    private final CatalogoService catalogo;
    private final List<Repuesto> productos;
    private final Supplier<Map<Integer, List<Compatibilidad>>> compat;
    private final Runnable alCambiarInventario;
    private final PermisoService permisos = PermisoService.get();

    private TableView<OrdenTrabajo> tabla;
    private ComboBox<String> comboEstado;
    private TextField txtBuscar;
    private VBox panelDetalle;
    private OrdenTrabajo actual;

    public VistaOrdenes(OrdenTrabajoService servicio, VehiculoService vehiculos, ClienteService clientes, CatalogoService catalogo,
                        List<Repuesto> productos, Supplier<Map<Integer, List<Compatibilidad>>> compat, Runnable alCambiarInventario) {
        this.servicio = servicio;
        this.vehiculos = vehiculos;
        this.clientes = clientes;
        this.catalogo = catalogo;
        this.productos = productos;
        this.compat = compat;
        this.alCambiarInventario = alCambiarInventario;
    }

    static String varianteEstado(OrdenTrabajo o) {
        return switch (o.getEstado()) {
            case OrdenTrabajo.ENTREGADO -> "ok";
            case OrdenTrabajo.LISTO -> "info";
            case OrdenTrabajo.CANCELADO -> "neutro";
            default -> "alerta";
        };
    }

    @SuppressWarnings("unchecked")
    public Node crear() {
        comboEstado = new ComboBox<>();
        comboEstado.getItems().add("En el taller (abiertas)");
        for (String e : OrdenTrabajo.ESTADOS) comboEstado.getItems().add(OrdenTrabajo.etiqueta(e));
        comboEstado.getItems().add("Todas");
        comboEstado.getSelectionModel().selectFirst();
        comboEstado.setOnAction(e -> cargar(0));
        txtBuscar = new TextField();
        txtBuscar.setPromptText("🔍 N° de orden, cliente, placa, vehículo o mecánico");
        txtBuscar.textProperty().addListener((o, a, n) -> cargar(0));
        HBox.setHgrow(txtBuscar, Priority.ALWAYS);

        tabla = Componentes.tabla("No hay órdenes con estos filtros");
        tabla.getColumns().addAll(
                Componentes.col("Orden", 85, o -> Formato.numeroOrden(o.getId())),
                Componentes.col("Ingreso", 105, o -> Componentes.fechaCorta(o.getFechaIngreso())),
                Componentes.col("Cliente", 140, OrdenTrabajo::getClienteNombre),
                Componentes.col("Vehículo", 160, o -> Componentes.nz(o.getVehiculoDescripcion())),
                Componentes.col("Mecánico", 100, o -> Componentes.nz(o.getMecanico())),
                Componentes.colEstado("Estado", 110, OrdenTrabajo::getEstadoEtiqueta, VistaOrdenes::varianteEstado),
                Componentes.colMoneda("Total", 95, OrdenTrabajo::getTotal));
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> {
            if (n != null) mostrar(servicio.obtener(n.getId()));
        });

        Button btnNueva = Componentes.boton("Nueva orden", "primary-button", Iconos.DESTORNILLADOR, () -> nuevaOrden(null));
        btnNueva.setVisible(permisos.tiene(Permiso.ORDENES_EDITAR));
        btnNueva.setManaged(btnNueva.isVisible());

        VBox izquierda = new VBox(10,
                Componentes.fila(Componentes.titulo("Órdenes de trabajo"), Componentes.espaciador(), btnNueva),
                Componentes.ayuda("Recibido → Diagnóstico → En reparación → Listo → Entregado. Los repuestos salen del inventario "
                        + "al confirmar su consumo (o al facturar)."),
                Componentes.fila(comboEstado, txtBuscar), tabla);
        izquierda.setPadding(new Insets(16));

        panelDetalle = new VBox(12, Componentes.ayuda("Selecciona una orden de la lista o crea una nueva."));
        SplitPane split = new SplitPane(izquierda, Componentes.scroll(panelDetalle));
        split.setDividerPositions(0.48);
        cargar(0);
        return split;
    }

    private void cargar(int seleccionar) {
        int idx = comboEstado.getSelectionModel().getSelectedIndex();
        String estado = idx == 0 ? "ABIERTAS" : idx > 0 && idx <= OrdenTrabajo.ESTADOS.size() ? OrdenTrabajo.ESTADOS.get(idx - 1) : null;
        tabla.setItems(FXCollections.observableArrayList(servicio.listar(estado, null, txtBuscar.getText(), null, null)));
        if (seleccionar > 0) {
            for (OrdenTrabajo o : tabla.getItems()) {
                if (o.getId() == seleccionar) { tabla.getSelectionModel().select(o); return; }
            }
            mostrar(servicio.obtener(seleccionar));
        }
    }

    private void refrescar() {
        int id = actual == null ? 0 : actual.getId();
        cargar(id);
        if (id > 0) mostrar(servicio.obtener(id));
    }

    // ------------------------------------------------------------------
    // NUEVA ORDEN
    // ------------------------------------------------------------------

    /** Abre el formulario de nueva orden (con el vehículo ya elegido si viene de la pantalla de vehículos). */
    public void nuevaOrden(Vehiculo vehiculoInicial) {
        if (!permisos.tiene(Permiso.ORDENES_EDITAR)) return;
        Dialog<OrdenTrabajo> d = new Dialog<>();
        d.setTitle("Nueva orden de trabajo");
        d.setHeaderText("Recepción del vehículo en el taller");
        ButtonType ok = new ButtonType("Crear orden", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        List<Cliente> listaClientes = clientes.listar();
        ComboBox<Cliente> comboCliente = new ComboBox<>();
        for (Cliente c : listaClientes) if (c.isActivo()) comboCliente.getItems().add(c);
        comboCliente.setPromptText("Cliente");
        ComboBox<Vehiculo> comboVeh = new ComboBox<>();
        comboVeh.setPromptText("Vehículo del cliente");
        Runnable cargarVeh = () -> {
            Cliente c = comboCliente.getValue();
            comboVeh.getItems().setAll(c == null ? List.of() : vehiculos.buscar(c.getId(), null, true));
            if (comboVeh.getItems().size() == 1) comboVeh.getSelectionModel().selectFirst();
        };
        comboCliente.setOnAction(e -> cargarVeh.run());
        Button btnVeh = Componentes.boton("+ Vehículo", "secondary-button", null, () -> {
            Vehiculo v = DialogoVehiculo.mostrar(vehiculos, listaClientes, catalogo.nombres(CatalogoDAO.Tipo.MARCA), null, comboCliente.getValue());
            if (v != null) {
                for (Cliente c : comboCliente.getItems()) if (c.getId() == v.getClienteId()) comboCliente.setValue(c);
                cargarVeh.run();
                for (Vehiculo x : comboVeh.getItems()) if (x.getId() == v.getId()) comboVeh.setValue(x);
            }
        });
        btnVeh.setVisible(permisos.tiene(Permiso.VEHICULOS_EDITAR));
        TextField txtKm = new TextField();
        txtKm.setPromptText("Kilometraje actual");
        TextArea txtProblema = new TextArea();
        txtProblema.setPromptText("Qué reporta el cliente / servicio solicitado");
        txtProblema.setPrefRowCount(3);
        DatePicker dpPromesa = Componentes.fecha(null);
        dpPromesa.setPromptText("Entrega prometida");
        ComboBox<String> comboMec = new ComboBox<>(FXCollections.observableArrayList(servicio.mecanicos()));
        comboMec.setEditable(true);
        comboMec.setPromptText("Sin asignar");

        if (vehiculoInicial != null) {
            for (Cliente c : comboCliente.getItems()) if (c.getId() == vehiculoInicial.getClienteId()) comboCliente.setValue(c);
            cargarVeh.run();
            for (Vehiculo x : comboVeh.getItems()) if (x.getId() == vehiculoInicial.getId()) comboVeh.setValue(x);
            if (vehiculoInicial.getKilometraje() != null) txtKm.setPromptText("Último: " + vehiculoInicial.getKilometraje());
        }
        comboVeh.setOnAction(e -> {
            Vehiculo v = comboVeh.getValue();
            txtKm.setPromptText(v != null && v.getKilometraje() != null ? "Último: " + v.getKilometraje() : "Kilometraje actual");
        });

        GridPane g = Componentes.formulario();
        int f = 0;
        f = Componentes.campo(g, f, "Cliente:", comboCliente);
        f = Componentes.campo(g, f, "Vehículo:", new HBox(8, comboVeh, btnVeh));
        f = Componentes.campo(g, f, "Kilometraje:", txtKm);
        f = Componentes.campo(g, f, "Problema / servicio:", txtProblema);
        f = Componentes.campo(g, f, "Entrega prometida:", dpPromesa);
        Componentes.campo(g, f, "Mecánico:", comboMec);
        VBox v = new VBox(g);
        v.setPrefWidth(540);
        d.getDialogPane().setContent(v);
        Alertas.estilizar(d);

        OrdenTrabajo[] creada = new OrdenTrabajo[1];
        Button b = (Button) d.getDialogPane().lookupButton(ok);
        b.getStyleClass().add("primary-button");
        b.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                Integer km = Componentes.entero(txtKm.getText().replace(",", ""));
                creada[0] = servicio.crear(comboVeh.getValue(), km, txtProblema.getText(), dpPromesa.getValue(),
                        comboMec.getEditor().getText());
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Kilometraje inválido", "Escribe el kilometraje como número entero.");
                ev.consume();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo crear la orden", ex.getMessage());
                ev.consume();
            }
        });
        d.setResultConverter(x -> x == ok ? creada[0] : null);
        Platform.runLater(() -> (vehiculoInicial == null ? comboCliente : txtProblema).requestFocus());
        OrdenTrabajo o = d.showAndWait().orElse(null);
        if (o != null) {
            comboEstado.getSelectionModel().selectFirst();
            cargar(o.getId());
        }
    }

    // ------------------------------------------------------------------
    // DETALLE DE LA ORDEN
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void mostrar(OrdenTrabajo o) {
        actual = o;
        panelDetalle.getChildren().clear();
        if (o == null) return;
        boolean editarOrden = permisos.tiene(Permiso.ORDENES_EDITAR);
        boolean editable = o.isEditable() && editarOrden;

        Label titulo = Componentes.seccion("Orden " + Formato.numeroOrden(o.getId()));
        Label chip = Componentes.chip(o.getEstadoEtiqueta(), varianteEstado(o));
        Label info = new Label(o.getClienteNombre() + "  ·  " + Componentes.nz(o.getVehiculoDescripcion())
                + "\nIngreso: " + Componentes.fechaCorta(o.getFechaIngreso()) + "  ·  recibido por " + o.getUsuario()
                + (o.getFechaEntrega() != null ? "  ·  entregado: " + Componentes.fechaCorta(o.getFechaEntrega()) : "")
                + (o.isFacturada() ? "  ·  factura " + Formato.numeroFactura(o.getVentaId()) : ""));
        info.setWrapText(true);
        info.getStyleClass().add("hint-label");

        // ---- Estado ----
        ComboBox<String> comboNuevoEstado = new ComboBox<>();
        for (String e : OrdenTrabajo.ESTADOS_ABIERTOS) comboNuevoEstado.getItems().add(OrdenTrabajo.etiqueta(e));
        comboNuevoEstado.setValue(o.getEstadoEtiqueta());
        comboNuevoEstado.setDisable(!o.isAbierta() || !editarOrden);
        comboNuevoEstado.setOnAction(e -> {
            int i = comboNuevoEstado.getSelectionModel().getSelectedIndex();
            if (i < 0 || OrdenTrabajo.ESTADOS_ABIERTOS.get(i).equals(o.getEstado())) return;
            try {
                servicio.cambiarEstado(o, OrdenTrabajo.ESTADOS_ABIERTOS.get(i));
                refrescar();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo cambiar el estado", ex.getMessage());
            }
        });

        // ---- Datos ----
        TextArea txtProblema = area(o.getProblema(), "Problema reportado");
        TextArea txtDiag = area(o.getDiagnostico(), "Diagnóstico del mecánico");
        TextArea txtObs = area(o.getObservaciones(), "Observaciones");
        TextField txtKm = new TextField(o.getKilometraje() == null ? "" : String.valueOf(o.getKilometraje()));
        DatePicker dpPromesa = Componentes.fecha(o.getFechaPromesa() == null ? null : LocalDate.parse(o.getFechaPromesa()));
        ComboBox<String> comboMec = new ComboBox<>(FXCollections.observableArrayList(servicio.mecanicos()));
        comboMec.setEditable(true);
        comboMec.getEditor().setText(Componentes.nz(o.getMecanico()));
        for (javafx.scene.control.Control c : new javafx.scene.control.Control[]{txtProblema, txtDiag, txtObs, txtKm, dpPromesa, comboMec}) {
            c.setDisable(!o.isAbierta() || !editarOrden);
        }
        GridPane g = Componentes.formulario();
        int f = 0;
        f = Componentes.campo(g, f, "Problema:", txtProblema);
        f = Componentes.campo(g, f, "Diagnóstico:", txtDiag);
        f = Componentes.campo(g, f, "Observaciones:", txtObs);
        f = Componentes.campo(g, f, "Kilometraje:", txtKm);
        f = Componentes.campo(g, f, "Entrega prometida:", dpPromesa);
        Componentes.campo(g, f, "Mecánico:", comboMec);
        Button btnGuardar = Componentes.boton("Guardar datos", "secondary-button", null, () -> {
            try {
                o.setProblema(txtProblema.getText());
                o.setDiagnostico(txtDiag.getText());
                o.setObservaciones(txtObs.getText());
                o.setKilometraje(Componentes.entero(txtKm.getText().replace(",", "")));
                o.setFechaPromesa(dpPromesa.getValue() == null ? null : dpPromesa.getValue().toString());
                o.setMecanico(comboMec.getEditor().getText());
                servicio.guardarDatos(o);
                refrescar();
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Kilometraje inválido", "Escribe el kilometraje como número entero.");
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo guardar", ex.getMessage());
            }
        });
        btnGuardar.setDisable(!o.isAbierta() || !editarOrden);

        // ---- Servicios ----
        TableView<OrdenTrabajo.Servicio> tServ = Componentes.tabla("Sin servicios");
        tServ.setPrefHeight(150);
        VBox.setVgrow(tServ, Priority.NEVER);
        tServ.getColumns().addAll(
                Componentes.col("Servicio / mano de obra", 220, s -> s.descripcion),
                Componentes.col("Mecánico", 110, s -> Componentes.nz(s.mecanico)),
                Componentes.col("Horas", 60, s -> s.horas > 0 ? String.valueOf(s.horas) : ""),
                Componentes.colMoneda("Precio", 90, s -> s.precio));
        tServ.setItems(FXCollections.observableArrayList(o.getServicios()));
        TextField txtServ = new TextField();
        txtServ.setPromptText("Ej: Cambio de pastillas, alineación...");
        HBox.setHgrow(txtServ, Priority.ALWAYS);
        TextField txtHoras = new TextField();
        txtHoras.setPromptText("Horas");
        txtHoras.setPrefWidth(65);
        TextField txtPrecioServ = new TextField();
        txtPrecioServ.setPromptText("Precio");
        txtPrecioServ.setPrefWidth(90);
        Runnable agregarServ = () -> {
            try {
                Double h = Componentes.monto(txtHoras.getText());
                Double p = Componentes.monto(txtPrecioServ.getText());
                servicio.agregarServicio(o, txtServ.getText(), comboMec.getEditor().getText(), h == null ? 0 : h, p == null ? 0 : p);
                refrescar();
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Dato inválido", "Horas y precio deben ser números.");
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo agregar", ex.getMessage());
            }
        };
        txtPrecioServ.setOnAction(e -> agregarServ.run());
        Button btnAddServ = Componentes.boton("Agregar", "primary-button", null, agregarServ);
        Button btnDelServ = Componentes.boton("Quitar", "danger-button", null, () -> {
            OrdenTrabajo.Servicio s = tServ.getSelectionModel().getSelectedItem();
            if (s == null) return;
            try {
                servicio.quitarServicio(o, s);
                refrescar();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo quitar", ex.getMessage());
            }
        });
        HBox filaServ = Componentes.fila(txtServ, txtHoras, txtPrecioServ, btnAddServ, btnDelServ);
        filaServ.setVisible(editable);
        filaServ.setManaged(editable);

        // ---- Repuestos ----
        TableView<OrdenTrabajo.RepuestoUsado> tRep = Componentes.tabla("Sin repuestos");
        tRep.setPrefHeight(160);
        VBox.setVgrow(tRep, Priority.NEVER);
        tRep.getColumns().addAll(
                Componentes.col("Código", 85, r -> Componentes.nz(r.codigo)),
                Componentes.col("Repuesto", 190, r -> r.descripcion),
                Componentes.colEntero("Cant.", 55, r -> r.cantidad),
                Componentes.colMoneda("P. unit.", 85, r -> r.precioUnitario),
                Componentes.colMoneda("Subtotal", 90, OrdenTrabajo.RepuestoUsado::getSubtotal),
                Componentes.colEstado("Inventario", 100, r -> r.consumido ? "Descontado" : "Por descontar",
                        r -> r.consumido ? "ok" : "alerta"));
        tRep.setItems(FXCollections.observableArrayList(o.getRepuestos()));
        TextField txtCant = new TextField("1");
        txtCant.setPrefWidth(55);
        SelectorProducto selector = new SelectorProducto(productos, compat.get(), r -> {
            try {
                int cant = Integer.parseInt(txtCant.getText().trim());
                servicio.agregarRepuesto(o, r, cant, r.getPrecio());
                txtCant.setText("1");
                refrescar();
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Cantidad inválida", "Escribe la cantidad como número entero.");
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo agregar", ex.getMessage());
            }
        });
        Button btnDelRep = Componentes.boton("Quitar repuesto", "danger-button", null, () -> {
            OrdenTrabajo.RepuestoUsado r = tRep.getSelectionModel().getSelectedItem();
            if (r == null) return;
            if (r.consumido && !Alertas.confirmar("Quitar repuesto", "Este repuesto ya se descontó del inventario. "
                    + "Al quitarlo, regresa al inventario. ¿Continuar?")) return;
            try {
                servicio.quitarRepuesto(o, r);
                alCambiarInventario.run();
                refrescar();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo quitar", ex.getMessage());
            }
        });
        Button btnConsumir = Componentes.boton("Confirmar consumo", "secondary-button", Iconos.CAJA,
                "Descuenta del inventario los repuestos que se instalaron", () -> {
                    try {
                        int n = servicio.consumirRepuestos(o);
                        alCambiarInventario.run();
                        refrescar();
                        Alertas.info("Inventario actualizado", n == 0 ? "No había repuestos pendientes de descontar."
                                : "Se descontaron " + n + " línea(s) del inventario.");
                    } catch (ReglaNegocioException ex) {
                        Alertas.advertencia("No se pudo descontar", ex.getMessage());
                    }
                });
        btnConsumir.setDisable(!o.tieneRepuestosSinConsumir());
        VBox bloqueRep = new VBox(6, Componentes.fila(new Label("Cantidad:"), txtCant,
                Componentes.ayuda("y busca el repuesto (Enter o doble clic para agregarlo):")), selector,
                Componentes.fila(btnDelRep, btnConsumir));
        bloqueRep.setVisible(editable);
        bloqueRep.setManaged(editable);

        // ---- Totales y acciones ----
        Label lblTotal = new Label("Mano de obra " + Formato.moneda(o.getTotalServicios()) + "   ·   Repuestos "
                + Formato.moneda(o.getTotalRepuestos()) + "   ·   TOTAL " + Formato.moneda(o.getTotal()));
        lblTotal.getStyleClass().add("total-label");

        Button btnFacturar = Componentes.boton("Facturar y entregar", "primary-button", Iconos.FACTURA, () -> facturar(o));
        Button btnSinCobro = Componentes.boton("Entregar sin cobro", "secondary-button", null, () -> {
            String motivo = Alertas.pedirTexto("Entregar sin cobro", "Motivo (garantía, revisión gratuita...):", "");
            if (motivo == null) return;
            try {
                servicio.entregarSinCobro(o, motivo);
                alCambiarInventario.run();
                refrescar();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo entregar", ex.getMessage());
            }
        });
        Button btnCancelar = Componentes.boton("Cancelar orden", "danger-button", null, () -> {
            String motivo = Alertas.pedirTexto("Cancelar orden", "Motivo de la cancelación de " + Formato.numeroOrden(o.getId())
                    + " (los repuestos descontados regresan al inventario):", "");
            if (motivo == null) return;
            try {
                servicio.cancelar(o, motivo);
                alCambiarInventario.run();
                refrescar();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo cancelar", ex.getMessage());
            }
        });
        Button btnPdf = Componentes.boton("Imprimir orden", "secondary-button", Iconos.PORTAPAPELES, () -> {
            try {
                Archivos.abrir(DocumentoPDF.orden(servicio.obtener(o.getId())));
            } catch (Exception ex) {
                Alertas.error("No se pudo generar el PDF", ex.getMessage());
            }
        });
        boolean abierta = o.isAbierta() && !o.isFacturada();
        visible(btnFacturar, abierta && permisos.tiene(Permiso.ORDENES_FACTURAR));
        visible(btnSinCobro, abierta && permisos.tiene(Permiso.ORDENES_FACTURAR) && o.getTotal() <= 0);
        visible(btnCancelar, abierta && permisos.tiene(Permiso.ORDENES_CANCELAR));

        panelDetalle.getChildren().addAll(
                Componentes.fila(titulo, chip, Componentes.espaciador(), new Label("Estado:"), comboNuevoEstado),
                info,
                Componentes.panel(g, Componentes.fila(btnGuardar)),
                Componentes.panel(Componentes.seccion("Servicios y mano de obra"), tServ, filaServ),
                Componentes.panel(Componentes.seccion("Repuestos"), tRep, bloqueRep),
                lblTotal,
                Componentes.fila(btnFacturar, btnSinCobro, btnCancelar, btnPdf));
    }

    private void facturar(OrdenTrabajo o) {
        OrdenTrabajo completa = servicio.obtener(o.getId());
        if (completa == null) return;
        // Total con impuestos, calculado igual que en el punto de venta
        List<ItemCarrito> items = new ArrayList<>();
        for (OrdenTrabajo.Servicio s : completa.getServicios()) {
            items.add(new ItemCarrito(new Repuesto(0, "", "", "", "", 0, s.precio, 0), 1));
        }
        for (OrdenTrabajo.RepuestoUsado r : completa.getRepuestos()) {
            items.add(new ItemCarrito(new Repuesto(r.repuestoId, "", "", "", "", 0, r.precioUnitario, 0), r.cantidad));
        }
        ConfiguracionService config = ConfiguracionService.get();
        double total;
        try {
            total = CalculoVenta.calcular(items, null, 0, config.impuestoPorcentaje(), config.impuestoIncluido()).total;
        } catch (IllegalArgumentException ex) {
            total = completa.getTotal();
        }

        Dialog<VentaService.Resultado> d = new Dialog<>();
        d.setTitle("Facturar orden");
        d.setHeaderText(Formato.numeroOrden(o.getId()) + " · " + o.getClienteNombre() + "\nTotal a cobrar: " + Formato.moneda(total));
        ButtonType ok = new ButtonType("Facturar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        ComboBox<String> comboMetodo = new ComboBox<>(FXCollections.observableArrayList(VentaService.METODOS_PAGO));
        comboMetodo.setValue("Efectivo");
        TextField txtRecibido = new TextField(String.format("%.2f", total));
        Label lblCambio = new Label();
        DatePicker dpLimite = Componentes.fecha(LocalDate.now().plusDays(config.diasCredito()));
        final double totalFinal = total;
        Runnable actualizar = () -> {
            boolean efectivo = "Efectivo".equals(comboMetodo.getValue());
            txtRecibido.setDisable(!efectivo);
            dpLimite.setDisable(!"Crédito".equals(comboMetodo.getValue()));
            try {
                double rec = Formato.leerMonto(txtRecibido.getText());
                lblCambio.setText(efectivo ? "Cambio: " + Formato.moneda(Math.max(0, rec - totalFinal)) : "");
            } catch (NumberFormatException ex) {
                lblCambio.setText("");
            }
        };
        comboMetodo.setOnAction(e -> actualizar.run());
        txtRecibido.textProperty().addListener((x, a, n) -> actualizar.run());
        actualizar.run();
        GridPane g = Componentes.formulario();
        int f = Componentes.campo(g, 0, "Forma de pago:", comboMetodo);
        f = Componentes.campo(g, f, "Monto recibido:", txtRecibido);
        f = Componentes.campo(g, f, "", lblCambio);
        Componentes.campo(g, f, "Fecha límite (crédito):", dpLimite);
        VBox v = new VBox(10, g, Componentes.ayuda("Se genera la factura con los servicios y repuestos de la orden; "
                + "los repuestos pendientes se descuentan del inventario y la orden queda Entregada."));
        v.setPrefWidth(440);
        d.getDialogPane().setContent(v);
        Alertas.estilizar(d);
        VentaService.Resultado[] res = new VentaService.Resultado[1];
        Button b = (Button) d.getDialogPane().lookupButton(ok);
        b.getStyleClass().add("primary-button");
        b.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                Double recibido = "Efectivo".equals(comboMetodo.getValue()) ? Formato.leerMonto(txtRecibido.getText()) : null;
                res[0] = servicio.facturar(o, comboMetodo.getValue(), recibido, dpLimite.getValue());
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Monto inválido", "Escribe el monto recibido como número.");
                ev.consume();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo facturar", ex.getMessage());
                ev.consume();
            }
        });
        d.setResultConverter(x -> x == ok ? res[0] : null);
        VentaService.Resultado r = d.showAndWait().orElse(null);
        alCambiarInventario.run();
        refrescar();
        if (r != null) {
            DialogoFactura.mostrar(r.factura, new ArrayList<>());
        }
    }

    private static TextArea area(String texto, String prompt) {
        TextArea t = new TextArea(texto == null ? "" : texto);
        t.setPromptText(prompt);
        t.setPrefRowCount(2);
        t.setWrapText(true);
        return t;
    }

    private static void visible(Node n, boolean v) {
        n.setVisible(v);
        n.setManaged(v);
    }
}
