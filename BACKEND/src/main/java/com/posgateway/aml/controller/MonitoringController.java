package com.posgateway.aml.controller;

import com.posgateway.aml.entity.ModelMetrics;
import com.posgateway.aml.service.MonitoringMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Monitoring Controller
 * Provides endpoints for model metrics and monitoring
 */
@RestController
@RequestMapping("/monitoring")
public class MonitoringController {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);

    private final MonitoringMetricsService metricsService;

    @Autowired
    public MonitoringController(MonitoringMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * Get latest metrics
     * GET /api/v1/monitoring/metrics/latest
     * 
     * @return Latest model metrics
     */
    @GetMapping("/metrics/latest")
    public ResponseEntity<ModelMetrics> getLatestMetrics() {
        ModelMetrics metrics = metricsService.getLatestMetrics();
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get metrics for specific date
     * GET /api/v1/monitoring/metrics/date/{date}
     * 
     * @param date Date in YYYY-MM-DD format
     * @return Model metrics for date
     */
    @GetMapping("/metrics/date/{date}")
    public ResponseEntity<ModelMetrics> getMetricsForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ModelMetrics metrics = metricsService.getMetricsForDate(date);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get metrics for date range
     * GET /api/v1/monitoring/metrics/range
     * 
     * @param startDate Start date
     * @param endDate End date
     * @return List of model metrics
     */
    @GetMapping("/metrics/range")
    public ResponseEntity<List<ModelMetrics>> getMetricsForRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<ModelMetrics> metrics = metricsService.getMetricsForDateRange(startDate, endDate);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Trigger manual metrics computation
     * POST /api/v1/monitoring/metrics/compute
     * 
     * @return Success message
     */
    @PostMapping("/metrics/compute")
    public ResponseEntity<String> computeMetrics() {
        logger.info("Manual metrics computation triggered");
        metricsService.computeDailyMetrics();
        return ResponseEntity.ok("Metrics computation completed");
    }

    /**
     * Health check endpoint
     * GET /api/v1/monitoring/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Monitoring Service is running");
    }
}

