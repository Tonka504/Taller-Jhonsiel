package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.MovimientoInventario;
import com.taller.repuestos.model.MovimientoInventario.Tipo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Kardex: historial de todos los movimientos de existencias.
 * Los métodos estáticos que reciben una Connection se usan DENTRO de las transacciones de otros DAO
 * (venta, compra, devolución...), para que el movimiento quede guardado junto con la operación o no quede.
 */
public class MovimientoInventarioDAO {

    private static final String MARCA_MIGRADO = "kardex.migrado";

    /** Filtros para consultar el kardex (los null no filtran). */
    public static class Filtro {
        public Integer repuestoId;
        public String desde;   // yyyy-MM-dd
        public String hasta;   // yyyy-MM-dd
        public String tipo;    // nombre del enum Tipo
        public String texto;   // código, descripción, documento o motivo
        public int limite = 1000;
    }

    // ------------------------------------------------------------------
    // USO DENTRO DE OTRAS TRANSACCIONES
    // ------------------------------------------------------------------

    /**
     * Registra un movimiento que ya se aplicó al stock (el stock resultante se lee del producto).
     * @param costo costo unitario del movimiento; null = el costo actual del producto
     */
    public static void registrar(Connection conn, int repuestoId, String fecha, Tipo tipo, int cantidad,
                                 Double costo, String documento, String motivo, String usuario) throws SQLException {
        String sql = "INSERT INTO movimientos_inventario (repuesto_id, fecha, tipo, cantidad, stock_resultante, "
                   + "costo_unitario, documento, motivo, usuario) VALUES (?, ?, ?, ?, "
                   + "(SELECT stock FROM repuestos WHERE id = ?), COALESCE(?, (SELECT costo FROM repuestos WHERE id = ?)), ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, repuestoId);
            ps.setString(2, fecha);
            ps.setString(3, tipo.name());
            ps.setInt(4, cantidad);
            ps.setInt(5, repuestoId);
            if (costo == null) ps.setNull(6, Types.REAL); else ps.setDouble(6, costo);
            ps.setInt(7, repuestoId);
            ps.setString(8, documento);
            ps.setString(9, motivo);
            ps.setString(10, usuario == null ? "Sistema" : usuario);
            ps.executeUpdate();
        }
    }

    /**
     * Suma (cantidad positiva) o resta (negativa) existencias sin permitir que queden negativas,
     * y registra el movimiento. Devuelve false si no hay existencias suficientes.
     */
    public static boolean moverStock(Connection conn, int repuestoId, int cantidad, String fecha, Tipo tipo,
                                     Double costo, String documento, String motivo, String usuario) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE repuestos SET stock = stock + ? WHERE id = ? AND stock + ? >= 0")) {
            ps.setInt(1, cantidad);
            ps.setInt(2, repuestoId);
            ps.setInt(3, cantidad);
            if (ps.executeUpdate() != 1) return false;
        }
        registrar(conn, repuestoId, fecha, tipo, cantidad, costo, documento, motivo, usuario);
        return true;
    }

    // ------------------------------------------------------------------
    // OPERACIONES PROPIAS (cada una en su transacción)
    // ------------------------------------------------------------------

    /** Entrada o salida manual. Devuelve false si la salida deja el stock en negativo o falla la base de datos. */
    public boolean ajustar(int repuestoId, int cantidad, Tipo tipo, String fecha, String documento, String motivo, String usuario) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (!moverStock(conn, repuestoId, cantidad, fecha, tipo, null, documento, motivo, usuario)) {
                    conn.rollback();
                    return false;
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
     * Deja la existencia en un valor exacto (conteo físico) y registra la diferencia.
     * @return la diferencia aplicada (puede ser 0), o null si falló
     */
    public Integer fijarStock(int repuestoId, int nuevoStock, Tipo tipo, String fecha, String motivo, String usuario) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int actual;
                try (PreparedStatement ps = conn.prepareStatement("SELECT stock FROM repuestos WHERE id = ?")) {
                    ps.setInt(1, repuestoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return null;
                        }
                        actual = rs.getInt(1);
                    }
                }
                int diferencia = nuevoStock - actual;
                if (diferencia != 0) {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE repuestos SET stock = ? WHERE id = ?")) {
                        ps.setInt(1, nuevoStock);
                        ps.setInt(2, repuestoId);
                        ps.executeUpdate();
                    }
                    registrar(conn, repuestoId, fecha, tipo, diferencia, null, null, motivo, usuario);
                }
                conn.commit();
                return diferencia;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Registra un movimiento suelto fuera de otra transacción (por ejemplo, el inventario inicial de un producto nuevo). */
    public void registrarSuelto(int repuestoId, String fecha, Tipo tipo, int cantidad, String motivo, String usuario) {
        try (Connection conn = Database.getConnection()) {
            registrar(conn, repuestoId, fecha, tipo, cantidad, null, null, motivo, usuario);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // CONSULTAS
    // ------------------------------------------------------------------

    public List<MovimientoInventario> buscar(Filtro f) {
        List<MovimientoInventario> lista = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT m.*, r.codigo, r.descripcion FROM movimientos_inventario m "
              + "LEFT JOIN repuestos r ON r.id = m.repuesto_id WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (f.repuestoId != null) {
            sql.append(" AND m.repuesto_id = ?");
            params.add(f.repuestoId);
        }
        if (f.desde != null && !f.desde.isEmpty()) {
            sql.append(" AND m.fecha >= ?");
            params.add(f.desde);
        }
        if (f.hasta != null && !f.hasta.isEmpty()) {
            sql.append(" AND m.fecha < date(?, '+1 day')");
            params.add(f.hasta);
        }
        if (f.tipo != null && !f.tipo.isEmpty()) {
            sql.append(" AND m.tipo = ?");
            params.add(f.tipo);
        }
        if (f.texto != null && !f.texto.trim().isEmpty()) {
            sql.append(" AND (r.codigo LIKE ? OR r.descripcion LIKE ? OR m.documento LIKE ? OR m.motivo LIKE ? OR m.usuario LIKE ?)");
            String like = "%" + f.texto.trim() + "%";
            for (int i = 0; i < 5; i++) params.add(like);
        }
        sql.append(" ORDER BY m.fecha DESC, m.id DESC LIMIT ").append(Math.max(1, f.limite));

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int stock = rs.getInt("stock_resultante");
                    Integer stockRes = rs.wasNull() ? null : stock;
                    double costo = rs.getDouble("costo_unitario");
                    Double costoRes = rs.wasNull() ? null : costo;
                    lista.add(new MovimientoInventario(rs.getInt("id"), rs.getInt("repuesto_id"),
                            rs.getString("codigo"), rs.getString("descripcion"), rs.getString("fecha"),
                            rs.getString("tipo"), rs.getInt("cantidad"), stockRes, costoRes,
                            rs.getString("documento"), rs.getString("motivo"), rs.getString("usuario")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    // ------------------------------------------------------------------
    // MIGRACIÓN
    // ------------------------------------------------------------------

    /**
     * La primera vez que existe el kardex, copia el historial que ya había (reabastecimientos, ventas,
     * anulaciones y devoluciones) para que los productos antiguos no aparezcan sin movimientos.
     */
    public static void migrarHistorial(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM configuracion WHERE clave = ?")) {
            ps.setString(1, MARCA_MIGRADO);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        String prefFac = leerConfig(conn, "documentos.prefijo_factura", "FAC-");
        String prefDev = leerConfig(conn, "documentos.prefijo_devolucion", "DEV-");
        int digitos;
        try {
            digitos = Math.max(1, Math.min(10, Integer.parseInt(leerConfig(conn, "documentos.digitos", "6").trim())));
        } catch (NumberFormatException e) {
            digitos = 6;
        }
        String fmt = "'%0" + digitos + "d'";

        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            String insert = "INSERT INTO movimientos_inventario (repuesto_id, fecha, tipo, cantidad, stock_resultante, "
                          + "costo_unitario, documento, motivo, usuario) ";
            st.executeUpdate(insert
                    + "SELECT h.repuesto_id, h.fecha, 'ENTRADA', h.cantidad_agregada, h.stock_resultante, r.costo, NULL, "
                    + "'Reabastecimiento (histórico)', 'Sistema' "
                    + "FROM historial_reabastecimiento h LEFT JOIN repuestos r ON r.id = h.repuesto_id");
            st.executeUpdate(insert
                    + "SELECT d.repuesto_id, v.fecha, 'VENTA', -d.cantidad, NULL, d.costo_unitario, "
                    + "'" + prefFac.replace("'", "''") + "' || printf(" + fmt + ", v.id), 'Venta (histórico)', COALESCE(v.vendedor, 'Sistema') "
                    + "FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id");
            st.executeUpdate(insert
                    + "SELECT d.repuesto_id, COALESCE(v.fecha_anulacion, v.fecha), 'ANULACION', d.cantidad, NULL, d.costo_unitario, "
                    + "'" + prefFac.replace("'", "''") + "' || printf(" + fmt + ", v.id), v.motivo_anulacion, COALESCE(v.anulada_por, 'Sistema') "
                    + "FROM venta_detalle d JOIN ventas v ON v.id = d.venta_id WHERE v.estado = 'ANULADA'");
            st.executeUpdate(insert
                    + "SELECT dd.repuesto_id, dv.fecha, 'DEVOLUCION_CLIENTE', dd.cantidad, NULL, NULL, "
                    + "'" + prefDev.replace("'", "''") + "' || printf(" + fmt + ", dv.id), dv.motivo, dv.usuario "
                    + "FROM detalle_devoluciones dd JOIN devoluciones dv ON dv.id = dd.devolucion_id");
            try (PreparedStatement marca = conn.prepareStatement("INSERT OR REPLACE INTO configuracion (clave, valor) VALUES (?, 'true')")) {
                marca.setString(1, MARCA_MIGRADO);
                marca.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private static String leerConfig(Connection conn, String clave, String porDefecto) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT valor FROM configuracion WHERE clave = ?")) {
            ps.setString(1, clave);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString(1) != null && !rs.getString(1).isEmpty()) return rs.getString(1);
            }
        }
        return porDefecto;
    }
}
