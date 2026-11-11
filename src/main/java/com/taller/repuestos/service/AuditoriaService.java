package com.taller.repuestos.service;

import com.taller.repuestos.dao.AuditoriaDAO;
import com.taller.repuestos.model.RegistroAuditoria;
import com.taller.repuestos.util.Sesion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Registra quién hizo qué. Si por algún motivo no se puede escribir la auditoría,
 * se informa en consola pero nunca se interrumpe la operación del usuario.
 */
public class AuditoriaService {

    // Módulos (para filtrar en la pantalla de auditoría)
    public static final String SESION = "Sesión";
    public static final String USUARIOS = "Usuarios";
    public static final String INVENTARIO = "Inventario";
    public static final String VENTAS = "Ventas";
    public static final String CAJA = "Caja";
    public static final String CONFIGURACION = "Configuración";
    public static final String CLIENTES = "Clientes";
    public static final String CATALOGO = "Catálogo";
    public static final String PROVEEDORES = "Proveedores";
    public static final String COMPRAS = "Compras";
    public static final String TALLER = "Taller";

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AuditoriaDAO DAO = new AuditoriaDAO();

    private AuditoriaService() {}

    /** Registra una acción del usuario con la sesión iniciada. */
    public static void registrar(String modulo, String accion, String registro, String anterior, String nuevo) {
        registrarComo(Sesion.nombreActual(), modulo, accion, registro, anterior, nuevo);
    }

    /** Registra una acción a nombre de alguien concreto (por ejemplo, un intento de acceso fallido). */
    public static void registrarComo(String usuario, String modulo, String accion,
                                     String registro, String anterior, String nuevo) {
        try {
            DAO.registrar(LocalDateTime.now().format(FORMATO_FECHA), usuario, modulo, accion, registro, anterior, nuevo);
        } catch (RuntimeException e) {
            System.err.println("No se pudo registrar la auditoría: " + e.getMessage());
        }
    }

    public static List<RegistroAuditoria> buscar(String desde, String hasta, String usuario, String modulo,
                                                 String texto, int limite) {
        return DAO.buscar(desde, hasta, usuario, modulo, texto, limite);
    }

    public static List<String> usuariosRegistrados() { return DAO.usuariosRegistrados(); }

    public static List<String> modulosRegistrados() { return DAO.modulosRegistrados(); }
}
