package com.taller.repuestos.ui;

import com.taller.repuestos.dao.CajaDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Caja;
import com.taller.repuestos.model.MovimientoCaja;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.service.CajaService;
import com.taller.repuestos.service.CajaService.ResumenCaja;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.util.Formato;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Pantalla de caja: apertura con fondo inicial, movimientos de efectivo, cierre con conteo e historial. */
public class VistaCaja {

    private final CajaService servicio;
    private final Runnable alCambiar;
    private final PermisoService permisos = PermisoService.get();
    private final ScrollPane scroll = new ScrollPane();

    /**
     * @param alCambiar se ejecuta cuando la caja se abre o se cierra (para actualizar el indicador de la barra superior)
     */
    public VistaCaja(CajaService servicio, Runnable alCambiar) {
        this.servicio = servicio;
        this.alCambiar = alCambiar;
    }

    public Node crear() {
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dashboard-scroll");
        reconstruir();
        return scroll;
    }

    /** Dibuja la pantalla según el estado actual de la caja. */
    private void reconstruir() {
        VBox contenido = new VBox(18);
        contenido.setPadding(new Insets(16));

        Label titulo = new Label("Caja");
        titulo.getStyleClass().add("dashboard-title");
        contenido.getChildren().add(titulo);

        Caja abierta = servicio.cajaAbierta();
        if (abierta == null) {
            contenido.getChildren().add(panelApertura());
        } else {
            ResumenCaja resumen = servicio.resumen(abierta);
            contenido.getChildren().addAll(panelResumen(resumen), panelMovimientos(abierta), panelCierre(resumen));
        }
        contenido.getChildren().add(panelHistorial());
        scroll.setContent(contenido);
    }

    // ------------------------------------------------------------------
    // APERTURA
    // ------------------------------------------------------------------
    private Node panelApertura() {
        Label estado = new Label("La caja está cerrada");
        estado.getStyleClass().add("section-title");
        Label ayuda = new Label("Para vender y registrar movimientos primero hay que abrir la caja con el efectivo inicial (fondo).");
        ayuda.getStyleClass().add("hint-label");
        ayuda.setWrapText(true);

        VBox panel = new VBox(10, estado, ayuda);
        panel.getStyleClass().add("panel-card");

        if (!permisos.tiene(Permiso.CAJA_ABRIR)) {
            Label sinPermiso = new Label("Tu rol no puede abrir la caja. Pídele a un cajero o administrador que la abra.");
            sinPermiso.getStyleClass().add("estado-error");
            sinPermiso.setWrapText(true);
            panel.getChildren().add(sinPermiso);
            return panel;
        }

        TextField txtFondo = new TextField();
        txtFondo.setPromptText("Fondo inicial, ej: 500.00");
        txtFondo.setPrefWidth(200);
        Button btnAbrir = new Button("Abrir caja");
        btnAbrir.getStyleClass().add("primary-button");

        Runnable abrir = () -> {
            double monto;
            try {
                monto = Formato.leerMonto(txtFondo.getText());
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Monto inválido", "Escribe el fondo inicial con números (ej: 500.00). Si no hay fondo, escribe 0.");
                return;
            }
            try {
                CajaDAO.Apertura apertura = servicio.abrir(monto);
                alCambiar.run();
                reconstruir();
                String extra = apertura.ventasIncluidas > 0
                        ? "\n\nSe incluyeron " + apertura.ventasIncluidas + " venta(s) de hoy que se hicieron sin caja abierta."
                        : "";
                Alertas.info("Caja abierta", "La caja quedó abierta con un fondo de " + Formato.moneda(monto) + "." + extra);
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo abrir la caja", ex.getMessage());
            }
        };
        btnAbrir.setOnAction(e -> abrir.run());
        txtFondo.setOnAction(e -> abrir.run());

        HBox fila = new HBox(10, txtFondo, btnAbrir);
        fila.setAlignment(Pos.CENTER_LEFT);
        panel.getChildren().add(fila);
        return panel;
    }

    // ------------------------------------------------------------------
    // RESUMEN DEL TURNO
    // ------------------------------------------------------------------
    private Node panelResumen(ResumenCaja r) {
        Label titulo = new Label("Caja abierta");
        titulo.getStyleClass().add("section-title");
        Label detalle = new Label("Abierta por " + r.caja.getUsuarioApertura() + " el " + r.caja.getFechaApertura());
        detalle.getStyleClass().add("hint-label");

        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(6);
        int fila = 0;
        fila = agregarFila(grid, fila, "Fondo inicial", Formato.moneda(r.caja.getMontoInicial()), false);
        fila = agregarFila(grid, fila, "Ventas en efectivo", Formato.moneda(r.ventasEfectivo), false);
        fila = agregarFila(grid, fila, "Ventas con tarjeta", Formato.moneda(r.ventasTarjeta), false);
        fila = agregarFila(grid, fila, "Ventas por transferencia", Formato.moneda(r.ventasTransferencia), false);
        fila = agregarFila(grid, fila, "Ventas a crédito", Formato.moneda(r.ventasCredito), false);
        fila = agregarFila(grid, fila, "Total vendido (" + r.cantidadVentas + " ventas)", Formato.moneda(r.getTotalVentas()), false);
        fila = agregarFila(grid, fila, "Abonos cobrados en efectivo", Formato.moneda(r.abonosEfectivo), false);
        fila = agregarFila(grid, fila, "Entradas de efectivo", Formato.moneda(r.entradas), false);
        fila = agregarFila(grid, fila, "Retiros", Formato.moneda(r.retiros), false);
        fila = agregarFila(grid, fila, "Gastos", Formato.moneda(r.gastos), false);
        fila = agregarFila(grid, fila, "Devoluciones pagadas en efectivo", Formato.moneda(r.devoluciones), false);
        agregarFila(grid, fila, "Efectivo esperado en caja", Formato.moneda(r.efectivoEsperado), true);

        VBox panel = new VBox(10, titulo, detalle, grid);
        panel.getStyleClass().add("panel-card");
        return panel;
    }

    private static int agregarFila(GridPane grid, int fila, String etiqueta, String valor, boolean destacado) {
        Label lbl = new Label(etiqueta);
        lbl.getStyleClass().add("metric-label");
        Label val = new Label(valor);
        val.getStyleClass().add(destacado ? "metric-value-grande" : "metric-value");
        grid.add(lbl, 0, fila);
        grid.add(val, 1, fila);
        return fila + 1;
    }

    // ------------------------------------------------------------------
    // MOVIMIENTOS
    // ------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private Node panelMovimientos(Caja caja) {
        Label titulo = new Label("Movimientos de efectivo");
        titulo.getStyleClass().add("section-title");

        TableView<MovimientoCaja> tabla = new TableView<>();
        TableColumn<MovimientoCaja, String> colFecha = new TableColumn<>("Fecha");
        colFecha.setCellValueFactory(new PropertyValueFactory<>("fecha"));
        colFecha.setPrefWidth(150);
        TableColumn<MovimientoCaja, String> colTipo = new TableColumn<>("Tipo");
        colTipo.setCellValueFactory(new PropertyValueFactory<>("tipoEtiqueta"));
        colTipo.setPrefWidth(150);
        TableColumn<MovimientoCaja, Double> colMonto = columnaMoneda("Monto", "monto");
        TableColumn<MovimientoCaja, String> colMotivo = new TableColumn<>("Motivo");
        colMotivo.setCellValueFactory(new PropertyValueFactory<>("motivo"));
        colMotivo.setPrefWidth(220);
        TableColumn<MovimientoCaja, String> colDoc = new TableColumn<>("Documento");
        colDoc.setCellValueFactory(new PropertyValueFactory<>("documento"));
        TableColumn<MovimientoCaja, String> colUsuario = new TableColumn<>("Usuario");
        colUsuario.setCellValueFactory(new PropertyValueFactory<>("usuario"));
        colUsuario.setPrefWidth(140);
        tabla.getColumns().addAll(colFecha, colTipo, colMonto, colMotivo, colDoc, colUsuario);
        tabla.setPlaceholder(new Label("Aún no hay movimientos en este turno"));
        tabla.setPrefHeight(200);
        tabla.setItems(FXCollections.observableArrayList(servicio.movimientos(caja.getId())));

        VBox panel = new VBox(10, titulo);
        panel.getStyleClass().add("panel-card");

        if (permisos.tiene(Permiso.CAJA_MOVIMIENTOS)) {
            ComboBox<MovimientoCaja.Tipo> comboTipo = new ComboBox<>();
            for (MovimientoCaja.Tipo tipo : MovimientoCaja.Tipo.values()) {
                if (tipo.isManual()) comboTipo.getItems().add(tipo);
            }
            comboTipo.setValue(MovimientoCaja.Tipo.RETIRO);
            TextField txtMonto = new TextField();
            txtMonto.setPromptText("Monto");
            txtMonto.setPrefWidth(110);
            TextField txtMotivo = new TextField();
            txtMotivo.setPromptText("Motivo (obligatorio)");
            HBox.setHgrow(txtMotivo, Priority.ALWAYS);
            TextField txtDoc = new TextField();
            txtDoc.setPromptText("N° de documento (opcional)");
            txtDoc.setPrefWidth(190);
            Button btnRegistrar = new Button("Registrar");
            btnRegistrar.getStyleClass().add("secondary-button");

            btnRegistrar.setOnAction(e -> {
                double monto;
                try {
                    monto = Formato.leerMonto(txtMonto.getText());
                } catch (NumberFormatException ex) {
                    Alertas.advertencia("Monto inválido", "Escribe el monto con números (ej: 120.50).");
                    return;
                }
                try {
                    servicio.registrarMovimiento(comboTipo.getValue(), monto, txtMotivo.getText(), txtDoc.getText());
                    reconstruir();
                } catch (ReglaNegocioException ex) {
                    Alertas.advertencia("No se pudo registrar el movimiento", ex.getMessage());
                }
            });

            HBox formulario = new HBox(10, comboTipo, txtMonto, txtMotivo, txtDoc, btnRegistrar);
            formulario.setAlignment(Pos.CENTER_LEFT);
            panel.getChildren().add(formulario);
        }

        panel.getChildren().add(tabla);
        return panel;
    }

    // ------------------------------------------------------------------
    // CIERRE
    // ------------------------------------------------------------------
    private Node panelCierre(ResumenCaja resumen) {
        Label titulo = new Label("Cierre de caja");
        titulo.getStyleClass().add("section-title");

        VBox panel = new VBox(10, titulo);
        panel.getStyleClass().add("panel-card");

        if (!permisos.tiene(Permiso.CAJA_CERRAR)) {
            Label nota = new Label("Tu rol no puede cerrar la caja.");
            nota.getStyleClass().add("hint-label");
            panel.getChildren().add(nota);
            return panel;
        }

        TextField txtContado = new TextField();
        txtContado.setPromptText("Efectivo contado en caja");
        txtContado.setPrefWidth(220);
        Label lblDiferencia = new Label("Diferencia: -");
        lblDiferencia.getStyleClass().add("metric-value");
        TextField txtObservaciones = new TextField();
        txtObservaciones.setPromptText("Observaciones (opcional)");
        Button btnCerrar = new Button("Cerrar caja");
        btnCerrar.getStyleClass().add("danger-button");

        // Diferencia en vivo mientras se escribe el conteo
        txtContado.textProperty().addListener((obs, anterior, nuevo) -> {
            lblDiferencia.getStyleClass().removeAll("valor-ok", "valor-alerta");
            try {
                double dif = Math.round((Formato.leerMonto(nuevo) - resumen.efectivoEsperado) * 100.0) / 100.0;
                lblDiferencia.setText("Diferencia: " + Formato.moneda(dif) + (dif == 0 ? "  (cuadra)" : dif > 0 ? "  (sobra)" : "  (falta)"));
                lblDiferencia.getStyleClass().add(dif == 0 ? "valor-ok" : "valor-alerta");
            } catch (NumberFormatException ex) {
                lblDiferencia.setText("Diferencia: -");
            }
        });

        btnCerrar.setOnAction(e -> {
            double contado;
            try {
                contado = Formato.leerMonto(txtContado.getText());
            } catch (NumberFormatException ex) {
                Alertas.advertencia("Monto inválido", "Escribe el efectivo contado con números (ej: 1500.50).");
                return;
            }
            if (!Alertas.confirmar("Cerrar caja",
                    "Efectivo esperado: " + Formato.moneda(resumen.efectivoEsperado) + "\n"
                    + "Efectivo contado: " + Formato.moneda(contado) + "\n\n¿Cerrar la caja ahora?")) {
                return;
            }
            try {
                Caja cerrada = servicio.cerrar(contado, txtObservaciones.getText());
                alCambiar.run();
                reconstruir();
                Alertas.informe("Caja cerrada",
                        "Total vendido: " + Formato.moneda(cerrada.getTotalSistema()) + "\n"
                        + "Efectivo esperado: " + Formato.moneda(cerrada.getEfectivoEsperado()) + "\n"
                        + "Efectivo contado: " + Formato.moneda(cerrada.getEfectivoContado()) + "\n"
                        + "Diferencia: " + Formato.moneda(cerrada.getDiferencia()));
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo cerrar la caja", ex.getMessage());
            }
        });

        HBox filaConteo = new HBox(15, txtContado, lblDiferencia);
        filaConteo.setAlignment(Pos.CENTER_LEFT);
        panel.getChildren().addAll(filaConteo, txtObservaciones, btnCerrar);
        return panel;
    }

    // ------------------------------------------------------------------
    // HISTORIAL
    // ------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private Node panelHistorial() {
        Label titulo = new Label("Historial de cierres");
        titulo.getStyleClass().add("section-title");

        TableView<Caja> tabla = new TableView<>();
        TableColumn<Caja, Integer> colId = new TableColumn<>("Nº");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setPrefWidth(50);
        TableColumn<Caja, String> colApertura = new TableColumn<>("Apertura");
        colApertura.setCellValueFactory(new PropertyValueFactory<>("fechaApertura"));
        colApertura.setPrefWidth(150);
        TableColumn<Caja, String> colCierre = new TableColumn<>("Cierre");
        colCierre.setCellValueFactory(new PropertyValueFactory<>("fechaCierre"));
        colCierre.setPrefWidth(150);
        TableColumn<Caja, String> colUsuario = new TableColumn<>("Abrió");
        colUsuario.setCellValueFactory(new PropertyValueFactory<>("usuarioApertura"));
        colUsuario.setPrefWidth(130);
        tabla.getColumns().addAll(colId, colApertura, colCierre, colUsuario,
                columnaMoneda("Fondo", "montoInicial"),
                columnaMoneda("Vendido", "totalSistema"),
                columnaMoneda("Esperado", "efectivoEsperado"),
                columnaMoneda("Contado", "efectivoContado"),
                columnaMoneda("Diferencia", "diferencia"));
        tabla.setPlaceholder(new Label("Aún no hay cierres registrados"));
        tabla.setPrefHeight(260);
        tabla.setItems(FXCollections.observableArrayList(servicio.historial(30)));

        VBox panel = new VBox(10, titulo, tabla);
        return panel;
    }

    private static <S> TableColumn<S, Double> columnaMoneda(String titulo, String propiedad) {
        TableColumn<S, Double> col = new TableColumn<>(titulo);
        col.setCellValueFactory(new PropertyValueFactory<>(propiedad));
        col.setCellFactory(c -> new TableCell<S, Double>() {
            @Override
            protected void updateItem(Double valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : Formato.moneda(valor));
            }
        });
        return col;
    }
}
