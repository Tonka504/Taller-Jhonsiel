package com.taller.repuestos.ui;

import com.taller.repuestos.dao.CompraDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Compatibilidad;
import com.taller.repuestos.model.Compra;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Proveedor;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.service.CompraService;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.service.ProveedorService;
import com.taller.repuestos.util.Archivos;
import com.taller.repuestos.util.DocumentoPDF;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Iconos;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Compras: órdenes a proveedores, recepción de mercadería, pagos, devoluciones y anulaciones. */
public class VistaCompras {

    private final CompraService servicio;
    private final ProveedorService proveedores;
    private final List<Repuesto> productos;
    private final Supplier<Map<Integer, List<Compatibilidad>>> compat;
    private final Runnable alCambiarInventario;
    private final PermisoService permisos = PermisoService.get();

    private TableView<Compra> tabla;
    private TableView<Compra.Linea> tablaLineas;
    private ComboBox<Object> comboProv;
    private ComboBox<String> comboEstado;
    private DatePicker dpDesde, dpHasta;
    private Label lblDetalle;

    public VistaCompras(CompraService servicio, ProveedorService proveedores, List<Repuesto> productos,
                        Supplier<Map<Integer, List<Compatibilidad>>> compat, Runnable alCambiarInventario) {
        this.servicio = servicio;
        this.proveedores = proveedores;
        this.productos = productos;
        this.compat = compat;
        this.alCambiarInventario = alCambiarInventario;
    }

    /** Variante de color del chip de estado. */
    static String varianteEstado(Compra c) {
        return switch (c.getEstado()) {
            case Compra.RECIBIDA -> "ok";
            case Compra.PARCIAL -> "info";
            case Compra.ANULADA -> "neutro";
            default -> "alerta";
        };
    }

    @SuppressWarnings("unchecked")
    public Node crear() {
        comboProv = new ComboBox<>();
        comboProv.getItems().add("Todos los proveedores");
        comboProv.getItems().addAll(proveedores.listar());
        comboProv.getSelectionModel().selectFirst();
        comboEstado = new ComboBox<>(FXCollections.observableArrayList("Todos los estados", "Pendiente", "Recibida parcial", "Recibida", "Anulada"));
        comboEstado.getSelectionModel().selectFirst();
        dpDesde = Componentes.fecha(LocalDate.now().minusMonths(3));
        dpHasta = Componentes.fecha(null);
        dpHasta.setPromptText("Hasta");
        for (javafx.scene.control.Control c : new javafx.scene.control.Control[]{comboProv, comboEstado, dpDesde, dpHasta}) {
            if (c instanceof ComboBox<?> cb) cb.setOnAction(e -> cargar());
            if (c instanceof DatePicker dp) dp.setOnAction(e -> cargar());
        }

        Button btnNueva = Componentes.boton("Nueva compra", "primary-button", Iconos.CARRITO, this::nueva);
        btnNueva.setVisible(permisos.tiene(Permiso.COMPRAS_CREAR));
        btnNueva.setManaged(btnNueva.isVisible());

        tabla = Componentes.tabla("No hay compras con estos filtros");
        tabla.getColumns().addAll(
                Componentes.col("Número", 90, c -> Formato.numeroCompra(c.getId())),
                Componentes.col("Fecha", 120, c -> Componentes.fechaCorta(c.getFecha())),
                Componentes.col("Proveedor", 170, Compra::getProveedorNombre),
                Componentes.col("Factura prov.", 100, c -> Componentes.nz(c.getDocumentoProveedor())),
                Componentes.col("Condición", 80, Compra::getCondicionPago),
                Componentes.colEstado("Estado", 120, Compra::getEstadoEtiqueta, VistaCompras::varianteEstado),
                Componentes.colMoneda("Total", 100, Compra::getTotal),
                Componentes.colMoneda("Pagado", 100, Compra::getPagado),
                Componentes.colMoneda("Saldo", 100, Compra::getSaldo));
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> mostrarDetalle(n));

        tablaLineas = Componentes.tabla("Selecciona una compra");
        tablaLineas.setPrefHeight(180);
        VBox.setVgrow(tablaLineas, Priority.NEVER);
        tablaLineas.getColumns().addAll(
                Componentes.col("Código", 90, l -> l.codigo),
                Componentes.col("Producto", 220, l -> l.descripcion),
                Componentes.colEntero("Pedido", 70, l -> l.cantidad),
                Componentes.colEntero("Recibido", 75, l -> l.cantidadRecibida),
                Componentes.colEntero("Devuelto", 75, l -> l.cantidadDevuelta),
                Componentes.colMoneda("Costo unit.", 95, l -> l.costoUnitario),
                Componentes.colMoneda("Descuento", 90, l -> l.descuento),
                Componentes.colMoneda("Subtotal", 100, Compra.Linea::getSubtotal));
        lblDetalle = new Label();
        lblDetalle.getStyleClass().add("hint-label");

        Button btnRecibir = accion("Recibir mercadería", "primary-button", Iconos.CAJA, Permiso.COMPRAS_RECIBIR, this::recibir);
        Button btnPagar = accion("Registrar pago", "secondary-button", Iconos.BILLETERA, Permiso.COMPRAS_PAGAR, this::pagar);
        Button btnDevolver = accion("Devolver a proveedor", "secondary-button", null, Permiso.COMPRAS_ANULAR, this::devolver);
        Button btnAnular = accion("Anular", "danger-button", null, Permiso.COMPRAS_ANULAR, this::anular);
        Button btnPdf = Componentes.boton("Ver / imprimir", "secondary-button", Iconos.FACTURA, this::imprimir);
        btnPdf.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());

        VBox raiz = new VBox(12,
                Componentes.fila(Componentes.titulo("Compras"), Componentes.espaciador(), btnNueva),
                Componentes.ayuda("Flujo: crea la orden → cuando llegue la mercadería, regístrala con \"Recibir\" (aumenta el inventario "
                        + "y actualiza costos) → registra los pagos. Si algo viene dañado, usa \"Devolver a proveedor\"."),
                Componentes.fila(comboProv, comboEstado, new Label("Desde"), dpDesde, new Label("Hasta"), dpHasta,
                        Componentes.boton("Excel", "secondary-button", null, () -> Exportador.csv(tabla, "compras"))),
                tabla,
                Componentes.fila(btnRecibir, btnPagar, btnDevolver, btnAnular, btnPdf),
                Componentes.seccion("Detalle"), lblDetalle, tablaLineas);
        raiz.setPadding(new javafx.geometry.Insets(16));
        cargar();
        return raiz;
    }

    private Button accion(String texto, String estilo, String icono, Permiso permiso, Runnable r) {
        Button b = Componentes.boton(texto, estilo, icono, r);
        b.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        b.setVisible(permisos.tiene(permiso));
        b.setManaged(b.isVisible());
        return b;
    }

    private void cargar() {
        Integer provId = comboProv.getValue() instanceof Proveedor p ? p.getId() : null;
        String estado = switch (comboEstado.getSelectionModel().getSelectedIndex()) {
            case 1 -> Compra.PENDIENTE;
            case 2 -> Compra.PARCIAL;
            case 3 -> Compra.RECIBIDA;
            case 4 -> Compra.ANULADA;
            default -> null;
        };
        int seleccion = tabla.getSelectionModel().getSelectedItem() == null ? 0 : tabla.getSelectionModel().getSelectedItem().getId();
        tabla.setItems(FXCollections.observableArrayList(servicio.listar(provId, estado, dpDesde.getValue(), dpHasta.getValue())));
        for (Compra c : tabla.getItems()) {
            if (c.getId() == seleccion) { tabla.getSelectionModel().select(c); break; }
        }
    }

    private void mostrarDetalle(Compra resumen) {
        if (resumen == null) {
            tablaLineas.getItems().clear();
            lblDetalle.setText("");
            return;
        }
        Compra c = servicio.obtener(resumen.getId());
        if (c == null) return;
        tablaLineas.setItems(FXCollections.observableArrayList(c.getLineas()));
        StringBuilder sb = new StringBuilder(Formato.numeroCompra(c.getId()) + " · " + c.getProveedorNombre()
                + " · creada por " + c.getUsuario()
                + (c.getFechaRecepcion() != null ? " · última recepción " + Componentes.fechaCorta(c.getFechaRecepcion()) : "")
                + (c.getFechaVencimiento() != null ? " · vence " + c.getFechaVencimiento() : ""));
        if (c.getDevuelto() > 0) sb.append(" · devuelto al proveedor: ").append(Formato.moneda(c.getDevuelto()));
        if (c.isAnulada()) sb.append(" · ANULADA por ").append(c.getAnuladaPor()).append(": ").append(Componentes.nz(c.getMotivoAnulacion()));
        List<CompraDAO.Pago> pagos = servicio.pagos(c.getId());
        for (CompraDAO.Pago p : pagos) {
            sb.append("\nPago ").append(Componentes.fechaCorta(p.fecha)).append(": ").append(Formato.moneda(p.monto))
              .append(" (").append(p.metodo).append(p.referencia == null || p.referencia.isEmpty() ? "" : " · ref. " + p.referencia)
              .append(") por ").append(p.usuario);
        }
        lblDetalle.setText(sb.toString());
    }

    private Compra seleccionada() {
        Compra c = tabla.getSelectionModel().getSelectedItem();
        return c == null ? null : servicio.obtener(c.getId());
    }

    // ------------------------------------------------------------------
    // ACCIONES
    // ------------------------------------------------------------------

    private void nueva() {
        Compra c = DialogoCompra.mostrar(servicio, proveedores.listar(), productos, compat.get());
        if (c != null) {
            cargar();
            alCambiarInventario.run();
            Alertas.info("Compra registrada", "Se creó la orden " + Formato.numeroCompra(c.getId()) + " (" + c.getEstadoEtiqueta() + ").");
        }
    }

    private void recibir() {
        Compra c = seleccionada();
        if (c == null) return;
        if (c.isAnulada() || Compra.RECIBIDA.equals(c.getEstado())) {
            Alertas.advertencia("Nada por recibir", "Esta compra está " + c.getEstadoEtiqueta().toLowerCase() + ".");
            return;
        }
        CheckBox chkCosto = new CheckBox("Actualizar el costo de los productos con el de esta compra (queda historial de costos)");
        chkCosto.setSelected(permisos.tiene(Permiso.PRECIOS_MODIFICAR));
        chkCosto.setDisable(!permisos.tiene(Permiso.PRECIOS_MODIFICAR));
        Map<Integer, Integer> cant = pedirCantidades("Recibir mercadería", "¿Cuántas unidades llegaron de cada producto?",
                c, true, chkCosto);
        if (cant == null) return;
        try {
            servicio.recibir(c, cant, chkCosto.isSelected());
            alCambiarInventario.run();
            cargar();
            Alertas.info("Mercadería recibida", "El inventario se actualizó.");
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo recibir", ex.getMessage());
        }
    }

    private void devolver() {
        Compra c = seleccionada();
        if (c == null) return;
        boolean hay = c.getLineas().stream().anyMatch(l -> l.getDevolvible() > 0);
        if (!hay) {
            Alertas.advertencia("Nada que devolver", "Esta compra no tiene mercadería recibida pendiente de devolver.");
            return;
        }
        TextField txtMotivo = new TextField();
        txtMotivo.setPromptText("Motivo: pieza defectuosa, error en el pedido...");
        Map<Integer, Integer> cant = pedirCantidades("Devolver a proveedor", "¿Cuántas unidades devuelves? (salen del inventario)",
                c, false, txtMotivo);
        if (cant == null) return;
        try {
            servicio.devolver(c, cant, txtMotivo.getText());
            alCambiarInventario.run();
            cargar();
            Alertas.info("Devolución registrada", "Las unidades salieron del inventario y se descontaron de lo que se le debe al proveedor.");
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo devolver", ex.getMessage());
        }
    }

    private void anular() {
        Compra c = seleccionada();
        if (c == null) return;
        String motivo = Alertas.pedirTexto("Anular compra", "Motivo de la anulación de " + Formato.numeroCompra(c.getId()) + ":", "");
        if (motivo == null) return;
        try {
            servicio.anular(c, motivo);
            cargar();
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo anular", ex.getMessage());
        }
    }

    private void pagar() {
        Compra c = seleccionada();
        if (c == null) return;
        if (c.getSaldo() <= 0) {
            Alertas.info("Sin saldo", Compra.PENDIENTE.equals(c.getEstado())
                    ? "Primero registra la recepción de la mercadería." : "Esta compra no tiene saldo pendiente.");
            return;
        }
        Dialog<Boolean> d = new Dialog<>();
        d.setTitle("Pago a proveedor");
        d.setHeaderText(Formato.numeroCompra(c.getId()) + " · " + c.getProveedorNombre() + "\nSaldo pendiente: " + Formato.moneda(c.getSaldo()));
        ButtonType ok = new ButtonType("Registrar pago", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        TextField txtMonto = new TextField(String.format("%.2f", c.getSaldo()));
        ComboBox<String> comboMetodo = new ComboBox<>(FXCollections.observableArrayList(CompraService.METODOS_PAGO));
        comboMetodo.setValue("Transferencia");
        TextField txtRef = new TextField();
        txtRef.setPromptText("N° de transferencia, cheque... (opcional)");
        GridPane g = Componentes.formulario();
        int f = Componentes.campo(g, 0, "Monto:", txtMonto);
        f = Componentes.campo(g, f, "Forma de pago:", comboMetodo);
        Componentes.campo(g, f, "Referencia:", txtRef);
        VBox v = new VBox(10, g, Componentes.ayuda("\"Efectivo (desde caja)\" registra la salida de dinero en la caja abierta."));
        v.setPrefWidth(420);
        d.getDialogPane().setContent(v);
        Alertas.estilizar(d);
        Button b = (Button) d.getDialogPane().lookupButton(ok);
        b.getStyleClass().add("primary-button");
        b.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                servicio.registrarPago(c, Formato.leerMonto(txtMonto.getText()), comboMetodo.getValue(), txtRef.getText());
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Monto inválido", "Escribe el monto como número.");
                ev.consume();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo registrar el pago", ex.getMessage());
                ev.consume();
            }
        });
        d.setResultConverter(x -> x == ok);
        if (d.showAndWait().orElse(false)) {
            cargar();
            Alertas.info("Pago registrado", "El pago al proveedor quedó registrado.");
        }
    }

    private void imprimir() {
        Compra c = seleccionada();
        if (c == null) return;
        try {
            Archivos.abrir(DocumentoPDF.compra(c));
        } catch (Exception ex) {
            Alertas.error("No se pudo generar el PDF", ex.getMessage());
        }
    }

    /**
     * Pide una cantidad por línea (recepción o devolución).
     * @param recepcion true: propone lo pendiente por recibir; false: propone 0 y limita a lo devolvible
     * @param extra     control adicional bajo la tabla (casilla de costo o campo de motivo)
     */
    @SuppressWarnings("unchecked")
    private Map<Integer, Integer> pedirCantidades(String titulo, String encabezado, Compra c, boolean recepcion, Node extra) {
        Map<Integer, Integer> cantidades = new HashMap<>();
        for (Compra.Linea l : c.getLineas()) cantidades.put(l.id, recepcion ? l.getPendiente() : 0);

        TableView<Compra.Linea> t = Componentes.tabla("");
        t.setEditable(true);
        t.setPrefHeight(260);
        TableColumn<Compra.Linea, String> colCant = new TableColumn<>(recepcion ? "Recibir ahora" : "Devolver");
        colCant.setCellValueFactory(x -> new SimpleStringProperty(String.valueOf(cantidades.get(x.getValue().id))));
        colCant.setCellFactory(TextFieldTableCell.forTableColumn());
        colCant.setOnEditCommit(e -> {
            try {
                cantidades.put(e.getRowValue().id, Integer.parseInt(e.getNewValue().trim()));
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Cantidad inválida", "Escribe un número entero.");
            }
            t.refresh();
        });
        colCant.setPrefWidth(110);
        t.getColumns().addAll(
                Componentes.col("Código", 90, l -> l.codigo),
                Componentes.col("Producto", 220, l -> l.descripcion),
                Componentes.colEntero("Pedido", 70, l -> l.cantidad),
                Componentes.colEntero(recepcion ? "Pendiente" : "Devolvible", 85, l -> recepcion ? l.getPendiente() : l.getDevolvible()),
                colCant);
        t.setItems(FXCollections.observableArrayList(c.getLineas()));

        Dialog<Boolean> d = new Dialog<>();
        d.setTitle(titulo);
        d.setHeaderText(Formato.numeroCompra(c.getId()) + " · " + c.getProveedorNombre() + "\n" + encabezado);
        ButtonType ok = new ButtonType("Confirmar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        VBox v = new VBox(10, t, Componentes.ayuda("Doble clic en la columna de cantidad para cambiarla."), extra);
        v.setPrefWidth(700);
        d.getDialogPane().setContent(v);
        Alertas.estilizar(d);
        ((Button) d.getDialogPane().lookupButton(ok)).getStyleClass().add("primary-button");
        d.setResultConverter(x -> x == ok);
        return d.showAndWait().orElse(false) ? cantidades : null;
    }
}
