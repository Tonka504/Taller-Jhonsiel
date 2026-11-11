package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.Cotizacion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Acceso a datos de cotizaciones. */
public class CotizacionDAO {

    public int crear(Cotizacion c) {
        String sql = "INSERT INTO cotizaciones (fecha, fecha_vencimiento, cliente_id, cliente_nombre, vendedor, subtotal, descuento, "
                   + "tipo_descuento, descuento_valor, impuesto, total, estado, observaciones) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?)";
        String sqlLinea = "INSERT INTO detalle_cotizaciones (cotizacion_id, repuesto_id, descripcion, cantidad, precio_unitario, "
                        + "descuento_pct, subtotal) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement pl = conn.prepareStatement(sqlLinea)) {
                ps.setString(1, c.getFecha());
                ps.setString(2, c.getFechaVencimiento());
                if (c.getClienteId() == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, c.getClienteId());
                ps.setString(4, c.getClienteNombre());
                ps.setString(5, c.getVendedor());
                ps.setDouble(6, c.getSubtotal());
                ps.setDouble(7, c.getDescuento());
                ps.setString(8, c.getTipoDescuento());
                ps.setDouble(9, c.getDescuentoValor());
                ps.setDouble(10, c.getImpuesto());
                ps.setDouble(11, c.getTotal());
                ps.setString(12, c.getObservaciones());
                ps.executeUpdate();
                int id;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("Sin id de cotización");
                    id = rs.getInt(1);
                }
                for (Cotizacion.Linea l : c.getLineas()) {
                    pl.setInt(1, id);
                    pl.setInt(2, l.repuestoId);
                    pl.setString(3, l.descripcion);
                    pl.setInt(4, l.cantidad);
                    pl.setDouble(5, l.precioUnitario);
                    pl.setDouble(6, l.descuentoPct);
                    pl.setDouble(7, l.subtotal);
                    pl.addBatch();
                }
                pl.executeBatch();
                conn.commit();
                return id;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /** Cotizaciones de un período, filtradas por texto (cliente, vendedor o número). */
    public List<Cotizacion> listar(String desde, String hasta, String texto) {
        StringBuilder sql = new StringBuilder("SELECT * FROM cotizaciones WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (desde != null) { sql.append(" AND fecha >= ?"); params.add(desde); }
        if (hasta != null) { sql.append(" AND fecha < date(?, '+1 day')"); params.add(hasta); }
        if (texto != null && !texto.isBlank()) {
            String like = "%" + texto.trim() + "%";
            sql.append(" AND (cliente_nombre LIKE ? OR vendedor LIKE ? OR CAST(id AS TEXT) = ?)");
            params.add(like);
            params.add(like);
            params.add(texto.trim().replaceAll("\\D", ""));
        }
        sql.append(" ORDER BY id DESC");
        List<Cotizacion> lista = new ArrayList<>();
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

    public Cotizacion obtener(int id) {
        try (Connection conn = Database.getConnection()) {
            Cotizacion c;
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM cotizaciones WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    c = mapear(rs);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT d.*, r.codigo FROM detalle_cotizaciones d LEFT JOIN repuestos r ON r.id = d.repuesto_id "
                  + "WHERE d.cotizacion_id = ? ORDER BY d.id")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Cotizacion.Linea l = new Cotizacion.Linea();
                        l.id = rs.getInt("id");
                        l.repuestoId = rs.getInt("repuesto_id");
                        l.codigo = rs.getString("codigo");
                        l.descripcion = rs.getString("descripcion");
                        l.cantidad = rs.getInt("cantidad");
                        l.precioUnitario = rs.getDouble("precio_unitario");
                        l.descuentoPct = rs.getDouble("descuento_pct");
                        l.subtotal = rs.getDouble("subtotal");
                        c.getLineas().add(l);
                    }
                }
            }
            return c;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean cambiarEstado(int id, String nuevo, Integer ventaId, String observacion) {
        String sql = "UPDATE cotizaciones SET estado = ?, venta_id = COALESCE(?, venta_id), "
                   + "observaciones = CASE WHEN ? IS NULL THEN observaciones ELSE TRIM(COALESCE(observaciones, '') || ' ' || ?) END "
                   + "WHERE id = ? AND estado = 'PENDIENTE'";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nuevo);
            if (ventaId == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, ventaId);
            ps.setString(3, observacion);
            ps.setString(4, observacion);
            ps.setInt(5, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static Cotizacion mapear(ResultSet rs) throws SQLException {
        Cotizacion c = new Cotizacion();
        c.setId(rs.getInt("id"));
        c.setFecha(rs.getString("fecha"));
        c.setFechaVencimiento(rs.getString("fecha_vencimiento"));
        int cli = rs.getInt("cliente_id");
        c.setClienteId(rs.wasNull() ? null : cli);
        c.setClienteNombre(rs.getString("cliente_nombre"));
        c.setVendedor(rs.getString("vendedor"));
        c.setSubtotal(rs.getDouble("subtotal"));
        c.setDescuento(rs.getDouble("descuento"));
        c.setTipoDescuento(rs.getString("tipo_descuento"));
        c.setDescuentoValor(rs.getDouble("descuento_valor"));
        c.setImpuesto(rs.getDouble("impuesto"));
        c.setTotal(rs.getDouble("total"));
        c.setEstado(rs.getString("estado"));
        int venta = rs.getInt("venta_id");
        c.setVentaId(rs.wasNull() ? null : venta);
        c.setObservaciones(rs.getString("observaciones"));
        return c;
    }
}
