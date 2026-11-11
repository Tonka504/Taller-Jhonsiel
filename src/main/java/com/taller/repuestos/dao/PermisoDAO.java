package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Rol;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Permisos asignados a cada rol (tabla roles_permisos). */
public class PermisoDAO {

    private static final String MARCA_SEMBRADO = "permisos.sembrados";

    /** rol -> nombres de los permisos que tiene. */
    public Map<String, Set<String>> obtenerTodo() {
        Map<String, Set<String>> mapa = new HashMap<>();
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT rol, permiso FROM roles_permisos")) {
            while (rs.next()) {
                mapa.computeIfAbsent(rs.getString("rol"), k -> new HashSet<>()).add(rs.getString("permiso"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mapa;
    }

    /** Reemplaza todos los permisos de un rol por los indicados (en una sola transacción). */
    public boolean reemplazar(String rol, Collection<String> permisos) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM roles_permisos WHERE rol = ?");
                 PreparedStatement ins = conn.prepareStatement("INSERT INTO roles_permisos (rol, permiso) VALUES (?, ?)")) {
                del.setString(1, rol);
                del.executeUpdate();
                for (String p : permisos) {
                    ins.setString(1, rol);
                    ins.setString(2, p);
                    ins.addBatch();
                }
                ins.executeBatch();
                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static final String MARCA_VERSION = "permisos.version";

    /**
     * Carga los permisos que cada rol tiene por defecto.
     * La primera vez carga todos; en las actualizaciones carga solo los permisos nuevos, para no
     * pisar los cambios que el administrador ya hizo. La versión cargada queda en la configuración.
     */
    public static void sembrarPorDefecto(Connection conn) throws SQLException {
        int version = leerVersion(conn);
        if (version >= Permiso.versionActual()) return;

        boolean autoCommitOriginal = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT OR IGNORE INTO roles_permisos (rol, permiso) VALUES (?, ?)");
             PreparedStatement marca = conn.prepareStatement(
                "INSERT OR REPLACE INTO configuracion (clave, valor) VALUES (?, ?)")) {
            for (Rol rol : Rol.values()) {
                for (Permiso p : Permiso.predeterminados(rol)) {
                    if (p.getDesde() <= version) continue;
                    ins.setString(1, rol.name());
                    ins.setString(2, p.name());
                    ins.addBatch();
                }
            }
            ins.executeBatch();
            marca.setString(1, MARCA_VERSION);
            marca.setString(2, String.valueOf(Permiso.versionActual()));
            marca.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommitOriginal);
        }
    }

    /** 0 si nunca se cargaron permisos; 1 si solo estaba la carga inicial de la fase 1; si no, la versión guardada. */
    private static int leerVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT valor FROM configuracion WHERE clave = ?")) {
            ps.setString(1, MARCA_VERSION);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        return Integer.parseInt(rs.getString(1).trim());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
            }
            ps.setString(1, MARCA_SEMBRADO);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return 1;
            }
        }
        return 0;
    }
}
