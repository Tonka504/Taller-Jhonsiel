package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.Devolucion;
import com.taller.repuestos.model.MovimientoCaja;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/** Acceso a datos de devoluciones. Cada devolución se guarda en una sola transacción. */
public class DevolucionDAO {

    /** Código que devuelve registrar() cuando se intenta devolver más unidades de las vendidas. */
    public static final int ERROR_CANTIDAD = -2;

    /**
     * Guarda la devolución, reintegra las piezas al inventario y, si parte se devuelve en efectivo,
     * registra la salida de dinero en la caja. Todo o nada.
     *
     * @param numerador convierte el id de la devolución en su número de documento (ej: DEV-000003)
     * @return id de la devolución; ERROR_CANTIDAD si alguna cantidad excede lo vendido; -1 si falló
     */
    public int registrar(Devolucion d, IntFunction<String> numerador) {
        String sqlDev = "INSERT INTO devoluciones (venta_id, fecha, usuario, motivo, monto_total, monto_saldo, forma_reembolso, caja_id) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlLinea = "INSERT INTO detalle_devoluciones (devolucion_id, venta_detalle_id, repuesto_id, descripcion, cantidad, monto) "
                        + "VALUES (?, ?, ?, ?, ?, ?)";
        String sqlVendida = "SELECT cantidad FROM venta_detalle WHERE id = ? AND venta_id = ?";
        String sqlYaDevuelta = "SELECT COALESCE(SUM(cantidad), 0) FROM detalle_devoluciones WHERE venta_detalle_id = ?";
        String sqlMovimiento = "INSERT INTO movimientos_caja (caja_id, fecha, usuario, tipo, monto, motivo, documento) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Comprobar de nuevo, dentro de la transacción, que no se devuelva más de lo vendido
                try (PreparedStatement vendida = conn.prepareStatement(sqlVendida);
                     PreparedStatement yaDev = conn.prepareStatement(sqlYaDevuelta)) {
                    for (Devolucion.Linea l : d.getLineas()) {
                        vendida.setInt(1, l.detalleId);
                        vendida.setInt(2, d.getVentaId());
                        int cantidadVendida;
                        try (ResultSet rs = vendida.executeQuery()) {
                            if (!rs.next()) {
                                conn.rollback();
                                return -1;
                            }
                            cantidadVendida = rs.getInt(1);
                        }
                        yaDev.setInt(1, l.detalleId);
                        int cantidadDevuelta;
                        try (ResultSet rs = yaDev.executeQuery()) {
                            cantidadDevuelta = rs.next() ? rs.getInt(1) : 0;
                        }
                        if (l.cantidad <= 0 || cantidadDevuelta + l.cantidad > cantidadVendida) {
                            conn.rollback();
                            return ERROR_CANTIDAD;
                        }
                    }
                }

                int devId;
                try (PreparedStatement ps = conn.prepareStatement(sqlDev, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, d.getVentaId());
                    ps.setString(2, d.getFecha());
                    ps.setString(3, d.getUsuario());
                    ps.setString(4, d.getMotivo());
                    ps.setDouble(5, d.getMontoTotal());
                    ps.setDouble(6, d.getMontoSaldo());
                    ps.setString(7, d.getFormaReembolso());
                    if (d.getCajaId() == null) {
                        ps.setNull(8, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(8, d.getCajaId());
                    }
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No se obtuvo el id de la devolución");
                        devId = keys.getInt(1);
                    }
                }

                String docDevolucion = numerador.apply(devId);
                try (PreparedStatement linea = conn.prepareStatement(sqlLinea)) {
                    for (Devolucion.Linea l : d.getLineas()) {
                        linea.setInt(1, devId);
                        linea.setInt(2, l.detalleId);
                        linea.setInt(3, l.repuestoId);
                        linea.setString(4, l.descripcion);
                        linea.setInt(5, l.cantidad);
                        linea.setDouble(6, l.monto);
                        linea.addBatch();
                    }
                    linea.executeBatch();
                }
                for (Devolucion.Linea l : d.getLineas()) {
                    if (l.repuestoId <= 0) continue; // servicios: no vuelven al inventario
                    MovimientoInventarioDAO.moverStock(conn, l.repuestoId, l.cantidad, d.getFecha(),
                            com.taller.repuestos.model.MovimientoInventario.Tipo.DEVOLUCION_CLIENTE, null,
                            docDevolucion, d.getMotivo(), d.getUsuario());
                }

                // Dinero que sale de la caja
                boolean efectivo = Devolucion.EFECTIVO.equals(d.getFormaReembolso()) && d.getMontoReembolsado() > 0;
                if (efectivo && d.getCajaId() != null) {
                    String documento = numerador.apply(devId);
                    try (PreparedStatement ps = conn.prepareStatement(sqlMovimiento)) {
                        ps.setInt(1, d.getCajaId());
                        ps.setString(2, d.getFecha());
                        ps.setString(3, d.getUsuario());
                        ps.setString(4, MovimientoCaja.Tipo.DEVOLUCION.name());
                        ps.setDouble(5, d.getMontoReembolsado());
                        ps.setString(6, "Devolución " + documento);
                        ps.setString(7, documento);
                        ps.executeUpdate();
                    }
                }

                conn.commit();
                return devId;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public List<Devolucion> obtenerPorVenta(int ventaId) {
        List<Devolucion> lista = new ArrayList<>();
        String sql = "SELECT * FROM devoluciones WHERE venta_id = ? ORDER BY id";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
            for (Devolucion d : lista) cargarLineas(conn, d);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public Devolucion obtenerPorId(int id) {
        String sql = "SELECT * FROM devoluciones WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Devolucion d = mapear(rs);
                    cargarLineas(conn, d);
                    return d;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void cargarLineas(Connection conn, Devolucion d) throws SQLException {
        String sql = "SELECT * FROM detalle_devoluciones WHERE devolucion_id = ? ORDER BY id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, d.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    d.getLineas().add(new Devolucion.Linea(
                            rs.getInt("venta_detalle_id"), rs.getInt("repuesto_id"),
                            rs.getString("descripcion"), rs.getInt("cantidad"), rs.getDouble("monto")));
                }
            }
        }
    }

    private static Devolucion mapear(ResultSet rs) throws SQLException {
        Devolucion d = new Devolucion();
        d.setId(rs.getInt("id"));
        d.setVentaId(rs.getInt("venta_id"));
        d.setFecha(rs.getString("fecha"));
        d.setUsuario(rs.getString("usuario"));
        d.setMotivo(rs.getString("motivo"));
        d.setMontoTotal(rs.getDouble("monto_total"));
        d.setMontoSaldo(rs.getDouble("monto_saldo"));
        d.setFormaReembolso(rs.getString("forma_reembolso"));
        int caja = rs.getInt("caja_id");
        d.setCajaId(rs.wasNull() ? null : caja);
        return d;
    }
}
