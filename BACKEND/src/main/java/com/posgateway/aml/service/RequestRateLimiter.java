package com.posgateway.aml.service;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Request Rate Limiter
 * Tracks and limits request rate for backpressure handling
 */
// @RequiredArgsConstructor removed
@Service
public class RequestRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RequestRateLimiter.class);

    private final AtomicInteger requestsPerSecond = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private volatile long lastResetTime = System.currentTimeMillis();

    @Value("${ultra.throughput.max.requests.per.second:50000}")
    private int maxRequestsPerSecond;

    @Value("${ultra.throughput.max.concurrent.requests:30000}")
    private int maxConcurrentRequests;

    /**
     * Check if request can be processed
     * 
     * @return true if can process, false if rate limited
     */
    public boolean canProcess() {
        // Reset counter every second
        long now = System.currentTimeMillis();
        if (now - lastResetTime >= 1000) {
            requestsPerSecond.set(0);
            lastResetTime = now;
        }

        // Check rate limit
        int current = requestsPerSecond.incrementAndGet();
        if (current > maxRequestsPerSecond) {
            logger.warn("Rate limit exceeded: {}/{} requests per second", 
                current, maxRequestsPerSecond);
            return false;
        }

        totalRequests.incrementAndGet();
        return true;
    }

    /**
     * Get current requests per second
     */
    public int getCurrentRequestsPerSecond() {
        return requestsPerSecond.get();
    }

    /**
     * Get total requests processed
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }

    /**
     * Reset counters
     */
    public void reset() {
        requestsPerSecond.set(0);
        totalRequests.set(0);
        lastResetTime = System.currentTimeMillis();
    }
}

