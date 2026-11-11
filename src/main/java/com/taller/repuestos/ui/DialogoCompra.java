package com.taller.repuestos.ui;

import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Compatibilidad;
import com.taller.repuestos.model.Compra;
import com.taller.repuestos.model.Proveedor;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.service.CompraService;
import com.taller.repuestos.service.ConfiguracionService;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Iconos;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Nueva orden de compra: proveedor, productos, cantidades, costos y descuentos.
 * Las cantidades, costos y descuentos se editan con doble clic en la tabla.
 */
public final class DialogoCompra {

    private DialogoCompra() {}

    /**
     * "Guardar y recibir todo" sirve cuando la mercadería ya llegó junto con la factura.
     * @return la compra creada o null si se canceló
     */
    @SuppressWarnings("unchecked")
    public static Compra mostrar(CompraService servicio, List<Proveedor> proveedores, List<Repuesto> productos,
                                 Map<Integer, List<Compatibilidad>> compat) {
        Dialog<Compra> d = new Dialog<>();
        d.setTitle("Nueva compra");
        d.setHeaderText("Orden de compra a proveedor");
        ButtonType guardar = new ButtonType("Guardar orden", ButtonBar.ButtonData.OK_DONE);
        ButtonType guardarRecibir = new ButtonType("Guardar y recibir todo", ButtonBar.ButtonData.OTHER);
        d.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);
        if (com.taller.repuestos.service.PermisoService.get().tiene(com.taller.repuestos.model.Permiso.COMPRAS_RECIBIR)) {
            d.getDialogPane().getButtonTypes().add(0, guardarRecibir);
        }

        ComboBox<Proveedor> comboProv = new ComboBox<>();
        for (Proveedor p : proveedores) if (p.isActivo()) comboProv.getItems().add(p);
        comboProv.setPromptText("Elige el proveedor");
        TextField txtDoc = new TextField();
        txtDoc.setPromptText("N° de factura del proveedor (opcional)");
        ComboBox<String> comboCond = new ComboBox<>(FXCollections.observableArrayList(Compra.CONDICIONES));
        comboCond.setValue("Contado");
        DatePicker dpVence = Componentes.fecha(null);
        dpVence.setDisable(true);
        comboCond.setOnAction(e -> {
            boolean credito = "Crédito".equals(comboCond.getValue());
            dpVence.setDisable(!credito);
            if (credito && dpVence.getValue() == null) dpVence.setValue(LocalDate.now().plusDays(30));
        });
        TextField txtImpuesto = new TextField(String.valueOf(ConfiguracionService.get().impuestoPorcentaje()));
        txtImpuesto.setPrefWidth(70);
        TextField txtObs = new TextField();

        Compra compra = new Compra();
        ObservableList<Compra.Linea> lineas = FXCollections.observableArrayList();
        Label lblTotales = new Label();
        lblTotales.getStyleClass().add("total-label");

        Runnable recalcular = () -> {
            compra.getLineas().clear();
            compra.getLineas().addAll(lineas);
            try {
                compra.setImpuestoPct(Formato.leerMonto(txtImpuesto.getText()));
            } catch (NumberFormatException ex) {
                compra.setImpuestoPct(0);
            }
            compra.recalcular();
            lblTotales.setText("Subtotal " + Formato.moneda(compra.getSubtotal()) + "   ·   Descuento -" + Formato.moneda(compra.getDescuento())
                    + "   ·   Impuesto " + Formato.moneda(compra.getImpuesto()) + "   ·   TOTAL " + Formato.moneda(compra.getTotal()));
        };
        txtImpuesto.textProperty().addListener((o, a, n) -> recalcular.run());

        TableView<Compra.Linea> tabla = Componentes.tabla("Busca un producto abajo y presiona Enter para agregarlo");
        tabla.setEditable(true);
        tabla.setPrefHeight(220);
        tabla.getColumns().addAll(
                Componentes.col("Código", 90, l -> l.codigo),
                Componentes.col("Producto", 220, l -> l.descripcion),
                editable("Cantidad", l -> String.valueOf(l.cantidad), (l, v) -> l.cantidad = Integer.parseInt(v.trim()), recalcular, tabla),
                editable("Costo unit.", l -> String.format("%.2f", l.costoUnitario), (l, v) -> l.costoUnitario = Formato.leerMonto(v), recalcular, tabla),
                editable("Descuento", l -> String.format("%.2f", l.descuento), (l, v) -> l.descuento = Formato.leerMonto(v), recalcular, tabla),
                Componentes.colMoneda("Subtotal", 100, Compra.Linea::getSubtotal));
        tabla.setItems(lineas);

        SelectorProducto selector = new SelectorProducto(productos, compat, r -> agregar(lineas, r, 1, tabla, recalcular));

        Button btnQuitar = Componentes.boton("Quitar línea", "danger-button", null, () -> {
            Compra.Linea l = tabla.getSelectionModel().getSelectedItem();
            if (l != null) { lineas.remove(l); recalcular.run(); }
        });
        Button btnSugerir = Componentes.boton("Sugerir stock bajo", "secondary-button", Iconos.CAJA,
                "Agrega los productos de este proveedor que están en o bajo su stock mínimo", () -> {
                    Proveedor p = comboProv.getValue();
                    int agregados = 0;
                    for (Repuesto r : productos) {
                        if (!r.isStockBajo()) continue;
                        if (p != null && r.getProveedor() != null && !r.getProveedor().isEmpty()
                                && !r.getProveedor().equalsIgnoreCase(p.getNombre())) continue;
                        if (p != null && (r.getProveedor() == null || r.getProveedor().isEmpty())) continue;
                        int objetivo = r.getStockMaximo() > 0 ? r.getStockMaximo() : r.getStockMinimo() * 2;
                        int pedir = Math.max(1, objetivo - r.getStock());
                        if (agregar(lineas, r, pedir, tabla, recalcular)) agregados++;
                    }
                    if (agregados == 0) {
                        Alertas.info("Sin sugerencias", p == null ? "No hay productos con stock bajo."
                                : "No hay productos de " + p.getNombre() + " con stock bajo.");
                    }
                });

        GridPane g = Componentes.formulario();
        int f = 0;
        f = Componentes.campo(g, f, "Proveedor:", comboProv);
        f = Componentes.campo(g, f, "Factura del proveedor:", txtDoc);
        f = Componentes.campo(g, f, "Condición de pago:", new HBox(8, comboCond, new Label("Vence:"), dpVence));
        f = Componentes.campo(g, f, "Impuesto (%):", txtImpuesto);
        Componentes.campo(g, f, "Observaciones:", txtObs);

        VBox contenido = new VBox(10, g, Componentes.seccion("Productos"), tabla,
                Componentes.fila(btnQuitar, btnSugerir, Componentes.espaciador(), lblTotales),
                Componentes.ayuda("Doble clic en Cantidad, Costo o Descuento para cambiarlos. El descuento es el monto total de la línea."),
                selector);
        contenido.setPrefWidth(880);
        d.getDialogPane().setContent(contenido);
        d.setResizable(true);
        Alertas.estilizar(d);
        recalcular.run();

        Compra[] resultado = new Compra[1];
        for (ButtonType tipo : new ButtonType[]{guardar, guardarRecibir}) {
            Button b = (Button) d.getDialogPane().lookupButton(tipo);
            if (b == null) continue;
            b.getStyleClass().add(tipo == guardar ? "primary-button" : "secondary-button");
            b.addEventFilter(ActionEvent.ACTION, ev -> {
                recalcular.run();
                Proveedor p = comboProv.getValue();
                compra.setProveedorId(p == null ? 0 : p.getId());
                compra.setDocumentoProveedor(txtDoc.getText() == null || txtDoc.getText().trim().isEmpty() ? null : txtDoc.getText().trim());
                compra.setCondicionPago(comboCond.getValue());
                compra.setFechaVencimiento("Crédito".equals(comboCond.getValue()) && dpVence.getValue() != null ? dpVence.getValue().toString() : null);
                compra.setObservaciones(txtObs.getText() == null ? null : txtObs.getText().trim());
                try {
                    Compra creada = servicio.crear(compra);
                    if (tipo == guardarRecibir) {
                        Map<Integer, Integer> todo = new java.util.HashMap<>();
                        for (Compra.Linea l : creada.getLineas()) todo.put(l.id, l.cantidad);
                        try {
                            servicio.recibir(creada, todo, com.taller.repuestos.service.PermisoService.get()
                                    .tiene(com.taller.repuestos.model.Permiso.PRECIOS_MODIFICAR));
                        } catch (ReglaNegocioException ex) {
                            Alertas.advertencia("La orden se guardó, pero no se pudo recibir", ex.getMessage());
                        }
                        creada = servicio.obtener(creada.getId());
                    }
                    resultado[0] = creada;
                } catch (ReglaNegocioException ex) {
                    Alertas.advertencia("No se pudo guardar la compra", ex.getMessage());
                    ev.consume();
                }
            });
        }
        d.setResultConverter(b -> b == guardar || b == guardarRecibir ? resultado[0] : null);
        javafx.application.Platform.runLater(selector::enfocar);
        return d.showAndWait().orElse(null);
    }

    /** Agrega el producto o, si ya está, suma la cantidad. Devuelve true si agregó una línea nueva. */
    private static boolean agregar(ObservableList<Compra.Linea> lineas, Repuesto r, int cantidad,
                                   TableView<Compra.Linea> tabla, Runnable recalcular) {
        for (Compra.Linea l : lineas) {
            if (l.repuestoId == r.getId()) {
                l.cantidad += cantidad;
                tabla.refresh();
                recalcular.run();
                return false;
            }
        }
        Compra.Linea l = new Compra.Linea();
        l.repuestoId = r.getId();
        l.codigo = r.getCodigo();
        l.descripcion = r.getDescripcion();
        l.cantidad = cantidad;
        l.costoUnitario = r.getCosto();
        lineas.add(l);
        tabla.getSelectionModel().select(l);
        recalcular.run();
        return true;
    }

    private static TableColumn<Compra.Linea, String> editable(String titulo, java.util.function.Function<Compra.Linea, String> valor,
                                                             BiConsumer<Compra.Linea, String> asignar, Runnable recalcular,
                                                             TableView<Compra.Linea> tabla) {
        TableColumn<Compra.Linea, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(x -> new SimpleStringProperty(valor.apply(x.getValue())));
        c.setCellFactory(TextFieldTableCell.forTableColumn());
        c.setPrefWidth(90);
        c.setOnEditCommit(e -> {
            try {
                asignar.accept(e.getRowValue(), e.getNewValue());
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Valor inválido", "Escribe un número válido.");
            }
            tabla.refresh();
            recalcular.run();
        });
        return c;
    }
}
