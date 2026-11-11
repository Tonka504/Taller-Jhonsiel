package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.Compatibilidad;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Acceso a datos de la compatibilidad de repuestos con vehículos. */
public class CompatibilidadDAO {

    public List<Compatibilidad> listarPorRepuesto(int repuestoId) {
        return consultar("SELECT * FROM compatibilidades WHERE repuesto_id = ? ORDER BY marca_vehiculo COLLATE NOCASE, modelo COLLATE NOCASE, anio_desde", repuestoId);
    }

    /** Todas las compatibilidades (para armar el buscador en memoria). */
    public List<Compatibilidad> listarTodas() {
        return consultar("SELECT * FROM compatibilidades ORDER BY repuesto_id, id", null);
    }

    private List<Compatibilidad> consultar(String sql, Integer parametro) {
        List<Compatibilidad> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (parametro != null) ps.setInt(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /** true si el producto ya tiene exactamente este vehículo (misma marca, modelo, años y motor). */
    public boolean existeIgual(Compatibilidad c) {
        String sql = "SELECT 1 FROM compatibilidades WHERE repuesto_id = ? AND marca_vehiculo = ? COLLATE NOCASE "
                   + "AND modelo = ? COLLATE NOCASE AND COALESCE(anio_desde, 0) = ? AND COALESCE(anio_hasta, 0) = ? "
                   + "AND COALESCE(motor, '') = ? COLLATE NOCASE";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, c.getRepuestoId());
            ps.setString(2, c.getMarcaVehiculo());
            ps.setString(3, c.getModelo());
            ps.setInt(4, c.getAnioDesde() == null ? 0 : c.getAnioDesde());
            ps.setInt(5, c.getAnioHasta() == null ? 0 : c.getAnioHasta());
            ps.setString(6, c.getMotor() == null ? "" : c.getMotor());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }

    /** Guarda la compatibilidad y devuelve su id, o -1 si falló. */
    public int agregar(Compatibilidad c) {
        String sql = "INSERT INTO compatibilidades (repuesto_id, marca_vehiculo, modelo, anio_desde, anio_hasta, motor, combustible, transmision, observaciones) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.getRepuestoId());
            ps.setString(2, c.getMarcaVehiculo());
            ps.setString(3, c.getModelo());
            setIntONull(ps, 4, c.getAnioDesde());
            setIntONull(ps, 5, c.getAnioHasta());
            ps.setString(6, c.getMotor());
            ps.setString(7, c.getCombustible());
            ps.setString(8, c.getTransmision());
            ps.setString(9, c.getObservaciones());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public boolean eliminar(int id) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM compatibilidades WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void setIntONull(PreparedStatement ps, int indice, Integer valor) throws SQLException {
        if (valor == null) ps.setNull(indice, Types.INTEGER);
        else ps.setInt(indice, valor);
    }

    private static Compatibilidad mapear(ResultSet rs) throws SQLException {
        int desde = rs.getInt("anio_desde");
        Integer anioDesde = rs.wasNull() ? null : desde;
        int hasta = rs.getInt("anio_hasta");
        Integer anioHasta = rs.wasNull() ? null : hasta;
        return new Compatibilidad(rs.getInt("id"), rs.getInt("repuesto_id"), rs.getString("marca_vehiculo"),
                rs.getString("modelo"), anioDesde, anioHasta, rs.getString("motor"), rs.getString("combustible"),
                rs.getString("transmision"), rs.getString("observaciones"));
    }
}
