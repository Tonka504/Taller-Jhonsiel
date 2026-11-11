package com.taller.repuestos.service;

import com.taller.repuestos.dao.PermisoDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Rol;
import com.taller.repuestos.model.Usuario;
import com.taller.repuestos.util.Sesion;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Decide qué puede hacer cada rol. El administrador siempre puede todo. */
public class PermisoService {

    private static PermisoService instancia;

    public static synchronized PermisoService get() {
        if (instancia == null) instancia = new PermisoService();
        return instancia;
    }

    private final PermisoDAO dao = new PermisoDAO();
    private Map<String, Set<String>> porRol = new HashMap<>();

    private PermisoService() {
        recargar();
    }

    public synchronized void recargar() {
        porRol = dao.obtenerTodo();
    }

    public synchronized boolean tiene(String rol, Permiso permiso) {
        if (Rol.ADMIN.name().equals(rol)) return true;
        Set<String> permisos = porRol.get(rol);
        return permisos != null && permisos.contains(permiso.name());
    }

    public boolean tiene(Usuario usuario, Permiso permiso) {
        return usuario != null && tiene(usuario.getRol(), permiso);
    }

    /** ¿El usuario con la sesión iniciada tiene este permiso? */
    public boolean tiene(Permiso permiso) {
        return tiene(Sesion.getUsuario(), permiso);
    }

    /** Lanza un error con mensaje claro si el usuario de la sesión no tiene el permiso. */
    public void exigir(Permiso permiso) throws ReglaNegocioException {
        if (!tiene(permiso)) {
            throw new ReglaNegocioException("No tienes permiso para: " + permiso.getEtiqueta().toLowerCase() + ".");
        }
    }

    public synchronized Set<Permiso> permisosDe(String rol) {
        Set<Permiso> resultado = EnumSet.noneOf(Permiso.class);
        for (Permiso p : Permiso.values()) {
            if (tiene(rol, p)) resultado.add(p);
        }
        return resultado;
    }

    /** Guarda los permisos de un rol (el de ADMIN no se puede cambiar). */
    public synchronized void guardarPermisos(String rol, Set<Permiso> permisos) throws ReglaNegocioException {
        exigir(Permiso.USUARIOS_ADMIN);
        if (Rol.ADMIN.name().equals(rol)) {
            throw new ReglaNegocioException("Los permisos del administrador no se pueden modificar.");
        }
        if (!Rol.esValido(rol)) {
            throw new ReglaNegocioException("El rol indicado no es válido.");
        }
        Set<Permiso> anteriores = permisosDe(rol);
        Set<String> nombres = new HashSet<>();
        for (Permiso p : permisos) nombres.add(p.name());
        if (!dao.reemplazar(rol, nombres)) {
            throw new ReglaNegocioException("No se pudieron guardar los permisos.");
        }
        recargar();
        AuditoriaService.registrar(AuditoriaService.USUARIOS, "Cambió los permisos del rol", rol,
                resumen(anteriores), resumen(permisos));
    }

    private static String resumen(Set<Permiso> permisos) {
        if (permisos.isEmpty()) return "(ninguno)";
        StringBuilder sb = new StringBuilder();
        for (Permiso p : Permiso.values()) {
            if (permisos.contains(p)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p.name());
            }
        }
        return sb.toString();
    }
}
