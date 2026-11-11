package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/** Lee y guarda la configuración del sistema (tabla clave/valor). */
public class ConfiguracionDAO {

    public Map<String, String> obtenerTodo() {
        Map<String, String> mapa = new HashMap<>();
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT clave, valor FROM configuracion")) {
            while (rs.next()) {
                mapa.put(rs.getString("clave"), rs.getString("valor"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mapa;
    }

    public boolean guardar(String clave, String valor) {
        String sql = "INSERT INTO configuracion (clave, valor) VALUES (?, ?) "
                   + "ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clave);
            ps.setString(2, valor);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
