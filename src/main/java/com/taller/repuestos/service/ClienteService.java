package com.taller.repuestos.service;

import com.taller.repuestos.dao.ClienteDAO;
import com.taller.repuestos.dao.VentaDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Cliente;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.util.Formato;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reglas de clientes: validaciones, permisos y auditoría. Los clientes no se borran, se desactivan. */
public class ClienteService {

    private final ClienteDAO dao = new ClienteDAO();
    private final VentaDAO ventaDAO = new VentaDAO();
    private final PermisoService permisos = PermisoService.get();

    /** Clientes con su saldo pendiente. Los ven quienes consultan clientes y quienes venden. */
    public List<Cliente> listar() {
        if (!permisos.tiene(Permiso.CLIENTES_VER) && !permisos.tiene(Permiso.POS_USAR)) return new ArrayList<>();
        return dao.obtenerTodos();
    }

    public Cliente obtener(int id) {
        return dao.obtenerPorId(id);
    }

    public List<VentaDAO.Venta> compras(int clienteId) {
        return ventaDAO.obtenerVentasDeCliente(clienteId);
    }

    public List<VentaDAO.CreditoPendiente> creditosPendientes(int clienteId) {
        return ventaDAO.obtenerCreditosPendientes(clienteId);
    }

    public Cliente crear(Cliente c) throws ReglaNegocioException {
        permisos.exigir(Permiso.CLIENTES_EDITAR);
        validar(c);
        int id = dao.crear(c);
        if (id <= 0) throw new ReglaNegocioException("No se pudo guardar el cliente.");
        AuditoriaService.registrar(AuditoriaService.CLIENTES, "Creó el cliente", c.getNombre(), null, resumen(c));
        return dao.obtenerPorId(id);
    }

    public void actualizar(Cliente original, Cliente nuevo) throws ReglaNegocioException {
        permisos.exigir(Permiso.CLIENTES_EDITAR);
        validar(nuevo);
        if (!dao.actualizar(nuevo)) throw new ReglaNegocioException("No se pudo actualizar el cliente.");
        if (!resumen(original).equals(resumen(nuevo))) {
            AuditoriaService.registrar(AuditoriaService.CLIENTES, "Modificó el cliente", original.getNombre(),
                    resumen(original), resumen(nuevo));
        }
    }

    public void cambiarEstado(Cliente c, boolean activo) throws ReglaNegocioException {
        permisos.exigir(Permiso.CLIENTES_EDITAR);
        if (!dao.cambiarEstado(c.getId(), activo)) throw new ReglaNegocioException("No se pudo cambiar el estado del cliente.");
        AuditoriaService.registrar(AuditoriaService.CLIENTES, activo ? "Activó el cliente" : "Desactivó el cliente",
                c.getNombre(), activo ? "Inactivo" : "Activo", activo ? "Activo" : "Inactivo");
    }

    // ------------------------------------------------------------------

    private void validar(Cliente c) throws ReglaNegocioException {
        if (c.getNombre() == null || c.getNombre().trim().isEmpty()) {
            throw new ReglaNegocioException("El nombre del cliente es obligatorio.");
        }
        if (!Cliente.TIPOS.contains(c.getTipo())) {
            throw new ReglaNegocioException("Elige un tipo de cliente válido.");
        }
        double limite = c.getLimiteCredito();
        if (Double.isNaN(limite) || Double.isInfinite(limite) || limite < 0) {
            throw new ReglaNegocioException("El límite de crédito no puede ser negativo.");
        }
        String correo = c.getCorreo();
        if (correo != null && !correo.trim().isEmpty() && !correo.contains("@")) {
            throw new ReglaNegocioException("El correo no parece válido.");
        }
        String ident = c.getIdentificacion();
        if (ident != null && !ident.trim().isEmpty() && dao.existeIdentificacion(ident.trim(), c.getId())) {
            throw new ReglaNegocioException("Ya hay otro cliente con la identificación \"" + ident.trim() + "\".");
        }
    }

    private static String resumen(Cliente c) {
        return "Nombre: " + nz(c.getNombre()) + "; identificación: " + nz(c.getIdentificacion())
                + "; teléfono: " + nz(c.getTelefono()) + "; tipo: " + nz(c.getTipo())
                + "; límite de crédito: " + Formato.moneda(c.getLimiteCredito());
    }

    private static String nz(String s) {
        return Objects.toString(s, "");
    }
}
