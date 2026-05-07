package com.posgateway.aml.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.features.CustomerFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Feature Cache Service
 * 
 * Purpose: Provide O(1) access to customer features via Redis
 * 
 * Caching Strategy:
 * - Features: No expiry (persisted in DB, cached in Redis)
 * - Velocity counters: Sliding window with TTL
 * - Session data: Short TTL (hours)
 * 
 * Key Patterns:
 * - aml:customer:{customerId}:features - Main feature object
 * - aml:customer:{customerId}:tx:timestamps - Transaction timestamps (sorted set)
 * - aml:customer:{customerId}:velocity:1h - Rolling 1h velocity
 * - aml:blacklist:{type} - Blacklist entries (set)
 * 
 * Performance:
 * - Target: < 1ms for feature retrieval
 * - Fallback to DB on cache miss
 * - Async cache updates via Kafka
 */
@Service
public class FeatureCacheService {

    private static final Logger logger = LoggerFactory.getLogger(FeatureCacheService.class);

    // Key prefixes
    private static final String KEY_PREFIX = "aml:";
    private static final String KEY_FEATURES = KEY_PREFIX + "customer:%s:features";
    private static final String KEY_TX_TIMESTAMPS = KEY_PREFIX + "customer:%s:tx:timestamps";
    private static final String KEY_VELOCITY_1H = KEY_PREFIX + "customer:%s:velocity:1h";
    private static final String KEY_VELOCITY_24H = KEY_PREFIX + "customer:%s:velocity:24h";
    private static final String KEY_COUNTRIES = KEY_PREFIX + "customer:%s:countries";
    private static final String KEY_BLACKLIST = KEY_PREFIX + "blacklist:%s";

    // TTL values
    private static final long TTL_FEATURES_DAYS = 7;
    private static final long TTL_VELOCITY_SECONDS = 3600; // 1 hour
    private static final long TTL_COUNTRIES_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public FeatureCacheService(RedisTemplate<String, Object> redisTemplate,
                               RedisTemplate<String, String> stringRedisTemplate,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    // ========== Customer Features ==========

    /**
     * Get customer features from cache
     * Falls back to null on cache miss (caller should load from DB)
     */
    public Optional<CustomerFeatures> getFeatures(String customerId) {
        try {
            String key = String.format(KEY_FEATURES, customerId);
            Object value = redisTemplate.opsForValue().get(key);
            
            if (value == null) {
                logger.debug("Cache miss for customer features: {}", customerId);
                return Optional.empty();
            }
            
            // Deserialize
            String json = objectMapper.writeValueAsString(value);
            CustomerFeatures features = objectMapper.readValue(json, CustomerFeatures.class);
            logger.debug("Cache hit for customer features: {}", customerId);
            return Optional.of(features);
            
        } catch (Exception e) {
            logger.error("Error reading features from cache for customer: {}", customerId, e);
            return Optional.empty();
        }
    }

    /**
     * Store customer features in cache
     */
    public void putFeatures(String customerId, CustomerFeatures features) {
        try {
            String key = String.format(KEY_FEATURES, customerId);
            redisTemplate.opsForValue().set(key, features);
            redisTemplate.expire(key, TTL_FEATURES_DAYS, TimeUnit.DAYS);
            logger.debug("Cached features for customer: {}", customerId);
        } catch (Exception e) {
            logger.error("Error caching features for customer: {}", customerId, e);
        }
    }

    /**
     * Invalidate customer features cache
     */
    public void invalidateFeatures(String customerId) {
        String key = String.format(KEY_FEATURES, customerId);
        redisTemplate.delete(key);
        logger.debug("Invalidated cache for customer: {}", customerId);
    }

    // ========== Velocity Tracking (Sliding Window) ==========

    /**
     * Record transaction for velocity tracking
     * Uses Redis sorted sets for O(log n) sliding window
     */
    public void recordTransaction(String customerId, long timestamp, double score) {
        try {
            String key = String.format(KEY_TX_TIMESTAMPS, customerId);
            ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();
            
            // Add transaction timestamp
            zSetOps.add(key, String.valueOf(timestamp), timestamp);
            
            // Clean old entries (> 24 hours)
            long cutoff = timestamp - (24 * 3600 * 1000);
            zSetOps.removeRangeByScore(key, 0, cutoff);
            
            // Set expiry
            stringRedisTemplate.expire(key, TTL_VELOCITY_SECONDS, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            logger.error("Error recording transaction for velocity: {}", customerId, e);
        }
    }

    /**
     * Get transaction count in time window
     */
    public long getTxCountInWindow(String customerId, long windowMs) {
        try {
            String key = String.format(KEY_TX_TIMESTAMPS, customerId);
            ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();
            
            long now = System.currentTimeMillis();
            long start = now - windowMs;
            
            Long count = zSetOps.count(key, start, now);
            return count != null ? count : 0;
            
        } catch (Exception e) {
            logger.error("Error getting velocity for customer: {}", customerId, e);
            return 0;
        }
    }

    /**
     * Increment counter with expiry (simple approach)
     */
    public void incrementCounter(String customerId, String counterType, long windowSeconds) {
        String key = String.format(KEY_PREFIX + "customer:%s:counter:%s", customerId, counterType);
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        
        Long current = valueOps.increment(key);
        if (current != null && current == 1) {
            // First increment, set expiry
            stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
    }

    /**
     * Get counter value
     */
    public long getCounter(String customerId, String counterType) {
        String key = String.format(KEY_PREFIX + "customer:%s:counter:%s", customerId, counterType);
        String value = stringRedisTemplate.opsForValue().get(key);
        try {
            return value != null ? Long.parseLong(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ========== Blacklist / Whitelist ==========

    /**
     * Check if value is in blacklist
     */
    public boolean isBlacklisted(String type, String value) {
        try {
            String key = String.format(KEY_BLACKLIST, type);
            return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, value));
        } catch (Exception e) {
            logger.error("Error checking blacklist: {}:{}", type, value, e);
            return false;
        }
    }

    /**
     * Add to blacklist
     */
    public void addToBlacklist(String type, String value) {
        try {
            String key = String.format(KEY_BLACKLIST, type);
            stringRedisTemplate.opsForSet().add(key, value);
        } catch (Exception e) {
            logger.error("Error adding to blacklist: {}:{}", type, value, e);
        }
    }

    /**
     * Remove from blacklist
     */
    public void removeFromBlacklist(String type, String value) {
        try {
            String key = String.format(KEY_BLACKLIST, type);
            stringRedisTemplate.opsForSet().remove(key, value);
        } catch (Exception e) {
            logger.error("Error removing from blacklist: {}:{}", type, value, e);
        }
    }

    // ========== Country Tracking ==========

    /**
     * Add country to customer's recent countries
     */
    public void addCountry(String customerId, String countryCode, long windowHours) {
        try {
            String key = String.format(KEY_COUNTRIES, customerId);
            long now = System.currentTimeMillis();
            stringRedisTemplate.opsForZSet().add(key, countryCode, now);
            stringRedisTemplate.expire(key, windowHours, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.error("Error adding country for customer: {}", customerId, e);
        }
    }

    /**
     * Get unique countries in time window
     */
    public Set<String> getCountriesInWindow(String customerId, long hours) {
        try {
            String key = String.format(KEY_COUNTRIES, customerId);
            long now = System.currentTimeMillis();
            long start = now - (hours * 3600 * 1000);
            
            Set<String> countries = stringRedisTemplate.opsForZSet()
                    .rangeByScore(key, start, now);
            
            return countries != null ? countries : new HashSet<>();
            
        } catch (Exception e) {
            logger.error("Error getting countries for customer: {}", customerId, e);
            return new HashSet<>();
        }
    }

    /**
     * Get count of unique countries
     */
    public int getUniqueCountryCount(String customerId, long hours) {
        return getCountriesInWindow(customerId, hours).size();
    }

    // ========== Utility Methods ==========

    /**
     * Clear all cache for a customer
     */
    public void clearCustomerCache(String customerId) {
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "customer:" + customerId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("Cleared {} cache keys for customer: {}", keys.size(), customerId);
            }
        } catch (Exception e) {
            logger.error("Error clearing cache for customer: {}", customerId, e);
        }
    }

    /**
     * Check Redis connectivity
     */
    public boolean isHealthy() {
        try {
            StringRedisSerializer serializer = new StringRedisSerializer();
            return redisTemplate.getConnectionFactory() != null &&
                   redisTemplate.getConnectionFactory().getConnection() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
