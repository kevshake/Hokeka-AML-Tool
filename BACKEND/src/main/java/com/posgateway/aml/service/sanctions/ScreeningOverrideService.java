package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.entity.sanctions.ScreeningOverride;
import com.posgateway.aml.repository.sanctions.ScreeningOverrideRepository;
import com.posgateway.aml.service.cache.ScreeningCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Screening Override Service
 * Manages screening overrides with approval workflow
 */
@Service
public class ScreeningOverrideService {

    private static final Logger logger = LoggerFactory.getLogger(ScreeningOverrideService.class);

    private final ScreeningOverrideRepository overrideRepository;
    private final ScreeningCacheService screeningCacheService; // Aerospike cache

    @Autowired
    public ScreeningOverrideService(ScreeningOverrideRepository overrideRepository,
                                     ScreeningCacheService screeningCacheService) {
        this.overrideRepository = overrideRepository;
        this.screeningCacheService = screeningCacheService;
    }

    /**
     * Check if entity has an active override
     * Uses Aerospike cache for fast lookups
     */
    public boolean hasActiveOverride(String entityId, String entityType) {
        // Fast Aerospike cache lookup first (for Long entityId)
        try {
            Long entityIdLong = Long.parseLong(entityId);
            Boolean cached = screeningCacheService.hasOverride(entityIdLong, entityType);
            if (cached != null && cached) {
                return true;
            }
        } catch (NumberFormatException e) {
            // Continue with database lookup
        }
        
        // Fallback to database lookup
        Optional<ScreeningOverride> override = overrideRepository.findByEntityIdAndEntityTypeAndStatus(
                entityId, entityType, "APPROVED");
        
        if (override.isPresent()) {
            ScreeningOverride entry = override.get();
            // Check if override has expired
            if (entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(LocalDateTime.now())) {
                logger.debug("Override for {} expired, marking as expired", entityId);
                entry.setStatus("EXPIRED");
                overrideRepository.save(entry);
                return false;
            }
            
            // Cache in Aerospike for future fast lookups
            try {
                Long entityIdLong = Long.parseLong(entityId);
                screeningCacheService.cacheOverride(entityIdLong, entityType, true);
            } catch (NumberFormatException e) {
                // Skip caching if entityId is not a number
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if entity is overridden (alias for hasActiveOverride)
     */
    public boolean isOverridden(Long entityId, String entityType) {
        // Fast Aerospike cache lookup first
        Boolean cached = screeningCacheService.hasOverride(entityId, entityType);
        if (cached != null) {
            return cached;
        }
        
        // Fallback to database lookup
        return hasActiveOverride(String.valueOf(entityId), entityType);
    }

    /**
     * Create override request
     */
    @Transactional
    public ScreeningOverride createOverride(String entityId, String entityType,
                                           String overrideReason, String justification,
                                           Long createdBy, LocalDateTime expiresAt) {
        ScreeningOverride override = new ScreeningOverride();
        override.setEntityId(entityId);
        override.setEntityType(entityType);
        override.setOverrideReason(overrideReason);
        override.setJustification(justification);
        override.setCreatedBy(createdBy);
        override.setExpiresAt(expiresAt);
        override.setStatus("PENDING");
        override.setCreatedAt(LocalDateTime.now());
        override.setUpdatedAt(LocalDateTime.now());

        logger.info("Created override request for {} {} by user {}", entityType, entityId, createdBy);
        ScreeningOverride saved = overrideRepository.save(override);
        
        // Note: Only cache when approved, not when pending
        return saved;
    }

    /**
     * Approve override
     */
    @Transactional
    public ScreeningOverride approveOverride(Long overrideId, Long approvedBy) {
        ScreeningOverride override = overrideRepository.findById(overrideId)
                .orElseThrow(() -> new RuntimeException("Override not found: " + overrideId));

        if (!"PENDING".equals(override.getStatus())) {
            throw new IllegalStateException("Override is not in PENDING status");
        }

        override.setStatus("APPROVED");
        override.setApprovedBy(approvedBy);
        override.setApprovedAt(LocalDateTime.now());
        override.setUpdatedAt(LocalDateTime.now());

        logger.info("Override {} approved by user {}", overrideId, approvedBy);
        ScreeningOverride saved = overrideRepository.save(override);
        
        // Cache in Aerospike for fast lookups
        try {
            Long entityIdLong = Long.parseLong(override.getEntityId());
            screeningCacheService.cacheOverride(entityIdLong, override.getEntityType(), true);
        } catch (NumberFormatException e) {
            // Skip caching if entityId is not a number
        }
        
        return saved;
    }

    /**
     * Reject override
     */
    @Transactional
    public ScreeningOverride rejectOverride(Long overrideId, Long rejectedBy) {
        ScreeningOverride override = overrideRepository.findById(overrideId)
                .orElseThrow(() -> new RuntimeException("Override not found: " + overrideId));

        override.setStatus("REJECTED");
        override.setApprovedBy(rejectedBy);
        override.setUpdatedAt(LocalDateTime.now());

        logger.info("Override {} rejected by user {}", overrideId, rejectedBy);
        return overrideRepository.save(override);
    }

    /**
     * Get pending overrides
     */
    public List<ScreeningOverride> getPendingOverrides() {
        return overrideRepository.findByStatus("PENDING");
    }

    /**
     * Get overrides by entity
     */
    public List<ScreeningOverride> getOverridesByEntity(String entityId, String entityType) {
        return overrideRepository.findByEntityIdAndEntityType(entityId, entityType);
    }
}

