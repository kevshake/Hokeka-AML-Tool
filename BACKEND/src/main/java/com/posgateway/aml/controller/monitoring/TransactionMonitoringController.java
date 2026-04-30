package com.posgateway.aml.controller.monitoring;

import com.posgateway.aml.service.monitoring.TransactionMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for Transaction Monitoring Dashboard
 */
@RestController
@RequestMapping("/monitoring")
public class TransactionMonitoringController {

    private final TransactionMonitoringService monitoringService;

    public TransactionMonitoringController(TransactionMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    /**
     * Get dashboard statistics
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        return ResponseEntity.ok(monitoringService.getDashboardStats());
    }

    /**
     * Get risk score distribution
     */
    @GetMapping("/risk-distribution")
    public ResponseEntity<Map<String, Object>> getRiskDistribution() {
        return ResponseEntity.ok(monitoringService.getRiskDistribution());
    }

    /**
     * Get top risk indicators
     */
    @GetMapping("/risk-indicators")
    public ResponseEntity<List<Map<String, Object>>> getTopRiskIndicators() {
        return ResponseEntity.ok(monitoringService.getTopRiskIndicators());
    }

    /**
     * Get recent monitoring activity
     */
    @GetMapping("/recent-activity")
    public ResponseEntity<List<Map<String, Object>>> getRecentActivity() {
        return ResponseEntity.ok(monitoringService.getRecentActivity());
    }

    /**
     * Get monitored transactions with pagination
     */
    @GetMapping("/transactions")
    public ResponseEntity<org.springframework.data.domain.Page<Map<String, Object>>> getMonitoredTransactions(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "25") int size,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String decision) {
        return ResponseEntity.ok(monitoringService.getMonitoredTransactions(page, size, riskLevel, decision));
    }

    /**
     * Get SARs for monitoring
     */
    @GetMapping("/sars")
    public ResponseEntity<List<Map<String, Object>>> getMonitoringSARs() {
        return ResponseEntity.ok(monitoringService.getMonitoringSARs());
    }

    /**
     * Generate decline report
     * GET /api/v1/monitoring/reports/declines
     */
    @GetMapping("/reports/declines")
    public ResponseEntity<Map<String, Object>> generateDeclineReport() {
        return ResponseEntity.ok(monitoringService.generateDeclineReport());
    }

    /**
     * Generate monitoring summary report
     * GET /api/v1/monitoring/reports/summary
     */
    @GetMapping("/reports/summary")
    public ResponseEntity<Map<String, Object>> generateMonitoringSummary() {
        return ResponseEntity.ok(monitoringService.generateMonitoringSummary());
    }
}

