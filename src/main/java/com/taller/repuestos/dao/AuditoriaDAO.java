package com.taller.repuestos.dao;

import com.taller.repuestos.config.Database;
import com.taller.repuestos.model.RegistroAuditoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Guarda y consulta el historial de auditoría. Solo se agrega: nunca se modifica ni se borra. */
public class AuditoriaDAO {

    public boolean registrar(String fecha, String usuario, String modulo, String accion,
                             String registro, String valorAnterior, String valorNuevo) {
        String sql = "INSERT INTO auditoria (fecha, usuario, modulo, accion, registro, valor_anterior, valor_nuevo) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fecha);
            ps.setString(2, usuario);
            ps.setString(3, modulo);
            ps.setString(4, accion);
            ps.setString(5, registro);
            ps.setString(6, valorAnterior);
            ps.setString(7, valorNuevo);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Busca registros, del más reciente al más antiguo. Cualquier filtro en null o vacío se ignora.
     * @param desde  fecha inicial "yyyy-MM-dd" (incluida)
     * @param hasta  fecha final "yyyy-MM-dd" (incluida)
     * @param texto  texto a buscar en acción, registro y valores
     */
    public List<RegistroAuditoria> buscar(String desde, String hasta, String usuario, String modulo,
                                          String texto, int limite) {
        StringBuilder sql = new StringBuilder("SELECT * FROM auditoria WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (desde != null && !desde.isEmpty()) {
            sql.append(" AND fecha >= ?");
            params.add(desde + " 00:00:00");
        }
        if (hasta != null && !hasta.isEmpty()) {
            sql.append(" AND fecha <= ?");
            params.add(hasta + " 23:59:59");
        }
        if (usuario != null && !usuario.isEmpty()) {
            sql.append(" AND usuario = ?");
            params.add(usuario);
        }
        if (modulo != null && !modulo.isEmpty()) {
            sql.append(" AND modulo = ?");
            params.add(modulo);
        }
        if (texto != null && !texto.trim().isEmpty()) {
            sql.append(" AND (accion LIKE ? OR registro LIKE ? OR valor_anterior LIKE ? OR valor_nuevo LIKE ?)");
            String patron = "%" + texto.trim() + "%";
            for (int i = 0; i < 4; i++) params.add(patron);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(limite);

        List<RegistroAuditoria> lista = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new RegistroAuditoria(
                            rs.getInt("id"),
                            rs.getString("fecha"),
                            rs.getString("usuario"),
                            rs.getString("modulo"),
                            rs.getString("accion"),
                            rs.getString("registro"),
                            rs.getString("valor_anterior"),
                            rs.getString("valor_nuevo")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    public List<String> usuariosRegistrados() {
        return listarDistintos("usuario");
    }

    public List<String> modulosRegistrados() {
        return listarDistintos("modulo");
    }

    private List<String> listarDistintos(String columna) {
        List<String> lista = new ArrayList<>();
        // 'columna' siempre es un valor fijo escrito en esta clase, nunca texto del usuario
        String sql = "SELECT DISTINCT " + columna + " FROM auditoria WHERE " + columna + " IS NOT NULL ORDER BY " + columna;
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) lista.add(rs.getString(1));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }
}
