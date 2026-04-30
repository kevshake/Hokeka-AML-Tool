package com.posgateway.aml.controller.analytics;

import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.service.ReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/reporting")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(reportingService.summary());
    }

    @GetMapping("/cases/status")
    public ResponseEntity<Map<CaseStatus, Long>> casesByStatus() {
        return ResponseEntity.ok(reportingService.casesByStatus());
    }

    @GetMapping("/sars/status")
    public ResponseEntity<Map<SarStatus, Long>> sarsByStatus() {
        return ResponseEntity.ok(reportingService.sarsByStatus());
    }

    @GetMapping("/cases/daily")
    public ResponseEntity<Map<String, Long>> casesDaily(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(reportingService.dailyCountsCases(days));
    }

    @GetMapping("/sars/daily")
    public ResponseEntity<Map<String, Long>> sarsDaily(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(reportingService.dailyCountsSars(days));
    }

    @GetMapping("/audit/last24h")
    public ResponseEntity<Long> auditLast24h() {
        return ResponseEntity.ok(reportingService.auditCountLastHours(24));
    }

    /**
     * Hourly audit counts for sparkline
     * GET /api/v1/reporting/audit/hourly?hours=24
     */
    @GetMapping("/audit/hourly")
    public ResponseEntity<Map<String, Long>> auditHourly(@RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(reportingService.auditHourly(hours));
    }

    /**
     * Status+Priority matrix for cases
     */
    @GetMapping("/cases/matrix")
    public ResponseEntity<Map<String, Long>> casesMatrix() {
        return ResponseEntity.ok(reportingService.casesStatusPriorityMatrix());
    }

    /**
     * Merchant-filtered daily series
     */
    @GetMapping("/cases/daily/merchant")
    public ResponseEntity<Map<String, Long>> casesDailyByMerchant(@RequestParam(defaultValue = "7") int days,
            @RequestParam String merchantId) {
        return ResponseEntity.ok(reportingService.dailyCountsCases(days, merchantId));
    }
}
