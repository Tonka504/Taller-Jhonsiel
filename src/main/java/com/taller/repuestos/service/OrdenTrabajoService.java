package com.taller.repuestos.service;

import com.taller.repuestos.dao.OrdenTrabajoDAO;
import com.taller.repuestos.dao.UsuarioDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Cliente;
import com.taller.repuestos.model.ItemCarrito;
import com.taller.repuestos.model.OrdenTrabajo;
import com.taller.repuestos.model.Permiso;
import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.model.Rol;
import com.taller.repuestos.model.Usuario;
import com.taller.repuestos.model.Vehiculo;
import com.taller.repuestos.util.Formato;
import com.taller.repuestos.util.Sesion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Órdenes de trabajo: Recibido → Diagnóstico → En reparación → Listo → Entregado (o Cancelado).
 * Los repuestos se descuentan del inventario al confirmar su consumo; al facturar se crea una venta
 * (con su pago, caja y auditoría) ligada a la orden.
 */
public class OrdenTrabajoService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrdenTrabajoDAO dao = new OrdenTrabajoDAO();
    private final VehiculoService vehiculos;
    private final ClienteService clientes;
    private final VentaService ventas;
    private final PermisoService permisos = PermisoService.get();

    public OrdenTrabajoService(VehiculoService vehiculos, ClienteService clientes, VentaService ventas) {
        this.vehiculos = vehiculos;
        this.clientes = clientes;
        this.ventas = ventas;
    }

    public List<OrdenTrabajo> listar(String estado, Integer vehiculoId, String texto, LocalDate desde, LocalDate hasta) {
        return dao.listar(estado, vehiculoId, texto, desde == null ? null : desde.toString(), hasta == null ? null : hasta.toString());
    }

    public OrdenTrabajo obtener(int id) { return dao.obtener(id); }

    public java.util.Map<String, Integer> contarPorEstado() { return dao.contarPorEstado(); }

    /** Nombres de los usuarios activos con rol Mecánico (para asignar trabajos). */
    public List<String> mecanicos() {
        List<String> lista = new ArrayList<>();
        for (Usuario u : new UsuarioDAO().obtenerTodos()) {
            if (u.isActivo() && Rol.MECANICO.name().equals(u.getRol())) lista.add(u.getNombreCompleto());
        }
        return lista;
    }

    // ------------------------------------------------------------------
    // CREAR Y EDITAR
    // ------------------------------------------------------------------

    public OrdenTrabajo crear(Vehiculo v, Integer kilometraje, String problema, LocalDate fechaPromesa, String mecanico)
            throws ReglaNegocioException {
        permisos.exigir(Permiso.ORDENES_EDITAR);
        if (v == null) throw new ReglaNegocioException("Elige el vehículo que ingresa al taller.");
        Cliente c = clientes.obtener(v.getClienteId());
        if (c == null) throw new ReglaNegocioException("El dueño del vehículo ya no existe.");
        if (!c.isActivo()) throw new ReglaNegocioException("El cliente \"" + c.getNombre() + "\" está inactivo.");
        if (problema == null || problema.trim().isEmpty()) throw new ReglaNegocioException("Describe el problema o servicio solicitado.");
        if (kilometraje != null && kilometraje < 0) throw new ReglaNegocioException("El kilometraje no puede ser negativo.");
        if (fechaPromesa != null && fechaPromesa.isBefore(LocalDate.now())) {
            throw new ReglaNegocioException("La fecha prometida de entrega no puede ser anterior a hoy.");
        }
        OrdenTrabajo o = new OrdenTrabajo();
        o.setClienteId(c.getId());
        o.setVehiculoId(v.getId());
        o.setFechaIngreso(ahora());
        o.setFechaPromesa(fechaPromesa == null ? null : fechaPromesa.toString());
        o.setKilometraje(kilometraje);
        o.setProblema(problema.trim());
        o.setMecanico(vacioANull(mecanico));
        o.setUsuario(Sesion.nombreActual());
        int id = dao.crear(o);
        if (id <= 0) throw new ReglaNegocioException("No se pudo crear la orden de trabajo.");
        vehiculos.actualizarKilometraje(v.getId(), kilometraje);
        AuditoriaService.registrar(AuditoriaService.TALLER, "Creó la orden de trabajo", Formato.numeroOrden(id), null,
                "Cliente: " + c.getNombre() + "; vehículo: " + v.getDescripcion() + "; problema: " + problema.trim());
        return dao.obtener(id);
    }

    /** Guarda diagnóstico, observaciones, mecánico, kilometraje y fecha prometida. */
    public void guardarDatos(OrdenTrabajo o) throws ReglaNegocioException {
        permisos.exigir(Permiso.ORDENES_EDITAR);
        exigirAbierta(o);
        if (o.getKilometraje() != null && o.getKilometraje() < 0) throw new ReglaNegocioException("El kilometraje no puede ser negativo.");
        o.setMecanico(vacioANull(o.getMecanico()));
        if (!dao.actualizarDatos(o)) throw new ReglaNegocioException("No se pudieron guardar los datos de la orden.");
        vehiculos.actualizarKilometraje(o.getVehiculoId(), o.getKilometraje());
    }

    /** Mueve la orden entre los estados de trabajo (Recibido, Diagnóstico, En reparación, Listo). */
    public void cambiarEstado(OrdenTrabajo o, String estado) throws ReglaNegocioException {
        permisos.exigir(Permiso.ORDENES_EDITAR);
        exigirAbierta(o);
        if (!OrdenTrabajo.ESTADOS_ABIERTOS.contains(estado)) {
            throw new ReglaNegocioException("Para entregar usa \"Facturar y entregar\"; para cancelar usa \"Cancelar orden\".");
        }
        if (estado.equals(o.getEstado())) return;
        if (!dao.cambiarEstado(o.getId(), estado, null)) throw new ReglaNegocioException("No se pudo cambiar el estado.");
        AuditoriaService.registrar(AuditoriaService.TALLER, "Cambió el estado de la orden", Formato.numeroOrden(o.getId()),
                o.getEstadoEtiqueta(), OrdenTrabajo.etiqueta(estado));
        o.setEstado(estado);
    }

    public void agregarServicio(OrdenTrabajo o, String descripcion, String mecanico, double horas, double precio)
            throws ReglaNegocioException {
        permisos.exigir(Permiso.ORDENES_EDITAR);
        exigirEditable(o);
        if (descripcion == null || descripcion.trim().isEmpty()) throw new ReglaNegocioException("Describe el servicio o la mano de obra.");
        if (horas < 0 || Double.isNaN(horas)) throw new ReglaNegocioException("Las horas no pueden ser negativas.");
        if (precio < 0 || Double.isNaN(precio)) throw new ReglaNegocioException("El precio no puede ser negativo.");
        OrdenTrabajo.Servicio s = new OrdenTrabajo.Servicio();
        s.descripcion = descripcion.trim();
        s.mecanico = vacioANull(mecanico);
        s.horas = horas;
        s.precio = Math.round(precio * 100.0) / 100.0;
        if (!dao.agregarServicio(o.getId(), s)) throw new ReglaNegocioException("No se pudo agregar el servicio.");
    }

    public void quitarServicio(OrdenTrabajo o, OrdenTrabajo.Servicio s) throws ReglaNegocioException {
        permisos.exigir(Permiso.ORDENES_EDITAR);
        exigirEditable(o);
        if (!dao.eliminarServicio(o.getId(), s.id)) throw new ReglaNegocioException("No se pudo quitar el servicio.");
    }

    public void agregarRepuesto(OrdenTrabajo o, Repuesto r, int cantidad, double precio) throws ReglaNegocioException {
        permisos.exigir(Permiso.ORDENES_EDITAR);
        exigirEditable(o);
        if (cantidad <= 0) throw new ReglaNegocioException("La cantidad debe ser mayor que cero.");
        if (precio < 0 || Double.isNaN(precio)) throw new ReglaNegocioException("El precio no puede ser negativo.");
        if (precio != r.getPrecio() && precio != r.getPrecioMayorista()) permisos.exigir(Permiso.PRECIOS_MODIFICAR);
        if (cantidad > r.getStock()) {
            throw new ReglaNegocioException("Solo hay " + r.getStock() + " unidades de \"" + r.getDescripcion() + "\" en inventario.");
        }
        OrdenTrabajo.RepuestoUsado u = new OrdenTrabajo.RepuestoUsado();
        u.repuestoId = r.getId();
        u.descripcion = r.getDescripcion();
        u.cantidad = cantidad;
        u.precioUnitario = Math.round(precio * 100.0) / 100.0;
        u.costoUnitario = r.getCosto();
        if (!dao.agregarRepuesto(o.getId(), u)) throw new ReglaNegocioException("No se pudo agregar el repuesto.");
    }

    public void quitarRepuesto(OrdenTrabajo o, OrdenTrabajo.RepuestoUsado r) throws ReglaNegocioException {
        permisos.exigir(Permiso.ORDENES_EDITAR);
        exigirEditable(o);
        if (!dao.quitarRepuesto(o.getId(), r.id, ahora(), Sesion.nombreActual(), Formato.numeroOrden(o.getId()))) {
            throw new ReglaNegocioException("No se pudo quitar el repuesto.");
        }
        if (r.consumido) {
            AuditoriaService.registrar(AuditoriaService.TALLER, "Reintegró un repuesto al inventario", Formato.numeroOrden(o.getId()),
                    null, r.codigo + " x" + r.cantidad);
        }
    }

    /** Descuenta del inventario los repuestos agregados que todavía no se habían consumido. */
    public int consumirRepuestos(OrdenTrabajo o) throws ReglaNegocioException {
        permisos.exigir(Permiso.ORDENES_EDITAR);
        exigirEditable(o);
        int r = dao.consumirRepuestos(o.getId(), ahora(), Sesion.nombreActual(), Formato.numeroOrden(o.getId()));
        if (r == OrdenTrabajoDAO.ERROR_STOCK) {
            throw new ReglaNegocioException("Alguno de los repuestos ya no tiene existencias suficientes. Revisa el inventario.");
        }
        if (r < 0) throw new ReglaNegocioException("No se pudo descontar el inventario.");
        if (r > 0) {
            AuditoriaService.registrar(AuditoriaService.TALLER, "Confirmó consumo de repuestos", Formato.numeroOrden(o.getId()),
                    null, r + " línea(s) descontadas del inventario");
        }
        return r;
    }

    public void cancelar(OrdenTrabajo o, String motivo) throws ReglaNegocioException {
        permisos.exigir(Permiso.ORDENES_CANCELAR);
        if (motivo == null || motivo.trim().isEmpty()) throw new ReglaNegocioException("Escribe el motivo de la cancelación.");
        if (o.isFacturada()) throw new ReglaNegocioException("La orden ya está facturada y no se puede cancelar.");
        exigirAbierta(o);
        if (!dao.cancelar(o.getId(), ahora(), Sesion.nombreActual(), Formato.numeroOrden(o.getId()), motivo.trim())) {
            throw new ReglaNegocioException("No se pudo cancelar la orden.");
        }
        AuditoriaService.registrar(AuditoriaService.TALLER, "Canceló la orden de trabajo", Formato.numeroOrden(o.getId()),
                o.getEstadoEtiqueta(), "Cancelada. Motivo: " + motivo.trim() + " (repuestos consumidos devueltos al inventario)");
    }

    // ------------------------------------------------------------------
    // FACTURAR Y ENTREGAR
    // ------------------------------------------------------------------

    /**
     * Factura la orden (servicios + repuestos) como una venta normal del sistema: valida la forma de pago,
     * registra el pago y la caja, y deja la orden como ENTREGADA.
     */
    public VentaService.Resultado facturar(OrdenTrabajo o, String metodoPago, Double montoRecibido, LocalDate fechaLimite)
            throws ReglaNegocioException {
        permisos.exigir(Permiso.ORDENES_FACTURAR);
        OrdenTrabajo actual = dao.obtener(o.getId());
        if (actual == null) throw new ReglaNegocioException("La orden ya no existe.");
        if (actual.isFacturada()) throw new ReglaNegocioException("Esta orden ya fue facturada.");
        exigirAbierta(actual);
        if (actual.getServicios().isEmpty() && actual.getRepuestos().isEmpty()) {
            throw new ReglaNegocioException("La orden no tiene servicios ni repuestos. Si no hay nada que cobrar, usa \"Entregar sin cobro\".");
        }
        // Los repuestos pendientes se descuentan ahora (si falta alguno, no se factura)
        if (actual.tieneRepuestosSinConsumir()) {
            int r = dao.consumirRepuestos(actual.getId(), ahora(), Sesion.nombreActual(), Formato.numeroOrden(actual.getId()));
            if (r == OrdenTrabajoDAO.ERROR_STOCK) {
                throw new ReglaNegocioException("Alguno de los repuestos de la orden ya no tiene existencias. Revisa el inventario.");
            }
            if (r < 0) throw new ReglaNegocioException("No se pudo descontar el inventario de los repuestos.");
        }

        List<ItemCarrito> lineas = new ArrayList<>();
        for (OrdenTrabajo.Servicio s : actual.getServicios()) {
            if (s.precio <= 0) continue;
            Repuesto servicio = new Repuesto(0, "SERV", "Mano de obra: " + s.descripcion, "", "", 0, s.precio, 0);
            lineas.add(new ItemCarrito(servicio, 1));
        }
        for (OrdenTrabajo.RepuestoUsado u : actual.getRepuestos()) {
            Repuesto r = new Repuesto(u.repuestoId, u.codigo, u.descripcion, "", "", u.costoUnitario, u.precioUnitario, 0);
            lineas.add(new ItemCarrito(r, u.cantidad));
        }
        VentaService.Solicitud s = new VentaService.Solicitud();
        s.lineas = lineas;
        s.cliente = clientes.obtener(actual.getClienteId());
        s.metodoPago = metodoPago;
        s.montoRecibido = montoRecibido;
        s.fechaLimite = fechaLimite;
        s.ordenId = actual.getId();
        VentaService.Resultado resultado = ventas.procesar(s);
        if (!dao.vincularVenta(actual.getId(), resultado.factura.getId(), ahora())) {
            throw new ReglaNegocioException("La factura " + Formato.numeroFactura(resultado.factura.getId())
                    + " se registró, pero no se pudo marcar la orden como entregada. Avísale al administrador.");
        }
        AuditoriaService.registrar(AuditoriaService.TALLER, "Facturó y entregó la orden", Formato.numeroOrden(actual.getId()),
                actual.getEstadoEtiqueta(), "Factura " + Formato.numeroFactura(resultado.factura.getId())
                        + "; total " + Formato.moneda(resultado.factura.getTotal()));
        return resultado;
    }

    /** Entrega una orden sin nada que cobrar (garantía, revisión gratuita...). */
    public void entregarSinCobro(OrdenTrabajo o, String motivo) throws ReglaNegocioException {
        permisos.exigir(Permiso.ORDENES_FACTURAR);
        OrdenTrabajo actual = dao.obtener(o.getId());
        if (actual == null) throw new ReglaNegocioException("La orden ya no existe.");
        exigirAbierta(actual);
        if (actual.getTotal() > 0) {
            throw new ReglaNegocioException("La orden tiene " + Formato.moneda(actual.getTotal()) + " por cobrar. Usa \"Facturar y entregar\".");
        }
        if (motivo == null || motivo.trim().isEmpty()) throw new ReglaNegocioException("Indica por qué se entrega sin cobro.");
        if (actual.tieneRepuestosSinConsumir()) consumirRepuestos(actual);
        if (!dao.cambiarEstado(actual.getId(), OrdenTrabajo.ENTREGADO, ahora())) {
            throw new ReglaNegocioException("No se pudo entregar la orden.");
        }
        AuditoriaService.registrar(AuditoriaService.TALLER, "Entregó la orden sin cobro", Formato.numeroOrden(actual.getId()),
                actual.getEstadoEtiqueta(), "Entregado. Motivo: " + motivo.trim());
    }

    // ------------------------------------------------------------------

    private static void exigirAbierta(OrdenTrabajo o) throws ReglaNegocioException {
        if (!o.isAbierta()) {
            throw new ReglaNegocioException("La orden está " + o.getEstadoEtiqueta().toLowerCase() + " y ya no se puede modificar.");
        }
    }

    private static void exigirEditable(OrdenTrabajo o) throws ReglaNegocioException {
        exigirAbierta(o);
        if (o.isFacturada()) throw new ReglaNegocioException("La orden ya está facturada.");
    }

    private static String vacioANull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static String ahora() {
        return LocalDateTime.now().format(FORMATO_FECHA);
    }
}
