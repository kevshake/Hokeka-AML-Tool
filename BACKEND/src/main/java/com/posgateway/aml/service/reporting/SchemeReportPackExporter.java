package com.posgateway.aml.service.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Renders a scheme / central-bank reporting pack (the {@code Map} produced by a
 * {@link ReportGenerator}) into the export format the caller asked for: JSON, CSV or PDF.
 *
 * <p>The pack shape is: scalar summary values (counts, dates, ids), nested maps of
 * breakdowns (e.g. {@code cases_by_status}) and lists of row maps (e.g. {@code sar_details},
 * {@code high_risk_merchants}). CSV and PDF both render every part — summary first, then
 * one table per row set — with keys sorted so the output is deterministic regardless of the
 * generator's map implementation.
 */
@Component
public class SchemeReportPackExporter {

    private static final Color HEADER_BG = new Color(0x8B, 0x40, 0x49);
    private static final Color ZEBRA_BG = new Color(0xF5, 0xF5, 0xF5);
    private static final Color BORDER = new Color(0xCC, 0xCC, 0xCC);
    private static final DateTimeFormatter GENERATED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObjectMapper objectMapper;

    /** Creates the exporter with the application's Jackson mapper (for JSON output). */
    public SchemeReportPackExporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Supported export formats with their file extension and HTTP media type. */
    public enum Format {
        JSON("json", MediaType.APPLICATION_JSON),
        CSV("csv", new MediaType("text", "csv", StandardCharsets.UTF_8)),
        PDF("pdf", MediaType.APPLICATION_PDF);

        private final String extension;
        private final MediaType mediaType;

        Format(String extension, MediaType mediaType) {
            this.extension = extension;
            this.mediaType = mediaType;
        }

        /** File extension (without dot) used in the download filename. */
        public String extension() {
            return extension;
        }

        /** Content-Type sent with the download. */
        public MediaType mediaType() {
            return mediaType;
        }

        /**
         * Parses a caller-supplied format name case-insensitively; blank means JSON.
         *
         * @throws IllegalArgumentException for any value other than JSON, CSV or PDF
         */
        public static Format parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return JSON;
            }
            try {
                return Format.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unsupported format: " + raw + " (use JSON, CSV or PDF)");
            }
        }
    }

    /** Rendered export: the bytes plus the format they were rendered in. */
    public record RenderedPack(byte[] content, Format format) {
    }

    /**
     * Renders the pack in the requested format.
     *
     * @param reportType report type label shown in the PDF title and CSV header
     * @param startDate  reporting period start (inclusive)
     * @param endDate    reporting period end (inclusive)
     * @param data       generator output
     * @param format     target format
     * @return rendered bytes
     */
    public RenderedPack render(String reportType, LocalDate startDate, LocalDate endDate,
                               Map<String, Object> data, Format format) {
        byte[] bytes = switch (format) {
            case JSON -> toJson(data);
            case CSV -> toCsv(reportType, startDate, endDate, data).getBytes(StandardCharsets.UTF_8);
            case PDF -> toPdf(reportType, startDate, endDate, data);
        };
        return new RenderedPack(bytes, format);
    }

    /** Builds a filesystem-safe download filename: {@code <TYPE>_Report_<start>_<end>.<ext>}. */
    public String filename(String reportType, LocalDate startDate, LocalDate endDate, Format format) {
        String safeType = reportType == null ? "REPORT" : reportType.replaceAll("[^A-Za-z0-9_-]", "_");
        return safeType + "_Report_" + startDate + "_" + endDate + "." + format.extension();
    }

    // ---------------------------------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------------------------------

    /** Pretty-printed JSON of the raw pack. */
    private byte[] toJson(Map<String, Object> data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise report pack to JSON: " + e.getOriginalMessage(), e);
        }
    }

    // ---------------------------------------------------------------------------------------
    // CSV
    // ---------------------------------------------------------------------------------------

    /**
     * CSV layout: a commented report header, a {@code section,key,value} summary block
     * (scalars plus flattened nested maps), then one block per row set with its own header
     * row built from the union of row keys. Cells are quoted and formula-injection guarded.
     */
    String toCsv(String reportType, LocalDate startDate, LocalDate endDate, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(csvCell(reportType)).append(" report pack, period ")
                .append(startDate).append(" to ").append(endDate).append("\r\n");

        Map<String, String> summary = summaryRows(data);
        sb.append(csvCell("key")).append(',').append(csvCell("value")).append("\r\n");
        for (Map.Entry<String, String> e : summary.entrySet()) {
            sb.append(csvCell(e.getKey())).append(',').append(csvCell(e.getValue())).append("\r\n");
        }

        for (Map.Entry<String, List<Map<String, Object>>> section : rowSets(data).entrySet()) {
            sb.append("\r\n# ").append(csvCell(section.getKey())).append("\r\n");
            List<Map<String, Object>> rows = section.getValue();
            if (rows.isEmpty()) {
                sb.append(csvCell("no records")).append("\r\n");
                continue;
            }
            List<String> headers = headers(rows);
            sb.append(String.join(",", headers.stream().map(this::csvCell).toList())).append("\r\n");
            for (Map<String, Object> row : rows) {
                sb.append(String.join(",", headers.stream()
                        .map(h -> csvCell(stringify(row.get(h))))
                        .toList())).append("\r\n");
            }
        }
        return sb.toString();
    }

    /** Quotes and escapes a CSV cell, prefixing formula-leading characters to block injection. */
    private String csvCell(String value) {
        if (value == null) {
            return "\"\"";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    // ---------------------------------------------------------------------------------------
    // PDF
    // ---------------------------------------------------------------------------------------

    /** Renders the pack as an A4-landscape PDF: title, period, summary table, one table per row set. */
    byte[] toPdf(String reportType, LocalDate startDate, LocalDate endDate, Map<String, Object> data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, HEADER_BG);
            Font meta = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font section = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);

            document.add(new Paragraph(reportType + " Report Pack", title));
            document.add(new Paragraph("Period: " + startDate + " to " + endDate
                    + "    Generated: " + LocalDateTime.now().format(GENERATED_FMT), meta));

            Paragraph summaryHeading = new Paragraph("Summary", section);
            summaryHeading.setSpacingBefore(12);
            summaryHeading.setSpacingAfter(6);
            document.add(summaryHeading);
            Map<String, String> summary = summaryRows(data);
            List<String> kvHeaders = List.of("key", "value");
            List<List<String>> kvRows = new ArrayList<>();
            summary.forEach((k, v) -> kvRows.add(List.of(k, v)));
            document.add(table(kvHeaders, kvRows));

            for (Map.Entry<String, List<Map<String, Object>>> rowSet : rowSets(data).entrySet()) {
                Paragraph heading = new Paragraph(rowSet.getKey(), section);
                heading.setSpacingBefore(14);
                heading.setSpacingAfter(6);
                document.add(heading);
                List<Map<String, Object>> rows = rowSet.getValue();
                if (rows.isEmpty()) {
                    document.add(new Paragraph("No records for this period.", meta));
                    continue;
                }
                List<String> headers = headers(rows);
                List<List<String>> cells = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    cells.add(headers.stream().map(h -> stringify(row.get(h))).toList());
                }
                document.add(table(headers, cells));
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not render report pack PDF: " + e.getMessage(), e);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    /** Builds a full-width bordered table with a shaded header row and zebra-striped body. */
    private PdfPTable table(List<String> headers, List<List<String>> rows) {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(4);
            cell.setBorderColor(BORDER);
            table.addCell(cell);
        }
        boolean zebra = false;
        for (List<String> row : rows) {
            for (String value : row) {
                PdfPCell cell = new PdfPCell(new Phrase(value, bodyFont));
                cell.setPadding(4);
                cell.setBorder(Rectangle.BOX);
                cell.setBorderColor(BORDER);
                cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                if (zebra) {
                    cell.setBackgroundColor(ZEBRA_BG);
                }
                table.addCell(cell);
            }
            zebra = !zebra;
        }
        return table;
    }

    // ---------------------------------------------------------------------------------------
    // Pack decomposition shared by CSV and PDF
    // ---------------------------------------------------------------------------------------

    /**
     * Flattens the pack's scalar values and nested maps into sorted {@code key -> value}
     * summary rows; nested map entries become {@code parent.child}. Row-set lists are excluded
     * (they render as their own tables); other lists are joined into one cell.
     */
    Map<String, String> summaryRows(Map<String, Object> data) {
        Map<String, String> summary = new TreeMap<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            Object value = e.getValue();
            if (isRowSet(value)) {
                continue;
            }
            if (value instanceof Map<?, ?> nested) {
                for (Map.Entry<?, ?> n : nested.entrySet()) {
                    summary.put(e.getKey() + "." + n.getKey(), stringify(n.getValue()));
                }
            } else {
                summary.put(e.getKey(), stringify(value));
            }
        }
        return summary;
    }

    /** Returns every list-of-row-maps in the pack keyed (and sorted) by its pack key. */
    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, Object>>> rowSets(Map<String, Object> data) {
        Map<String, List<Map<String, Object>>> sets = new TreeMap<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (isRowSet(e.getValue())) {
                sets.put(e.getKey(), (List<Map<String, Object>>) e.getValue());
            }
        }
        return sets;
    }

    /** A row set is a list whose elements are all maps (an empty list counts, so it renders as "no records"). */
    private boolean isRowSet(Object value) {
        if (!(value instanceof List<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (!(item instanceof Map)) {
                return false;
            }
        }
        return true;
    }

    /** Sorted union of the keys across all rows, used as the table header. */
    private List<String> headers(List<Map<String, Object>> rows) {
        TreeSet<String> keys = new TreeSet<>();
        for (Map<String, Object> row : rows) {
            keys.addAll(row.keySet());
        }
        return new ArrayList<>(keys);
    }

    /** Null-safe string form of a cell value; lists are joined with "; ". */
    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            return String.join("; ", list.stream().map(this::stringify).toList());
        }
        return value.toString();
    }
}
