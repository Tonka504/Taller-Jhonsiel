package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ejecuta las consultas de solo lectura de reportes y dashboard.
 * Las consultas se arman en el servicio con parámetros (?), nunca concatenando texto del usuario.
 */
public class ReporteDAO {

    /** Filas de una consulta; cada fila es un arreglo con los valores de sus columnas en orden. */
    public List<Object[]> filas(String sql, List<Object> params) {
        List<Object[]> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                int n = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Object[] fila = new Object[n];
                    for (int i = 0; i < n; i++) fila[i] = rs.getObject(i + 1);
                    lista.add(fila);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public List<Object[]> filas(String sql, Object... params) {
        return filas(sql, List.of(params));
    }

    /** Primer valor numérico de la consulta (0 si no hay filas). */
    public double numero(String sql, Object... params) {
        List<Object[]> f = filas(sql, List.of(params));
        if (f.isEmpty() || f.get(0)[0] == null) return 0;
        Object v = f.get(0)[0];
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
