package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.entity.sanctions.ScreeningWhitelist;
import com.posgateway.aml.repository.sanctions.ScreeningWhitelistRepository;
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
 * Screening Whitelist Service
 * Manages whitelist entries for known false positives
 */
@Service
public class ScreeningWhitelistService {

    private static final Logger logger = LoggerFactory.getLogger(ScreeningWhitelistService.class);

    private final ScreeningWhitelistRepository whitelistRepository;
    private final ScreeningCacheService screeningCacheService; // Aerospike cache

    @Autowired
    public ScreeningWhitelistService(ScreeningWhitelistRepository whitelistRepository,
                                     ScreeningCacheService screeningCacheService) {
        this.whitelistRepository = whitelistRepository;
        this.screeningCacheService = screeningCacheService;
    }

    /**
     * Check if entity is whitelisted
     * Uses Aerospike cache for fast lookups
     */
    public boolean isWhitelisted(String entityId, String entityType) {
        // Fast Aerospike cache lookup first (for Long entityId)
        try {
            Long entityIdLong = Long.parseLong(entityId);
            if (screeningCacheService.isWhitelisted(entityIdLong, entityType)) {
                return true;
            }
        } catch (NumberFormatException e) {
            // Continue with database lookup
        }
        
        // Fallback to database lookup
        Optional<ScreeningWhitelist> whitelist = whitelistRepository.findByEntityIdAndEntityTypeAndActive(
                entityId, entityType, true);
        
        if (whitelist.isPresent()) {
            ScreeningWhitelist entry = whitelist.get();
            // Check if whitelist entry has expired
            if (entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(LocalDateTime.now())) {
                logger.debug("Whitelist entry for {} expired, removing", entityId);
                entry.setActive(false);
                whitelistRepository.save(entry);
                return false;
            }
            
            // Cache in Aerospike for future fast lookups
            try {
                Long entityIdLong = Long.parseLong(entityId);
                screeningCacheService.cacheWhitelistEntry(entityIdLong, entityId, entityType);
            } catch (NumberFormatException e) {
                // Skip caching if entityId is not a number
            }
            
            return true;
        }
        
        return false;
    }

    /**
     * Add entity to whitelist
     */
    @Transactional
    public ScreeningWhitelist addToWhitelist(String entityId, String entityType, 
                                            String reason, Long createdBy, 
                                            LocalDateTime expiresAt) {
        // Check if already whitelisted
        Optional<ScreeningWhitelist> existing = whitelistRepository.findByEntityIdAndEntityType(
                entityId, entityType);
        
        if (existing.isPresent()) {
            ScreeningWhitelist entry = existing.get();
            entry.setActive(true);
            entry.setReason(reason);
            entry.setExpiresAt(expiresAt);
            entry.setUpdatedAt(LocalDateTime.now());
            return whitelistRepository.save(entry);
        }

        ScreeningWhitelist whitelist = new ScreeningWhitelist();
        whitelist.setEntityId(entityId);
        whitelist.setEntityType(entityType);
        whitelist.setReason(reason);
        whitelist.setCreatedBy(createdBy);
        whitelist.setExpiresAt(expiresAt);
        whitelist.setActive(true);
        whitelist.setCreatedAt(LocalDateTime.now());
        whitelist.setUpdatedAt(LocalDateTime.now());

        logger.info("Added {} {} to whitelist: {}", entityType, entityId, reason);
        ScreeningWhitelist saved = whitelistRepository.save(whitelist);
        
        // Cache in Aerospike for fast lookups
        try {
            Long entityIdLong = Long.parseLong(entityId);
            screeningCacheService.cacheWhitelistEntry(entityIdLong, entityId, entityType);
        } catch (NumberFormatException e) {
            // Skip caching if entityId is not a number
        }
        
        return saved;
    }

    /**
     * Remove entity from whitelist
     */
    @Transactional
    public void removeFromWhitelist(String entityId, String entityType) {
        Optional<ScreeningWhitelist> whitelist = whitelistRepository.findByEntityIdAndEntityType(
                entityId, entityType);
        
        if (whitelist.isPresent()) {
            ScreeningWhitelist entry = whitelist.get();
            entry.setActive(false);
            entry.setUpdatedAt(LocalDateTime.now());
            whitelistRepository.save(entry);
            
            // Remove from Aerospike cache
            try {
                Long entityIdLong = Long.parseLong(entityId);
                screeningCacheService.removeWhitelistEntry(entityIdLong, entityType);
            } catch (NumberFormatException e) {
                // Skip cache removal if entityId is not a number
            }
            
            logger.info("Removed {} {} from whitelist", entityType, entityId);
        }
    }

    /**
     * Get all active whitelist entries
     */
    public List<ScreeningWhitelist> getAllActiveWhitelist() {
        return whitelistRepository.findByActive(true);
    }

    /**
     * Get whitelist entries by entity type
     */
    public List<ScreeningWhitelist> getWhitelistByType(String entityType) {
        return whitelistRepository.findByEntityTypeAndActive(entityType, true);
    }
}

