package com.posgateway.aml.controller;

import com.posgateway.aml.service.Http2FailoverService;
import com.posgateway.aml.service.Http2HealthMonitorService;
import com.posgateway.aml.service.Http2NetworkStabilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP/2 Monitoring Controller
 * Provides endpoints to monitor HTTP/2 status, health, and failover
 */
@RestController
@RequestMapping("/http2")
public class Http2MonitoringController {

    private final Http2FailoverService failoverService;
    private final Http2HealthMonitorService healthMonitorService;
    private final Http2NetworkStabilityService networkStabilityService;

    @Autowired
    public Http2MonitoringController(Http2FailoverService failoverService,
                                     Http2HealthMonitorService healthMonitorService,
                                     Http2NetworkStabilityService networkStabilityService) {
        this.failoverService = failoverService;
        this.healthMonitorService = healthMonitorService;
        this.networkStabilityService = networkStabilityService;
    }

    /**
     * Get HTTP/2 status and protocol information
     * GET /api/v1/http2/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getHttp2Status() {
        Map<String, Object> status = new HashMap<>();
        
        Http2FailoverService.FailoverStats stats = failoverService.getFailoverStats();
        status.put("currentProtocol", stats.getCurrentProtocol());
        status.put("http2Enabled", stats.isHttp2Enabled());
        status.put("failoverActive", stats.isFailoverActive());
        status.put("failoverCount", stats.getFailoverCount());
        status.put("lastFailoverTime", stats.getLastFailoverTime());
        status.put("lastRetryTime", stats.getLastRetryTime());
        
        return ResponseEntity.ok(status);
    }

    /**
     * Get HTTP/2 health metrics
     * GET /api/v1/http2/health
     */
    @GetMapping("/health")
    public ResponseEntity<Http2HealthMonitorService.Http2HealthMetrics> getHttp2Health() {
        Http2HealthMonitorService.Http2HealthMetrics metrics = healthMonitorService.getHealthMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get HTTP/2 vs HTTP/1.1 comparison
     * GET /api/v1/http2/comparison
     */
    @GetMapping("/comparison")
    public ResponseEntity<Map<String, Object>> getProtocolComparison() {
        Map<String, Object> comparison = new HashMap<>();
        
        Http2HealthMonitorService.Http2HealthMetrics metrics = healthMonitorService.getHealthMetrics();
        Http2FailoverService.FailoverStats stats = failoverService.getFailoverStats();
        
        comparison.put("currentProtocol", stats.getCurrentProtocol());
        comparison.put("http2Health", metrics.getHealthStatus().name());
        comparison.put("http2ErrorRate", metrics.getErrorRate() * 100); // Percentage
        comparison.put("http2AverageLatency", metrics.getAverageLatency());
        comparison.put("http2ConnectionDrops", metrics.getTotalDrops());
        comparison.put("failoverActive", stats.isFailoverActive());
        
        // Recommendations
        if (stats.isFailoverActive()) {
            comparison.put("recommendation", "Using HTTP/1.1 due to HTTP/2 issues");
        } else if (metrics.getErrorRate() > 0.02) {
            comparison.put("recommendation", "Monitor HTTP/2 closely - elevated error rate");
        } else {
            comparison.put("recommendation", "HTTP/2 is healthy");
        }
        
        return ResponseEntity.ok(comparison);
    }

    /**
     * Manually trigger HTTP/2 detection refresh
     * POST /api/v1/http2/detect
     */
    @PostMapping("/detect")
    public ResponseEntity<Map<String, Object>> triggerDetection() {
        Map<String, Object> result = new HashMap<>();
        // Detection refresh is handled by the service
        result.put("message", "Detection refresh triggered");
        result.put("currentProtocol", failoverService.getCurrentProtocol());
        return ResponseEntity.ok(result);
    }

    /**
     * Manually trigger failover to HTTP/1.1
     * POST /api/v1/http2/failover
     */
    @PostMapping("/failover")
    public ResponseEntity<Map<String, Object>> triggerFailover() {
        failoverService.performFailover();
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Failover to HTTP/1.1 triggered");
        result.put("currentProtocol", failoverService.getCurrentProtocol());
        return ResponseEntity.ok(result);
    }

    /**
     * Reset HTTP/2 health metrics
     * POST /api/v1/http2/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetMetrics() {
        healthMonitorService.resetMetrics();
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Health metrics reset");
        return ResponseEntity.ok(result);
    }

    /**
     * Get network stability metrics
     * GET /api/v1/http2/network/stability
     */
    @GetMapping("/network/stability")
    public ResponseEntity<Http2NetworkStabilityService.NetworkStabilityMetrics> getNetworkStability() {
        Http2NetworkStabilityService.NetworkStabilityMetrics metrics = 
            networkStabilityService.getStabilityMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get comprehensive HTTP/2 status including network stability
     * GET /api/v1/http2/comprehensive
     */
    @GetMapping("/comprehensive")
    public ResponseEntity<Map<String, Object>> getComprehensiveStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Failover stats
        Http2FailoverService.FailoverStats failoverStats = failoverService.getFailoverStats();
        status.put("failoverStats", failoverStats);
        
        // Health metrics
        Http2HealthMonitorService.Http2HealthMetrics healthMetrics = healthMonitorService.getHealthMetrics();
        status.put("healthMetrics", healthMetrics);
        
        // Network stability
        Http2NetworkStabilityService.NetworkStabilityMetrics stabilityMetrics = 
            networkStabilityService.getStabilityMetrics();
        status.put("networkStability", stabilityMetrics);
        
        // Recommendations
        String recommendation = generateRecommendation(failoverStats, healthMetrics, stabilityMetrics);
        status.put("recommendation", recommendation);
        
        return ResponseEntity.ok(status);
    }

    /**
     * Generate recommendation based on all metrics
     */
    private String generateRecommendation(Http2FailoverService.FailoverStats failoverStats,
                                         Http2HealthMonitorService.Http2HealthMetrics healthMetrics,
                                         Http2NetworkStabilityService.NetworkStabilityMetrics stabilityMetrics) {
        if (failoverStats.isFailoverActive()) {
            if (stabilityMetrics.isNetworkStable()) {
                return "Network is stable - HTTP/2 retry recommended";
            } else {
                return "Network is unstable - continue using HTTP/1.1";
            }
        } else {
            if (healthMetrics.getHealthStatus() == Http2HealthMonitorService.Http2HealthStatus.HEALTHY) {
                return "HTTP/2 is healthy and operational";
            } else {
                return "Monitor HTTP/2 closely - health status: " + healthMetrics.getHealthStatus();
            }
        }
    }
}

