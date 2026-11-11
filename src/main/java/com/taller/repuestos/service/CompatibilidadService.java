package com.taller.repuestos.service;

import com.taller.repuestos.dao.CatalogoDAO;
import com.taller.repuestos.dao.CompatibilidadDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Compatibilidad;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Repuesto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Vehículos compatibles de cada repuesto, y las listas para los filtros en cascada del buscador. */
public class CompatibilidadService {

    public static final List<String> COMBUSTIBLES = List.of("Gasolina", "Diésel", "Híbrido", "Eléctrico", "Gas");
    public static final List<String> TRANSMISIONES = List.of("Manual", "Automática", "CVT");

    private static final int LARGO_MAXIMO = 60;

    private final CompatibilidadDAO dao = new CompatibilidadDAO();
    private final CatalogoService catalogo;
    private final PermisoService permisos = PermisoService.get();

    public CompatibilidadService(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    public List<Compatibilidad> listar(int repuestoId) {
        return dao.listarPorRepuesto(repuestoId);
    }

    /** Todas las compatibilidades agrupadas por producto (para el buscador, que trabaja en memoria). */
    public Map<Integer, List<Compatibilidad>> porRepuesto() {
        Map<Integer, List<Compatibilidad>> mapa = new HashMap<>();
        for (Compatibilidad c : dao.listarTodas()) {
            mapa.computeIfAbsent(c.getRepuestoId(), k -> new ArrayList<>()).add(c);
        }
        return mapa;
    }

    public Compatibilidad agregar(Repuesto r, String marca, String modelo, Integer desde, Integer hasta,
                                  String motor, String combustible, String transmision, String observaciones)
            throws ReglaNegocioException {
        permisos.exigir(Permiso.INVENTARIO_EDITAR);

        String marcaLimpia = limpiar(marca);
        String modeloLimpio = limpiar(modelo);
        if (marcaLimpia.isEmpty()) throw new ReglaNegocioException("Escribe la marca del vehículo.");
        if (modeloLimpio.isEmpty()) throw new ReglaNegocioException("Escribe el modelo del vehículo.");
        if (marcaLimpia.length() > LARGO_MAXIMO || modeloLimpio.length() > LARGO_MAXIMO) {
            throw new ReglaNegocioException("La marca y el modelo pueden tener hasta " + LARGO_MAXIMO + " caracteres.");
        }
        validarAnio(desde, "inicial");
        validarAnio(hasta, "final");
        if (desde != null && hasta != null && desde > hasta) {
            throw new ReglaNegocioException("El año inicial no puede ser mayor que el año final.");
        }

        Compatibilidad nueva = new Compatibilidad(0, r.getId(), marcaLimpia, modeloLimpio, desde, hasta,
                vacioANull(motor), vacioANull(combustible), vacioANull(transmision), vacioANull(observaciones));
        if (dao.existeIgual(nueva)) {
            throw new ReglaNegocioException("Este vehículo ya está registrado para el producto.");
        }
        int id = dao.agregar(nueva);
        if (id <= 0) throw new ReglaNegocioException("No se pudo guardar la compatibilidad.");

        catalogo.asegurar(CatalogoDAO.Tipo.MARCA, marcaLimpia); // la marca del vehículo queda disponible en el catálogo
        AuditoriaService.registrar(AuditoriaService.INVENTARIO, "Agregó compatibilidad", r.getCodigo() + " - " + r.getDescripcion(),
                null, nueva.getResumen());
        return new Compatibilidad(id, r.getId(), marcaLimpia, modeloLimpio, desde, hasta, nueva.getMotor(),
                nueva.getCombustible(), nueva.getTransmision(), nueva.getObservaciones());
    }

    public void eliminar(Repuesto r, Compatibilidad c) throws ReglaNegocioException {
        permisos.exigir(Permiso.INVENTARIO_EDITAR);
        if (!dao.eliminar(c.getId())) throw new ReglaNegocioException("No se pudo quitar la compatibilidad.");
        AuditoriaService.registrar(AuditoriaService.INVENTARIO, "Quitó compatibilidad", r.getCodigo() + " - " + r.getDescripcion(),
                c.getResumen(), null);
    }

    // ------------------------------------------------------------------
    // Listas para los filtros en cascada: Marca -> Modelo -> Motor
    // ------------------------------------------------------------------

    public static List<String> marcasDeVehiculos(Map<Integer, List<Compatibilidad>> mapa) {
        TreeSet<String> set = nuevoConjunto();
        for (List<Compatibilidad> lista : mapa.values()) {
            for (Compatibilidad c : lista) set.add(c.getMarcaVehiculo());
        }
        return new ArrayList<>(set);
    }

    public static List<String> modelosDe(Map<Integer, List<Compatibilidad>> mapa, String marca) {
        TreeSet<String> set = nuevoConjunto();
        for (List<Compatibilidad> lista : mapa.values()) {
            for (Compatibilidad c : lista) {
                if (marca != null && marca.equalsIgnoreCase(c.getMarcaVehiculo())) set.add(c.getModelo());
            }
        }
        return new ArrayList<>(set);
    }

    public static List<String> motoresDe(Map<Integer, List<Compatibilidad>> mapa, String marca, String modelo) {
        TreeSet<String> set = nuevoConjunto();
        for (List<Compatibilidad> lista : mapa.values()) {
            for (Compatibilidad c : lista) {
                if (marca != null && marca.equalsIgnoreCase(c.getMarcaVehiculo())
                        && modelo != null && modelo.equalsIgnoreCase(c.getModelo())
                        && c.getMotor() != null && !c.getMotor().trim().isEmpty()) {
                    set.add(c.getMotor().trim());
                }
            }
        }
        return new ArrayList<>(set);
    }

    // ------------------------------------------------------------------

    private static TreeSet<String> nuevoConjunto() {
        return new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    private static void validarAnio(Integer anio, String cual) throws ReglaNegocioException {
        if (anio != null && (anio < 1950 || anio > 2100)) {
            throw new ReglaNegocioException("El año " + cual + " debe estar entre 1950 y 2100.");
        }
    }

    private static String limpiar(String s) {
        return s == null ? "" : s.trim();
    }

    private static String vacioANull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
