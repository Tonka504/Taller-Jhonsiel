package com.taller.repuestos.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.taller.repuestos.model.DetalleVenta;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class FacturaPDFService {

    public static String generarFactura(List<DetalleVenta> carrito, double total) {
        String carpetaFacturas = "facturas";
        File dir = new File(carpetaFacturas);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fechaStr = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String nombreArchivo = carpetaFacturas + "/Factura_" + fechaStr + ".pdf";

        try {
            PdfWriter writer = new PdfWriter(nombreArchivo);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // Encabezado
            document.add(new Paragraph("TALLER Y VENTA DE REPUESTOS")
                    .setBold().setFontSize(18).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Comprobante de Venta")
                    .setFontSize(12).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Fecha: " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()))
                    .setFontSize(10).setTextAlignment(TextAlignment.RIGHT));
            document.add(new Paragraph("\n"));

            // Tabla de productos
            Table table = new Table(UnitValue.createPercentArray(new float[]{20, 40, 15, 10, 15}))
                    .useAllAvailableWidth();

            table.addHeaderCell("Código");
            table.addHeaderCell("Descripción");
            table.addHeaderCell("Precio U.");
            table.addHeaderCell("Cant.");
            table.addHeaderCell("Subtotal");

            for (DetalleVenta item : carrito) {
                table.addCell(item.getCodigo());
                table.addCell(item.getDescripcion());
                table.addCell(String.format("$%.2f", item.getPrecioUnitario()));
                table.addCell(String.valueOf(item.getCantidad()));
                table.addCell(String.format("$%.2f", item.getSubtotal()));
            }

            document.add(table);

            // Total
            document.add(new Paragraph("\n"));
            document.add(new Paragraph(String.format("TOTAL: $%.2f", total))
                    .setBold().setFontSize(14).setTextAlignment(TextAlignment.RIGHT));

            document.close();
            return nombreArchivo;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}