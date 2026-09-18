package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.security.PaymentBlacklistEntry;
import com.posgateway.aml.repository.security.PaymentBlacklistRepository;
import com.posgateway.aml.service.cache.FeatureCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Checks and maintains the PAN/terminal/IP blacklist (Redis cache with Postgres fallback).
 */
@Service
public class PaymentBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(PaymentBlacklistService.class);

    private final FeatureCacheService featureCacheService;
    private final PaymentBlacklistRepository blacklistRepository;

    public PaymentBlacklistService(FeatureCacheService featureCacheService,
                                   PaymentBlacklistRepository blacklistRepository) {
        this.featureCacheService = featureCacheService;
        this.blacklistRepository = blacklistRepository;
    }

    public boolean isBlacklisted(String type, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        // The cache only ever holds permanent blocks (see addToBlacklist), so a cache hit is
        // authoritative. Time-boxed blocks (e.g. Do-Not-Honour) are resolved by the indexed
        // expiry-aware DB query so they correctly stop matching once expired.
        if (featureCacheService.isBlacklisted(type, value)) {
            return true;
        }
        return blacklistRepository.existsActiveNonExpired(type, value, LocalDateTime.now());
    }

    /**
     * Add an identifier to the blacklist (idempotent). Persists an active entry to
     * Postgres if one does not already exist and mirrors it into the Redis cache so
     * subsequent {@link #isBlacklisted} checks short-circuit. Failures are swallowed
     * so a blocklist write can never break the decision path that triggered it.
     *
     * @param type      identifier type, e.g. {@code "PAN"}, {@code "TERMINAL"}, {@code "IP"}
     * @param value     the identifier value (blank values are ignored)
     * @param reason    why it was blacklisted (audit)
     * @param createdBy user id responsible, or null for a system action
     */
    @Transactional
    public void addToBlacklist(String type, String value, String reason, Long createdBy) {
        addToBlacklist(type, value, reason, createdBy, null);
    }

    /**
     * Add or refresh a blacklist entry with an optional expiry.
     *
     * <p>{@code expiresAt == null} is a permanent block (mirrored into the fast cache). A non-null
     * {@code expiresAt} is a time-boxed block (e.g. the 30-day Do-Not-Honour card decline): it is
     * NOT mirrored into the permanent cache — the expiry-aware DB query in {@link #isBlacklisted}
     * stops matching it once it lapses. If an active entry already exists its expiry is extended
     * (never shortened), so a repeat Do-Not-Honour resets the full window and a permanent block is
     * never downgraded to a temporary one.
     */
    @Transactional
    public void addToBlacklist(String type, String value, String reason, Long createdBy, LocalDateTime expiresAt) {
        if (type == null || type.isBlank() || value == null || value.isBlank()) {
            return;
        }
        try {
            Optional<PaymentBlacklistEntry> existing =
                    blacklistRepository.findByEntryTypeAndEntryValueAndActiveTrue(type, value);
            if (existing.isPresent()) {
                PaymentBlacklistEntry entry = existing.get();
                LocalDateTime current = entry.getExpiresAt();
                // Extend only: null (permanent) wins; otherwise keep the later expiry.
                if (expiresAt == null) {
                    entry.setExpiresAt(null);
                } else if (current != null && expiresAt.isAfter(current)) {
                    entry.setExpiresAt(expiresAt);
                }
                blacklistRepository.save(entry);
            } else {
                PaymentBlacklistEntry entry = new PaymentBlacklistEntry();
                entry.setEntryType(type);
                entry.setEntryValue(value);
                entry.setReason(reason);
                entry.setActive(true);
                entry.setCreatedBy(createdBy);
                entry.setExpiresAt(expiresAt);
                blacklistRepository.save(entry);
                log.info("Blacklisted {} (reason: {}, expiresAt: {})", type, reason, expiresAt);
            }
            // Only permanent blocks go in the permanent cache; time-boxed ones rely on the DB
            // query so they expire correctly.
            if (expiresAt == null) {
                featureCacheService.addToBlacklist(type, value);
            }
        } catch (Exception e) {
            // Unique-constraint race or cache hiccup — the check path still works via DB.
            log.warn("Failed to persist blacklist entry {}={}: {}", type, value, e.getMessage());
        }
    }
}
