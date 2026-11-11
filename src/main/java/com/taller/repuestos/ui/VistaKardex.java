package com.taller.repuestos.ui;

import com.taller.repuestos.dao.MovimientoInventarioDAO;
import com.taller.repuestos.model.MovimientoInventario;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.service.InventarioService;
import com.taller.repuestos.util.Iconos;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/**
 * Kardex: historial de movimientos de inventario (entradas, salidas, ajustes, ventas, compras...).
 * Se usa como pantalla completa o como diálogo con el historial de un solo producto.
 */
public class VistaKardex {

    private final InventarioService inventarioService;
    private final Repuesto producto; // null = todos los productos

    private TableView<MovimientoInventario> tabla;
    private TextField txtTexto;
    private ComboBox<Object> comboTipo;
    private DatePicker dpDesde;
    private DatePicker dpHasta;
    private Label lblResumen;

    public VistaKardex(InventarioService inventarioService, Repuesto producto) {
        this.inventarioService = inventarioService;
        this.producto = producto;
    }

    /** Abre el kardex de un producto en una ventana aparte. */
    public static void mostrar(InventarioService servicio, Repuesto r) {
        Dialog<Void> d = new Dialog<>();
        d.setTitle("Kardex - " + r.getCodigo());
        d.setHeaderText(r.getCodigo() + " · " + r.getDescripcion() + "   (existencia actual: " + r.getStock() + ")");
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Node contenido = new VistaKardex(servicio, r).crearContenido();
        d.getDialogPane().setContent(contenido);
        d.getDialogPane().setPrefSize(980, 600);
        d.setResizable(true);
        Alertas.estilizar(d);
        d.showAndWait();
    }

    public Node crear() {
        VBox v = new VBox(12, Componentes.titulo("Movimientos de inventario"),
                Componentes.ayuda("Kardex de existencias: cada venta, compra, devolución, ajuste o consumo en el taller queda registrado "
                        + "con su fecha, usuario, documento y la existencia que quedó."),
                crearContenido());
        v.setPadding(new javafx.geometry.Insets(16));
        return v;
    }

    @SuppressWarnings("unchecked")
    private VBox crearContenido() {
        txtTexto = new TextField();
        txtTexto.setPromptText("Código, producto, documento, motivo o usuario");
        HBox.setHgrow(txtTexto, Priority.ALWAYS);
        txtTexto.setOnAction(e -> cargar());
        comboTipo = new ComboBox<>();
        comboTipo.getItems().add("Todos los tipos");
        comboTipo.getItems().addAll((Object[]) MovimientoInventario.Tipo.values());
        comboTipo.getSelectionModel().selectFirst();
        comboTipo.setOnAction(e -> cargar());
        dpDesde = Componentes.fecha(producto == null ? LocalDate.now().minusDays(30) : null);
        dpHasta = Componentes.fecha(null);
        dpDesde.setPromptText("Desde");
        dpHasta.setPromptText("Hasta");
        dpDesde.setOnAction(e -> cargar());
        dpHasta.setOnAction(e -> cargar());

        HBox filtros = Componentes.fila(dpDesde, dpHasta, comboTipo);
        if (producto == null) filtros.getChildren().add(txtTexto);
        filtros.getChildren().addAll(
                Componentes.boton("Buscar", "secondary-button", Iconos.BUSCAR, this::cargar),
                Componentes.boton("Excel", "secondary-button", null, "Exportar a CSV (se abre con Excel)",
                        () -> Exportador.csv(tabla, "kardex")),
                Componentes.boton("PDF", "secondary-button", null, "Exportar a PDF",
                        () -> Exportador.pdf(tabla, "Movimientos de inventario",
                                producto == null ? null : producto.getCodigo() + " - " + producto.getDescripcion())));

        tabla = Componentes.tabla("No hay movimientos con estos filtros");
        tabla.getColumns().add(Componentes.col("Fecha", 120, m -> Componentes.fechaCorta(m.getFecha())));
        if (producto == null) {
            tabla.getColumns().add(Componentes.col("Código", 90, MovimientoInventario::getCodigo));
            tabla.getColumns().add(Componentes.col("Producto", 190, MovimientoInventario::getDescripcion));
        }
        tabla.getColumns().addAll(
                Componentes.colEstado("Tipo", 180, MovimientoInventario::getTipoEtiqueta,
                        m -> m.isEntrada() ? "ok" : "alerta"),
                Componentes.colObjeto("Cantidad", 75, m -> (m.getCantidad() > 0 ? "+" : "") + m.getCantidad()),
                Componentes.colObjeto("Existencia", 80, m -> m.getStockResultante() == null ? "—" : String.valueOf(m.getStockResultante())),
                Componentes.colMoneda("Costo unit.", 90, m -> m.getCostoUnitario() == null ? 0 : m.getCostoUnitario()),
                Componentes.col("Documento", 100, m -> Componentes.nz(m.getDocumento())),
                Componentes.col("Motivo", 170, m -> Componentes.nz(m.getMotivo())),
                Componentes.col("Usuario", 110, MovimientoInventario::getUsuario));

        lblResumen = new Label();
        lblResumen.getStyleClass().add("metric-value");

        cargar();
        VBox v = new VBox(10, filtros, tabla, lblResumen);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        return v;
    }

    private void cargar() {
        MovimientoInventarioDAO.Filtro f = new MovimientoInventarioDAO.Filtro();
        if (producto != null) f.repuestoId = producto.getId();
        f.desde = dpDesde.getValue() == null ? null : dpDesde.getValue().toString();
        f.hasta = dpHasta.getValue() == null ? null : dpHasta.getValue().toString();
        Object tipo = comboTipo.getValue();
        f.tipo = tipo instanceof MovimientoInventario.Tipo t ? t.name() : null;
        f.texto = producto == null ? txtTexto.getText() : null;
        f.limite = 3000;
        List<MovimientoInventario> lista = inventarioService.movimientos(f);
        tabla.setItems(FXCollections.observableArrayList(lista));
        int entradas = 0, salidas = 0;
        for (MovimientoInventario m : lista) {
            if (m.getCantidad() > 0) entradas += m.getCantidad(); else salidas -= m.getCantidad();
        }
        lblResumen.setText(lista.size() + " movimientos   ·   Entradas: +" + entradas + "   ·   Salidas: -" + salidas
                + (lista.size() >= f.limite ? "   (se muestran los " + f.limite + " más recientes)" : ""));
    }
}
