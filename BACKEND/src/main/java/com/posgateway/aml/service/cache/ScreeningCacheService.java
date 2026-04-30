package com.posgateway.aml.service.cache;

import com.posgateway.aml.model.ScreeningResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Screening Cache Service
 * Caches watchlist data and screening results in Aerospike for ultra-fast lookups
 */
@Service
public class ScreeningCacheService {

    private static final Logger logger = LoggerFactory.getLogger(ScreeningCacheService.class);

    @Autowired
    private AerospikeCacheService cacheService;

    @Value("${sanctions.cache.ttl.hours:24}")
    private int cacheTtlHours;

    private static final String SET_SCREENING_RESULTS = "screening_results";
    private static final String SET_WHITELIST = "screening_whitelist";
    private static final String SET_OVERRIDES = "screening_overrides";
    private static final String SET_CUSTOM_WATCHLISTS = "custom_watchlists";

    /**
     * Cache screening result
     */
    public void cacheScreeningResult(String entityId, String entityType, ScreeningResult result) {
        String key = entityId + ":" + entityType;
        cacheService.put(SET_SCREENING_RESULTS, key, result, (int) TimeUnit.HOURS.toSeconds(cacheTtlHours));
        logger.debug("Cached screening result for {}:{}", entityType, entityId);
    }

    /**
     * Get cached screening result
     */
    public ScreeningResult getCachedScreeningResult(String entityId, String entityType) {
        String key = entityId + ":" + entityType;
        return cacheService.get(SET_SCREENING_RESULTS, key, ScreeningResult.class);
    }

    /**
     * Cache whitelist entry
     */
    public void cacheWhitelistEntry(Long entityId, String entityName, String entityType) {
        String key = entityId + ":" + entityType;
        cacheService.put(SET_WHITELIST, key, entityName, (int) TimeUnit.HOURS.toSeconds(24));
        logger.debug("Cached whitelist entry: {}:{}", entityType, entityId);
    }

    /**
     * Check if entity is whitelisted (fast lookup)
     */
    public boolean isWhitelisted(Long entityId, String entityType) {
        String key = entityId + ":" + entityType;
        return cacheService.exists(SET_WHITELIST, key);
    }

    /**
     * Remove whitelist entry from cache
     */
    public void removeWhitelistEntry(Long entityId, String entityType) {
        String key = entityId + ":" + entityType;
        cacheService.delete(SET_WHITELIST, key);
    }

    /**
     * Cache override entry
     */
    public void cacheOverride(Long entityId, String entityType, boolean isOverridden) {
        String key = entityId + ":" + entityType;
        cacheService.put(SET_OVERRIDES, key, isOverridden, (int) TimeUnit.HOURS.toSeconds(24));
        logger.debug("Cached override: {}:{} = {}", entityType, entityId, isOverridden);
    }

    /**
     * Check if entity has override (fast lookup)
     */
    public Boolean hasOverride(Long entityId, String entityType) {
        String key = entityId + ":" + entityType;
        return cacheService.get(SET_OVERRIDES, key, Boolean.class);
    }

    /**
     * Remove override from cache
     */
    public void removeOverride(Long entityId, String entityType) {
        String key = entityId + ":" + entityType;
        cacheService.delete(SET_OVERRIDES, key);
    }

    /**
     * Cache custom watchlist entry
     */
    public void cacheCustomWatchlistEntry(String entityName, String entityType, boolean isOnWatchlist) {
        String key = entityName.toLowerCase() + ":" + entityType;
        cacheService.put(SET_CUSTOM_WATCHLISTS, key, isOnWatchlist, (int) TimeUnit.HOURS.toSeconds(24));
        logger.debug("Cached custom watchlist entry: {}:{}", entityType, entityName);
    }

    /**
     * Check if entity is on custom watchlist (fast lookup)
     */
    public Boolean isOnCustomWatchlist(String entityName, String entityType) {
        String key = entityName.toLowerCase() + ":" + entityType;
        return cacheService.get(SET_CUSTOM_WATCHLISTS, key, Boolean.class);
    }

    /**
     * Invalidate all screening caches for an entity
     */
    public void invalidateEntity(String entityId, String entityType) {
        String key = entityId + ":" + entityType;
        cacheService.delete(SET_SCREENING_RESULTS, key);
        cacheService.delete(SET_WHITELIST, key);
        cacheService.delete(SET_OVERRIDES, key);
        logger.debug("Invalidated all caches for {}:{}", entityType, entityId);
    }
}

