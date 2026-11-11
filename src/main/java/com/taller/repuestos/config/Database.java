package com.taller.repuestos.config;

import com.taller.repuestos.dao.CajaDAO;
import com.taller.repuestos.dao.CatalogoDAO;
import com.taller.repuestos.dao.MovimientoInventarioDAO;
import com.taller.repuestos.dao.ProveedorDAO;
import com.taller.repuestos.dao.PermisoDAO;
import com.taller.repuestos.dao.UsuarioDAO;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private static final String URL = "jdbc:sqlite:taller_repuestos.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initDatabase() {
        String sqlRepuestos = """
            CREATE TABLE IF NOT EXISTS repuestos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                codigo TEXT UNIQUE NOT NULL,
                descripcion TEXT NOT NULL,
                marca TEXT,
                aplicacion TEXT,
                costo REAL NOT NULL,
                precio REAL NOT NULL,
                stock INTEGER NOT NULL
            );
        """;

        String sqlHistorial = """
            CREATE TABLE IF NOT EXISTS historial_reabastecimiento (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                repuesto_id INTEGER NOT NULL,
                cantidad_agregada INTEGER NOT NULL,
                stock_resultante INTEGER NOT NULL,
                fecha TEXT NOT NULL,
                FOREIGN KEY (repuesto_id) REFERENCES repuestos(id)
            );
        """;

        String sqlVentas = """
            CREATE TABLE IF NOT EXISTS ventas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha TEXT NOT NULL,
                subtotal REAL NOT NULL,
                descuento REAL NOT NULL,
                tipo_descuento TEXT,
                total REAL NOT NULL
            );
        """;

        String sqlVentaDetalle = """
            CREATE TABLE IF NOT EXISTS venta_detalle (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                venta_id INTEGER NOT NULL,
                repuesto_id INTEGER NOT NULL,
                descripcion TEXT NOT NULL,
                cantidad INTEGER NOT NULL,
                precio_unitario REAL NOT NULL,
                costo_unitario REAL NOT NULL,
                subtotal REAL NOT NULL,
                FOREIGN KEY (venta_id) REFERENCES ventas(id),
                FOREIGN KEY (repuesto_id) REFERENCES repuestos(id)
            );
        """;

        // Un registro por cada cierre de caja (turno) realizado
        String sqlCierresCaja = """
            CREATE TABLE IF NOT EXISTS cierres_caja (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha_apertura TEXT NOT NULL,
                fecha_cierre TEXT NOT NULL,
                total_efectivo REAL NOT NULL,
                total_tarjeta REAL NOT NULL,
                total_transferencia REAL NOT NULL,
                total_sistema REAL NOT NULL,
                efectivo_contado REAL NOT NULL,
                diferencia REAL NOT NULL
            );
        """;
        String sqlUsuarios = "CREATE TABLE IF NOT EXISTS usuarios ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT UNIQUE, "
                + "password TEXT, "
                + "nombre TEXT, "
                + "rol TEXT);";

        // Configuración del negocio y del sistema (clave/valor)
        String sqlConfiguracion = "CREATE TABLE IF NOT EXISTS configuracion ("
                + "clave TEXT PRIMARY KEY, "
                + "valor TEXT);";

        // Historial de auditoría: solo se agregan registros, nunca se modifican
        String sqlAuditoria = """
            CREATE TABLE IF NOT EXISTS auditoria (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha TEXT NOT NULL,
                usuario TEXT NOT NULL,
                modulo TEXT NOT NULL,
                accion TEXT NOT NULL,
                registro TEXT,
                valor_anterior TEXT,
                valor_nuevo TEXT
            );
        """;

        // Permisos que tiene cada rol
        String sqlRolesPermisos = """
            CREATE TABLE IF NOT EXISTS roles_permisos (
                rol TEXT NOT NULL,
                permiso TEXT NOT NULL,
                PRIMARY KEY (rol, permiso)
            );
        """;

        // Turnos de caja: se abren con un fondo inicial y se cierran contando el efectivo
        String sqlCajas = """
            CREATE TABLE IF NOT EXISTS cajas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                estado TEXT NOT NULL DEFAULT 'ABIERTA',
                usuario_apertura TEXT NOT NULL,
                fecha_apertura TEXT NOT NULL,
                monto_inicial REAL NOT NULL DEFAULT 0,
                usuario_cierre TEXT,
                fecha_cierre TEXT,
                total_efectivo REAL,
                total_tarjeta REAL,
                total_transferencia REAL,
                total_sistema REAL,
                entradas REAL,
                retiros REAL,
                gastos REAL,
                efectivo_esperado REAL,
                efectivo_contado REAL,
                diferencia REAL,
                observaciones TEXT
            );
        """;

        // Entradas, retiros y gastos manuales dentro de un turno de caja
        String sqlMovimientosCaja = """
            CREATE TABLE IF NOT EXISTS movimientos_caja (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                caja_id INTEGER NOT NULL,
                fecha TEXT NOT NULL,
                usuario TEXT NOT NULL,
                tipo TEXT NOT NULL,
                monto REAL NOT NULL,
                motivo TEXT NOT NULL,
                documento TEXT,
                FOREIGN KEY (caja_id) REFERENCES cajas(id)
            );
        """;

        // Clientes (con límite de crédito; su saldo pendiente se calcula con las ventas a crédito)
        String sqlClientes = """
            CREATE TABLE IF NOT EXISTS clientes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL,
                identificacion TEXT,
                telefono TEXT,
                direccion TEXT,
                correo TEXT,
                tipo TEXT NOT NULL DEFAULT 'Minorista',
                activo INTEGER NOT NULL DEFAULT 1,
                limite_credito REAL NOT NULL DEFAULT 0,
                notas TEXT
            );
        """;

        // Pagos: el que se hace al vender (PAGO) y los abonos posteriores a una venta a crédito (ABONO)
        String sqlPagos = """
            CREATE TABLE IF NOT EXISTS pagos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                venta_id INTEGER NOT NULL,
                fecha TEXT NOT NULL,
                monto REAL NOT NULL,
                metodo TEXT NOT NULL,
                tipo TEXT NOT NULL,
                usuario TEXT NOT NULL,
                caja_id INTEGER,
                FOREIGN KEY (venta_id) REFERENCES ventas(id)
            );
        """;

        // Devoluciones (notas de crédito) de parte de una factura
        String sqlDevoluciones = """
            CREATE TABLE IF NOT EXISTS devoluciones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                venta_id INTEGER NOT NULL,
                fecha TEXT NOT NULL,
                usuario TEXT NOT NULL,
                motivo TEXT NOT NULL,
                monto_total REAL NOT NULL,
                monto_saldo REAL NOT NULL DEFAULT 0,
                forma_reembolso TEXT NOT NULL,
                caja_id INTEGER,
                FOREIGN KEY (venta_id) REFERENCES ventas(id)
            );
        """;

        String sqlDetalleDevoluciones = """
            CREATE TABLE IF NOT EXISTS detalle_devoluciones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                devolucion_id INTEGER NOT NULL,
                venta_detalle_id INTEGER NOT NULL,
                repuesto_id INTEGER NOT NULL,
                descripcion TEXT NOT NULL,
                cantidad INTEGER NOT NULL,
                monto REAL NOT NULL,
                FOREIGN KEY (devolucion_id) REFERENCES devoluciones(id)
            );
        """;

        // Catálogo: categorías y marcas (los nombres no se repiten, sin importar mayúsculas)
        String sqlCategorias = """
            CREATE TABLE IF NOT EXISTS categorias (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL COLLATE NOCASE UNIQUE,
                activo INTEGER NOT NULL DEFAULT 1
            );
        """;
        String sqlMarcas = """
            CREATE TABLE IF NOT EXISTS marcas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL COLLATE NOCASE UNIQUE,
                activo INTEGER NOT NULL DEFAULT 1
            );
        """;

        // Vehículos con los que es compatible cada repuesto (un vacío en año o motor significa "cualquiera")
        String sqlCompatibilidades = """
            CREATE TABLE IF NOT EXISTS compatibilidades (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                repuesto_id INTEGER NOT NULL,
                marca_vehiculo TEXT NOT NULL,
                modelo TEXT NOT NULL,
                anio_desde INTEGER,
                anio_hasta INTEGER,
                motor TEXT,
                combustible TEXT,
                transmision TEXT,
                observaciones TEXT,
                FOREIGN KEY (repuesto_id) REFERENCES repuestos(id)
            );
        """;

        // ---------------- Fase 3: kardex ----------------
        // Cada cambio de existencias deja una fila (cantidad con signo: + entra, - sale)
        String sqlMovimientosInventario = """
            CREATE TABLE IF NOT EXISTS movimientos_inventario (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                repuesto_id INTEGER NOT NULL,
                fecha TEXT NOT NULL,
                tipo TEXT NOT NULL,
                cantidad INTEGER NOT NULL,
                stock_resultante INTEGER,
                costo_unitario REAL,
                documento TEXT,
                motivo TEXT,
                usuario TEXT NOT NULL,
                FOREIGN KEY (repuesto_id) REFERENCES repuestos(id)
            );
        """;

        // ---------------- Fase 4: proveedores y compras ----------------
        String sqlProveedores = """
            CREATE TABLE IF NOT EXISTS proveedores (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL COLLATE NOCASE UNIQUE,
                empresa TEXT,
                identificacion_fiscal TEXT,
                telefono TEXT,
                direccion TEXT,
                correo TEXT,
                contacto TEXT,
                activo INTEGER NOT NULL DEFAULT 1,
                observaciones TEXT
            );
        """;
        // Estado: PENDIENTE (orden enviada) -> PARCIAL -> RECIBIDA, o ANULADA
        String sqlCompras = """
            CREATE TABLE IF NOT EXISTS compras (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                proveedor_id INTEGER NOT NULL,
                fecha TEXT NOT NULL,
                documento_proveedor TEXT,
                estado TEXT NOT NULL DEFAULT 'PENDIENTE',
                condicion_pago TEXT NOT NULL DEFAULT 'Contado',
                fecha_vencimiento TEXT,
                subtotal REAL NOT NULL DEFAULT 0,
                descuento REAL NOT NULL DEFAULT 0,
                impuesto_pct REAL NOT NULL DEFAULT 0,
                impuesto REAL NOT NULL DEFAULT 0,
                total REAL NOT NULL DEFAULT 0,
                usuario TEXT NOT NULL,
                fecha_recepcion TEXT,
                observaciones TEXT,
                anulada_por TEXT,
                fecha_anulacion TEXT,
                motivo_anulacion TEXT,
                FOREIGN KEY (proveedor_id) REFERENCES proveedores(id)
            );
        """;
        String sqlDetalleCompras = """
            CREATE TABLE IF NOT EXISTS detalle_compras (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                compra_id INTEGER NOT NULL,
                repuesto_id INTEGER NOT NULL,
                descripcion TEXT NOT NULL,
                cantidad INTEGER NOT NULL,
                cantidad_recibida INTEGER NOT NULL DEFAULT 0,
                costo_unitario REAL NOT NULL,
                descuento REAL NOT NULL DEFAULT 0,
                subtotal REAL NOT NULL,
                FOREIGN KEY (compra_id) REFERENCES compras(id),
                FOREIGN KEY (repuesto_id) REFERENCES repuestos(id)
            );
        """;
        String sqlPagosProveedor = """
            CREATE TABLE IF NOT EXISTS pagos_proveedor (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                compra_id INTEGER NOT NULL,
                fecha TEXT NOT NULL,
                monto REAL NOT NULL,
                metodo TEXT NOT NULL,
                referencia TEXT,
                usuario TEXT NOT NULL,
                caja_id INTEGER,
                FOREIGN KEY (compra_id) REFERENCES compras(id)
            );
        """;
        String sqlDevolucionesProveedor = """
            CREATE TABLE IF NOT EXISTS devoluciones_proveedor (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                compra_id INTEGER NOT NULL,
                fecha TEXT NOT NULL,
                usuario TEXT NOT NULL,
                motivo TEXT NOT NULL,
                total REAL NOT NULL,
                FOREIGN KEY (compra_id) REFERENCES compras(id)
            );
        """;
        String sqlDetalleDevProveedor = """
            CREATE TABLE IF NOT EXISTS detalle_devoluciones_proveedor (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                devolucion_id INTEGER NOT NULL,
                detalle_compra_id INTEGER NOT NULL,
                repuesto_id INTEGER NOT NULL,
                cantidad INTEGER NOT NULL,
                costo_unitario REAL NOT NULL,
                FOREIGN KEY (devolucion_id) REFERENCES devoluciones_proveedor(id)
            );
        """;
        String sqlHistorialCostos = """
            CREATE TABLE IF NOT EXISTS historial_costos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                repuesto_id INTEGER NOT NULL,
                fecha TEXT NOT NULL,
                costo_anterior REAL NOT NULL,
                costo_nuevo REAL NOT NULL,
                origen TEXT,
                usuario TEXT NOT NULL,
                FOREIGN KEY (repuesto_id) REFERENCES repuestos(id)
            );
        """;

        // ---------------- Fase 5: vehículos y taller ----------------
        String sqlVehiculos = """
            CREATE TABLE IF NOT EXISTS vehiculos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cliente_id INTEGER NOT NULL,
                marca TEXT NOT NULL,
                modelo TEXT NOT NULL,
                anio INTEGER,
                placa TEXT,
                vin TEXT,
                motor TEXT,
                cilindrada TEXT,
                combustible TEXT,
                transmision TEXT,
                color TEXT,
                kilometraje INTEGER,
                observaciones TEXT,
                activo INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY (cliente_id) REFERENCES clientes(id)
            );
        """;
        // Estado: RECIBIDO, DIAGNOSTICO, EN_REPARACION, LISTO, ENTREGADO, CANCELADO
        String sqlOrdenes = """
            CREATE TABLE IF NOT EXISTS ordenes_trabajo (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cliente_id INTEGER NOT NULL,
                vehiculo_id INTEGER NOT NULL,
                fecha_ingreso TEXT NOT NULL,
                fecha_promesa TEXT,
                fecha_entrega TEXT,
                estado TEXT NOT NULL DEFAULT 'RECIBIDO',
                kilometraje INTEGER,
                problema TEXT,
                diagnostico TEXT,
                observaciones TEXT,
                mecanico TEXT,
                usuario TEXT NOT NULL,
                venta_id INTEGER,
                FOREIGN KEY (cliente_id) REFERENCES clientes(id),
                FOREIGN KEY (vehiculo_id) REFERENCES vehiculos(id)
            );
        """;
        String sqlOrdenServicios = """
            CREATE TABLE IF NOT EXISTS orden_servicios (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                orden_id INTEGER NOT NULL,
                descripcion TEXT NOT NULL,
                mecanico TEXT,
                horas REAL NOT NULL DEFAULT 0,
                precio REAL NOT NULL,
                FOREIGN KEY (orden_id) REFERENCES ordenes_trabajo(id)
            );
        """;
        String sqlOrdenRepuestos = """
            CREATE TABLE IF NOT EXISTS orden_repuestos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                orden_id INTEGER NOT NULL,
                repuesto_id INTEGER NOT NULL,
                descripcion TEXT NOT NULL,
                cantidad INTEGER NOT NULL,
                precio_unitario REAL NOT NULL,
                costo_unitario REAL NOT NULL,
                consumido INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (orden_id) REFERENCES ordenes_trabajo(id),
                FOREIGN KEY (repuesto_id) REFERENCES repuestos(id)
            );
        """;

        // ---------------- Fase 7: cotizaciones (no mueven inventario ni caja) ----------------
        // Estado: PENDIENTE, CONVERTIDA (se vendió: venta_id), ANULADA. "Vencida" se calcula con fecha_vencimiento.
        String sqlCotizaciones = """
            CREATE TABLE IF NOT EXISTS cotizaciones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha TEXT NOT NULL,
                fecha_vencimiento TEXT,
                cliente_id INTEGER,
                cliente_nombre TEXT,
                vendedor TEXT NOT NULL,
                subtotal REAL NOT NULL,
                descuento REAL NOT NULL DEFAULT 0,
                tipo_descuento TEXT,
                descuento_valor REAL NOT NULL DEFAULT 0,
                impuesto REAL NOT NULL DEFAULT 0,
                total REAL NOT NULL,
                estado TEXT NOT NULL DEFAULT 'PENDIENTE',
                venta_id INTEGER,
                observaciones TEXT,
                FOREIGN KEY (cliente_id) REFERENCES clientes(id)
            );
        """;
        String sqlDetalleCotizaciones = """
            CREATE TABLE IF NOT EXISTS detalle_cotizaciones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cotizacion_id INTEGER NOT NULL,
                repuesto_id INTEGER NOT NULL,
                descripcion TEXT NOT NULL,
                cantidad INTEGER NOT NULL,
                precio_unitario REAL NOT NULL,
                descuento_pct REAL NOT NULL DEFAULT 0,
                subtotal REAL NOT NULL,
                FOREIGN KEY (cotizacion_id) REFERENCES cotizaciones(id)
            );
        """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlRepuestos);
            stmt.execute(sqlHistorial);
            stmt.execute(sqlVentas);
            stmt.execute(sqlVentaDetalle);
            stmt.execute(sqlCierresCaja);
            stmt.execute(sqlUsuarios);
            stmt.execute(sqlConfiguracion);
            stmt.execute(sqlAuditoria);
            stmt.execute(sqlRolesPermisos);
            stmt.execute(sqlCajas);
            stmt.execute(sqlMovimientosCaja);
            stmt.execute(sqlClientes);
            stmt.execute(sqlPagos);
            stmt.execute(sqlDevoluciones);
            stmt.execute(sqlDetalleDevoluciones);
            stmt.execute(sqlCategorias);
            stmt.execute(sqlMarcas);
            stmt.execute(sqlCompatibilidades);
            stmt.execute(sqlMovimientosInventario);
            stmt.execute(sqlProveedores);
            stmt.execute(sqlCompras);
            stmt.execute(sqlDetalleCompras);
            stmt.execute(sqlPagosProveedor);
            stmt.execute(sqlDevolucionesProveedor);
            stmt.execute(sqlDetalleDevProveedor);
            stmt.execute(sqlHistorialCostos);
            stmt.execute(sqlVehiculos);
            stmt.execute(sqlOrdenes);
            stmt.execute(sqlOrdenServicios);
            stmt.execute(sqlOrdenRepuestos);

            // Migraciones: agregan columnas nuevas a tablas que ya existían en instalaciones previas.
            // Si la columna ya existe, SQLite lanza error "duplicate column name" y simplemente se ignora.
            agregarColumnaSiNoExiste(stmt, "repuestos", "stock_minimo", "INTEGER NOT NULL DEFAULT 5");
            agregarColumnaSiNoExiste(stmt, "repuestos", "proveedor", "TEXT");
            agregarColumnaSiNoExiste(stmt, "ventas", "metodo_pago", "TEXT NOT NULL DEFAULT 'Efectivo'");
            agregarColumnaSiNoExiste(stmt, "ventas", "vendedor", "TEXT");
            agregarColumnaSiNoExiste(stmt, "usuarios", "activo", "INTEGER NOT NULL DEFAULT 1");
            agregarColumnaSiNoExiste(stmt, "ventas", "caja_id", "INTEGER");

            // Fase 2: cliente, descuento general, impuesto, pago en efectivo, vencimiento del crédito y anulación
            agregarColumnaSiNoExiste(stmt, "ventas", "cliente_id", "INTEGER");
            agregarColumnaSiNoExiste(stmt, "ventas", "descuento_valor", "REAL NOT NULL DEFAULT 0");
            agregarColumnaSiNoExiste(stmt, "ventas", "impuesto", "REAL NOT NULL DEFAULT 0");
            agregarColumnaSiNoExiste(stmt, "ventas", "monto_recibido", "REAL");
            agregarColumnaSiNoExiste(stmt, "ventas", "cambio", "REAL");
            agregarColumnaSiNoExiste(stmt, "ventas", "estado", "TEXT NOT NULL DEFAULT 'EMITIDA'");
            agregarColumnaSiNoExiste(stmt, "ventas", "fecha_limite", "TEXT");
            agregarColumnaSiNoExiste(stmt, "ventas", "anulada_por", "TEXT");
            agregarColumnaSiNoExiste(stmt, "ventas", "fecha_anulacion", "TEXT");
            agregarColumnaSiNoExiste(stmt, "ventas", "motivo_anulacion", "TEXT");
            agregarColumnaSiNoExiste(stmt, "venta_detalle", "descuento_pct", "REAL NOT NULL DEFAULT 0");
            agregarColumnaSiNoExiste(stmt, "venta_detalle", "descuento", "REAL NOT NULL DEFAULT 0");
            agregarColumnaSiNoExiste(stmt, "cajas", "total_credito", "REAL");
            agregarColumnaSiNoExiste(stmt, "cajas", "abonos_efectivo", "REAL");
            agregarColumnaSiNoExiste(stmt, "cajas", "devoluciones", "REAL");

            // Fase 3: datos del producto para el catálogo y la búsqueda
            agregarColumnaSiNoExiste(stmt, "repuestos", "categoria", "TEXT");
            agregarColumnaSiNoExiste(stmt, "repuestos", "codigo_barras", "TEXT");
            agregarColumnaSiNoExiste(stmt, "repuestos", "ubicacion", "TEXT");
            agregarColumnaSiNoExiste(stmt, "repuestos", "precio_mayorista", "REAL NOT NULL DEFAULT 0");
            agregarColumnaSiNoExiste(stmt, "repuestos", "stock_maximo", "INTEGER NOT NULL DEFAULT 0");
            agregarColumnaSiNoExiste(stmt, "repuestos", "unidad", "TEXT NOT NULL DEFAULT 'Unidad'");

            // Fase 5: la factura que se genera al cobrar una orden de trabajo queda ligada a ella
            agregarColumnaSiNoExiste(stmt, "ventas", "orden_id", "INTEGER");

            // Fase 7: fotografía del producto y cotizaciones
            agregarColumnaSiNoExiste(stmt, "repuestos", "foto", "TEXT");
            stmt.execute(sqlCotizaciones);
            stmt.execute(sqlDetalleCotizaciones);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_detcot_cotizacion ON detalle_cotizaciones(cotizacion_id)");

            // Fases 3-5: índices de las tablas nuevas
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_movinv_repuesto ON movimientos_inventario(repuesto_id, fecha)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_movinv_fecha ON movimientos_inventario(fecha)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_compras_proveedor ON compras(proveedor_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_detcompras_compra ON detalle_compras(compra_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_detcompras_repuesto ON detalle_compras(repuesto_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pagosprov_compra ON pagos_proveedor(compra_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_devprov_compra ON devoluciones_proveedor(compra_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_costos_repuesto ON historial_costos(repuesto_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_vehiculos_cliente ON vehiculos(cliente_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ordenes_vehiculo ON ordenes_trabajo(vehiculo_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ordenes_estado ON ordenes_trabajo(estado)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ordserv_orden ON orden_servicios(orden_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ordrep_orden ON orden_repuestos(orden_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ventas_fecha ON ventas(fecha)");

            // Índices (IF NOT EXISTS: seguro de repetir). El último garantiza una sola caja abierta a la vez.
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ventas_caja ON ventas(caja_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_auditoria_fecha ON auditoria(fecha)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_movcaja_caja ON movimientos_caja(caja_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ventas_cliente ON ventas(cliente_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pagos_venta ON pagos(venta_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pagos_caja ON pagos(caja_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_devoluciones_venta ON devoluciones(venta_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_detdev_detalle ON detalle_devoluciones(venta_detalle_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_compat_repuesto ON compatibilidades(repuesto_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_compat_vehiculo ON compatibilidades(marca_vehiculo, modelo)");
            // Dos productos no pueden compartir código de barras (los vacíos no cuentan)
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_repuestos_barras ON repuestos(codigo_barras) "
                       + "WHERE codigo_barras IS NOT NULL AND codigo_barras <> ''");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_caja_unica_abierta ON cajas(estado) WHERE estado = 'ABIERTA'");

            // Seguridad de contraseñas: cifra las que sigan en texto plano y,
            // si la base de datos es nueva, crea los usuarios por defecto ya cifrados.
            UsuarioDAO.migrarContrasenasTextoPlano(conn);
            UsuarioDAO.crearUsuariosPorDefecto(conn);

            // Permisos iniciales de cada rol y cierres de caja anteriores al módulo de caja
            PermisoDAO.sembrarPorDefecto(conn);
            CajaDAO.migrarCierresAnteriores(conn);
            CatalogoDAO.sembrar(conn);
            MovimientoInventarioDAO.migrarHistorial(conn);
            ProveedorDAO.migrarDesdeProductos(conn);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void agregarColumnaSiNoExiste(Statement stmt, String tabla, String columna, String definicion) {
        try {
            stmt.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
        } catch (SQLException e) {
            // Ya existe la columna (instalación no nueva) -> no es un error real, se ignora.
        }
    }
}
