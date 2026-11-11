package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.Factura;
import com.taller.repuestos.model.ItemCarrito;
import com.taller.repuestos.model.MovimientoInventario;
import com.taller.repuestos.model.LineaFactura;
import com.taller.repuestos.model.PagoRegistro;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Acceso a datos de ventas: registrar, consultar facturas, anular y cobrar abonos. */
public class VentaDAO {

    /** Forma de pago "a crédito" (la deuda se cobra después con abonos). */
    public static final String METODO_CREDITO = "Crédito";

    /** Código que devuelve registrarVenta cuando alguna pieza ya no tiene existencias suficientes. */
    public static final int ERROR_STOCK = -2;

    /** Fila resumida de una venta, para listados. */
    public static class Venta {
        private final int id;
        private final String fecha;
        private final String vendedor;
        private final double total;
        private final String metodoPago;
        private final String cliente;
        private final String estado;

        public Venta(int id, String fecha, String vendedor, double total, String metodoPago, String cliente, String estado) {
            this.id = id;
            this.fecha = fecha;
            this.vendedor = vendedor;
            this.total = total;
            this.metodoPago = metodoPago;
            this.cliente = cliente;
            this.estado = estado;
        }

        public int getId() { return id; }
        public String getFecha() { return fecha; }
        public String getVendedor() { return vendedor; }
        public double getTotal() { return total; }
        public String getMetodoPago() { return metodoPago; }
        public String getCliente() { return cliente; }
        public String getEstado() { return estado; }
        public boolean isAnulada() { return Factura.ANULADA.equals(estado); }
    }

    /** Línea simple de una venta (se conserva por compatibilidad; la factura completa usa LineaFactura). */
    public static class DetalleVenta {
        private final String producto;
        private final int cantidad;
        private final double precioUnitario;
        private final double subtotal;

        public DetalleVenta(String producto, int cantidad, double precioUnitario, double subtotal) {
            this.producto = producto;
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
            this.subtotal = subtotal;
        }

        public String getProducto() { return producto; }
        public int getCantidad() { return cantidad; }
        public double getPrecioUnitario() { return precioUnitario; }
        public double getSubtotal() { return subtotal; }
    }

    /** Todo lo necesario para guardar una venta (los números ya vienen calculados por CalculoVenta). */
    public static class DatosVenta {
        public String fecha;
        public String vendedor;
        public Integer clienteId;
        public String metodoPago;
        public List<ItemCarrito> lineas;
        public double subtotal;
        public double descuento;
        public String tipoDescuento;
        public double descuentoValor;
        public double impuesto;
        public double total;
        public Double montoRecibido;
        public Double cambio;
        public String fechaLimite;
        public Integer cajaId;
        /** false cuando las piezas ya salieron del inventario antes (por ejemplo, consumidas en una orden de trabajo). */
        public boolean descontarStock = true;
        /** Orden de trabajo que se está facturando (null en una venta normal). */
        public Integer ordenId;
    }

    /** Una venta a crédito que todavía tiene saldo por cobrar. */
    public static class CreditoPendiente {
        private final int ventaId;
        private final String fecha;
        private final String fechaLimite;
        private final double total;
        private final double saldo;

        public CreditoPendiente(int ventaId, String fecha, String fechaLimite, double total, double saldo) {
            this.ventaId = ventaId;
            this.fecha = fecha;
            this.fechaLimite = fechaLimite;
            this.total = total;
            this.saldo = saldo;
        }

        public int getVentaId() { return ventaId; }
        public String getFecha() { return fecha; }
        public String getFechaLimite() { return fechaLimite; }
        public double getTotal() { return total; }
        public double getSaldo() { return saldo; }
    }

    // ------------------------------------------------------------------
    // REGISTRAR VENTA
    // ------------------------------------------------------------------

    /**
     * Registra la venta, su detalle y su pago, y descuenta el inventario, todo en una sola transacción:
     * o se guarda todo o no se guarda nada.
     *
     * @return número de venta; ERROR_STOCK si falta stock de alguna pieza; -1 si falló la base de datos
     */
    public int registrarVenta(DatosVenta d) {
        String sqlVenta = """
            INSERT INTO ventas (fecha, vendedor, cliente_id, subtotal, descuento, tipo_descuento, descuento_valor,
                                impuesto, total, metodo_pago, monto_recibido, cambio, fecha_limite, caja_id, orden_id, estado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'EMITIDA')
        """;
        String sqlDetalle = """
            INSERT INTO venta_detalle (venta_id, repuesto_id, descripcion, cantidad, precio_unitario, costo_unitario,
                                       subtotal, descuento_pct, descuento)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        // Solo descuenta si todavía hay existencias suficientes (evita vender de más y stock negativo)
        String sqlStock = "UPDATE repuestos SET stock = stock - ? WHERE id = ? AND stock >= ?";
        String sqlPago = "INSERT INTO pagos (venta_id, fecha, monto, metodo, tipo, usuario, caja_id) VALUES (?, ?, ?, ?, 'PAGO', ?, ?)";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false); // Transacción atómica

            try (PreparedStatement pVenta = conn.prepareStatement(sqlVenta, Statement.RETURN_GENERATED_KEYS)) {
                pVenta.setString(1, d.fecha);
                pVenta.setString(2, d.vendedor);
                setIntONull(pVenta, 3, d.clienteId);
                pVenta.setDouble(4, d.subtotal);
                pVenta.setDouble(5, d.descuento);
                pVenta.setString(6, d.tipoDescuento);
                pVenta.setDouble(7, d.descuentoValor);
                pVenta.setDouble(8, d.impuesto);
                pVenta.setDouble(9, d.total);
                pVenta.setString(10, d.metodoPago);
                setDoubleONull(pVenta, 11, d.montoRecibido);
                setDoubleONull(pVenta, 12, d.cambio);
                pVenta.setString(13, d.fechaLimite);
                setIntONull(pVenta, 14, d.cajaId);
                setIntONull(pVenta, 15, d.ordenId);
                pVenta.executeUpdate();

                int ventaId = 0;
                try (ResultSet rs = pVenta.getGeneratedKeys()) {
                    if (rs.next()) ventaId = rs.getInt(1);
                }

                try (PreparedStatement pDetalle = conn.prepareStatement(sqlDetalle);
                     PreparedStatement pStock = conn.prepareStatement(sqlStock)) {
                    String documento = com.taller.repuestos.util.Formato.numeroFactura(ventaId);
                    for (ItemCarrito item : d.lineas) {
                        // Los servicios (id 0) y las piezas ya consumidas en una orden no mueven inventario
                        if (d.descontarStock && item.getRepuesto().getId() > 0) {
                            pStock.setInt(1, item.getCantidad());
                            pStock.setInt(2, item.getRepuesto().getId());
                            pStock.setInt(3, item.getCantidad());
                            if (pStock.executeUpdate() != 1) {
                                conn.rollback();
                                return ERROR_STOCK;
                            }
                            MovimientoInventarioDAO.registrar(conn, item.getRepuesto().getId(), d.fecha,
                                    MovimientoInventario.Tipo.VENTA, -item.getCantidad(), item.getRepuesto().getCosto(),
                                    documento, null, d.vendedor);
                        }

                        pDetalle.setInt(1, ventaId);
                        pDetalle.setInt(2, item.getRepuesto().getId());
                        pDetalle.setString(3, item.getRepuesto().getDescripcion());
                        pDetalle.setInt(4, item.getCantidad());
                        pDetalle.setDouble(5, item.getPrecioUnitario());
                        pDetalle.setDouble(6, item.getRepuesto().getCosto());
                        pDetalle.setDouble(7, item.getSubtotal());
                        pDetalle.setDouble(8, item.getDescuentoPct());
                        pDetalle.setDouble(9, item.getDescuentoMonto());
                        pDetalle.addBatch();
                    }
                    pDetalle.executeBatch();
                }

                // Las ventas a crédito no generan pago: la deuda se cobra después con abonos
                if (!METODO_CREDITO.equals(d.metodoPago)) {
                    try (PreparedStatement pPago = conn.prepareStatement(sqlPago)) {
                        pPago.setInt(1, ventaId);
                        pPago.setString(2, d.fecha);
                        pPago.setDouble(3, d.total);
                        pPago.setString(4, d.metodoPago);
                        pPago.setString(5, d.vendedor);
                        setIntONull(pPago, 6, d.cajaId);
                        pPago.executeUpdate();
                    }
                }

                conn.commit();
                return ventaId;
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

    public List<Venta> obtenerTodasLasVentas() {
        return consultarVentas("", null);
    }

    public List<Venta> obtenerVentasDeCliente(int clienteId) {
        return consultarVentas("WHERE v.cliente_id = ?", clienteId);
    }

    private List<Venta> consultarVentas(String where, Integer parametro) {
        List<Venta> lista = new ArrayList<>();
        String sql = "SELECT v.id, v.fecha, v.vendedor, v.total, v.metodo_pago, v.estado, c.nombre AS cliente "
                   + "FROM ventas v LEFT JOIN clientes c ON c.id = v.cliente_id " + where + " ORDER BY v.id DESC";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (parametro != null) ps.setInt(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Venta(
                            rs.getInt("id"), rs.getString("fecha"), rs.getString("vendedor"), rs.getDouble("total"),
                            rs.getString("metodo_pago"), rs.getString("cliente"), rs.getString("estado")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /** Líneas simples de una venta (se conserva por compatibilidad). */
    public List<DetalleVenta> obtenerDetallesPorVenta(int ventaId) {
        List<DetalleVenta> lista = new ArrayList<>();
        String sql = "SELECT * FROM venta_detalle WHERE venta_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new DetalleVenta(rs.getString("descripcion"), rs.getInt("cantidad"),
                            rs.getDouble("precio_unitario"), rs.getDouble("subtotal")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /** La factura completa (encabezado, líneas, abonos y devoluciones), o null si no existe. */
    public Factura obtenerFactura(int ventaId) {
        String sqlEncabezado = """
            SELECT v.*, c.nombre AS cliente_nombre, c.identificacion AS cliente_identificacion
            FROM ventas v LEFT JOIN clientes c ON c.id = v.cliente_id WHERE v.id = ?
        """;
        String sqlLineas = """
            SELECT d.*, COALESCE((SELECT SUM(dd.cantidad) FROM detalle_devoluciones dd WHERE dd.venta_detalle_id = d.id), 0) AS devuelta
            FROM venta_detalle d WHERE d.venta_id = ? ORDER BY d.id
        """;
        String sqlAbonos = "SELECT COALESCE(SUM(monto), 0) FROM pagos WHERE venta_id = ? AND tipo = 'ABONO'";
        String sqlDevoluciones = "SELECT COALESCE(SUM(monto_total), 0), COALESCE(SUM(monto_saldo), 0) FROM devoluciones WHERE venta_id = ?";

        try (Connection conn = Database.getConnection()) {
            Factura f = new Factura();
            try (PreparedStatement ps = conn.prepareStatement(sqlEncabezado)) {
                ps.setInt(1, ventaId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    f.setId(rs.getInt("id"));
                    f.setFecha(rs.getString("fecha"));
                    f.setVendedor(rs.getString("vendedor"));
                    int cliente = rs.getInt("cliente_id");
                    f.setClienteId(rs.wasNull() ? null : cliente);
                    f.setClienteNombre(rs.getString("cliente_nombre"));
                    f.setClienteIdentificacion(rs.getString("cliente_identificacion"));
                    f.setMetodoPago(rs.getString("metodo_pago"));
                    f.setEstado(rs.getString("estado"));
                    f.setSubtotal(rs.getDouble("subtotal"));
                    f.setDescuento(rs.getDouble("descuento"));
                    f.setTipoDescuento(rs.getString("tipo_descuento"));
                    f.setDescuentoValor(rs.getDouble("descuento_valor"));
                    f.setImpuesto(rs.getDouble("impuesto"));
                    f.setTotal(rs.getDouble("total"));
                    double recibido = rs.getDouble("monto_recibido");
                    f.setMontoRecibido(rs.wasNull() ? null : recibido);
                    double cambio = rs.getDouble("cambio");
                    f.setCambio(rs.wasNull() ? null : cambio);
                    f.setFechaLimite(rs.getString("fecha_limite"));
                    int caja = rs.getInt("caja_id");
                    f.setCajaId(rs.wasNull() ? null : caja);
                    f.setMotivoAnulacion(rs.getString("motivo_anulacion"));
                    f.setAnuladaPor(rs.getString("anulada_por"));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlLineas)) {
                ps.setInt(1, ventaId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        f.getLineas().add(new LineaFactura(
                                rs.getInt("id"), rs.getInt("repuesto_id"), rs.getString("descripcion"),
                                rs.getInt("cantidad"), rs.getDouble("precio_unitario"),
                                rs.getDouble("descuento_pct"), rs.getDouble("descuento"),
                                rs.getDouble("subtotal"), rs.getInt("devuelta")));
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlAbonos)) {
                ps.setInt(1, ventaId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) f.setAbonado(rs.getDouble(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlDevoluciones)) {
                ps.setInt(1, ventaId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        f.setDevueltoTotal(rs.getDouble(1));
                        f.setDevueltoSaldo(rs.getDouble(2));
                    }
                }
            }
            return f;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // ANULAR
    // ------------------------------------------------------------------

    /**
     * Marca la venta como anulada y devuelve sus piezas al inventario, en una sola transacción.
     * Devuelve false si la venta no existe o ya estaba anulada.
     */
    public boolean anular(int ventaId, String usuario, String fecha, String motivo) {
        String sqlAnular = "UPDATE ventas SET estado = 'ANULADA', anulada_por = ?, fecha_anulacion = ?, motivo_anulacion = ? "
                         + "WHERE id = ? AND estado = 'EMITIDA'";
        String sqlLineas = "SELECT repuesto_id, cantidad, costo_unitario FROM venta_detalle WHERE venta_id = ? AND repuesto_id > 0";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sqlAnular)) {
                    ps.setString(1, usuario);
                    ps.setString(2, fecha);
                    ps.setString(3, motivo);
                    ps.setInt(4, ventaId);
                    if (ps.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }
                List<int[]> piezas = new ArrayList<>();
                List<Double> costos = new ArrayList<>();
                try (PreparedStatement lineas = conn.prepareStatement(sqlLineas)) {
                    lineas.setInt(1, ventaId);
                    try (ResultSet rs = lineas.executeQuery()) {
                        while (rs.next()) {
                            piezas.add(new int[]{rs.getInt("repuesto_id"), rs.getInt("cantidad")});
                            costos.add(rs.getDouble("costo_unitario"));
                        }
                    }
                }
                String documento = com.taller.repuestos.util.Formato.numeroFactura(ventaId);
                for (int i = 0; i < piezas.size(); i++) {
                    MovimientoInventarioDAO.moverStock(conn, piezas.get(i)[0], piezas.get(i)[1], fecha,
                            MovimientoInventario.Tipo.ANULACION, costos.get(i), documento, motivo, usuario);
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

    // ------------------------------------------------------------------
    // CRÉDITO: ABONOS Y SALDOS
    // ------------------------------------------------------------------

    public boolean registrarAbono(int ventaId, String fecha, double monto, String metodo, String usuario, Integer cajaId) {
        String sql = "INSERT INTO pagos (venta_id, fecha, monto, metodo, tipo, usuario, caja_id) VALUES (?, ?, ?, ?, 'ABONO', ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ventaId);
            ps.setString(2, fecha);
            ps.setDouble(3, monto);
            ps.setString(4, metodo);
            ps.setString(5, usuario);
            setIntONull(ps, 6, cajaId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<PagoRegistro> obtenerAbonos(int ventaId) {
        List<PagoRegistro> lista = new ArrayList<>();
        String sql = "SELECT * FROM pagos WHERE venta_id = ? AND tipo = 'ABONO' ORDER BY id";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new PagoRegistro(rs.getInt("id"), rs.getInt("venta_id"), rs.getString("fecha"),
                            rs.getDouble("monto"), rs.getString("metodo"), rs.getString("tipo"), rs.getString("usuario")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /** Ventas a crédito del cliente que todavía tienen saldo por cobrar, de la más antigua a la más reciente. */
    public List<CreditoPendiente> obtenerCreditosPendientes(int clienteId) {
        List<CreditoPendiente> lista = new ArrayList<>();
        String sql = """
            SELECT v.id, v.fecha, v.fecha_limite, v.total,
                v.total
                  - COALESCE((SELECT SUM(p.monto) FROM pagos p WHERE p.venta_id = v.id AND p.tipo = 'ABONO'), 0)
                  - COALESCE((SELECT SUM(d.monto_saldo) FROM devoluciones d WHERE d.venta_id = v.id), 0) AS saldo
            FROM ventas v
            WHERE v.cliente_id = ? AND v.metodo_pago = ? AND v.estado = 'EMITIDA'
            ORDER BY v.id
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clienteId);
            ps.setString(2, METODO_CREDITO);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double saldo = Math.round(rs.getDouble("saldo") * 100.0) / 100.0;
                    if (saldo > 0.005) {
                        lista.add(new CreditoPendiente(rs.getInt("id"), rs.getString("fecha"),
                                rs.getString("fecha_limite"), rs.getDouble("total"), saldo));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    // ------------------------------------------------------------------

    private static void setIntONull(PreparedStatement ps, int indice, Integer valor) throws SQLException {
        if (valor == null) ps.setNull(indice, Types.INTEGER);
        else ps.setInt(indice, valor);
    }

    private static void setDoubleONull(PreparedStatement ps, int indice, Double valor) throws SQLException {
        if (valor == null) ps.setNull(indice, Types.REAL);
        else ps.setDouble(indice, valor);
    }
}
