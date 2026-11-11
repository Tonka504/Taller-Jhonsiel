package com.taller.repuestos.ui;

import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.dao.CatalogoDAO;
import com.taller.repuestos.model.Compatibilidad;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.service.BuscadorRepuestos;
import com.taller.repuestos.service.CatalogoService;
import com.taller.repuestos.service.CompatibilidadService;
import com.taller.repuestos.service.InventarioService;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.util.Archivos;
import com.taller.repuestos.util.EtiquetaPDF;
import com.taller.repuestos.util.Formato;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Inventario: alta y edición de productos, compatibilidad con vehículos y etiquetas de código de barras. */
public class VistaInventario {

    private final ObservableList<Repuesto> productos;
    private final InventarioService inventarioService;
    private final CatalogoService catalogoService;
    private final CompatibilidadService compatibilidadService;
    private final Runnable alCambiar;
    private final PermisoService permisos = PermisoService.get();

    private final ObservableList<Compatibilidad> compatDelProducto = FXCollections.observableArrayList();
    private Map<Integer, List<Compatibilidad>> compatPorProducto = new HashMap<>();
    private Repuesto seleccionado;

    private TableView<Repuesto> tabla;
    private FilteredList<Repuesto> filtrados;
    private TextField txtBuscar;

    private TextField txtCodigo;
    private TextField txtBarras;
    private TextField txtDescripcion;
    private ComboBox<String> comboMarca;
    private ComboBox<String> comboCategoria;
    private TextField txtAplicacion;
    private TextField txtUbicacion;
    private ComboBox<String> comboUnidad;
    private TextField txtCosto;
    private TextField txtPrecio;
    private TextField txtMayorista;
    private TextField txtStock;
    private TextField txtStockMin;
    private TextField txtStockMax;
    private TextField txtProveedor;
    private Label lblSeleccion;
    private FotoProducto fotoForm;
    private String fotoActual;

    private ComboBox<String> comboMarcaVeh;
    private ComboBox<String> comboModeloVeh;
    private TextField txtDesde;
    private TextField txtHasta;
    private TextField txtMotor;
    private ComboBox<String> comboCombustible;
    private ComboBox<String> comboTransmision;
    private TextField txtObservaciones;
    private TableView<Compatibilidad> tablaCompat;

    /** @param alCambiar se ejecuta cuando algo cambia (para que el resto del sistema recargue los productos) */
    public VistaInventario(ObservableList<Repuesto> productos, InventarioService inventarioService,
                           CatalogoService catalogoService, CompatibilidadService compatibilidadService, Runnable alCambiar) {
        this.productos = productos;
        this.inventarioService = inventarioService;
        this.catalogoService = catalogoService;
        this.compatibilidadService = compatibilidadService;
        this.alCambiar = alCambiar;
    }

    // ------------------------------------------------------------------
    // CONSTRUCCIÓN
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public Node crear() {
        boolean puedeEditar = permisos.tiene(Permiso.INVENTARIO_EDITAR);
        compatPorProducto = compatibilidadService.porRepuesto();

        // ---------------- Tabla con búsqueda inteligente ----------------
        txtBuscar = new TextField();
        txtBuscar.setPromptText("🔍 Buscar por código, nombre, marca, categoría, ubicación o vehículo (ej: Toyota Hilux 2007 filtro)");
        HBox.setHgrow(txtBuscar, Priority.ALWAYS);
        filtrados = new FilteredList<>(productos, p -> true);
        txtBuscar.textProperty().addListener((obs, anterior, nuevo) -> aplicarFiltro());

        tabla = new TableView<>();
        tabla.getColumns().add(Componentes.colFoto(Repuesto::getFoto));
        tabla.getColumns().addAll(
                columnaTexto("Código", 95, r -> r.getCodigo()),
                columnaTexto("Descripción", 210, r -> r.getDescripcion()),
                columnaTexto("Marca", 90, r -> r.getMarca()),
                columnaTexto("Categoría", 110, r -> r.getCategoria()),
                columnaTexto("Ubicación", 90, r -> r.getUbicacion()),
                columnaTexto("Precio", 90, r -> Formato.moneda(r.getPrecio())));
        TableColumn<Repuesto, Integer> colStock = new TableColumn<>("Stock");
        colStock.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getStock()).asObject());
        colStock.setPrefWidth(60);
        TableColumn<Repuesto, Integer> colMin = new TableColumn<>("Mín.");
        colMin.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getStockMinimo()).asObject());
        colMin.setPrefWidth(55);
        tabla.getColumns().addAll(colStock, colMin);
        tabla.setItems(filtrados);
        tabla.setPlaceholder(new Label("No hay productos que coincidan"));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo != null) seleccionar(nuevo);
        });

        // ---------------- Compatibilidad ----------------
        VBox panelCompat = crearPanelCompatibilidad(puedeEditar);

        // Ajustes y kardex del producto seleccionado
        Button btnAjuste = Componentes.boton("Ajustar existencia", "secondary-button", com.taller.repuestos.util.Iconos.CAJA,
                "Entrada, salida o corrección de conteo con motivo", () -> {
                    Repuesto r = tabla.getSelectionModel().getSelectedItem();
                    if (r == null) {
                        Alertas.advertencia("Sin selección", "Selecciona un producto de la tabla.");
                        return;
                    }
                    if (DialogoAjusteInventario.mostrar(inventarioService, r)) {
                        alCambiar.run();
                        Alertas.info("Ajuste registrado", "La existencia se actualizó y quedó registrada en el kardex.");
                    }
                });
        btnAjuste.setVisible(permisos.tiene(Permiso.STOCK_AJUSTAR));
        btnAjuste.setManaged(btnAjuste.isVisible());
        Button btnKardex = Componentes.boton("Kardex", "secondary-button", com.taller.repuestos.util.Iconos.HISTORIAL,
                "Historial de movimientos del producto", () -> {
                    Repuesto r = tabla.getSelectionModel().getSelectedItem();
                    if (r == null) {
                        Alertas.advertencia("Sin selección", "Selecciona un producto de la tabla.");
                        return;
                    }
                    VistaKardex.mostrar(inventarioService, r);
                });
        btnAjuste.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        btnKardex.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        HBox barraBusqueda = new HBox(8, txtBuscar, btnAjuste, btnKardex);
        barraBusqueda.setAlignment(Pos.CENTER_LEFT);

        // Filas de stock bajo / agotado resaltadas
        tabla.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
            @Override
            protected void updateItem(Repuesto r, boolean vacio) {
                super.updateItem(r, vacio);
                getStyleClass().removeAll("fila-stock-bajo", "fila-agotado");
                if (!vacio && r != null) {
                    if (r.getStock() <= 0) getStyleClass().add("fila-agotado");
                    else if (r.isStockBajo()) getStyleClass().add("fila-stock-bajo");
                }
            }
        });

        VBox centro = new VBox(10, barraBusqueda, tabla);
        centro.setPadding(new Insets(0, 0, 0, puedeEditar ? 14 : 0));

        BorderPane raiz = new BorderPane();
        raiz.setPadding(new Insets(16));
        if (puedeEditar) {
            VBox formulario = crearFormulario();
            VBox izquierda = new VBox(14, formulario, panelCompat);
            izquierda.setPrefWidth(500);
            ScrollPane scroll = new ScrollPane(izquierda);
            scroll.setFitToWidth(true);
            scroll.setPrefWidth(530);
            scroll.getStyleClass().add("dashboard-scroll");
            raiz.setLeft(scroll);
            raiz.setCenter(centro);
        } else {
            centro.getChildren().add(panelCompat);
            raiz.setCenter(centro);
        }

        recargarCatalogos();
        return raiz;
    }

    private VBox crearFormulario() {
        txtCodigo = new TextField();
        txtBarras = new TextField();
        txtBarras.setPromptText("Si lo dejas vacío se usa el código");
        txtDescripcion = new TextField();
        comboMarca = comboEditable();
        comboCategoria = comboEditable();
        txtAplicacion = new TextField();
        txtAplicacion.setPromptText("Texto libre: pickups, sedán...");
        txtUbicacion = new TextField();
        txtUbicacion.setPromptText("Ej: Estante A-03");
        comboUnidad = new ComboBox<>();
        comboUnidad.getItems().addAll(InventarioService.UNIDADES);
        comboUnidad.setValue("Unidad");
        txtCosto = new TextField();
        txtPrecio = new TextField();
        txtMayorista = new TextField();
        txtMayorista.setPromptText("0 = sin precio mayorista");
        txtStock = new TextField();
        txtStockMin = new TextField();
        txtStockMin.setPromptText("5 si lo dejas vacío");
        txtStockMax = new TextField();
        txtStockMax.setPromptText("0 = sin máximo");
        txtProveedor = new TextField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        int f = 0;
        f = fila(grid, f, "Código (SKU):", txtCodigo);
        f = fila(grid, f, "Código de barras:", txtBarras);
        f = fila(grid, f, "Descripción:", txtDescripcion);
        f = fila(grid, f, "Marca:", comboMarca);
        f = fila(grid, f, "Categoría:", comboCategoria);
        f = fila(grid, f, "Aplicación:", txtAplicacion);
        f = fila(grid, f, "Ubicación:", txtUbicacion);
        f = fila(grid, f, "Unidad:", comboUnidad);
        f = fila(grid, f, "Costo:", txtCosto);
        f = fila(grid, f, "Precio de venta:", txtPrecio);
        f = fila(grid, f, "Precio mayorista:", txtMayorista);
        f = fila(grid, f, "Existencia:", txtStock);
        f = fila(grid, f, "Stock mínimo:", txtStockMin);
        f = fila(grid, f, "Stock máximo:", txtStockMax);
        f = fila(grid, f, "Proveedor:", txtProveedor);

        fotoForm = new FotoProducto(96);
        Button btnFoto = Componentes.boton("Elegir foto…", "secondary-button", null, this::elegirFoto);
        Button btnSinFoto = Componentes.boton("Quitar foto", "secondary-button", null, () -> {
            fotoActual = null;
            fotoForm.mostrar(null);
        });
        VBox botonesFoto = new VBox(6, btnFoto, btnSinFoto, Componentes.ayuda("PNG o JPG, hasta 5 MB.\nSe guarda al presionar Guardar o Actualizar."));
        HBox filaFoto = new HBox(12, fotoForm, botonesFoto);
        filaFoto.setAlignment(Pos.CENTER_LEFT);
        f = fila(grid, f, "Fotografía:", filaFoto);

        Button btnGuardar = new Button("Guardar nuevo");
        btnGuardar.getStyleClass().add("primary-button");
        btnGuardar.setOnAction(e -> guardarNuevo());
        Button btnActualizar = new Button("Actualizar");
        btnActualizar.getStyleClass().add("secondary-button");
        btnActualizar.setOnAction(e -> actualizar());
        Button btnEliminar = new Button("Eliminar");
        btnEliminar.getStyleClass().add("danger-button");
        btnEliminar.setOnAction(e -> eliminar());
        Button btnLimpiar = new Button("Limpiar");
        btnLimpiar.getStyleClass().add("secondary-button");
        btnLimpiar.setOnAction(e -> limpiar());
        Button btnEtiquetas = new Button("Etiquetas");
        btnEtiquetas.getStyleClass().add("secondary-button");
        btnEtiquetas.setOnAction(e -> imprimirEtiquetas());
        btnEliminar.setDisable(!permisos.tiene(Permiso.INVENTARIO_ELIMINAR));

        javafx.scene.layout.FlowPane botones = new javafx.scene.layout.FlowPane(8, 8, btnGuardar, btnActualizar, btnEliminar, btnLimpiar, btnEtiquetas);
        botones.setAlignment(Pos.CENTER_LEFT);

        Label titulo = new Label("Datos del producto");
        titulo.getStyleClass().add("section-title");
        lblSeleccion = new Label("Nuevo producto");
        lblSeleccion.getStyleClass().add("hint-label");
        VBox panel = new VBox(10, titulo, lblSeleccion, grid, botones);
        panel.getStyleClass().add("panel-card");
        return panel;
    }

    @SuppressWarnings("unchecked")
    private VBox crearPanelCompatibilidad(boolean puedeEditar) {
        Label titulo = new Label("Compatibilidad con vehículos");
        titulo.getStyleClass().add("section-title");
        Label ayuda = new Label("Selecciona un producto de la tabla para ver o registrar los vehículos con los que es compatible. "
                + "Un año o motor vacío significa \"cualquiera\".");
        ayuda.getStyleClass().add("hint-label");
        ayuda.setWrapText(true);

        tablaCompat = new TableView<>();
        TableColumn<Compatibilidad, String> cMarca = new TableColumn<>("Marca");
        cMarca.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMarcaVehiculo()));
        cMarca.setPrefWidth(85);
        TableColumn<Compatibilidad, String> cModelo = new TableColumn<>("Modelo");
        cModelo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getModelo()));
        cModelo.setPrefWidth(90);
        TableColumn<Compatibilidad, String> cAnios = new TableColumn<>("Años");
        cAnios.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAnios()));
        cAnios.setPrefWidth(85);
        TableColumn<Compatibilidad, String> cMotor = new TableColumn<>("Motor");
        cMotor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMotor()));
        cMotor.setPrefWidth(65);
        TableColumn<Compatibilidad, String> cComb = new TableColumn<>("Combustible");
        cComb.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCombustible()));
        cComb.setPrefWidth(85);
        TableColumn<Compatibilidad, String> cTrans = new TableColumn<>("Transm.");
        cTrans.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTransmision()));
        cTrans.setPrefWidth(75);
        TableColumn<Compatibilidad, String> cObs = new TableColumn<>("Observaciones");
        cObs.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getObservaciones()));
        cObs.setPrefWidth(120);
        tablaCompat.getColumns().addAll(cMarca, cModelo, cAnios, cMotor, cComb, cTrans, cObs);
        tablaCompat.setItems(compatDelProducto);
        tablaCompat.setPlaceholder(new Label("Este producto no tiene vehículos registrados"));
        tablaCompat.setPrefHeight(170);

        VBox panel = new VBox(10, titulo, ayuda, tablaCompat);
        panel.getStyleClass().add("panel-card");
        if (!puedeEditar) return panel;

        comboMarcaVeh = comboEditable();
        comboMarcaVeh.setPromptText("Marca");
        comboModeloVeh = comboEditable();
        comboModeloVeh.setPromptText("Modelo");
        comboMarcaVeh.setOnAction(e -> actualizarModelos());
        txtDesde = new TextField();
        txtDesde.setPromptText("Año desde");
        txtDesde.setPrefWidth(90);
        txtHasta = new TextField();
        txtHasta.setPromptText("Año hasta");
        txtHasta.setPrefWidth(90);
        txtMotor = new TextField();
        txtMotor.setPromptText("Motor (ej: 2.7L)");
        txtMotor.setPrefWidth(110);
        comboCombustible = new ComboBox<>();
        comboCombustible.getItems().add("");
        comboCombustible.getItems().addAll(CompatibilidadService.COMBUSTIBLES);
        comboCombustible.setPromptText("Combustible");
        comboTransmision = new ComboBox<>();
        comboTransmision.getItems().add("");
        comboTransmision.getItems().addAll(CompatibilidadService.TRANSMISIONES);
        comboTransmision.setPromptText("Transmisión");
        txtObservaciones = new TextField();
        txtObservaciones.setPromptText("Observaciones");
        HBox.setHgrow(txtObservaciones, Priority.ALWAYS);

        Button btnAgregar = new Button("Agregar vehículo");
        btnAgregar.getStyleClass().add("primary-button");
        btnAgregar.setOnAction(e -> agregarCompatibilidad());
        Button btnQuitar = new Button("Quitar seleccionado");
        btnQuitar.getStyleClass().add("danger-button");
        btnQuitar.setOnAction(e -> quitarCompatibilidad());

        HBox fila1 = new HBox(8, comboMarcaVeh, comboModeloVeh, txtDesde, txtHasta);
        HBox fila2 = new HBox(8, txtMotor, comboCombustible, comboTransmision, txtObservaciones);
        HBox fila3 = new HBox(8, btnAgregar, btnQuitar);
        panel.getChildren().addAll(fila1, fila2, fila3);
        return panel;
    }

    // ------------------------------------------------------------------
    // SELECCIÓN Y FORMULARIO
    // ------------------------------------------------------------------

    private void seleccionar(Repuesto r) {
        seleccionado = r;
        compatDelProducto.setAll(compatPorProducto.getOrDefault(r.getId(), new ArrayList<>()));
        if (txtCodigo == null) return; // vista de solo consulta

        txtCodigo.setText(nz(r.getCodigo()));
        txtBarras.setText(nz(r.getCodigoBarras()));
        txtDescripcion.setText(nz(r.getDescripcion()));
        fijarTexto(comboMarca, r.getMarca());
        fijarTexto(comboCategoria, r.getCategoria());
        txtAplicacion.setText(nz(r.getAplicacion()));
        txtUbicacion.setText(nz(r.getUbicacion()));
        comboUnidad.setValue(r.getUnidad() == null ? "Unidad" : r.getUnidad());
        txtCosto.setText(String.valueOf(r.getCosto()));
        txtPrecio.setText(String.valueOf(r.getPrecio()));
        txtMayorista.setText(String.valueOf(r.getPrecioMayorista()));
        txtStock.setText(String.valueOf(r.getStock()));
        txtStockMin.setText(String.valueOf(r.getStockMinimo()));
        txtStockMax.setText(String.valueOf(r.getStockMaximo()));
        txtProveedor.setText(nz(r.getProveedor()));
        fotoActual = r.getFoto();
        fotoForm.mostrar(fotoActual);
        lblSeleccion.setText("Editando: " + r.getCodigo() + " - " + r.getDescripcion());
    }

    private void limpiar() {
        seleccionado = null;
        compatDelProducto.clear();
        txtCodigo.clear();
        txtBarras.clear();
        txtDescripcion.clear();
        fijarTexto(comboMarca, "");
        fijarTexto(comboCategoria, "");
        txtAplicacion.clear();
        txtUbicacion.clear();
        comboUnidad.setValue("Unidad");
        txtCosto.clear();
        txtPrecio.clear();
        txtMayorista.clear();
        txtStock.clear();
        txtStockMin.clear();
        txtStockMax.clear();
        txtProveedor.clear();
        fotoActual = null;
        fotoForm.mostrar(null);
        lblSeleccion.setText("Nuevo producto");
        tabla.getSelectionModel().clearSelection();
    }

    /** Lee el formulario. Lanza NumberFormatException si un número está mal escrito. */
    private Repuesto leerFormulario(int id) {
        Repuesto r = new Repuesto(id, txtCodigo.getText(), txtDescripcion.getText(), textoDe(comboMarca),
                txtAplicacion.getText(), Formato.leerMonto(txtCosto.getText()), Formato.leerMonto(txtPrecio.getText()),
                entero(txtStock, 0), entero(txtStockMin, 5), txtProveedor.getText());
        r.setCategoria(textoDe(comboCategoria));
        r.setCodigoBarras(txtBarras.getText());
        r.setUbicacion(txtUbicacion.getText());
        r.setPrecioMayorista(txtMayorista.getText() == null || txtMayorista.getText().trim().isEmpty()
                ? 0 : Formato.leerMonto(txtMayorista.getText()));
        r.setStockMaximo(entero(txtStockMax, 0));
        r.setUnidad(comboUnidad.getValue());
        r.setFoto(fotoActual);
        return r;
    }

    /** Copia la imagen elegida a la carpeta de fotos; queda asignada al guardar el producto. */
    private void elegirFoto() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Fotografía del producto");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        java.io.File archivo = fc.showOpenDialog(tabla.getScene() == null ? null : tabla.getScene().getWindow());
        if (archivo == null) return;
        try {
            fotoActual = com.taller.repuestos.util.Fotos.guardar(archivo, txtCodigo.getText());
            fotoForm.mostrar(fotoActual);
        } catch (java.io.IOException ex) {
            Alertas.advertencia("No se pudo usar la imagen", ex.getMessage());
        }
    }

    private void guardarNuevo() {
        Repuesto r;
        try {
            r = leerFormulario(0);
        } catch (NumberFormatException ex) {
            Alertas.advertencia("Datos inválidos", "Verifica que costo y precios sean números, y que las existencias sean números enteros.");
            return;
        }
        try {
            inventarioService.crear(r);
            alCambiar.run();
            recargarCatalogos();
            limpiar();
            Alertas.info("Guardado", "Producto registrado con éxito.");
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo guardar", ex.getMessage());
        }
    }

    private void actualizar() {
        if (seleccionado == null) {
            Alertas.advertencia("Sin selección", "Selecciona un producto de la tabla para actualizarlo.");
            return;
        }
        Repuesto original = seleccionado;
        Repuesto nuevo;
        try {
            nuevo = leerFormulario(original.getId());
        } catch (NumberFormatException ex) {
            Alertas.advertencia("Datos inválidos", "Verifica que costo y precios sean números, y que las existencias sean números enteros.");
            return;
        }
        try {
            inventarioService.actualizar(original, nuevo);
            alCambiar.run();
            recargarCatalogos();
            limpiar();
            Alertas.info("Actualizado", "Producto actualizado correctamente.");
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo actualizar", ex.getMessage());
        }
    }

    private void eliminar() {
        if (seleccionado == null) {
            Alertas.advertencia("Sin selección", "Selecciona un producto de la tabla para eliminarlo.");
            return;
        }
        if (!Alertas.confirmar("Eliminar producto", "¿Eliminar \"" + seleccionado.getDescripcion()
                + "\" (" + seleccionado.getCodigo() + ")? Se borrarán también sus vehículos compatibles. Esta acción no se puede deshacer.")) {
            return;
        }
        try {
            inventarioService.eliminar(seleccionado);
            alCambiar.run();
            limpiar();
            Alertas.info("Eliminado", "Producto eliminado.");
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo eliminar", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // ETIQUETAS
    // ------------------------------------------------------------------

    private void imprimirEtiquetas() {
        if (seleccionado == null) {
            Alertas.advertencia("Sin selección", "Selecciona el producto del que quieres imprimir etiquetas.");
            return;
        }
        String texto = Alertas.pedirTexto("Etiquetas de código de barras",
                "¿Cuántas etiquetas de \"" + seleccionado.getDescripcion() + "\"?", "1");
        if (texto == null) return;
        int copias;
        try {
            copias = Integer.parseInt(texto.trim());
        } catch (NumberFormatException ex) {
            Alertas.advertencia("Cantidad inválida", "Escribe un número entero (ej: 12).");
            return;
        }
        if (copias < 1 || copias > 500) {
            Alertas.advertencia("Cantidad inválida", "Puedes imprimir entre 1 y 500 etiquetas por hoja.");
            return;
        }
        List<EtiquetaPDF.ProductoEtiqueta> lista = new ArrayList<>();
        for (int i = 0; i < copias; i++) {
            lista.add(new EtiquetaPDF.ProductoEtiqueta(seleccionado.getCodigoParaLector(), seleccionado.getDescripcion()));
        }
        try {
            File archivo = EtiquetaPDF.generarHojaEtiquetas(lista);
            Alertas.info("Etiquetas listas", "Se guardó la hoja de etiquetas en:\n" + archivo.getAbsolutePath()
                    + "\n\nEl código de barras es: " + seleccionado.getCodigoParaLector());
            Archivos.abrir(archivo);
        } catch (Exception ex) {
            ex.printStackTrace();
            Alertas.error("No se pudieron crear las etiquetas", "Detalle: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // COMPATIBILIDAD
    // ------------------------------------------------------------------

    private void agregarCompatibilidad() {
        if (seleccionado == null) {
            Alertas.advertencia("Sin selección", "Primero selecciona el producto en la tabla.");
            return;
        }
        Integer desde;
        Integer hasta;
        try {
            desde = anio(txtDesde);
            hasta = anio(txtHasta);
        } catch (NumberFormatException ex) {
            Alertas.advertencia("Año inválido", "Escribe los años con 4 números (ej: 2005) o déjalos vacíos.");
            return;
        }
        try {
            compatibilidadService.agregar(seleccionado, textoDe(comboMarcaVeh), textoDe(comboModeloVeh), desde, hasta,
                    txtMotor.getText(), comboCombustible.getValue(), comboTransmision.getValue(), txtObservaciones.getText());
            recargarCompatibilidad();
            txtDesde.clear();
            txtHasta.clear();
            txtMotor.clear();
            txtObservaciones.clear();
            alCambiar.run();
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo agregar el vehículo", ex.getMessage());
        }
    }

    private void quitarCompatibilidad() {
        Compatibilidad c = tablaCompat.getSelectionModel().getSelectedItem();
        if (seleccionado == null || c == null) {
            Alertas.advertencia("Sin selección", "Selecciona en la lista el vehículo que quieres quitar.");
            return;
        }
        try {
            compatibilidadService.eliminar(seleccionado, c);
            recargarCompatibilidad();
            alCambiar.run();
        } catch (ReglaNegocioException ex) {
            Alertas.advertencia("No se pudo quitar el vehículo", ex.getMessage());
        }
    }

    private void recargarCompatibilidad() {
        compatPorProducto = compatibilidadService.porRepuesto();
        if (seleccionado != null) {
            compatDelProducto.setAll(compatPorProducto.getOrDefault(seleccionado.getId(), new ArrayList<>()));
        }
        aplicarFiltro();
        actualizarModelos();
    }

    private void actualizarModelos() {
        if (comboModeloVeh == null) return;
        String actual = textoDe(comboModeloVeh);
        comboModeloVeh.getItems().setAll(CompatibilidadService.modelosDe(compatPorProducto, textoDe(comboMarcaVeh)));
        fijarTexto(comboModeloVeh, actual);
    }

    // ------------------------------------------------------------------
    // AUXILIARES
    // ------------------------------------------------------------------

    private void aplicarFiltro() {
        BuscadorRepuestos.Consulta consulta = BuscadorRepuestos.Consulta.de(txtBuscar.getText());
        filtrados.setPredicate(p -> consulta.coincide(p, compatPorProducto.get(p.getId())));
    }

    /** Vuelve a llenar los combos de marca y categoría con el catálogo (conservando lo que haya escrito). */
    private void recargarCatalogos() {
        if (comboMarca == null) return;
        List<String> marcas = catalogoService.nombres(CatalogoDAO.Tipo.MARCA);
        String m = textoDe(comboMarca);
        comboMarca.getItems().setAll(marcas);
        fijarTexto(comboMarca, m);
        String c = textoDe(comboCategoria);
        comboCategoria.getItems().setAll(catalogoService.nombres(CatalogoDAO.Tipo.CATEGORIA));
        fijarTexto(comboCategoria, c);
        if (comboMarcaVeh != null) {
            String mv = textoDe(comboMarcaVeh);
            comboMarcaVeh.getItems().setAll(marcas);
            fijarTexto(comboMarcaVeh, mv);
        }
    }

    private static ComboBox<String> comboEditable() {
        ComboBox<String> combo = new ComboBox<>();
        combo.setEditable(true);
        combo.setPrefWidth(220);
        return combo;
    }

    /** Lo que se ve escrito en un combo editable (el valor puede no estar al día hasta que se confirma). */
    private static String textoDe(ComboBox<String> combo) {
        if (combo == null) return "";
        String t = combo.getEditor().getText();
        return t == null ? "" : t.trim();
    }

    private static void fijarTexto(ComboBox<String> combo, String texto) {
        combo.setValue(texto == null || texto.isEmpty() ? null : texto);
        combo.getEditor().setText(texto == null ? "" : texto);
    }

    private static int fila(GridPane grid, int fila, String etiqueta, Node campo) {
        grid.add(new Label(etiqueta), 0, fila);
        grid.add(campo, 1, fila);
        GridPane.setHgrow(campo, Priority.ALWAYS);
        return fila + 1;
    }

    private interface Extractor {
        String de(Repuesto r);
    }

    private static TableColumn<Repuesto, String> columnaTexto(String titulo, double ancho, Extractor extractor) {
        TableColumn<Repuesto, String> col = new TableColumn<>(titulo);
        col.setCellValueFactory(c -> new SimpleStringProperty(extractor.de(c.getValue())));
        col.setPrefWidth(ancho);
        return col;
    }

    /** Número entero de un campo; si está vacío devuelve el valor por defecto. */
    private static int entero(TextField campo, int porDefecto) {
        String t = campo.getText() == null ? "" : campo.getText().trim();
        return t.isEmpty() ? porDefecto : Integer.parseInt(t);
    }

    /** Año de 4 cifras; vacío = null. */
    private static Integer anio(TextField campo) {
        String t = campo.getText() == null ? "" : campo.getText().trim();
        return t.isEmpty() ? null : Integer.valueOf(t);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
