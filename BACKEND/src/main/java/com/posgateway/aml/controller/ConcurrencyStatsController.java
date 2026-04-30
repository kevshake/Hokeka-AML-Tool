package com.posgateway.aml.controller;

import com.posgateway.aml.service.RequestRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Concurrency Statistics Controller
 * Provides real-time concurrency and throughput statistics
 */
@RestController
@RequestMapping("/stats")
public class ConcurrencyStatsController {

    private final RequestRateLimiter rateLimiter;

    @Autowired
    public ConcurrencyStatsController(RequestRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * Get concurrency statistics
     * GET /api/v1/stats/concurrency
     * 
     * @return Concurrency statistics
     */
    @GetMapping("/concurrency")
    public ResponseEntity<Map<String, Object>> getConcurrencyStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("currentRequestsPerSecond", rateLimiter.getCurrentRequestsPerSecond());
        stats.put("totalRequests", rateLimiter.getTotalRequests());
        return ResponseEntity.ok(stats);
    }
}

