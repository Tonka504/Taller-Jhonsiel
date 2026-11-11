package com.taller.repuestos.ui;

import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.service.ConfiguracionService;
import com.taller.repuestos.service.RespaldoService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Configuración del negocio, de la caja y de las copias de seguridad. */
public class VistaConfiguracion {

    private final ConfiguracionService config = ConfiguracionService.get();
    private final Runnable alGuardar;

    /** @param alGuardar se ejecuta después de guardar (para refrescar nombre del negocio, etc.) */
    public VistaConfiguracion(Runnable alGuardar) {
        this.alGuardar = alGuardar;
    }

    public Node crear() {
        Label titulo = new Label("Configuración");
        titulo.getStyleClass().add("dashboard-title");

        // ---- Datos del negocio ----
        TextField txtNombre = campo(config.get(ConfiguracionService.NEGOCIO_NOMBRE));
        TextField txtDireccion = campo(config.get(ConfiguracionService.NEGOCIO_DIRECCION));
        TextField txtTelefono = campo(config.get(ConfiguracionService.NEGOCIO_TELEFONO));
        TextField txtCorreo = campo(config.get(ConfiguracionService.NEGOCIO_CORREO));
        TextField txtFiscales = campo(config.get(ConfiguracionService.NEGOCIO_DATOS_FISCALES));
        txtFiscales.setPromptText("RTN u otros datos fiscales que quieras mostrar en tus documentos");
        TextField txtMoneda = campo(config.get(ConfiguracionService.MONEDA_SIMBOLO));
        txtMoneda.setPromptText("Ej: L. o $");
        txtMoneda.setPrefWidth(90);
        txtMoneda.setMaxWidth(90);

        GridPane datos = new GridPane();
        datos.setHgap(12);
        datos.setVgap(10);
        datos.add(new Label("Nombre del negocio:"), 0, 0);
        datos.add(txtNombre, 1, 0);
        datos.add(new Label("Dirección:"), 0, 1);
        datos.add(txtDireccion, 1, 1);
        datos.add(new Label("Teléfono:"), 0, 2);
        datos.add(txtTelefono, 1, 2);
        datos.add(new Label("Correo:"), 0, 3);
        datos.add(txtCorreo, 1, 3);
        datos.add(new Label("Datos fiscales:"), 0, 4);
        datos.add(txtFiscales, 1, 4);
        datos.add(new Label("Símbolo de moneda:"), 0, 5);
        datos.add(txtMoneda, 1, 5);
        GridPane.setHgrow(txtNombre, Priority.ALWAYS);

        Label tituloNegocio = new Label("Datos del negocio");
        tituloNegocio.getStyleClass().add("section-title");
        VBox panelNegocio = new VBox(12, tituloNegocio, datos);
        panelNegocio.getStyleClass().add("panel-card");

        // ---- Caja ----
        CheckBox chkCaja = new CheckBox("Exigir caja abierta para poder vender");
        chkCaja.setSelected(config.getBoolean(ConfiguracionService.CAJA_EXIGIR_ABIERTA));
        Label notaCaja = new Label("Recomendado: así todas las ventas quedan dentro de un turno de caja y el cierre siempre cuadra.");
        notaCaja.getStyleClass().add("hint-label");
        notaCaja.setWrapText(true);
        Label tituloCaja = new Label("Caja");
        tituloCaja.getStyleClass().add("section-title");
        VBox panelCaja = new VBox(10, tituloCaja, chkCaja, notaCaja);
        panelCaja.getStyleClass().add("panel-card");

        // ---- Ventas e impuestos ----
        TextField txtImpNombre = campo(config.get(ConfiguracionService.IMPUESTO_NOMBRE));
        txtImpNombre.setPrefWidth(160);
        TextField txtImpPct = campo(config.get(ConfiguracionService.IMPUESTO_PORCENTAJE));
        txtImpPct.setPrefWidth(90);
        CheckBox chkImpIncluido = new CheckBox("Los precios ya incluyen el impuesto (solo se muestra desglosado)");
        chkImpIncluido.setSelected(config.getBoolean(ConfiguracionService.IMPUESTO_INCLUIDO));
        Label notaImp = new Label("Déjalo en 0 si no cobras impuesto. El sistema no asume ningún dato fiscal: configúralo según lo que corresponda a tu negocio.");
        notaImp.getStyleClass().add("hint-label");
        notaImp.setWrapText(true);
        TextField txtDescMax = campo(config.get(ConfiguracionService.DESCUENTO_MAXIMO));
        txtDescMax.setPrefWidth(90);
        TextField txtDiasCredito = campo(config.get(ConfiguracionService.CREDITO_DIAS));
        txtDiasCredito.setPrefWidth(90);

        GridPane ventas = new GridPane();
        ventas.setHgap(12);
        ventas.setVgap(10);
        ventas.add(new Label("Nombre del impuesto:"), 0, 0);
        ventas.add(txtImpNombre, 1, 0);
        ventas.add(new Label("Porcentaje (%):"), 0, 1);
        ventas.add(txtImpPct, 1, 1);
        ventas.add(new Label("Descuento máximo (%):"), 0, 2);
        ventas.add(txtDescMax, 1, 2);
        ventas.add(new Label("Plazo del crédito (días):"), 0, 3);
        ventas.add(txtDiasCredito, 1, 3);
        Label notaDesc = new Label("El descuento máximo aplica a todos los usuarios con permiso de descuentos, excepto al administrador.");
        notaDesc.getStyleClass().add("hint-label");
        notaDesc.setWrapText(true);
        Label tituloVentas = new Label("Ventas e impuestos");
        tituloVentas.getStyleClass().add("section-title");
        VBox panelVentas = new VBox(10, tituloVentas, ventas, chkImpIncluido, notaImp, notaDesc);
        panelVentas.getStyleClass().add("panel-card");

        // ---- Numeración de documentos ----
        TextField txtPrefFactura = campo(config.get(ConfiguracionService.DOC_PREFIJO_FACTURA));
        txtPrefFactura.setPrefWidth(90);
        TextField txtPrefDevolucion = campo(config.get(ConfiguracionService.DOC_PREFIJO_DEVOLUCION));
        txtPrefDevolucion.setPrefWidth(90);
        TextField txtDigitos = campo(config.get(ConfiguracionService.DOC_DIGITOS));
        txtDigitos.setPrefWidth(90);
        TextField txtPrefCompra = campo(config.get(ConfiguracionService.DOC_PREFIJO_COMPRA));
        txtPrefCompra.setPrefWidth(90);
        TextField txtPrefOrden = campo(config.get(ConfiguracionService.DOC_PREFIJO_ORDEN));
        txtPrefOrden.setPrefWidth(90);
        TextField txtPrefCot = campo(config.get(ConfiguracionService.DOC_PREFIJO_COTIZACION));
        txtPrefCot.setPrefWidth(90);
        TextField txtDiasCot = campo(config.get(ConfiguracionService.COTIZACION_DIAS));
        txtDiasCot.setPrefWidth(90);
        GridPane docs = new GridPane();
        docs.setHgap(12);
        docs.setVgap(10);
        docs.add(new Label("Prefijo de facturas:"), 0, 0);
        docs.add(txtPrefFactura, 1, 0);
        docs.add(new Label("Prefijo de devoluciones:"), 0, 1);
        docs.add(txtPrefDevolucion, 1, 1);
        docs.add(new Label("Dígitos del número:"), 0, 2);
        docs.add(txtDigitos, 1, 2);
        docs.add(new Label("Prefijo de compras:"), 0, 3);
        docs.add(txtPrefCompra, 1, 3);
        docs.add(new Label("Prefijo de órdenes de trabajo:"), 0, 4);
        docs.add(txtPrefOrden, 1, 4);
        docs.add(new Label("Prefijo de cotizaciones:"), 0, 5);
        docs.add(txtPrefCot, 1, 5);
        docs.add(new Label("Validez de cotizaciones (días):"), 0, 6);
        docs.add(txtDiasCot, 1, 6);
        Label notaDocs = new Label("Solo cambia cómo se muestran los números (ej: FAC-000012). Las facturas anteriores se muestran con el formato nuevo.");
        notaDocs.getStyleClass().add("hint-label");
        notaDocs.setWrapText(true);
        Label tituloDocs = new Label("Numeración de documentos");
        tituloDocs.getStyleClass().add("section-title");
        VBox panelDocs = new VBox(10, tituloDocs, docs, notaDocs);
        panelDocs.getStyleClass().add("panel-card");

        // ---- Respaldos ----
        CheckBox chkRespaldo = new CheckBox("Hacer un respaldo automático al abrir el sistema (una vez al día)");
        chkRespaldo.setSelected(config.getBoolean(ConfiguracionService.RESPALDO_AUTOMATICO));
        Spinner<Integer> spConservar = new Spinner<>(1, 100,
                config.getEntero(ConfiguracionService.RESPALDO_CONSERVAR, 1, 100));
        spConservar.setEditable(true);
        spConservar.setPrefWidth(90);
        Label lblUltimo = new Label();
        Label lblCarpeta = new Label();
        lblCarpeta.getStyleClass().add("hint-label");
        lblCarpeta.setWrapText(true);
        Button btnRespaldar = new Button("Respaldar ahora");
        btnRespaldar.getStyleClass().add("secondary-button");

        Runnable actualizarEstadoRespaldo = () -> {
            String ultimo = config.get(ConfiguracionService.RESPALDO_ULTIMO);
            List<File> existentes = RespaldoService.listar();
            lblUltimo.setText("Último respaldo: " + (ultimo.isEmpty() ? "ninguno todavía" : ultimo)
                    + "   |   Respaldos guardados: " + existentes.size());
            lblCarpeta.setText("Carpeta: " + RespaldoService.carpeta().getAbsolutePath()
                    + "\nPara restaurar: cierra el sistema y reemplaza el archivo taller_repuestos.db por una copia de esa carpeta.");
        };
        actualizarEstadoRespaldo.run();

        btnRespaldar.setOnAction(e -> {
            try {
                File archivo = RespaldoService.respaldar(false);
                actualizarEstadoRespaldo.run();
                Alertas.info("Respaldo creado", "Se guardó una copia de seguridad:\n" + archivo.getAbsolutePath());
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo respaldar", ex.getMessage());
            }
        });

        HBox filaConservar = new HBox(10, new Label("Respaldos a conservar:"), spConservar);
        filaConservar.setAlignment(Pos.CENTER_LEFT);
        Label tituloRespaldo = new Label("Copias de seguridad");
        tituloRespaldo.getStyleClass().add("section-title");
        VBox panelRespaldo = new VBox(10, tituloRespaldo, chkRespaldo, filaConservar, lblUltimo, lblCarpeta, btnRespaldar);
        panelRespaldo.getStyleClass().add("panel-card");

        // ---- Guardar ----
        Button btnGuardar = new Button("Guardar configuración");
        btnGuardar.getStyleClass().add("primary-button");
        btnGuardar.setOnAction(e -> {
            Map<String, String> cambios = new LinkedHashMap<>();
            cambios.put(ConfiguracionService.NEGOCIO_NOMBRE, txtNombre.getText());
            cambios.put(ConfiguracionService.NEGOCIO_DIRECCION, txtDireccion.getText());
            cambios.put(ConfiguracionService.NEGOCIO_TELEFONO, txtTelefono.getText());
            cambios.put(ConfiguracionService.NEGOCIO_CORREO, txtCorreo.getText());
            cambios.put(ConfiguracionService.NEGOCIO_DATOS_FISCALES, txtFiscales.getText());
            cambios.put(ConfiguracionService.MONEDA_SIMBOLO, txtMoneda.getText());
            cambios.put(ConfiguracionService.CAJA_EXIGIR_ABIERTA, String.valueOf(chkCaja.isSelected()));
            cambios.put(ConfiguracionService.IMPUESTO_NOMBRE, txtImpNombre.getText());
            cambios.put(ConfiguracionService.IMPUESTO_PORCENTAJE, txtImpPct.getText());
            cambios.put(ConfiguracionService.IMPUESTO_INCLUIDO, String.valueOf(chkImpIncluido.isSelected()));
            cambios.put(ConfiguracionService.DESCUENTO_MAXIMO, txtDescMax.getText());
            cambios.put(ConfiguracionService.CREDITO_DIAS, txtDiasCredito.getText());
            cambios.put(ConfiguracionService.DOC_PREFIJO_FACTURA, txtPrefFactura.getText());
            cambios.put(ConfiguracionService.DOC_PREFIJO_DEVOLUCION, txtPrefDevolucion.getText());
            cambios.put(ConfiguracionService.DOC_DIGITOS, txtDigitos.getText());
            cambios.put(ConfiguracionService.DOC_PREFIJO_COMPRA, txtPrefCompra.getText());
            cambios.put(ConfiguracionService.DOC_PREFIJO_ORDEN, txtPrefOrden.getText());
            cambios.put(ConfiguracionService.DOC_PREFIJO_COTIZACION, txtPrefCot.getText());
            cambios.put(ConfiguracionService.COTIZACION_DIAS, txtDiasCot.getText());
            cambios.put(ConfiguracionService.RESPALDO_AUTOMATICO, String.valueOf(chkRespaldo.isSelected()));
            cambios.put(ConfiguracionService.RESPALDO_CONSERVAR, String.valueOf(leerConservar(spConservar)));
            try {
                int cambiados = config.guardarCambios(cambios);
                alGuardar.run();
                actualizarEstadoRespaldo.run();
                Alertas.info("Configuración guardada",
                        cambiados == 0 ? "No había cambios que guardar." : "Se guardaron " + cambiados + " cambio(s).");
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo guardar", ex.getMessage());
            }
        });

        VBox contenido = new VBox(16, titulo, panelNegocio, panelVentas, panelDocs, panelCaja, panelRespaldo, btnGuardar);
        contenido.setPadding(new Insets(16));
        contenido.setMaxWidth(760);

        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dashboard-scroll");
        return scroll;
    }

    private static TextField campo(String valor) {
        TextField t = new TextField(valor);
        t.setPrefWidth(420);
        return t;
    }

    /** Lee el spinner aunque el usuario haya escrito el número a mano y no haya presionado Enter. */
    private static int leerConservar(Spinner<Integer> spinner) {
        try {
            return Integer.parseInt(spinner.getEditor().getText().trim());
        } catch (NumberFormatException e) {
            return -1; // el servicio lo rechaza con un mensaje claro
        }
    }
}
