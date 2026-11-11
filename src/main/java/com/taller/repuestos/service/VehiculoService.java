package com.taller.repuestos.service;

import com.taller.repuestos.dao.CatalogoDAO;
import com.taller.repuestos.dao.VehiculoDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Vehiculo;

import java.time.Year;
import java.util.List;

/** Vehículos de los clientes: validación, permisos y auditoría. */
public class VehiculoService {

    private final VehiculoDAO dao = new VehiculoDAO();
    private final CatalogoService catalogo;
    private final PermisoService permisos = PermisoService.get();

    public VehiculoService(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    public List<Vehiculo> buscar(Integer clienteId, String texto, boolean soloActivos) {
        return dao.buscar(clienteId, texto, soloActivos);
    }

    public Vehiculo obtener(int id) { return dao.obtener(id); }

    public Vehiculo guardar(Vehiculo v) throws ReglaNegocioException {
        permisos.exigir(Permiso.VEHICULOS_EDITAR);
        v.setMarca(nz(v.getMarca()));
        v.setModelo(nz(v.getModelo()));
        v.setPlaca(nz(v.getPlaca()).toUpperCase());
        v.setVin(nz(v.getVin()).toUpperCase());
        if (v.getClienteId() <= 0) throw new ReglaNegocioException("Elige el cliente dueño del vehículo.");
        if (v.getMarca().isEmpty() || v.getModelo().isEmpty()) throw new ReglaNegocioException("La marca y el modelo son obligatorios.");
        int maxAnio = Year.now().getValue() + 1;
        if (v.getAnio() != null && (v.getAnio() < 1950 || v.getAnio() > maxAnio)) {
            throw new ReglaNegocioException("El año debe estar entre 1950 y " + maxAnio + ".");
        }
        if (v.getKilometraje() != null && v.getKilometraje() < 0) throw new ReglaNegocioException("El kilometraje no puede ser negativo.");
        if (!v.getPlaca().isEmpty() && dao.existe("placa", v.getPlaca(), v.getId())) {
            throw new ReglaNegocioException("Ya hay otro vehículo registrado con la placa " + v.getPlaca() + ".");
        }
        if (!v.getVin().isEmpty() && dao.existe("vin", v.getVin(), v.getId())) {
            throw new ReglaNegocioException("Ya hay otro vehículo registrado con el VIN " + v.getVin() + ".");
        }
        if (v.getPlaca().isEmpty()) v.setPlaca(null);
        if (v.getVin().isEmpty()) v.setVin(null);
        catalogo.asegurar(CatalogoDAO.Tipo.MARCA, v.getMarca());
        if (v.getId() == 0) {
            int id = dao.crear(v);
            if (id <= 0) throw new ReglaNegocioException("No se pudo guardar el vehículo.");
            v.setId(id);
            AuditoriaService.registrar(AuditoriaService.TALLER, "Registró un vehículo", v.getDescripcion(), null,
                    "Cliente id " + v.getClienteId());
        } else {
            Vehiculo antes = dao.obtener(v.getId());
            if (!dao.actualizar(v)) throw new ReglaNegocioException("No se pudo actualizar el vehículo.");
            AuditoriaService.registrar(AuditoriaService.TALLER, "Modificó un vehículo", v.getDescripcion(),
                    antes == null ? null : antes.getDescripcion() + " (" + nz(antes.getClienteNombre()) + ")", v.getDescripcion());
        }
        return v;
    }

    void actualizarKilometraje(int vehiculoId, Integer km) {
        if (km != null && km > 0) dao.actualizarKilometraje(vehiculoId, km);
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }
}
