package com.posgateway.aml.service.reporting;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Report Export Service
 * Produces real PDF (via OpenPDF / com.lowagie.text.*) and proper CSV bytes.
 * All output is returned as byte[] — no file-system writes.
 */
@Service
public class ReportExportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportExportService.class);

    // Brand palette (matches InvoicePdfService)
    private static final Color BRAND_BURGUNDY = new Color(0x8B, 0x40, 0x49);
    private static final Color BODY_DARK      = new Color(0x33, 0x33, 0x33);
    private static final Color LIGHT_GRAY     = new Color(0xF5, 0xF5, 0xF5);
    private static final Color MID_GRAY       = new Color(0xCC, 0xCC, 0xCC);
    private static final Color WHITE          = Color.WHITE;

    private static final String PLATFORM_NAME = "Hokeka AML Platform";
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    // -------------------------------------------------------------------------
    // PDF export
    // -------------------------------------------------------------------------

    /**
     * Produce a real PDF for the given report data.
     *
     * @param reportData   rows — each map is one data row; keys are column names
     * @param reportName   human-readable report title
     * @param metadata     optional extra metadata pairs shown in the header table
     *                     (e.g. period, generated-by, status); may be null or empty
     * @return raw PDF bytes
     */
    public byte[] exportToPDF(List<Map<String, Object>> reportData,
                              String reportName,
                              Map<String, String> metadata) {

        logger.info("Generating PDF for report '{}' with {} rows", reportName, reportData.size());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 60, 60);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageFooterEvent(PLATFORM_NAME));
            document.open();

            addPdfHeader(document, reportName);
            document.add(spacer(12));

            if (metadata != null && !metadata.isEmpty()) {
                addMetadataTable(document, metadata);
                document.add(spacer(16));
            }

            addDataTable(document, reportData);

        } catch (DocumentException e) {
            logger.error("PDF generation failed for report '{}': {}", reportName, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        } finally {
            document.close();
        }

        logger.info("PDF generated: {} bytes", out.size());
        return out.toByteArray();
    }

    /**
     * Convenience overload with no extra metadata.
     */
    public byte[] exportToPDF(List<Map<String, Object>> reportData, String reportName) {
        return exportToPDF(reportData, reportName, null);
    }

    // -------------------------------------------------------------------------
    // CSV export
    // -------------------------------------------------------------------------

    /**
     * Produce a UTF-8 CSV (with BOM for Excel compatibility).
     *
     * @param reportData rows — each map is one data row; keys are column names
     * @return raw CSV bytes (UTF-8 with BOM)
     */
    public byte[] exportToCSV(List<Map<String, Object>> reportData) {
        logger.info("Generating CSV for {} rows", reportData.size());

        StringBuilder csv = new StringBuilder();
        csv.append('﻿'); // UTF-8 BOM — Excel opens without "garbled" dialog

        if (!reportData.isEmpty()) {
            Set<String> columns = reportData.get(0).keySet();

            // Header row
            csv.append(columns.stream()
                    .map(this::formatColumnName)
                    .map(this::csvEscape)
                    .collect(Collectors.joining(",")));
            csv.append("\r\n");

            // Data rows
            for (Map<String, Object> row : reportData) {
                csv.append(columns.stream()
                        .map(col -> csvEscape(formatValue(row.get(col))))
                        .collect(Collectors.joining(",")));
                csv.append("\r\n");
            }
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        logger.info("CSV generated: {} bytes", bytes.length);
        return bytes;
    }

    // -------------------------------------------------------------------------
    // XML export (kept for completeness, not changed in contract)
    // -------------------------------------------------------------------------

    /**
     * Produce an XML export.
     *
     * @param reportData rows
     * @param reportName report title embedded in metadata
     * @return raw XML bytes (UTF-8)
     */
    public byte[] exportToXML(List<Map<String, Object>> reportData, String reportName) {
        logger.info("Generating XML for report '{}' with {} rows", reportName, reportData.size());

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<report>\n");
        xml.append("  <metadata>\n");
        xml.append("    <name>").append(escapeXml(reportName)).append("</name>\n");
        xml.append("    <generated>")
           .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
           .append("</generated>\n");
        xml.append("    <recordCount>").append(reportData.size()).append("</recordCount>\n");
        xml.append("  </metadata>\n");
        xml.append("  <data>\n");

        for (Map<String, Object> row : reportData) {
            xml.append("    <row>\n");
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String colName = sanitizeXmlElementName(entry.getKey());
                String value = entry.getValue() != null ? escapeXml(entry.getValue().toString()) : "";
                xml.append("      <").append(colName).append(">")
                   .append(value)
                   .append("</").append(colName).append(">\n");
            }
            xml.append("    </row>\n");
        }

        xml.append("  </data>\n");
        xml.append("</report>");

        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // PDF internals
    // -------------------------------------------------------------------------

    private void addPdfHeader(Document doc, String reportName) throws DocumentException {
        Font titleFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND_BURGUNDY);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9, BODY_DARK);

        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{65, 35});

        // Left: report title
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0);
        Paragraph titlePara = new Paragraph(reportName, titleFont);
        leftCell.addElement(titlePara);
        headerTable.addCell(leftCell);

        // Right: platform name + timestamp
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.setPadding(0);
        Paragraph platformPara = new Paragraph(PLATFORM_NAME,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND_BURGUNDY));
        platformPara.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(platformPara);
        Paragraph tsPara = new Paragraph("Generated: " + LocalDateTime.now().format(TS_FMT), subtitleFont);
        tsPara.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(tsPara);
        headerTable.addCell(rightCell);

        doc.add(headerTable);

        // Burgundy divider
        LineSeparator separator = new LineSeparator(2f, 100f, BRAND_BURGUNDY, Element.ALIGN_CENTER, -4f);
        doc.add(new Chunk(separator));
    }

    private void addMetadataTable(Document doc, Map<String, String> metadata) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BODY_DARK);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 8, BODY_DARK);

        // Two-column layout: label | value | label | value
        PdfPTable meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setWidths(new float[]{20, 30, 20, 30});

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            PdfPCell labelCell = new PdfPCell(new Phrase(entry.getKey(), labelFont));
            labelCell.setBackgroundColor(LIGHT_GRAY);
            labelCell.setPadding(5);
            labelCell.setBorder(Rectangle.BOX);
            labelCell.setBorderColor(MID_GRAY);
            meta.addCell(labelCell);

            PdfPCell valueCell = new PdfPCell(new Phrase(
                    entry.getValue() != null ? entry.getValue() : "", valueFont));
            valueCell.setPadding(5);
            valueCell.setBorder(Rectangle.BOX);
            valueCell.setBorderColor(MID_GRAY);
            meta.addCell(valueCell);
        }

        // Pad to even number of columns if needed
        int remainder = metadata.size() % 2;
        if (remainder != 0) {
            meta.addCell(emptyCell());
            meta.addCell(emptyCell());
        }

        doc.add(meta);
    }

    private void addDataTable(Document doc, java.util.List<Map<String, Object>> reportData) throws DocumentException {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, WHITE);
        Font bodyFont   = FontFactory.getFont(FontFactory.HELVETICA, 7, BODY_DARK);

        if (reportData.isEmpty()) {
            doc.add(new Paragraph("No data available for this report.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, BODY_DARK)));
            return;
        }

        Set<String> rawColumns = reportData.get(0).keySet();
        int colCount = rawColumns.size();

        PdfPTable table = new PdfPTable(colCount);
        table.setWidthPercentage(100);

        // Header row — burgundy background, white text
        for (String col : rawColumns) {
            PdfPCell hCell = new PdfPCell(new Phrase(formatColumnName(col), headerFont));
            hCell.setBackgroundColor(BRAND_BURGUNDY);
            hCell.setPadding(6);
            hCell.setBorder(Rectangle.NO_BORDER);
            hCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(hCell);
        }

        // Data rows — alternating white / light-grey
        boolean alt = false;
        for (Map<String, Object> row : reportData) {
            Color rowBg = alt ? LIGHT_GRAY : WHITE;
            alt = !alt;

            for (String col : rawColumns) {
                Object val = row.get(col);
                String text = formatValue(val);
                boolean isNumeric = val instanceof Number;

                PdfPCell dCell = new PdfPCell(new Phrase(text, bodyFont));
                dCell.setBackgroundColor(rowBg);
                dCell.setPadding(5);
                dCell.setBorder(Rectangle.BOX);
                dCell.setBorderColor(MID_GRAY);
                dCell.setHorizontalAlignment(isNumeric ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                table.addCell(dCell);
            }
        }

        doc.add(table);
    }

    private PdfPCell emptyCell() {
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    /** Page N of M footer — registered as a page event on the PdfWriter. */
    private static class PageFooterEvent extends PdfPageEventHelper {
        private PdfTemplate totalPageTemplate;
        private final String platformName;

        PageFooterEvent(String platformName) {
            this.platformName = platformName;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPageTemplate = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Font font = FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(0x88, 0x88, 0x88));
            PdfContentByte cb = writer.getDirectContent();

            // Left: platform name
            Phrase left = new Phrase(platformName, font);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, left,
                    document.leftMargin(), document.bottomMargin() - 10, 0);

            // Right: Page N of M
            cb.beginText();
            cb.setFontAndSize(FontFactory.getFont(FontFactory.HELVETICA, 7).getBaseFont(), 7);
            cb.setColorFill(new Color(0x88, 0x88, 0x88));
            float x = document.right();
            float y = document.bottomMargin() - 10;
            cb.setTextMatrix(x - 60, y);
            cb.showText("Page " + writer.getPageNumber() + " of ");
            cb.endText();

            cb.addTemplate(totalPageTemplate, x - 16, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalPageTemplate.beginText();
            totalPageTemplate.setFontAndSize(
                    FontFactory.getFont(FontFactory.HELVETICA, 7).getBaseFont(), 7);
            totalPageTemplate.setColorFill(new Color(0x88, 0x88, 0x88));
            totalPageTemplate.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPageTemplate.endText();
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private static Paragraph spacer(float pts) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(pts);
        p.setLeading(0f);
        return p;
    }

    private String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDateTime ldt) return ldt.format(TS_FMT);
        return value.toString();
    }

    /**
     * Convert camelCase / snake_case column name to Title Case with spaces.
     */
    private String formatColumnName(String columnName) {
        if (columnName == null || columnName.isEmpty()) return "";
        String formatted = columnName.replaceAll("_", " ")
                                     .replaceAll("([a-z])([A-Z])", "$1 $2");
        String[] words = formatted.toLowerCase().split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }

    private String sanitizeXmlElementName(String name) {
        if (name == null || name.isEmpty()) return "field";
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (Character.isDigit(sanitized.charAt(0))) sanitized = "_" + sanitized;
        return sanitized;
    }
}
