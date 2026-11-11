package com.taller.repuestos.ui;

import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Rol;
import com.taller.repuestos.model.Usuario;
import com.taller.repuestos.service.PermisoService;
import com.taller.repuestos.service.UsuarioService;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Pantalla de gestión de usuarios (crear, cambiar contraseña y rol, activar/desactivar, permisos por rol). */
public class VistaUsuarios {

    private final UsuarioService servicio;

    public VistaUsuarios(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @SuppressWarnings("unchecked")
    public Node crear() {
        Label titulo = new Label("Gestión de usuarios");
        titulo.getStyleClass().add("section-title");

        TableView<Usuario> tabla = new TableView<>();

        TableColumn<Usuario, String> colNombre = new TableColumn<>("Nombre");
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombreCompleto"));
        TableColumn<Usuario, String> colUsuario = new TableColumn<>("Usuario");
        colUsuario.setCellValueFactory(new PropertyValueFactory<>("username"));
        TableColumn<Usuario, String> colRol = new TableColumn<>("Rol");
        colRol.setCellValueFactory(new PropertyValueFactory<>("rol"));
        TableColumn<Usuario, String> colEstado = new TableColumn<>("Estado");
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));

        tabla.getColumns().addAll(colNombre, colUsuario, colRol, colEstado);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay usuarios registrados"));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Runnable refrescar = () -> tabla.setItems(FXCollections.observableArrayList(servicio.listar()));
        refrescar.run();

        Button btnNuevo = new Button("Nuevo usuario");
        btnNuevo.getStyleClass().add("primary-button");
        Button btnPassword = new Button("Cambiar contraseña");
        btnPassword.getStyleClass().add("secondary-button");
        Button btnRol = new Button("Cambiar rol");
        btnRol.getStyleClass().add("secondary-button");
        Button btnActivar = new Button("Activar");
        btnActivar.getStyleClass().add("secondary-button");
        Button btnDesactivar = new Button("Desactivar");
        btnDesactivar.getStyleClass().add("danger-button");
        Button btnPermisos = new Button("Permisos por rol");
        btnPermisos.getStyleClass().add("secondary-button");

        // Los botones solo se habilitan cuando tienen sentido para el usuario seleccionado
        MultipleSelectionModel<Usuario> seleccion = tabla.getSelectionModel();
        btnPassword.disableProperty().bind(seleccion.selectedItemProperty().isNull());
        btnRol.disableProperty().bind(seleccion.selectedItemProperty().isNull());
        btnActivar.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            Usuario u = seleccion.getSelectedItem();
            return u == null || u.isActivo();
        }, seleccion.selectedItemProperty()));
        btnDesactivar.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            Usuario u = seleccion.getSelectedItem();
            return u == null || !u.isActivo();
        }, seleccion.selectedItemProperty()));

        btnNuevo.setOnAction(e -> mostrarDialogoNuevoUsuario(refrescar));

        btnPassword.setOnAction(e -> {
            Usuario u = seleccion.getSelectedItem();
            if (u != null) mostrarDialogoCambiarPassword(u);
        });

        btnRol.setOnAction(e -> {
            Usuario u = seleccion.getSelectedItem();
            if (u == null) return;
            ChoiceDialog<String> dialogo = new ChoiceDialog<>(u.getRol(), Rol.nombres());
            dialogo.setTitle("Cambiar rol");
            dialogo.setHeaderText("Rol de " + u.getNombreCompleto() + " (" + u.getUsername() + ")");
            dialogo.setContentText("Nuevo rol:");
            Alertas.estilizar(dialogo);
            dialogo.showAndWait().ifPresent(nuevoRol -> {
                if (nuevoRol.equals(u.getRol())) return;
                try {
                    servicio.cambiarRol(u, nuevoRol);
                    refrescar.run();
                    Alertas.info("Rol actualizado", u.getNombreCompleto() + " ahora es " + nuevoRol + ".");
                } catch (ReglaNegocioException ex) {
                    Alertas.advertencia("No se pudo cambiar el rol", ex.getMessage());
                }
            });
        });

        btnActivar.setOnAction(e -> {
            Usuario u = seleccion.getSelectedItem();
            if (u == null) return;
            try {
                servicio.cambiarEstado(u, true);
                refrescar.run();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo activar el usuario", ex.getMessage());
            }
        });

        btnDesactivar.setOnAction(e -> {
            Usuario u = seleccion.getSelectedItem();
            if (u == null) return;
            if (!Alertas.confirmar("Desactivar usuario",
                    "¿Desactivar a " + u.getNombreCompleto() + "? No podrá iniciar sesión hasta que lo actives de nuevo.")) {
                return;
            }
            try {
                servicio.cambiarEstado(u, false);
                refrescar.run();
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo desactivar el usuario", ex.getMessage());
            }
        });

        btnPermisos.setOnAction(e -> DialogoPermisos.mostrar(PermisoService.get()));

        HBox botones = new HBox(10, btnNuevo, btnPassword, btnRol, btnActivar, btnDesactivar, btnPermisos);
        botones.setAlignment(Pos.CENTER_LEFT);

        VBox contenido = new VBox(14, titulo, botones, tabla);
        contenido.setPadding(new Insets(16));
        return contenido;
    }

    private void mostrarDialogoNuevoUsuario(Runnable alCrear) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Nuevo usuario");
        dialogo.setHeaderText("Datos del nuevo usuario");

        ButtonType btnCrear = new ButtonType("Crear usuario", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(btnCrear, ButtonType.CANCEL);

        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre completo");
        TextField txtUsuario = new TextField();
        txtUsuario.setPromptText("Nombre de usuario (sin espacios)");
        PasswordField txtPassword = new PasswordField();
        txtPassword.setPromptText("Mínimo 6 caracteres");
        PasswordField txtConfirmar = new PasswordField();
        txtConfirmar.setPromptText("Repite la contraseña");
        ComboBox<String> comboRol = new ComboBox<>();
        comboRol.getItems().addAll(Rol.nombres());
        comboRol.setValue(Rol.VENDEDOR.name());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));
        grid.add(new Label("Nombre:"), 0, 0);
        grid.add(txtNombre, 1, 0);
        grid.add(new Label("Usuario:"), 0, 1);
        grid.add(txtUsuario, 1, 1);
        grid.add(new Label("Contraseña:"), 0, 2);
        grid.add(txtPassword, 1, 2);
        grid.add(new Label("Confirmar:"), 0, 3);
        grid.add(txtConfirmar, 1, 3);
        grid.add(new Label("Rol:"), 0, 4);
        grid.add(comboRol, 1, 4);
        dialogo.getDialogPane().setContent(grid);

        Alertas.estilizar(dialogo);
        Button okBtn = (Button) dialogo.getDialogPane().lookupButton(btnCrear);
        okBtn.getStyleClass().add("primary-button");

        // La validación se hace aquí para que, si algo está mal, el diálogo NO se cierre
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            if (!txtPassword.getText().equals(txtConfirmar.getText())) {
                Alertas.advertencia("Contraseñas distintas", "La contraseña y su confirmación no coinciden.");
                ev.consume();
                return;
            }
            try {
                servicio.crear(txtUsuario.getText(), txtPassword.getText(), txtNombre.getText(), comboRol.getValue());
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo crear el usuario", ex.getMessage());
                ev.consume();
            }
        });

        dialogo.showAndWait().ifPresent(resultado -> {
            if (resultado == btnCrear) {
                alCrear.run();
                Alertas.info("Usuario creado", "El usuario \"" + txtUsuario.getText().trim() + "\" se creó correctamente.");
            }
        });
    }

    private void mostrarDialogoCambiarPassword(Usuario usuario) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Cambiar contraseña");
        dialogo.setHeaderText("Nueva contraseña para " + usuario.getNombreCompleto() + " (" + usuario.getUsername() + ")");

        ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CANCEL);

        PasswordField txtNueva = new PasswordField();
        txtNueva.setPromptText("Mínimo 6 caracteres");
        PasswordField txtConfirmar = new PasswordField();
        txtConfirmar.setPromptText("Repite la contraseña");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));
        grid.add(new Label("Nueva contraseña:"), 0, 0);
        grid.add(txtNueva, 1, 0);
        grid.add(new Label("Confirmar:"), 0, 1);
        grid.add(txtConfirmar, 1, 1);
        dialogo.getDialogPane().setContent(grid);

        Alertas.estilizar(dialogo);
        Button okBtn = (Button) dialogo.getDialogPane().lookupButton(btnGuardar);
        okBtn.getStyleClass().add("primary-button");

        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            if (!txtNueva.getText().equals(txtConfirmar.getText())) {
                Alertas.advertencia("Contraseñas distintas", "La contraseña y su confirmación no coinciden.");
                ev.consume();
                return;
            }
            try {
                servicio.cambiarPassword(usuario, txtNueva.getText());
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudo cambiar la contraseña", ex.getMessage());
                ev.consume();
            }
        });

        dialogo.showAndWait().ifPresent(resultado -> {
            if (resultado == btnGuardar) {
                Alertas.info("Contraseña actualizada", "La contraseña de " + usuario.getUsername() + " se cambió correctamente.");
            }
        });
    }
}
