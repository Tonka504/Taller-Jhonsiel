package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.Vehiculo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Acceso a datos de vehículos de clientes. */
public class VehiculoDAO {

    private static final String SQL_BASE = "SELECT v.*, c.nombre AS cliente_nombre FROM vehiculos v "
                                         + "LEFT JOIN clientes c ON c.id = v.cliente_id";

    /** Vehículos (de un cliente si clienteId no es null), filtrados por texto en marca, modelo, placa, VIN o cliente. */
    public List<Vehiculo> buscar(Integer clienteId, String texto, boolean soloActivos) {
        StringBuilder sql = new StringBuilder(SQL_BASE).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (clienteId != null) { sql.append(" AND v.cliente_id = ?"); params.add(clienteId); }
        if (soloActivos) sql.append(" AND v.activo = 1");
        if (texto != null && !texto.trim().isEmpty()) {
            for (String palabra : texto.trim().split("\\s+")) {
                sql.append(" AND (v.marca || ' ' || v.modelo || ' ' || COALESCE(v.anio, '') || ' ' || COALESCE(v.placa, '') || ' ' "
                         + "|| COALESCE(v.vin, '') || ' ' || COALESCE(v.motor, '') || ' ' || COALESCE(c.nombre, '')) LIKE ?");
                params.add("%" + palabra + "%");
            }
        }
        sql.append(" ORDER BY c.nombre COLLATE NOCASE, v.marca, v.modelo");
        List<Vehiculo> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public Vehiculo obtener(int id) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_BASE + " WHERE v.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapear(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** true si otro vehículo ya tiene esa placa o ese VIN. */
    public boolean existe(String columna, String valor, int excluirId) {
        if (!"placa".equals(columna) && !"vin".equals(columna)) throw new IllegalArgumentException(columna);
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM vehiculos WHERE " + columna + " = ? COLLATE NOCASE AND id <> ?")) {
            ps.setString(1, valor);
            ps.setInt(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }

    public int crear(Vehiculo v) {
        String sql = "INSERT INTO vehiculos (cliente_id, marca, modelo, anio, placa, vin, motor, cilindrada, combustible, "
                   + "transmision, color, kilometraje, observaciones, activo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            llenar(ps, v);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public boolean actualizar(Vehiculo v) {
        String sql = "UPDATE vehiculos SET cliente_id=?, marca=?, modelo=?, anio=?, placa=?, vin=?, motor=?, cilindrada=?, "
                   + "combustible=?, transmision=?, color=?, kilometraje=?, observaciones=?, activo=? WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            llenar(ps, v);
            ps.setInt(15, v.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Actualiza el kilometraje solo si el nuevo es mayor (se llama al recibir el vehículo en el taller). */
    public void actualizarKilometraje(int id, int km) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE vehiculos SET kilometraje = ? WHERE id = ? AND (kilometraje IS NULL OR kilometraje < ?)")) {
            ps.setInt(1, km);
            ps.setInt(2, id);
            ps.setInt(3, km);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void llenar(PreparedStatement ps, Vehiculo v) throws SQLException {
        ps.setInt(1, v.getClienteId());
        ps.setString(2, v.getMarca());
        ps.setString(3, v.getModelo());
        if (v.getAnio() == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, v.getAnio());
        ps.setString(5, v.getPlaca());
        ps.setString(6, v.getVin());
        ps.setString(7, v.getMotor());
        ps.setString(8, v.getCilindrada());
        ps.setString(9, v.getCombustible());
        ps.setString(10, v.getTransmision());
        ps.setString(11, v.getColor());
        if (v.getKilometraje() == null) ps.setNull(12, Types.INTEGER); else ps.setInt(12, v.getKilometraje());
        ps.setString(13, v.getObservaciones());
        ps.setInt(14, v.isActivo() ? 1 : 0);
    }

    private static Vehiculo mapear(ResultSet rs) throws SQLException {
        Vehiculo v = new Vehiculo();
        v.setId(rs.getInt("id"));
        v.setClienteId(rs.getInt("cliente_id"));
        v.setClienteNombre(rs.getString("cliente_nombre"));
        v.setMarca(rs.getString("marca"));
        v.setModelo(rs.getString("modelo"));
        int anio = rs.getInt("anio");
        v.setAnio(rs.wasNull() ? null : anio);
        v.setPlaca(rs.getString("placa"));
        v.setVin(rs.getString("vin"));
        v.setMotor(rs.getString("motor"));
        v.setCilindrada(rs.getString("cilindrada"));
        v.setCombustible(rs.getString("combustible"));
        v.setTransmision(rs.getString("transmision"));
        v.setColor(rs.getString("color"));
        int km = rs.getInt("kilometraje");
        v.setKilometraje(rs.wasNull() ? null : km);
        v.setObservaciones(rs.getString("observaciones"));
        v.setActivo(rs.getInt("activo") == 1);
        return v;
    }
}
