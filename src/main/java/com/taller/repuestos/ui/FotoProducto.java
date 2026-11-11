package com.taller.repuestos.ui;

import com.taller.repuestos.util.Fotos;
import com.taller.repuestos.util.Iconos;

import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** Recuadro que muestra la foto de un producto (o un icono de tuerca si no tiene). */
public class FotoProducto extends StackPane {

    // Caché pequeña para no volver a leer del disco las fotos de las miniaturas al desplazarse por las tablas
    private static final Map<String, Image> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> e) { return size() > 200; }
    };

    private final double tamano;
    private final ImageView vista = new ImageView();

    public FotoProducto(double tamano) {
        this.tamano = tamano;
        getStyleClass().add("foto-producto");
        setMinSize(tamano, tamano);
        setPrefSize(tamano, tamano);
        setMaxSize(tamano, tamano);
        setAlignment(Pos.CENTER);
        vista.setFitWidth(tamano - 4);
        vista.setFitHeight(tamano - 4);
        vista.setPreserveRatio(true);
        vista.setSmooth(true);
        Rectangle recorte = new Rectangle(tamano - 4, tamano - 4);
        recorte.setArcWidth(10);
        recorte.setArcHeight(10);
        vista.setClip(recorte);
        mostrar(null);
    }

    /** Muestra la foto guardada con ese nombre (null o inexistente = icono). */
    public void mostrar(String nombreFoto) {
        File f = Fotos.archivo(nombreFoto);
        if (f == null) {
            getChildren().setAll(Iconos.crear(Iconos.TUERCA, Math.max(16, tamano * 0.4)));
            if (!getStyleClass().contains("foto-vacia")) getStyleClass().add("foto-vacia");
            return;
        }
        getStyleClass().remove("foto-vacia");
        vista.setImage(cargar(f, tamano));
        getChildren().setAll(vista);
    }

    /** Las miniaturas de tabla cargan en segundo plano; las fotos grandes se cargan al momento. */
    private static Image cargar(File f, double tamano) {
        String clave = f.getName() + "@" + (int) tamano;
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(clave, k -> new Image(f.toURI().toString(), tamano * 2, tamano * 2, true, true, tamano <= 40));
        }
    }
}
