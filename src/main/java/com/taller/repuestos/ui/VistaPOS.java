package com.taller.repuestos.ui;

import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Cliente;
import com.taller.repuestos.model.Compatibilidad;
import com.taller.repuestos.model.ItemCarrito;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.model.Rol;
import com.taller.repuestos.model.Usuario;
import com.taller.repuestos.service.BuscadorRepuestos;
import com.taller.repuestos.service.CalculoVenta;
import com.taller.repuestos.service.ClienteService;
import com.taller.repuestos.service.CompatibilidadService;
import com.taller.repuestos.service.ConfiguracionService;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.service.VentaService;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Sesion;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Punto de venta: busca productos (o escanea su código), arma el carrito y cobra. */
public class VistaPOS {

    private static final String PAGO_EFECTIVO = "Efectivo";

    private final ObservableList<Repuesto> productos;
    private final VentaService ventaService;
    private final ClienteService clienteService;
    private final CompatibilidadService compatibilidadService;
    private final Runnable alVender;
    private final PermisoService permisos = PermisoService.get();
    private final ConfiguracionService config = ConfiguracionService.get();

    private final ObservableList<ItemCarrito> carrito = FXCollections.observableArrayList();
    private Cliente cliente;                 // null = consumidor final
    private String tipoDescuento;            // descuento general: null, PORCENTAJE o MONTO
    private double valorDescuento;
    private double ultimoTotal;
    private Map<Integer, List<Compatibilidad>> compatPorProducto = new HashMap<>();
    private FilteredList<Repuesto> productosFiltrados;
    private String ultimoTexto = "";

    private TextField txtBuscar;
    private Label lblEstadoBusqueda;
    private TableView<ItemCarrito> tablaCarrito;
    private Label lblCliente;
    private Label lblSubtotal;
    private Label lblDescuento;
    private Label lblImpuesto;
    private Label lblTotal;
    private Label lblCambio;
    private Label lblCredito;
    private ComboBox<String> comboPago;
    private TextField txtRecibido;
    private DatePicker dpLimite;
    private HBox boxEfectivo;
    private VBox boxCredito;
    private final com.taller.repuestos.service.CotizacionService cotizacionService;
    private Integer cotizacionCargada;      // cotización que se está cobrando (se marca como vendida al procesar)
    private Label lblCotizacion;
    private Label lblUnidades;
    private TableView<Repuesto> tablaProductosRef;
    private javafx.scene.layout.FlowPane mosaico;
    private Label lblMosaico;
    private static final int MAX_TARJETAS = 80;

    /**
     * @param productos  la lista de repuestos del sistema (se actualiza sola cuando cambia el inventario)
     * @param alVender   se ejecuta tras una venta para que el sistema recargue las existencias
     */
    public VistaPOS(ObservableList<Repuesto> productos, VentaService ventaService, ClienteService clienteService,
                    CompatibilidadService compatibilidadService,
                    com.taller.repuestos.service.CotizacionService cotizacionService, Runnable alVender) {
        this.cotizacionService = cotizacionService;
        this.productos = productos;
        this.ventaService = ventaService;
        this.clienteService = clienteService;
        this.compatibilidadService = compatibilidadService;
        this.alVender = alVender;
    }

    // ------------------------------------------------------------------
    // CONSTRUCCIÓN DE LA PANTALLA
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public Node crear() {
        // ---------------- Productos ----------------
        Label lblProductos = new Label("Productos disponibles:");
        lblProductos.getStyleClass().add("section-title");

        txtBuscar = new TextField();
        txtBuscar.setPromptText("🔍 Código, nombre, vehículo (ej: Toyota Hilux 2007 filtro) o escanea el código de barras...");
        lblEstadoBusqueda = new Label();

        compatPorProducto = compatibilidadService.porRepuesto();
        FilteredList<Repuesto> filtrados = new FilteredList<>(productos, p -> true);
        productosFiltrados = filtrados;
        txtBuscar.textProperty().addListener((obs, anterior, nuevo) -> {
            ultimoTexto = nuevo == null ? "" : nuevo;
            aplicarFiltro();
        });

        TableView<Repuesto> tablaProductos = new TableView<>();
        TableColumn<Repuesto, String> pCodigo = new TableColumn<>("Código");
        pCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        pCodigo.setPrefWidth(90);
        TableColumn<Repuesto, String> pDesc = new TableColumn<>("Descripción");
        pDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescripcion()));
        pDesc.setPrefWidth(200);
        TableColumn<Repuesto, String> pMarca = new TableColumn<>("Marca");
        pMarca.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMarca()));
        pMarca.setPrefWidth(90);
        TableColumn<Repuesto, String> pUbicacion = new TableColumn<>("Ubicación");
        pUbicacion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUbicacion()));
        pUbicacion.setPrefWidth(85);
        TableColumn<Repuesto, String> pPrecio = new TableColumn<>("Precio");
        pPrecio.setCellValueFactory(c -> new SimpleStringProperty(Formato.moneda(c.getValue().getPrecio())));
        pPrecio.setPrefWidth(90);
        TableColumn<Repuesto, Integer> pStock = new TableColumn<>("Stock");
        pStock.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getStock()).asObject());
        pStock.setPrefWidth(60);
        tablaProductos.getColumns().add(Componentes.colFoto(Repuesto::getFoto));
        tablaProductos.getColumns().addAll(pCodigo, pDesc, pMarca, pUbicacion, pPrecio, pStock);
        tablaProductos.setItems(filtrados);
        tablaProductos.setPlaceholder(new Label("No hay productos que coincidan"));
        VBox.setVgrow(tablaProductos, Priority.ALWAYS);

        txtBuscar.setOnAction(e -> procesarCodigoEscaneado(tablaProductos, filtrados));

        Button btnAgregar = new Button("Agregar al carrito");
        btnAgregar.getStyleClass().add("secondary-button");
        btnAgregar.setOnAction(e -> {
            Repuesto seleccionado = tablaProductos.getSelectionModel().getSelectedItem();
            if (seleccionado == null) {
                Alertas.advertencia("Sin selección", "Selecciona un producto de la tabla.");
                return;
            }
            agregarAlCarrito(seleccionado);
        });

        // Ficha del producto seleccionado: foto, datos, ubicación, compatibilidad y disponibilidad
        FotoProducto fotoFicha = new FotoProducto(110);
        Label lblFichaNombre = new Label("Selecciona un producto para ver su ficha");
        lblFichaNombre.getStyleClass().add("metric-value");
        lblFichaNombre.setWrapText(true);
        Label lblFichaDatos = new Label();
        lblFichaDatos.getStyleClass().add("hint-label");
        lblFichaDatos.setWrapText(true);
        HBox chipFicha = new HBox();
        VBox textosFicha = new VBox(4, lblFichaNombre, chipFicha, lblFichaDatos);
        HBox.setHgrow(textosFicha, Priority.ALWAYS);
        HBox ficha = new HBox(14, fotoFicha, textosFicha);
        ficha.getStyleClass().add("panel-card");
        ficha.setAlignment(Pos.CENTER_LEFT);
        tablaProductos.getSelectionModel().selectedItemProperty().addListener((obs, anterior, r) -> {
            if (r == null) return;
            fotoFicha.mostrar(r.getFoto());
            lblFichaNombre.setText(r.getDescripcion() + "  ·  " + Formato.moneda(precioPara(r)));
            chipFicha.getChildren().setAll(r.getStock() <= 0 ? Componentes.chip("Agotado", "alerta")
                    : r.isStockBajo() ? Componentes.chip("Stock bajo: " + r.getStock(), "info")
                    : Componentes.chip("Disponible: " + r.getStock() + " en existencia", "ok"));
            String compat = com.taller.repuestos.service.BuscadorRepuestos.resumenCompatibilidad(compatPorProducto.get(r.getId()));
            lblFichaDatos.setText("Código: " + r.getCodigo() + (r.getMarca() == null || r.getMarca().isEmpty() ? "" : "   ·   Marca: " + r.getMarca())
                    + (r.getUbicacion() == null || r.getUbicacion().isEmpty() ? "" : "   ·   Ubicación: " + r.getUbicacion())
                    + "\nCompatible con: " + (compat == null || compat.isEmpty() ? "sin vehículos registrados" : compat));
        });

        // Vista en tarjetas (por defecto) o en lista; ambas comparten la misma selección
        tablaProductosRef = tablaProductos;
        mosaico = new javafx.scene.layout.FlowPane(12, 12);
        mosaico.setPadding(new Insets(4, 4, 8, 4));
        lblMosaico = new Label();
        lblMosaico.getStyleClass().add("hint-label");
        ScrollPane scrollMosaico = new ScrollPane(new VBox(6, mosaico, lblMosaico));
        scrollMosaico.setFitToWidth(true);
        scrollMosaico.getStyleClass().add("dashboard-scroll");
        scrollMosaico.setPrefHeight(330);
        javafx.scene.layout.StackPane catalogo = new javafx.scene.layout.StackPane(tablaProductos, scrollMosaico);
        VBox.setVgrow(catalogo, Priority.ALWAYS);
        filtrados.addListener((javafx.collections.ListChangeListener<Repuesto>) c -> reconstruirMosaico());
        tablaProductos.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> marcarTarjeta(n));

        javafx.scene.control.ToggleGroup grupoVista = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.ToggleButton tgTarjetas = new javafx.scene.control.ToggleButton("Tarjetas",
                com.taller.repuestos.util.Iconos.crear(com.taller.repuestos.util.Iconos.MOSAICO, 14));
        javafx.scene.control.ToggleButton tgLista = new javafx.scene.control.ToggleButton("Lista",
                com.taller.repuestos.util.Iconos.crear(com.taller.repuestos.util.Iconos.LISTA, 14));
        for (javafx.scene.control.ToggleButton tg : List.of(tgTarjetas, tgLista)) {
            tg.setToggleGroup(grupoVista);
            tg.getStyleClass().add("interruptor");
        }
        tgTarjetas.setSelected(true);
        tablaProductos.setVisible(false);
        grupoVista.selectedToggleProperty().addListener((o, a, n) -> {
            if (n == null) { a.setSelected(true); return; }
            boolean tarjetas = n == tgTarjetas;
            scrollMosaico.setVisible(tarjetas);
            tablaProductos.setVisible(!tarjetas);
        });
        HBox encabezadoProductos = new HBox(8, lblProductos, new javafx.scene.layout.Region(), tgTarjetas, tgLista);
        HBox.setHgrow(encabezadoProductos.getChildren().get(1), Priority.ALWAYS);
        encabezadoProductos.setAlignment(Pos.CENTER_LEFT);
        reconstruirMosaico();

        VBox panelProductos = new VBox(10, encabezadoProductos, txtBuscar, lblEstadoBusqueda, catalogo, ficha, btnAgregar);
        panelProductos.setPrefWidth(600);
        HBox.setHgrow(panelProductos, Priority.ALWAYS);

        // ---------------- Cliente ----------------
        lblCliente = new Label();
        lblCliente.getStyleClass().add("metric-value");
        Button btnCliente = new Button("Elegir cliente");
        btnCliente.getStyleClass().add("secondary-button");
        btnCliente.setOnAction(e -> elegirCliente());
        lblCotizacion = new Label();
        lblCotizacion.getStyleClass().addAll("chip", "chip-info");
        mostrar(lblCotizacion, false);
        HBox filaCliente = new HBox(12, lblCliente, btnCliente, lblCotizacion);
        filaCliente.setAlignment(Pos.CENTER_LEFT);
        actualizarEtiquetaCliente();

        // ---------------- Carrito ----------------
        Label lblCarrito = new Label("Carrito de venta:");
        lblCarrito.getStyleClass().add("section-title");

        tablaCarrito = new TableView<>();
        TableColumn<ItemCarrito, String> cProducto = new TableColumn<>("Producto");
        cProducto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRepuesto().getDescripcion()));
        cProducto.setPrefWidth(190);
        TableColumn<ItemCarrito, Integer> cCant = new TableColumn<>("Cant.");
        cCant.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getCantidad()).asObject());
        cCant.setPrefWidth(55);
        TableColumn<ItemCarrito, String> cPrecio = new TableColumn<>("Precio");
        cPrecio.setCellValueFactory(c -> new SimpleStringProperty(Formato.moneda(c.getValue().getPrecioUnitario())));
        cPrecio.setPrefWidth(85);
        TableColumn<ItemCarrito, String> cDesc = new TableColumn<>("Desc.");
        cDesc.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDescuentoPct() > 0 ? porcentaje(c.getValue().getDescuentoPct()) + "%" : ""));
        cDesc.setPrefWidth(55);
        TableColumn<ItemCarrito, String> cSubtotal = new TableColumn<>("Subtotal");
        cSubtotal.setCellValueFactory(c -> new SimpleStringProperty(Formato.moneda(c.getValue().getSubtotal())));
        cSubtotal.setPrefWidth(90);
        tablaCarrito.getColumns().addAll(cProducto, cCant, cPrecio, cDesc, cSubtotal);
        tablaCarrito.setItems(carrito);
        tablaCarrito.setPlaceholder(new Label("El carrito está vacío"));
        tablaCarrito.setPrefHeight(210);

        Button btnMenos = new Button("−");
        btnMenos.getStyleClass().add("secondary-button");
        btnMenos.setOnAction(e -> cambiarCantidad(-1));
        Button btnMas = new Button("+");
        btnMas.getStyleClass().add("secondary-button");
        btnMas.setOnAction(e -> cambiarCantidad(1));
        Button btnQuitar = new Button("Quitar");
        btnQuitar.getStyleClass().add("danger-button");
        btnQuitar.setOnAction(e -> {
            ItemCarrito sel = tablaCarrito.getSelectionModel().getSelectedItem();
            if (sel != null) {
                carrito.remove(sel);
                actualizarTotales();
            }
        });
        Button btnVaciar = new Button("Vaciar");
        btnVaciar.getStyleClass().add("secondary-button");
        btnVaciar.setOnAction(e -> {
            if (!carrito.isEmpty() && Alertas.confirmar("Vaciar carrito", "¿Quitar todos los productos del carrito?")) {
                limpiarVenta();
            }
        });
        HBox filaCarrito = new HBox(8, btnMenos, btnMas, btnQuitar, btnVaciar);

        boolean puedeDescontar = permisos.tiene(Permiso.DESCUENTOS_APLICAR);
        Button btnDescLinea = new Button("Descuento del producto");
        btnDescLinea.getStyleClass().add("secondary-button");
        btnDescLinea.setOnAction(e -> descuentoDeLinea());
        Button btnDescGeneral = new Button("Descuento general");
        btnDescGeneral.getStyleClass().add("secondary-button");
        btnDescGeneral.setOnAction(e -> descuentoGeneral());
        Button btnQuitarDesc = new Button("Quitar descuentos");
        btnQuitarDesc.getStyleClass().add("secondary-button");
        btnQuitarDesc.setOnAction(e -> {
            for (ItemCarrito i : carrito) i.setDescuentoPct(0);
            tipoDescuento = null;
            valorDescuento = 0;
            tablaCarrito.refresh();
            actualizarTotales();
        });
        HBox filaDescuentos = new HBox(8, btnDescLinea, btnDescGeneral, btnQuitarDesc);
        filaDescuentos.setVisible(puedeDescontar);
        filaDescuentos.setManaged(puedeDescontar);

        // ---------------- Totales ----------------
        lblSubtotal = new Label();
        lblSubtotal.getStyleClass().add("metric-label");
        lblDescuento = new Label();
        lblDescuento.getStyleClass().add("metric-label");
        lblImpuesto = new Label();
        lblImpuesto.getStyleClass().add("metric-label");
        lblTotal = new Label();
        lblTotal.getStyleClass().add("total-label");
        Label lblResumen = new Label("RESUMEN DE LA VENTA");
        lblResumen.getStyleClass().add("cart-total-titulo");
        lblUnidades = new Label();
        lblUnidades.getStyleClass().add("metric-label");
        Label lblTotalTitulo = new Label("TOTAL A PAGAR");
        lblTotalTitulo.getStyleClass().add("cart-total-titulo");
        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setOpacity(0.35);
        VBox panelTotales = new VBox(4, lblResumen, lblUnidades, lblSubtotal, lblDescuento, lblImpuesto, sep, lblTotalTitulo, lblTotal);
        panelTotales.getStyleClass().add("cart-panel");

        // ---------------- Pago ----------------
        comboPago = new ComboBox<>();
        comboPago.getItems().addAll(VentaService.METODOS_PAGO);
        comboPago.setValue(PAGO_EFECTIVO);

        txtRecibido = new TextField();
        txtRecibido.setPromptText("Monto recibido");
        txtRecibido.setPrefWidth(130);
        Button btnExacto = new Button("Exacto");
        btnExacto.getStyleClass().add("secondary-button");
        btnExacto.setOnAction(e -> txtRecibido.setText(String.format("%.2f", ultimoTotal)));
        lblCambio = new Label("Cambio: -");
        lblCambio.getStyleClass().add("metric-value");
        boxEfectivo = new HBox(10, txtRecibido, btnExacto, lblCambio);
        boxEfectivo.setAlignment(Pos.CENTER_LEFT);

        lblCredito = new Label();
        lblCredito.getStyleClass().add("hint-label");
        lblCredito.setWrapText(true);
        dpLimite = new DatePicker();
        dpLimite.setValue(LocalDate.now().plusDays(config.diasCredito()));
        HBox filaLimite = new HBox(10, new Label("Fecha límite de pago:"), dpLimite);
        filaLimite.setAlignment(Pos.CENTER_LEFT);
        boxCredito = new VBox(6, lblCredito, filaLimite);

        txtRecibido.textProperty().addListener((obs, anterior, nuevo) -> actualizarCambio());
        comboPago.setOnAction(e -> alCambiarPago());

        Button btnProcesar = new Button("Procesar venta");
        btnProcesar.getStyleClass().add("primary-button");
        btnProcesar.setMaxWidth(Double.MAX_VALUE);
        btnProcesar.setOnAction(e -> Componentes.conCarga(btnProcesar, this::procesar));

        Label lblPago = new Label("Forma de pago:");
        lblPago.getStyleClass().add("metric-label");
        Button btnCotizar = new Button("Guardar como cotización");
        btnCotizar.getStyleClass().add("secondary-button");
        btnCotizar.setMaxWidth(Double.MAX_VALUE);
        btnCotizar.setOnAction(e -> guardarCotizacion());
        VBox panelPago = new VBox(8, new HBox(10, lblPago, comboPago), boxEfectivo, boxCredito, btnProcesar, btnCotizar);
        panelPago.getStyleClass().add("panel-card");

        VBox panelVenta = new VBox(10, filaCliente, lblCarrito, tablaCarrito, filaCarrito, filaDescuentos, panelTotales, panelPago);
        panelVenta.setPrefWidth(520);

        HBox raiz = new HBox(16, panelProductos, panelVenta);
        raiz.setPadding(new Insets(16));

        alCambiarPago();
        actualizarTotales();

        ScrollPane scroll = new ScrollPane(raiz);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dashboard-scroll");
        return scroll;
    }

    /** Deja el cursor en el buscador, listo para escanear o escribir. */
    public void enfocarBusqueda() {
        if (txtBuscar != null) Platform.runLater(() -> txtBuscar.requestFocus());
    }

    // ------------------------------------------------------------------
    // CARRITO
    // ------------------------------------------------------------------

    /** Agrega una unidad del producto al carrito. Devuelve false (y avisa) si no hay existencias suficientes. */
    private boolean agregarAlCarrito(Repuesto producto) {
        if (producto.getStock() <= 0) {
            Alertas.advertencia("Sin stock", "Este producto no tiene existencias disponibles.");
            return false;
        }
        ItemCarrito existente = buscarEnCarrito(producto);
        if (existente != null) {
            if (existente.getCantidad() >= producto.getStock()) {
                Alertas.advertencia("Límite de stock", "No puedes agregar más unidades que las disponibles (" + producto.getStock() + ").");
                return false;
            }
            existente.setCantidad(existente.getCantidad() + 1);
        } else {
            ItemCarrito nuevo = new ItemCarrito(producto, 1);
            nuevo.setPrecioUnitario(precioPara(producto));
            carrito.add(nuevo);
        }
        tablaCarrito.refresh();
        actualizarTotales();
        return true;
    }

    private ItemCarrito buscarEnCarrito(Repuesto producto) {
        for (ItemCarrito item : carrito) {
            if (item.getRepuesto().getId() == producto.getId()) return item;
        }
        return null;
    }

    /** Suma o resta una unidad a la línea seleccionada (si llega a cero se quita). */
    private void cambiarCantidad(int delta) {
        ItemCarrito sel = tablaCarrito.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Alertas.advertencia("Sin selección", "Selecciona una línea del carrito.");
            return;
        }
        int nueva = sel.getCantidad() + delta;
        if (nueva <= 0) {
            carrito.remove(sel);
        } else if (nueva > sel.getRepuesto().getStock()) {
            Alertas.advertencia("Límite de stock", "Solo hay " + sel.getRepuesto().getStock() + " unidades disponibles.");
            return;
        } else {
            sel.setCantidad(nueva);
        }
        tablaCarrito.refresh();
        actualizarTotales();
    }

    /**
     * Enter en el buscador (lo que hace un lector de código de barras al terminar de leer):
     * si el texto es exactamente el código de un producto, lo agrega y deja el buscador listo para el siguiente.
     */
    private void procesarCodigoEscaneado(TableView<Repuesto> tabla, FilteredList<Repuesto> filtrados) {
        String codigo = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim();
        if (codigo.isEmpty()) return;

        Repuesto exacto = null;
        for (Repuesto r : productos) {
            boolean porCodigo = r.getCodigo() != null && r.getCodigo().trim().equalsIgnoreCase(codigo);
            boolean porBarras = r.getCodigoBarras() != null && r.getCodigoBarras().trim().equalsIgnoreCase(codigo);
            if (porCodigo || porBarras) {
                exacto = r;
                break;
            }
        }

        if (exacto != null) {
            if (agregarAlCarrito(exacto)) {
                txtBuscar.clear();
                mostrarEstadoBusqueda("Agregado: " + exacto.getDescripcion(), false);
            } else {
                txtBuscar.selectAll();
            }
        } else if (filtrados.size() == 1) {
            tabla.getSelectionModel().select(0);
            mostrarEstadoBusqueda("Hay un resultado y quedó seleccionado. Presiona \"Agregar al carrito\".", false);
        } else if (filtrados.size() > 1) {
            mostrarEstadoBusqueda("Hay " + filtrados.size() + " resultados: elige uno de la tabla.", false);
        } else {
            mostrarEstadoBusqueda("No existe ningún producto con ese código: " + codigo, true);
            txtBuscar.selectAll();
        }
        txtBuscar.requestFocus();
    }

    private void mostrarEstadoBusqueda(String texto, boolean error) {
        lblEstadoBusqueda.setText(texto);
        lblEstadoBusqueda.getStyleClass().removeAll("estado-ok", "estado-error");
        lblEstadoBusqueda.getStyleClass().add(error ? "estado-error" : "estado-ok");
    }

    // ------------------------------------------------------------------
    // CLIENTE Y DESCUENTOS
    // ------------------------------------------------------------------

    private void elegirCliente() {
        Cliente elegido = DialogoSeleccionCliente.mostrar(clienteService);
        if (elegido == null) return;
        cliente = elegido == DialogoSeleccionCliente.CONSUMIDOR_FINAL ? null : elegido;
        actualizarEtiquetaCliente();
        actualizarInfoCredito();
        repreciarCarrito();
    }

    private void actualizarEtiquetaCliente() {
        lblCliente.setText("Cliente: " + (cliente == null ? "Consumidor final" : cliente.getNombre())
                + (esMayorista() ? "  (precio mayorista)" : ""));
    }

    private boolean esMayorista() {
        return cliente != null && "Mayorista".equals(cliente.getTipo());
    }

    /** Precio al que se vende el producto a este cliente: el mayorista (si existe) para clientes mayoristas. */
    private double precioPara(Repuesto producto) {
        if (esMayorista() && producto.getPrecioMayorista() > 0) return producto.getPrecioMayorista();
        return producto.getPrecio();
    }

    /** Al cambiar de cliente se vuelven a calcular los precios de las líneas que ya están en el carrito. */
    private void repreciarCarrito() {
        for (ItemCarrito item : carrito) item.setPrecioUnitario(precioPara(item.getRepuesto()));
        tablaCarrito.refresh();
        actualizarTotales();
        reconstruirMosaico(); // las tarjetas muestran el precio del cliente (minorista o mayorista)
    }

    /** Vuelve a dibujar las tarjetas con los productos que pasan el filtro de búsqueda. */
    private void reconstruirMosaico() {
        if (mosaico == null) return;
        List<javafx.scene.Node> tarjetas = new ArrayList<>();
        int n = 0;
        for (Repuesto r : productosFiltrados) {
            if (n++ >= MAX_TARJETAS) break;
            tarjetas.add(tarjetaProducto(r));
        }
        mosaico.getChildren().setAll(tarjetas);
        lblMosaico.setText(productosFiltrados.isEmpty() ? "No hay productos que coincidan con la búsqueda."
                : productosFiltrados.size() > MAX_TARJETAS
                    ? "Mostrando " + MAX_TARJETAS + " de " + productosFiltrados.size() + " productos: escribe para afinar la búsqueda."
                    : productosFiltrados.size() + " producto(s)");
        marcarTarjeta(tablaProductosRef.getSelectionModel().getSelectedItem());
    }

    private javafx.scene.Node tarjetaProducto(Repuesto r) {
        FotoProducto foto = new FotoProducto(86);
        foto.mostrar(r.getFoto());
        Label nombre = new Label(r.getDescripcion());
        nombre.getStyleClass().add("producto-nombre");
        nombre.setWrapText(true);
        nombre.setMaxHeight(36);
        nombre.setMinHeight(36);
        Label codigo = new Label(r.getCodigo() + (r.getMarca() == null || r.getMarca().isBlank() ? "" : " · " + r.getMarca()));
        codigo.getStyleClass().add("producto-codigo");
        Label precio = new Label(Formato.moneda(precioPara(r)));
        precio.getStyleClass().add("producto-precio");
        Label stock = Componentes.chip(r.getStock() <= 0 ? "Agotado" : (r.isStockBajo() ? "Bajo: " : "Stock: ") + r.getStock(),
                r.getStock() <= 0 ? "alerta" : r.isStockBajo() ? "aviso" : "ok");
        javafx.scene.layout.Region esp = new javafx.scene.layout.Region();
        HBox.setHgrow(esp, Priority.ALWAYS);
        HBox pie = new HBox(6, precio, esp, stock);
        pie.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.layout.StackPane marcoFoto = new javafx.scene.layout.StackPane(foto);
        VBox card = new VBox(6, marcoFoto, nombre, codigo, pie);
        card.getStyleClass().add("producto-card");
        if (r.getStock() <= 0) card.getStyleClass().add("agotada");
        card.setPrefWidth(196);
        card.setMaxWidth(196);
        card.setUserData(r);
        javafx.scene.control.Tooltip.install(card, new javafx.scene.control.Tooltip(
                r.getDescripcion() + "\nUbicación: " + (r.getUbicacion() == null || r.getUbicacion().isBlank() ? "—" : r.getUbicacion())
                + "\nDoble clic para agregar al carrito"));
        card.setOnMouseClicked(e -> {
            tablaProductosRef.getSelectionModel().select(r);
            if (e.getClickCount() == 2) agregarAlCarrito(r);
        });
        return card;
    }

    private void marcarTarjeta(Repuesto seleccionado) {
        if (mosaico == null) return;
        for (javafx.scene.Node n : mosaico.getChildren()) {
            n.getStyleClass().remove("seleccionada");
            if (seleccionado != null && n.getUserData() == seleccionado) n.getStyleClass().add("seleccionada");
        }
    }

    private void aplicarFiltro() {
        BuscadorRepuestos.Consulta consulta = BuscadorRepuestos.Consulta.de(ultimoTexto);
        productosFiltrados.setPredicate(p -> consulta.coincide(p, compatPorProducto.get(p.getId())));
    }

    /** Vuelve a leer los vehículos compatibles (se llama cuando cambia el inventario). */
    public void recargarCompatibilidad() {
        compatPorProducto = compatibilidadService.porRepuesto();
        if (productosFiltrados != null) aplicarFiltro();
    }

    private void descuentoDeLinea() {
        ItemCarrito sel = tablaCarrito.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Alertas.advertencia("Sin selección", "Selecciona primero la línea del carrito a la que quieres dar el descuento.");
            return;
        }
        String texto = Alertas.pedirTexto("Descuento del producto",
                "Descuento (%) para " + sel.getRepuesto().getDescripcion() + ". Escribe 0 para quitarlo.",
                porcentaje(sel.getDescuentoPct()));
        if (texto == null) return;
        double pct;
        try {
            pct = Formato.leerMonto(texto);
        } catch (NumberFormatException ex) {
            Alertas.advertencia("Descuento inválido", "Escribe el porcentaje con números (ej: 10).");
            return;
        }
        if (pct < 0 || pct > 100) {
            Alertas.advertencia("Descuento inválido", "El descuento debe estar entre 0 y 100%.");
            return;
        }
        if (!esAdmin() && pct > config.descuentoMaximo() + 0.0001) {
            Alertas.advertencia("Descuento no permitido", "El descuento máximo permitido es " + porcentaje(config.descuentoMaximo()) + "%.");
            return;
        }
        sel.setDescuentoPct(pct);
        tablaCarrito.refresh();
        actualizarTotales();
    }

    private void descuentoGeneral() {
        if (carrito.isEmpty()) {
            Alertas.advertencia("Carrito vacío", "Agrega productos antes de aplicar un descuento general.");
            return;
        }
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Descuento general");
        dialogo.setHeaderText("Descuento sobre el subtotal de la venta. Escribe 0 para quitarlo.");
        ButtonType aplicar = new ButtonType("Aplicar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(aplicar, ButtonType.CANCEL);

        ComboBox<String> comboTipo = new ComboBox<>();
        comboTipo.getItems().addAll("Porcentaje (%)", "Monto");
        comboTipo.setValue(CalculoVenta.MONTO.equals(tipoDescuento) ? "Monto" : "Porcentaje (%)");
        TextField txtValor = new TextField(valorDescuento > 0 ? String.format("%.2f", valorDescuento) : "0");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));
        grid.add(new Label("Tipo:"), 0, 0);
        grid.add(comboTipo, 1, 0);
        grid.add(new Label("Valor:"), 0, 1);
        grid.add(txtValor, 1, 1);
        dialogo.getDialogPane().setContent(grid);

        Alertas.estilizar(dialogo);
        Button okBtn = (Button) dialogo.getDialogPane().lookupButton(aplicar);
        okBtn.getStyleClass().add("primary-button");

        final String[] tipoElegido = new String[1];
        final double[] valorElegido = new double[1];
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            double valor;
            try {
                valor = Formato.leerMonto(txtValor.getText());
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Valor inválido", "Escribe el descuento con números.");
                ev.consume();
                return;
            }
            String tipo = valor <= 0 ? null : ("Monto".equals(comboTipo.getValue()) ? CalculoVenta.MONTO : CalculoVenta.PORCENTAJE);
            try {
                CalculoVenta.Resultado prueba = CalculoVenta.calcular(carrito, tipo, Math.max(valor, 0),
                        config.impuestoPorcentaje(), config.impuestoIncluido());
                if (!esAdmin() && prueba.getDescuentoGeneralPct() > config.descuentoMaximo() + 0.0001) {
                    Alertas.advertencia("Descuento no permitido",
                            "El descuento máximo permitido es " + porcentaje(config.descuentoMaximo()) + "%.");
                    ev.consume();
                    return;
                }
            } catch (IllegalArgumentException ex) {
                Alertas.advertencia("Descuento inválido", ex.getMessage());
                ev.consume();
                return;
            }
            tipoElegido[0] = tipo;
            valorElegido[0] = valor <= 0 ? 0 : valor;
        });

        if (dialogo.showAndWait().map(b -> b == aplicar).orElse(false)) {
            tipoDescuento = tipoElegido[0];
            valorDescuento = valorElegido[0];
            actualizarTotales();
        }
    }

    private boolean esAdmin() {
        Usuario u = Sesion.getUsuario();
        return u != null && Rol.ADMIN.name().equals(u.getRol());
    }

    // ------------------------------------------------------------------
    // TOTALES Y PAGO
    // ------------------------------------------------------------------

    private CalculoVenta.Resultado calcular() {
        try {
            return CalculoVenta.calcular(carrito, tipoDescuento, valorDescuento,
                    config.impuestoPorcentaje(), config.impuestoIncluido());
        } catch (IllegalArgumentException e) {
            // Por ejemplo, el descuento general quedó mayor que el subtotal al quitar productos
            tipoDescuento = null;
            valorDescuento = 0;
            return CalculoVenta.calcular(carrito, null, 0, config.impuestoPorcentaje(), config.impuestoIncluido());
        }
    }

    /** Recalcula y muestra subtotal, descuento, impuesto y total (también se llama al cambiar la configuración). */
    public void actualizarTotales() {
        if (lblTotal == null) return;
        CalculoVenta.Resultado r = calcular();
        ultimoTotal = r.total;

        lblSubtotal.setText("Subtotal: " + Formato.moneda(r.subtotal));
        mostrar(lblDescuento, r.descuentoGeneral > 0);
        lblDescuento.setText("Descuento general: -" + Formato.moneda(r.descuentoGeneral));

        double pctImpuesto = config.impuestoPorcentaje();
        mostrar(lblImpuesto, pctImpuesto > 0);
        lblImpuesto.setText(config.nombreImpuesto() + " (" + porcentaje(pctImpuesto) + "%"
                + (config.impuestoIncluido() ? ", incluido" : "") + "): " + Formato.moneda(r.impuesto));

        lblTotal.setText(Formato.moneda(r.total));
        if (lblUnidades != null) {
            int unidades = 0;
            for (ItemCarrito i : carrito) unidades += i.getCantidad();
            lblUnidades.setText(carrito.size() + " producto(s) · " + unidades + " unidad(es)");
        }
        actualizarCambio();
    }

    private void alCambiarPago() {
        boolean efectivo = PAGO_EFECTIVO.equals(comboPago.getValue());
        boolean credito = "Crédito".equals(comboPago.getValue());
        mostrar(boxEfectivo, efectivo);
        mostrar(boxCredito, credito);
        if (credito) actualizarInfoCredito();
        actualizarCambio();
    }

    private void actualizarInfoCredito() {
        if (lblCredito == null) return;
        if (cliente == null) {
            lblCredito.setText("Para vender a crédito elige un cliente (botón \"Elegir cliente\").");
        } else {
            lblCredito.setText("Límite de " + cliente.getNombre() + ": " + Formato.moneda(cliente.getLimiteCredito())
                    + "  |  Debe: " + Formato.moneda(cliente.getSaldoPendiente())
                    + "  |  Disponible: " + Formato.moneda(cliente.getCreditoDisponible()));
        }
    }

    private void actualizarCambio() {
        if (lblCambio == null || !PAGO_EFECTIVO.equals(comboPago.getValue())) return;
        lblCambio.getStyleClass().removeAll("valor-ok", "valor-alerta");
        try {
            double recibido = Formato.leerMonto(txtRecibido.getText());
            double diferencia = Math.round((recibido - ultimoTotal) * 100.0) / 100.0;
            if (diferencia >= 0) {
                lblCambio.setText("Cambio: " + Formato.moneda(diferencia));
                lblCambio.getStyleClass().add("valor-ok");
            } else {
                lblCambio.setText("Faltan: " + Formato.moneda(-diferencia));
                lblCambio.getStyleClass().add("valor-alerta");
            }
        } catch (NumberFormatException e) {
            lblCambio.setText("Cambio: -");
        }
    }

    // ------------------------------------------------------------------
    // PROCESAR LA VENTA
    // ------------------------------------------------------------------

    private void procesar() {
        VentaService.Solicitud s = new VentaService.Solicitud();
        s.lineas = new ArrayList<>(carrito);
        s.cliente = cliente;
        s.metodoPago = comboPago.getValue();
        s.tipoDescuento = tipoDescuento;
        s.valorDescuento = valorDescuento;

        if (PAGO_EFECTIVO.equals(s.metodoPago)) {
            try {
                s.montoRecibido = Formato.leerMonto(txtRecibido.getText());
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Monto recibido", "Escribe el monto que entrega el cliente (o presiona \"Exacto\").");
                txtRecibido.requestFocus();
                return;
            }
        } else if ("Crédito".equals(s.metodoPago)) {
            s.fechaLimite = dpLimite.getValue();
        }

        try {
            VentaService.Resultado resultado = ventaService.procesar(s);
            if (cotizacionCargada != null) cotizacionService.marcarVendida(cotizacionCargada, resultado.factura.getId());
            limpiarVenta();
            alVender.run();
            DialogoFactura.mostrar(resultado.factura, new ArrayList<>());
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo procesar la venta", ex.getMessage());
            alVender.run(); // por si las existencias cambiaron
        }
        enfocarBusqueda();
    }

    /** Deja la pantalla lista para la siguiente venta. */
    private void limpiarVenta() {
        carrito.clear();
        cotizacionCargada = null;
        if (lblCotizacion != null) mostrar(lblCotizacion, false);
        tipoDescuento = null;
        valorDescuento = 0;
        cliente = null;
        actualizarEtiquetaCliente();
        comboPago.setValue(PAGO_EFECTIVO);
        txtRecibido.clear();
        dpLimite.setValue(LocalDate.now().plusDays(config.diasCredito()));
        alCambiarPago();
        actualizarTotales();
    }

    // ------------------------------------------------------------------
    // COTIZACIONES
    // ------------------------------------------------------------------

    /** Guarda el carrito actual como cotización (no descuenta inventario) y ofrece abrir el PDF. */
    private void guardarCotizacion() {
        if (carrito.isEmpty()) {
            Alertas.advertencia("Carrito vacío", "Agrega productos al carrito para cotizar.");
            return;
        }
        javafx.scene.control.Dialog<Boolean> d = new javafx.scene.control.Dialog<>();
        d.setTitle("Guardar cotización");
        d.setHeaderText("Total: " + Formato.moneda(ultimoTotal) + (cliente == null ? "" : "\nCliente: " + cliente.getNombre()));
        javafx.scene.control.ButtonType ok = new javafx.scene.control.ButtonType("Guardar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, javafx.scene.control.ButtonType.CANCEL);
        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre del interesado (si no es cliente registrado)");
        txtNombre.setDisable(cliente != null);
        TextField txtObs = new TextField();
        txtObs.setPromptText("Observaciones (vehículo, tiempo de entrega...)");
        VBox v = new VBox(10, txtNombre, txtObs);
        v.setPrefWidth(420);
        d.getDialogPane().setContent(v);
        Alertas.estilizar(d);
        ((Button) d.getDialogPane().lookupButton(ok)).getStyleClass().add("primary-button");
        d.setResultConverter(b -> b == ok);
        if (!d.showAndWait().orElse(false)) return;
        try {
            com.taller.repuestos.model.Cotizacion c = cotizacionService.crear(new ArrayList<>(carrito), cliente, txtNombre.getText(),
                    tipoDescuento, valorDescuento, txtObs.getText());
            limpiarVenta();
            if (Alertas.confirmar("Cotización guardada", "Se guardó la cotización " + Formato.numeroCotizacion(c.getId())
                    + " (válida hasta " + c.getFechaVencimiento() + ").\n¿Abrir el PDF para imprimir o enviar?")) {
                com.taller.repuestos.util.Archivos.abrir(com.taller.repuestos.util.DocumentoPDF.cotizacion(c));
            }
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo guardar la cotización", ex.getMessage());
        } catch (java.io.IOException ex) {
            Alertas.error("No se pudo generar el PDF", ex.getMessage());
        }
        enfocarBusqueda();
    }

    /**
     * Carga una cotización en el carrito para cobrarla. Si sigue vigente se respetan sus precios;
     * si venció se usan los precios actuales. Las existencias se validan igual que en cualquier venta.
     */
    public void cargarCotizacion(com.taller.repuestos.model.Cotizacion c) {
        if (!carrito.isEmpty() && !Alertas.confirmar("Reemplazar carrito",
                "El carrito tiene productos. ¿Reemplazarlos por la cotización " + Formato.numeroCotizacion(c.getId()) + "?")) {
            return;
        }
        limpiarVenta();
        if (c.getClienteId() != null) {
            Cliente cl = clienteService.obtener(c.getClienteId());
            if (cl != null && cl.isActivo()) cliente = cl;
        }
        boolean vigente = !c.isVencida();
        StringBuilder avisos = new StringBuilder();
        for (com.taller.repuestos.model.Cotizacion.Linea l : c.getLineas()) {
            Repuesto r = null;
            for (Repuesto p : productos) if (p.getId() == l.repuestoId) { r = p; break; }
            if (r == null) {
                avisos.append("\n• ").append(l.descripcion).append(": ya no existe en el inventario.");
                continue;
            }
            int cantidad = Math.min(l.cantidad, Math.max(0, r.getStock()));
            if (cantidad < l.cantidad) {
                avisos.append("\n• ").append(l.descripcion).append(": solo hay ").append(r.getStock()).append(" de ").append(l.cantidad).append(".");
            }
            if (cantidad <= 0) continue;
            ItemCarrito item = new ItemCarrito(r, cantidad);
            item.setPrecioUnitario(vigente ? l.precioUnitario : precioPara(r));
            item.setDescuentoPct(l.descuentoPct);
            carrito.add(item);
        }
        tipoDescuento = c.getTipoDescuento();
        valorDescuento = c.getDescuentoValor();
        cotizacionCargada = c.getId();
        lblCotizacion.setText("Cotización " + Formato.numeroCotizacion(c.getId()) + (vigente ? "" : " (vencida: precios actuales)"));
        mostrar(lblCotizacion, true);
        actualizarEtiquetaCliente();
        actualizarInfoCredito();
        tablaCarrito.refresh();
        actualizarTotales();
        if (avisos.length() > 0) {
            Alertas.advertencia("Revisa la cotización", "Algunos productos cambiaron desde que se cotizó:" + avisos);
        }
    }

    // ------------------------------------------------------------------

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private static boolean contiene(String texto, String q) {
        return texto != null && texto.toLowerCase().contains(q);
    }

    private static String porcentaje(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
