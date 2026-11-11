package com.taller.repuestos.service;

import com.taller.repuestos.dao.UsuarioDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Usuario;
import com.taller.repuestos.util.Sesion;

import java.util.ArrayList;
import java.util.List;

/** Inicio de sesión y administración de usuarios, con permisos y auditoría. */
public class UsuarioService {

    private final UsuarioDAO dao = new UsuarioDAO();
    private final PermisoService permisos = PermisoService.get();

    /** Valida usuario y contraseña. Registra en la auditoría tanto los accesos como los intentos fallidos. */
    public Usuario autenticar(String username, String password) {
        Usuario usuario = dao.autenticar(username, password);
        if (usuario == null) {
            AuditoriaService.registrarComo(username == null || username.isEmpty() ? "(sin usuario)" : username,
                    AuditoriaService.SESION, "Intento de acceso fallido", username, null, null);
        } else {
            AuditoriaService.registrarComo(usuario.getNombreCompleto(),
                    AuditoriaService.SESION, "Inicio de sesión", usuario.getUsername(), null, null);
        }
        return usuario;
    }

    public List<Usuario> listar() {
        if (!permisos.tiene(Permiso.USUARIOS_ADMIN)) return new ArrayList<>();
        return dao.obtenerTodos();
    }

    public void crear(String username, String password, String nombre, String rol) throws ReglaNegocioException {
        permisos.exigir(Permiso.USUARIOS_ADMIN);
        dao.crear(username, password, nombre, rol);
        AuditoriaService.registrar(AuditoriaService.USUARIOS, "Creó el usuario", username.trim(), null,
                "Nombre: " + nombre.trim() + "; rol: " + rol);
    }

    public void cambiarPassword(Usuario usuario, String nuevaPassword) throws ReglaNegocioException {
        permisos.exigir(Permiso.USUARIOS_ADMIN);
        dao.cambiarPassword(usuario.getId(), nuevaPassword);
        // Nunca se guarda la contraseña en la auditoría, ni siquiera cifrada
        AuditoriaService.registrar(AuditoriaService.USUARIOS, "Cambió la contraseña", usuario.getUsername(), null, null);
    }

    public void cambiarRol(Usuario usuario, String nuevoRol) throws ReglaNegocioException {
        permisos.exigir(Permiso.USUARIOS_ADMIN);
        dao.cambiarRol(usuario.getId(), nuevoRol, idSesion());
        AuditoriaService.registrar(AuditoriaService.USUARIOS, "Cambió el rol", usuario.getUsername(),
                usuario.getRol(), nuevoRol);
    }

    public void cambiarEstado(Usuario usuario, boolean activo) throws ReglaNegocioException {
        permisos.exigir(Permiso.USUARIOS_ADMIN);
        dao.cambiarEstado(usuario.getId(), activo, idSesion());
        AuditoriaService.registrar(AuditoriaService.USUARIOS, activo ? "Activó el usuario" : "Desactivó el usuario",
                usuario.getUsername(), activo ? "Inactivo" : "Activo", activo ? "Activo" : "Inactivo");
    }

    private static int idSesion() {
        Usuario u = Sesion.getUsuario();
        return u == null ? -1 : u.getId();
    }
}
