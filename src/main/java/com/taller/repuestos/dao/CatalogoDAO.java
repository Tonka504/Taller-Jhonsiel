package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Acceso a datos del catálogo: categorías y marcas. */
public class CatalogoDAO {

    /** Qué catálogo se está usando. El nombre de la tabla sale de aquí, nunca de texto escrito por el usuario. */
    public enum Tipo {
        CATEGORIA("categorias"),
        MARCA("marcas");

        private final String tabla;

        Tipo(String tabla) { this.tabla = tabla; }

        String tabla() { return tabla; }
    }

    /** Una categoría o marca, con la cantidad de veces que se usa. */
    public static class Item {
        private final int id;
        private final String nombre;
        private final boolean activo;
        private final int usos;

        public Item(int id, String nombre, boolean activo, int usos) {
            this.id = id;
            this.nombre = nombre;
            this.activo = activo;
            this.usos = usos;
        }

        public int getId() { return id; }
        public String getNombre() { return nombre; }
        public boolean isActivo() { return activo; }
        public int getUsos() { return usos; }
        public String getEstado() { return activo ? "Activo" : "Inactivo"; }
    }

    private static final List<String> CATEGORIAS_BASE = List.of("Motor", "Frenos", "Suspensión", "Dirección",
            "Sistema eléctrico", "Transmisión", "Refrigeración", "Lubricantes", "Filtros", "Carrocería",
            "Accesorios", "Otros");

    private static final List<String> MARCAS_BASE = List.of("Toyota", "Nissan", "Ford", "Chevrolet", "Hyundai",
            "Kia", "Honda", "Mazda", "Mitsubishi", "Volkswagen");

    /** Cuántos productos (y, en marcas, vehículos compatibles) usan cada elemento. */
    private static String sqlListar(Tipo tipo) {
        if (tipo == Tipo.CATEGORIA) {
            return "SELECT c.id, c.nombre, c.activo, "
                 + "(SELECT COUNT(*) FROM repuestos r WHERE r.categoria = c.nombre COLLATE NOCASE) AS usos "
                 + "FROM categorias c ORDER BY c.nombre COLLATE NOCASE";
        }
        return "SELECT c.id, c.nombre, c.activo, "
             + "(SELECT COUNT(*) FROM repuestos r WHERE r.marca = c.nombre COLLATE NOCASE) "
             + "+ (SELECT COUNT(*) FROM compatibilidades k WHERE k.marca_vehiculo = c.nombre COLLATE NOCASE) AS usos "
             + "FROM marcas c ORDER BY c.nombre COLLATE NOCASE";
    }

    public List<Item> listar(Tipo tipo) {
        List<Item> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlListar(tipo))) {
            while (rs.next()) {
                lista.add(new Item(rs.getInt("id"), rs.getString("nombre"), rs.getInt("activo") == 1, rs.getInt("usos")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /** Nombres de los elementos activos, para los combos del sistema. */
    public List<String> nombresActivos(Tipo tipo) {
        List<String> lista = new ArrayList<>();
        String sql = "SELECT nombre FROM " + tipo.tabla() + " WHERE activo = 1 ORDER BY nombre COLLATE NOCASE";
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) lista.add(rs.getString(1));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public boolean existe(Tipo tipo, String nombre) {
        String sql = "SELECT 1 FROM " + tipo.tabla() + " WHERE nombre = ? COLLATE NOCASE";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true; // ante la duda no se crea un duplicado
    }

    /** Crea el elemento y devuelve su id, o -1 si no se pudo (por ejemplo, si ya existe). */
    public int crear(Tipo tipo, String nombre) {
        String sql = "INSERT INTO " + tipo.tabla() + " (nombre, activo) VALUES (?, 1)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Cambia el nombre y actualiza todo lo que lo usaba (productos y, en marcas, vehículos compatibles),
     * en una sola transacción.
     */
    public boolean renombrar(Tipo tipo, int id, String nombreAnterior, String nombreNuevo) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE " + tipo.tabla() + " SET nombre = ? WHERE id = ?")) {
                    ps.setString(1, nombreNuevo);
                    ps.setInt(2, id);
                    if (ps.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }
                if (tipo == Tipo.CATEGORIA) {
                    cascada(conn, "UPDATE repuestos SET categoria = ? WHERE categoria = ? COLLATE NOCASE", nombreNuevo, nombreAnterior);
                } else {
                    cascada(conn, "UPDATE repuestos SET marca = ? WHERE marca = ? COLLATE NOCASE", nombreNuevo, nombreAnterior);
                    cascada(conn, "UPDATE compatibilidades SET marca_vehiculo = ? WHERE marca_vehiculo = ? COLLATE NOCASE", nombreNuevo, nombreAnterior);
                }
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

    private static void cascada(Connection conn, String sql, String nuevo, String anterior) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevo);
            ps.setString(2, anterior);
            ps.executeUpdate();
        }
    }

    public boolean cambiarEstado(Tipo tipo, int id, boolean activo) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE " + tipo.tabla() + " SET activo = ? WHERE id = ?")) {
            ps.setInt(1, activo ? 1 : 0);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Borra el elemento solo si ningún producto (ni vehículo compatible) lo usa. */
    public boolean eliminarSiNoSeUsa(Tipo tipo, int id) {
        for (Item item : listar(tipo)) {
            if (item.getId() == id && item.getUsos() > 0) return false;
        }
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM " + tipo.tabla() + " WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Primera carga: las categorías base y las marcas que ya usaban tus productos (más unas marcas comunes).
     * Solo corre si el catálogo está vacío, así no vuelve a agregar lo que borres después.
     */
    public static void sembrar(Connection conn) throws SQLException {
        if (estaVacia(conn, "categorias")) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO categorias (nombre, activo) VALUES (?, 1)")) {
                for (String c : CATEGORIAS_BASE) {
                    ps.setString(1, c);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        if (estaVacia(conn, "marcas")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT OR IGNORE INTO marcas (nombre, activo) "
                        + "SELECT DISTINCT TRIM(marca), 1 FROM repuestos WHERE marca IS NOT NULL AND TRIM(marca) <> ''");
            }
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO marcas (nombre, activo) VALUES (?, 1)")) {
                for (String m : MARCAS_BASE) {
                    ps.setString(1, m);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    private static boolean estaVacia(Connection conn, String tabla) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }
}
