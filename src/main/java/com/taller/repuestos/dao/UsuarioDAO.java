package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Rol;
import com.taller.repuestos.model.Usuario;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class UsuarioDAO {

    // =====================================================================
    // HASH DE CONTRASEÑAS (PBKDF2WithHmacSHA256, incluido en Java: sin librerías extra)
    // Formato guardado:  pbkdf2_sha256$ITERACIONES$SAL_BASE64$HASH_BASE64
    // La cantidad de iteraciones queda dentro del propio texto, así que en el futuro
    // se puede subir sin invalidar las contraseñas ya guardadas.
    // =====================================================================
    private static final String PREFIJO = "pbkdf2_sha256";
    private static final int ITERACIONES = 600_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    private static final int MIN_LONGITUD_PASSWORD = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String hashPassword(String passwordPlano) {
        byte[] sal = new byte[BYTES_SAL];
        RANDOM.nextBytes(sal);
        byte[] hash = derivar(passwordPlano, sal, ITERACIONES);
        return PREFIJO + "$" + ITERACIONES + "$"
                + Base64.getEncoder().encodeToString(sal) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verificarPassword(String passwordPlano, String guardado) {
        if (!esHash(guardado)) return false;
        try {
            String[] partes = guardado.split("\\$");
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] calculado = derivar(passwordPlano, sal, iteraciones);
            return MessageDigest.isEqual(esperado, calculado); // comparación en tiempo constante
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** true si el texto tiene el formato de hash de este sistema (y no es una contraseña en texto plano). */
    static boolean esHash(String valor) {
        if (valor == null) return false;
        String[] partes = valor.split("\\$");
        if (partes.length != 4 || !PREFIJO.equals(partes[0])) return false;
        try {
            Integer.parseInt(partes[1]);
            Base64.getDecoder().decode(partes[2]);
            Base64.getDecoder().decode(partes[3]);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derivar(String password, byte[] sal, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), sal, iteraciones, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el hash de la contraseña", e);
        } finally {
            spec.clearPassword();
        }
    }

    // =====================================================================
    // INICIALIZACIÓN (las llama Database.initDatabase con su propia conexión)
    // =====================================================================

    /** Convierte a hash las contraseñas que sigan en texto plano (bases de datos anteriores). */
    public static void migrarContrasenasTextoPlano(Connection conn) throws SQLException {
        List<Object[]> pendientes = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, password FROM usuarios")) {
            while (rs.next()) {
                String actual = rs.getString("password");
                if (actual != null && !actual.isEmpty() && !esHash(actual)) {
                    pendientes.add(new Object[]{rs.getInt("id"), actual});
                }
            }
        }
        if (pendientes.isEmpty()) return;

        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET password = ? WHERE id = ?")) {
            for (Object[] fila : pendientes) {
                ps.setString(1, hashPassword((String) fila[1]));
                ps.setInt(2, (Integer) fila[0]);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommitOriginal);
        }
    }

    /** En una base de datos nueva (sin usuarios) crea admin y vendedor, ya con la contraseña cifrada. */
    public static void crearUsuariosPorDefecto(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM usuarios")) {
            if (rs.next() && rs.getInt(1) > 0) return;
        }
        String sql = "INSERT INTO usuarios (username, password, nombre, rol, activo) VALUES (?, ?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "admin");
            ps.setString(2, hashPassword("admin123"));
            ps.setString(3, "Administrador Principal");
            ps.setString(4, "ADMIN");
            ps.executeUpdate();

            ps.setString(1, "vendedor");
            ps.setString(2, hashPassword("vendedor123"));
            ps.setString(3, "Vendedor de Caja");
            ps.setString(4, "VENDEDOR");
            ps.executeUpdate();
        }
    }

    // =====================================================================
    // LOGIN
    // =====================================================================

    /** Devuelve el usuario si las credenciales son correctas y está activo; si no, null. */
    public Usuario autenticar(String username, String password) {
        String sql = "SELECT * FROM usuarios WHERE username = ? COLLATE NOCASE AND activo = 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next() && verificarPassword(password, rs.getString("password"))) {
                    return mapear(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // =====================================================================
    // GESTIÓN DE USUARIOS
    // =====================================================================

    public List<Usuario> obtenerTodos() {
        List<Usuario> lista = new ArrayList<>();
        String sql = "SELECT * FROM usuarios ORDER BY activo DESC, nombre COLLATE NOCASE";
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) lista.add(mapear(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public void crear(String username, String password, String nombre, String rol) throws ReglaNegocioException {
        username = username == null ? "" : username.trim();
        nombre = nombre == null ? "" : nombre.trim();

        if (nombre.isEmpty()) throw new ReglaNegocioException("El nombre es obligatorio.");
        if (username.isEmpty()) throw new ReglaNegocioException("El nombre de usuario es obligatorio.");
        if (username.contains(" ")) throw new ReglaNegocioException("El nombre de usuario no puede tener espacios.");
        validarPassword(password);
        validarRol(rol);

        try (Connection conn = Database.getConnection()) {
            try (PreparedStatement existe = conn.prepareStatement(
                    "SELECT 1 FROM usuarios WHERE username = ? COLLATE NOCASE")) {
                existe.setString(1, username);
                try (ResultSet rs = existe.executeQuery()) {
                    if (rs.next()) throw new ReglaNegocioException("Ya existe un usuario llamado \"" + username + "\".");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO usuarios (username, password, nombre, rol, activo) VALUES (?, ?, ?, ?, 1)")) {
                ps.setString(1, username);
                ps.setString(2, hashPassword(password));
                ps.setString(3, nombre);
                ps.setString(4, rol);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw errorBD(e);
        }
    }

    public void cambiarPassword(int idUsuario, String nuevaPassword) throws ReglaNegocioException {
        validarPassword(nuevaPassword);
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET password = ? WHERE id = ?")) {
            ps.setString(1, hashPassword(nuevaPassword));
            ps.setInt(2, idUsuario);
            if (ps.executeUpdate() == 0) throw new ReglaNegocioException("El usuario ya no existe.");
        } catch (SQLException e) {
            throw errorBD(e);
        }
    }

    /**
     * Activa o desactiva un usuario.
     * Reglas: nadie puede desactivarse a sí mismo ni desactivar al último ADMIN activo.
     */
    public void cambiarEstado(int idUsuario, boolean activo, int idUsuarioActual) throws ReglaNegocioException {
        try (Connection conn = Database.getConnection()) {
            Usuario objetivo = buscarPorId(conn, idUsuario);
            if (objetivo == null) throw new ReglaNegocioException("El usuario ya no existe.");

            if (!activo) {
                if (idUsuario == idUsuarioActual) {
                    throw new ReglaNegocioException("No puedes desactivar tu propio usuario.");
                }
                if (objetivo.isActivo() && "ADMIN".equals(objetivo.getRol()) && contarAdminsActivos(conn) <= 1) {
                    throw new ReglaNegocioException("No se puede desactivar al último administrador activo.");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET activo = ? WHERE id = ?")) {
                ps.setInt(1, activo ? 1 : 0);
                ps.setInt(2, idUsuario);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw errorBD(e);
        }
    }

    /**
     * Cambia el rol de un usuario.
     * Reglas: no puedes cambiar tu propio rol ni quitarle el rol ADMIN al último administrador activo.
     */
    public void cambiarRol(int idUsuario, String nuevoRol, int idUsuarioActual) throws ReglaNegocioException {
        validarRol(nuevoRol);
        try (Connection conn = Database.getConnection()) {
            Usuario objetivo = buscarPorId(conn, idUsuario);
            if (objetivo == null) throw new ReglaNegocioException("El usuario ya no existe.");

            if (idUsuario == idUsuarioActual) {
                throw new ReglaNegocioException("No puedes cambiar tu propio rol.");
            }
            if (objetivo.isActivo() && "ADMIN".equals(objetivo.getRol()) && !"ADMIN".equals(nuevoRol)
                    && contarAdminsActivos(conn) <= 1) {
                throw new ReglaNegocioException("No se puede quitar el rol al último administrador activo.");
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET rol = ? WHERE id = ?")) {
                ps.setString(1, nuevoRol);
                ps.setInt(2, idUsuario);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw errorBD(e);
        }
    }

    /** Busca un usuario por su id, o null si no existe. */
    public Usuario obtenerPorId(int id) {
        try (Connection conn = Database.getConnection()) {
            return buscarPorId(conn, id);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // =====================================================================
    // AUXILIARES
    // =====================================================================

    private static void validarPassword(String password) throws ReglaNegocioException {
        if (password == null || password.length() < MIN_LONGITUD_PASSWORD) {
            throw new ReglaNegocioException("La contraseña debe tener al menos " + MIN_LONGITUD_PASSWORD + " caracteres.");
        }
    }

    private static void validarRol(String rol) throws ReglaNegocioException {
        if (!Rol.esValido(rol)) {
            throw new ReglaNegocioException("El rol indicado no es válido.");
        }
    }

    private static int contarAdminsActivos(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM usuarios WHERE rol = 'ADMIN' AND activo = 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static Usuario buscarPorId(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM usuarios WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    private static Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("nombre"),
                rs.getString("rol"),
                rs.getInt("activo") == 1
        );
    }

    private static ReglaNegocioException errorBD(SQLException e) {
        e.printStackTrace();
        return new ReglaNegocioException("Error de base de datos: " + e.getMessage());
    }
}
