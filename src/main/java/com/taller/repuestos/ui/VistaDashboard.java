package com.taller.repuestos.ui;

import com.taller.repuestos.model.Compra;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.service.DashboardService;
import com.taller.repuestos.service.DashboardService.Punto;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Iconos;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Panel principal: indicadores del negocio, gráficos de ventas, alertas y accesos rápidos. */
public class VistaDashboard {

    private final DashboardService servicio;
    private final Consumer<String> navegar;
    private final Predicate<String> accesible;
    private final PermisoService permisos = PermisoService.get();

    /**
     * @param navegar   abre una sección del menú por su clave ("pos", "inventario"...)
     * @param accesible indica si el usuario tiene esa sección en su menú
     */
    public VistaDashboard(DashboardService servicio, Consumer<String> navegar, Predicate<String> accesible) {
        this.servicio = servicio;
        this.navegar = navegar;
        this.accesible = accesible;
    }

    public Node crear() {
        DashboardService.Resumen r = servicio.resumen();
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", new Locale("es")));

        Node hero = hero(r, fecha);

        // ---------------- Indicadores ----------------
        GridPane kpis = new GridPane();
        kpis.setHgap(14);
        kpis.setVgap(14);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cc.setHgrow(Priority.ALWAYS);
            kpis.getColumnConstraints().add(cc);
        }
        boolean finanzas = permisos.tiene(Permiso.REPORTES_FINANZAS);
        List<HBox> tarjetas = new ArrayList<>();
        tarjetas.add(tarjeta(Iconos.FACTURA, Formato.moneda(r.totalHoy), "Ventas de hoy", r.ventasHoy + " factura(s)", false));
        tarjetas.add(tarjeta(Iconos.GRAFICA, Formato.moneda(r.totalMes), "Ventas del mes", r.ventasMes + " factura(s)", false));
        tarjetas.add(finanzas
                ? tarjeta(Iconos.BILLETERA, Formato.moneda(r.gananciaMes), "Ganancias del mes", "estimado: ventas sin impuesto − costo", false)
                : tarjeta(Iconos.BILLETERA, Formato.moneda(r.saldoCreditos), "Créditos por cobrar", r.creditosPendientes + " factura(s)", false));
        tarjetas.add(tarjeta(Iconos.TUERCA, String.valueOf(r.unidadesMes), "Productos vendidos", "unidades este mes", false));
        tarjetas.add(tarjeta(Iconos.CAJA, String.valueOf(r.productosDisponibles), "Productos disponibles",
                "de " + r.productosTotal + " registrados", false));
        tarjetas.add(tarjeta(Iconos.ALERTA, String.valueOf(r.stockBajo), "Poco inventario", "en o bajo el mínimo", r.stockBajo > 0));
        tarjetas.add(tarjeta(Iconos.ALERTA, String.valueOf(r.agotados), "Productos agotados", "sin existencia", r.agotados > 0));
        tarjetas.add(tarjeta(Iconos.CLIENTES, String.valueOf(r.clientesActivos), "Total de clientes", "activos", false));
        tarjetas.add(tarjeta(Iconos.CARRITO, String.valueOf(r.comprasPendientes), "Pedidos pendientes",
                "compras por recibir de proveedores", r.comprasPendientes > 0));
        tarjetas.add(tarjeta(Iconos.DESTORNILLADOR, String.valueOf(r.ordenesAbiertas), "Vehículos en el taller",
                r.ordenesListas + " listo(s) para entregar", false));
        tarjetas.add(finanzas
                ? tarjeta(Iconos.BILLETERA, Formato.moneda(r.saldoCreditos), "Créditos por cobrar",
                        r.creditosPendientes + " factura(s)" + (r.creditosVencidos > 0 ? " · " + r.creditosVencidos + " vencida(s)" : ""),
                        r.creditosVencidos > 0)
                : tarjeta(Iconos.CLIENTES, String.valueOf(r.creditosVencidos), "Créditos vencidos", "facturas fuera de plazo", r.creditosVencidos > 0));
        tarjetas.add(finanzas
                ? tarjeta(Iconos.CAJA, Formato.moneda(r.valorInventario), "Valor del inventario", "a costo", false)
                : tarjeta(Iconos.CAJA, String.valueOf(r.productosTotal), "Productos registrados", "en el catálogo", false));
        for (int i = 0; i < tarjetas.size(); i++) {
            kpis.add(tarjetas.get(i), i % 4, i / 4);
            aparecer(tarjetas.get(i), i * 45);
        }

        // ---------------- Gráficos ----------------
        TabPane tabsVentas = new TabPane(
                new Tab("Últimos 14 días", barras(servicio.ventasPorDia(14), "Ventas")),
                new Tab("8 semanas", barras(servicio.ventasPorSemana(8), "Ventas")),
                new Tab("12 meses", linea(servicio.ventasPorMes(12))));
        if (finanzas) tabsVentas.getTabs().add(new Tab("Ingresos vs. gastos", ingresosVsGastos(servicio.ingresosVsGastos(6))));
        tabsVentas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabsVentas.setPrefHeight(300);
        VBox panelVentas = Componentes.panel(Componentes.seccion("Ventas"), tabsVentas);

        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        List<Punto> categorias = servicio.ventasPorCategoria(inicioMes);
        Node graficoCat = categorias.isEmpty() ? vacio("Aún no hay ventas este mes") : pastel(categorias);
        VBox panelCat = Componentes.panel(Componentes.seccion("Categorías más vendidas (mes)"), graficoCat);

        List<Punto> top = servicio.topProductos(inicioMes, 7);
        Node graficoTop = top.isEmpty() ? vacio("Aún no hay ventas este mes") : barrasHorizontales(top);
        VBox panelTop = Componentes.panel(Componentes.seccion("Productos más vendidos (mes)"), graficoTop);

        VBox panelAlertas = Componentes.panel(Componentes.seccion("Alertas"));
        panelAlertas.getChildren().addAll(alertas(r));

        GridPane graficos = new GridPane();
        graficos.setHgap(14);
        graficos.setVgap(14);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(60);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(40);
        graficos.getColumnConstraints().addAll(c1, c2);
        graficos.add(panelVentas, 0, 0);
        graficos.add(panelCat, 1, 0);
        graficos.add(panelTop, 0, 1);
        graficos.add(panelAlertas, 1, 1);

        // ---------------- Tablas ----------------
        HBox tablas = new HBox(14);
        VBox panelUltimas = Componentes.panel(Componentes.seccion("Últimas ventas"), tablaVentas());
        HBox.setHgrow(panelUltimas, Priority.ALWAYS);
        tablas.getChildren().add(panelUltimas);
        if (permisos.tiene(Permiso.COMPRAS_VER)) {
            VBox panelCompras = Componentes.panel(Componentes.seccion("Compras recientes"), tablaCompras());
            HBox.setHgrow(panelCompras, Priority.ALWAYS);
            tablas.getChildren().add(panelCompras);
        } else {
            VBox panelStock = Componentes.panel(Componentes.seccion("Reponer pronto"), tablaStock());
            HBox.setHgrow(panelStock, Priority.ALWAYS);
            tablas.getChildren().add(panelStock);
        }

        // ---------------- Accesos rápidos ----------------
        FlowPane acciones = new FlowPane(14, 14);
        accion(acciones, "Nueva venta", Iconos.FACTURA, "pos");
        accion(acciones, "Orden de taller", Iconos.DESTORNILLADOR, "ordenes");
        accion(acciones, "Buscar repuesto", Iconos.BUSCAR, "buscador");
        accion(acciones, "Nueva compra", Iconos.CARRITO, "compras");
        accion(acciones, "Inventario", Iconos.TUERCA, "inventario");
        accion(acciones, "Clientes", Iconos.CLIENTES, "clientes");
        accion(acciones, "Caja", Iconos.BILLETERA, "caja");
        accion(acciones, "Reportes", Iconos.GRAFICA, "reportes");

        aparecer(graficos, 320);
        aparecer(tablas, 420);
        VBox contenido = new VBox(18, hero, kpis, graficos, tablas);
        if (!acciones.getChildren().isEmpty()) contenido.getChildren().addAll(Componentes.seccion("Accesos rápidos"), acciones);
        return Componentes.scroll(contenido);
    }

    // ------------------------------------------------------------------
    // BIENVENIDA (fotografía panorámica + datos clave)
    // ------------------------------------------------------------------

    private Node hero(DashboardService.Resumen r, String fecha) {
        Region fondo = new Region();
        fondo.getStyleClass().add("hero");
        java.net.URL foto = getClass().getResource("/img/civic.jpg");
        if (foto != null) {
            fondo.setStyle("-fx-background-image: url('" + foto.toExternalForm() + "'); "
                    + "-fx-background-size: 135% auto; -fx-background-position: right 52%;");
        }
        Region velo = new Region();
        velo.setStyle("-fx-background-color: linear-gradient(to right, rgba(12,14,16,0.97) 0%, rgba(12,14,16,0.82) 38%, "
                + "rgba(12,14,16,0.15) 75%, rgba(12,14,16,0.05) 100%); -fx-background-radius: 14;");

        com.taller.repuestos.model.Usuario u = com.taller.repuestos.util.Sesion.getUsuario();
        String nombre = u == null ? "" : u.getNombreCompleto().split(" ")[0];
        Label etiqueta = new Label("JOHNZIEL · CENTRO DE OPERACIONES");
        etiqueta.getStyleClass().add("hero-etiqueta");
        Label titulo = new Label("Bienvenido" + (nombre.isEmpty() ? "" : ", " + nombre));
        titulo.getStyleClass().add("hero-titulo");
        Label sub = new Label(Character.toUpperCase(fecha.charAt(0)) + fecha.substring(1) + "  ·  " + Marca.LEMA);
        sub.getStyleClass().add("hero-subtitulo");

        HBox datos = new HBox(12,
                dato(Formato.moneda(r.totalHoy), "vendido hoy"),
                dato(String.valueOf(r.ordenesAbiertas), "vehículos en taller"),
                dato(String.valueOf(r.stockBajo + r.agotados), "repuestos por reponer"));
        VBox textos = new VBox(8, etiqueta, titulo, sub, datos);
        VBox.setMargin(datos, new javafx.geometry.Insets(10, 0, 0, 0));
        textos.setPadding(new javafx.geometry.Insets(24, 28, 24, 28));
        textos.setAlignment(Pos.CENTER_LEFT);
        textos.setMaxWidth(640);
        StackPane.setAlignment(textos, Pos.CENTER_LEFT);

        StackPane hero = new StackPane(fondo, velo, textos);
        hero.setMinHeight(260);
        hero.setPrefHeight(260);
        javafx.scene.shape.Rectangle recorte = new javafx.scene.shape.Rectangle();
        recorte.setArcWidth(28);
        recorte.setArcHeight(28);
        recorte.widthProperty().bind(hero.widthProperty());
        recorte.heightProperty().bind(hero.heightProperty());
        hero.setClip(recorte);
        aparecer(hero, 0);
        return hero;
    }

    private static VBox dato(String valor, String texto) {
        Label v = new Label(valor);
        v.getStyleClass().add("hero-dato-valor");
        Label t = new Label(texto);
        t.getStyleClass().add("hero-dato-texto");
        VBox box = new VBox(0, v, t);
        box.getStyleClass().add("hero-dato");
        return box;
    }

    /** Aparición suave: el nodo sube unos píxeles mientras se hace visible. */
    private static void aparecer(Node n, int retrasoMs) {
        n.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(380), n);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(380), n);
        t.setFromY(14);
        t.setToY(0);
        t.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        ParallelTransition p = new ParallelTransition(f, t);
        p.setDelay(Duration.millis(retrasoMs));
        p.play();
    }

    /** Barras pareadas: ingresos (rojo) contra gastos (aluminio) por mes. */
    private static Node ingresosVsGastos(List<Object[]> filas) {
        BarChart<String, Number> g = new BarChart<>(new CategoryAxis(), new NumberAxis());
        XYChart.Series<String, Number> ingresos = new XYChart.Series<>();
        ingresos.setName("Ingresos");
        XYChart.Series<String, Number> gastos = new XYChart.Series<>();
        gastos.setName("Gastos y pagos a proveedores");
        for (Object[] f : filas) {
            ingresos.getData().add(new XYChart.Data<>((String) f[0], (Double) f[1]));
            gastos.getData().add(new XYChart.Data<>((String) f[0], (Double) f[2]));
        }
        g.getData().add(ingresos);
        g.getData().add(gastos);
        g.setAnimated(false);
        g.setCategoryGap(18);
        g.setBarGap(3);
        g.getStyleClass().add("grafico-dashboard");
        return g;
    }

    // ------------------------------------------------------------------
    // ALERTAS
    // ------------------------------------------------------------------

    private List<Node> alertas(DashboardService.Resumen r) {
        VBox lista = new VBox(8);
        if (r.agotados > 0) alerta(lista, "alerta", r.agotados + " producto(s) agotado(s)", "inventario");
        if (r.stockBajo > 0) alerta(lista, "alerta", r.stockBajo + " producto(s) con stock bajo",
                accesible.test("compras") ? "compras" : "inventario");
        if (r.creditosVencidos > 0) alerta(lista, "alerta", r.creditosVencidos + " factura(s) a crédito vencida(s) · "
                + Formato.moneda(r.saldoVencido), "clientes");
        if (r.creditosPendientes > 0) alerta(lista, "info", r.creditosPendientes + " factura(s) a crédito pendiente(s) · "
                + Formato.moneda(r.saldoCreditos), "clientes");
        if (r.comprasPendientes > 0) alerta(lista, "info", r.comprasPendientes + " compra(s) pendiente(s) de recibir", "compras");
        if (r.saldoProveedores > 0.005 && permisos.tiene(Permiso.COMPRAS_VER)) {
            alerta(lista, "info", "Saldo por pagar a proveedores: " + Formato.moneda(r.saldoProveedores), "compras");
        }
        if (r.ordenesListas > 0) alerta(lista, "ok", r.ordenesListas + " vehículo(s) listo(s) para entregar", "ordenes");
        if (r.ordenesAbiertas > 0) alerta(lista, "neutro", r.ordenesAbiertas + " orden(es) de trabajo en el taller", "ordenes");
        if (lista.getChildren().isEmpty()) {
            lista.getChildren().add(Componentes.chip("Todo en orden: no hay alertas pendientes", "ok"));
        }
        return List.of(lista);
    }

    private void alerta(VBox lista, String variante, String texto, String destino) {
        StackPane punto = new StackPane();
        punto.getStyleClass().addAll("alerta-punto", "chip-" + variante);
        Label l = new Label(texto);
        l.setWrapText(true);
        l.getStyleClass().add("alerta-texto");
        HBox fila = new HBox(10, punto, l, Componentes.espaciador());
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("alerta-fila");
        if (accesible.test(destino)) {
            Button ver = new Button("Ver");
            ver.getStyleClass().add("link-button");
            ver.setOnAction(e -> navegar.accept(destino));
            fila.getChildren().add(ver);
        }
        lista.getChildren().add(fila);
    }

    // ------------------------------------------------------------------
    // GRÁFICOS
    // ------------------------------------------------------------------

    private static Node barras(List<Punto> puntos, String nombre) {
        BarChart<String, Number> g = new BarChart<>(new CategoryAxis(), new NumberAxis());
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName(nombre);
        for (Punto p : puntos) s.getData().add(new XYChart.Data<>(p.etiqueta(), p.valor()));
        g.getData().add(s);
        g.setLegendVisible(false);
        g.setAnimated(false);
        g.setCategoryGap(6);
        g.getStyleClass().add("grafico-dashboard");
        return g;
    }

    private static Node linea(List<Punto> puntos) {
        LineChart<String, Number> g = new LineChart<>(new CategoryAxis(), new NumberAxis());
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (Punto p : puntos) s.getData().add(new XYChart.Data<>(p.etiqueta(), p.valor()));
        g.getData().add(s);
        g.setLegendVisible(false);
        g.setAnimated(false);
        g.getStyleClass().add("grafico-dashboard");
        return g;
    }

    private static Node pastel(List<Punto> puntos) {
        PieChart g = new PieChart();
        double otros = 0;
        for (int i = 0; i < puntos.size(); i++) {
            if (i < 6) g.getData().add(new PieChart.Data(puntos.get(i).etiqueta(), puntos.get(i).valor()));
            else otros += puntos.get(i).valor();
        }
        if (otros > 0) g.getData().add(new PieChart.Data("Otras", otros));
        g.setLegendVisible(false);
        g.setAnimated(false);
        g.setPrefHeight(290);
        g.getStyleClass().add("grafico-dashboard");
        return g;
    }

    private static Node barrasHorizontales(List<Punto> puntos) {
        NumberAxis x = new NumberAxis();
        CategoryAxis y = new CategoryAxis();
        BarChart<Number, String> g = new BarChart<>(x, y);
        XYChart.Series<Number, String> s = new XYChart.Series<>();
        for (int i = puntos.size() - 1; i >= 0; i--) {
            String e = puntos.get(i).etiqueta();
            if (e.length() > 26) e = e.substring(0, 25) + "…";
            s.getData().add(new XYChart.Data<>(puntos.get(i).valor(), e));
        }
        g.getData().add(s);
        g.setLegendVisible(false);
        g.setAnimated(false);
        g.setPrefHeight(260);
        g.getStyleClass().add("grafico-dashboard");
        return g;
    }

    private static Node vacio(String texto) {
        Label l = Componentes.ayuda(texto);
        StackPane p = new StackPane(l);
        p.setPrefHeight(200);
        return p;
    }

    // ------------------------------------------------------------------
    // TABLAS
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private TableView<Object[]> tablaVentas() {
        TableView<Object[]> t = Componentes.tabla("Aún no hay ventas registradas");
        t.getColumns().addAll(
                Componentes.col("Factura", 90, f -> Formato.numeroFactura(((Number) f[0]).intValue())),
                Componentes.col("Fecha", 110, f -> Componentes.fechaCorta(String.valueOf(f[1]))),
                Componentes.col("Cliente", 140, f -> String.valueOf(f[2])),
                Componentes.col("Pago", 85, f -> String.valueOf(f[4])),
                Componentes.colEstado("Estado", 85, f -> "ANULADA".equals(f[6]) ? "Anulada" : "Emitida",
                        f -> "ANULADA".equals(f[6]) ? "neutro" : "ok"),
                Componentes.colMoneda("Total", 95, f -> ((Number) f[5]).doubleValue()));
        t.setItems(FXCollections.observableArrayList(servicio.ultimasVentas(8)));
        t.setPrefHeight(300);
        t.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && accesible.test("historial")) navegar.accept("historial"); });
        return t;
    }

    @SuppressWarnings("unchecked")
    private TableView<Object[]> tablaCompras() {
        TableView<Object[]> t = Componentes.tabla("Aún no hay compras registradas");
        t.getColumns().addAll(
                Componentes.col("Compra", 90, f -> Formato.numeroCompra(((Number) f[0]).intValue())),
                Componentes.col("Fecha", 110, f -> Componentes.fechaCorta(String.valueOf(f[1]))),
                Componentes.col("Proveedor", 140, f -> String.valueOf(f[2])),
                Componentes.colEstado("Estado", 110, f -> Compra.etiquetaEstado(String.valueOf(f[3])),
                        f -> switch (String.valueOf(f[3])) {
                            case Compra.RECIBIDA -> "ok";
                            case Compra.ANULADA -> "neutro";
                            case Compra.PARCIAL -> "info";
                            default -> "alerta";
                        }),
                Componentes.colMoneda("Total", 95, f -> ((Number) f[4]).doubleValue()));
        t.setItems(FXCollections.observableArrayList(servicio.comprasRecientes(8)));
        t.setPrefHeight(300);
        t.setOnMouseClicked(e -> { if (e.getClickCount() == 2) navegar.accept("compras"); });
        return t;
    }

    @SuppressWarnings("unchecked")
    private TableView<Object[]> tablaStock() {
        TableView<Object[]> t = Componentes.tabla("Ningún producto bajo su mínimo");
        t.getColumns().addAll(
                Componentes.col("Código", 90, f -> String.valueOf(f[0])),
                Componentes.col("Producto", 200, f -> String.valueOf(f[1])),
                Componentes.colEntero("Stock", 60, f -> ((Number) f[2]).intValue()),
                Componentes.colEntero("Mínimo", 60, f -> ((Number) f[3]).intValue()));
        t.setItems(FXCollections.observableArrayList(servicio.stockCritico(10)));
        t.setPrefHeight(300);
        return t;
    }

    // ------------------------------------------------------------------
    // PIEZAS
    // ------------------------------------------------------------------

    private static HBox tarjeta(String icono, String valor, String titulo, String detalle, boolean alerta) {
        StackPane cajaIcono = new StackPane(Iconos.crear(icono, 26));
        cajaIcono.getStyleClass().add("card-icon-box");
        Label lblValor = new Label(valor);
        lblValor.getStyleClass().add("card-value");
        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("card-label");
        Label lblDetalle = new Label(detalle);
        lblDetalle.getStyleClass().add("card-detail");
        VBox textos = new VBox(1, lblValor, lblTitulo, lblDetalle);
        HBox t = new HBox(14, cajaIcono, textos);
        t.setAlignment(Pos.CENTER_LEFT);
        t.setMaxWidth(Double.MAX_VALUE);
        t.getStyleClass().add("dashboard-card");
        if (alerta) t.getStyleClass().add("dashboard-card-alert");
        GridPane.setHgrow(t, Priority.ALWAYS);
        return t;
    }

    private void accion(FlowPane destino, String texto, String icono, String clave) {
        if (!accesible.test(clave)) return;
        Button btn = new Button(texto, Iconos.crear(icono, 34));
        btn.setContentDisplay(ContentDisplay.TOP);
        btn.setGraphicTextGap(8);
        btn.setMinSize(138, 100);
        btn.getStyleClass().add("tool-button");
        btn.setOnAction(e -> navegar.accept(clave));
        ScaleTransition escala = new ScaleTransition(Duration.millis(140), btn);
        btn.setOnMouseEntered(e -> { escala.stop(); escala.setToX(1.05); escala.setToY(1.05); escala.play(); });
        btn.setOnMouseExited(e -> { escala.stop(); escala.setToX(1.0); escala.setToY(1.0); escala.play(); });
        destino.getChildren().add(btn);
    }
}
