package com.posgateway.aml.service;

import com.posgateway.aml.service.graph.AerospikeGraphCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * XGBoost Explainability Service.
 * 
 * Provides feature importance extraction and SHAP-like explanations
 * for regulatory compliance and audit trails. Results cached in Aerospike.
 * 
 * Use cases:
 * - Explain why a transaction was flagged
 * - Identify top contributing features to risk score
 * - Audit trail for regulators
 */
@Service
public class XGBoostExplainabilityService {

    private static final Logger logger = LoggerFactory.getLogger(XGBoostExplainabilityService.class);

    private final AerospikeGraphCacheService aerospikeCache;

    // Feature weights (simulated - in production, extract from XGBoost model)
    private static final Map<String, Double> FEATURE_WEIGHTS = Map.ofEntries(
            Map.entry("amount", 0.12),
            Map.entry("log_amount", 0.08),
            Map.entry("txn_hour_of_day", 0.05),
            Map.entry("merchant_txn_count_1h", 0.09),
            Map.entry("merchant_txn_amount_sum_24h", 0.07),
            Map.entry("pan_txn_count_1h", 0.11),
            Map.entry("pan_txn_amount_sum_7d", 0.06),
            Map.entry("distinct_terminals_last_30d_for_pan", 0.08),
            Map.entry("avg_amount_by_pan_30d", 0.05),
            Map.entry("time_since_last_txn_for_pan_minutes", 0.04),
            Map.entry("zscore_amount_vs_pan_history", 0.10),
            Map.entry("pageRank", 0.04),
            Map.entry("communityId", 0.03),
            Map.entry("betweenness", 0.05),
            Map.entry("connectionCount", 0.03));

    @Autowired
    public XGBoostExplainabilityService(
            @Autowired(required = false) AerospikeGraphCacheService aerospikeCache) {
        this.aerospikeCache = aerospikeCache;
    }

    /**
     * Generate explanation for a transaction risk score.
     * 
     * @param txnId    Transaction ID
     * @param features Feature map used for scoring
     * @param mlScore  The ML risk score
     * @return Explanation with top contributing features
     */
    public ScoreExplanation explainScore(Long txnId, Map<String, Object> features, Double mlScore) {
        logger.debug("Generating explanation for txn {} with score {}", txnId, mlScore);

        // Calculate feature contributions
        Map<String, FeatureContribution> contributions = new HashMap<>();

        for (Map.Entry<String, Double> weight : FEATURE_WEIGHTS.entrySet()) {
            String featureName = weight.getKey();
            Double featureWeight = weight.getValue();
            Object featureValue = features.get(featureName);

            if (featureValue != null) {
                double normalizedValue = normalizeFeature(featureName, featureValue);
                double contribution = featureWeight * normalizedValue * mlScore;

                contributions.put(featureName, new FeatureContribution(
                        featureName,
                        featureValue,
                        normalizedValue,
                        featureWeight,
                        contribution));
            }
        }

        // Sort by absolute contribution (highest first)
        List<FeatureContribution> sortedContributions = contributions.values().stream()
                .sorted((a, b) -> Double.compare(Math.abs(b.contribution), Math.abs(a.contribution)))
                .collect(Collectors.toList());

        // Get top 5 contributors
        List<FeatureContribution> topContributors = sortedContributions.stream()
                .limit(5)
                .collect(Collectors.toList());

        // Generate human-readable reasons
        List<String> reasons = topContributors.stream()
                .map(this::generateReason)
                .collect(Collectors.toList());

        ScoreExplanation explanation = new ScoreExplanation(
                txnId,
                mlScore,
                topContributors,
                reasons,
                System.currentTimeMillis());

        // Cache in Aerospike for audit trail
        cacheExplanation(txnId, explanation);

        logger.info("Generated explanation for txn {}: top factors = {}",
                txnId, topContributors.stream().map(c -> c.featureName).collect(Collectors.joining(", ")));

        return explanation;
    }

    /**
     * Get feature importance rankings.
     * Returns all features sorted by their weight in the model.
     */
    public List<Map<String, Object>> getFeatureImportance() {
        return FEATURE_WEIGHTS.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("feature", e.getKey());
                    item.put("importance", e.getValue());
                    item.put("category", categorizeFeature(e.getKey()));
                    return item;
                })
                .collect(Collectors.toList());
    }

    private double normalizeFeature(String featureName, Object value) {
        if (value == null)
            return 0.0;
        double v = value instanceof Number ? ((Number) value).doubleValue() : 0.0;

        // Feature-specific normalization
        return switch (featureName) {
            case "amount" -> Math.min(v / 10000.0, 1.0);
            case "log_amount" -> Math.min(v / 12.0, 1.0);
            case "txn_hour_of_day" -> v / 24.0;
            case "merchant_txn_count_1h", "pan_txn_count_1h" -> Math.min(v / 50.0, 1.0);
            case "merchant_txn_amount_sum_24h" -> Math.min(v / 100000.0, 1.0);
            case "pan_txn_amount_sum_7d" -> Math.min(v / 50000.0, 1.0);
            case "distinct_terminals_last_30d_for_pan" -> Math.min(v / 20.0, 1.0);
            case "avg_amount_by_pan_30d" -> Math.min(v / 5000.0, 1.0);
            case "time_since_last_txn_for_pan_minutes" -> v > 0 ? Math.min(v / 1440.0, 1.0) : 0.5;
            case "zscore_amount_vs_pan_history" -> (v + 5.0) / 10.0; // Normalize -5 to 5 → 0 to 1
            case "pageRank", "betweenness" -> Math.min(v, 1.0);
            case "communityId" -> (v % 100) / 100.0;
            case "connectionCount" -> Math.min(v / 100.0, 1.0);
            default -> Math.min(Math.abs(v), 1.0);
        };
    }

    private String generateReason(FeatureContribution fc) {
        return switch (fc.featureName) {
            case "amount" -> String.format("Transaction amount ($%.2f) is %s",
                    ((Number) fc.rawValue).doubleValue(), fc.contribution > 0.05 ? "unusually high" : "elevated");
            case "pan_txn_count_1h" -> String.format("Card used %d times in last hour (velocity concern)",
                    ((Number) fc.rawValue).longValue());
            case "zscore_amount_vs_pan_history" -> String.format("Amount is %.1f std deviations from card's normal",
                    ((Number) fc.rawValue).doubleValue());
            case "merchant_txn_count_1h" -> String.format("Merchant has %d transactions in last hour",
                    ((Number) fc.rawValue).longValue());
            case "pageRank" -> String.format("Merchant has high network influence (PageRank: %.3f)",
                    ((Number) fc.rawValue).doubleValue());
            case "betweenness" -> String.format("Merchant is a network hub (Betweenness: %.3f)",
                    ((Number) fc.rawValue).doubleValue());
            case "distinct_terminals_last_30d_for_pan" -> String.format("Card used at %d different terminals",
                    ((Number) fc.rawValue).longValue());
            default -> String.format("%s = %s contributed to risk", fc.featureName, fc.rawValue);
        };
    }

    private String categorizeFeature(String featureName) {
        if (featureName.contains("pageRank") || featureName.contains("betweenness") ||
                featureName.contains("community") || featureName.contains("connection")) {
            return "GRAPH";
        }
        if (featureName.contains("pan_") || featureName.contains("card")) {
            return "CARD_BEHAVIOR";
        }
        if (featureName.contains("merchant_")) {
            return "MERCHANT_BEHAVIOR";
        }
        if (featureName.contains("amount")) {
            return "AMOUNT";
        }
        return "OTHER";
    }

    private void cacheExplanation(Long txnId, ScoreExplanation explanation) {
        if (aerospikeCache == null)
            return;

        try {
            Map<String, Object> cacheData = new HashMap<>();
            cacheData.put("mlScore", explanation.mlScore);
            cacheData.put("topFeatures", explanation.topContributors.stream()
                    .map(c -> c.featureName).collect(Collectors.joining(",")));
            cacheData.put("reasons", String.join("|", explanation.reasons));
            cacheData.put("timestamp", explanation.timestamp);

            aerospikeCache.cacheGraphMetrics("explain_" + txnId, cacheData);
            logger.debug("Cached explanation for txn {} in Aerospike", txnId);
        } catch (Exception e) {
            logger.warn("Error caching explanation for txn {}: {}", txnId, e.getMessage());
        }
    }

    // DTO classes
    public static class FeatureContribution {
        public final String featureName;
        public final Object rawValue;
        public final double normalizedValue;
        public final double weight;
        public final double contribution;

        public FeatureContribution(String featureName, Object rawValue,
                double normalizedValue, double weight, double contribution) {
            this.featureName = featureName;
            this.rawValue = rawValue;
            this.normalizedValue = normalizedValue;
            this.weight = weight;
            this.contribution = contribution;
        }
    }

    public static class ScoreExplanation {
        public final Long txnId;
        public final Double mlScore;
        public final List<FeatureContribution> topContributors;
        public final List<String> reasons;
        public final long timestamp;

        public ScoreExplanation(Long txnId, Double mlScore,
                List<FeatureContribution> topContributors,
                List<String> reasons, long timestamp) {
            this.txnId = txnId;
            this.mlScore = mlScore;
            this.topContributors = topContributors;
            this.reasons = reasons;
            this.timestamp = timestamp;
        }
    }
}
