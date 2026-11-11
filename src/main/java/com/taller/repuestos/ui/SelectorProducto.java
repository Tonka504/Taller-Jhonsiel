package com.taller.repuestos.ui;

import com.taller.repuestos.model.Compatibilidad;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.service.BuscadorRepuestos;
import com.taller.repuestos.util.Formato;

import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Buscador de productos para formularios (compras, órdenes de trabajo): escribe código, nombre, marca o
 * vehículo y elige con doble clic o Enter. Acepta el código de barras del lector.
 */
public class SelectorProducto extends VBox {

    private final TextField txt = new TextField();
    private final ListView<Repuesto> lista = new ListView<>();

    public SelectorProducto(List<Repuesto> productos, Map<Integer, List<Compatibilidad>> compat, Consumer<Repuesto> alElegir) {
        super(6);
        txt.setPromptText("🔍 Código, código de barras, nombre, marca o vehículo (ej: Hilux 2007 filtro)");
        FilteredList<Repuesto> filtrados = new FilteredList<>(FXCollections.observableArrayList(productos), r -> true);
        txt.textProperty().addListener((o, a, n) -> {
            BuscadorRepuestos.Consulta c = BuscadorRepuestos.Consulta.de(n);
            filtrados.setPredicate(r -> c.coincide(r, compat == null ? null : compat.get(r.getId())));
            if (!filtrados.isEmpty()) lista.getSelectionModel().selectFirst();
        });
        lista.setItems(filtrados);
        lista.setPrefHeight(150);
        lista.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Repuesto r, boolean vacio) {
                super.updateItem(r, vacio);
                setText(vacio || r == null ? null : r.getCodigo() + "  ·  " + r.getDescripcion()
                        + (r.getMarca() == null || r.getMarca().isEmpty() ? "" : "  (" + r.getMarca() + ")")
                        + "   —   stock " + r.getStock() + "   ·   costo " + Formato.moneda(r.getCosto())
                        + "   ·   precio " + Formato.moneda(r.getPrecio()));
            }
        });
        Runnable elegir = () -> {
            Repuesto r = lista.getSelectionModel().getSelectedItem();
            if (r == null) {
                // Lector de códigos: coincidencia exacta con el código o el código de barras
                String q = txt.getText() == null ? "" : txt.getText().trim();
                for (Repuesto p : productos) {
                    if (q.equalsIgnoreCase(p.getCodigo()) || q.equals(p.getCodigoParaLector())) { r = p; break; }
                }
            }
            if (r != null) {
                alElegir.accept(r);
                txt.clear();
                txt.requestFocus();
            }
        };
        txt.setOnAction(e -> elegir.run());
        txt.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) { lista.requestFocus(); lista.getSelectionModel().selectFirst(); }
        });
        lista.setOnMouseClicked(e -> { if (e.getClickCount() == 2) elegir.run(); });
        lista.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) elegir.run(); });
        getChildren().addAll(txt, lista);
    }

    public void enfocar() { txt.requestFocus(); }
}
