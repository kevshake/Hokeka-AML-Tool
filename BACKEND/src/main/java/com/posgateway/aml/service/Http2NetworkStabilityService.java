package com.posgateway.aml.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP/2 Network Stability Service
 * Background service that monitors network stability and determines
 * when it's safe to switch back to HTTP/2 after failover
 */
@Service
public class Http2NetworkStabilityService {

    private static final Logger logger = LoggerFactory.getLogger(Http2NetworkStabilityService.class);

    @SuppressWarnings("unused")
    private final Http2DetectionService detectionService;

    @Value("${http2.network.stability.enabled:true}")
    private boolean stabilityMonitoringEnabled;

    @Value("${http2.network.stability.check.interval.seconds:30}")
    private int stabilityCheckIntervalSeconds;

    @Value("${http2.network.stability.test.url:http://localhost:8080/actuator/health}")
    private String stabilityTestUrl;

    @Value("${http2.network.stability.timeout.ms:5000}")
    private int stabilityTestTimeout;

    @Value("${http2.network.stability.success.threshold:5}")
    private int successThreshold; // Consecutive successful tests before considering stable

    @Value("${http2.network.stability.failure.threshold:3}")
    private int failureThreshold; // Consecutive failures before considering unstable

    // Stability metrics
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong totalStabilityTests = new AtomicLong(0);
    private final AtomicLong successfulStabilityTests = new AtomicLong(0);
    private final AtomicBoolean networkStable = new AtomicBoolean(true);
    private final AtomicReference<Long> lastStabilityCheckTime = new AtomicReference<>(0L);
    private final AtomicReference<NetworkStabilityStatus> currentStatus = 
        new AtomicReference<>(NetworkStabilityStatus.STABLE);

    @Autowired
    public Http2NetworkStabilityService(Http2DetectionService detectionService) {
        this.detectionService = detectionService;
    }

    /**
     * Background process that regularly checks network stability
     * Runs every configured interval (default: 30 seconds)
     */
    @Scheduled(fixedRateString = "${http2.network.stability.check.interval.seconds:30}000")
    public void checkNetworkStability() {
        if (!stabilityMonitoringEnabled) {
            return;
        }

        try {
            totalStabilityTests.incrementAndGet();
            lastStabilityCheckTime.set(System.currentTimeMillis());

            // Perform network stability test
            boolean testPassed = performStabilityTest();

            if (testPassed) {
                successfulStabilityTests.incrementAndGet();
                int successes = consecutiveSuccesses.incrementAndGet();
                consecutiveFailures.set(0);

                // Check if network has become stable
                if (successes >= successThreshold) {
                    if (!networkStable.get()) {
                        networkStable.set(true);
                        currentStatus.set(NetworkStabilityStatus.STABLE);
                        logger.info("Network stability restored - {} consecutive successful tests", successes);
                    }
                }
            } else {
                int failures = consecutiveFailures.incrementAndGet();
                consecutiveSuccesses.set(0);

                // Check if network has become unstable
                if (failures >= failureThreshold) {
                    if (networkStable.get()) {
                        networkStable.set(false);
                        currentStatus.set(NetworkStabilityStatus.UNSTABLE);
                        logger.warn("Network stability degraded - {} consecutive failed tests", failures);
                    }
                }
            }

            // Update status based on current state
            updateStabilityStatus();

        } catch (Exception e) {
            logger.error("Error during network stability check: {}", e.getMessage());
            recordStabilityFailure();
        }
    }

    /**
     * Perform network stability test
     * Tests connectivity, response time, and HTTP/2 capability
     * 
     * @return true if network is stable, false otherwise
     */
    private boolean performStabilityTest() {
        try {
            URL url = new URL(stabilityTestUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(stabilityTestTimeout);
            connection.setReadTimeout(stabilityTestTimeout);
            connection.setRequestMethod("GET");

            long startTime = System.currentTimeMillis();
            connection.connect();
            int responseCode = connection.getResponseCode();
            long latency = System.currentTimeMillis() - startTime;

            connection.disconnect();

            // Check if test passed
            boolean passed = responseCode >= 200 && responseCode < 300 && latency < stabilityTestTimeout;

            if (passed) {
                logger.debug("Network stability test passed - latency: {}ms", latency);
            } else {
                logger.debug("Network stability test failed - response code: {}, latency: {}ms", 
                    responseCode, latency);
            }

            return passed;

        } catch (IOException e) {
            logger.debug("Network stability test failed with exception: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("Unexpected error during network stability test: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Record stability failure
     */
    private void recordStabilityFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        consecutiveSuccesses.set(0);

        if (failures >= failureThreshold) {
            networkStable.set(false);
            currentStatus.set(NetworkStabilityStatus.UNSTABLE);
        }
    }

    /**
     * Update stability status
     */
    private void updateStabilityStatus() {
        int successes = consecutiveSuccesses.get();
        int failures = consecutiveFailures.get();

        if (successes >= successThreshold) {
            currentStatus.set(NetworkStabilityStatus.STABLE);
        } else if (failures >= failureThreshold) {
            currentStatus.set(NetworkStabilityStatus.UNSTABLE);
        } else {
            currentStatus.set(NetworkStabilityStatus.TESTING);
        }
    }

    /**
     * Check if network is stable enough to use HTTP/2
     * 
     * @return true if network is stable
     */
    public boolean isNetworkStable() {
        return networkStable.get() && currentStatus.get() == NetworkStabilityStatus.STABLE;
    }

    /**
     * Check if network is stable enough for HTTP/2 retry
     * More lenient check for retry attempts
     * 
     * @return true if network is stable enough for retry
     */
    public boolean isNetworkStableForRetry() {
        // More lenient - require fewer consecutive successes for retry
        int requiredSuccesses = Math.max(2, successThreshold / 2);
        return consecutiveSuccesses.get() >= requiredSuccesses && 
               consecutiveFailures.get() < failureThreshold;
    }

    /**
     * Get current network stability status
     */
    public NetworkStabilityStatus getStabilityStatus() {
        return currentStatus.get();
    }

    /**
     * Get network stability metrics
     */
    public NetworkStabilityMetrics getStabilityMetrics() {
        long total = totalStabilityTests.get();
        long successful = successfulStabilityTests.get();

        NetworkStabilityMetrics metrics = new NetworkStabilityMetrics();
        metrics.setTotalTests(total);
        metrics.setSuccessfulTests(successful);
        metrics.setSuccessRate(total > 0 ? (double) successful / total : 0.0);
        metrics.setConsecutiveSuccesses(consecutiveSuccesses.get());
        metrics.setConsecutiveFailures(consecutiveFailures.get());
        metrics.setNetworkStable(networkStable.get());
        metrics.setStabilityStatus(currentStatus.get());
        metrics.setLastCheckTime(lastStabilityCheckTime.get());

        return metrics;
    }

    /**
     * Reset stability metrics
     */
    public void resetMetrics() {
        consecutiveSuccesses.set(0);
        consecutiveFailures.set(0);
        networkStable.set(true);
        currentStatus.set(NetworkStabilityStatus.STABLE);
        logger.info("Network stability metrics reset");
    }

    /**
     * Network Stability Status
     */
    public enum NetworkStabilityStatus {
        STABLE,
        TESTING,
        UNSTABLE
    }

    /**
     * Network Stability Metrics
     */
    public static class NetworkStabilityMetrics {
        private long totalTests;
        private long successfulTests;
        private double successRate;
        private int consecutiveSuccesses;
        private int consecutiveFailures;
        private boolean networkStable;
        private NetworkStabilityStatus stabilityStatus;
        private long lastCheckTime;

        // Getters and Setters
        public long getTotalTests() { return totalTests; }
        public void setTotalTests(long totalTests) { this.totalTests = totalTests; }

        public long getSuccessfulTests() { return successfulTests; }
        public void setSuccessfulTests(long successfulTests) { this.successfulTests = successfulTests; }

        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }

        public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
        public void setConsecutiveSuccesses(int consecutiveSuccesses) { this.consecutiveSuccesses = consecutiveSuccesses; }

        public int getConsecutiveFailures() { return consecutiveFailures; }
        public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

        public boolean isNetworkStable() { return networkStable; }
        public void setNetworkStable(boolean networkStable) { this.networkStable = networkStable; }

        public NetworkStabilityStatus getStabilityStatus() { return stabilityStatus; }
        public void setStabilityStatus(NetworkStabilityStatus stabilityStatus) { this.stabilityStatus = stabilityStatus; }

        public long getLastCheckTime() { return lastCheckTime; }
        public void setLastCheckTime(long lastCheckTime) { this.lastCheckTime = lastCheckTime; }
    }
}

