package com.taller.repuestos.util;

import com.taller.repuestos.model.Devolucion;
import com.taller.repuestos.model.Factura;
import com.taller.repuestos.model.LineaFactura;
import com.taller.repuestos.service.ConfiguracionService;

import java.util.List;

/** Arma el texto del ticket (40 columnas, tipografía monoespaciada) de una factura o una devolución. */
public final class TicketTexto {

    private static final int ANCHO = 40;
    private static final String DOBLE = "=".repeat(ANCHO) + "\n";
    private static final String SIMPLE = "-".repeat(ANCHO) + "\n";

    private TicketTexto() {}

    public static String factura(Factura f, List<Devolucion> devoluciones) {
        ConfiguracionService config = ConfiguracionService.get();
        StringBuilder sb = new StringBuilder();
        encabezado(sb, config);

        sb.append(fila("FACTURA", Formato.numeroFactura(f.getId())));
        if (f.isAnulada()) {
            sb.append(centrar("*** ANULADA ***")).append("\n");
        }
        sb.append("Fecha: ").append(f.getFecha()).append("\n");
        sb.append("Cliente: ").append(f.getClienteNombre() == null ? "Consumidor final" : f.getClienteNombre()).append("\n");
        if (f.getClienteIdentificacion() != null && !f.getClienteIdentificacion().trim().isEmpty()) {
            sb.append("Identificación: ").append(f.getClienteIdentificacion()).append("\n");
        }
        sb.append("Atendido por: ").append(f.getVendedor()).append("\n");
        sb.append("Pago: ").append(f.getMetodoPago()).append("\n");
        sb.append(SIMPLE);
        sb.append(String.format("%-15s %3s %9s %9s\n", "Producto", "Cant", "P.Unit", "Total"));
        sb.append(SIMPLE);
        for (LineaFactura l : f.getLineas()) {
            sb.append(String.format("%-15s %3d %9s %9s\n", cortar(l.getDescripcion(), 15), l.getCantidad(),
                    Formato.moneda(l.getPrecioUnitario()), Formato.moneda(l.getSubtotal())));
            if (l.getDescuento() > 0) {
                sb.append(fila("   Desc. " + porcentaje(l.getDescuentoPct()) + "%", "-" + Formato.moneda(l.getDescuento())));
            }
        }
        sb.append(SIMPLE);

        sb.append(fila("Subtotal:", Formato.moneda(f.getSubtotal())));
        if (f.getDescuento() > 0) {
            String etiqueta = "PORCENTAJE".equals(f.getTipoDescuento())
                    ? "Descuento (" + porcentaje(f.getDescuentoValor()) + "%):" : "Descuento:";
            sb.append(fila(etiqueta, "-" + Formato.moneda(f.getDescuento())));
        }
        if (f.getImpuesto() > 0) {
            sb.append(fila(config.nombreImpuesto() + (config.impuestoIncluido() ? " (incluido):" : ":"),
                    Formato.moneda(f.getImpuesto())));
        }
        sb.append(fila("TOTAL:", Formato.moneda(f.getTotal())));
        sb.append(DOBLE);

        if (f.getMontoRecibido() != null) {
            sb.append(fila("Recibido:", Formato.moneda(f.getMontoRecibido())));
            sb.append(fila("Cambio:", Formato.moneda(f.getCambio() == null ? 0 : f.getCambio())));
        }
        if (f.isCredito()) {
            sb.append(fila("Venta a crédito. Vence:", f.getFechaLimite() == null ? "-" : f.getFechaLimite()));
            if (f.getAbonado() > 0) sb.append(fila("Abonado:", Formato.moneda(f.getAbonado())));
            sb.append(fila("Saldo pendiente:", Formato.moneda(f.getSaldoPendiente())));
        }
        if (devoluciones != null && !devoluciones.isEmpty()) {
            sb.append(SIMPLE).append("Devoluciones:\n");
            for (Devolucion d : devoluciones) {
                sb.append(fila(" " + Formato.numeroDevolucion(d.getId()), "-" + Formato.moneda(d.getMontoTotal())));
            }
        }
        if (f.isAnulada()) {
            sb.append(SIMPLE);
            sb.append("Anulada por: ").append(f.getAnuladaPor() == null ? "-" : f.getAnuladaPor()).append("\n");
            sb.append("Motivo: ").append(f.getMotivoAnulacion() == null ? "-" : f.getMotivoAnulacion()).append("\n");
        }
        sb.append(DOBLE);
        sb.append(centrar("¡Gracias por su compra!")).append("\n");
        sb.append(DOBLE);
        return sb.toString();
    }

    public static String devolucion(Factura f, Devolucion d) {
        ConfiguracionService config = ConfiguracionService.get();
        StringBuilder sb = new StringBuilder();
        encabezado(sb, config);

        sb.append(centrar("NOTA DE CRÉDITO / DEVOLUCIÓN")).append("\n");
        sb.append(fila("Documento:", Formato.numeroDevolucion(d.getId())));
        sb.append(fila("Factura original:", Formato.numeroFactura(f.getId())));
        sb.append("Fecha: ").append(d.getFecha()).append("\n");
        sb.append("Cliente: ").append(f.getClienteNombre() == null ? "Consumidor final" : f.getClienteNombre()).append("\n");
        sb.append("Atendido por: ").append(d.getUsuario()).append("\n");
        sb.append("Motivo: ").append(d.getMotivo()).append("\n");
        sb.append(SIMPLE);
        sb.append(String.format("%-24s %4s %10s\n", "Producto", "Cant", "Monto"));
        sb.append(SIMPLE);
        for (Devolucion.Linea l : d.getLineas()) {
            sb.append(String.format("%-24s %4d %10s\n", cortar(l.descripcion, 24), l.cantidad, Formato.moneda(l.monto)));
        }
        sb.append(SIMPLE);
        sb.append(fila("TOTAL DEVUELTO:", Formato.moneda(d.getMontoTotal())));
        if (d.getMontoSaldo() > 0) {
            sb.append(fila("Descontado de la deuda:", Formato.moneda(d.getMontoSaldo())));
        }
        if (d.getMontoReembolsado() > 0) {
            sb.append(fila("Reembolsado (" + d.getFormaReembolso().toLowerCase() + "):", Formato.moneda(d.getMontoReembolsado())));
        }
        sb.append(DOBLE);
        return sb.toString();
    }

    // ------------------------------------------------------------------

    private static void encabezado(StringBuilder sb, ConfiguracionService config) {
        sb.append(DOBLE);
        sb.append(centrar(config.nombreNegocio().toUpperCase())).append("\n");
        agregarSiHay(sb, config.get(ConfiguracionService.NEGOCIO_DIRECCION), "");
        agregarSiHay(sb, config.get(ConfiguracionService.NEGOCIO_TELEFONO), "Tel: ");
        agregarSiHay(sb, config.get(ConfiguracionService.NEGOCIO_DATOS_FISCALES), "");
        sb.append(DOBLE);
    }

    private static void agregarSiHay(StringBuilder sb, String valor, String prefijo) {
        if (valor != null && !valor.trim().isEmpty()) {
            sb.append(centrar(prefijo + valor.trim())).append("\n");
        }
    }

    /** Texto a la izquierda y valor a la derecha, ocupando las 40 columnas. */
    static String fila(String izquierda, String derecha) {
        int espacio = ANCHO - derecha.length() - 1;
        String izq = izquierda.length() > espacio ? izquierda.substring(0, Math.max(0, espacio)) : izquierda;
        return izq + " ".repeat(Math.max(1, ANCHO - izq.length() - derecha.length())) + derecha + "\n";
    }

    static String centrar(String texto) {
        if (texto.length() >= ANCHO) return texto.substring(0, ANCHO);
        return " ".repeat((ANCHO - texto.length()) / 2) + texto;
    }

    private static String cortar(String texto, int max) {
        if (texto == null) return "";
        return texto.length() > max ? texto.substring(0, max - 2) + ".." : texto;
    }

    private static String porcentaje(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
