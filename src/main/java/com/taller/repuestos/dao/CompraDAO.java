package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.Compra;
import com.taller.repuestos.model.MovimientoCaja;
import com.taller.repuestos.model.MovimientoInventario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compras a proveedores: crear la orden, recibir mercadería (aumenta inventario y actualiza costos),
 * pagar, devolver al proveedor y anular. Cada operación es una transacción: todo o nada.
 */
public class CompraDAO {

    public static final int ERROR_CANTIDAD = -2;
    public static final int ERROR_STOCK = -3;

    /** Pago hecho a un proveedor. */
    public static class Pago {
        public final String fecha;
        public final double monto;
        public final String metodo;
        public final String referencia;
        public final String usuario;

        Pago(String fecha, double monto, String metodo, String referencia, String usuario) {
            this.fecha = fecha;
            this.monto = monto;
            this.metodo = metodo;
            this.referencia = referencia;
            this.usuario = usuario;
        }

        public String getFecha() { return fecha; }
        public double getMonto() { return monto; }
        public String getMetodo() { return metodo; }
        public String getReferencia() { return referencia; }
        public String getUsuario() { return usuario; }
    }

    private static final String SQL_ENCABEZADO = """
        SELECT c.*, p.nombre AS proveedor_nombre,
            COALESCE((SELECT SUM(pp.monto) FROM pagos_proveedor pp WHERE pp.compra_id = c.id), 0) AS pagado,
            COALESCE((SELECT SUM(dp.total) FROM devoluciones_proveedor dp WHERE dp.compra_id = c.id), 0) AS devuelto
        FROM compras c LEFT JOIN proveedores p ON p.id = c.proveedor_id
    """;

    // ------------------------------------------------------------------
    // CREAR
    // ------------------------------------------------------------------

    public int crear(Compra c) {
        String sqlCompra = "INSERT INTO compras (proveedor_id, fecha, documento_proveedor, estado, condicion_pago, fecha_vencimiento, "
                         + "subtotal, descuento, impuesto_pct, impuesto, total, usuario, observaciones) "
                         + "VALUES (?, ?, ?, 'PENDIENTE', ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlLinea = "INSERT INTO detalle_compras (compra_id, repuesto_id, descripcion, cantidad, costo_unitario, descuento, subtotal) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sqlCompra, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement pl = conn.prepareStatement(sqlLinea)) {
                ps.setInt(1, c.getProveedorId());
                ps.setString(2, c.getFecha());
                ps.setString(3, c.getDocumentoProveedor());
                ps.setString(4, c.getCondicionPago());
                ps.setString(5, c.getFechaVencimiento());
                ps.setDouble(6, c.getSubtotal());
                ps.setDouble(7, c.getDescuento());
                ps.setDouble(8, c.getImpuestoPct());
                ps.setDouble(9, c.getImpuesto());
                ps.setDouble(10, c.getTotal());
                ps.setString(11, c.getUsuario());
                ps.setString(12, c.getObservaciones());
                ps.executeUpdate();
                int id;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("Sin id de compra");
                    id = rs.getInt(1);
                }
                for (Compra.Linea l : c.getLineas()) {
                    pl.setInt(1, id);
                    pl.setInt(2, l.repuestoId);
                    pl.setString(3, l.descripcion);
                    pl.setInt(4, l.cantidad);
                    pl.setDouble(5, l.costoUnitario);
                    pl.setDouble(6, l.descuento);
                    pl.setDouble(7, l.getSubtotal());
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

    // ------------------------------------------------------------------
    // CONSULTAS
    // ------------------------------------------------------------------

    public List<Compra> listar(Integer proveedorId, String estado, String desde, String hasta) {
        StringBuilder sql = new StringBuilder(SQL_ENCABEZADO).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (proveedorId != null) { sql.append(" AND c.proveedor_id = ?"); params.add(proveedorId); }
        if (estado != null) { sql.append(" AND c.estado = ?"); params.add(estado); }
        if (desde != null) { sql.append(" AND c.fecha >= ?"); params.add(desde); }
        if (hasta != null) { sql.append(" AND c.fecha < date(?, '+1 day')"); params.add(hasta); }
        sql.append(" ORDER BY c.id DESC");
        List<Compra> lista = new ArrayList<>();
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

    /** La compra completa, con sus líneas (recibido y devuelto por línea), o null si no existe. */
    public Compra obtener(int id) {
        String sqlLineas = """
            SELECT d.*, r.codigo,
                COALESCE((SELECT SUM(x.cantidad) FROM detalle_devoluciones_proveedor x WHERE x.detalle_compra_id = d.id), 0) AS devuelta
            FROM detalle_compras d LEFT JOIN repuestos r ON r.id = d.repuesto_id
            WHERE d.compra_id = ? ORDER BY d.id
        """;
        try (Connection conn = Database.getConnection()) {
            Compra c;
            try (PreparedStatement ps = conn.prepareStatement(SQL_ENCABEZADO + " WHERE c.id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    c = mapear(rs);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlLineas)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Compra.Linea l = new Compra.Linea();
                        l.id = rs.getInt("id");
                        l.repuestoId = rs.getInt("repuesto_id");
                        l.codigo = rs.getString("codigo");
                        l.descripcion = rs.getString("descripcion");
                        l.cantidad = rs.getInt("cantidad");
                        l.cantidadRecibida = rs.getInt("cantidad_recibida");
                        l.cantidadDevuelta = rs.getInt("devuelta");
                        l.costoUnitario = rs.getDouble("costo_unitario");
                        l.descuento = rs.getDouble("descuento");
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

    public List<Pago> pagos(int compraId) {
        List<Pago> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM pagos_proveedor WHERE compra_id = ? ORDER BY id")) {
            ps.setInt(1, compraId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Pago(rs.getString("fecha"), rs.getDouble("monto"), rs.getString("metodo"),
                            rs.getString("referencia"), rs.getString("usuario")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    // ------------------------------------------------------------------
    // RECIBIR MERCADERÍA
    // ------------------------------------------------------------------

    /**
     * Registra lo que llegó: suma al inventario (kardex), actualiza el costo del producto (guardando el historial)
     * y deja la compra como PARCIAL o RECIBIDA.
     *
     * @param cantidades id de la línea → unidades que llegaron ahora
     * @return 1 si todo bien; ERROR_CANTIDAD si se intenta recibir más de lo pedido; -1 si falló
     */
    public int recibir(int compraId, Map<Integer, Integer> cantidades, boolean actualizarCosto,
                       String fecha, String usuario, String documento, String proveedorNombre) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (Map.Entry<Integer, Integer> e : cantidades.entrySet()) {
                    int cant = e.getValue();
                    if (cant <= 0) continue;
                    int repuestoId;
                    double costoNeto;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT repuesto_id, cantidad, cantidad_recibida, costo_unitario, descuento FROM detalle_compras "
                          + "WHERE id = ? AND compra_id = ?")) {
                        ps.setInt(1, e.getKey());
                        ps.setInt(2, compraId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) { conn.rollback(); return -1; }
                            if (rs.getInt("cantidad_recibida") + cant > rs.getInt("cantidad")) {
                                conn.rollback();
                                return ERROR_CANTIDAD;
                            }
                            repuestoId = rs.getInt("repuesto_id");
                            int pedida = rs.getInt("cantidad");
                            costoNeto = Math.round((pedida * rs.getDouble("costo_unitario") - rs.getDouble("descuento")) / pedida * 100.0) / 100.0;
                        }
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE detalle_compras SET cantidad_recibida = cantidad_recibida + ? WHERE id = ?")) {
                        ps.setInt(1, cant);
                        ps.setInt(2, e.getKey());
                        ps.executeUpdate();
                    }
                    MovimientoInventarioDAO.moverStock(conn, repuestoId, cant, fecha, MovimientoInventario.Tipo.COMPRA,
                            costoNeto, documento, "Recepción de compra a " + proveedorNombre, usuario);

                    if (actualizarCosto) {
                        double costoActual;
                        try (PreparedStatement ps = conn.prepareStatement("SELECT costo FROM repuestos WHERE id = ?")) {
                            ps.setInt(1, repuestoId);
                            try (ResultSet rs = ps.executeQuery()) {
                                costoActual = rs.next() ? rs.getDouble(1) : costoNeto;
                            }
                        }
                        if (Math.abs(costoActual - costoNeto) >= 0.005) {
                            try (PreparedStatement ps = conn.prepareStatement(
                                    "INSERT INTO historial_costos (repuesto_id, fecha, costo_anterior, costo_nuevo, origen, usuario) VALUES (?, ?, ?, ?, ?, ?)")) {
                                ps.setInt(1, repuestoId);
                                ps.setString(2, fecha);
                                ps.setDouble(3, costoActual);
                                ps.setDouble(4, costoNeto);
                                ps.setString(5, documento);
                                ps.setString(6, usuario);
                                ps.executeUpdate();
                            }
                            try (PreparedStatement ps = conn.prepareStatement("UPDATE repuestos SET costo = ? WHERE id = ?")) {
                                ps.setDouble(1, costoNeto);
                                ps.setInt(2, repuestoId);
                                ps.executeUpdate();
                            }
                        }
                    }
                    // Si el producto no tenía proveedor asignado, queda este
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE repuestos SET proveedor = ? WHERE id = ? AND (proveedor IS NULL OR TRIM(proveedor) = '')")) {
                        ps.setString(1, proveedorNombre);
                        ps.setInt(2, repuestoId);
                        ps.executeUpdate();
                    }
                }
                // Nuevo estado según lo que falta por llegar
                int faltan;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(SUM(cantidad - cantidad_recibida), 0) FROM detalle_compras WHERE compra_id = ?")) {
                    ps.setInt(1, compraId);
                    try (ResultSet rs = ps.executeQuery()) {
                        faltan = rs.next() ? rs.getInt(1) : 0;
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE compras SET estado = ?, fecha_recepcion = ? WHERE id = ? AND estado IN ('PENDIENTE', 'PARCIAL')")) {
                    ps.setString(1, faltan > 0 ? Compra.PARCIAL : Compra.RECIBIDA);
                    ps.setString(2, fecha);
                    ps.setInt(3, compraId);
                    if (ps.executeUpdate() != 1) { conn.rollback(); return -1; }
                }
                conn.commit();
                return 1;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // ANULAR, PAGAR Y DEVOLVER
    // ------------------------------------------------------------------

    /** Anula una orden que todavía no tiene mercadería recibida ni pagos. */
    public boolean anular(int compraId, String usuario, String fecha, String motivo) {
        String sql = "UPDATE compras SET estado = 'ANULADA', anulada_por = ?, fecha_anulacion = ?, motivo_anulacion = ? "
                   + "WHERE id = ? AND estado = 'PENDIENTE' "
                   + "AND NOT EXISTS (SELECT 1 FROM detalle_compras d WHERE d.compra_id = compras.id AND d.cantidad_recibida > 0) "
                   + "AND NOT EXISTS (SELECT 1 FROM pagos_proveedor p WHERE p.compra_id = compras.id)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, fecha);
            ps.setString(3, motivo);
            ps.setInt(4, compraId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Registra un pago al proveedor; si se paga en efectivo desde la caja, también la salida de dinero. */
    public boolean registrarPago(int compraId, String fecha, double monto, String metodo, String referencia,
                                 String usuario, Integer cajaId, String documento) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO pagos_proveedor (compra_id, fecha, monto, metodo, referencia, usuario, caja_id) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, compraId);
                ps.setString(2, fecha);
                ps.setDouble(3, monto);
                ps.setString(4, metodo);
                ps.setString(5, referencia);
                ps.setString(6, usuario);
                if (cajaId == null) ps.setNull(7, Types.INTEGER); else ps.setInt(7, cajaId);
                ps.executeUpdate();
                if (cajaId != null) {
                    try (PreparedStatement mc = conn.prepareStatement(
                            "INSERT INTO movimientos_caja (caja_id, fecha, usuario, tipo, monto, motivo, documento) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                        mc.setInt(1, cajaId);
                        mc.setString(2, fecha);
                        mc.setString(3, usuario);
                        mc.setString(4, MovimientoCaja.Tipo.PAGO_PROVEEDOR.name());
                        mc.setDouble(5, monto);
                        mc.setString(6, "Pago de compra " + documento);
                        mc.setString(7, documento);
                        mc.executeUpdate();
                    }
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
     * Devuelve mercadería al proveedor: descuenta inventario y reduce lo que se le debe.
     * @return id de la devolución; ERROR_CANTIDAD si excede lo recibido; ERROR_STOCK si no hay existencias; -1 si falló
     */
    public int devolver(int compraId, Map<Integer, Integer> cantidades, String motivo, String fecha, String usuario,
                        String documento, double impuestoPct) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int devId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO devoluciones_proveedor (compra_id, fecha, usuario, motivo, total) VALUES (?, ?, ?, ?, 0)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, compraId);
                    ps.setString(2, fecha);
                    ps.setString(3, usuario);
                    ps.setString(4, motivo);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("Sin id de devolución");
                        devId = rs.getInt(1);
                    }
                }
                double base = 0;
                for (Map.Entry<Integer, Integer> e : cantidades.entrySet()) {
                    int cant = e.getValue();
                    if (cant <= 0) continue;
                    int repuestoId;
                    double costoNeto;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT d.repuesto_id, d.cantidad, d.cantidad_recibida, d.costo_unitario, d.descuento, "
                          + "COALESCE((SELECT SUM(x.cantidad) FROM detalle_devoluciones_proveedor x WHERE x.detalle_compra_id = d.id), 0) AS devuelta "
                          + "FROM detalle_compras d WHERE d.id = ? AND d.compra_id = ?")) {
                        ps.setInt(1, e.getKey());
                        ps.setInt(2, compraId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) { conn.rollback(); return -1; }
                            if (rs.getInt("devuelta") + cant > rs.getInt("cantidad_recibida")) {
                                conn.rollback();
                                return ERROR_CANTIDAD;
                            }
                            repuestoId = rs.getInt("repuesto_id");
                            int pedida = rs.getInt("cantidad");
                            costoNeto = Math.round((pedida * rs.getDouble("costo_unitario") - rs.getDouble("descuento")) / pedida * 100.0) / 100.0;
                        }
                    }
                    if (!MovimientoInventarioDAO.moverStock(conn, repuestoId, -cant, fecha, MovimientoInventario.Tipo.DEVOLUCION_PROVEEDOR,
                            costoNeto, documento, motivo, usuario)) {
                        conn.rollback();
                        return ERROR_STOCK;
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO detalle_devoluciones_proveedor (devolucion_id, detalle_compra_id, repuesto_id, cantidad, costo_unitario) "
                          + "VALUES (?, ?, ?, ?, ?)")) {
                        ps.setInt(1, devId);
                        ps.setInt(2, e.getKey());
                        ps.setInt(3, repuestoId);
                        ps.setInt(4, cant);
                        ps.setDouble(5, costoNeto);
                        ps.executeUpdate();
                    }
                    base += cant * costoNeto;
                }
                double total = Math.round(base * (1 + impuestoPct / 100.0) * 100.0) / 100.0;
                if (total <= 0) { conn.rollback(); return ERROR_CANTIDAD; }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE devoluciones_proveedor SET total = ? WHERE id = ?")) {
                    ps.setDouble(1, total);
                    ps.setInt(2, devId);
                    ps.executeUpdate();
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

    /** Cantidad de órdenes pendientes o parciales (para las alertas del dashboard). */
    public int contarPendientes() {
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM compras WHERE estado IN ('PENDIENTE', 'PARCIAL')")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static Compra mapear(ResultSet rs) throws SQLException {
        Compra c = new Compra();
        c.setId(rs.getInt("id"));
        c.setProveedorId(rs.getInt("proveedor_id"));
        c.setProveedorNombre(rs.getString("proveedor_nombre"));
        c.setFecha(rs.getString("fecha"));
        c.setDocumentoProveedor(rs.getString("documento_proveedor"));
        c.setEstado(rs.getString("estado"));
        c.setCondicionPago(rs.getString("condicion_pago"));
        c.setFechaVencimiento(rs.getString("fecha_vencimiento"));
        c.setSubtotal(rs.getDouble("subtotal"));
        c.setDescuento(rs.getDouble("descuento"));
        c.setImpuestoPct(rs.getDouble("impuesto_pct"));
        c.setImpuesto(rs.getDouble("impuesto"));
        c.setTotal(rs.getDouble("total"));
        c.setUsuario(rs.getString("usuario"));
        c.setFechaRecepcion(rs.getString("fecha_recepcion"));
        c.setObservaciones(rs.getString("observaciones"));
        c.setMotivoAnulacion(rs.getString("motivo_anulacion"));
        c.setAnuladaPor(rs.getString("anulada_por"));
        c.setPagado(rs.getDouble("pagado"));
        c.setDevuelto(rs.getDouble("devuelto"));
        return c;
    }
}
