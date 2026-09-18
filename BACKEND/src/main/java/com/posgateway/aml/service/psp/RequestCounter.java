package com.posgateway.aml.service.psp;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counter for rate limiting requests.
 */
public class RequestCounter {
    private final AtomicInteger count = new AtomicInteger(0);
    private volatile long windowStart = Instant.now().getEpochSecond() / 60; // Minute window

    public synchronized boolean tryAcquire(int maxRequests) {
        long currentWindow = Instant.now().getEpochSecond() / 60;
        if (currentWindow > windowStart) {
            windowStart = currentWindow;
            count.set(0);
        }

        return count.incrementAndGet() <= maxRequests;
    }
}
