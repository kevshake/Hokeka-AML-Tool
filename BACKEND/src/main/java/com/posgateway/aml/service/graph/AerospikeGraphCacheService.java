package com.posgateway.aml.service.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-process cache for Neo4j GDS graph metrics, XGBoost scores and final risk
 * decisions.
 *
 * <p>Historical name kept ({@code AerospikeGraphCacheService}). Aerospike was
 * relocated to the {@code aml-microservice}; this class now uses Caffeine.
 *
 * <p>TODO(aerospike-removal): the XGBoost score cache is a hot path (5 min TTL,
 * called per transaction). When the microservice path is healthy these scores
 * are served by it directly. Consider deleting this class once all call sites
 * are routed through AmlMicroserviceClient.
 */
@Service
public class AerospikeGraphCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeGraphCacheService.class);

    @Value("${aerospike.graph.cache.ttl:3600}")
    private int graphCacheTtlSeconds;

    @Value("${aerospike.xgboost.cache.ttl:300}")
    private int xgboostCacheTtlSeconds;

    private Cache<String, Map<String, Object>> graphMetrics;
    private Cache<Long, Map<String, Object>> xgboostScores;
    private Cache<Long, Map<String, Object>> riskDecisions;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AerospikeGraphCacheService() {
        // Build immediately with sane defaults; @Value injection happens after
        // construction so we re-init lazily on first use if values changed.
        this.graphMetrics  = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(50_000).build();
        this.xgboostScores = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).maximumSize(50_000).build();
        this.riskDecisions = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(24)).maximumSize(50_000).build();
    }

    // =========================================================================
    // GRAPH METRICS CACHING (from Neo4j GDS)
    // =========================================================================

    public void cacheGraphMetrics(String merchantId, Map<String, Object> metrics) {
        if (merchantId == null || metrics == null) return;
        Map<String, Object> snap = new HashMap<>(metrics);
        snap.put("updatedAt", System.currentTimeMillis());
        graphMetrics.put(merchantId, snap);
        logger.debug("Cached graph metrics for merchant {}", merchantId);
    }

    public Map<String, Object> getGraphMetrics(String merchantId) {
        if (merchantId == null) return null;
        Map<String, Object> v = graphMetrics.getIfPresent(merchantId);
        return v != null ? new HashMap<>(v) : null;
    }

    // =========================================================================
    // XGBOOST SCORE CACHING
    // =========================================================================

    public void cacheXGBoostScore(Long txnId, Double score, Long latencyMs, Map<String, Object> riskDetails) {
        if (txnId == null) return;
        Map<String, Object> entry = new HashMap<>();
        entry.put("score", score);
        entry.put("latencyMs", latencyMs);
        entry.put("scoredAt", System.currentTimeMillis());
        try {
            entry.put("riskDetails", riskDetails != null ? objectMapper.writeValueAsString(riskDetails) : "{}");
        } catch (Exception e) {
            entry.put("riskDetails", "{}");
        }
        xgboostScores.put(txnId, entry);
        logger.debug("Cached XGBoost score {} for txn {}", score, txnId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getXGBoostScore(Long txnId) {
        if (txnId == null) return null;
        Map<String, Object> v = xgboostScores.getIfPresent(txnId);
        if (v == null) return null;
        Map<String, Object> out = new HashMap<>(v);
        Object json = out.get("riskDetails");
        if (json instanceof String) {
            try {
                out.put("riskDetails", objectMapper.readValue((String) json, Map.class));
            } catch (Exception ignored) {}
        }
        return out;
    }

    // =========================================================================
    // RISK DECISION CACHING
    // =========================================================================

    public void cacheRiskDecision(Long txnId, String decision, Double finalScore,
                                  List<String> reasons, String triggeredRules) {
        if (txnId == null) return;
        Map<String, Object> entry = new HashMap<>();
        entry.put("decision", decision);
        entry.put("finalScore", finalScore);
        entry.put("reasons", reasons != null ? String.join("|", reasons) : "");
        entry.put("triggeredRules", triggeredRules);
        entry.put("decidedAt", System.currentTimeMillis());
        riskDecisions.put(txnId, entry);
        logger.info("Cached risk decision {} for txn {}", decision, txnId);
    }

    public Map<String, Object> getRiskDecision(Long txnId) {
        if (txnId == null) return null;
        Map<String, Object> v = riskDecisions.getIfPresent(txnId);
        return v != null ? new HashMap<>(v) : null;
    }

    public void bulkCacheGraphMetrics(Map<String, Map<String, Object>> merchantMetrics) {
        merchantMetrics.forEach(this::cacheGraphMetrics);
        logger.info("Bulk cached graph metrics for {} merchants", merchantMetrics.size());
    }
}
