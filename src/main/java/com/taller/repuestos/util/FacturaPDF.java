package com.taller.repuestos.util;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.taller.repuestos.model.Devolucion;
import com.taller.repuestos.model.Factura;
import com.taller.repuestos.model.LineaFactura;
import com.taller.repuestos.service.ConfiguracionService;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Genera en PDF las facturas y las notas de devolución, en la carpeta "facturas". */
public final class FacturaPDF {

    private static final String CARPETA = System.getProperty("user.dir") + File.separator + "facturas";

    private FacturaPDF() {}

    public static File generarFactura(Factura f, List<Devolucion> devoluciones) throws IOException {
        File archivo = archivoNuevo(Formato.numeroFactura(f.getId()));
        try (PdfWriter writer = new PdfWriter(archivo.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {

            encabezado(document);
            document.add(new Paragraph("FACTURA " + Formato.numeroFactura(f.getId())).setBold().setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER));
            if (f.isAnulada()) {
                document.add(new Paragraph("*** ANULADA ***").setBold().setFontSize(14).setTextAlignment(TextAlignment.CENTER));
            }
            document.add(new Paragraph("Fecha: " + f.getFecha()));
            document.add(new Paragraph("Cliente: " + (f.getClienteNombre() == null ? "Consumidor final" : f.getClienteNombre())));
            if (f.getClienteIdentificacion() != null && !f.getClienteIdentificacion().trim().isEmpty()) {
                document.add(new Paragraph("Identificación: " + f.getClienteIdentificacion()));
            }
            document.add(new Paragraph("Atendido por: " + f.getVendedor()));
            document.add(new Paragraph("Forma de pago: " + f.getMetodoPago()));
            document.add(new Paragraph(" "));

            Table table = new Table(UnitValue.createPercentArray(new float[]{5, 1, 2, 2, 2}));
            table.setWidth(UnitValue.createPercentValue(100));
            table.addHeaderCell("Producto");
            table.addHeaderCell("Cant.");
            table.addHeaderCell("P. Unit");
            table.addHeaderCell("Desc.");
            table.addHeaderCell("Subtotal");
            for (LineaFactura l : f.getLineas()) {
                table.addCell(l.getDescripcion());
                table.addCell(String.valueOf(l.getCantidad()));
                table.addCell(Formato.moneda(l.getPrecioUnitario()));
                table.addCell(l.getDescuento() > 0 ? "-" + Formato.moneda(l.getDescuento()) : "");
                table.addCell(Formato.moneda(l.getSubtotal()));
            }
            document.add(table);
            document.add(new Paragraph(" "));

            ConfiguracionService config = ConfiguracionService.get();
            derecha(document, "Subtotal: " + Formato.moneda(f.getSubtotal()));
            if (f.getDescuento() > 0) {
                derecha(document, "Descuento: -" + Formato.moneda(f.getDescuento()));
            }
            if (f.getImpuesto() > 0) {
                derecha(document, config.nombreImpuesto() + (config.impuestoIncluido() ? " (incluido): " : ": ")
                        + Formato.moneda(f.getImpuesto()));
            }
            document.add(new Paragraph("TOTAL: " + Formato.moneda(f.getTotal())).setBold().setFontSize(14)
                    .setTextAlignment(TextAlignment.RIGHT));

            if (f.getMontoRecibido() != null) {
                derecha(document, "Recibido: " + Formato.moneda(f.getMontoRecibido()));
                derecha(document, "Cambio: " + Formato.moneda(f.getCambio() == null ? 0 : f.getCambio()));
            }
            if (f.isCredito()) {
                derecha(document, "Venta a crédito. Fecha límite de pago: " + (f.getFechaLimite() == null ? "-" : f.getFechaLimite()));
                if (f.getAbonado() > 0) derecha(document, "Abonado: " + Formato.moneda(f.getAbonado()));
                derecha(document, "Saldo pendiente: " + Formato.moneda(f.getSaldoPendiente()));
            }
            if (devoluciones != null) {
                for (Devolucion d : devoluciones) {
                    derecha(document, "Devolución " + Formato.numeroDevolucion(d.getId()) + ": -" + Formato.moneda(d.getMontoTotal()));
                }
            }
            if (f.isAnulada()) {
                document.add(new Paragraph("Anulada por " + f.getAnuladaPor() + ". Motivo: " + f.getMotivoAnulacion()));
            }
            document.add(new Paragraph(" "));
            document.add(new Paragraph("¡Gracias por su compra!").setTextAlignment(TextAlignment.CENTER));
        }
        return archivo;
    }

    public static File generarDevolucion(Factura f, Devolucion d) throws IOException {
        File archivo = archivoNuevo(Formato.numeroDevolucion(d.getId()));
        try (PdfWriter writer = new PdfWriter(archivo.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {

            encabezado(document);
            document.add(new Paragraph("NOTA DE CRÉDITO / DEVOLUCIÓN " + Formato.numeroDevolucion(d.getId()))
                    .setBold().setFontSize(14).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Factura original: " + Formato.numeroFactura(f.getId())));
            document.add(new Paragraph("Fecha: " + d.getFecha()));
            document.add(new Paragraph("Cliente: " + (f.getClienteNombre() == null ? "Consumidor final" : f.getClienteNombre())));
            document.add(new Paragraph("Atendido por: " + d.getUsuario()));
            document.add(new Paragraph("Motivo: " + d.getMotivo()));
            document.add(new Paragraph(" "));

            Table table = new Table(UnitValue.createPercentArray(new float[]{6, 1, 2}));
            table.setWidth(UnitValue.createPercentValue(100));
            table.addHeaderCell("Producto");
            table.addHeaderCell("Cant.");
            table.addHeaderCell("Monto");
            for (Devolucion.Linea l : d.getLineas()) {
                table.addCell(l.descripcion);
                table.addCell(String.valueOf(l.cantidad));
                table.addCell(Formato.moneda(l.monto));
            }
            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("TOTAL DEVUELTO: " + Formato.moneda(d.getMontoTotal())).setBold().setFontSize(14)
                    .setTextAlignment(TextAlignment.RIGHT));
            if (d.getMontoSaldo() > 0) {
                derecha(document, "Descontado de la deuda del cliente: " + Formato.moneda(d.getMontoSaldo()));
            }
            if (d.getMontoReembolsado() > 0) {
                derecha(document, "Reembolsado (" + d.getFormaReembolso().toLowerCase() + "): " + Formato.moneda(d.getMontoReembolsado()));
            }
        }
        return archivo;
    }

    // ------------------------------------------------------------------

    private static void encabezado(Document document) {
        ConfiguracionService config = ConfiguracionService.get();
        document.add(new Paragraph(config.nombreNegocio()).setBold().setFontSize(16).setTextAlignment(TextAlignment.CENTER));
        for (String linea : new String[]{
                config.get(ConfiguracionService.NEGOCIO_DIRECCION),
                config.get(ConfiguracionService.NEGOCIO_TELEFONO).isEmpty() ? "" : "Tel: " + config.get(ConfiguracionService.NEGOCIO_TELEFONO),
                config.get(ConfiguracionService.NEGOCIO_CORREO),
                config.get(ConfiguracionService.NEGOCIO_DATOS_FISCALES)}) {
            if (linea != null && !linea.trim().isEmpty()) {
                document.add(new Paragraph(linea.trim()).setTextAlignment(TextAlignment.CENTER));
            }
        }
        document.add(new Paragraph(" "));
    }

    private static void derecha(Document document, String texto) {
        document.add(new Paragraph(texto).setTextAlignment(TextAlignment.RIGHT));
    }

    /** Archivo de destino. Si el anterior está abierto en un visor (y no se puede sobrescribir), se crea uno con la hora. */
    private static File archivoNuevo(String numero) throws IOException {
        File carpeta = new File(CARPETA);
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta " + carpeta.getAbsolutePath());
        }
        String base = numero.replaceAll("[^A-Za-z0-9_-]", "_");
        File archivo = new File(carpeta, base + ".pdf");
        if (archivo.exists() && !archivo.delete()) {
            String hora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            archivo = new File(carpeta, base + "_" + hora + ".pdf");
        }
        return archivo;
    }
}
