package com.posgateway.aml.service;

import com.posgateway.aml.config.security.ProductionRateLimitFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Uses observed HTTP throughput to resize the production general request limit without
 * rebuilding the filter or losing per-client counters.
 */
@Service
@Profile("production")
public class DynamicCapacityService {
    private final MeterRegistry meterRegistry;
    private final ProductionRateLimitFilter rateLimitFilter;
    private long previousRequests;
    private long previousSampleNanos = System.nanoTime();

    @Value("${dynamic-capacity.min-rpm:100}")
    private int minRpm = 100;
    @Value("${dynamic-capacity.max-rpm:10000}")
    private int maxRpm = 10000;

    public DynamicCapacityService(MeterRegistry meterRegistry,
            ProductionRateLimitFilter rateLimitFilter) {
        this.meterRegistry = meterRegistry;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Scheduled(fixedDelayString = "${dynamic-capacity.sample-ms:60000}")
    public synchronized int adjustCapacity() {
        long now = System.nanoTime();
        long requests = meterRegistry.find("http.server.requests").timers().stream()
                .mapToLong(timer -> timer.count()).sum();
        double elapsedSeconds = Math.max(0.001, (now - previousSampleNanos) / 1_000_000_000.0);
        double observedRpm = Math.max(0, requests - previousRequests) * 60.0 / elapsedSeconds;
        previousRequests = requests;
        previousSampleNanos = now;

        int current = rateLimitFilter.getGeneralRequestsPerMinute();
        int next = current;
        if (observedRpm >= current * 0.80) {
            next = Math.min(maxRpm, Math.max(current + 1, (int) Math.ceil(current * 1.25)));
        } else if (observedRpm < current * 0.25) {
            next = Math.max(minRpm, (int) Math.floor(current * 0.90));
        }
        rateLimitFilter.updateGeneralRequestsPerMinute(next);
        return next;
    }

    void setBoundsForTest(int minRpm, int maxRpm) {
        this.minRpm = minRpm;
        this.maxRpm = maxRpm;
    }
}
