package com.posgateway.aml.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.model.ScreeningResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Screening Cache Service
 * Caches watchlist data and screening results in Redis for ultra-fast lookups.
 */
@Service
public class ScreeningCacheService {

    private static final Logger logger = LoggerFactory.getLogger(ScreeningCacheService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${sanctions.cache.ttl.hours:24}")
    private int cacheTtlHours;

    private static final String SET_SCREENING_RESULTS = "screening_results";
    private static final String SET_WHITELIST = "screening_whitelist";
    private static final String SET_OVERRIDES = "screening_overrides";
    private static final String SET_CUSTOM_WATCHLISTS = "custom_watchlists";

    private static String key(String set, String id) {
        return set + ":" + id;
    }

    /** Cache screening result */
    public void cacheScreeningResult(String entityId, String entityType, ScreeningResult result) {
        String k = key(SET_SCREENING_RESULTS, entityId + ":" + entityType);
        redisTemplate.opsForValue().set(k, result, Duration.ofHours(cacheTtlHours));
        logger.debug("Cached screening result for {}:{}", entityType, entityId);
    }

    /** Get cached screening result */
    public ScreeningResult getCachedScreeningResult(String entityId, String entityType) {
        String k = key(SET_SCREENING_RESULTS, entityId + ":" + entityType);
        Object v = redisTemplate.opsForValue().get(k);
        if (v == null) return null;
        if (v instanceof ScreeningResult) return (ScreeningResult) v;
        // Round-trip via Jackson if Redis returned a generic Map (default value-serializer behavior)
        try {
            return objectMapper.convertValue(v, ScreeningResult.class);
        } catch (Exception e) {
            logger.warn("Failed to coerce cached screening result for {}:{}: {}", entityType, entityId, e.getMessage());
            return null;
        }
    }

    /** Cache whitelist entry */
    public void cacheWhitelistEntry(Long entityId, String entityName, String entityType) {
        String k = key(SET_WHITELIST, entityId + ":" + entityType);
        redisTemplate.opsForValue().set(k, entityName, Duration.ofHours(24));
        logger.debug("Cached whitelist entry: {}:{}", entityType, entityId);
    }

    /** Check if entity is whitelisted (fast lookup) */
    public boolean isWhitelisted(Long entityId, String entityType) {
        String k = key(SET_WHITELIST, entityId + ":" + entityType);
        return Boolean.TRUE.equals(redisTemplate.hasKey(k));
    }

    /** Remove whitelist entry from cache */
    public void removeWhitelistEntry(Long entityId, String entityType) {
        String k = key(SET_WHITELIST, entityId + ":" + entityType);
        redisTemplate.delete(k);
    }

    /** Cache override entry */
    public void cacheOverride(Long entityId, String entityType, boolean isOverridden) {
        String k = key(SET_OVERRIDES, entityId + ":" + entityType);
        redisTemplate.opsForValue().set(k, isOverridden, Duration.ofHours(24));
        logger.debug("Cached override: {}:{} = {}", entityType, entityId, isOverridden);
    }

    /** Check if entity has override (fast lookup) */
    public Boolean hasOverride(Long entityId, String entityType) {
        String k = key(SET_OVERRIDES, entityId + ":" + entityType);
        Object v = redisTemplate.opsForValue().get(k);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    /** Remove override from cache */
    public void removeOverride(Long entityId, String entityType) {
        String k = key(SET_OVERRIDES, entityId + ":" + entityType);
        redisTemplate.delete(k);
    }

    /** Cache custom watchlist entry */
    public void cacheCustomWatchlistEntry(String entityName, String entityType, boolean isOnWatchlist) {
        String k = key(SET_CUSTOM_WATCHLISTS, entityName.toLowerCase() + ":" + entityType);
        redisTemplate.opsForValue().set(k, isOnWatchlist, Duration.ofHours(24));
        logger.debug("Cached custom watchlist entry: {}:{}", entityType, entityName);
    }

    /** Check if entity is on custom watchlist (fast lookup) */
    public Boolean isOnCustomWatchlist(String entityName, String entityType) {
        String k = key(SET_CUSTOM_WATCHLISTS, entityName.toLowerCase() + ":" + entityType);
        Object v = redisTemplate.opsForValue().get(k);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    /** Invalidate all screening caches for an entity */
    public void invalidateEntity(String entityId, String entityType) {
        String suffix = entityId + ":" + entityType;
        redisTemplate.delete(key(SET_SCREENING_RESULTS, suffix));
        redisTemplate.delete(key(SET_WHITELIST, suffix));
        redisTemplate.delete(key(SET_OVERRIDES, suffix));
        logger.debug("Invalidated all caches for {}:{}", entityType, entityId);
    }
}
