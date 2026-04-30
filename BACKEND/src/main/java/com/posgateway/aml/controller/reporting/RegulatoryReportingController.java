package com.posgateway.aml.controller.reporting;

import com.posgateway.aml.service.reporting.RegulatoryReportingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/regulatory")
public class RegulatoryReportingController {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryReportingController.class);

    private final RegulatoryReportingService reportingService;

    public RegulatoryReportingController(RegulatoryReportingService reportingService) {
        this.reportingService = reportingService;
    }

    /**
     * Generate Currency Transaction Report (CTR)
     * GET /regulatory/ctr
     */
    @GetMapping("/ctr")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<RegulatoryReportingService.CurrencyTransactionReport> generateCtr(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        if (startDate == null) startDate = LocalDateTime.now().minusDays(30);
        if (endDate == null) endDate = LocalDateTime.now();

        log.info("Generating CTR from {} to {}", startDate, endDate);
        return ResponseEntity.ok(reportingService.generateCtr(startDate, endDate));
    }

    /**
     * Generate Large Cash Transaction Report (LCTR)
     * GET /regulatory/lctr
     */
    @GetMapping("/lctr")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<RegulatoryReportingService.LargeCashTransactionReport> generateLctr(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        if (startDate == null) startDate = LocalDateTime.now().minusDays(30);
        if (endDate == null) endDate = LocalDateTime.now();

        log.info("Generating LCTR from {} to {}", startDate, endDate);
        return ResponseEntity.ok(reportingService.generateLctr(startDate, endDate));
    }

    /**
     * Generate International Funds Transfer Report (IFTR)
     * GET /regulatory/iftr
     */
    @GetMapping("/iftr")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'PSP_ADMIN', 'PSP_COMPLIANCE_OFFICER')")
    public ResponseEntity<RegulatoryReportingService.InternationalFundsTransferReport> generateIftr(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        if (startDate == null) startDate = LocalDateTime.now().minusDays(30);
        if (endDate == null) endDate = LocalDateTime.now();

        log.info("Generating IFTR from {} to {}", startDate, endDate);
        return ResponseEntity.ok(reportingService.generateIftr(startDate, endDate));
    }
}
