package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.Repuesto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RepuestoDAO {

    // --- CLASE MODELO INTERNA PARA REGISTROS DEL HISTORIAL ---
    public static class EntradaHistorial {
        private String codigo;
        private String descripcion;
        private int cantidadAgregada;
        private int stockResultante;
        private String fecha;

        public EntradaHistorial(String codigo, String descripcion, int cantidadAgregada, int stockResultante, String fecha) {
            this.codigo = codigo;
            this.descripcion = descripcion;
            this.cantidadAgregada = cantidadAgregada;
            this.stockResultante = stockResultante;
            this.fecha = fecha;
        }

        public String getCodigo() { return codigo; }
        public String getDescripcion() { return descripcion; }
        public int getCantidadAgregada() { return cantidadAgregada; }
        public int getStockResultante() { return stockResultante; }
        public String getFecha() { return fecha; }
    }

    // --- OPERACIONES DE REPUESTOS E INVENTARIO ---

    public List<Repuesto> obtenerTodos() {
        List<Repuesto> lista = new ArrayList<>();
        String sql = "SELECT * FROM repuestos ORDER BY descripcion ASC";

        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                lista.add(mapearRepuesto(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /** Productos cuyo stock actual está en o por debajo de su stock mínimo, del más urgente al menos urgente. */
    public List<Repuesto> obtenerStockBajo() {
        List<Repuesto> lista = new ArrayList<>();
        String sql = "SELECT * FROM repuestos WHERE stock <= stock_minimo ORDER BY (stock_minimo - stock) DESC";

        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                lista.add(mapearRepuesto(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    private Repuesto mapearRepuesto(ResultSet rs) throws SQLException {
        Repuesto r = new Repuesto(
            rs.getInt("id"),
            rs.getString("codigo"),
            rs.getString("descripcion"),
            rs.getString("marca"),
            rs.getString("aplicacion"),
            rs.getDouble("costo"),
            rs.getDouble("precio"),
            rs.getInt("stock"),
            rs.getInt("stock_minimo"),
            rs.getString("proveedor")
        );
        r.setCategoria(rs.getString("categoria"));
        r.setCodigoBarras(rs.getString("codigo_barras"));
        r.setUbicacion(rs.getString("ubicacion"));
        r.setPrecioMayorista(rs.getDouble("precio_mayorista"));
        r.setStockMaximo(rs.getInt("stock_maximo"));
        String unidad = rs.getString("unidad");
        r.setUnidad(unidad == null || unidad.isEmpty() ? "Unidad" : unidad);
        r.setFoto(rs.getString("foto"));
        return r;
    }

    public boolean guardar(Repuesto r) {
        String sql = "INSERT INTO repuestos (codigo, descripcion, marca, aplicacion, costo, precio, stock, stock_minimo, proveedor, "
                   + "categoria, codigo_barras, ubicacion, precio_mayorista, stock_maximo, unidad, foto) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, r.getCodigo());
            stmt.setString(2, r.getDescripcion());
            stmt.setString(3, r.getMarca());
            stmt.setString(4, r.getAplicacion());
            stmt.setDouble(5, r.getCosto());
            stmt.setDouble(6, r.getPrecio());
            stmt.setInt(7, r.getStock());
            stmt.setInt(8, r.getStockMinimo());
            stmt.setString(9, r.getProveedor());
            stmt.setString(10, r.getCategoria());
            stmt.setString(11, vacioANull(r.getCodigoBarras()));
            stmt.setString(12, r.getUbicacion());
            stmt.setDouble(13, r.getPrecioMayorista());
            stmt.setInt(14, r.getStockMaximo());
            stmt.setString(15, r.getUnidad() == null || r.getUnidad().isEmpty() ? "Unidad" : r.getUnidad());
            stmt.setString(16, vacioANull(r.getFoto()));

            int filasAfectadas = stmt.executeUpdate();
            if (filasAfectadas > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        r.setId(generatedKeys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean actualizar(Repuesto r) {
        return actualizar(r, true);
    }

    /**
     * Guarda los cambios de un producto.
     * @param incluirStock false para no tocar las existencias: así, si mientras se editaba se hizo una venta,
     *                     no se pisa el stock real con el que se veía en pantalla
     */
    public boolean actualizar(Repuesto r, boolean incluirStock) {
        String sql = "UPDATE repuestos SET codigo=?, descripcion=?, marca=?, aplicacion=?, costo=?, precio=?, "
                   + (incluirStock ? "stock=?, " : "")
                   + "stock_minimo=?, proveedor=?, categoria=?, codigo_barras=?, ubicacion=?, precio_mayorista=?, "
                   + "stock_maximo=?, unidad=?, foto=? WHERE id=?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int i = 1;
            stmt.setString(i++, r.getCodigo());
            stmt.setString(i++, r.getDescripcion());
            stmt.setString(i++, r.getMarca());
            stmt.setString(i++, r.getAplicacion());
            stmt.setDouble(i++, r.getCosto());
            stmt.setDouble(i++, r.getPrecio());
            if (incluirStock) stmt.setInt(i++, r.getStock());
            stmt.setInt(i++, r.getStockMinimo());
            stmt.setString(i++, r.getProveedor());
            stmt.setString(i++, r.getCategoria());
            stmt.setString(i++, vacioANull(r.getCodigoBarras()));
            stmt.setString(i++, r.getUbicacion());
            stmt.setDouble(i++, r.getPrecioMayorista());
            stmt.setInt(i++, r.getStockMaximo());
            stmt.setString(i++, r.getUnidad() == null || r.getUnidad().isEmpty() ? "Unidad" : r.getUnidad());
            stmt.setString(i++, vacioANull(r.getFoto()));
            stmt.setInt(i, r.getId());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Borra el producto y los vehículos compatibles que tenía registrados. */
    public boolean eliminar(int id) {
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement compat = conn.prepareStatement("DELETE FROM compatibilidades WHERE repuesto_id = ?");
                 PreparedStatement kardex = conn.prepareStatement("DELETE FROM movimientos_inventario WHERE repuesto_id = ? AND tipo = 'INICIAL'");
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM repuestos WHERE id = ?")) {
                compat.setInt(1, id);
                compat.executeUpdate();
                kardex.setInt(1, id);
                kardex.executeUpdate();
                stmt.setInt(1, id);
                boolean borrado = stmt.executeUpdate() > 0;
                conn.commit();
                return borrado;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** true si otro producto (distinto de excluirId) ya usa este código. */
    public boolean existeCodigo(String codigo, int excluirId) {
        return existe("SELECT 1 FROM repuestos WHERE codigo = ? COLLATE NOCASE AND id <> ?", codigo, excluirId);
    }

    /** true si otro producto (distinto de excluirId) ya usa este código de barras. */
    public boolean existeCodigoBarras(String codigoBarras, int excluirId) {
        return existe("SELECT 1 FROM repuestos WHERE codigo_barras = ? AND id <> ?", codigoBarras, excluirId);
    }

    private boolean existe(String sql, String valor, int excluirId) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, valor);
            ps.setInt(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true; // ante la duda no se permite duplicar
    }

    private static String vacioANull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    /**
     * true si el producto ya tiene ventas o reabastecimientos registrados.
     * Ante cualquier error responde true, para no permitir un borrado que no se pudo comprobar.
     */
    public boolean tieneMovimientos(int repuestoId) {
        String sql = "SELECT (SELECT COUNT(*) FROM venta_detalle WHERE repuesto_id = ?) "
                   + "+ (SELECT COUNT(*) FROM historial_reabastecimiento WHERE repuesto_id = ?) "
                   + "+ (SELECT COUNT(*) FROM movimientos_inventario WHERE repuesto_id = ? AND tipo <> 'INICIAL') "
                   + "+ (SELECT COUNT(*) FROM detalle_compras WHERE repuesto_id = ?) "
                   + "+ (SELECT COUNT(*) FROM orden_repuestos WHERE repuesto_id = ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) stmt.setInt(i, repuestoId);
            try (ResultSet rs = stmt.executeQuery()) {
                return !rs.next() || rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }

    // --- OPERACIONES DE REABASTECIMIENTO E HISTORIAL ---

    public boolean reabastecerStock(int repuestoId, int cantidadSumar, int nuevoStockTotal) {
        String sqlUpdate = "UPDATE repuestos SET stock = stock + ? WHERE id = ?";
        String sqlHistorial = "INSERT INTO historial_reabastecimiento (repuesto_id, cantidad_agregada, stock_resultante, fecha) VALUES (?, ?, ?, ?)";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt1 = conn.prepareStatement(sqlUpdate);
                 PreparedStatement stmt2 = conn.prepareStatement(sqlHistorial)) {

                stmt1.setInt(1, cantidadSumar);
                stmt1.setInt(2, repuestoId);
                stmt1.executeUpdate();

                String fechaStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                stmt2.setInt(1, repuestoId);
                stmt2.setInt(2, cantidadSumar);
                stmt2.setInt(3, nuevoStockTotal);
                stmt2.setString(4, fechaStr);
                stmt2.executeUpdate();

                MovimientoInventarioDAO.registrar(conn, repuestoId, fechaStr,
                        com.taller.repuestos.model.MovimientoInventario.Tipo.ENTRADA, cantidadSumar, null, null,
                        "Reabastecimiento", com.taller.repuestos.util.Sesion.nombreActual());

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<EntradaHistorial> obtenerHistorialReabastecimiento() {
        List<EntradaHistorial> lista = new ArrayList<>();
        String sql = """
            SELECT r.codigo, r.descripcion, h.cantidad_agregada, h.stock_resultante, h.fecha 
            FROM historial_reabastecimiento h
            JOIN repuestos r ON h.repuesto_id = r.id
            ORDER BY h.id DESC LIMIT 50;
        """;

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                lista.add(new EntradaHistorial(
                    rs.getString("codigo"),
                    rs.getString("descripcion"),
                    rs.getInt("cantidad_agregada"),
                    rs.getInt("stock_resultante"),
                    rs.getString("fecha")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }
}
