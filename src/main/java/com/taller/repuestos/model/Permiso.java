package com.taller.repuestos.model;

import java.util.EnumSet;
import java.util.Set;

/** Acciones que se pueden permitir o negar por rol. El rol ADMIN siempre las tiene todas. */
public enum Permiso {
    // Versión 1
    POS_USAR("Ventas", "Usar el punto de venta", 1),
    HISTORIAL_VER("Ventas", "Ver el historial de facturas", 1),
    INVENTARIO_VER("Inventario", "Consultar el inventario", 1),
    INVENTARIO_EDITAR("Inventario", "Crear y editar productos", 1),
    INVENTARIO_ELIMINAR("Inventario", "Eliminar productos", 1),
    PRECIOS_MODIFICAR("Inventario", "Modificar costos y precios", 1),
    STOCK_AJUSTAR("Inventario", "Reabastecer y ajustar existencias", 1),
    CAJA_VER("Caja", "Ver la caja", 1),
    CAJA_ABRIR("Caja", "Abrir caja", 1),
    CAJA_CERRAR("Caja", "Cerrar caja", 1),
    CAJA_MOVIMIENTOS("Caja", "Registrar entradas, retiros y gastos", 1),
    USUARIOS_ADMIN("Sistema", "Administrar usuarios y permisos", 1),
    AUDITORIA_VER("Sistema", "Ver la auditoría", 1),
    CONFIGURACION_EDITAR("Sistema", "Cambiar la configuración y los respaldos", 1),
    // Versión 2
    DESCUENTOS_APLICAR("Ventas", "Aplicar descuentos en las ventas", 2),
    VENTAS_ANULAR("Ventas", "Anular facturas", 2),
    DEVOLUCIONES_REALIZAR("Ventas", "Registrar devoluciones", 2),
    CLIENTES_VER("Clientes", "Consultar clientes", 2),
    CLIENTES_EDITAR("Clientes", "Crear y editar clientes", 2),
    COBROS_REGISTRAR("Clientes", "Registrar abonos a créditos", 2),
    // Versión 3: proveedores, compras, taller y reportes
    PROVEEDORES_VER("Compras", "Consultar proveedores", 3),
    PROVEEDORES_EDITAR("Compras", "Crear y editar proveedores", 3),
    COMPRAS_VER("Compras", "Consultar compras", 3),
    COMPRAS_CREAR("Compras", "Crear órdenes de compra", 3),
    COMPRAS_RECIBIR("Compras", "Recibir mercadería (aumenta inventario)", 3),
    COMPRAS_ANULAR("Compras", "Anular compras y devolver a proveedores", 3),
    COMPRAS_PAGAR("Compras", "Registrar pagos a proveedores", 3),
    VEHICULOS_EDITAR("Taller", "Registrar y editar vehículos de clientes", 3),
    ORDENES_VER("Taller", "Consultar órdenes de trabajo", 3),
    ORDENES_EDITAR("Taller", "Crear y trabajar órdenes de trabajo", 3),
    ORDENES_FACTURAR("Taller", "Facturar y entregar órdenes de trabajo", 3),
    ORDENES_CANCELAR("Taller", "Cancelar órdenes de trabajo", 3),
    REPORTES_VER("Reportes", "Ver reportes de ventas, inventario y compras", 3),
    REPORTES_FINANZAS("Reportes", "Ver reportes financieros (costos y ganancias)", 3);

    private final String grupo;
    private final String etiqueta;
    private final int desde;

    Permiso(String grupo, String etiqueta, int desde) {
        this.grupo = grupo;
        this.etiqueta = etiqueta;
        this.desde = desde;
    }

    /** Versión del sistema en la que apareció este permiso (sirve para cargar solo los nuevos al actualizar). */
    public int getDesde() { return desde; }

    /** Versión más reciente de permisos. */
    public static int versionActual() {
        int max = 0;
        for (Permiso p : values()) max = Math.max(max, p.desde);
        return max;
    }

    public String getGrupo() { return grupo; }
    public String getEtiqueta() { return etiqueta; }

    /** Permisos con los que arranca cada rol (después el administrador puede cambiarlos). */
    public static Set<Permiso> predeterminados(Rol rol) {
        switch (rol) {
            case ADMIN:
                return EnumSet.allOf(Permiso.class);
            case VENDEDOR:
                return EnumSet.of(POS_USAR, HISTORIAL_VER, INVENTARIO_VER, CLIENTES_VER, CLIENTES_EDITAR,
                        VEHICULOS_EDITAR, ORDENES_VER);
            case CAJERO:
                return EnumSet.of(POS_USAR, HISTORIAL_VER, INVENTARIO_VER,
                        CAJA_VER, CAJA_ABRIR, CAJA_CERRAR, CAJA_MOVIMIENTOS,
                        CLIENTES_VER, COBROS_REGISTRAR, ORDENES_VER, ORDENES_FACTURAR, REPORTES_VER);
            case BODEGUERO:
                return EnumSet.of(INVENTARIO_VER, INVENTARIO_EDITAR, STOCK_AJUSTAR,
                        PROVEEDORES_VER, PROVEEDORES_EDITAR, COMPRAS_VER, COMPRAS_CREAR, COMPRAS_RECIBIR, REPORTES_VER);
            case MECANICO:
                return EnumSet.of(INVENTARIO_VER, CLIENTES_VER, VEHICULOS_EDITAR, ORDENES_VER, ORDENES_EDITAR);
            default:
                return EnumSet.noneOf(Permiso.class);
        }
    }
}
