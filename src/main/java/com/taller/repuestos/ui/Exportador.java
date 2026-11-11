package com.taller.repuestos.ui;

import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.taller.repuestos.service.ConfiguracionService;
import com.taller.repuestos.util.Archivos;
import com.taller.repuestos.util.Formato;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Exporta lo que se ve en una tabla a CSV (se abre con Excel) o a PDF, en la carpeta "reportes".
 * Las columnas de dinero se exportan como número en el CSV para que Excel pueda sumarlas.
 */
public final class Exportador {

    private static final String CARPETA = System.getProperty("user.dir") + File.separator + "reportes";

    private Exportador() {}

    /** Exporta a CSV y abre el archivo. Muestra el error al usuario si falla. */
    public static void csv(TableView<?> tabla, String nombre) {
        try {
            File archivo = archivo(nombre, "csv");
            try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(archivo), StandardCharsets.UTF_8))) {
                w.print('﻿'); // BOM: Excel reconoce los acentos
                List<TableColumn<?, ?>> cols = columnas(tabla);
                List<String> enc = new ArrayList<>();
                for (TableColumn<?, ?> c : cols) enc.add(c.getText());
                w.println(lineaCsv(enc));
                for (int i = 0; i < tabla.getItems().size(); i++) {
                    List<String> fila = new ArrayList<>();
                    for (TableColumn<?, ?> c : cols) {
                        Object v = c.getCellData(i);
                        fila.add(v == null ? "" : (v instanceof Double d ? String.format(java.util.Locale.US, "%.2f", d) : v.toString()));
                    }
                    w.println(lineaCsv(fila));
                }
            }
            Archivos.abrir(archivo);
            Alertas.info("Exportado", "Se guardó el archivo:\n" + archivo.getAbsolutePath());
        } catch (IOException e) {
            Alertas.error("No se pudo exportar", e.getMessage());
        }
    }

    /** Exporta a PDF (horizontal si hay muchas columnas) y abre el archivo. */
    public static void pdf(TableView<?> tabla, String titulo, String subtitulo) {
        try {
            File archivo = archivo(titulo, "pdf");
            List<TableColumn<?, ?>> cols = columnas(tabla);
            try (PdfWriter writer = new PdfWriter(archivo.getAbsolutePath());
                 PdfDocument pdf = new PdfDocument(writer);
                 Document doc = new Document(pdf, cols.size() > 6 ? PageSize.LETTER.rotate() : PageSize.LETTER)) {
                doc.setMargins(28, 28, 28, 28);
                doc.add(new Paragraph(ConfiguracionService.get().nombreNegocio()).setBold().setFontSize(14)
                        .setTextAlignment(TextAlignment.CENTER));
                doc.add(new Paragraph(titulo).setBold().setFontSize(12).setTextAlignment(TextAlignment.CENTER));
                if (subtitulo != null && !subtitulo.isEmpty()) {
                    doc.add(new Paragraph(subtitulo).setFontSize(9).setTextAlignment(TextAlignment.CENTER));
                }
                doc.add(new Paragraph("Generado: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                        .setFontSize(8));

                float[] anchos = new float[cols.size()];
                for (int i = 0; i < cols.size(); i++) anchos[i] = (float) Math.max(40, cols.get(i).getWidth());
                Table t = new Table(UnitValue.createPercentArray(anchos));
                t.setWidth(UnitValue.createPercentValue(100));
                t.setFontSize(8);
                for (TableColumn<?, ?> c : cols) {
                    t.addHeaderCell(new Cell().add(new Paragraph(c.getText()).setBold())
                            .setBackgroundColor(new com.itextpdf.kernel.colors.DeviceRgb(0x2A, 0x36, 0x3B))
                            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE));
                }
                for (int i = 0; i < tabla.getItems().size(); i++) {
                    for (TableColumn<?, ?> c : cols) {
                        Object v = c.getCellData(i);
                        String texto = formatear(c, v);
                        Cell celda = new Cell().add(new Paragraph(texto));
                        if (v instanceof Number) celda.setTextAlignment(TextAlignment.RIGHT);
                        t.addCell(celda);
                    }
                }
                doc.add(t);
            }
            Archivos.abrir(archivo);
        } catch (IOException e) {
            Alertas.error("No se pudo generar el PDF", e.getMessage());
        }
    }

    /**
     * Texto de una celda para el PDF. Si la columna trae un formateador en su userData
     * (Function&lt;Object, String&gt;), se usa ese; si no, los decimales se muestran como dinero.
     */
    @SuppressWarnings("unchecked")
    private static String formatear(TableColumn<?, ?> c, Object v) {
        if (v == null) return "";
        if (c.getUserData() instanceof java.util.function.Function<?, ?> f) {
            return ((java.util.function.Function<Object, String>) f).apply(v);
        }
        return v instanceof Double d ? Formato.moneda(d) : v.toString();
    }

    private static List<TableColumn<?, ?>> columnas(TableView<?> tabla) {
        List<TableColumn<?, ?>> cols = new ArrayList<>();
        for (TableColumn<?, ?> c : tabla.getVisibleLeafColumns()) cols.add(c);
        return cols;
    }

    private static File archivo(String nombre, String extension) {
        File carpeta = new File(CARPETA);
        if (!carpeta.exists()) carpeta.mkdirs();
        String limpio = nombre.toLowerCase().replaceAll("[^a-z0-9áéíóúñ]+", "_").replaceAll("^_|_$", "");
        String marca = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return new File(carpeta, limpio + "_" + marca + "." + extension);
    }

    private static String lineaCsv(List<String> valores) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) sb.append(',');
            String v = valores.get(i).replace("\"", "\"\"");
            sb.append('"').append(v).append('"');
        }
        return sb.toString();
    }
}
