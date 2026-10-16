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
 * Entries are tenant-scoped by {@code pspId}; null pspId writes are platform-wide.
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

    /**
     * True if blacklisted for this PSP or on the platform-wide list.
     */
    public boolean isBlacklisted(Long pspId, String type, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (featureCacheService.isBlacklisted(pspId, type, value)
                || featureCacheService.isBlacklisted(null, type, value)) {
            return true;
        }
        return blacklistRepository.existsActiveNonExpiredForTenantOrPlatform(
                pspId, type, value, LocalDateTime.now());
    }

    /** @deprecated Prefer {@link #isBlacklisted(Long, String, String)} with a PSP id. */
    @Deprecated
    public boolean isBlacklisted(String type, String value) {
        return isBlacklisted(null, type, value);
    }

    /** Adds a permanent blacklist entry in the requested tenant scope. */
    @Transactional
    public void addToBlacklist(Long pspId, String type, String value, String reason, Long createdBy) {
        addToBlacklist(pspId, type, value, reason, createdBy, null);
    }

    /** Adds a permanent platform-wide blacklist entry for legacy callers. */
    @Transactional
    public void addToBlacklist(String type, String value, String reason, Long createdBy) {
        addToBlacklist(null, type, value, reason, createdBy, null);
    }

    /** Adds a platform-wide blacklist entry with an optional expiry for legacy callers. */
    @Transactional
    public void addToBlacklist(String type, String value, String reason, Long createdBy, LocalDateTime expiresAt) {
        addToBlacklist(null, type, value, reason, createdBy, expiresAt);
    }

    /**
     * Add or refresh a blacklist entry with an optional expiry, scoped to {@code pspId}.
     * Null {@code pspId} writes a platform-wide entry.
     */
    @Transactional
    public void addToBlacklist(Long pspId, String type, String value, String reason, Long createdBy,
                               LocalDateTime expiresAt) {
        if (type == null || type.isBlank() || value == null || value.isBlank()) {
            return;
        }
        boolean platformWide = pspId == null;
        try {
            Optional<PaymentBlacklistEntry> existing = platformWide
                    ? blacklistRepository.findPlatformActive(type, value)
                    : blacklistRepository.findTenantActive(pspId, type, value);
            if (existing.isPresent()) {
                PaymentBlacklistEntry entry = existing.get();
                LocalDateTime current = entry.getExpiresAt();
                if (expiresAt == null) {
                    entry.setExpiresAt(null);
                } else if (current != null && expiresAt.isAfter(current)) {
                    entry.setExpiresAt(expiresAt);
                }
                blacklistRepository.save(entry);
            } else {
                PaymentBlacklistEntry entry = new PaymentBlacklistEntry();
                entry.setPspId(pspId);
                entry.setPlatformWide(platformWide);
                entry.setEntryType(type);
                entry.setEntryValue(value);
                entry.setReason(reason);
                entry.setActive(true);
                entry.setCreatedBy(createdBy);
                entry.setExpiresAt(expiresAt);
                blacklistRepository.save(entry);
                log.info("Blacklisted {} for psp={} (reason: {}, expiresAt: {})",
                        type, pspId == null ? "platform" : pspId, reason, expiresAt);
            }
            if (expiresAt == null) {
                featureCacheService.addToBlacklist(pspId, type, value);
            }
        } catch (Exception e) {
            log.warn("Failed to persist blacklist entry {}={} psp={}: {}",
                    type, value, pspId, e.getMessage());
        }
    }
}
