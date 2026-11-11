package com.taller.repuestos.util;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.taller.repuestos.model.Compra;
import com.taller.repuestos.model.OrdenTrabajo;
import com.taller.repuestos.service.ConfiguracionService;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** PDF de los documentos internos: órdenes de compra y órdenes de trabajo del taller. */
public final class DocumentoPDF {

    private static final DeviceRgb OSCURO = new DeviceRgb(0x2A, 0x36, 0x3B);
    private static final DeviceRgb ACENTO = new DeviceRgb(0xE8, 0x4A, 0x5F);

    private DocumentoPDF() {}

    // ------------------------------------------------------------------
    // ORDEN DE COMPRA
    // ------------------------------------------------------------------

    public static File compra(Compra c) throws IOException {
        String numero = Formato.numeroCompra(c.getId());
        File archivo = archivo("ordenes_compra", numero);
        try (PdfWriter w = new PdfWriter(archivo.getAbsolutePath());
             PdfDocument pdf = new PdfDocument(w);
             Document doc = new Document(pdf)) {
            encabezado(doc, "ORDEN DE COMPRA " + numero, c.isAnulada() ? "*** ANULADA ***" : c.getEstadoEtiqueta());
            doc.add(new Paragraph("Proveedor: " + c.getProveedorNombre()));
            doc.add(new Paragraph("Fecha: " + c.getFecha() + "    Condición: " + c.getCondicionPago()
                    + (c.getFechaVencimiento() != null ? " (vence " + c.getFechaVencimiento() + ")" : "")));
            if (c.getDocumentoProveedor() != null && !c.getDocumentoProveedor().isEmpty()) {
                doc.add(new Paragraph("Factura del proveedor: " + c.getDocumentoProveedor()));
            }
            doc.add(new Paragraph("Elaborada por: " + c.getUsuario()));

            Table t = tabla(new float[]{2, 5, 1.2f, 1.2f, 2, 1.6f, 2}, "Código", "Producto", "Cant.", "Recib.", "Costo", "Desc.", "Subtotal");
            for (Compra.Linea l : c.getLineas()) {
                t.addCell(celda(l.codigo));
                t.addCell(celda(l.descripcion));
                t.addCell(num(String.valueOf(l.cantidad)));
                t.addCell(num(String.valueOf(l.cantidadRecibida)));
                t.addCell(num(Formato.moneda(l.costoUnitario)));
                t.addCell(num(l.descuento > 0 ? "-" + Formato.moneda(l.descuento) : ""));
                t.addCell(num(Formato.moneda(l.getSubtotal())));
            }
            doc.add(t);
            totales(doc, new String[][]{
                    {"Subtotal", Formato.moneda(c.getSubtotal())},
                    {"Descuento", "-" + Formato.moneda(c.getDescuento())},
                    {"Impuesto (" + c.getImpuestoPct() + "%)", Formato.moneda(c.getImpuesto())},
                    {"TOTAL", Formato.moneda(c.getTotal())}});
            if (c.getObservaciones() != null && !c.getObservaciones().isEmpty()) {
                doc.add(new Paragraph("Observaciones: " + c.getObservaciones()).setFontSize(9));
            }
        }
        return archivo;
    }

    // ------------------------------------------------------------------
    // COTIZACIÓN
    // ------------------------------------------------------------------

    public static File cotizacion(com.taller.repuestos.model.Cotizacion c) throws IOException {
        String numero = Formato.numeroCotizacion(c.getId());
        File archivo = archivo("cotizaciones", numero);
        try (PdfWriter w = new PdfWriter(archivo.getAbsolutePath());
             PdfDocument pdf = new PdfDocument(w);
             Document doc = new Document(pdf)) {
            encabezado(doc, "COTIZACIÓN " + numero, c.getEstadoEtiqueta().equals("Pendiente") ? null : c.getEstadoEtiqueta());
            doc.add(new Paragraph("Cliente: " + c.getClienteMostrar()));
            doc.add(new Paragraph("Fecha: " + c.getFecha() + "    Válida hasta: " + fechaCorta(c.getFechaVencimiento())));
            doc.add(new Paragraph("Atendido por: " + c.getVendedor()));

            Table t = tabla(new float[]{2, 5, 1.2f, 2, 1.4f, 2}, "Código", "Producto", "Cant.", "P. Unit", "Desc.", "Subtotal");
            for (com.taller.repuestos.model.Cotizacion.Linea l : c.getLineas()) {
                t.addCell(celda(l.codigo));
                t.addCell(celda(l.descripcion));
                t.addCell(num(String.valueOf(l.cantidad)));
                t.addCell(num(Formato.moneda(l.precioUnitario)));
                t.addCell(num(l.descuentoPct > 0 ? String.format("%.1f %%", l.descuentoPct) : ""));
                t.addCell(num(Formato.moneda(l.subtotal)));
            }
            doc.add(t);
            ConfiguracionService config = ConfiguracionService.get();
            totales(doc, new String[][]{
                    {"Subtotal", Formato.moneda(c.getSubtotal())},
                    {"Descuento", "-" + Formato.moneda(c.getDescuento())},
                    {config.nombreImpuesto(), Formato.moneda(c.getImpuesto())},
                    {"TOTAL", Formato.moneda(c.getTotal())}});
            if (c.getObservaciones() != null && !c.getObservaciones().isEmpty()) {
                doc.add(new Paragraph("Observaciones: " + c.getObservaciones()).setFontSize(9));
            }
            doc.add(new Paragraph("Precios y existencias sujetos a disponibilidad al momento de la compra. "
                    + "Esta cotización no es una factura.").setFontSize(8).setMarginTop(14));
        }
        return archivo;
    }

    /** Fecha ISO "2026-11-26" → "26/11/2026". */
    private static String fechaCorta(String iso) {
        if (iso == null || iso.length() < 10) return iso == null ? "" : iso;
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
    }

    // ------------------------------------------------------------------
    // ORDEN DE TRABAJO
    // ------------------------------------------------------------------

    public static File orden(OrdenTrabajo o) throws IOException {
        String numero = Formato.numeroOrden(o.getId());
        File archivo = archivo("ordenes_trabajo", numero);
        try (PdfWriter w = new PdfWriter(archivo.getAbsolutePath());
             PdfDocument pdf = new PdfDocument(w);
             Document doc = new Document(pdf)) {
            encabezado(doc, "ORDEN DE TRABAJO " + numero, o.getEstadoEtiqueta());
            doc.add(new Paragraph("Cliente: " + o.getClienteNombre()));
            doc.add(new Paragraph("Vehículo: " + o.getVehiculoDescripcion()
                    + (o.getKilometraje() != null ? "    Kilometraje: " + String.format("%,d", o.getKilometraje()) + " km" : "")));
            doc.add(new Paragraph("Ingreso: " + o.getFechaIngreso()
                    + (o.getFechaPromesa() != null ? "    Entrega prometida: " + o.getFechaPromesa() : "")
                    + (o.getFechaEntrega() != null ? "    Entregado: " + o.getFechaEntrega() : "")));
            doc.add(new Paragraph("Mecánico: " + (o.getMecanico() == null ? "Sin asignar" : o.getMecanico())
                    + "    Recibido por: " + o.getUsuario()));
            if (o.getProblema() != null && !o.getProblema().isEmpty()) doc.add(new Paragraph("Problema reportado: " + o.getProblema()));
            if (o.getDiagnostico() != null && !o.getDiagnostico().isEmpty()) doc.add(new Paragraph("Diagnóstico: " + o.getDiagnostico()));

            if (!o.getServicios().isEmpty()) {
                doc.add(new Paragraph("Mano de obra y servicios").setBold().setMarginTop(8));
                Table t = tabla(new float[]{6, 2.5f, 1.2f, 2}, "Servicio", "Mecánico", "Horas", "Precio");
                for (OrdenTrabajo.Servicio s : o.getServicios()) {
                    t.addCell(celda(s.descripcion));
                    t.addCell(celda(s.mecanico == null ? "" : s.mecanico));
                    t.addCell(num(s.horas > 0 ? String.valueOf(s.horas) : ""));
                    t.addCell(num(Formato.moneda(s.precio)));
                }
                doc.add(t);
            }
            if (!o.getRepuestos().isEmpty()) {
                doc.add(new Paragraph("Repuestos").setBold().setMarginTop(8));
                Table t = tabla(new float[]{2, 5, 1.2f, 2, 2}, "Código", "Repuesto", "Cant.", "P. Unit", "Subtotal");
                for (OrdenTrabajo.RepuestoUsado r : o.getRepuestos()) {
                    t.addCell(celda(r.codigo));
                    t.addCell(celda(r.descripcion));
                    t.addCell(num(String.valueOf(r.cantidad)));
                    t.addCell(num(Formato.moneda(r.precioUnitario)));
                    t.addCell(num(Formato.moneda(r.getSubtotal())));
                }
                doc.add(t);
            }
            totales(doc, new String[][]{
                    {"Mano de obra", Formato.moneda(o.getTotalServicios())},
                    {"Repuestos", Formato.moneda(o.getTotalRepuestos())},
                    {"TOTAL (antes de impuestos)", Formato.moneda(o.getTotal())}});
            if (o.getObservaciones() != null && !o.getObservaciones().isEmpty()) {
                doc.add(new Paragraph("Observaciones: " + o.getObservaciones()).setFontSize(9));
            }
            doc.add(new Paragraph("\n\n______________________________          ______________________________\n"
                    + "            Firma del cliente                                              Firma del taller")
                    .setFontSize(9).setMarginTop(30));
        }
        return archivo;
    }

    // ------------------------------------------------------------------

    private static void encabezado(Document doc, String titulo, String estado) {
        ConfiguracionService config = ConfiguracionService.get();
        doc.add(new Paragraph(config.nombreNegocio()).setBold().setFontSize(16).setFontColor(OSCURO)
                .setTextAlignment(TextAlignment.CENTER));
        for (String linea : new String[]{config.get(ConfiguracionService.NEGOCIO_DIRECCION),
                config.get(ConfiguracionService.NEGOCIO_TELEFONO), config.get(ConfiguracionService.NEGOCIO_CORREO)}) {
            if (linea != null && !linea.trim().isEmpty()) {
                doc.add(new Paragraph(linea.trim()).setFontSize(9).setTextAlignment(TextAlignment.CENTER));
            }
        }
        doc.add(new Paragraph(titulo).setBold().setFontSize(13).setTextAlignment(TextAlignment.CENTER).setMarginTop(8));
        if (estado != null) {
            doc.add(new Paragraph(estado).setFontColor(ACENTO).setBold().setTextAlignment(TextAlignment.CENTER));
        }
    }

    private static Table tabla(float[] anchos, String... encabezados) {
        Table t = new Table(UnitValue.createPercentArray(anchos));
        t.setWidth(UnitValue.createPercentValue(100));
        t.setFontSize(9);
        t.setMarginTop(6);
        for (String e : encabezados) {
            t.addHeaderCell(new Cell().add(new Paragraph(e).setBold()).setBackgroundColor(OSCURO).setFontColor(ColorConstants.WHITE));
        }
        return t;
    }

    private static Cell celda(String texto) {
        return new Cell().add(new Paragraph(texto == null ? "" : texto));
    }

    private static Cell num(String texto) {
        return celda(texto).setTextAlignment(TextAlignment.RIGHT);
    }

    private static void totales(Document doc, String[][] filas) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{3, 2}));
        t.setWidth(UnitValue.createPercentValue(45));
        t.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.RIGHT);
        t.setMarginTop(8);
        t.setFontSize(10);
        for (int i = 0; i < filas.length; i++) {
            boolean ultima = i == filas.length - 1;
            Paragraph a = new Paragraph(filas[i][0]);
            Paragraph b = new Paragraph(filas[i][1]);
            if (ultima) { a.setBold(); b.setBold(); }
            t.addCell(new Cell().add(a).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            t.addCell(new Cell().add(b).setTextAlignment(TextAlignment.RIGHT).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        }
        doc.add(t);
    }

    private static File archivo(String carpetaNombre, String numero) throws IOException {
        File carpeta = new File(System.getProperty("user.dir") + File.separator + carpetaNombre);
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta " + carpeta.getAbsolutePath());
        }
        String base = numero.replaceAll("[^A-Za-z0-9_-]", "_");
        File archivo = new File(carpeta, base + ".pdf");
        if (archivo.exists() && !archivo.delete()) {
            archivo = new File(carpeta, base + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf");
        }
        return archivo;
    }
}
