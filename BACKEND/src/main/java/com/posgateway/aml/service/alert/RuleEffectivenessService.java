package com.posgateway.aml.service.alert;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.service.cache.AlertMetricsCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule Effectiveness Tracking Service
 * Tracks and measures rule performance metrics
 */
@Service
public class RuleEffectivenessService {

    private static final Logger logger = LoggerFactory.getLogger(RuleEffectivenessService.class);

    private final AlertRepository alertRepository;
    private final AlertMetricsCacheService metricsCacheService; // Aerospike cache for fast lookups

    @Autowired
    public RuleEffectivenessService(AlertRepository alertRepository,
                                     AlertMetricsCacheService metricsCacheService) {
        this.alertRepository = alertRepository;
        this.metricsCacheService = metricsCacheService;
    }

    /**
     * Get rule effectiveness metrics
     */
    public Map<String, Object> getRuleEffectiveness(String ruleName) {
        // Try to get from cache first
        Map<String, Object> cachedMetrics = metricsCacheService.getRuleEffectivenessMetrics(ruleName);
        if (cachedMetrics != null) {
            logger.debug("Rule effectiveness metrics for {} retrieved from cache", ruleName);
            return cachedMetrics;
        }

        // Get all alerts (in real implementation, would filter by rule name)
        List<Alert> allAlerts = alertRepository.findAll();
        
        long totalAlerts = allAlerts.size();
        long truePositives = allAlerts.stream()
                .filter(a -> a.getDisposition() != null && a.getDisposition().isTruePositive())
                .count();
        long falsePositives = allAlerts.stream()
                .filter(a -> a.getDisposition() != null && a.getDisposition().isFalsePositive())
                .count();
        long pending = allAlerts.stream()
                .filter(a -> a.getDisposition() == null || a.getStatus().equals("open"))
                .count();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("ruleName", ruleName);
        metrics.put("totalAlerts", totalAlerts);
        metrics.put("truePositives", truePositives);
        metrics.put("falsePositives", falsePositives);
        metrics.put("pending", pending);
        
        if (totalAlerts > 0) {
            metrics.put("truePositiveRate", truePositives / (double) totalAlerts);
            metrics.put("falsePositiveRate", falsePositives / (double) totalAlerts);
            metrics.put("precision", (truePositives + falsePositives) > 0 ? 
                    truePositives / (double) (truePositives + falsePositives) : 0.0);
        } else {
            metrics.put("truePositiveRate", 0.0);
            metrics.put("falsePositiveRate", 0.0);
            metrics.put("precision", 0.0);
        }

        // Cache the calculated metrics
        metricsCacheService.cacheRuleEffectivenessMetrics(ruleName, metrics);
        return metrics;
    }

    /**
     * Get effectiveness metrics for all rules
     */
    public List<Map<String, Object>> getAllRulesEffectiveness() {
        // In real implementation, would group by rule name
        // For now, return overall metrics
        Map<String, Object> overall = getRuleEffectiveness("ALL_RULES");
        return List.of(overall);
    }

    /**
     * Get effectiveness trends over time
     */
    public Map<String, Object> getEffectivenessTrends(String ruleName, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<Alert> recentAlerts = alertRepository.findAll().stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(startDate))
                .toList();

        Map<String, Object> trends = new HashMap<>();
        trends.put("ruleName", ruleName);
        trends.put("periodDays", days);
        trends.put("alertsInPeriod", recentAlerts.size());
        
        // Calculate trends
        long recentTruePositives = recentAlerts.stream()
                .filter(a -> a.getDisposition() != null && a.getDisposition().isTruePositive())
                .count();
        long recentFalsePositives = recentAlerts.stream()
                .filter(a -> a.getDisposition() != null && a.getDisposition().isFalsePositive())
                .count();

        trends.put("recentTruePositives", recentTruePositives);
        trends.put("recentFalsePositives", recentFalsePositives);
        
        // Cache trends in Aerospike for future fast lookups
        String periodKey = metricsCacheService.generatePeriodKey(startDate, LocalDateTime.now());
        metricsCacheService.cacheRuleEffectivenessSummary(periodKey, trends);

        return trends;
    }
}

