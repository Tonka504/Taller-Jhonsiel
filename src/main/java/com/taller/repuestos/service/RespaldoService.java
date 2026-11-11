package com.taller.repuestos.service;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Permiso;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Copias de seguridad de la base de datos en la carpeta "respaldos". */
public class RespaldoService {

    private static final String PREFIJO = "taller_repuestos_";
    private static final String EXTENSION = ".db";
    private static final DateTimeFormatter FORMATO_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private RespaldoService() {}

    public static File carpeta() {
        return new File("respaldos");
    }

    /** Crea una copia de seguridad ahora mismo y borra las más antiguas según la configuración. */
    public static File respaldar(boolean automatico) throws ReglaNegocioException {
        if (!automatico) PermisoService.get().exigir(Permiso.CONFIGURACION_EDITAR);
        File carpeta = carpeta();
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw new ReglaNegocioException("No se pudo crear la carpeta de respaldos: " + carpeta.getAbsolutePath());
        }
        LocalDateTime ahora = LocalDateTime.now();
        File destino = new File(carpeta, PREFIJO + ahora.format(FORMATO_ARCHIVO) + EXTENSION);
        if (destino.exists()) {
            throw new ReglaNegocioException("Ya existe un respaldo hecho en este mismo segundo. Intenta de nuevo.");
        }

        // VACUUM INTO crea una copia consistente y compacta de la base de datos, aunque esté en uso
        String ruta = destino.getAbsolutePath().replace("'", "''");
        try (Connection conn = Database.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("VACUUM INTO '" + ruta + "'");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ReglaNegocioException("No se pudo crear el respaldo: " + e.getMessage());
        }

        ConfiguracionService config = ConfiguracionService.get();
        config.set(ConfiguracionService.RESPALDO_ULTIMO, ahora.format(FORMATO_FECHA));
        eliminarAntiguos(config.getEntero(ConfiguracionService.RESPALDO_CONSERVAR, 1, 100));

        AuditoriaService.registrar(AuditoriaService.CONFIGURACION,
                automatico ? "Respaldo automático" : "Respaldo manual", destino.getName(), null, null);
        return destino;
    }

    /** Al iniciar el sistema: si el respaldo automático está activo y hoy no se ha hecho ninguno, lo hace. */
    public static void respaldoAutomaticoSiCorresponde() {
        ConfiguracionService config = ConfiguracionService.get();
        if (!config.getBoolean(ConfiguracionService.RESPALDO_AUTOMATICO)) return;
        String hoy = LocalDateTime.now().format(FORMATO_FECHA).substring(0, 10);
        if (config.get(ConfiguracionService.RESPALDO_ULTIMO).startsWith(hoy)) return;
        try {
            respaldar(true);
        } catch (ReglaNegocioException e) {
            // Un fallo del respaldo no debe impedir que el sistema abra
            System.err.println("Respaldo automático no realizado: " + e.getMessage());
        }
    }

    /** Respaldos existentes, del más reciente al más antiguo. */
    public static List<File> listar() {
        File[] archivos = carpeta().listFiles((dir, nombre) -> nombre.startsWith(PREFIJO) && nombre.endsWith(EXTENSION));
        if (archivos == null) return new ArrayList<>();
        List<File> lista = new ArrayList<>(Arrays.asList(archivos));
        lista.sort(Collections.reverseOrder()); // el nombre lleva la fecha, así que ordenar por nombre ordena por fecha
        return lista;
    }

    private static void eliminarAntiguos(int conservar) {
        List<File> todos = listar();
        for (int i = conservar; i < todos.size(); i++) {
            if (!todos.get(i).delete()) {
                System.err.println("No se pudo borrar el respaldo antiguo: " + todos.get(i).getName());
            }
        }
    }
}
