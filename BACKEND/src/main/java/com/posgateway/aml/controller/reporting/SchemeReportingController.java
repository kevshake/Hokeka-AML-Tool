package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.service.reporting.ReportRunTraceService;
import com.posgateway.aml.service.reporting.SchemeReportPackExporter;
import com.posgateway.aml.service.reporting.SchemeReportingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

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
    private final SchemeReportPackExporter packExporter;

    /** Wires the pack generator, the run-trace recorder and the format renderer. */
    @Autowired
    public SchemeReportingController(SchemeReportingService reportingService,
                                     ReportRunTraceService reportRunTraceService,
                                     SchemeReportPackExporter packExporter) {
        this.reportingService = reportingService;
        this.reportRunTraceService = reportRunTraceService;
        this.packExporter = packExporter;
    }

    /**
     * Generate a Scheme Reporting Pack and download it in the requested format.
     * GET /api/v1/reporting/schemes/pack
     *
     * @param type      report type (CENTRAL_BANK, SCHEME_MONITORING)
     * @param startDate period start (inclusive)
     * @param endDate   period end (inclusive)
     * @param format    JSON (default), CSV or PDF — drives the body, Content-Type and filename
     * @return the rendered pack as an attachment, with an {@code X-Report-Execution-Id} trace header;
     *         400 for an unknown type/format or an inverted period
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
            // Validate everything cheap before generating, so a bad request records no run trace.
            SchemeReportPackExporter.Format fmt = SchemeReportPackExporter.Format.parse(format);
            String normalizedType = type.trim().toUpperCase(Locale.ROOT);
            String reportCode = switch (normalizedType) {
                case "CENTRAL_BANK" -> "SCM_001";
                case "SCHEME_MONITORING" -> "SCM_002";
                default -> throw new IllegalArgumentException("Unknown report type: " + type);
            };
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("endDate must not be before startDate");
            }

            Map<String, Object> data = reportingService.generateReport(normalizedType, startDate, endDate);
            String executionId = reportRunTraceService.recordDocument(
                    reportCode, startDate.atStartOfDay(), endDate.atTime(23, 59, 59), data);

            SchemeReportPackExporter.RenderedPack pack =
                    packExporter.render(normalizedType, startDate, endDate, data, fmt);
            String filename = packExporter.filename(normalizedType, startDate, endDate, fmt);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename).build().toString())
                    .header("X-Report-Execution-Id", executionId)
                    .contentType(fmt.mediaType())
                    .contentLength(pack.content().length)
                    .body(pack.content());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(String.valueOf(e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            if (e instanceof org.springframework.security.access.AccessDeniedException denied) {
                throw denied;
            }
            logger.error("Error generating report pack", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
