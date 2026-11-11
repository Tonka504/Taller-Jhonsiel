package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.Caja;
import com.taller.repuestos.model.MovimientoCaja;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Acceso a datos de la caja: turnos (cajas) y movimientos manuales de efectivo. */
public class CajaDAO {

    /** Resultado de abrir una caja. */
    public static class Apertura {
        public final int cajaId;
        public final int ventasIncluidas;

        public Apertura(int cajaId, int ventasIncluidas) {
            this.cajaId = cajaId;
            this.ventasIncluidas = ventasIncluidas;
        }
    }

    // ------------------------------------------------------------------
    // TURNOS
    // ------------------------------------------------------------------

    /** La caja que está abierta ahora, o null si no hay ninguna. */
    public Caja obtenerCajaAbierta() {
        String sql = "SELECT * FROM cajas WHERE estado = 'ABIERTA' ORDER BY id DESC LIMIT 1";
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return mapear(rs);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Abre una caja con el fondo inicial indicado. Las ventas de HOY que se hicieron sin caja abierta
     * (y después del último cierre) pasan a este turno para que no se pierdan en el cierre.
     * Devuelve null si no se pudo abrir (por ejemplo, porque ya hay una caja abierta).
     */
    public Apertura abrir(String usuario, double montoInicial, String fechaApertura) {
        String sqlInsert = "INSERT INTO cajas (estado, usuario_apertura, fecha_apertura, monto_inicial) "
                         + "VALUES ('ABIERTA', ?, ?, ?)";
        String sqlAdoptar = "UPDATE ventas SET caja_id = ? WHERE caja_id IS NULL AND estado = 'EMITIDA' AND fecha > ?";
        String sqlAdoptarAbonos = "UPDATE pagos SET caja_id = ? WHERE caja_id IS NULL AND tipo = 'ABONO' AND fecha > ?";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String ultimoCierre = ultimaFechaCierre(conn);
                String inicioHoy = fechaApertura.substring(0, 10) + " 00:00:00";
                String limite = (ultimoCierre != null && ultimoCierre.compareTo(inicioHoy) > 0) ? ultimoCierre : inicioHoy;

                int cajaId;
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, usuario);
                    ps.setString(2, fechaApertura);
                    ps.setDouble(3, montoInicial);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No se obtuvo el id de la caja");
                        cajaId = keys.getInt(1);
                    }
                }

                int adoptadas;
                try (PreparedStatement ps = conn.prepareStatement(sqlAdoptar)) {
                    ps.setInt(1, cajaId);
                    ps.setString(2, limite);
                    adoptadas = ps.executeUpdate();
                }
                // Los abonos cobrados sin caja abierta también pasan a este turno
                try (PreparedStatement ps = conn.prepareStatement(sqlAdoptarAbonos)) {
                    ps.setInt(1, cajaId);
                    ps.setString(2, limite);
                    ps.executeUpdate();
                }

                conn.commit();
                return new Apertura(cajaId, adoptadas);
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Cierra la caja guardando todos los totales. Devuelve false si ya estaba cerrada o falló. */
    public boolean cerrar(Caja c) {
        String sql = """
            UPDATE cajas SET estado = 'CERRADA', usuario_cierre = ?, fecha_cierre = ?,
                total_efectivo = ?, total_tarjeta = ?, total_transferencia = ?, total_sistema = ?,
                entradas = ?, retiros = ?, gastos = ?,
                total_credito = ?, abonos_efectivo = ?, devoluciones = ?,
                efectivo_esperado = ?, efectivo_contado = ?, diferencia = ?, observaciones = ?
            WHERE id = ? AND estado = 'ABIERTA'
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getUsuarioCierre());
            ps.setString(2, c.getFechaCierre());
            ps.setDouble(3, c.getTotalEfectivo());
            ps.setDouble(4, c.getTotalTarjeta());
            ps.setDouble(5, c.getTotalTransferencia());
            ps.setDouble(6, c.getTotalSistema());
            ps.setDouble(7, c.getEntradas());
            ps.setDouble(8, c.getRetiros());
            ps.setDouble(9, c.getGastos());
            ps.setDouble(10, c.getTotalCredito());
            ps.setDouble(11, c.getAbonosEfectivo());
            ps.setDouble(12, c.getDevoluciones());
            ps.setDouble(13, c.getEfectivoEsperado());
            ps.setDouble(14, c.getEfectivoContado());
            ps.setDouble(15, c.getDiferencia());
            ps.setString(16, c.getObservaciones());
            ps.setInt(17, c.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<Caja> obtenerHistorial(int limite) {
        List<Caja> lista = new ArrayList<>();
        String sql = "SELECT * FROM cajas WHERE estado = 'CERRADA' ORDER BY id DESC LIMIT ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    // ------------------------------------------------------------------
    // TOTALES DE UN TURNO
    // ------------------------------------------------------------------

    /**
     * Ventas emitidas del turno (las anuladas no cuentan) por forma de pago:
     * [efectivo, tarjeta, transferencia, cantidad de ventas, crédito].
     */
    public double[] totalesVentas(int cajaId) {
        double[] t = new double[5];
        String sql = "SELECT metodo_pago, SUM(total) AS total, COUNT(*) AS cantidad FROM ventas "
                   + "WHERE caja_id = ? AND estado = 'EMITIDA' GROUP BY metodo_pago";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cajaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String metodo = rs.getString("metodo_pago");
                    double total = rs.getDouble("total");
                    if ("Tarjeta".equalsIgnoreCase(metodo)) {
                        t[1] += total;
                    } else if ("Transferencia".equalsIgnoreCase(metodo)) {
                        t[2] += total;
                    } else if ("Crédito".equalsIgnoreCase(metodo)) {
                        t[4] += total;
                    } else {
                        t[0] += total; // Efectivo y cualquier otro valor
                    }
                    t[3] += rs.getInt("cantidad");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return t;
    }

    /** Abonos a créditos cobrados en el turno: [efectivo, tarjeta, transferencia]. */
    public double[] totalesAbonos(int cajaId) {
        double[] t = new double[3];
        String sql = "SELECT metodo, SUM(monto) AS total FROM pagos WHERE caja_id = ? AND tipo = 'ABONO' GROUP BY metodo";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cajaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String metodo = rs.getString("metodo");
                    double total = rs.getDouble("total");
                    if ("Tarjeta".equalsIgnoreCase(metodo)) t[1] += total;
                    else if ("Transferencia".equalsIgnoreCase(metodo)) t[2] += total;
                    else t[0] += total;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return t;
    }

    /** Movimientos de efectivo del turno: [entradas, retiros, gastos, devoluciones en efectivo]. */
    public double[] totalesMovimientos(int cajaId) {
        double[] t = new double[4];
        String sql = "SELECT tipo, SUM(monto) AS total FROM movimientos_caja WHERE caja_id = ? GROUP BY tipo";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cajaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tipo = rs.getString("tipo");
                    double total = rs.getDouble("total");
                    if (MovimientoCaja.Tipo.ENTRADA.name().equals(tipo)) t[0] += total;
                    else if (MovimientoCaja.Tipo.RETIRO.name().equals(tipo)) t[1] += total;
                    else if (MovimientoCaja.Tipo.GASTO.name().equals(tipo)
                            || MovimientoCaja.Tipo.PAGO_PROVEEDOR.name().equals(tipo)) t[2] += total;
                    else if (MovimientoCaja.Tipo.DEVOLUCION.name().equals(tipo)) t[3] += total;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return t;
    }

    // ------------------------------------------------------------------
    // MOVIMIENTOS
    // ------------------------------------------------------------------

    public boolean registrarMovimiento(int cajaId, String fecha, String usuario, String tipo,
                                       double monto, String motivo, String documento) {
        String sql = "INSERT INTO movimientos_caja (caja_id, fecha, usuario, tipo, monto, motivo, documento) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cajaId);
            ps.setString(2, fecha);
            ps.setString(3, usuario);
            ps.setString(4, tipo);
            ps.setDouble(5, monto);
            ps.setString(6, motivo);
            ps.setString(7, documento);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<MovimientoCaja> obtenerMovimientos(int cajaId) {
        List<MovimientoCaja> lista = new ArrayList<>();
        String sql = "SELECT * FROM movimientos_caja WHERE caja_id = ? ORDER BY id DESC";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cajaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new MovimientoCaja(
                            rs.getInt("id"), rs.getInt("caja_id"), rs.getString("fecha"),
                            rs.getString("usuario"), rs.getString("tipo"), rs.getDouble("monto"),
                            rs.getString("motivo"), rs.getString("documento")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    // ------------------------------------------------------------------
    // INICIALIZACIÓN
    // ------------------------------------------------------------------

    /**
     * Copia los cierres hechos antes del módulo de caja (tabla cierres_caja) a la tabla cajas,
     * para que el historial siga completo. Solo corre una vez (cuando cajas está vacía).
     */
    public static void migrarCierresAnteriores(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM cajas")) {
            if (rs.next() && rs.getInt(1) > 0) return;
        }
        String sql = """
            INSERT INTO cajas (estado, usuario_apertura, fecha_apertura, monto_inicial, usuario_cierre, fecha_cierre,
                total_efectivo, total_tarjeta, total_transferencia, total_sistema, entradas, retiros, gastos,
                efectivo_esperado, efectivo_contado, diferencia, observaciones)
            SELECT 'CERRADA', 'Histórico', fecha_apertura, 0, 'Histórico', fecha_cierre,
                total_efectivo, total_tarjeta, total_transferencia, total_sistema, 0, 0, 0,
                total_efectivo, efectivo_contado, diferencia, 'Cierre anterior al módulo de caja'
            FROM cierres_caja ORDER BY id
        """;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    // ------------------------------------------------------------------
    // AUXILIARES
    // ------------------------------------------------------------------

    private static String ultimaFechaCierre(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(fecha_cierre) FROM cajas WHERE estado = 'CERRADA'")) {
            if (rs.next()) return rs.getString(1);
        }
        return null;
    }

    private static Caja mapear(ResultSet rs) throws SQLException {
        Caja c = new Caja();
        c.setId(rs.getInt("id"));
        c.setEstado(rs.getString("estado"));
        c.setUsuarioApertura(rs.getString("usuario_apertura"));
        c.setFechaApertura(rs.getString("fecha_apertura"));
        c.setMontoInicial(rs.getDouble("monto_inicial"));
        c.setUsuarioCierre(rs.getString("usuario_cierre"));
        c.setFechaCierre(rs.getString("fecha_cierre"));
        c.setTotalEfectivo(rs.getDouble("total_efectivo"));
        c.setTotalTarjeta(rs.getDouble("total_tarjeta"));
        c.setTotalTransferencia(rs.getDouble("total_transferencia"));
        c.setTotalSistema(rs.getDouble("total_sistema"));
        c.setEntradas(rs.getDouble("entradas"));
        c.setRetiros(rs.getDouble("retiros"));
        c.setGastos(rs.getDouble("gastos"));
        c.setTotalCredito(rs.getDouble("total_credito"));
        c.setAbonosEfectivo(rs.getDouble("abonos_efectivo"));
        c.setDevoluciones(rs.getDouble("devoluciones"));
        c.setEfectivoEsperado(rs.getDouble("efectivo_esperado"));
        c.setEfectivoContado(rs.getDouble("efectivo_contado"));
        c.setDiferencia(rs.getDouble("diferencia"));
        c.setObservaciones(rs.getString("observaciones"));
        return c;
    }
}
