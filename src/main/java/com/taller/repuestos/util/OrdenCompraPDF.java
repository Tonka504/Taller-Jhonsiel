package com.taller.repuestos.util;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import com.taller.repuestos.model.Repuesto;
import com.taller.repuestos.service.ConfiguracionService;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrdenCompraPDF {

    private static final String CARPETA_ORDENES = System.getProperty("user.dir") + File.separator + "ordenes_compra";

    /**
     * Genera un PDF con la orden de compra, agrupando los productos por proveedor
     * (los productos sin proveedor asignado se agrupan como "Sin proveedor asignado").
     * La cantidad sugerida es: (stockMinimo * 2) - stockActual (mínimo 1).
     */
    public static File generar(List<Repuesto> productos) throws IOException {
        if (productos == null || productos.isEmpty()) {
            throw new IllegalArgumentException("No hay productos para incluir en la orden de compra.");
        }

        File carpeta = new File(CARPETA_ORDENES);
        if (!carpeta.exists()) {
            carpeta.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File archivo = new File(carpeta, "orden_compra_" + timestamp + ".pdf");

        // Agrupar por proveedor, respetando el orden en que aparecen
        Map<String, java.util.List<Repuesto>> porProveedor = new LinkedHashMap<>();
        for (Repuesto r : productos) {
            String proveedor = (r.getProveedor() == null || r.getProveedor().trim().isEmpty())
                    ? "Sin proveedor asignado"
                    : r.getProveedor().trim();
            porProveedor.computeIfAbsent(proveedor, k -> new java.util.ArrayList<>()).add(r);
        }

        try (PdfWriter writer = new PdfWriter(archivo.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {

            document.add(new Paragraph(ConfiguracionService.get().nombreNegocio()).setBold().setFontSize(16).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Orden de Compra").setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Fecha: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
            document.add(new Paragraph(" "));

            for (Map.Entry<String, java.util.List<Repuesto>> grupo : porProveedor.entrySet()) {
                document.add(new Paragraph("Proveedor: " + grupo.getKey())
                        .setBold().setFontSize(12).setMarginTop(10));

                Table table = new Table(UnitValue.createPercentArray(new float[]{2, 4, 1.5f, 1.5f, 1.5f}));
                table.setWidth(UnitValue.createPercentValue(100));
                table.addHeaderCell("Código");
                table.addHeaderCell("Producto");
                table.addHeaderCell("Stock Actual");
                table.addHeaderCell("Mínimo");
                table.addHeaderCell("Pedir");

                for (Repuesto r : grupo.getValue()) {
                    int sugerida = Math.max((r.getStockMinimo() * 2) - r.getStock(), 1);
                    table.addCell(r.getCodigo());
                    table.addCell(r.getDescripcion());
                    table.addCell(String.valueOf(r.getStock()));
                    table.addCell(String.valueOf(r.getStockMinimo()));
                    table.addCell(String.valueOf(sugerida));
                }

                document.add(table);
            }
        }

        return archivo;
    }
}