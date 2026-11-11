package com.taller.repuestos.ui;

import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Cotizacion;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.service.CotizacionService;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.util.Archivos;
import com.taller.repuestos.util.DocumentoPDF;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Iconos;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/** Cotizaciones: consultar, imprimir, anular y cargarlas en el punto de venta para cobrarlas. */
public class VistaCotizaciones {

    private final CotizacionService servicio;
    private final Consumer<Cotizacion> cargarEnPos;
    private final PermisoService permisos = PermisoService.get();

    private TableView<Cotizacion> tabla;
    private TableView<Cotizacion.Linea> tablaLineas;
    private ComboBox<String> comboEstado;
    private TextField txtBuscar;
    private DatePicker dpDesde;
    private Label lblDetalle;

    /** @param cargarEnPos abre el punto de venta con la cotización en el carrito */
    public VistaCotizaciones(CotizacionService servicio, Consumer<Cotizacion> cargarEnPos) {
        this.servicio = servicio;
        this.cargarEnPos = cargarEnPos;
    }

    static String variante(Cotizacion c) {
        if (c.isVencida()) return "neutro";
        return switch (c.getEstado()) {
            case Cotizacion.CONVERTIDA -> "ok";
            case Cotizacion.ANULADA -> "neutro";
            default -> "info";
        };
    }

    @SuppressWarnings("unchecked")
    public Node crear() {
        comboEstado = new ComboBox<>(FXCollections.observableArrayList("Todas", "Pendientes", "Vencidas", "Vendidas", "Anuladas"));
        comboEstado.getSelectionModel().select(1);
        comboEstado.setOnAction(e -> cargar());
        dpDesde = Componentes.fecha(LocalDate.now().minusMonths(3));
        dpDesde.setOnAction(e -> cargar());
        txtBuscar = new TextField();
        txtBuscar.setPromptText("🔍 Cliente, vendedor o número");
        txtBuscar.textProperty().addListener((o, a, n) -> cargar());
        HBox.setHgrow(txtBuscar, Priority.ALWAYS);

        tabla = Componentes.tabla("No hay cotizaciones con estos filtros");
        tabla.getColumns().addAll(
                Componentes.col("Número", 95, c -> Formato.numeroCotizacion(c.getId())),
                Componentes.col("Fecha", 115, c -> Componentes.fechaCorta(c.getFecha())),
                Componentes.col("Cliente", 180, Cotizacion::getClienteMostrar),
                Componentes.col("Vendedor", 130, Cotizacion::getVendedor),
                Componentes.col("Válida hasta", 95, c -> Componentes.fechaCorta(c.getFechaVencimiento())),
                Componentes.colEstado("Estado", 95, Cotizacion::getEstadoEtiqueta, VistaCotizaciones::variante),
                Componentes.colMoneda("Total", 100, Cotizacion::getTotal));
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> mostrar(n));
        tabla.setOnMouseClicked(e -> { if (e.getClickCount() == 2) cargarEnPuntoDeVenta(); });

        tablaLineas = Componentes.tabla("Selecciona una cotización");
        tablaLineas.setPrefHeight(190);
        VBox.setVgrow(tablaLineas, Priority.NEVER);
        tablaLineas.getColumns().addAll(
                Componentes.col("Código", 95, l -> Componentes.nz(l.codigo)),
                Componentes.col("Producto", 240, l -> l.descripcion),
                Componentes.colEntero("Cant.", 60, l -> l.cantidad),
                Componentes.colMoneda("P. unit.", 95, l -> l.precioUnitario),
                Componentes.col("Desc.", 65, l -> l.descuentoPct > 0 ? String.format("%.1f %%", l.descuentoPct) : ""),
                Componentes.colMoneda("Subtotal", 100, l -> l.subtotal));
        lblDetalle = Componentes.ayuda("");
        lblDetalle.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        boolean pos = permisos.tiene(Permiso.POS_USAR);
        Button btnCargar = Componentes.boton("Cobrar en punto de venta", "primary-button", Iconos.FACTURA,
                "Carga la cotización en el carrito para venderla", this::cargarEnPuntoDeVenta);
        Button btnPdf = Componentes.boton("Ver / imprimir", "secondary-button", Iconos.PORTAPAPELES, this::imprimir);
        Button btnAnular = Componentes.boton("Anular", "danger-button", null, this::anular);
        for (Button b : List.of(btnCargar, btnPdf, btnAnular)) {
            b.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        }
        btnCargar.setVisible(pos);
        btnCargar.setManaged(pos);
        btnAnular.setVisible(pos);
        btnAnular.setManaged(pos);

        VBox raiz = new VBox(12, Componentes.titulo("Cotizaciones"),
                Componentes.ayuda("Las cotizaciones se crean desde el Punto de Venta con \"Guardar como cotización\". No apartan "
                        + "inventario. Cuando el cliente acepta, usa \"Cobrar en punto de venta\": al procesar la venta queda como Vendida."),
                Componentes.fila(comboEstado, new Label("Desde"), dpDesde, txtBuscar,
                        Componentes.boton("Excel", "secondary-button", null, () -> Exportador.csv(tabla, "cotizaciones"))),
                tabla, Componentes.fila(btnCargar, btnPdf, btnAnular), Componentes.seccion("Detalle"), lblDetalle, tablaLineas);
        raiz.setPadding(new Insets(16));
        cargar();
        return raiz;
    }

    private void cargar() {
        int idx = comboEstado.getSelectionModel().getSelectedIndex();
        List<Cotizacion> lista = servicio.listar(dpDesde.getValue(), null, txtBuscar.getText());
        lista.removeIf(c -> switch (idx) {
            case 1 -> !c.isPendiente() || c.isVencida();
            case 2 -> !c.isVencida();
            case 3 -> !Cotizacion.CONVERTIDA.equals(c.getEstado());
            case 4 -> !Cotizacion.ANULADA.equals(c.getEstado());
            default -> false;
        });
        tabla.setItems(FXCollections.observableArrayList(lista));
    }

    private void mostrar(Cotizacion resumen) {
        if (resumen == null) {
            tablaLineas.getItems().clear();
            lblDetalle.setText("");
            return;
        }
        Cotizacion c = servicio.obtener(resumen.getId());
        if (c == null) return;
        tablaLineas.setItems(FXCollections.observableArrayList(c.getLineas()));
        lblDetalle.setText(Formato.numeroCotizacion(c.getId()) + " · " + c.getClienteMostrar()
                + " · subtotal " + Formato.moneda(c.getSubtotal())
                + (c.getDescuento() > 0 ? " · descuento -" + Formato.moneda(c.getDescuento()) : "")
                + " · impuesto " + Formato.moneda(c.getImpuesto()) + " · total " + Formato.moneda(c.getTotal())
                + (c.getVentaId() != null ? " · factura " + Formato.numeroFactura(c.getVentaId()) : "")
                + (c.getObservaciones() != null ? "\n" + c.getObservaciones() : ""));
    }

    private Cotizacion seleccionada() {
        Cotizacion c = tabla.getSelectionModel().getSelectedItem();
        return c == null ? null : servicio.obtener(c.getId());
    }

    private void cargarEnPuntoDeVenta() {
        Cotizacion c = seleccionada();
        if (c == null || !permisos.tiene(Permiso.POS_USAR)) return;
        if (!c.isPendiente()) {
            Alertas.advertencia("No disponible", "Esta cotización está " + c.getEstadoEtiqueta().toLowerCase() + ".");
            return;
        }
        if (c.isVencida() && !Alertas.confirmar("Cotización vencida", "La cotización venció el "
                + Componentes.fechaCorta(c.getFechaVencimiento()) + ". Se cargará con los precios actuales. ¿Continuar?")) {
            return;
        }
        cargarEnPos.accept(c);
    }

    private void imprimir() {
        Cotizacion c = seleccionada();
        if (c == null) return;
        try {
            Archivos.abrir(DocumentoPDF.cotizacion(c));
        } catch (Exception ex) {
            Alertas.error("No se pudo generar el PDF", ex.getMessage());
        }
    }

    private void anular() {
        Cotizacion c = seleccionada();
        if (c == null) return;
        String motivo = Alertas.pedirTexto("Anular cotización", "Motivo de la anulación de " + Formato.numeroCotizacion(c.getId()) + ":", "");
        if (motivo == null) return;
        try {
            servicio.anular(c, motivo);
            cargar();
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo anular", ex.getMessage());
        }
    }
}
