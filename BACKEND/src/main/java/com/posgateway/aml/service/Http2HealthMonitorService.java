package com.posgateway.aml.service;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP/2 Health Monitor Service
 * Monitors HTTP/2 connection health and detects problems
 * Tracks connection drops, errors, and performance metrics
 */
// @RequiredArgsConstructor removed
@Service
public class Http2HealthMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(Http2HealthMonitorService.class);

    @Value("${http2.health.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${http2.health.check.interval.seconds:30}")
    private int healthCheckIntervalSeconds;

    @Value("${http2.failover.drop.threshold:10}")
    private int dropThreshold; // Number of drops before failover

    @Value("${http2.failover.error.rate.threshold:0.05}")
    private double errorRateThreshold; // 5% error rate triggers failover

    @Value("${http2.failover.latency.threshold.ms:1000}")
    private long latencyThresholdMs;

    // Metrics
    private final AtomicLong totalHttp2Requests = new AtomicLong(0);
    private final AtomicLong totalHttp2Errors = new AtomicLong(0);
    private final AtomicLong totalHttp2Drops = new AtomicLong(0);
    private final AtomicLong totalHttp2Latency = new AtomicLong(0);
    private final AtomicInteger consecutiveDrops = new AtomicInteger(0);
    
    private final AtomicReference<Http2HealthStatus> currentStatus = 
        new AtomicReference<>(Http2HealthStatus.HEALTHY);
    
    private final AtomicLong lastHealthCheckTime = new AtomicLong(0);

    /**
     * Record HTTP/2 request
     */
    public void recordRequest(long latencyMs) {
        if (!monitoringEnabled) {
            return;
        }

        totalHttp2Requests.incrementAndGet();
        totalHttp2Latency.addAndGet(latencyMs);
        
        // Reset consecutive drops on successful request
        if (consecutiveDrops.get() > 0) {
            consecutiveDrops.set(0);
        }
        
        updateHealthStatus();
    }

    /**
     * Record HTTP/2 error
     */
    public void recordError(String errorType) {
        if (!monitoringEnabled) {
            return;
        }

        totalHttp2Errors.incrementAndGet();
        logger.warn("HTTP/2 error recorded: type={}, total_errors={}", 
            errorType, totalHttp2Errors.get());
        
        updateHealthStatus();
    }

    /**
     * Record HTTP/2 connection drop
     */
    public void recordDrop() {
        if (!monitoringEnabled) {
            return;
        }

        totalHttp2Drops.incrementAndGet();
        int drops = consecutiveDrops.incrementAndGet();
        
        logger.warn("HTTP/2 connection drop recorded: consecutive_drops={}, total_drops={}", 
            drops, totalHttp2Drops.get());
        
        updateHealthStatus();
    }

    /**
     * Update health status based on metrics
     */
    private void updateHealthStatus() {
        long requests = totalHttp2Requests.get();
        long errors = totalHttp2Errors.get();
        int consecutiveDropsCount = consecutiveDrops.get();
        
        // Check error rate
        double errorRate = requests > 0 ? (double) errors / requests : 0.0;
        
        // Check average latency
        long avgLatency = requests > 0 ? totalHttp2Latency.get() / requests : 0;
        
        // Determine health status
        Http2HealthStatus status = Http2HealthStatus.HEALTHY;
        
        if (consecutiveDropsCount >= dropThreshold) {
            status = Http2HealthStatus.CRITICAL;
            logger.error("HTTP/2 health status: CRITICAL - consecutive drops: {}", consecutiveDropsCount);
        } else if (errorRate >= errorRateThreshold) {
            status = Http2HealthStatus.DEGRADED;
            logger.warn("HTTP/2 health status: DEGRADED - error rate: {:.2f}%", errorRate * 100);
        } else if (avgLatency > latencyThresholdMs) {
            status = Http2HealthStatus.DEGRADED;
            logger.warn("HTTP/2 health status: DEGRADED - avg latency: {}ms", avgLatency);
        }
        
        currentStatus.set(status);
    }

    /**
     * Periodic health check
     */
    @Scheduled(fixedRateString = "${http2.health.check.interval.seconds:30}000")
    public void periodicHealthCheck() {
        if (!monitoringEnabled) {
            return;
        }

        updateHealthStatus();
        lastHealthCheckTime.set(System.currentTimeMillis());
        
        Http2HealthStatus status = currentStatus.get();
        if (status != Http2HealthStatus.HEALTHY) {
            logger.warn("HTTP/2 health check: status={}, requests={}, errors={}, drops={}, error_rate={:.2f}%",
                status,
                totalHttp2Requests.get(),
                totalHttp2Errors.get(),
                totalHttp2Drops.get(),
                totalHttp2Requests.get() > 0 ? 
                    (double) totalHttp2Errors.get() / totalHttp2Requests.get() * 100 : 0.0);
        }
    }

    /**
     * Get current health status
     */
    public Http2HealthStatus getHealthStatus() {
        return currentStatus.get();
    }

    /**
     * Check if HTTP/2 is healthy enough to use
     */
    public boolean isHealthy() {
        return currentStatus.get() == Http2HealthStatus.HEALTHY;
    }

    /**
     * Check if failover to HTTP/1.1 is needed
     */
    public boolean shouldFailover() {
        Http2HealthStatus status = currentStatus.get();
        return status == Http2HealthStatus.CRITICAL || 
               (status == Http2HealthStatus.DEGRADED && consecutiveDrops.get() >= dropThreshold / 2);
    }

    /**
     * Get health metrics
     */
    public Http2HealthMetrics getHealthMetrics() {
        long requests = totalHttp2Requests.get();
        long errors = totalHttp2Errors.get();
        long drops = totalHttp2Drops.get();
        
        Http2HealthMetrics metrics = new Http2HealthMetrics();
        metrics.setTotalRequests(requests);
        metrics.setTotalErrors(errors);
        metrics.setTotalDrops(drops);
        metrics.setErrorRate(requests > 0 ? (double) errors / requests : 0.0);
        metrics.setAverageLatency(requests > 0 ? totalHttp2Latency.get() / requests : 0);
        metrics.setConsecutiveDrops(consecutiveDrops.get());
        metrics.setHealthStatus(currentStatus.get());
        metrics.setLastHealthCheckTime(lastHealthCheckTime.get());
        
        return metrics;
    }

    /**
     * Reset metrics (for testing or after failover)
     */
    public void resetMetrics() {
        totalHttp2Requests.set(0);
        totalHttp2Errors.set(0);
        totalHttp2Drops.set(0);
        totalHttp2Latency.set(0);
        consecutiveDrops.set(0);
        currentStatus.set(Http2HealthStatus.HEALTHY);
        logger.info("HTTP/2 health metrics reset");
    }

    /**
     * HTTP/2 Health Status
     */
    public enum Http2HealthStatus {
        HEALTHY,
        DEGRADED,
        CRITICAL
    }

    /**
     * HTTP/2 Health Metrics
     */
    public static class Http2HealthMetrics {
        private long totalRequests;
        private long totalErrors;
        private long totalDrops;
        private double errorRate;
        private long averageLatency;
        private int consecutiveDrops;
        private Http2HealthStatus healthStatus;
        private long lastHealthCheckTime;

        // Getters and Setters
        public long getTotalRequests() { return totalRequests; }
        public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }

        public long getTotalErrors() { return totalErrors; }
        public void setTotalErrors(long totalErrors) { this.totalErrors = totalErrors; }

        public long getTotalDrops() { return totalDrops; }
        public void setTotalDrops(long totalDrops) { this.totalDrops = totalDrops; }

        public double getErrorRate() { return errorRate; }
        public void setErrorRate(double errorRate) { this.errorRate = errorRate; }

        public long getAverageLatency() { return averageLatency; }
        public void setAverageLatency(long averageLatency) { this.averageLatency = averageLatency; }

        public int getConsecutiveDrops() { return consecutiveDrops; }
        public void setConsecutiveDrops(int consecutiveDrops) { this.consecutiveDrops = consecutiveDrops; }

        public Http2HealthStatus getHealthStatus() { return healthStatus; }
        public void setHealthStatus(Http2HealthStatus healthStatus) { this.healthStatus = healthStatus; }

        public long getLastHealthCheckTime() { return lastHealthCheckTime; }
        public void setLastHealthCheckTime(long lastHealthCheckTime) { this.lastHealthCheckTime = lastHealthCheckTime; }
    }
}

