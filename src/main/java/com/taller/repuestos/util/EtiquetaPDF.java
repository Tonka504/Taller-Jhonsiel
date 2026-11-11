package com.taller.repuestos.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EtiquetaPDF {

    // Carpeta fija donde se guardan las hojas de etiquetas generadas
    private static final String CARPETA_ETIQUETAS = System.getProperty("user.dir") + File.separator + "etiquetas";
    private static final int COLUMNAS = 3; // etiquetas por fila en la hoja

    /** Datos mínimos de un producto para imprimir su etiqueta. */
    public static class ProductoEtiqueta {
        private final String codigo;
        private final String descripcion;

        public ProductoEtiqueta(String codigo, String descripcion) {
            this.codigo = codigo;
            this.descripcion = descripcion;
        }

        public String getCodigo() { return codigo; }
        public String getDescripcion() { return descripcion; }
    }

    /**
     * Genera un PDF (tamaño carta) con una etiqueta por producto: nombre arriba,
     * código de barras en medio, y el código en texto abajo. Las etiquetas se
     * acomodan en una cuadrícula, listas para imprimir en una sola hoja.
     */
    public static File generarHojaEtiquetas(List<ProductoEtiqueta> productos) throws WriterException, IOException {
        if (productos == null || productos.isEmpty()) {
            throw new IllegalArgumentException("No hay productos para generar etiquetas.");
        }

        File carpeta = new File(CARPETA_ETIQUETAS);
        if (!carpeta.exists()) {
            carpeta.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File archivo = new File(carpeta, "etiquetas_" + timestamp + ".pdf");

        try (PdfWriter writer = new PdfWriter(archivo.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc, PageSize.LETTER)) {

            document.setMargins(20, 20, 20, 20);

            Table table = new Table(UnitValue.createPercentArray(new float[COLUMNAS]));
            table.setWidth(UnitValue.createPercentValue(100));

            for (ProductoEtiqueta producto : productos) {
                table.addCell(crearCeldaEtiqueta(producto));
            }

            document.add(table);
        }

        return archivo;
    }

    private static Cell crearCeldaEtiqueta(ProductoEtiqueta producto) throws WriterException, IOException {
        BufferedImage barcodeImg = generarImagenBarcode(producto.getCodigo());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(barcodeImg, "png", baos);

        Image barcode = new Image(ImageDataFactory.create(baos.toByteArray()));
        barcode.setWidth(150);
        barcode.setAutoScaleHeight(true);
        barcode.setHorizontalAlignment(HorizontalAlignment.CENTER);

        Cell celda = new Cell();
        celda.setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f));
        celda.setPadding(8);
        celda.setTextAlignment(TextAlignment.CENTER);

        // Nombre del producto arriba (facilita colocar la etiqueta en el producto correcto)
        celda.add(new Paragraph(producto.getDescripcion())
                .setFontSize(8)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(3));

        celda.add(barcode);

        // Código en texto debajo del código de barras (por si el lector falla o hay que leerlo a simple vista)
        celda.add(new Paragraph(producto.getCodigo())
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(3));

        return celda;
    }

    private static BufferedImage generarImagenBarcode(String codigo) throws WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 2);
        BitMatrix bitMatrix = new MultiFormatWriter().encode(codigo, BarcodeFormat.CODE_128, 300, 100, hints);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }
}