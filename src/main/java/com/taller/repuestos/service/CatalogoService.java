package com.taller.repuestos.service;

import com.taller.repuestos.dao.CatalogoDAO;
import com.taller.repuestos.dao.CatalogoDAO.Item;
import com.taller.repuestos.dao.CatalogoDAO.Tipo;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Permiso;

import java.util.List;

/** Catálogo de categorías y marcas: crear, renombrar, activar/desactivar y eliminar (solo si no se usan). */
public class CatalogoService {

    private static final int LARGO_MAXIMO = 60;

    private final CatalogoDAO dao = new CatalogoDAO();
    private final PermisoService permisos = PermisoService.get();

    public List<Item> listar(Tipo tipo) {
        return dao.listar(tipo);
    }

    /** Nombres activos, para los combos de los formularios. */
    public List<String> nombres(Tipo tipo) {
        return dao.nombresActivos(tipo);
    }

    public void crear(Tipo tipo, String nombre) throws ReglaNegocioException {
        permisos.exigir(Permiso.INVENTARIO_EDITAR);
        String limpio = validarNombre(tipo, nombre);
        if (dao.existe(tipo, limpio)) {
            throw new ReglaNegocioException("Ya existe " + articulo(tipo) + " \"" + limpio + "\".");
        }
        if (dao.crear(tipo, limpio) <= 0) {
            throw new ReglaNegocioException("No se pudo guardar.");
        }
        AuditoriaService.registrar(AuditoriaService.CATALOGO, "Creó " + articulo(tipo), limpio, null, null);
    }

    /** Si el nombre todavía no está en el catálogo lo agrega (se usa al guardar un producto con un valor nuevo). */
    void asegurar(Tipo tipo, String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) return;
        String limpio = nombre.trim();
        if (limpio.length() <= LARGO_MAXIMO && !dao.existe(tipo, limpio)) {
            if (dao.crear(tipo, limpio) > 0) {
                AuditoriaService.registrar(AuditoriaService.CATALOGO, "Creó " + articulo(tipo), limpio, null, "Desde el formulario de producto");
            }
        }
    }

    public void renombrar(Tipo tipo, Item item, String nuevoNombre) throws ReglaNegocioException {
        permisos.exigir(Permiso.INVENTARIO_EDITAR);
        String limpio = validarNombre(tipo, nuevoNombre);
        if (limpio.equals(item.getNombre())) return;
        // Cambiar solo mayúsculas/minúsculas del mismo nombre es válido; usar el de otro elemento no
        if (!limpio.equalsIgnoreCase(item.getNombre()) && dao.existe(tipo, limpio)) {
            throw new ReglaNegocioException("Ya existe " + articulo(tipo) + " \"" + limpio + "\".");
        }
        if (!dao.renombrar(tipo, item.getId(), item.getNombre(), limpio)) {
            throw new ReglaNegocioException("No se pudo cambiar el nombre.");
        }
        AuditoriaService.registrar(AuditoriaService.CATALOGO, "Renombró " + articulo(tipo), item.getNombre(),
                item.getNombre(), limpio + " (" + item.getUsos() + " uso(s) actualizados)");
    }

    public void cambiarEstado(Tipo tipo, Item item, boolean activo) throws ReglaNegocioException {
        permisos.exigir(Permiso.INVENTARIO_EDITAR);
        if (!dao.cambiarEstado(tipo, item.getId(), activo)) {
            throw new ReglaNegocioException("No se pudo cambiar el estado.");
        }
        AuditoriaService.registrar(AuditoriaService.CATALOGO, (activo ? "Activó " : "Desactivó ") + articulo(tipo),
                item.getNombre(), activo ? "Inactivo" : "Activo", activo ? "Activo" : "Inactivo");
    }

    public void eliminar(Tipo tipo, Item item) throws ReglaNegocioException {
        permisos.exigir(Permiso.INVENTARIO_EDITAR);
        if (item.getUsos() > 0) {
            throw new ReglaNegocioException("\"" + item.getNombre() + "\" lo usan " + item.getUsos()
                    + " producto(s) o vehículo(s) y no se puede eliminar. Puedes desactivarlo para que no aparezca en los formularios.");
        }
        if (!dao.eliminarSiNoSeUsa(tipo, item.getId())) {
            throw new ReglaNegocioException("No se pudo eliminar (puede que alguien ya lo esté usando).");
        }
        AuditoriaService.registrar(AuditoriaService.CATALOGO, "Eliminó " + articulo(tipo), item.getNombre(), item.getNombre(), null);
    }

    // ------------------------------------------------------------------

    private static String validarNombre(Tipo tipo, String nombre) throws ReglaNegocioException {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            throw new ReglaNegocioException("Escribe el nombre " + (tipo == Tipo.CATEGORIA ? "de la categoría." : "de la marca."));
        }
        if (limpio.length() > LARGO_MAXIMO) {
            throw new ReglaNegocioException("El nombre es demasiado largo (máximo " + LARGO_MAXIMO + " caracteres).");
        }
        return limpio;
    }

    private static String articulo(Tipo tipo) {
        return tipo == Tipo.CATEGORIA ? "la categoría" : "la marca";
    }
}
