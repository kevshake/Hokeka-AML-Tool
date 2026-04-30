package com.posgateway.aml.controller.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.service.reporting.SchemeReportingService;
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
import java.util.Map;

/**
 * Scheme Reporting Controller
 * Exports reporting packs for Central Banks and Schemes
 */
@RestController
@RequestMapping("/reporting/schemes")
public class SchemeReportingController {

    private static final Logger logger = LoggerFactory.getLogger(SchemeReportingController.class);

    private final SchemeReportingService reportingService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SchemeReportingController(SchemeReportingService reportingService, ObjectMapper objectMapper) {
        this.reportingService = reportingService;
        this.objectMapper = objectMapper;
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
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        logger.info("Generating {} report pack from {} to {}", type, startDate, endDate);

        try {
            Map<String, Object> data = reportingService.generateReport(type, startDate, endDate);

            // For MVP, returning JSON as a file.
            // In production, this would convert 'data' to CSV or PDF byte array.
            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            byte[] content = jsonContent.getBytes(StandardCharsets.UTF_8);

            String filename = String.format("%s_Report_%s_%s.json", type, startDate, LocalDateTime.now().getNano());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(content.length)
                    .body(content);

        } catch (Exception e) {
            logger.error("Error generating report pack", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
