package com.posgateway.aml.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.features.CustomerFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Feature Cache Service.
 *
 * Purpose: Provide O(1) access to customer features via Redis.
 *
 * Caching Strategy:
 * - Features: 7-day TTL (refreshed on update)
 * - Velocity counters: Sliding window (sorted sets) with TTL
 * - Country tracking: 24h TTL
 * - Blacklist sets: no expiry
 *
 * Key Patterns:
 * - aml:customer:{customerId}:features        - Main feature object
 * - aml:customer:{customerId}:tx:timestamps   - Transaction timestamps (sorted set)
 * - aml:customer:{customerId}:velocity:1m     - Rolling 1m velocity counter
 * - aml:customer:{customerId}:velocity:5m     - Rolling 5m velocity counter
 * - aml:customer:{customerId}:velocity:1h     - Rolling 1h velocity counter
 * - aml:customer:{customerId}:countries       - Recent countries (sorted set)
 * - aml:blacklist:{type}                      - Blacklist entries (set)
 *
 * Performance: target &lt; 1ms for feature retrieval.
 */
@Service
public class FeatureCacheService {

    private static final Logger logger = LoggerFactory.getLogger(FeatureCacheService.class);

    // Key prefixes
    private static final String KEY_PREFIX = "aml:";
    private static final String KEY_FEATURES = KEY_PREFIX + "customer:%s:features";
    private static final String KEY_TX_TIMESTAMPS = KEY_PREFIX + "customer:%s:tx:timestamps";
    private static final String KEY_COUNTRIES = KEY_PREFIX + "customer:%s:countries";
    private static final String KEY_BLACKLIST = KEY_PREFIX + "blacklist:%s";
    private static final String KEY_VELOCITY = KEY_PREFIX + "customer:%s:velocity:%s";
    private static final String KEY_LAST_GEO = KEY_PREFIX + "customer:%s:last-geo";

    // TTL values
    private static final long TTL_FEATURES_DAYS = 7;
    private static final long TTL_VELOCITY_SECONDS = 3600; // 1 hour
    private static final long TTL_VELOCITY_1M_SECONDS = 60;
    private static final long TTL_VELOCITY_5M_SECONDS = 300;

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
     * Get customer features from cache. Returns Optional.empty() on cache miss
     * (caller should load from DB).
     */
    public Optional<CustomerFeatures> getFeatures(String customerId) {
        try {
            String key = String.format(KEY_FEATURES, customerId);
            Object value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                logger.debug("Cache miss for customer features: {}", customerId);
                return Optional.empty();
            }

            // Deserialize through Jackson (the value is a generic Map after JSON round-trip)
            String json = objectMapper.writeValueAsString(value);
            CustomerFeatures features = objectMapper.readValue(json, CustomerFeatures.class);
            logger.debug("Cache hit for customer features: {}", customerId);
            return Optional.of(features);

        } catch (Exception e) {
            logger.error("Error reading features from cache for customer: {}", customerId, e);
            return Optional.empty();
        }
    }

    /** Store customer features in cache. */
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

    /** Invalidate customer features cache. */
    public void invalidateFeatures(String customerId) {
        String key = String.format(KEY_FEATURES, customerId);
        redisTemplate.delete(key);
        logger.debug("Invalidated cache for customer: {}", customerId);
    }

    // ========== Velocity Tracking (Sliding Window) ==========

    /**
     * Record transaction for velocity tracking.
     * Uses Redis sorted sets for O(log n) sliding window.
     */
    public void recordTransaction(String customerId, long timestamp, double score) {
        try {
            String key = String.format(KEY_TX_TIMESTAMPS, customerId);
            ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();

            zSetOps.add(key, String.valueOf(timestamp), timestamp);

            // Clean old entries (> 24 hours)
            long cutoff = timestamp - (24 * 3600 * 1000);
            zSetOps.removeRangeByScore(key, 0, cutoff);

            stringRedisTemplate.expire(key, TTL_VELOCITY_SECONDS, TimeUnit.SECONDS);
            incrementVelocityWindows(customerId);

        } catch (Exception e) {
            logger.error("Error recording transaction for velocity: {}", customerId, e);
        }
    }

    /**
     * Idempotently records a committed transaction event. The transaction ID is
     * the sorted-set member, so Kafka redelivery updates the same member instead
     * of inflating velocity counts.
     */
    public void recordTransactionEvent(String customerId, String transactionId, long timestamp) {
        String key = String.format(KEY_TX_TIMESTAMPS, customerId);
        ZSetOperations<String, String> zSetOps = stringRedisTemplate.opsForZSet();
        zSetOps.add(key, transactionId, timestamp);
        long cutoff = timestamp - (24 * 3600 * 1000);
        zSetOps.removeRangeByScore(key, 0, cutoff);
        stringRedisTemplate.expire(key, TTL_VELOCITY_SECONDS, TimeUnit.SECONDS);
        incrementVelocityWindows(customerId);
    }

    public void incrementVelocityWindows(String customerId) {
        incrementVelocityKey(customerId, "1m", TTL_VELOCITY_1M_SECONDS);
        incrementVelocityKey(customerId, "5m", TTL_VELOCITY_5M_SECONDS);
        incrementVelocityKey(customerId, "1h", TTL_VELOCITY_SECONDS);
    }

    public long getVelocity1m(String customerId) {
        return getVelocity(customerId, "1m");
    }

    public long getVelocity5m(String customerId) {
        return getVelocity(customerId, "5m");
    }

    public long getVelocity1h(String customerId) {
        return getVelocity(customerId, "1h");
    }

    private void incrementVelocityKey(String customerId, String window, long ttlSeconds) {
        String key = String.format(KEY_VELOCITY, customerId, window);
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current != null && current == 1) {
            stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    private long getVelocity(String customerId, String window) {
        String key = String.format(KEY_VELOCITY, customerId, window);
        String value = stringRedisTemplate.opsForValue().get(key);
        try {
            return value != null ? Long.parseLong(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void recordLastLocation(String customerId, double latitude, double longitude, long timestampMs) {
        if (customerId == null || customerId.isBlank()) {
            return;
        }
        try {
            String key = String.format(KEY_LAST_GEO, customerId);
            stringRedisTemplate.opsForValue().set(key, latitude + "|" + longitude + "|" + timestampMs);
            stringRedisTemplate.expire(key, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.debug("Could not cache last location for {}: {}", customerId, e.getMessage());
        }
    }

    public java.util.Optional<GeoPoint> getLastLocation(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            String raw = stringRedisTemplate.opsForValue().get(String.format(KEY_LAST_GEO, customerId));
            if (raw == null || raw.isBlank()) {
                return java.util.Optional.empty();
            }
            String[] parts = raw.split("\\|");
            if (parts.length < 3) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new GeoPoint(
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    java.time.Instant.ofEpochMilli(Long.parseLong(parts[2]))
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()));
        } catch (Exception e) {
            logger.debug("Could not read last location for {}: {}", customerId, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    public record GeoPoint(double latitude, double longitude, java.time.LocalDateTime recordedAt) {}

    /** Get transaction count in time window. */
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

    /** Increment counter with expiry (simple approach). */
    public void incrementCounter(String customerId, String counterType, long windowSeconds) {
        String key = String.format(KEY_PREFIX + "customer:%s:counter:%s", customerId, counterType);
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();

        Long current = valueOps.increment(key);
        if (current != null && current == 1) {
            stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
    }

    /** Get counter value. */
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

    /** Check if value is in blacklist. */
    public boolean isBlacklisted(String type, String value) {
        try {
            String key = String.format(KEY_BLACKLIST, type);
            return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, value));
        } catch (Exception e) {
            logger.error("Error checking blacklist: {}:{}", type, value, e);
            return false;
        }
    }

    /** Add to blacklist. */
    public void addToBlacklist(String type, String value) {
        try {
            String key = String.format(KEY_BLACKLIST, type);
            stringRedisTemplate.opsForSet().add(key, value);
        } catch (Exception e) {
            logger.error("Error adding to blacklist: {}:{}", type, value, e);
        }
    }

    /** Remove from blacklist. */
    public void removeFromBlacklist(String type, String value) {
        try {
            String key = String.format(KEY_BLACKLIST, type);
            stringRedisTemplate.opsForSet().remove(key, value);
        } catch (Exception e) {
            logger.error("Error removing from blacklist: {}:{}", type, value, e);
        }
    }

    // ========== Country Tracking ==========

    /** Add country to customer's recent countries. */
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

    /** Get unique countries in time window. */
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

    /** Get count of unique countries. */
    public int getUniqueCountryCount(String customerId, long hours) {
        return getCountriesInWindow(customerId, hours).size();
    }

    // ========== Utility Methods ==========

    /** Clear all cache for a customer. */
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

    /** Check Redis connectivity. */
    public boolean isHealthy() {
        try {
            return redisTemplate.getConnectionFactory() != null &&
                   redisTemplate.getConnectionFactory().getConnection() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
