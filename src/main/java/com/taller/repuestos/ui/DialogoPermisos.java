package com.taller.repuestos.ui;

import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Rol;
import com.taller.repuestos.service.PermisoService;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Diálogo para elegir qué puede hacer cada rol. El rol ADMIN siempre tiene todo y no se edita. */
public final class DialogoPermisos {

    private DialogoPermisos() {}

    public static void mostrar(PermisoService permisos) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Permisos por rol");
        dialogo.setHeaderText("Elige qué puede hacer cada rol. El administrador siempre tiene todos los permisos.");

        ButtonType btnGuardar = new ButtonType("Guardar cambios", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(btnGuardar, ButtonType.CLOSE);

        ComboBox<String> comboRol = new ComboBox<>();
        for (Rol r : Rol.values()) {
            if (r != Rol.ADMIN) comboRol.getItems().add(r.name());
        }
        comboRol.setValue(comboRol.getItems().get(0));

        // Un casillero por permiso, agrupados por área
        Map<Permiso, CheckBox> casilleros = new EnumMap<>(Permiso.class);
        VBox lista = new VBox(6);
        lista.setPadding(new Insets(10));
        String grupoActual = null;
        for (Permiso p : Permiso.values()) {
            if (!p.getGrupo().equals(grupoActual)) {
                grupoActual = p.getGrupo();
                Label cabecera = new Label(grupoActual);
                cabecera.getStyleClass().add("section-title");
                VBox.setMargin(cabecera, new Insets(8, 0, 2, 0));
                lista.getChildren().add(cabecera);
            }
            CheckBox cb = new CheckBox(p.getEtiqueta());
            casilleros.put(p, cb);
            lista.getChildren().add(cb);
        }

        Runnable cargar = () -> {
            Set<Permiso> actuales = permisos.permisosDe(comboRol.getValue());
            for (Map.Entry<Permiso, CheckBox> e : casilleros.entrySet()) {
                e.getValue().setSelected(actuales.contains(e.getKey()));
            }
        };
        comboRol.setOnAction(e -> cargar.run());
        cargar.run();

        ScrollPane scroll = new ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);
        scroll.setPrefViewportWidth(420);

        Label nota = new Label("Los cambios se aplican a cada usuario la próxima vez que inicie sesión.");
        nota.getStyleClass().add("hint-label");

        HBox fila = new HBox(10, new Label("Rol:"), comboRol);
        VBox contenido = new VBox(12, fila, scroll, nota);
        contenido.setPadding(new Insets(10));
        dialogo.getDialogPane().setContent(contenido);

        Alertas.estilizar(dialogo);
        Button okBtn = (Button) dialogo.getDialogPane().lookupButton(btnGuardar);
        okBtn.getStyleClass().add("primary-button");

        // Guardar no cierra el diálogo: así se pueden ajustar varios roles seguidos
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume();
            Set<Permiso> elegidos = EnumSet.noneOf(Permiso.class);
            for (Map.Entry<Permiso, CheckBox> e : casilleros.entrySet()) {
                if (e.getValue().isSelected()) elegidos.add(e.getKey());
            }
            try {
                permisos.guardarPermisos(comboRol.getValue(), elegidos);
                Alertas.info("Permisos guardados", "Se actualizaron los permisos del rol " + comboRol.getValue() + ".");
            } catch (ReglaNegocioException ex) {
                Alertas.advertencia("No se pudieron guardar los permisos", ex.getMessage());
            }
        });

        dialogo.showAndWait();
    }
}
