package com.posgateway.aml.controller.analytics;

import com.posgateway.aml.service.analytics.RiskAnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Risk Analytics Controller
 * Provides endpoints for risk analytics and heatmaps
 */
@RestController
@RequestMapping("/analytics/risk")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'MLRO', 'ANALYST')")
public class RiskAnalyticsController {

    private final RiskAnalyticsService riskAnalyticsService;

    @Autowired
    public RiskAnalyticsController(RiskAnalyticsService riskAnalyticsService) {
        this.riskAnalyticsService = riskAnalyticsService;
    }

    @GetMapping("/heatmap/customer")
    public ResponseEntity<Map<String, RiskAnalyticsService.RiskHeatmapData>> getCustomerRiskHeatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(3);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(riskAnalyticsService.getCustomerRiskHeatmap(startDate, endDate));
    }

    @GetMapping("/heatmap/merchant")
    public ResponseEntity<Map<String, RiskAnalyticsService.RiskHeatmapData>> getMerchantRiskHeatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(3);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(riskAnalyticsService.getMerchantRiskHeatmap(startDate, endDate));
    }

    @GetMapping("/heatmap/geographic")
    public ResponseEntity<Map<String, RiskAnalyticsService.RiskHeatmapData>> getGeographicRiskHeatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(3);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(riskAnalyticsService.getGeographicRiskHeatmap(startDate, endDate));
    }

    @GetMapping("/trends")
    public ResponseEntity<RiskAnalyticsService.RiskTrendAnalysis> analyzeRiskTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(6);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(riskAnalyticsService.analyzeRiskTrends(startDate, endDate));
    }

    @GetMapping("/false-positive-rate")
    public ResponseEntity<Map<String, Double>> getFalsePositiveRate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusMonths(1);
        if (endDate == null) endDate = LocalDateTime.now();
        return ResponseEntity.ok(Map.of("falsePositiveRate", 
                riskAnalyticsService.calculateFalsePositiveRate(startDate, endDate)));
    }
}

