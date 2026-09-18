package com.posgateway.aml.controller.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.service.reporting.SchemeReportingService;
import com.posgateway.aml.service.reporting.ReportRunTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Scheme Reporting Controller
 * Exports reporting packs for Central Banks and Schemes
 */
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/reporting/schemes")
public class SchemeReportingController {

    private static final Logger logger = LoggerFactory.getLogger(SchemeReportingController.class);

    private final SchemeReportingService reportingService;
    private final ReportRunTraceService reportRunTraceService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SchemeReportingController(SchemeReportingService reportingService, ObjectMapper objectMapper,
                                     ReportRunTraceService reportRunTraceService) {
        this.reportingService = reportingService;
        this.objectMapper = objectMapper;
        this.reportRunTraceService = reportRunTraceService;
    }

    /**
     * Generate Scheme Reporting Pack
     * GET /api/v1/reporting/schemes/pack
     * 
     * @param type      Report Type (CENTRAL_BANK, SCHEME_MONITORING)
     * @param startDate Start Date
     * @param endDate   End Date
     * @return File download (JSON for now, CSV/PDF logic to be added in Utils)
     */
    @GetMapping("/pack")
    @PreAuthorize("hasAnyRole('PSP_ADMIN', 'PSP_USER', 'ADMIN', 'APP_CONTROLLER')")
    public ResponseEntity<byte[]> generateReportPack(
            @RequestParam("type") String type,
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "format", defaultValue = "JSON") String format) {

        logger.info("Generating {} report pack ({}) from {} to {}", type, format, startDate, endDate);

        try {
            Map<String, Object> data = reportingService.generateReport(type, startDate, endDate);
            String reportCode = switch (type.toUpperCase()) {
                case "CENTRAL_BANK" -> "SCM_001";
                case "SCHEME_MONITORING" -> "SCM_002";
                default -> throw new IllegalArgumentException("Unknown report type: " + type);
            };
            String executionId = reportRunTraceService.recordDocument(
                    reportCode, startDate.atStartOfDay(), endDate.atTime(23, 59, 59), data);

            String fmt = format == null ? "JSON" : format.trim().toUpperCase();
            byte[] content;
            String extension;
            switch (fmt) {
                case "CSV" -> {
                    content = toCsv(data).getBytes(StandardCharsets.UTF_8);
                    extension = "csv";
                }
                case "JSON" -> {
                    content = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
                    extension = "json";
                }
                default -> {
                    return ResponseEntity.badRequest()
                            .body(("Unsupported format: " + format + " (use JSON or CSV)")
                                    .getBytes(StandardCharsets.UTF_8));
                }
            }

            String filename = String.format("%s_Report_%s_%s.%s",
                    type, startDate, LocalDateTime.now().getNano(), extension);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header("X-Report-Execution-Id", executionId)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(content.length)
                    .body(content);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Error generating report pack", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Render a report map as CSV. If the map holds a single list-of-rows value (the
     * common "pack" shape) that list is emitted as a table with a header row built from
     * the union of row keys; otherwise the flat key/value pairs are emitted. Cells are
     * quoted/escaped and CSV-injection-guarded.
     */
    @SuppressWarnings("unchecked")
    private String toCsv(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();

        // Prefer the first value that is a list of maps — that is the report's row set.
        List<Map<String, Object>> rows = null;
        String rowsKey = null;
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (e.getValue() instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
                rows = (List<Map<String, Object>>) list;
                rowsKey = e.getKey();
                break;
            }
        }

        if (rows != null) {
            Set<String> headers = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                headers.addAll(row.keySet());
            }
            sb.append("# ").append(csvCell(rowsKey)).append('\n');
            sb.append(String.join(",", headers.stream().map(this::csvCell).toList())).append('\n');
            for (Map<String, Object> row : rows) {
                sb.append(String.join(",", headers.stream()
                        .map(h -> csvCell(row.get(h) == null ? "" : row.get(h).toString()))
                        .toList())).append('\n');
            }
            // Emit any remaining scalar summary fields after the table.
            for (Map.Entry<String, Object> e : data.entrySet()) {
                if (!e.getKey().equals(rowsKey) && !(e.getValue() instanceof List) && !(e.getValue() instanceof Map)) {
                    sb.append(csvCell(e.getKey())).append(',').append(csvCell(String.valueOf(e.getValue()))).append('\n');
                }
            }
            return sb.toString();
        }

        // No row set — emit flat key,value pairs.
        sb.append("key,value\n");
        for (Map.Entry<String, Object> e : data.entrySet()) {
            sb.append(csvCell(e.getKey())).append(',').append(csvCell(String.valueOf(e.getValue()))).append('\n');
        }
        return sb.toString();
    }

    /** Quote and escape a CSV cell, neutralising spreadsheet-formula injection. */
    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v; // prevent formula injection in spreadsheet apps
        }
        v = v.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }
}
