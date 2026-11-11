package com.taller.repuestos.util;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import com.taller.repuestos.model.ItemCarrito;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReciboPDF {

    private static final String CARPETA_RECIBOS = System.getProperty("user.dir") + File.separator + "recibos";
    private static final String NOMBRE_NEGOCIO = "Taller de Repuestos"; // cámbialo por el nombre real

    public static File generar(List<ItemCarrito> items, double descuento, String tipoDescuento, double total, String metodoPago) throws IOException {
        File carpeta = new File(CARPETA_RECIBOS);
        if (!carpeta.exists()) {
            carpeta.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File archivo = new File(carpeta, "recibo_" + timestamp + ".pdf");

        try (PdfWriter writer = new PdfWriter(archivo.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {

            document.add(new Paragraph(NOMBRE_NEGOCIO).setBold().setFontSize(16).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Recibo de Venta").setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Fecha: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
            document.add(new Paragraph("Método de pago: " + metodoPago));
            document.add(new Paragraph(" "));

            Table table = new Table(UnitValue.createPercentArray(new float[]{4, 1, 2, 2}));
            table.setWidth(UnitValue.createPercentValue(100));
            table.addHeaderCell("Producto");
            table.addHeaderCell("Cant.");
            table.addHeaderCell("P. Unit");
            table.addHeaderCell("Subtotal");

            double subtotal = 0;
            for (ItemCarrito item : items) {
                table.addCell(item.getRepuesto().getDescripcion());
                table.addCell(String.valueOf(item.getCantidad()));
                table.addCell(String.format("$%.2f", item.getRepuesto().getPrecio()));
                table.addCell(String.format("$%.2f", item.getSubtotal()));
                subtotal += item.getSubtotal();
            }

            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph(String.format("Subtotal: $%.2f", subtotal)).setTextAlignment(TextAlignment.RIGHT));

            if (descuento > 0) {
                document.add(new Paragraph(String.format("Descuento (%s): -$%.2f", tipoDescuento, descuento))
                        .setTextAlignment(TextAlignment.RIGHT));
            }

            document.add(new Paragraph(String.format("TOTAL: $%.2f", total))
                    .setBold().setFontSize(14).setTextAlignment(TextAlignment.RIGHT));
        }

        return archivo;
    }
}