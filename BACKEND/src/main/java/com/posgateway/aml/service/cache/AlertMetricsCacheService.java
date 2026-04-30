package com.posgateway.aml.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Alert Metrics Cache Service
 * Caches rule effectiveness metrics in Aerospike for fast dashboard queries
 */
@Service
public class AlertMetricsCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AlertMetricsCacheService.class);

    @Autowired
    private AerospikeCacheService cacheService;

    @Value("${alert.metrics.cache.ttl.hours:6}")
    private int cacheTtlHours;

    private static final String SET_RULE_METRICS = "rule_metrics";
    private static final String SET_RULE_EFFECTIVENESS = "rule_effectiveness";

    /**
     * Cache rule effectiveness metrics
     */
    public void cacheRuleEffectivenessMetrics(String ruleName, Map<String, Object> metrics) {
        String key = ruleName;
        cacheService.putMap(SET_RULE_METRICS, key, metrics, (int) TimeUnit.HOURS.toSeconds(cacheTtlHours));
        logger.debug("Cached rule metrics for rule: {}", ruleName);
    }

    /**
     * Get cached rule metrics
     */
    public Map<String, Object> getRuleEffectivenessMetrics(String ruleName) {
        String key = ruleName;
        return cacheService.getMap(SET_RULE_METRICS, key);
    }

    /**
     * Cache rule effectiveness summary
     */
    public void cacheRuleEffectivenessSummary(String periodKey, Map<String, Object> summary) {
        cacheService.putMap(SET_RULE_EFFECTIVENESS, periodKey, summary, 
                (int) TimeUnit.HOURS.toSeconds(cacheTtlHours));
        logger.debug("Cached rule effectiveness summary for period: {}", periodKey);
    }

    /**
     * Get cached rule effectiveness summary
     */
    public Map<String, Object> getCachedRuleEffectivenessSummary(String periodKey) {
        return cacheService.getMap(SET_RULE_EFFECTIVENESS, periodKey);
    }

    /**
     * Generate period key from dates
     */
    public String generatePeriodKey(LocalDateTime startDate, LocalDateTime endDate) {
        return startDate.toString() + "_" + endDate.toString();
    }

    /**
     * Invalidate rule metrics cache
     */
    public void invalidateRuleMetrics(String ruleName) {
        cacheService.delete(SET_RULE_METRICS, ruleName);
        logger.debug("Invalidated rule metrics cache for: {}", ruleName);
    }
}

