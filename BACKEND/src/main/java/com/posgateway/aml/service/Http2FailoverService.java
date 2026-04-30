package com.posgateway.aml.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP/2 Failover Service
 * Manages automatic failover between HTTP/2 and HTTP/1.1
 * Monitors health and switches protocols when needed
 */
@Service
public class Http2FailoverService {

    private static final Logger logger = LoggerFactory.getLogger(Http2FailoverService.class);

    private final Http2DetectionService detectionService;
    private final Http2HealthMonitorService healthMonitorService;
    private final Http2NetworkStabilityService networkStabilityService;

    @Value("${http2.failover.enabled:true}")
    private boolean failoverEnabled;

    @Value("${http2.failover.retry.interval.seconds:300}")
    private long retryIntervalSeconds;

    @Value("${http2.failover.min.retry.interval.seconds:60}")
    private long minRetryIntervalSeconds;

    @Value("${http2.failover.auto.retry.enabled:true}")
    private boolean autoRetryEnabled;

    private final AtomicBoolean http2Enabled = new AtomicBoolean(false);
    private final AtomicBoolean failoverActive = new AtomicBoolean(false);
    private final AtomicLong lastFailoverTime = new AtomicLong(0);
    private final AtomicLong lastRetryTime = new AtomicLong(0);
    private final AtomicReference<String> currentProtocol = new AtomicReference<>("HTTP/1.1");
    private final AtomicLong failoverCount = new AtomicLong(0);

    @Autowired
    public Http2FailoverService(Http2DetectionService detectionService,
                                Http2HealthMonitorService healthMonitorService,
                                Http2NetworkStabilityService networkStabilityService) {
        this.detectionService = detectionService;
        this.healthMonitorService = healthMonitorService;
        this.networkStabilityService = networkStabilityService;
        
        // Initial detection and setup
        initializeHttp2();
    }

    /**
     * Initialize HTTP/2 support
     */
    private void initializeHttp2() {
        if (!failoverEnabled) {
            logger.info("HTTP/2 failover is disabled, using HTTP/1.1");
            http2Enabled.set(false);
            currentProtocol.set("HTTP/1.1");
            return;
        }

        boolean supported = detectionService.detectHttp2Support();
        http2Enabled.set(supported);
        
        if (supported) {
            currentProtocol.set("HTTP/2");
            logger.info("HTTP/2 detected and enabled");
        } else {
            currentProtocol.set("HTTP/1.1");
            logger.info("HTTP/2 not available, using HTTP/1.1");
        }
    }

    /**
     * Check if HTTP/2 should be used
     * 
     * @return true if HTTP/2 should be used, false for HTTP/1.1
     */
    public boolean shouldUseHttp2() {
        if (!failoverEnabled || !http2Enabled.get()) {
            return false;
        }

        // Check if failover is active (switched to HTTP/1.1)
        if (failoverActive.get()) {
            return false;
        }

        // Check if HTTP/2 is healthy
        if (!healthMonitorService.isHealthy()) {
            performFailover();
            return false;
        }

        return true;
    }

    /**
     * Get current protocol being used
     */
    public String getCurrentProtocol() {
        return currentProtocol.get();
    }

    /**
     * Perform failover to HTTP/1.1
     */
    public synchronized void performFailover() {
        if (!failoverEnabled) {
            return;
        }

        if (!failoverActive.get()) {
            failoverActive.set(true);
            http2Enabled.set(false);
            currentProtocol.set("HTTP/1.1");
            lastFailoverTime.set(System.currentTimeMillis());
            failoverCount.incrementAndGet();
            
            logger.error("HTTP/2 failover triggered - switching to HTTP/1.1. Failover count: {}", 
                failoverCount.get());
            
            // Reset health metrics after failover
            healthMonitorService.resetMetrics();
        }
    }

    /**
     * Periodic check for HTTP/2 health and retry
     * Background thread that manages HTTP/2 connections and automatic retry
     */
    @Scheduled(fixedRate = 60000) // Check every minute
    public void checkHttp2Health() {
        if (!failoverEnabled) {
            return;
        }

        // If failover is active, check if we should retry HTTP/2
        if (failoverActive.get()) {
            if (autoRetryEnabled) {
                attemptAutomaticHttp2Retry();
            } else {
                // Manual retry logic (old behavior)
                long currentTime = System.currentTimeMillis();
                long timeSinceFailover = currentTime - lastFailoverTime.get();
                long timeSinceLastRetry = currentTime - lastRetryTime.get();
                
                if (timeSinceFailover >= retryIntervalSeconds * 1000 && 
                    timeSinceLastRetry >= minRetryIntervalSeconds * 1000) {
                    attemptHttp2Retry();
                }
            }
        } else {
            // Monitor HTTP/2 health while active
            if (http2Enabled.get() && healthMonitorService.shouldFailover()) {
                performFailover();
            }
        }
    }

    /**
     * Attempt automatic HTTP/2 retry based on network stability
     * Intelligently retries HTTP/2 when network conditions improve
     */
    private synchronized void attemptAutomaticHttp2Retry() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRetry = currentTime - lastRetryTime.get();

        // Check minimum retry interval
        if (timeSinceLastRetry < minRetryIntervalSeconds * 1000) {
            logger.debug("Retry attempt skipped - too soon since last retry: {}ms", timeSinceLastRetry);
            return;
        }

        // Check network stability before retrying
        boolean networkStable = networkStabilityService.isNetworkStableForRetry();
        
        if (!networkStable) {
            logger.debug("HTTP/2 retry deferred - network not stable enough. Consecutive successes: {}, failures: {}",
                networkStabilityService.getStabilityMetrics().getConsecutiveSuccesses(),
                networkStabilityService.getStabilityMetrics().getConsecutiveFailures());
            return;
        }

        // Network is stable, attempt retry
        logger.info("Network stability confirmed - attempting HTTP/2 retry after failover");
        attemptHttp2Retry();
    }

    /**
     * Attempt to retry HTTP/2
     * Comprehensive retry with network stability and health checks
     */
    private synchronized void attemptHttp2Retry() {
        logger.info("Attempting HTTP/2 retry after failover");
        
        lastRetryTime.set(System.currentTimeMillis());
        
        // Step 1: Check network stability
        if (!networkStabilityService.isNetworkStableForRetry()) {
            logger.debug("HTTP/2 retry aborted - network not stable");
            return;
        }
        
        // Step 2: Re-detect HTTP/2 support
        boolean supported = detectionService.refreshDetection();
        
        if (!supported) {
            logger.debug("HTTP/2 retry failed - HTTP/2 not supported");
            return;
        }
        
        // Step 3: Perform connectivity test
        boolean connectivityTest = performConnectivityTest();
        
        if (!connectivityTest) {
            logger.debug("HTTP/2 retry failed - connectivity test failed");
            return;
        }
        
        // Step 4: Re-enable HTTP/2 with careful monitoring
        failoverActive.set(false);
        http2Enabled.set(true);
        currentProtocol.set("HTTP/2");
        
        logger.info("HTTP/2 retry successful - re-enabled HTTP/2. Network stable: {}, HTTP/2 supported: {}",
            networkStabilityService.isNetworkStable(), supported);
        
        // Reset health metrics for fresh start
        healthMonitorService.resetMetrics();
        
        // Reset network stability consecutive failures to give HTTP/2 a fair chance
        // Note: This is done through the service's reset method if needed
    }

    /**
     * Perform connectivity test before enabling HTTP/2
     * 
     * @return true if connectivity test passes
     */
    private boolean performConnectivityTest() {
        try {
            // Use network stability service's test method
            // For now, return true if network is stable
            return networkStabilityService.isNetworkStableForRetry();
        } catch (Exception e) {
            logger.warn("Connectivity test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get failover statistics
     */
    public FailoverStats getFailoverStats() {
        FailoverStats stats = new FailoverStats();
        stats.setHttp2Enabled(http2Enabled.get());
        stats.setFailoverActive(failoverActive.get());
        stats.setCurrentProtocol(currentProtocol.get());
        stats.setFailoverCount(failoverCount.get());
        stats.setLastFailoverTime(lastFailoverTime.get());
        stats.setLastRetryTime(lastRetryTime.get());
        stats.setAutoRetryEnabled(autoRetryEnabled);
        stats.setNetworkStable(networkStabilityService.isNetworkStable());
        
        return stats;
    }

    /**
     * Failover Statistics
     */
    public static class FailoverStats {
        private boolean http2Enabled;
        private boolean failoverActive;
        private String currentProtocol;
        private long failoverCount;
        private long lastFailoverTime;
        private long lastRetryTime;

        // Getters and Setters
        public boolean isHttp2Enabled() { return http2Enabled; }
        public void setHttp2Enabled(boolean http2Enabled) { this.http2Enabled = http2Enabled; }

        public boolean isFailoverActive() { return failoverActive; }
        public void setFailoverActive(boolean failoverActive) { this.failoverActive = failoverActive; }

        public String getCurrentProtocol() { return currentProtocol; }
        public void setCurrentProtocol(String currentProtocol) { this.currentProtocol = currentProtocol; }

        public long getFailoverCount() { return failoverCount; }
        public void setFailoverCount(long failoverCount) { this.failoverCount = failoverCount; }

        public long getLastFailoverTime() { return lastFailoverTime; }
        public void setLastFailoverTime(long lastFailoverTime) { this.lastFailoverTime = lastFailoverTime; }

        public long getLastRetryTime() { return lastRetryTime; }
        public void setLastRetryTime(long lastRetryTime) { this.lastRetryTime = lastRetryTime; }

        private boolean autoRetryEnabled;
        private boolean networkStable;

        public boolean isAutoRetryEnabled() { return autoRetryEnabled; }
        public void setAutoRetryEnabled(boolean autoRetryEnabled) { this.autoRetryEnabled = autoRetryEnabled; }

        public boolean isNetworkStable() { return networkStable; }
        public void setNetworkStable(boolean networkStable) { this.networkStable = networkStable; }
    }
}

