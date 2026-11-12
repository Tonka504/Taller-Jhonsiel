package com.taller.repuestos.ui;

import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Iconos;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Piezas de interfaz que se repiten en todas las pantallas (títulos, columnas de tabla, botones,
 * formularios). Así todas las vistas se ven y se comportan igual.
 */
public final class Componentes {

    private Componentes() {}

    // ------------------------------------------------------------------
    // TEXTOS
    // ------------------------------------------------------------------

    public static Label titulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("dashboard-title");
        return l;
    }

    public static Label seccion(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("section-title");
        return l;
    }

    public static Label ayuda(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("hint-label");
        l.setWrapText(true);
        return l;
    }

    /** Etiqueta tipo "chip" de color según el estado (ok, alerta, neutro, info). */
    public static Label chip(String texto, String variante) {
        Label l = new Label(texto);
        l.getStyleClass().addAll("chip", "chip-" + variante);
        return l;
    }

    // ------------------------------------------------------------------
    // CONTENEDORES
    // ------------------------------------------------------------------

    /** Envuelve el contenido en un scroll transparente con el padding estándar de las vistas. */
    public static ScrollPane scroll(Node contenido) {
        if (contenido instanceof Region r) r.setPadding(new Insets(16));
        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dashboard-scroll");
        return scroll;
    }

    /** Tarjeta blanca con borde (panel-card) que agrupa varios nodos verticalmente. */
    public static VBox panel(Node... hijos) {
        VBox v = new VBox(10, hijos);
        v.getStyleClass().add("panel-card");
        return v;
    }

    /** Fila horizontal con los nodos alineados al centro. */
    public static HBox fila(Node... hijos) {
        HBox h = new HBox(8, hijos);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    /** Espacio flexible para empujar elementos a la derecha dentro de un HBox. */
    public static Region espaciador() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Formulario de dos columnas (etiqueta / campo). */
    public static GridPane formulario() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        c2.setFillWidth(true);
        grid.getColumnConstraints().addAll(c1, c2);
        return grid;
    }

    /** Agrega una fila al formulario y devuelve el número de la siguiente. */
    public static int campo(GridPane grid, int fila, String etiqueta, Node campo) {
        Label l = new Label(etiqueta);
        l.getStyleClass().add("form-label");
        grid.add(l, 0, fila);
        grid.add(campo, 1, fila);
        if (campo instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
        }
        return fila + 1;
    }

    // ------------------------------------------------------------------
    // BOTONES
    // ------------------------------------------------------------------

    /** Botón con estilo ("primary-button", "secondary-button", "danger-button"), icono opcional y acción. */
    public static Button boton(String texto, String estilo, String icono, Runnable accion) {
        Button b = icono == null ? new Button(texto) : new Button(texto, Iconos.crear(icono, 16));
        if (estilo != null) b.getStyleClass().add(estilo);
        b.setGraphicTextGap(8);
        if (accion != null) b.setOnAction(e -> accion.run());
        return b;
    }

    public static Button boton(String texto, String estilo, String icono, String tooltip, Runnable accion) {
        Button b = boton(texto, estilo, icono, accion);
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    /**
     * Ejecuta la acción mostrando el estado "cargando" en el botón (indicador giratorio y texto "Procesando…").
     * La acción corre igual que antes, en el hilo de la interfaz; solo se espera un instante para que el
     * indicador alcance a dibujarse. Evita además el doble clic mientras se procesa.
     */
    public static void conCarga(Button b, Runnable accion) {
        if (b.getStyleClass().contains("cargando")) return;
        Node grafico = b.getGraphic();
        String texto = b.getText();
        javafx.scene.control.ProgressIndicator pi = new javafx.scene.control.ProgressIndicator();
        pi.setPrefSize(15, 15);
        pi.setMaxSize(15, 15);
        pi.getStyleClass().add("boton-carga");
        b.getStyleClass().add("cargando");
        b.setMouseTransparent(true);
        b.setGraphic(pi);
        b.setText("Procesando…");
        javafx.animation.PauseTransition espera = new javafx.animation.PauseTransition(javafx.util.Duration.millis(70));
        // runLater: la acción puede abrir diálogos modales, que JavaFX no permite dentro de una animación
        espera.setOnFinished(e -> javafx.application.Platform.runLater(() -> {
            try {
                accion.run();
            } finally {
                b.getStyleClass().remove("cargando");
                b.setMouseTransparent(false);
                b.setGraphic(grafico);
                b.setText(texto);
            }
        }));
        espera.play();
    }

    // ------------------------------------------------------------------
    // TABLAS
    // ------------------------------------------------------------------

    public static <T> TableColumn<T, String> col(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new SimpleStringProperty(valor.apply(d.getValue())));
        c.setPrefWidth(ancho);
        return c;
    }

    /** Columna de dinero: se ordena como número y se muestra con el símbolo de la moneda. */
    public static <T> TableColumn<T, Number> colMoneda(String titulo, double ancho, ToDoubleFunction<T> valor) {
        TableColumn<T, Number> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new SimpleDoubleProperty(valor.applyAsDouble(d.getValue())));
        c.setPrefWidth(ancho);
        c.setCellFactory(x -> new TableCell<>() {
            @Override
            protected void updateItem(Number v, boolean vacio) {
                super.updateItem(v, vacio);
                setText(vacio || v == null ? null : Formato.moneda(v.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });
        return c;
    }

    public static <T> TableColumn<T, Number> colEntero(String titulo, double ancho, ToIntFunction<T> valor) {
        TableColumn<T, Number> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new SimpleIntegerProperty(valor.applyAsInt(d.getValue())));
        c.setPrefWidth(ancho);
        c.setCellFactory(x -> new TableCell<>() {
            @Override
            protected void updateItem(Number v, boolean vacio) {
                super.updateItem(v, vacio);
                setText(vacio || v == null ? null : String.valueOf(v.intValue()));
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });
        return c;
    }

    /** Columna de estado que se pinta como chip de color. */
    public static <T> TableColumn<T, String> colEstado(String titulo, double ancho, Function<T, String> texto,
                                                      Function<T, String> variante) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new SimpleStringProperty(texto.apply(d.getValue())));
        c.setPrefWidth(ancho);
        c.setCellFactory(x -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean vacio) {
                super.updateItem(v, vacio);
                setText(null);
                if (vacio || v == null || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(chip(v, variante.apply(getTableRow().getItem())));
            }
        });
        return c;
    }

    /** Columna genérica con objeto (para ordenar por un valor que no es texto). */
    public static <T, V> TableColumn<T, V> colObjeto(String titulo, double ancho, Function<T, V> valor) {
        TableColumn<T, V> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new SimpleObjectProperty<>(valor.apply(d.getValue())));
        c.setPrefWidth(ancho);
        return c;
    }

    /** Columna con la miniatura de la foto del producto. */
    public static <T> TableColumn<T, String> colFoto(Function<T, String> foto) {
        TableColumn<T, String> c = new TableColumn<>("");
        c.setCellValueFactory(d -> new SimpleStringProperty(foto.apply(d.getValue())));
        c.setPrefWidth(42);
        c.setMinWidth(42);
        c.setMaxWidth(42);
        c.setSortable(false);
        c.setCellFactory(x -> new TableCell<>() {
            private final FotoProducto f = new FotoProducto(30);
            @Override
            protected void updateItem(String v, boolean vacio) {
                super.updateItem(v, vacio);
                setText(null);
                if (vacio) {
                    setGraphic(null);
                } else {
                    f.mostrar(v);
                    setGraphic(f);
                }
            }
        });
        return c;
    }

    /** Tabla con el estilo y comportamiento estándar. */
    public static <T> TableView<T> tabla(String vacio) {
        TableView<T> t = new TableView<>();
        t.setPlaceholder(new Label(vacio));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(t, Priority.ALWAYS);
        return t;
    }

    // ------------------------------------------------------------------
    // CAMPOS Y CONVERSIONES
    // ------------------------------------------------------------------

    public static DatePicker fecha(LocalDate inicial) {
        DatePicker d = new DatePicker(inicial);
        d.setPrefWidth(140);
        return d;
    }

    /** Texto sin null. */
    public static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Lee un entero escrito por el usuario; null si está vacío. Lanza NumberFormatException si no es número. */
    public static Integer entero(String texto) {
        if (texto == null || texto.trim().isEmpty()) return null;
        return Integer.parseInt(texto.trim());
    }

    /** Lee un monto escrito por el usuario; null si está vacío. Lanza NumberFormatException si no es número. */
    public static Double monto(String texto) {
        if (texto == null || texto.trim().isEmpty()) return null;
        return Formato.leerMonto(texto);
    }

    /** "2026-11-11 14:30:05" -> "11/11/2026 14:30". */
    public static String fechaCorta(String iso) {
        if (iso == null || iso.length() < 10) return nz(iso);
        String f = iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
        return iso.length() >= 16 ? f + " " + iso.substring(11, 16) : f;
    }
}
