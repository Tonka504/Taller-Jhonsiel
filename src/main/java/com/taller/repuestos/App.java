package com.taller.repuestos;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.dao.RepuestoDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Caja;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.model.Usuario;
import com.taller.repuestos.service.CajaService;
import com.taller.repuestos.service.CatalogoService;
import com.taller.repuestos.service.ClienteService;
import com.taller.repuestos.service.ConfiguracionService;
import com.taller.repuestos.service.CompatibilidadService;
import com.taller.repuestos.service.CreditoService;
import com.taller.repuestos.service.DevolucionService;
import com.taller.repuestos.service.InventarioService;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.service.RespaldoService;
import com.taller.repuestos.service.UsuarioService;
import com.taller.repuestos.service.VentaService;
import com.taller.repuestos.ui.Alertas;
import com.taller.repuestos.ui.VistaAuditoria;
import com.taller.repuestos.ui.VistaBuscador;
import com.taller.repuestos.ui.VistaCaja;
import com.taller.repuestos.ui.VistaCatalogo;
import com.taller.repuestos.ui.VistaClientes;
import com.taller.repuestos.ui.VistaConfiguracion;
import com.taller.repuestos.ui.VistaHistorial;
import com.taller.repuestos.ui.VistaInventario;
import com.taller.repuestos.ui.VistaPOS;
import com.taller.repuestos.ui.VistaUsuarios;
import com.taller.repuestos.service.BuscadorRepuestos;
import com.taller.repuestos.service.CompraService;
import com.taller.repuestos.service.DashboardService;
import com.taller.repuestos.service.OrdenTrabajoService;
import com.taller.repuestos.service.ProveedorService;
import com.taller.repuestos.service.ReporteService;
import com.taller.repuestos.service.VehiculoService;
import com.taller.repuestos.model.Vehiculo;
import com.taller.repuestos.ui.VistaCompras;
import com.taller.repuestos.ui.VistaDashboard;
import com.taller.repuestos.ui.VistaKardex;
import com.taller.repuestos.ui.VistaOrdenes;
import com.taller.repuestos.ui.VistaProveedores;
import com.taller.repuestos.ui.VistaReportes;
import com.taller.repuestos.ui.VistaVehiculos;
import com.taller.repuestos.util.Archivos;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Iconos;
import com.taller.repuestos.util.OrdenCompraPDF;
import com.taller.repuestos.util.Sesion;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App extends Application {

    private RepuestoDAO repuestoDAO;

    // Servicios: aquí viven las reglas, los permisos y la auditoría
    private UsuarioService usuarioService;
    private InventarioService inventarioService;
    private VentaService ventaService;
    private CajaService cajaService;
    private ClienteService clienteService;
    private CreditoService creditoService;
    private DevolucionService devolucionService;
    private CatalogoService catalogoService;
    private CompatibilidadService compatibilidadService;
    private ProveedorService proveedorService;
    private CompraService compraService;
    private VehiculoService vehiculoService;
    private OrdenTrabajoService ordenService;
    private ReporteService reporteService;
    private DashboardService dashboardService;
    private com.taller.repuestos.service.CotizacionService cotizacionService;
    private final PermisoService permisoService = PermisoService.get();
    private final ConfiguracionService config = ConfiguracionService.get();

    private ObservableList<Repuesto> listaRepuestos;
    private Usuario usuarioActual;

    // Navegación (sidebar + área de contenido)
    private StackPane contenedorVistas;
    private Label lblTituloSeccion;
    private Label lblSidebarTitulo;
    private Label lblEstadoCaja;
    private Stage ventanaPrincipal;
    private final ToggleGroup grupoMenu = new ToggleGroup();
    private final Map<String, ToggleButton> botonesMenu = new HashMap<>();
    private final Map<String, Node> cacheVistas = new HashMap<>();

    // Formulario de Inventario
    // Punto de venta (se crea una sola vez para conservar el carrito al cambiar de pantalla)
    private VistaPOS vistaPOS;
    // Última pantalla de órdenes mostrada (para abrir una orden nueva desde Vehículos)
    private VistaOrdenes vistaOrdenes;

    @Override
    public void start(Stage primaryStage) {
        Database.initDatabase();
        RespaldoService.respaldoAutomaticoSiCorresponde();

        repuestoDAO = new RepuestoDAO();
        usuarioService = new UsuarioService();
        catalogoService = new CatalogoService();
        compatibilidadService = new CompatibilidadService(catalogoService);
        inventarioService = new InventarioService(catalogoService);
        cajaService = new CajaService();
        ventaService = new VentaService(cajaService);
        clienteService = new ClienteService();
        creditoService = new CreditoService(cajaService);
        devolucionService = new DevolucionService(cajaService);
        proveedorService = new ProveedorService();
        compraService = new CompraService(proveedorService, cajaService);
        vehiculoService = new VehiculoService(catalogoService);
        ordenService = new OrdenTrabajoService(vehiculoService, clienteService, ventaService);
        dashboardService = new DashboardService();
        cotizacionService = new com.taller.repuestos.service.CotizacionService();

        Usuario usuarioLogueado = mostrarPantallaLogin();
        if (usuarioLogueado == null) {
            primaryStage.close();
            return;
        }

        this.usuarioActual = usuarioLogueado;
        this.ventanaPrincipal = primaryStage;
        Sesion.iniciar(usuarioLogueado);
        reporteService = new ReporteService();
        listaRepuestos = FXCollections.observableArrayList();

        cargarDatosIniciales();

        contenedorVistas = new StackPane();
        contenedorVistas.getStyleClass().add("content-area");

        BorderPane panelDerecho = new BorderPane();
        panelDerecho.setTop(crearBarraSuperior());
        panelDerecho.setCenter(contenedorVistas);

        BorderPane mainLayout = new BorderPane();
        mainLayout.setLeft(crearSidebar());
        mainLayout.setCenter(panelDerecho);

        // La capa de notificaciones (toast) va encima de todo el contenido
        Scene scene = new Scene(com.taller.repuestos.ui.Notificacion.instalar(mainLayout), 1280, 760);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        primaryStage.setTitle(tituloVentana());
        if (com.taller.repuestos.ui.Marca.icono() != null) primaryStage.getIcons().add(com.taller.repuestos.ui.Marca.icono());
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(640);
        primaryStage.setScene(scene);
        // Atajos: F1 Dashboard · F2 Punto de venta · F3 Buscar repuestos · F4 Órdenes de trabajo
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F1), () -> irA("dashboard"));
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F2), () -> irA("pos"));
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F3), () -> irA("buscador"));
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F4), () -> irA("ordenes"));
        irA("dashboard");
        primaryStage.show();
        Alertas.setPropietario(primaryStage);
    }

    /** ¿El usuario con la sesión iniciada tiene este permiso? */
    private boolean puede(Permiso permiso) {
        return permisoService.tiene(usuarioActual, permiso);
    }

    // ==========================================
    // NAVEGACIÓN: SIDEBAR + BARRA SUPERIOR
    // ==========================================
    private VBox crearSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(250);
        sidebar.setMinWidth(250);
        sidebar.setMaxWidth(250);

        // Marca: logotipo del negocio + logotipo tipográfico JOHNZIEL + nombre configurado del negocio
        Label negocio = new Label(config.nombreNegocio());
        negocio.getStyleClass().add("sidebar-subtitle");
        negocio.setMaxWidth(150);
        lblSidebarTitulo = negocio;
        Label lema = new Label("Performance & Parts");
        lema.getStyleClass().add("sidebar-subtitle");

        VBox textosMarca = new VBox(3, com.taller.repuestos.ui.Marca.logotipo("marca-texto", 118), lema, negocio);
        textosMarca.setAlignment(Pos.CENTER_LEFT);
        HBox cabecera = new HBox(12, com.taller.repuestos.ui.Marca.logo(62), textosMarca);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        cabecera.getStyleClass().add("sidebar-header");

        Region separador = new Region();
        separador.setMinHeight(10);

        sidebar.getChildren().addAll(cabecera, separador);

        // Las opciones se agrupan por área; cada grupo solo aparece si el usuario tiene algo en él
        VBox menu = new VBox(3);
        agregarBotonMenu(menu, "dashboard", "Dashboard", Iconos.PANEL);

        grupoMenu(menu, "VENTAS");
        if (puede(Permiso.POS_USAR)) agregarBotonMenu(menu, "pos", "Punto de Venta", Iconos.FACTURA);
        if (puede(Permiso.HISTORIAL_VER)) agregarBotonMenu(menu, "historial", "Historial de Facturas", Iconos.PORTAPAPELES);
        if (puede(Permiso.POS_USAR) || puede(Permiso.HISTORIAL_VER)) agregarBotonMenu(menu, "cotizaciones", "Cotizaciones", Iconos.FACTURA);
        if (puede(Permiso.CLIENTES_VER)) agregarBotonMenu(menu, "clientes", "Clientes", Iconos.CLIENTES);
        if (puede(Permiso.CAJA_VER)) agregarBotonMenu(menu, "caja", "Caja", Iconos.BILLETERA);

        grupoMenu(menu, "TALLER");
        if (puede(Permiso.ORDENES_VER)) agregarBotonMenu(menu, "ordenes", "Órdenes de trabajo", Iconos.DESTORNILLADOR);
        if (puede(Permiso.CLIENTES_VER) || puede(Permiso.VEHICULOS_EDITAR)) agregarBotonMenu(menu, "vehiculos", "Vehículos", Iconos.AUTO);

        grupoMenu(menu, "INVENTARIO");
        if (puede(Permiso.INVENTARIO_VER) || puede(Permiso.POS_USAR)) agregarBotonMenu(menu, "buscador", "Buscar repuestos", Iconos.BUSCAR);
        if (puede(Permiso.INVENTARIO_VER)) {
            agregarBotonMenu(menu, "inventario", puede(Permiso.INVENTARIO_EDITAR) ? "Inventario / Registro" : "Inventario", Iconos.TUERCA);
        }
        if (puede(Permiso.INVENTARIO_EDITAR)) agregarBotonMenu(menu, "catalogo", "Catálogo", Iconos.LLAVE);
        if (puede(Permiso.STOCK_AJUSTAR)) agregarBotonMenu(menu, "reabastecer", "Reabastecer Stock", Iconos.CAJA);
        if (puede(Permiso.INVENTARIO_VER)) agregarBotonMenu(menu, "kardex", "Movimientos (Kardex)", Iconos.MOVIMIENTOS);

        grupoMenu(menu, "COMPRAS");
        if (puede(Permiso.COMPRAS_VER)) agregarBotonMenu(menu, "compras", "Compras", Iconos.CARRITO);
        if (puede(Permiso.PROVEEDORES_VER)) agregarBotonMenu(menu, "proveedores", "Proveedores", Iconos.CAMION);

        grupoMenu(menu, "ADMINISTRACIÓN");
        if (!reporteService.disponibles().isEmpty()) agregarBotonMenu(menu, "reportes", "Reportes", Iconos.GRAFICA);
        if (puede(Permiso.USUARIOS_ADMIN)) agregarBotonMenu(menu, "usuarios", "Usuarios", Iconos.USUARIO);
        if (puede(Permiso.AUDITORIA_VER)) agregarBotonMenu(menu, "auditoria", "Auditoría", Iconos.HISTORIAL);
        if (puede(Permiso.CONFIGURACION_EDITAR)) agregarBotonMenu(menu, "configuracion", "Configuración", Iconos.ENGRANAJE);

        // Quita los títulos de grupo que quedaron sin opciones
        for (int i = menu.getChildren().size() - 1; i >= 0; i--) {
            Node n = menu.getChildren().get(i);
            boolean esTitulo = n.getStyleClass().contains("sidebar-group");
            boolean siguienteEsTitulo = i + 1 >= menu.getChildren().size()
                    || menu.getChildren().get(i + 1).getStyleClass().contains("sidebar-group");
            if (esTitulo && siguienteEsTitulo) menu.getChildren().remove(i);
        }

        ScrollPane scrollMenu = new ScrollPane(menu);
        scrollMenu.setFitToWidth(true);
        scrollMenu.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollMenu.getStyleClass().add("sidebar-scroll");
        VBox.setVgrow(scrollMenu, Priority.ALWAYS);
        sidebar.getChildren().add(scrollMenu);
        return sidebar;
    }

    private void grupoMenu(VBox menu, String titulo) {
        Label l = new Label(titulo);
        l.getStyleClass().add("sidebar-group");
        menu.getChildren().add(l);
    }

    private void agregarBotonMenu(VBox sidebar, String clave, String texto, String icono) {
        ToggleButton btn = new ToggleButton(texto, Iconos.crear(icono, 20));
        btn.getStyleClass().add("sidebar-button");
        btn.setToggleGroup(grupoMenu);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setGraphicTextGap(14);
        btn.setOnAction(e -> irA(clave));
        botonesMenu.put(clave, btn);
        sidebar.getChildren().add(btn);
    }

    private HBox crearBarraSuperior() {
        HBox topBar = new HBox(15);
        topBar.getStyleClass().add("topbar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        lblTituloSeccion = new Label();
        lblTituloSeccion.getStyleClass().add("page-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblUsuario = new Label(usuarioActual.getNombreCompleto() + "  ·  " + usuarioActual.getRol(),
                Iconos.crear(Iconos.USUARIO, 18));
        lblUsuario.getStyleClass().add("user-chip");

        // Indicador de caja (solo para quien vende o ve la caja)
        lblEstadoCaja = new Label();
        lblEstadoCaja.getStyleClass().add("caja-chip");
        boolean muestraCaja = puede(Permiso.POS_USAR) || puede(Permiso.CAJA_VER);
        lblEstadoCaja.setVisible(muestraCaja);
        lblEstadoCaja.setManaged(muestraCaja);
        actualizarEstadoCaja();

        topBar.getChildren().addAll(lblTituloSeccion, spacer, lblEstadoCaja, lblUsuario);
        return topBar;
    }

    /** Actualiza el indicador "Caja abierta / cerrada" de la barra superior. */
    private void actualizarEstadoCaja() {
        if (lblEstadoCaja == null) return;
        Caja abierta = cajaService.cajaAbierta();
        lblEstadoCaja.getStyleClass().removeAll("caja-chip-abierta", "caja-chip-cerrada");
        if (abierta == null) {
            lblEstadoCaja.setText("Caja cerrada");
            lblEstadoCaja.getStyleClass().add("caja-chip-cerrada");
        } else {
            String f = abierta.getFechaApertura();
            String hora = f != null && f.length() >= 16 ? f.substring(11, 16) : "";
            lblEstadoCaja.setText("Caja abierta desde las " + hora);
            lblEstadoCaja.getStyleClass().add("caja-chip-abierta");
        }
    }

    /** Se ejecuta al guardar la configuración: refresca lo que se ve en pantalla. */
    private String tituloVentana() {
        return com.taller.repuestos.ui.Marca.NOMBRE + "  ·  " + config.nombreNegocio();
    }

    private void alCambiarConfiguracion() {
        if (lblSidebarTitulo != null) lblSidebarTitulo.setText(config.nombreNegocio());
        if (ventanaPrincipal != null) ventanaPrincipal.setTitle(tituloVentana());
        if (vistaPOS != null) vistaPOS.actualizarTotales();
    }

    /** Selecciona la opción del menú y muestra su vista con un fade suave. */
    private void irA(String clave) {
        ToggleButton btn = botonesMenu.get(clave);
        if (btn == null) return;
        btn.setSelected(true);
        lblTituloSeccion.setText(btn.getText());
        Node vista;
        try {
            vista = obtenerVista(clave);
        } catch (Throwable ex) {
            // Nunca dejar la pantalla sin respuesta: se avisa con el detalle para poder corregirlo
            ex.printStackTrace();
            cacheVistas.remove(clave);
            if ("pos".equals(clave)) vistaPOS = null;
            Alertas.error("No se pudo abrir \"" + btn.getText() + "\"",
                    "Ocurrió un error al cargar esta pantalla:\n" + ex
                    + (ex instanceof LinkageError
                        ? "\n\nParece que el programa quedó compilado a medias. Ciérralo, recompila el proyecto (mvn clean compile) y vuelve a abrirlo."
                        : ""));
            return;
        }
        mostrarVista(vista);
        if ("pos".equals(clave) && vistaPOS != null) {
            vistaPOS.enfocarBusqueda(); // listo para escanear
        }
    }

    private Node obtenerVista(String clave) {
        // Estas vistas se vuelven a crear cada vez para mostrar datos al día
        switch (clave) {
            case "dashboard": return new VistaDashboard(dashboardService, this::irA, botonesMenu::containsKey).crear();
            case "kardex": return new VistaKardex(inventarioService, null).crear();
            case "proveedores": return new VistaProveedores(proveedorService, compraService).crear();
            case "compras": return new VistaCompras(compraService, proveedorService, listaRepuestos,
                    compatibilidadService::porRepuesto, this::cargarDatosIniciales).crear();
            case "vehiculos": return new VistaVehiculos(vehiculoService, clienteService, catalogoService, ordenService,
                    this::abrirOrdenParaVehiculo).crear();
            case "ordenes": {
                vistaOrdenes = new VistaOrdenes(ordenService, vehiculoService, clienteService, catalogoService, listaRepuestos,
                        compatibilidadService::porRepuesto, this::cargarDatosIniciales);
                return vistaOrdenes.crear();
            }
            case "cotizaciones": return new com.taller.repuestos.ui.VistaCotizaciones(cotizacionService, this::cobrarCotizacion).crear();
            case "reportes": return new VistaReportes(reporteService, catalogoService, clienteService, proveedorService).crear();
            case "historial": return new VistaHistorial(ventaService, devolucionService, this::cargarDatosIniciales).crear();
            case "clientes": return new VistaClientes(clienteService, ventaService, creditoService, devolucionService).crear();
            case "buscador": return new VistaBuscador(listaRepuestos, compatibilidadService).crear();
            case "catalogo": return new VistaCatalogo(catalogoService).crear();
            case "inventario": return new VistaInventario(listaRepuestos, inventarioService, catalogoService, compatibilidadService, this::cargarDatosIniciales).crear();
            case "usuarios": return new VistaUsuarios(usuarioService).crear();
            case "caja": return new VistaCaja(cajaService, this::actualizarEstadoCaja).crear();
            case "auditoria": return new VistaAuditoria().crear();
            case "configuracion": return new VistaConfiguracion(this::alCambiarConfiguracion).crear();
            default: break;
        }

        return cacheVistas.computeIfAbsent(clave, k -> switch (k) {
            case "pos" -> {
                vistaPOS = new VistaPOS(listaRepuestos, ventaService, clienteService, compatibilidadService, cotizacionService, this::cargarDatosIniciales);
                yield vistaPOS.crear();
            }
            case "reabastecer" -> crearVistaReabastecimiento();
            default -> new Label("Vista no disponible");
        });
    }

    /** Desde Cotizaciones: abre el punto de venta con la cotización en el carrito. */
    private void cobrarCotizacion(com.taller.repuestos.model.Cotizacion c) {
        if (!botonesMenu.containsKey("pos")) return;
        irA("pos");
        if (vistaPOS != null) vistaPOS.cargarCotizacion(c);
    }

    /** Desde Vehículos: abre el taller con el formulario de nueva orden para ese vehículo. */
    private void abrirOrdenParaVehiculo(Vehiculo v) {
        irA("ordenes");
        if (vistaOrdenes != null) Platform.runLater(() -> vistaOrdenes.nuevaOrden(v));
    }

    private void mostrarVista(Node vista) {
        contenedorVistas.getChildren().setAll(vista);
        FadeTransition fade = new FadeTransition(Duration.millis(240), vista);
        fade.setFromValue(0);
        fade.setToValue(1);
        javafx.animation.TranslateTransition desliza = new javafx.animation.TranslateTransition(Duration.millis(240), vista);
        desliza.setFromY(10);
        desliza.setToY(0);
        desliza.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        new javafx.animation.ParallelTransition(fade, desliza).play();
    }

    private Usuario mostrarPantallaLogin() {
        Dialog<Usuario> dialog = new Dialog<>();
        dialog.setTitle(com.taller.repuestos.ui.Marca.NOMBRE + " · Acceso");
        dialog.setHeaderText(null);
        dialog.setGraphic(null);

        ButtonType loginButtonType = new ButtonType("Ingresar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        TextField username = new TextField();
        username.setPromptText("Tu usuario");
        PasswordField password = new PasswordField();
        password.setPromptText("Tu contraseña");

        // Diseño: fotografía deportiva de fondo (CSS .login-pane), velo oscuro y formulario a la derecha
        Label lblUsuario = new Label("USUARIO");
        lblUsuario.getStyleClass().add("form-label");
        Label lblClave = new Label("CONTRASEÑA");
        lblClave.getStyleClass().add("form-label");
        Label lema = new Label(com.taller.repuestos.ui.Marca.LEMA);
        lema.getStyleClass().add("login-subtitulo");
        lema.setWrapText(true);
        Label negocio = new Label(config.nombreNegocio());
        negocio.getStyleClass().add("login-subtitulo");
        Label pie = new Label("Ingresa con tus credenciales. Tras 5 intentos fallidos el sistema se cierra.");
        pie.getStyleClass().add("hint-label");
        pie.setWrapText(true);

        Region separadorLogin = new Region();
        separadorLogin.setMinHeight(12);
        VBox form = new VBox(10,
                com.taller.repuestos.ui.Marca.logo(132),
                com.taller.repuestos.ui.Marca.logotipo("login-titulo", 160), lema, negocio,
                separadorLogin,
                lblUsuario, username, lblClave, password, pie);
        form.getStyleClass().add("login-form");
        form.setAlignment(Pos.CENTER_LEFT);
        form.setPrefWidth(400);
        form.setMaxWidth(400);
        form.setFillWidth(true);

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox fila = new HBox(espacio, form);
        Region velo = new Region();
        velo.getStyleClass().add("login-velo");
        StackPane contenidoLogin = new StackPane(velo, fila);
        contenidoLogin.setPrefSize(980, 560);

        dialog.getDialogPane().getStyleClass().add("login-pane");
        dialog.getDialogPane().setContent(contenidoLogin);
        dialog.setResizable(true);
        Alertas.estilizar(dialog);

        final Usuario[] autenticado = new Usuario[1];
        final int[] intentosFallidos = {0};

        // La validación va en el botón: si las credenciales fallan, el diálogo NO se cierra y se puede reintentar
        Button btnIngresar = (Button) dialog.getDialogPane().lookupButton(loginButtonType);
        btnIngresar.getStyleClass().add("primary-button");
        btnIngresar.addEventFilter(ActionEvent.ACTION, ev -> {
            Usuario user = usuarioService.autenticar(username.getText().trim(), password.getText());
            if (user != null) {
                autenticado[0] = user;
                return;
            }
            ev.consume();
            intentosFallidos[0]++;
            password.clear();
            if (intentosFallidos[0] >= 5) {
                Alertas.advertencia("Acceso bloqueado", "Demasiados intentos fallidos. El sistema se cerrará.");
                dialog.close();
                return;
            }
            Alertas.advertencia("Credenciales inválidas", "Usuario o contraseña incorrectos, o el usuario está desactivado.");
            password.requestFocus();
        });

        dialog.setResultConverter(boton -> boton == loginButtonType ? autenticado[0] : null);
        Platform.runLater(username::requestFocus);

        return dialog.showAndWait().orElse(null);
    }

    private void cargarDatosIniciales() {
        if (listaRepuestos != null && repuestoDAO != null) {
            listaRepuestos.clear();
            listaRepuestos.addAll(repuestoDAO.obtenerTodos());
        }
        if (vistaPOS != null) vistaPOS.recargarCompatibilidad();
    }

    // ==========================================
    // REABASTECER STOCK (ADMIN)
    // ==========================================
    @SuppressWarnings("unchecked")
    private BorderPane crearVistaReabastecimiento() {
        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(15));

        VBox leftBox = new VBox(10);
        leftBox.setPrefWidth(550);

        TextField txtBuscarReabastecer = new TextField();
        txtBuscarReabastecer.setPromptText("🔍 Buscar por código, nombre, marca, categoría o ubicación...");

        TableView<Repuesto> tablaReabastecer = new TableView<>();

        TableColumn<Repuesto, String> colCod = new TableColumn<>("Código");
        colCod.setCellValueFactory(new PropertyValueFactory<>("codigo"));

        TableColumn<Repuesto, String> colDesc = new TableColumn<>("Producto / Descripción");
        colDesc.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        colDesc.setPrefWidth(180);

        TableColumn<Repuesto, String> colMarca = new TableColumn<>("Marca");
        colMarca.setCellValueFactory(new PropertyValueFactory<>("marca"));

        TableColumn<Repuesto, Integer> colStock = new TableColumn<>("Stock Actual");
        colStock.setCellValueFactory(new PropertyValueFactory<>("stock"));

        tablaReabastecer.getColumns().addAll(colCod, colDesc, colMarca, colStock);

        FilteredList<Repuesto> listaFiltradaReabastecer = new FilteredList<>(listaRepuestos, p -> true);

        txtBuscarReabastecer.textProperty().addListener((obs, oldVal, newVal) -> {
            BuscadorRepuestos.Consulta consulta = BuscadorRepuestos.Consulta.de(newVal);
            listaFiltradaReabastecer.setPredicate(p -> consulta.coincide(p, null));
        });

        tablaReabastecer.setItems(listaFiltradaReabastecer);
        leftBox.getChildren().addAll(new Label("1. Selecciona el producto a reabastecer:"), txtBuscarReabastecer, tablaReabastecer);
        layout.setLeft(leftBox);

        VBox rightBox = new VBox(15);
        rightBox.setPadding(new Insets(0, 0, 0, 15));

        Label lblNombre = new Label("Producto: (Ninguno seleccionado)");
        lblNombre.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label lblInfoActual = new Label("Stock Actual: - | Precio: -");

        Spinner<Integer> spCantidadIngreso = new Spinner<>(1, 10000, 1);
        spCantidadIngreso.setEditable(true);

        Button btnConfirmarReabastecimiento = new Button("📦 Confirmar e Incrementar Stock");
        btnConfirmarReabastecimiento.getStyleClass().add("primary-button");

        tablaReabastecer.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, seleccionado) -> {
            if (seleccionado != null) {
                lblNombre.setText("Producto: " + seleccionado.getDescripcion());
                lblInfoActual.setText("Stock Actual: " + seleccionado.getStock() + " unidades | Precio: " + Formato.moneda(seleccionado.getPrecio()));
            }
        });

        btnConfirmarReabastecimiento.setOnAction(e -> {
            Repuesto seleccionado = tablaReabastecer.getSelectionModel().getSelectedItem();
            if (seleccionado == null) {
                mostrarAlerta("Sin Selección", "Selecciona un producto para reabastecer.");
                return;
            }

            int cantidadAgregar;
            try {
                // Se lee lo que está escrito (el valor del spinner no se actualiza hasta confirmar con Enter)
                cantidadAgregar = Integer.parseInt(spCantidadIngreso.getEditor().getText().trim());
            } catch (NumberFormatException ex) {
                mostrarAlerta("Cantidad inválida", "Escribe la cantidad como un número entero.");
                return;
            }
            if (seleccionado.getStockMaximo() > 0 && seleccionado.getStock() + cantidadAgregar > seleccionado.getStockMaximo()
                    && !Alertas.confirmar("Supera el stock máximo", "Con esta entrada habrá " + (seleccionado.getStock() + cantidadAgregar)
                    + " unidades y el máximo definido es " + seleccionado.getStockMaximo() + ". ¿Continuar?")) {
                return;
            }
            try {
                inventarioService.reabastecer(seleccionado, cantidadAgregar);
                seleccionado.setStock(seleccionado.getStock() + cantidadAgregar);
                tablaReabastecer.refresh();
                mostrarInfoExito("Stock Actualizado", "Se agregaron " + cantidadAgregar + " unidades a: " + seleccionado.getDescripcion());
                cargarDatosIniciales();
            } catch (ReglaNegocioException ex) {
                mostrarAlerta("No se pudo reabastecer", ex.getMessage());
            }
        });

        Button btnOrdenCompra = new Button("📄 Orden de compra (productos con stock bajo)");
        btnOrdenCompra.getStyleClass().add("secondary-button");
        btnOrdenCompra.setOnAction(e -> {
            List<Repuesto> bajos = repuestoDAO.obtenerStockBajo();
            if (bajos.isEmpty()) {
                mostrarInfoExito("Todo en orden", "No hay productos con stock bajo: no hace falta ninguna orden de compra.");
                return;
            }
            try {
                java.io.File archivo = OrdenCompraPDF.generar(bajos);
                mostrarInfoExito("Orden de compra lista", "Se creó la orden de compra de " + bajos.size()
                        + " producto(s), agrupados por proveedor:\n" + archivo.getAbsolutePath());
                Archivos.abrir(archivo);
            } catch (Exception ex) {
                ex.printStackTrace();
                mostrarAlerta("No se pudo crear la orden de compra", "Detalle: " + ex.getMessage());
            }
        });

        rightBox.getChildren().addAll(
            new Label("2. Ingreso de Mercadería:"),
            lblNombre,
            lblInfoActual,
            new HBox(10, new Label("Cantidad a Agregar:"), spCantidadIngreso),
            btnConfirmarReabastecimiento,
            btnOrdenCompra
        );

        layout.setCenter(rightBox);
        return layout;
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alertas.advertencia(titulo, mensaje);
    }

    private void mostrarInfoExito(String titulo, String mensaje) {
        Alertas.info(titulo, mensaje);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
