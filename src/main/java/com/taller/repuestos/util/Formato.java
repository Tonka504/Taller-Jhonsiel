package com.taller.repuestos.util;

import com.taller.repuestos.service.ConfiguracionService;

/** Formatos comunes: dinero con el símbolo configurado y lectura de montos escritos por el usuario. */
public final class Formato {

    private Formato() {}

    /** Ejemplo: "L. 1,250.50" (el símbolo sale de la configuración). */
    public static String moneda(double monto) {
        return ConfiguracionService.get().simboloMoneda() + String.format("%,.2f", monto);
    }

    /**
     * Convierte texto escrito por una persona en un monto: acepta "1500.50", "1500,50" y "1,500.50".
     * @throws NumberFormatException si no es un número
     */
    public static double leerMonto(String texto) {
        if (texto == null) throw new NumberFormatException("vacío");
        String limpio = texto.trim().replace(" ", "");
        if (limpio.isEmpty()) throw new NumberFormatException("vacío");
        if (limpio.contains(",") && limpio.contains(".")) {
            limpio = limpio.replace(",", "");      // 1,500.50 -> 1500.50
        } else {
            limpio = limpio.replace(",", ".");     // 1500,50  -> 1500.50
        }
        double valor = Double.parseDouble(limpio);
        if (Double.isNaN(valor) || Double.isInfinite(valor)) throw new NumberFormatException("no válido");
        return valor;
    }

    /** Número de factura con el prefijo y los dígitos configurados. Ejemplo: FAC-000012 */
    public static String numeroFactura(int id) {
        ConfiguracionService c = ConfiguracionService.get();
        return numero(c.get(ConfiguracionService.DOC_PREFIJO_FACTURA).trim(), id);
    }

    /** Número de devolución (nota de crédito). Ejemplo: DEV-000003 */
    public static String numeroDevolucion(int id) {
        ConfiguracionService c = ConfiguracionService.get();
        return numero(c.get(ConfiguracionService.DOC_PREFIJO_DEVOLUCION).trim(), id);
    }

    /** Número de compra / orden de compra. Ejemplo: OC-000004 */
    public static String numeroCompra(int id) {
        return numero(ConfiguracionService.get().get(ConfiguracionService.DOC_PREFIJO_COMPRA).trim(), id);
    }

    /** Número de orden de trabajo del taller. Ejemplo: OT-000015 */
    public static String numeroOrden(int id) {
        return numero(ConfiguracionService.get().get(ConfiguracionService.DOC_PREFIJO_ORDEN).trim(), id);
    }

    /** Número de cotización. Ejemplo: COT-000007 */
    public static String numeroCotizacion(int id) {
        return numero(ConfiguracionService.get().get(ConfiguracionService.DOC_PREFIJO_COTIZACION).trim(), id);
    }

    private static String numero(String prefijo, int id) {
        int digitos = ConfiguracionService.get().getEntero(ConfiguracionService.DOC_DIGITOS, 1, 10);
        return prefijo + String.format("%0" + digitos + "d", id);
    }
}
