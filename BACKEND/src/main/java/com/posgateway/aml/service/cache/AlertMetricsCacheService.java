package com.posgateway.aml.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Alert Metrics Cache Service
 * Caches rule effectiveness metrics in Redis for fast dashboard queries.
 */
@Service
public class AlertMetricsCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AlertMetricsCacheService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${alert.metrics.cache.ttl.hours:6}")
    private int cacheTtlHours;

    private static final String SET_RULE_METRICS = "rule_metrics";
    private static final String SET_RULE_EFFECTIVENESS = "rule_effectiveness";

    private static String key(String set, String id) {
        return set + ":" + id;
    }

    /** Cache rule effectiveness metrics */
    public void cacheRuleEffectivenessMetrics(String ruleName, Map<String, Object> metrics) {
        String k = key(SET_RULE_METRICS, ruleName);
        redisTemplate.opsForValue().set(k, new HashMap<>(metrics), Duration.ofHours(cacheTtlHours));
        logger.debug("Cached rule metrics for rule: {}", ruleName);
    }

    /** Get cached rule metrics */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRuleEffectivenessMetrics(String ruleName) {
        String k = key(SET_RULE_METRICS, ruleName);
        Object v = redisTemplate.opsForValue().get(k);
        return v instanceof Map ? new HashMap<>((Map<String, Object>) v) : null;
    }

    /** Cache rule effectiveness summary */
    public void cacheRuleEffectivenessSummary(String periodKey, Map<String, Object> summary) {
        String k = key(SET_RULE_EFFECTIVENESS, periodKey);
        redisTemplate.opsForValue().set(k, new HashMap<>(summary), Duration.ofHours(cacheTtlHours));
        logger.debug("Cached rule effectiveness summary for period: {}", periodKey);
    }

    /** Get cached rule effectiveness summary */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedRuleEffectivenessSummary(String periodKey) {
        String k = key(SET_RULE_EFFECTIVENESS, periodKey);
        Object v = redisTemplate.opsForValue().get(k);
        return v instanceof Map ? new HashMap<>((Map<String, Object>) v) : null;
    }

    /** Generate period key from dates */
    public String generatePeriodKey(LocalDateTime startDate, LocalDateTime endDate) {
        return startDate.toString() + "_" + endDate.toString();
    }

    /** Invalidate rule metrics cache */
    public void invalidateRuleMetrics(String ruleName) {
        redisTemplate.delete(key(SET_RULE_METRICS, ruleName));
        logger.debug("Invalidated rule metrics cache for: {}", ruleName);
    }
}
