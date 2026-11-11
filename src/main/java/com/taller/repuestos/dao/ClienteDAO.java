package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.Cliente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Acceso a datos de clientes. El saldo pendiente se calcula con las ventas a crédito de cada cliente. */
public class ClienteDAO {

    /**
     * Saldo de un cliente = ventas a crédito emitidas, menos lo abonado, menos lo devuelto contra la deuda.
     * El parámetro es el nombre del método de pago "Crédito".
     */
    private static final String SELECT_CON_SALDO = """
        SELECT c.*,
            COALESCE((
                SELECT SUM(v.total
                    - COALESCE((SELECT SUM(p.monto) FROM pagos p WHERE p.venta_id = v.id AND p.tipo = 'ABONO'), 0)
                    - COALESCE((SELECT SUM(d.monto_saldo) FROM devoluciones d WHERE d.venta_id = v.id), 0))
                FROM ventas v
                WHERE v.cliente_id = c.id AND v.metodo_pago = ? AND v.estado = 'EMITIDA'
            ), 0) AS saldo
        FROM clientes c
    """;

    public List<Cliente> obtenerTodos() {
        List<Cliente> lista = new ArrayList<>();
        String sql = SELECT_CON_SALDO + " ORDER BY c.activo DESC, c.nombre COLLATE NOCASE";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, VentaDAO.METODO_CREDITO);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public Cliente obtenerPorId(int id) {
        String sql = SELECT_CON_SALDO + " WHERE c.id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, VentaDAO.METODO_CREDITO);
            ps.setInt(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapear(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** true si ya hay otro cliente con esa identificación (se ignora el cliente con id excluirId). */
    public boolean existeIdentificacion(String identificacion, int excluirId) {
        String sql = "SELECT 1 FROM clientes WHERE identificacion = ? COLLATE NOCASE AND id <> ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identificacion);
            ps.setInt(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true; // ante la duda no se permite duplicar
    }

    /** Guarda un cliente nuevo y devuelve su id, o -1 si falló. */
    public int crear(Cliente c) {
        String sql = "INSERT INTO clientes (nombre, identificacion, telefono, direccion, correo, tipo, activo, limite_credito, notas) "
                   + "VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getNombre());
            ps.setString(2, c.getIdentificacion());
            ps.setString(3, c.getTelefono());
            ps.setString(4, c.getDireccion());
            ps.setString(5, c.getCorreo());
            ps.setString(6, c.getTipo());
            ps.setDouble(7, c.getLimiteCredito());
            ps.setString(8, c.getNotas());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public boolean actualizar(Cliente c) {
        String sql = "UPDATE clientes SET nombre = ?, identificacion = ?, telefono = ?, direccion = ?, correo = ?, "
                   + "tipo = ?, limite_credito = ?, notas = ? WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getNombre());
            ps.setString(2, c.getIdentificacion());
            ps.setString(3, c.getTelefono());
            ps.setString(4, c.getDireccion());
            ps.setString(5, c.getCorreo());
            ps.setString(6, c.getTipo());
            ps.setDouble(7, c.getLimiteCredito());
            ps.setString(8, c.getNotas());
            ps.setInt(9, c.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean cambiarEstado(int id, boolean activo) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE clientes SET activo = ? WHERE id = ?")) {
            ps.setInt(1, activo ? 1 : 0);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static Cliente mapear(ResultSet rs) throws SQLException {
        Cliente c = new Cliente(
                rs.getInt("id"),
                rs.getString("nombre"),
                rs.getString("identificacion"),
                rs.getString("telefono"),
                rs.getString("direccion"),
                rs.getString("correo"),
                rs.getString("tipo"),
                rs.getInt("activo") == 1,
                rs.getDouble("limite_credito"),
                rs.getString("notas"));
        c.setSaldoPendiente(Math.round(rs.getDouble("saldo") * 100.0) / 100.0);
        return c;
    }
}
