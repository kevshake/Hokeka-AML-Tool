package com.hokeka.aml.controller;

import com.hokeka.aml.service.AerospikeCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Aerospike cache endpoints called by BACKEND for layer-0 cache writes.
 *
 * <p>All endpoints are fire-and-forget from the caller's perspective. The
 * Aerospike store is purely a performance layer — Postgres remains the
 * source of truth. Every PUT has a corresponding GET /read so BACKEND's
 * hot path can retrieve cached profiles with sub-millisecond latency.
 *
 * <p>Protected by InternalAuthFilter (already guards /internal/**).
 */
@RestController
@RequestMapping("/internal/v1/cache")
public class CacheController {

    private static final Logger log = LoggerFactory.getLogger(CacheController.class);

    @Autowired(required = false)
    private AerospikeCacheService cacheService;

    // ──────────────────────────────────────────────────────────────────
    // Risk-profile cache (P3-A)
    // ──────────────────────────────────────────────────────────────────

    @PutMapping("/risk-profile/{customerId}")
    public ResponseEntity<Void> cacheRiskProfile(
            @PathVariable Long customerId,
            @RequestBody Map<String, Object> profile) {
        if (customerId == null) return ResponseEntity.badRequest().build();
        if (cacheService == null) return ResponseEntity.ok().build(); // Aerospike unavailable
        cacheService.putRiskProfile(customerId, profile);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/risk-profile/{customerId}")
    public ResponseEntity<Map<String, Object>> getRiskProfile(
            @PathVariable Long customerId) {
        if (customerId == null) return ResponseEntity.badRequest().build();
        if (cacheService == null) return ResponseEntity.notFound().build();
        Map<String, Object> profile = cacheService.getRiskProfile(customerId);
        return profile != null ? ResponseEntity.ok(profile) : ResponseEntity.notFound().build();
    }

    // ──────────────────────────────────────────────────────────────────
    // Velocity counter cache (P3-B)
    // ──────────────────────────────────────────────────────────────────

    @PutMapping("/velocity/{customerId}")
    public ResponseEntity<Void> cacheVelocity(
            @PathVariable String customerId,
            @RequestBody Map<String, Object> event) {
        if (customerId == null || customerId.isBlank()) return ResponseEntity.badRequest().build();
        if (cacheService == null) return ResponseEntity.ok().build();
        cacheService.recordVelocity(customerId, event);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/velocity/{customerId}")
    public ResponseEntity<Map<String, Object>> getVelocity(
            @PathVariable String customerId) {
        if (customerId == null || customerId.isBlank()) return ResponseEntity.badRequest().build();
        if (cacheService == null) return ResponseEntity.notFound().build();
        Map<String, Object> data = cacheService.getVelocity(customerId);
        return data != null ? ResponseEntity.ok(data) : ResponseEntity.notFound().build();
    }

    // ──────────────────────────────────────────────────────────────────
    // Device fingerprint cache (P3-C)
    // ──────────────────────────────────────────────────────────────────

    @PutMapping("/device/{fingerprint}")
    public ResponseEntity<Void> cacheDevice(
            @PathVariable String fingerprint,
            @RequestBody Map<String, Object> obs) {
        if (fingerprint == null || fingerprint.isBlank()) return ResponseEntity.badRequest().build();
        if (cacheService == null) return ResponseEntity.ok().build();
        cacheService.putDevice(fingerprint, obs);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/device/{fingerprint}")
    public ResponseEntity<Map<String, Object>> getDevice(
            @PathVariable String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return ResponseEntity.badRequest().build();
        if (cacheService == null) return ResponseEntity.notFound().build();
        Map<String, Object> data = cacheService.getDevice(fingerprint);
        return data != null ? ResponseEntity.ok(data) : ResponseEntity.notFound().build();
    }

    // ──────────────────────────────────────────────────────────────────
    // IP reputation cache (P3-C)
    // ──────────────────────────────────────────────────────────────────

    @PutMapping("/ip-reputation/{ip}")
    public ResponseEntity<Void> cacheIpReputation(
            @PathVariable String ip,
            @RequestBody Map<String, Object> obs) {
        if (ip == null || ip.isBlank()) return ResponseEntity.badRequest().build();
        if (cacheService == null) return ResponseEntity.ok().build();
        cacheService.putIpReputation(ip, obs);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/ip-reputation/{ip}")
    public ResponseEntity<Map<String, Object>> getIpReputation(
            @PathVariable String ip) {
        if (ip == null || ip.isBlank()) return ResponseEntity.badRequest().build();
        if (cacheService == null) return ResponseEntity.notFound().build();
        Map<String, Object> data = cacheService.getIpReputation(ip);
        return data != null ? ResponseEntity.ok(data) : ResponseEntity.notFound().build();
    }
}