package com.posgateway.aml.service.graph;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import com.posgateway.aml.service.AerospikeConnectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Aerospike Cache Service for Graph Analytics and XGBoost Scores.
 * Provides ultra-fast read/write caching for:
 * - Neo4j GDS computed graph metrics (PageRank, Community, Betweenness)
 * - XGBoost ML scoring results
 * - Transaction risk assessments
 * 
 * Using Aerospike for speed: sub-millisecond reads for real-time decisioning.
 */
@Service
public class AerospikeGraphCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeGraphCacheService.class);

    // Aerospike sets for graph data
    private static final String SET_GRAPH_METRICS = "graph_metrics";
    private static final String SET_XGBOOST_SCORES = "xgboost_scores";
    private static final String SET_RISK_DECISIONS = "risk_decisions";

    private final AerospikeConnectionService aerospikeConnectionService;

    @Value("${aerospike.namespace:aml_fraud}")
    private String namespace;

    @Value("${aerospike.graph.cache.ttl:3600}")
    private int graphCacheTtlSeconds; // 1 hour default

    @Value("${aerospike.xgboost.cache.ttl:300}")
    private int xgboostCacheTtlSeconds; // 5 minutes default

    @Autowired
    public AerospikeGraphCacheService(AerospikeConnectionService aerospikeConnectionService) {
        this.aerospikeConnectionService = aerospikeConnectionService;
    }

    // =========================================================================
    // GRAPH METRICS CACHING (from Neo4j GDS)
    // =========================================================================

    /**
     * Cache graph metrics for a merchant (computed by Neo4j GDS).
     */
    public void cacheGraphMetrics(String merchantId, Map<String, Object> metrics) {
        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            if (client == null || !client.isConnected()) {
                logger.warn("Aerospike not connected, skipping graph metrics cache for {}", merchantId);
                return;
            }

            Key key = new Key(namespace, SET_GRAPH_METRICS, merchantId);
            WritePolicy policy = new WritePolicy();
            policy.expiration = graphCacheTtlSeconds;

            // Cast to specific types - Aerospike Bin requires explicit types
            double pageRankVal = metrics.get("pageRank") != null ? ((Number) metrics.get("pageRank")).doubleValue()
                    : 0.0;
            long communityIdVal = metrics.get("communityId") != null ? ((Number) metrics.get("communityId")).longValue()
                    : 0L;
            double betweennessVal = metrics.get("betweenness") != null
                    ? ((Number) metrics.get("betweenness")).doubleValue()
                    : 0.0;
            long connectionCountVal = metrics.get("connectionCount") != null
                    ? ((Number) metrics.get("connectionCount")).longValue()
                    : 0L;
            double clusterCoeffVal = metrics.get("clusteringCoefficient") != null
                    ? ((Number) metrics.get("clusteringCoefficient")).doubleValue()
                    : 0.0;

            Bin pageRank = new Bin("pageRank", pageRankVal);
            Bin communityId = new Bin("communityId", communityIdVal);
            Bin betweenness = new Bin("betweenness", betweennessVal);
            Bin connectionCount = new Bin("connectionCount", connectionCountVal);
            Bin clusterCoeff = new Bin("clusterCoeff", clusterCoeffVal);
            Bin updatedAt = new Bin("updatedAt", System.currentTimeMillis());

            client.put(policy, key, pageRank, communityId, betweenness, connectionCount, clusterCoeff, updatedAt);
            logger.debug("Cached graph metrics for merchant {} in Aerospike", merchantId);

        } catch (Exception e) {
            logger.error("Error caching graph metrics for {}: {}", merchantId, e.getMessage());
        }
    }

    /**
     * Retrieve cached graph metrics for a merchant.
     * Returns null if not cached or expired.
     */
    public Map<String, Object> getGraphMetrics(String merchantId) {
        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            if (client == null || !client.isConnected()) {
                return null;
            }

            Key key = new Key(namespace, SET_GRAPH_METRICS, merchantId);
            Record record = client.get(null, key);

            if (record != null) {
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("pageRank", record.getDouble("pageRank"));
                metrics.put("communityId", record.getLong("communityId"));
                metrics.put("betweenness", record.getDouble("betweenness"));
                metrics.put("connectionCount", record.getLong("connectionCount"));
                metrics.put("clusteringCoefficient", record.getDouble("clusterCoeff"));
                metrics.put("cachedAt", record.getLong("updatedAt"));
                logger.debug("Retrieved cached graph metrics for merchant {}", merchantId);
                return metrics;
            }
        } catch (Exception e) {
            logger.error("Error retrieving graph metrics for {}: {}", merchantId, e.getMessage());
        }
        return null;
    }

    // =========================================================================
    // XGBOOST SCORE CACHING
    // =========================================================================

    /**
     * Cache XGBoost scoring result for a transaction.
     */
    public void cacheXGBoostScore(Long txnId, Double score, Long latencyMs, Map<String, Object> riskDetails) {
        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            if (client == null || !client.isConnected()) {
                logger.warn("Aerospike not connected, skipping XGBoost score cache for txn {}", txnId);
                return;
            }

            Key key = new Key(namespace, SET_XGBOOST_SCORES, txnId.toString());
            WritePolicy policy = new WritePolicy();
            policy.expiration = xgboostCacheTtlSeconds;

            Bin scoreBin = new Bin("score", score);
            Bin latencyBin = new Bin("latencyMs", latencyMs);
            Bin timestampBin = new Bin("scoredAt", System.currentTimeMillis());

            // Serialize risk details as JSON string
            String riskDetailsJson = riskDetails != null
                    ? new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(riskDetails)
                    : "{}";
            Bin riskDetailsBin = new Bin("riskDetails", riskDetailsJson);

            client.put(policy, key, scoreBin, latencyBin, timestampBin, riskDetailsBin);
            logger.debug("Cached XGBoost score {} for txn {} in Aerospike (latency: {}ms)", score, txnId, latencyMs);

        } catch (Exception e) {
            logger.error("Error caching XGBoost score for txn {}: {}", txnId, e.getMessage());
        }
    }

    /**
     * Retrieve cached XGBoost score for a transaction.
     */
    public Map<String, Object> getXGBoostScore(Long txnId) {
        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            if (client == null || !client.isConnected()) {
                return null;
            }

            Key key = new Key(namespace, SET_XGBOOST_SCORES, txnId.toString());
            Record record = client.get(null, key);

            if (record != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("score", record.getDouble("score"));
                result.put("latencyMs", record.getLong("latencyMs"));
                result.put("scoredAt", record.getLong("scoredAt"));

                // Deserialize risk details
                String riskDetailsJson = record.getString("riskDetails");
                if (riskDetailsJson != null && !riskDetailsJson.isEmpty()) {
                    result.put("riskDetails", new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(riskDetailsJson, Map.class));
                }

                logger.debug("Cache HIT for XGBoost score txn {}", txnId);
                return result;
            }
        } catch (Exception e) {
            logger.error("Error retrieving XGBoost score for txn {}: {}", txnId, e.getMessage());
        }
        return null;
    }

    // =========================================================================
    // RISK DECISION CACHING (for audit and fast retrieval)
    // =========================================================================

    /**
     * Cache final risk decision for a transaction.
     */
    public void cacheRiskDecision(Long txnId, String decision, Double finalScore,
            java.util.List<String> reasons, String triggeredRules) {
        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            if (client == null || !client.isConnected()) {
                return;
            }

            Key key = new Key(namespace, SET_RISK_DECISIONS, txnId.toString());
            WritePolicy policy = new WritePolicy();
            policy.expiration = 86400; // 24 hours for audit purposes

            Bin decisionBin = new Bin("decision", decision);
            Bin scoreBin = new Bin("finalScore", finalScore);
            Bin reasonsBin = new Bin("reasons", String.join("|", reasons));
            Bin rulesBin = new Bin("triggeredRules", triggeredRules);
            Bin timestampBin = new Bin("decidedAt", System.currentTimeMillis());

            client.put(policy, key, decisionBin, scoreBin, reasonsBin, rulesBin, timestampBin);
            logger.info("Cached risk decision {} for txn {} in Aerospike", decision, txnId);

        } catch (Exception e) {
            logger.error("Error caching risk decision for txn {}: {}", txnId, e.getMessage());
        }
    }

    /**
     * Retrieve cached risk decision for a transaction.
     */
    public Map<String, Object> getRiskDecision(Long txnId) {
        try {
            AerospikeClient client = aerospikeConnectionService.getClient();
            if (client == null || !client.isConnected()) {
                return null;
            }

            Key key = new Key(namespace, SET_RISK_DECISIONS, txnId.toString());
            Record record = client.get(null, key);

            if (record != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("decision", record.getString("decision"));
                result.put("finalScore", record.getDouble("finalScore"));
                result.put("reasons", record.getString("reasons"));
                result.put("triggeredRules", record.getString("triggeredRules"));
                result.put("decidedAt", record.getLong("decidedAt"));
                return result;
            }
        } catch (Exception e) {
            logger.error("Error retrieving risk decision for txn {}: {}", txnId, e.getMessage());
        }
        return null;
    }

    // =========================================================================
    // BATCH OPERATIONS FOR HIGH THROUGHPUT
    // =========================================================================

    /**
     * Bulk cache graph metrics for multiple merchants.
     */
    public void bulkCacheGraphMetrics(Map<String, Map<String, Object>> merchantMetrics) {
        merchantMetrics.forEach(this::cacheGraphMetrics);
        logger.info("Bulk cached graph metrics for {} merchants in Aerospike", merchantMetrics.size());
    }
}
