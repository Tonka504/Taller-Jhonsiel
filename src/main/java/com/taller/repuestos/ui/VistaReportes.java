package com.taller.repuestos.ui;

import com.taller.repuestos.dao.CatalogoDAO;
import com.taller.repuestos.model.Cliente;
import com.taller.repuestos.model.Proveedor;
import com.taller.repuestos.service.CatalogoService;
import com.taller.repuestos.service.ClienteService;
import com.taller.repuestos.service.ProveedorService;
import com.taller.repuestos.service.ReporteService;
import com.taller.repuestos.service.ReporteService.Definicion;
import com.taller.repuestos.service.ReporteService.Filtro;
import com.taller.repuestos.service.ReporteService.TipoColumna;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Iconos;

import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Reportes de ventas, inventario, finanzas, compras y taller, con filtros, gráfico y exportación. */
public class VistaReportes {

    private final ReporteService servicio;
    private final CatalogoService catalogo;
    private final ClienteService clientes;
    private final ProveedorService proveedores;

    private Definicion actual;
    private ReporteService.Resultado ultimo;

    private Label lblTitulo, lblDescripcion, lblResumen;
    private FlowPane barraFiltros;
    private DatePicker dpDesde, dpHasta;
    private ComboBox<String> comboUsuario, comboCategoria, comboMarca;
    private ComboBox<Object> comboCliente, comboProveedor;
    private TextField txtProducto;
    private final Map<Filtro, Node> nodosFiltro = new LinkedHashMap<>();
    private TableView<Object[]> tabla;
    private StackPane contenedorGrafico;

    public VistaReportes(ReporteService servicio, CatalogoService catalogo, ClienteService clientes, ProveedorService proveedores) {
        this.servicio = servicio;
        this.catalogo = catalogo;
        this.clientes = clientes;
        this.proveedores = proveedores;
    }

    public Node crear() {
        // ---- Lista de reportes por grupo ----
        Map<String, List<Definicion>> grupos = new LinkedHashMap<>();
        for (Definicion d : servicio.disponibles()) grupos.computeIfAbsent(d.grupo, k -> new ArrayList<>()).add(d);
        Accordion acordeon = new Accordion();
        List<ListView<Definicion>> listas = new ArrayList<>();
        for (Map.Entry<String, List<Definicion>> g : grupos.entrySet()) {
            ListView<Definicion> lv = new ListView<>(FXCollections.observableArrayList(g.getValue()));
            lv.setPrefHeight(Math.min(10, g.getValue().size()) * 30 + 6);
            lv.getStyleClass().add("lista-reportes");
            listas.add(lv);
            lv.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> {
                if (n == null) return;
                for (ListView<Definicion> otra : listas) if (otra != lv) otra.getSelectionModel().clearSelection();
                seleccionar(n);
            });
            acordeon.getPanes().add(new TitledPane(g.getKey(), lv));
        }
        if (!acordeon.getPanes().isEmpty()) acordeon.setExpandedPane(acordeon.getPanes().get(0));
        VBox izquierda = new VBox(10, Componentes.titulo("Reportes"), acordeon);
        izquierda.setPadding(new Insets(16));
        izquierda.setMinWidth(230);

        // ---- Filtros ----
        dpDesde = Componentes.fecha(LocalDate.now().withDayOfMonth(1));
        dpHasta = Componentes.fecha(LocalDate.now());
        comboUsuario = combo("Todos los usuarios", servicio.usuariosConVentas());
        comboCategoria = combo("Todas las categorías", catalogo.nombres(CatalogoDAO.Tipo.CATEGORIA));
        comboMarca = combo("Todas las marcas", catalogo.nombres(CatalogoDAO.Tipo.MARCA));
        comboCliente = new ComboBox<>();
        comboCliente.getItems().add("Todos los clientes");
        comboCliente.getItems().addAll(clientes.listar());
        comboCliente.getSelectionModel().selectFirst();
        comboProveedor = new ComboBox<>();
        comboProveedor.getItems().add("Todos los proveedores");
        comboProveedor.getItems().addAll(proveedores.listar());
        comboProveedor.getSelectionModel().selectFirst();
        txtProducto = new TextField();
        txtProducto.setPromptText("Código o nombre de producto");
        txtProducto.setOnAction(e -> generar());

        ComboBox<String> rangos = new ComboBox<>(FXCollections.observableArrayList(
                "Hoy", "Esta semana", "Este mes", "Mes anterior", "Últimos 30 días", "Este año", "Todo"));
        rangos.setPromptText("Período rápido");
        rangos.setOnAction(e -> {
            LocalDate hoy = LocalDate.now();
            switch (rangos.getValue() == null ? "" : rangos.getValue()) {
                case "Hoy" -> { dpDesde.setValue(hoy); dpHasta.setValue(hoy); }
                case "Esta semana" -> { dpDesde.setValue(hoy.with(DayOfWeek.MONDAY)); dpHasta.setValue(hoy); }
                case "Este mes" -> { dpDesde.setValue(hoy.withDayOfMonth(1)); dpHasta.setValue(hoy); }
                case "Mes anterior" -> {
                    LocalDate ini = hoy.minusMonths(1).withDayOfMonth(1);
                    dpDesde.setValue(ini);
                    dpHasta.setValue(ini.withDayOfMonth(ini.lengthOfMonth()));
                }
                case "Últimos 30 días" -> { dpDesde.setValue(hoy.minusDays(29)); dpHasta.setValue(hoy); }
                case "Este año" -> { dpDesde.setValue(hoy.withDayOfYear(1)); dpHasta.setValue(hoy); }
                case "Todo" -> { dpDesde.setValue(null); dpHasta.setValue(null); }
                default -> { }
            }
            generar();
        });

        nodosFiltro.put(Filtro.FECHAS, Componentes.fila(rangos, new Label("Desde"), dpDesde, new Label("Hasta"), dpHasta));
        nodosFiltro.put(Filtro.USUARIO, comboUsuario);
        nodosFiltro.put(Filtro.CLIENTE, comboCliente);
        nodosFiltro.put(Filtro.PROVEEDOR, comboProveedor);
        nodosFiltro.put(Filtro.CATEGORIA, comboCategoria);
        nodosFiltro.put(Filtro.MARCA, comboMarca);
        nodosFiltro.put(Filtro.PRODUCTO, txtProducto);
        barraFiltros = new FlowPane(8, 8);

        Button btnGenerar = Componentes.boton("Generar", "primary-button", Iconos.GRAFICA, null);
        btnGenerar.setOnAction(e -> Componentes.conCarga(btnGenerar, this::generar));
        Button btnExcel = Componentes.boton("Excel", "secondary-button", null, "Exportar a CSV (se abre con Excel)", () -> {
            if (actual != null) Exportador.csv(tabla, actual.nombre);
        });
        Button btnPdf = Componentes.boton("PDF", "secondary-button", null, "Exportar a PDF", () -> {
            if (actual != null) Exportador.pdf(tabla, actual.nombre, descripcionFiltros());
        });

        lblTitulo = Componentes.seccion("Elige un reporte");
        lblDescripcion = Componentes.ayuda("Selecciona un reporte de la lista de la izquierda.");
        lblResumen = new Label();
        lblResumen.getStyleClass().add("metric-value");
        lblResumen.setWrapText(true);

        tabla = Componentes.tabla("Sin datos para estos filtros");
        tabla.setMinHeight(220);
        contenedorGrafico = new StackPane();
        contenedorGrafico.setMinHeight(0);

        VBox derecha = new VBox(12, lblTitulo, lblDescripcion,
                Componentes.panel(barraFiltros, Componentes.fila(btnGenerar, btnExcel, btnPdf)),
                contenedorGrafico, lblResumen, tabla);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        derecha.setPadding(new Insets(16));

        SplitPane split = new SplitPane(izquierda, derecha);
        split.setDividerPositions(0.22);
        // Primer reporte seleccionado de entrada
        if (!listas.isEmpty() && !listas.get(0).getItems().isEmpty()) listas.get(0).getSelectionModel().selectFirst();
        return split;
    }

    private void seleccionar(Definicion d) {
        actual = d;
        lblTitulo.setText(d.nombre);
        lblDescripcion.setText(d.descripcion);
        barraFiltros.getChildren().clear();
        for (Map.Entry<Filtro, Node> e : nodosFiltro.entrySet()) {
            if (d.filtros.contains(e.getKey())) barraFiltros.getChildren().add(e.getValue());
        }
        if (barraFiltros.getChildren().isEmpty()) barraFiltros.getChildren().add(Componentes.ayuda("Este reporte no necesita filtros."));
        generar();
    }

    private void generar() {
        if (actual == null) return;
        ReporteService.Filtros f = new ReporteService.Filtros();
        if (actual.filtros.contains(Filtro.FECHAS)) {
            f.desde = dpDesde.getValue();
            f.hasta = dpHasta.getValue();
            if (f.desde != null && f.hasta != null && f.desde.isAfter(f.hasta)) {
                Alertas.advertencia("Fechas inválidas", "La fecha inicial no puede ser posterior a la final.");
                return;
            }
        }
        if (actual.filtros.contains(Filtro.USUARIO)) f.usuario = valor(comboUsuario);
        if (actual.filtros.contains(Filtro.CATEGORIA)) f.categoria = valor(comboCategoria);
        if (actual.filtros.contains(Filtro.MARCA)) f.marca = valor(comboMarca);
        if (actual.filtros.contains(Filtro.PRODUCTO)) f.producto = txtProducto.getText();
        if (actual.filtros.contains(Filtro.CLIENTE) && comboCliente.getValue() instanceof Cliente c) f.clienteId = c.getId();
        if (actual.filtros.contains(Filtro.PROVEEDOR) && comboProveedor.getValue() instanceof Proveedor p) f.proveedorId = p.getId();

        ultimo = servicio.ejecutar(actual, f);
        construirTabla(ultimo);
        construirGrafico(ultimo);

        StringBuilder sb = new StringBuilder(ultimo.filas.size() + " filas");
        for (int i = 0; i < actual.columnas.size(); i++) {
            Double t = ultimo.totales[i];
            if (t == null) continue;
            ReporteService.Columna c = actual.columnas.get(i);
            sb.append("   ·   ").append(c.titulo()).append(": ")
              .append(c.tipo() == TipoColumna.MONEDA ? Formato.moneda(t) : String.format("%,.0f", t));
        }
        lblResumen.setText(sb.toString());
    }

    @SuppressWarnings("unchecked")
    private void construirTabla(ReporteService.Resultado r) {
        tabla.getColumns().clear();
        List<ReporteService.Columna> cols = r.definicion.columnas;
        for (int i = 0; i < cols.size(); i++) {
            final int idx = i;
            ReporteService.Columna c = cols.get(i);
            TableColumn<Object[], Object> col = new TableColumn<>(c.titulo());
            col.setCellValueFactory(x -> new SimpleObjectProperty<>(idx < x.getValue().length ? x.getValue()[idx] : null));
            Function<Object, String> fmt = formateador(c.tipo());
            col.setUserData(fmt);
            col.setCellFactory(x -> new TableCell<>() {
                @Override
                protected void updateItem(Object v, boolean vacio) {
                    super.updateItem(v, vacio);
                    if (vacio || v == null) {
                        setText(null);
                        return;
                    }
                    setText(fmt.apply(v));
                    setStyle(c.tipo() == TipoColumna.TEXTO ? "" : "-fx-alignment: CENTER-RIGHT;");
                }
            });
            col.setPrefWidth(c.tipo() == TipoColumna.TEXTO ? 150 : 110);
            // Ordenar números como números aunque haya vacíos
            col.setComparator((a, b) -> {
                if (a == null) return b == null ? 0 : -1;
                if (b == null) return 1;
                if (a instanceof Number na && b instanceof Number nb) return Double.compare(na.doubleValue(), nb.doubleValue());
                return a.toString().compareToIgnoreCase(b.toString());
            });
            tabla.getColumns().add(col);
        }
        tabla.setItems(FXCollections.observableArrayList(r.filas));
    }

    private void construirGrafico(ReporteService.Resultado r) {
        contenedorGrafico.getChildren().clear();
        Definicion d = r.definicion;
        if (d.grafico == ReporteService.TipoGrafico.NINGUNO || r.filas.isEmpty()) {
            contenedorGrafico.setPrefHeight(0);
            return;
        }
        String serie = d.columnas.get(d.colValor).titulo();
        Node grafico;
        switch (d.grafico) {
            case PASTEL -> {
                PieChart pie = new PieChart();
                int otros = 0;
                double resto = 0;
                for (Object[] f : r.filas) {
                    double v = numero(f[d.colValor]);
                    if (v <= 0) continue;
                    if (pie.getData().size() < 8) pie.getData().add(new PieChart.Data(String.valueOf(f[d.colEtiqueta]), v));
                    else { otros++; resto += v; }
                }
                if (otros > 0) pie.getData().add(new PieChart.Data("Otros (" + otros + ")", resto));
                pie.setLabelsVisible(true);
                pie.setLegendVisible(false);
                grafico = pie;
            }
            case LINEA -> {
                LineChart<String, Number> linea = new LineChart<>(new CategoryAxis(), new NumberAxis());
                XYChart.Series<String, Number> s = new XYChart.Series<>();
                s.setName(serie);
                for (Object[] f : r.filas) s.getData().add(new XYChart.Data<>(String.valueOf(f[d.colEtiqueta]), numero(f[d.colValor])));
                linea.getData().add(s);
                linea.setLegendVisible(false);
                linea.setCreateSymbols(r.filas.size() <= 40);
                grafico = linea;
            }
            default -> {
                BarChart<String, Number> barras = new BarChart<>(new CategoryAxis(), new NumberAxis());
                XYChart.Series<String, Number> s = new XYChart.Series<>();
                s.setName(serie);
                int n = 0;
                for (Object[] f : r.filas) {
                    if (n++ >= 15) break;
                    String etiqueta = String.valueOf(f[d.colEtiqueta]);
                    if (etiqueta.length() > 22) etiqueta = etiqueta.substring(0, 21) + "…";
                    s.getData().add(new XYChart.Data<>(etiqueta, numero(f[d.colValor])));
                }
                barras.getData().add(s);
                barras.setLegendVisible(false);
                grafico = barras;
            }
        }
        grafico.getStyleClass().add("grafico-reporte");
        javafx.scene.chart.Chart chart = (javafx.scene.chart.Chart) grafico;
        chart.setPrefHeight(260);
        chart.setAnimated(false);
        contenedorGrafico.setPrefHeight(270);
        contenedorGrafico.getChildren().add(grafico);
    }

    private static Function<Object, String> formateador(TipoColumna tipo) {
        return switch (tipo) {
            case MONEDA -> v -> v instanceof Number n ? Formato.moneda(n.doubleValue()) : String.valueOf(v);
            case PORCENTAJE -> v -> v instanceof Number n ? String.format("%.1f %%", n.doubleValue()) : String.valueOf(v);
            case ENTERO -> v -> v instanceof Number n ? String.format("%,d", n.longValue()) : String.valueOf(v);
            default -> v -> {
                String s = String.valueOf(v);
                // Fechas ISO completas se muestran cortas
                return s.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}.*") ? Componentes.fechaCorta(s) : s;
            };
        };
    }

    private String descripcionFiltros() {
        StringBuilder sb = new StringBuilder();
        if (actual.filtros.contains(Filtro.FECHAS)) {
            sb.append("Período: ").append(dpDesde.getValue() == null ? "inicio" : dpDesde.getValue())
              .append(" a ").append(dpHasta.getValue() == null ? "hoy" : dpHasta.getValue());
        }
        for (ComboBox<?> c : List.of(comboUsuario, comboCategoria, comboMarca, comboCliente, comboProveedor)) {
            if (barraFiltros.getChildren().contains(c) && c.getSelectionModel().getSelectedIndex() > 0) {
                sb.append(sb.length() > 0 ? " · " : "").append(c.getValue());
            }
        }
        return sb.toString();
    }

    private static ComboBox<String> combo(String todos, List<String> valores) {
        ComboBox<String> c = new ComboBox<>();
        c.getItems().add(todos);
        c.getItems().addAll(valores);
        c.getSelectionModel().selectFirst();
        return c;
    }

    private static String valor(ComboBox<String> c) {
        return c.getSelectionModel().getSelectedIndex() > 0 ? c.getValue() : null;
    }

    private static double numero(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
