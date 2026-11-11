package com.taller.repuestos.service;

import com.taller.repuestos.model.ItemCarrito;

import java.util.List;

/**
 * Calcula los totales de una venta: subtotal (con los descuentos por producto), descuento general,
 * impuesto y total. Es lógica pura (no toca la base de datos) para que sea fácil de probar.
 */
public final class CalculoVenta {

    public static final String PORCENTAJE = "PORCENTAJE";
    public static final String MONTO = "MONTO";

    /** Resultado del cálculo. */
    public static final class Resultado {
        public final double subtotal;
        public final double descuentoGeneral;
        public final double impuesto;
        public final double total;

        Resultado(double subtotal, double descuentoGeneral, double impuesto, double total) {
            this.subtotal = subtotal;
            this.descuentoGeneral = descuentoGeneral;
            this.impuesto = impuesto;
            this.total = total;
        }

        /** Descuento general como porcentaje del subtotal (0 si no hay subtotal). */
        public double getDescuentoGeneralPct() {
            return subtotal > 0 ? descuentoGeneral * 100.0 / subtotal : 0;
        }
    }

    private CalculoVenta() {}

    /**
     * @param tipoDescuento   null, PORCENTAJE o MONTO (descuento general)
     * @param valorDescuento  el porcentaje o el monto, según el tipo
     * @param impuestoPct     porcentaje de impuesto (0 si no se cobra)
     * @param impuestoIncluido true si los precios ya incluyen el impuesto (solo se desglosa);
     *                         false si el impuesto se suma al total
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public static Resultado calcular(List<ItemCarrito> lineas, String tipoDescuento, double valorDescuento,
                                     double impuestoPct, boolean impuestoIncluido) {
        if (Double.isNaN(valorDescuento) || Double.isInfinite(valorDescuento) || valorDescuento < 0) {
            throw new IllegalArgumentException("El descuento no puede ser negativo.");
        }
        if (Double.isNaN(impuestoPct) || impuestoPct < 0 || impuestoPct > 100) {
            throw new IllegalArgumentException("El porcentaje de impuesto no es válido.");
        }

        double subtotal = 0;
        for (ItemCarrito l : lineas) {
            if (l.getDescuentoPct() < 0 || l.getDescuentoPct() > 100) {
                throw new IllegalArgumentException("El descuento de un producto debe estar entre 0 y 100%.");
            }
            subtotal += l.getSubtotal();
        }
        subtotal = redondear(subtotal);

        double descuento = 0;
        if (PORCENTAJE.equals(tipoDescuento)) {
            if (valorDescuento > 100) {
                throw new IllegalArgumentException("El descuento no puede ser mayor al 100%.");
            }
            descuento = redondear(subtotal * valorDescuento / 100.0);
        } else if (MONTO.equals(tipoDescuento)) {
            if (valorDescuento > subtotal) {
                throw new IllegalArgumentException("El descuento no puede ser mayor que el subtotal.");
            }
            descuento = redondear(valorDescuento);
        } else if (tipoDescuento != null) {
            throw new IllegalArgumentException("Tipo de descuento no válido.");
        }

        double neto = redondear(subtotal - descuento);
        double impuesto;
        double total;
        if (impuestoIncluido) {
            impuesto = redondear(neto - neto / (1 + impuestoPct / 100.0));
            total = neto;
        } else {
            impuesto = redondear(neto * impuestoPct / 100.0);
            total = redondear(neto + impuesto);
        }
        return new Resultado(subtotal, descuento, impuesto, total);
    }

    static double redondear(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
