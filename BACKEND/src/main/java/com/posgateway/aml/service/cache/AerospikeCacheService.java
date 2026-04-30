package com.posgateway.aml.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic in-process key/value cache.
 *
 * <p>Historical name kept (was {@code AerospikeCacheService}). Aerospike has been
 * relocated to the {@code aml-microservice}; this class now uses Caffeine and
 * provides the same API surface so existing call sites compile unchanged.
 *
 * <p>TODO(aerospike-removal): if any of the four caller services
 * ({@link KycDataCacheService}, {@link ScreeningCacheService},
 * {@link AlertMetricsCacheService}, {@link DocumentAccessCacheService}) require
 * cluster-wide visibility of cached entries, route those calls through
 * {@code AmlMicroserviceClient} instead.
 */
@Service
public class AerospikeCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeCacheService.class);

    @Value("${aerospike.cache.default.ttl.seconds:3600}")
    private int defaultTtlSeconds;

    /** One Caffeine cache per logical "set" — created lazily, all share the default TTL. */
    private final Map<String, Cache<String, Object>> sets = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        logger.info("AerospikeCacheService is now Caffeine-backed (in-process). Default TTL={}s", defaultTtlSeconds);
    }

    private Cache<String, Object> setCache(String set, int ttlSeconds) {
        return sets.computeIfAbsent(set, s -> Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, ttlSeconds)))
                .maximumSize(50_000)
                .build());
    }

    public void put(String set, String key, Object value) {
        put(set, key, value, defaultTtlSeconds);
    }

    public void put(String set, String key, Object value, int ttlSeconds) {
        if (value == null) return;
        setCache(set, ttlSeconds).put(key, value);
    }

    public void putAll(String set, Map<String, Object> values, int ttlSeconds) {
        Cache<String, Object> c = setCache(set, ttlSeconds);
        values.forEach((k, v) -> { if (v != null) c.put(k, v); });
    }

    public Object get(String set, String key) {
        Cache<String, Object> c = sets.get(set);
        return c != null ? c.getIfPresent(key) : null;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String set, String key, Class<T> type) {
        Object v = get(set, key);
        return (v != null && type.isInstance(v)) ? (T) v : null;
    }

    public boolean exists(String set, String key) {
        return get(set, key) != null;
    }

    public void delete(String set, String key) {
        Cache<String, Object> c = sets.get(set);
        if (c != null) c.invalidate(key);
    }

    /**
     * Stores a {@code Map<String,Object>} as a single composite entry.
     * Different from {@link #putAll(String, Map, int)} which stores each entry separately.
     */
    public void putMap(String set, String key, Map<String, Object> map, int ttlSeconds) {
        if (map == null) return;
        setCache(set, ttlSeconds).put(key, new HashMap<>(map));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String set, String key) {
        Object v = get(set, key);
        return (v instanceof Map) ? new HashMap<>((Map<String, Object>) v) : null;
    }
}
