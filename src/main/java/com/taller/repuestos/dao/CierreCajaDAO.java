package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CierreCajaDAO {

    public static class EntradaCierre {
        private int id;
        private String fechaApertura;
        private String fechaCierre;
        private double totalEfectivo;
        private double totalTarjeta;
        private double totalTransferencia;
        private double totalSistema;
        private double efectivoContado;
        private double diferencia;

        public EntradaCierre(int id, String fechaApertura, String fechaCierre, double totalEfectivo,
                              double totalTarjeta, double totalTransferencia, double totalSistema,
                              double efectivoContado, double diferencia) {
            this.id = id;
            this.fechaApertura = fechaApertura;
            this.fechaCierre = fechaCierre;
            this.totalEfectivo = totalEfectivo;
            this.totalTarjeta = totalTarjeta;
            this.totalTransferencia = totalTransferencia;
            this.totalSistema = totalSistema;
            this.efectivoContado = efectivoContado;
            this.diferencia = diferencia;
        }

        public int getId() { return id; }
        public String getFechaApertura() { return fechaApertura; }
        public String getFechaCierre() { return fechaCierre; }
        public double getTotalEfectivo() { return totalEfectivo; }
        public double getTotalTarjeta() { return totalTarjeta; }
        public double getTotalTransferencia() { return totalTransferencia; }
        public double getTotalSistema() { return totalSistema; }
        public double getEfectivoContado() { return efectivoContado; }
        public double getDiferencia() { return diferencia; }
    }

    /** Fecha de cierre del último turno registrado, o null si nunca se ha hecho un cierre. */
    public String obtenerFechaUltimoCierre() {
        String sql = "SELECT fecha_cierre FROM cierres_caja ORDER BY id DESC LIMIT 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getString("fecha_cierre");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean registrarCierre(String fechaApertura, String fechaCierre, double totalEfectivo,
                                    double totalTarjeta, double totalTransferencia, double totalSistema,
                                    double efectivoContado, double diferencia) {
        String sql = """
            INSERT INTO cierres_caja
                (fecha_apertura, fecha_cierre, total_efectivo, total_tarjeta, total_transferencia, total_sistema, efectivo_contado, diferencia)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fechaApertura);
            stmt.setString(2, fechaCierre);
            stmt.setDouble(3, totalEfectivo);
            stmt.setDouble(4, totalTarjeta);
            stmt.setDouble(5, totalTransferencia);
            stmt.setDouble(6, totalSistema);
            stmt.setDouble(7, efectivoContado);
            stmt.setDouble(8, diferencia);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<EntradaCierre> obtenerHistorialCierres(int limite) {
        List<EntradaCierre> lista = new ArrayList<>();
        String sql = "SELECT * FROM cierres_caja ORDER BY id DESC LIMIT ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limite);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(new EntradaCierre(
                        rs.getInt("id"),
                        rs.getString("fecha_apertura"),
                        rs.getString("fecha_cierre"),
                        rs.getDouble("total_efectivo"),
                        rs.getDouble("total_tarjeta"),
                        rs.getDouble("total_transferencia"),
                        rs.getDouble("total_sistema"),
                        rs.getDouble("efectivo_contado"),
                        rs.getDouble("diferencia")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }
}