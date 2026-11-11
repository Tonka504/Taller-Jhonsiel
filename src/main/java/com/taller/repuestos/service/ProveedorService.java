package com.taller.repuestos.service;

import com.taller.repuestos.dao.ProveedorDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Proveedor;

import java.util.List;
import java.util.Objects;

/** Reglas de proveedores: validación, permisos y auditoría. */
public class ProveedorService {

    private final ProveedorDAO dao = new ProveedorDAO();
    private final PermisoService permisos = PermisoService.get();

    public List<Proveedor> listar() { return dao.obtenerTodos(); }

    public Proveedor obtener(int id) { return dao.obtenerPorId(id); }

    public List<String> nombresActivos() { return dao.nombresActivos(); }

    public List<ProveedorDAO.ProductoSuministrado> productosSuministrados(int proveedorId) {
        return dao.productosSuministrados(proveedorId);
    }

    public Proveedor crear(Proveedor p) throws ReglaNegocioException {
        permisos.exigir(Permiso.PROVEEDORES_EDITAR);
        normalizar(p);
        validar(p, 0);
        int id = dao.crear(p);
        if (id <= 0) throw new ReglaNegocioException("No se pudo guardar el proveedor.");
        p.setId(id);
        AuditoriaService.registrar(AuditoriaService.PROVEEDORES, "Creó el proveedor", p.getNombre(), null, resumen(p));
        return p;
    }

    public void actualizar(Proveedor original, Proveedor nuevo) throws ReglaNegocioException {
        permisos.exigir(Permiso.PROVEEDORES_EDITAR);
        normalizar(nuevo);
        validar(nuevo, original.getId());
        nuevo.setId(original.getId());
        if (!dao.actualizar(nuevo, original.getNombre())) {
            throw new ReglaNegocioException("No se pudo actualizar el proveedor.");
        }
        String antes = resumen(original), despues = resumen(nuevo);
        if (!Objects.equals(antes, despues)) {
            AuditoriaService.registrar(AuditoriaService.PROVEEDORES, "Modificó el proveedor", original.getNombre(), antes, despues);
        }
    }

    private void validar(Proveedor p, int idActual) throws ReglaNegocioException {
        if (p.getNombre().isEmpty()) throw new ReglaNegocioException("El nombre del proveedor es obligatorio.");
        if (p.getNombre().length() > 80) throw new ReglaNegocioException("El nombre puede tener hasta 80 caracteres.");
        if (dao.existeNombre(p.getNombre(), idActual)) {
            throw new ReglaNegocioException("Ya existe un proveedor llamado \"" + p.getNombre() + "\".");
        }
        if (!p.getCorreo().isEmpty() && !p.getCorreo().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ReglaNegocioException("El correo no tiene un formato válido.");
        }
    }

    private static void normalizar(Proveedor p) {
        p.setNombre(nz(p.getNombre()));
        p.setEmpresa(nz(p.getEmpresa()));
        p.setIdentificacionFiscal(nz(p.getIdentificacionFiscal()));
        p.setTelefono(nz(p.getTelefono()));
        p.setDireccion(nz(p.getDireccion()));
        p.setCorreo(nz(p.getCorreo()));
        p.setContacto(nz(p.getContacto()));
        p.setObservaciones(nz(p.getObservaciones()));
    }

    private static String resumen(Proveedor p) {
        return "Nombre: " + p.getNombre() + "; empresa: " + nz(p.getEmpresa()) + "; RTN: " + nz(p.getIdentificacionFiscal())
                + "; teléfono: " + nz(p.getTelefono()) + "; correo: " + nz(p.getCorreo()) + "; contacto: " + nz(p.getContacto())
                + "; estado: " + p.getEstado();
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }
}
