package com.taller.repuestos.service;

import com.taller.repuestos.dao.ConfiguracionDAO;
import com.taller.repuestos.exception.ReglaNegocioException;
import com.taller.repuestos.model.Permiso;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuración del negocio y del sistema. Se guarda en la base de datos (tabla configuracion)
 * y se mantiene en memoria para no consultar a cada momento.
 */
public class ConfiguracionService {

    public static final String NEGOCIO_NOMBRE = "negocio.nombre";
    public static final String NEGOCIO_DIRECCION = "negocio.direccion";
    public static final String NEGOCIO_TELEFONO = "negocio.telefono";
    public static final String NEGOCIO_CORREO = "negocio.correo";
    public static final String NEGOCIO_DATOS_FISCALES = "negocio.datos_fiscales";
    public static final String MONEDA_SIMBOLO = "moneda.simbolo";
    public static final String CAJA_EXIGIR_ABIERTA = "caja.exigir_abierta";
    public static final String RESPALDO_AUTOMATICO = "respaldo.automatico";
    public static final String RESPALDO_CONSERVAR = "respaldo.conservar";
    public static final String RESPALDO_ULTIMO = "respaldo.ultimo";
    public static final String IMPUESTO_NOMBRE = "impuesto.nombre";
    public static final String IMPUESTO_PORCENTAJE = "impuesto.porcentaje";
    public static final String IMPUESTO_INCLUIDO = "impuesto.incluido";
    public static final String DESCUENTO_MAXIMO = "ventas.descuento_maximo";
    public static final String CREDITO_DIAS = "credito.dias";
    public static final String DOC_PREFIJO_FACTURA = "documentos.prefijo_factura";
    public static final String DOC_PREFIJO_DEVOLUCION = "documentos.prefijo_devolucion";
    public static final String DOC_DIGITOS = "documentos.digitos";
    public static final String DOC_PREFIJO_COMPRA = "documentos.prefijo_compra";
    public static final String DOC_PREFIJO_ORDEN = "documentos.prefijo_orden";
    public static final String DOC_PREFIJO_COTIZACION = "documentos.prefijo_cotizacion";
    public static final String COTIZACION_DIAS = "cotizaciones.dias_validez";

    private static final Map<String, String> PREDETERMINADOS = new HashMap<>();
    static {
        PREDETERMINADOS.put(NEGOCIO_NOMBRE, "Taller Mecánico");
        PREDETERMINADOS.put(NEGOCIO_DIRECCION, "");
        PREDETERMINADOS.put(NEGOCIO_TELEFONO, "");
        PREDETERMINADOS.put(NEGOCIO_CORREO, "");
        PREDETERMINADOS.put(NEGOCIO_DATOS_FISCALES, "");
        PREDETERMINADOS.put(MONEDA_SIMBOLO, "$");
        PREDETERMINADOS.put(CAJA_EXIGIR_ABIERTA, "true");
        PREDETERMINADOS.put(RESPALDO_AUTOMATICO, "true");
        PREDETERMINADOS.put(RESPALDO_CONSERVAR, "10");
        PREDETERMINADOS.put(RESPALDO_ULTIMO, "");
        // El impuesto arranca en 0: no se asume ningún dato fiscal hasta que el negocio lo configure
        PREDETERMINADOS.put(IMPUESTO_NOMBRE, "Impuesto");
        PREDETERMINADOS.put(IMPUESTO_PORCENTAJE, "0");
        PREDETERMINADOS.put(IMPUESTO_INCLUIDO, "false");
        PREDETERMINADOS.put(DESCUENTO_MAXIMO, "20");
        PREDETERMINADOS.put(CREDITO_DIAS, "30");
        PREDETERMINADOS.put(DOC_PREFIJO_FACTURA, "FAC-");
        PREDETERMINADOS.put(DOC_PREFIJO_DEVOLUCION, "DEV-");
        PREDETERMINADOS.put(DOC_DIGITOS, "6");
        PREDETERMINADOS.put(DOC_PREFIJO_COMPRA, "OC-");
        PREDETERMINADOS.put(DOC_PREFIJO_ORDEN, "OT-");
        PREDETERMINADOS.put(DOC_PREFIJO_COTIZACION, "COT-");
        PREDETERMINADOS.put(COTIZACION_DIAS, "15");
    }

    private static ConfiguracionService instancia;

    public static synchronized ConfiguracionService get() {
        if (instancia == null) instancia = new ConfiguracionService();
        return instancia;
    }

    private final ConfiguracionDAO dao = new ConfiguracionDAO();
    private Map<String, String> valores = new HashMap<>();

    private ConfiguracionService() {
        recargar();
    }

    /** Vuelve a leer la configuración desde la base de datos. */
    public synchronized void recargar() {
        valores = dao.obtenerTodo();
    }

    public synchronized String get(String clave) {
        String v = valores.get(clave);
        if (v != null) return v;
        String porDefecto = PREDETERMINADOS.get(clave);
        return porDefecto != null ? porDefecto : "";
    }

    public boolean getBoolean(String clave) {
        return "true".equalsIgnoreCase(get(clave).trim());
    }

    public int getEntero(String clave, int minimo, int maximo) {
        int valor;
        try {
            valor = Integer.parseInt(get(clave).trim());
        } catch (NumberFormatException e) {
            valor = Integer.parseInt(PREDETERMINADOS.getOrDefault(clave, "0"));
        }
        return Math.max(minimo, Math.min(maximo, valor));
    }

    /** Guarda un valor. Devuelve false si no se pudo escribir en la base de datos. */
    public synchronized boolean set(String clave, String valor) {
        String nuevo = valor == null ? "" : valor;
        if (!dao.guardar(clave, nuevo)) return false;
        valores.put(clave, nuevo);
        return true;
    }

    /** Guarda varios valores y devuelve, por cada uno que cambió, "anterior -> nuevo" (para auditar). */
    public synchronized Map<String, String[]> setVarios(Map<String, String> cambios) {
        Map<String, String[]> aplicados = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : cambios.entrySet()) {
            String anterior = get(e.getKey());
            String nuevo = e.getValue() == null ? "" : e.getValue();
            if (anterior.equals(nuevo)) continue;
            if (set(e.getKey(), nuevo)) {
                aplicados.put(e.getKey(), new String[]{anterior, nuevo});
            }
        }
        return aplicados;
    }

    /** Nombre legible de cada ajuste, para mostrarlo en la auditoría. */
    private static final Map<String, String> ETIQUETAS = new HashMap<>();
    static {
        ETIQUETAS.put(NEGOCIO_NOMBRE, "Nombre del negocio");
        ETIQUETAS.put(NEGOCIO_DIRECCION, "Dirección");
        ETIQUETAS.put(NEGOCIO_TELEFONO, "Teléfono");
        ETIQUETAS.put(NEGOCIO_CORREO, "Correo");
        ETIQUETAS.put(NEGOCIO_DATOS_FISCALES, "Datos fiscales");
        ETIQUETAS.put(MONEDA_SIMBOLO, "Símbolo de moneda");
        ETIQUETAS.put(CAJA_EXIGIR_ABIERTA, "Exigir caja abierta para vender");
        ETIQUETAS.put(RESPALDO_AUTOMATICO, "Respaldo automático");
        ETIQUETAS.put(RESPALDO_CONSERVAR, "Respaldos a conservar");
        ETIQUETAS.put(IMPUESTO_NOMBRE, "Nombre del impuesto");
        ETIQUETAS.put(IMPUESTO_PORCENTAJE, "Porcentaje de impuesto");
        ETIQUETAS.put(IMPUESTO_INCLUIDO, "Precios con impuesto incluido");
        ETIQUETAS.put(DESCUENTO_MAXIMO, "Descuento máximo permitido (%)");
        ETIQUETAS.put(CREDITO_DIAS, "Días de plazo del crédito");
        ETIQUETAS.put(DOC_PREFIJO_FACTURA, "Prefijo de facturas");
        ETIQUETAS.put(DOC_PREFIJO_DEVOLUCION, "Prefijo de devoluciones");
        ETIQUETAS.put(DOC_DIGITOS, "Dígitos de la numeración");
        ETIQUETAS.put(DOC_PREFIJO_COMPRA, "Prefijo de compras");
        ETIQUETAS.put(DOC_PREFIJO_ORDEN, "Prefijo de órdenes de trabajo");
        ETIQUETAS.put(DOC_PREFIJO_COTIZACION, "Prefijo de cotizaciones");
        ETIQUETAS.put(COTIZACION_DIAS, "Días de validez de las cotizaciones");
    }

    /**
     * Guarda los cambios hechos desde la pantalla de configuración: valida, exige permiso
     * y deja cada cambio registrado en la auditoría. Devuelve cuántos ajustes cambiaron.
     */
    public int guardarCambios(Map<String, String> cambios) throws ReglaNegocioException {
        PermisoService.get().exigir(Permiso.CONFIGURACION_EDITAR);

        String nombre = cambios.getOrDefault(NEGOCIO_NOMBRE, get(NEGOCIO_NOMBRE));
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ReglaNegocioException("El nombre del negocio no puede quedar vacío.");
        }
        String simbolo = cambios.getOrDefault(MONEDA_SIMBOLO, get(MONEDA_SIMBOLO));
        if (simbolo != null && simbolo.length() > 5) {
            throw new ReglaNegocioException("El símbolo de moneda es demasiado largo (máximo 5 caracteres).");
        }
        String conservar = cambios.getOrDefault(RESPALDO_CONSERVAR, get(RESPALDO_CONSERVAR));
        try {
            int n = Integer.parseInt(conservar.trim());
            if (n < 1 || n > 100) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException("La cantidad de respaldos a conservar debe ser un número entre 1 y 100.");
        }

        validarNumero(cambios, IMPUESTO_PORCENTAJE, 0, 100, true, "El porcentaje de impuesto debe ser un número entre 0 y 100.");
        validarNumero(cambios, DESCUENTO_MAXIMO, 0, 100, true, "El descuento máximo debe ser un número entre 0 y 100.");
        validarNumero(cambios, CREDITO_DIAS, 0, 365, false, "Los días de plazo del crédito deben ser un número entero entre 0 y 365.");
        validarNumero(cambios, COTIZACION_DIAS, 1, 365, false, "Los días de validez de las cotizaciones deben ser un número entero entre 1 y 365.");
        validarNumero(cambios, DOC_DIGITOS, 1, 10, false, "Los dígitos de la numeración deben ser un número entero entre 1 y 10.");
        for (String clave : new String[]{DOC_PREFIJO_FACTURA, DOC_PREFIJO_DEVOLUCION, DOC_PREFIJO_COMPRA, DOC_PREFIJO_ORDEN, DOC_PREFIJO_COTIZACION}) {
            String prefijo = cambios.getOrDefault(clave, get(clave));
            if (prefijo != null && prefijo.trim().length() > 8) {
                throw new ReglaNegocioException("Los prefijos de los documentos pueden tener hasta 8 caracteres.");
            }
        }

        Map<String, String> limpios = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : cambios.entrySet()) {
            limpios.put(e.getKey(), e.getValue() == null ? "" : e.getValue().trim());
        }
        Map<String, String[]> aplicados = setVarios(limpios);
        for (Map.Entry<String, String[]> e : aplicados.entrySet()) {
            AuditoriaService.registrar(AuditoriaService.CONFIGURACION, "Cambió un ajuste",
                    ETIQUETAS.getOrDefault(e.getKey(), e.getKey()), e.getValue()[0], e.getValue()[1]);
        }
        return aplicados.size();
    }

    /** Si la clave viene en los cambios, comprueba que sea un número dentro del rango. */
    private void validarNumero(Map<String, String> cambios, String clave, double min, double max,
                               boolean admiteDecimales, String mensaje) throws ReglaNegocioException {
        if (!cambios.containsKey(clave)) return;
        String texto = cambios.get(clave) == null ? "" : cambios.get(clave).trim().replace(",", ".");
        try {
            double v = Double.parseDouble(texto);
            if (Double.isNaN(v) || v < min || v > max || (!admiteDecimales && v != Math.floor(v))) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(mensaje);
        }
    }

    // --- Atajos de uso frecuente ---
    public String nombreNegocio() {
        String n = get(NEGOCIO_NOMBRE).trim();
        return n.isEmpty() ? PREDETERMINADOS.get(NEGOCIO_NOMBRE) : n;
    }

    public String simboloMoneda() {
        String s = get(MONEDA_SIMBOLO);
        return s == null ? "" : s;
    }

    public boolean exigirCajaAbierta() {
        return getBoolean(CAJA_EXIGIR_ABIERTA);
    }

    public String nombreImpuesto() {
        String n = get(IMPUESTO_NOMBRE).trim();
        return n.isEmpty() ? PREDETERMINADOS.get(IMPUESTO_NOMBRE) : n;
    }

    public double impuestoPorcentaje() {
        try {
            double v = Double.parseDouble(get(IMPUESTO_PORCENTAJE).trim().replace(",", "."));
            return (Double.isNaN(v) || v < 0 || v > 100) ? 0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean impuestoIncluido() {
        return getBoolean(IMPUESTO_INCLUIDO);
    }

    /** Descuento máximo (en %) que pueden aplicar los usuarios que no son administradores. */
    public double descuentoMaximo() {
        try {
            double v = Double.parseDouble(get(DESCUENTO_MAXIMO).trim().replace(",", "."));
            return (Double.isNaN(v) || v < 0 || v > 100) ? 0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int diasCredito() {
        return getEntero(CREDITO_DIAS, 0, 365);
    }
}
