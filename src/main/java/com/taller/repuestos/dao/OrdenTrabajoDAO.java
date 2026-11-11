package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.MovimientoInventario;
import com.taller.repuestos.model.OrdenTrabajo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Órdenes de trabajo del taller: encabezado, servicios, repuestos y consumo de inventario. */
public class OrdenTrabajoDAO {

    public static final int ERROR_STOCK = -2;

    private static final String SQL_BASE = """
        SELECT o.*, c.nombre AS cliente_nombre,
            TRIM(v.marca || ' ' || v.modelo || ' ' || COALESCE(v.anio, '') || CASE WHEN COALESCE(v.placa, '') <> '' THEN ' · ' || v.placa ELSE '' END) AS vehiculo,
            COALESCE((SELECT SUM(s.precio) FROM orden_servicios s WHERE s.orden_id = o.id), 0)
              + COALESCE((SELECT SUM(ROUND(r.cantidad * r.precio_unitario, 2)) FROM orden_repuestos r WHERE r.orden_id = o.id), 0) AS total
        FROM ordenes_trabajo o
        LEFT JOIN clientes c ON c.id = o.cliente_id
        LEFT JOIN vehiculos v ON v.id = o.vehiculo_id
    """;

    // ------------------------------------------------------------------
    // CONSULTAS
    // ------------------------------------------------------------------

    /** Órdenes filtradas; estado null = todas, "ABIERTAS" = las que siguen en el taller. */
    public List<OrdenTrabajo> listar(String estado, Integer vehiculoId, String texto, String desde, String hasta) {
        StringBuilder sql = new StringBuilder(SQL_BASE).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if ("ABIERTAS".equals(estado)) {
            sql.append(" AND o.estado IN ('RECIBIDO', 'DIAGNOSTICO', 'EN_REPARACION', 'LISTO')");
        } else if (estado != null) {
            sql.append(" AND o.estado = ?");
            params.add(estado);
        }
        if (vehiculoId != null) { sql.append(" AND o.vehiculo_id = ?"); params.add(vehiculoId); }
        if (desde != null) { sql.append(" AND o.fecha_ingreso >= ?"); params.add(desde); }
        if (hasta != null) { sql.append(" AND o.fecha_ingreso < date(?, '+1 day')"); params.add(hasta); }
        if (texto != null && !texto.trim().isEmpty()) {
            sql.append(" AND (c.nombre LIKE ? OR v.placa LIKE ? OR v.marca LIKE ? OR v.modelo LIKE ? OR o.mecanico LIKE ? OR CAST(o.id AS TEXT) = ?)");
            String like = "%" + texto.trim() + "%";
            for (int i = 0; i < 5; i++) params.add(like);
            params.add(texto.trim().replaceAll("\\D", ""));
        }
        sql.append(" ORDER BY o.id DESC");
        List<OrdenTrabajo> lista = new ArrayList<>();
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

    public OrdenTrabajo obtener(int id) {
        try (Connection conn = Database.getConnection()) {
            OrdenTrabajo o;
            try (PreparedStatement ps = conn.prepareStatement(SQL_BASE + " WHERE o.id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    o = mapear(rs);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM orden_servicios WHERE orden_id = ? ORDER BY id")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        OrdenTrabajo.Servicio s = new OrdenTrabajo.Servicio();
                        s.id = rs.getInt("id");
                        s.descripcion = rs.getString("descripcion");
                        s.mecanico = rs.getString("mecanico");
                        s.horas = rs.getDouble("horas");
                        s.precio = rs.getDouble("precio");
                        o.getServicios().add(s);
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT r.*, p.codigo FROM orden_repuestos r LEFT JOIN repuestos p ON p.id = r.repuesto_id WHERE r.orden_id = ? ORDER BY r.id")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        OrdenTrabajo.RepuestoUsado r = new OrdenTrabajo.RepuestoUsado();
                        r.id = rs.getInt("id");
                        r.repuestoId = rs.getInt("repuesto_id");
                        r.codigo = rs.getString("codigo");
                        r.descripcion = rs.getString("descripcion");
                        r.cantidad = rs.getInt("cantidad");
                        r.precioUnitario = rs.getDouble("precio_unitario");
                        r.costoUnitario = rs.getDouble("costo_unitario");
                        r.consumido = rs.getInt("consumido") == 1;
                        o.getRepuestos().add(r);
                    }
                }
            }
            return o;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Número de orden ligada a una factura, o null si la factura no viene del taller. */
    public Integer ordenDeVenta(int ventaId) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM ordenes_trabajo WHERE venta_id = ?")) {
            ps.setInt(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Cantidad de órdenes por estado (para el dashboard). */
    public Map<String, Integer> contarPorEstado() {
        Map<String, Integer> mapa = new LinkedHashMap<>();
        for (String e : OrdenTrabajo.ESTADOS) mapa.put(e, 0);
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT estado, COUNT(*) FROM ordenes_trabajo GROUP BY estado")) {
            while (rs.next()) mapa.put(rs.getString(1), rs.getInt(2));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mapa;
    }

    // ------------------------------------------------------------------
    // ENCABEZADO
    // ------------------------------------------------------------------

    public int crear(OrdenTrabajo o) {
        String sql = "INSERT INTO ordenes_trabajo (cliente_id, vehiculo_id, fecha_ingreso, fecha_promesa, estado, kilometraje, "
                   + "problema, diagnostico, observaciones, mecanico, usuario) VALUES (?, ?, ?, ?, 'RECIBIDO', ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, o.getClienteId());
            ps.setInt(2, o.getVehiculoId());
            ps.setString(3, o.getFechaIngreso());
            ps.setString(4, o.getFechaPromesa());
            if (o.getKilometraje() == null) ps.setNull(5, Types.INTEGER); else ps.setInt(5, o.getKilometraje());
            ps.setString(6, o.getProblema());
            ps.setString(7, o.getDiagnostico());
            ps.setString(8, o.getObservaciones());
            ps.setString(9, o.getMecanico());
            ps.setString(10, o.getUsuario());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /** Guarda diagnóstico, observaciones, mecánico, kilometraje y fecha prometida. */
    public boolean actualizarDatos(OrdenTrabajo o) {
        String sql = "UPDATE ordenes_trabajo SET fecha_promesa=?, kilometraje=?, problema=?, diagnostico=?, observaciones=?, mecanico=? WHERE id=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, o.getFechaPromesa());
            if (o.getKilometraje() == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, o.getKilometraje());
            ps.setString(3, o.getProblema());
            ps.setString(4, o.getDiagnostico());
            ps.setString(5, o.getObservaciones());
            ps.setString(6, o.getMecanico());
            ps.setInt(7, o.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean cambiarEstado(int id, String estado, String fechaEntrega) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE ordenes_trabajo SET estado = ?, fecha_entrega = COALESCE(?, fecha_entrega) WHERE id = ?")) {
            ps.setString(1, estado);
            ps.setString(2, fechaEntrega);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean vincularVenta(int ordenId, int ventaId, String fechaEntrega) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE ordenes_trabajo SET venta_id = ?, estado = 'ENTREGADO', fecha_entrega = ? WHERE id = ? AND venta_id IS NULL")) {
            ps.setInt(1, ventaId);
            ps.setString(2, fechaEntrega);
            ps.setInt(3, ordenId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ------------------------------------------------------------------
    // SERVICIOS Y REPUESTOS
    // ------------------------------------------------------------------

    public boolean agregarServicio(int ordenId, OrdenTrabajo.Servicio s) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO orden_servicios (orden_id, descripcion, mecanico, horas, precio) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, ordenId);
            ps.setString(2, s.descripcion);
            ps.setString(3, s.mecanico);
            ps.setDouble(4, s.horas);
            ps.setDouble(5, s.precio);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean eliminarServicio(int ordenId, int servicioId) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM orden_servicios WHERE id = ? AND orden_id = ?")) {
            ps.setInt(1, servicioId);
            ps.setInt(2, ordenId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean agregarRepuesto(int ordenId, OrdenTrabajo.RepuestoUsado r) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO orden_repuestos (orden_id, repuesto_id, descripcion, cantidad, precio_unitario, costo_unitario, consumido) "
                   + "VALUES (?, ?, ?, ?, ?, ?, 0)")) {
            ps.setInt(1, ordenId);
            ps.setInt(2, r.repuestoId);
            ps.setString(3, r.descripcion);
            ps.setInt(4, r.cantidad);
            ps.setDouble(5, r.precioUnitario);
            ps.setDouble(6, r.costoUnitario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Quita un repuesto de la orden; si ya se había consumido, regresa al inventario (kardex). */
    public boolean quitarRepuesto(int ordenId, int lineaId, String fecha, String usuario, String documento) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int repuestoId, cantidad;
                boolean consumido;
                double costo;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT repuesto_id, cantidad, consumido, costo_unitario FROM orden_repuestos WHERE id = ? AND orden_id = ?")) {
                    ps.setInt(1, lineaId);
                    ps.setInt(2, ordenId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                        repuestoId = rs.getInt(1);
                        cantidad = rs.getInt(2);
                        consumido = rs.getInt(3) == 1;
                        costo = rs.getDouble(4);
                    }
                }
                if (consumido) {
                    MovimientoInventarioDAO.moverStock(conn, repuestoId, cantidad, fecha, MovimientoInventario.Tipo.ORDEN_REINTEGRO,
                            costo, documento, "Repuesto quitado de la orden", usuario);
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM orden_repuestos WHERE id = ?")) {
                    ps.setInt(1, lineaId);
                    ps.executeUpdate();
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

    /**
     * Descuenta del inventario los repuestos de la orden que todavía no se habían consumido.
     * @return cantidad de líneas consumidas; ERROR_STOCK si alguno no alcanza (no se descuenta ninguno); -1 si falla
     */
    public int consumirRepuestos(int ordenId, String fecha, String usuario, String documento) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<int[]> lineas = new ArrayList<>();
                List<Double> costos = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, repuesto_id, cantidad, costo_unitario FROM orden_repuestos WHERE orden_id = ? AND consumido = 0")) {
                    ps.setInt(1, ordenId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            lineas.add(new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3)});
                            costos.add(rs.getDouble(4));
                        }
                    }
                }
                for (int i = 0; i < lineas.size(); i++) {
                    int[] l = lineas.get(i);
                    if (!MovimientoInventarioDAO.moverStock(conn, l[1], -l[2], fecha, MovimientoInventario.Tipo.ORDEN_TRABAJO,
                            costos.get(i), documento, "Consumo en orden de trabajo", usuario)) {
                        conn.rollback();
                        return ERROR_STOCK;
                    }
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE orden_repuestos SET consumido = 1 WHERE id = ?")) {
                        ps.setInt(1, l[0]);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
                return lineas.size();
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /** Cancela la orden y regresa al inventario los repuestos que ya se habían consumido. */
    public boolean cancelar(int ordenId, String fecha, String usuario, String documento, String motivo) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, repuesto_id, cantidad, costo_unitario FROM orden_repuestos WHERE orden_id = ? AND consumido = 1");
                     PreparedStatement upd = conn.prepareStatement("UPDATE orden_repuestos SET consumido = 0 WHERE id = ?")) {
                    ps.setInt(1, ordenId);
                    List<Object[]> filas = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) filas.add(new Object[]{rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getDouble(4)});
                    }
                    for (Object[] f : filas) {
                        MovimientoInventarioDAO.moverStock(conn, (int) f[1], (int) f[2], fecha, MovimientoInventario.Tipo.ORDEN_REINTEGRO,
                                (double) f[3], documento, "Orden cancelada: " + motivo, usuario);
                        upd.setInt(1, (int) f[0]);
                        upd.executeUpdate();
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE ordenes_trabajo SET estado = 'CANCELADO', observaciones = TRIM(COALESCE(observaciones, '') || ' [Cancelada: ' || ? || ']') "
                      + "WHERE id = ? AND venta_id IS NULL AND estado NOT IN ('ENTREGADO', 'CANCELADO')")) {
                    ps.setString(1, motivo);
                    ps.setInt(2, ordenId);
                    if (ps.executeUpdate() != 1) { conn.rollback(); return false; }
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

    private static OrdenTrabajo mapear(ResultSet rs) throws SQLException {
        OrdenTrabajo o = new OrdenTrabajo();
        o.setId(rs.getInt("id"));
        o.setClienteId(rs.getInt("cliente_id"));
        o.setClienteNombre(rs.getString("cliente_nombre"));
        o.setVehiculoId(rs.getInt("vehiculo_id"));
        o.setVehiculoDescripcion(rs.getString("vehiculo"));
        o.setFechaIngreso(rs.getString("fecha_ingreso"));
        o.setFechaPromesa(rs.getString("fecha_promesa"));
        o.setFechaEntrega(rs.getString("fecha_entrega"));
        o.setEstado(rs.getString("estado"));
        int km = rs.getInt("kilometraje");
        o.setKilometraje(rs.wasNull() ? null : km);
        o.setProblema(rs.getString("problema"));
        o.setDiagnostico(rs.getString("diagnostico"));
        o.setObservaciones(rs.getString("observaciones"));
        o.setMecanico(rs.getString("mecanico"));
        o.setUsuario(rs.getString("usuario"));
        int venta = rs.getInt("venta_id");
        o.setVentaId(rs.wasNull() ? null : venta);
        o.setTotalCalculado(rs.getDouble("total"));
        return o;
    }
}
