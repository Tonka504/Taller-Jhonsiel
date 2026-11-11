package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.Proveedor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Acceso a datos de proveedores. */
public class ProveedorDAO {

    /** Producto que un proveedor ha suministrado, con lo recibido y el último costo. */
    public static class ProductoSuministrado {
        public final String codigo;
        public final String descripcion;
        public final int unidades;
        public final double ultimoCosto;
        public final String ultimaFecha;

        public ProductoSuministrado(String codigo, String descripcion, int unidades, double ultimoCosto, String ultimaFecha) {
            this.codigo = codigo;
            this.descripcion = descripcion;
            this.unidades = unidades;
            this.ultimoCosto = ultimoCosto;
            this.ultimaFecha = ultimaFecha;
        }

        public String getCodigo() { return codigo; }
        public String getDescripcion() { return descripcion; }
        public int getUnidades() { return unidades; }
        public double getUltimoCosto() { return ultimoCosto; }
        public String getUltimaFecha() { return ultimaFecha; }
    }

    // Total comprado (sin anuladas) y saldo de lo ya recibido menos pagos y devoluciones
    private static final String SQL_LISTAR = """
        SELECT p.*,
            (SELECT COUNT(*) FROM compras c WHERE c.proveedor_id = p.id AND c.estado <> 'ANULADA') AS cantidad_compras,
            (SELECT COALESCE(SUM(c.total), 0) FROM compras c WHERE c.proveedor_id = p.id AND c.estado IN ('PARCIAL', 'RECIBIDA')) AS total_comprado,
            (SELECT COALESCE(SUM(c.total
                    - COALESCE((SELECT SUM(pp.monto) FROM pagos_proveedor pp WHERE pp.compra_id = c.id), 0)
                    - COALESCE((SELECT SUM(dp.total) FROM devoluciones_proveedor dp WHERE dp.compra_id = c.id), 0)), 0)
             FROM compras c WHERE c.proveedor_id = p.id AND c.estado IN ('PARCIAL', 'RECIBIDA')) AS saldo
        FROM proveedores p
    """;

    public List<Proveedor> obtenerTodos() {
        List<Proveedor> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(SQL_LISTAR + " ORDER BY p.nombre COLLATE NOCASE")) {
            while (rs.next()) lista.add(mapear(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public Proveedor obtenerPorId(int id) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LISTAR + " WHERE p.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapear(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean existeNombre(String nombre, int excluirId) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM proveedores WHERE nombre = ? COLLATE NOCASE AND id <> ?")) {
            ps.setString(1, nombre);
            ps.setInt(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }

    public int crear(Proveedor p) {
        String sql = "INSERT INTO proveedores (nombre, empresa, identificacion_fiscal, telefono, direccion, correo, contacto, activo, observaciones) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            llenar(ps, p);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Guarda los cambios. Si cambió el nombre, también se actualiza en los productos que lo tenían
     * como proveedor (en la misma transacción).
     */
    public boolean actualizar(Proveedor p, String nombreAnterior) {
        String sql = "UPDATE proveedores SET nombre=?, empresa=?, identificacion_fiscal=?, telefono=?, direccion=?, correo=?, "
                   + "contacto=?, activo=?, observaciones=? WHERE id=?";
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 PreparedStatement prod = conn.prepareStatement("UPDATE repuestos SET proveedor = ? WHERE proveedor = ? COLLATE NOCASE")) {
                llenar(ps, p);
                ps.setInt(10, p.getId());
                ps.executeUpdate();
                if (nombreAnterior != null && !nombreAnterior.equals(p.getNombre())) {
                    prod.setString(1, p.getNombre());
                    prod.setString(2, nombreAnterior);
                    prod.executeUpdate();
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

    /** Si no existe un proveedor con ese nombre, lo crea (se usa al guardar un producto con un proveedor nuevo). */
    public boolean asegurar(String nombre) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO proveedores (nombre) VALUES (?)")) {
            ps.setString(1, nombre);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Nombres de proveedores activos, para combos. */
    public List<String> nombresActivos() {
        List<String> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre FROM proveedores WHERE activo = 1 ORDER BY nombre COLLATE NOCASE")) {
            while (rs.next()) lista.add(rs.getString(1));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public List<ProductoSuministrado> productosSuministrados(int proveedorId) {
        List<ProductoSuministrado> lista = new ArrayList<>();
        String sql = """
            SELECT r.codigo, r.descripcion, SUM(d.cantidad_recibida) AS unidades, MAX(c.fecha) AS ultima,
                (SELECT ROUND((d2.cantidad * d2.costo_unitario - d2.descuento) / d2.cantidad, 2)
                   FROM detalle_compras d2 JOIN compras c2 ON c2.id = d2.compra_id
                  WHERE d2.repuesto_id = d.repuesto_id AND c2.proveedor_id = c.proveedor_id AND c2.estado <> 'ANULADA'
                  ORDER BY c2.fecha DESC, d2.id DESC LIMIT 1) AS ultimo_costo
            FROM detalle_compras d
            JOIN compras c ON c.id = d.compra_id
            JOIN repuestos r ON r.id = d.repuesto_id
            WHERE c.proveedor_id = ? AND c.estado <> 'ANULADA'
            GROUP BY d.repuesto_id
            ORDER BY unidades DESC
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, proveedorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new ProductoSuministrado(rs.getString("codigo"), rs.getString("descripcion"),
                            rs.getInt("unidades"), rs.getDouble("ultimo_costo"), rs.getString("ultima")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /** Crea un proveedor por cada nombre distinto escrito en el campo "proveedor" de los productos existentes. */
    public static void migrarDesdeProductos(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT OR IGNORE INTO proveedores (nombre) "
                    + "SELECT DISTINCT TRIM(proveedor) FROM repuestos WHERE proveedor IS NOT NULL AND TRIM(proveedor) <> ''");
        }
    }

    private static void llenar(PreparedStatement ps, Proveedor p) throws SQLException {
        ps.setString(1, p.getNombre());
        ps.setString(2, p.getEmpresa());
        ps.setString(3, p.getIdentificacionFiscal());
        ps.setString(4, p.getTelefono());
        ps.setString(5, p.getDireccion());
        ps.setString(6, p.getCorreo());
        ps.setString(7, p.getContacto());
        ps.setInt(8, p.isActivo() ? 1 : 0);
        ps.setString(9, p.getObservaciones());
    }

    private static Proveedor mapear(ResultSet rs) throws SQLException {
        Proveedor p = new Proveedor();
        p.setId(rs.getInt("id"));
        p.setNombre(rs.getString("nombre"));
        p.setEmpresa(rs.getString("empresa"));
        p.setIdentificacionFiscal(rs.getString("identificacion_fiscal"));
        p.setTelefono(rs.getString("telefono"));
        p.setDireccion(rs.getString("direccion"));
        p.setCorreo(rs.getString("correo"));
        p.setContacto(rs.getString("contacto"));
        p.setActivo(rs.getInt("activo") == 1);
        p.setObservaciones(rs.getString("observaciones"));
        p.setCantidadCompras(rs.getInt("cantidad_compras"));
        p.setTotalComprado(rs.getDouble("total_comprado"));
        p.setSaldoPendiente(Math.max(0, Math.round(rs.getDouble("saldo") * 100.0) / 100.0));
        return p;
    }
}
