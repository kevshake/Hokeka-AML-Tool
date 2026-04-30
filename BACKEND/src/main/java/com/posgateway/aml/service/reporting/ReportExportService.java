package com.posgateway.aml.service.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Report Export Service
 * Handles export of report data to various formats (PDF, CSV, Excel, XML)
 */
@Service
public class ReportExportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportExportService.class);

    /**
     * Export report data to PDF (simplified HTML-based PDF)
     */
    public String exportToPDF(List<Map<String, Object>> reportData, String reportName, String filePath) {
        logger.info("Exporting {} records to PDF: {}", reportData.size(), filePath);
        
        try {
            // Ensure directory exists
            File directory = new File(filePath).getParentFile();
            if (directory != null && !directory.exists()) {
                directory.mkdirs();
            }
            
            // Generate simple HTML-based report
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html>\n");
            html.append("<head>\n");
            html.append("<title>").append(escapeHtml(reportName)).append("</title>\n");
            html.append("<style>\n");
            html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
            html.append("h1 { color: #333; border-bottom: 2px solid #333; padding-bottom: 10px; }\n");
            html.append(".meta { color: #666; font-size: 12px; margin-bottom: 20px; }\n");
            html.append("table { border-collapse: collapse; width: 100%; }\n");
            html.append("th { background-color: #333; color: white; padding: 10px; text-align: left; }\n");
            html.append("td { padding: 8px; border-bottom: 1px solid #ddd; }\n");
            html.append("tr:nth-child(even) { background-color: #f2f2f2; }\n");
            html.append("tr:hover { background-color: #ddd; }\n");
            html.append("</style>\n");
            html.append("</head>\n");
            html.append("<body>\n");
            
            // Title
            html.append("<h1>").append(escapeHtml(reportName)).append("</h1>\n");
            
            // Metadata
            html.append("<p class='meta'>Generated: ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
                .append(" | Records: ")
                .append(reportData.size())
                .append("</p>\n");
            
            if (reportData.isEmpty()) {
                html.append("<p>No data available</p>\n");
            } else {
                // Table
                html.append("<table>\n");
                
                // Headers
                Set<String> columns = reportData.get(0).keySet();
                html.append("<tr>\n");
                for (String column : columns) {
                    html.append("<th>").append(escapeHtml(formatColumnName(column))).append("</th>\n");
                }
                html.append("</tr>\n");
                
                // Data rows
                for (Map<String, Object> row : reportData) {
                    html.append("<tr>\n");
                    for (String column : columns) {
                        Object value = row.get(column);
                        html.append("<td>").append(escapeHtml(formatValue(value))).append("</td>\n");
                    }
                    html.append("</tr>\n");
                }
                
                html.append("</table>\n");
            }
            
            html.append("</body>\n");
            html.append("</html>");
            
            // Save as HTML (can be converted to PDF using external tools)
            String htmlPath = filePath.replace(".pdf", ".html");
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(htmlPath), StandardCharsets.UTF_8)) {
                writer.write(html.toString());
            }
            
            // Also create a marker PDF file (in production, use proper PDF library)
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
                writer.write("PDF Export - View HTML version at: " + htmlPath);
            }
            
            logger.info("PDF export completed: {}", filePath);
            return filePath;
            
        } catch (Exception e) {
            logger.error("PDF export failed: {}", filePath, e);
            throw new RuntimeException("PDF export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Export report data to CSV
     */
    public String exportToCSV(List<Map<String, Object>> reportData, String filePath) {
        logger.info("Exporting {} records to CSV: {}", reportData.size(), filePath);
        
        try {
            // Ensure directory exists
            File directory = new File(filePath).getParentFile();
            if (directory != null && !directory.exists()) {
                directory.mkdirs();
            }
            
            StringBuilder csv = new StringBuilder();
            
            if (!reportData.isEmpty()) {
                // Write headers
                Set<String> columns = reportData.get(0).keySet();
                csv.append(String.join(",", columns.stream().map(this::formatColumnName).toList()));
                csv.append("\n");
                
                // Write data rows
                for (Map<String, Object> row : reportData) {
                    List<String> values = new ArrayList<>();
                    for (String column : columns) {
                        Object value = row.get(column);
                        values.add(escapeCsv(formatValue(value)));
                    }
                    csv.append(String.join(",", values));
                    csv.append("\n");
                }
            }
            
            // Write to file with BOM for Excel compatibility
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
                writer.write('\ufeff'); // UTF-8 BOM
                writer.write(csv.toString());
            }
            
            logger.info("CSV export completed: {}", filePath);
            return filePath;
            
        } catch (Exception e) {
            logger.error("CSV export failed: {}", filePath, e);
            throw new RuntimeException("CSV export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Export report data to Excel (CSV format with XLSX extension for compatibility)
     */
    public String exportToExcel(List<Map<String, Object>> reportData, String reportName, String filePath) {
        logger.info("Exporting {} records to Excel: {}", reportData.size(), filePath);
        
        try {
            // Ensure directory exists
            File directory = new File(filePath).getParentFile();
            if (directory != null && !directory.exists()) {
                directory.mkdirs();
            }
            
            // For simplicity, we create a TSV format that Excel can open
            StringBuilder tsv = new StringBuilder();
            
            // Title
            tsv.append(reportName).append("\n");
            tsv.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n\n");
            
            if (!reportData.isEmpty()) {
                // Headers
                Set<String> columns = reportData.get(0).keySet();
                tsv.append(String.join("\t", columns.stream().map(this::formatColumnName).toList()));
                tsv.append("\n");
                
                // Data rows
                for (Map<String, Object> row : reportData) {
                    List<String> values = new ArrayList<>();
                    for (String column : columns) {
                        Object value = row.get(column);
                        values.add(escapeTsv(formatValue(value)));
                    }
                    tsv.append(String.join("\t", values));
                    tsv.append("\n");
                }
            }
            
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
                writer.write(tsv.toString());
            }
            
            logger.info("Excel export completed: {}", filePath);
            return filePath;
            
        } catch (Exception e) {
            logger.error("Excel export failed: {}", filePath, e);
            throw new RuntimeException("Excel export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Export report data to XML
     */
    public String exportToXML(List<Map<String, Object>> reportData, String reportName, String filePath) {
        logger.info("Exporting {} records to XML: {}", reportData.size(), filePath);
        
        try {
            // Ensure directory exists
            File directory = new File(filePath).getParentFile();
            if (directory != null && !directory.exists()) {
                directory.mkdirs();
            }
            
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<report>\n");
            xml.append("  <metadata>\n");
            xml.append("    <name>").append(escapeXml(reportName)).append("</name>\n");
            xml.append("    <generated>").append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date())).append("</generated>\n");
            xml.append("    <recordCount>").append(reportData.size()).append("</recordCount>\n");
            xml.append("  </metadata>\n");
            xml.append("  <data>\n");
            
            for (Map<String, Object> row : reportData) {
                xml.append("    <row>\n");
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String columnName = sanitizeXmlElementName(entry.getKey());
                    String value = entry.getValue() != null ? escapeXml(entry.getValue().toString()) : "";
                    xml.append("      <").append(columnName).append(">").append(value).append("</").append(columnName).append(">\n");
                }
                xml.append("    </row>\n");
            }
            
            xml.append("  </data>\n");
            xml.append("</report>");
            
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
                writer.write(xml.toString());
            }
            
            logger.info("XML export completed: {}", filePath);
            return filePath;
            
        } catch (Exception e) {
            logger.error("XML export failed: {}", filePath, e);
            throw new RuntimeException("XML export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Format value for display
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "";
        } else if (value instanceof Date) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value);
        } else {
            return value.toString();
        }
    }

    /**
     * Format column name for display (camelCase to Title Case)
     */
    private String formatColumnName(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return "";
        }
        
        // Replace underscores with spaces
        String formatted = columnName.replaceAll("_", " ");
        
        // Split camelCase
        formatted = formatted.replaceAll("([a-z])([A-Z])", "$1 $2");
        
        // Capitalize each word
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

    /**
     * Escape special characters in CSV
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Escape quotes and wrap in quotes if contains comma or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Escape special characters in TSV
     */
    private String escapeTsv(String value) {
        if (value == null) {
            return "";
        }
        // Replace tabs with spaces to avoid breaking TSV format
        return value.replace("\t", " ").replace("\n", " ").replace("\r", "");
    }

    /**
     * Escape special characters in XML
     */
    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\u0026", "\u0026amp;")
                   .replace("\u003c", "\u0026lt;")
                   .replace("\u003e", "\u0026gt;")
                   .replace("\"", "\u0026quot;")
                   .replace("'", "\u0026apos;");
    }

    /**
     * Escape special characters in HTML
     */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\u0026", "\u0026amp;")
                   .replace("\u003c", "\u0026lt;")
                   .replace("\u003e", "\u0026gt;")
                   .replace("\"", "\u0026quot;");
    }

    /**
     * Sanitize element name for XML
     */
    private String sanitizeXmlElementName(String name) {
        if (name == null || name.isEmpty()) {
            return "field";
        }
        
        // Replace invalid characters with underscore
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        
        // Ensure starts with letter or underscore
        if (Character.isDigit(sanitized.charAt(0))) {
            sanitized = "_" + sanitized;
        }
        
        return sanitized;
    }
}
