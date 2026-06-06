package com.posgateway.aml.service.feature;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis-backed hot-path feature store.
 *
 * Aerospike is intentionally not used in BACKEND. Low-latency sanctions and AML
 * lookups live in aml-microservice; backend feature/rule counters use Redis.
 */
@Service
public class FeatureStoreService {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final String FEATURE_KEY = "aml:feature:%s";
    private static final String COUNTER_KEY = "aml:counter:%s";
    private static final String RISK_KEY = "aml:risk-score:%s";

    private final RedisTemplate<String, Object> redisTemplate;

    public FeatureStoreService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeFeature(String key, String fieldName, Object value) {
        String redisKey = FEATURE_KEY.formatted(key);
        redisTemplate.opsForHash().put(redisKey, fieldName, value);
        redisTemplate.expire(redisKey, DEFAULT_TTL);
    }

    public Object getFeature(String key, String fieldName) {
        return redisTemplate.opsForHash().get(FEATURE_KEY.formatted(key), fieldName);
    }

    public Map<String, Object> getAllFeatures(String key) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(FEATURE_KEY.formatted(key));
        Map<String, Object> features = new HashMap<>();
        raw.forEach((k, v) -> features.put(String.valueOf(k), v));
        return features;
    }

    public void incrementCounter(String key, String fieldName, long amount) {
        String redisKey = COUNTER_KEY.formatted(key);
        redisTemplate.opsForHash().increment(redisKey, fieldName, amount);
        redisTemplate.expire(redisKey, DEFAULT_TTL);
    }

    public long getCounter(String key, String fieldName) {
        Object value = redisTemplate.opsForHash().get(COUNTER_KEY.formatted(key), fieldName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    public void storeRiskScore(String entityId, double score, String riskType) {
        String redisKey = RISK_KEY.formatted(entityId);
        redisTemplate.opsForHash().put(redisKey, "score", score);
        redisTemplate.opsForHash().put(redisKey, "riskType", riskType);
        redisTemplate.opsForHash().put(redisKey, "lastUpdated", System.currentTimeMillis());
        redisTemplate.expire(redisKey, DEFAULT_TTL);
    }
}
