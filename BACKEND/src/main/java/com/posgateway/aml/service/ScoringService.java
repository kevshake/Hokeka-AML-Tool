package com.posgateway.aml.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Scoring Service
 * Calls external ML scoring service (XGBoost model) using REST Assured
 * Handles retries and timeout configuration
 */
@Service
public class ScoringService {

    private static final Logger logger = LoggerFactory.getLogger(ScoringService.class);

    private final RestClientService restClientService;
    private final com.posgateway.aml.service.risk.SchemeSimulatorService schemeSimulatorService;
    private final com.posgateway.aml.service.graph.AerospikeGraphCacheService aerospikeGraphCacheService;
    private final com.posgateway.aml.service.rules.DroolsRulesService droolsRulesService;
    private final com.posgateway.aml.service.deeplearning.DL4JAnomalyService dl4jAnomalyService;

    @Value("${scoring.service.enabled:true}")
    private boolean scoringEnabled;

    @Value("${scoring.service.url:http://localhost:8000}")
    private String scoringServiceUrl;

    @Value("${scoring.service.retry.max:3}")
    private int maxRetries;

    @Value("${scoring.cache.enabled:true}")
    private boolean cacheEnabled;

    @Autowired
    public ScoringService(RestClientService restClientService,
            com.posgateway.aml.service.risk.SchemeSimulatorService schemeSimulatorService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.posgateway.aml.service.graph.AerospikeGraphCacheService aerospikeGraphCacheService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.posgateway.aml.service.rules.DroolsRulesService droolsRulesService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.posgateway.aml.service.deeplearning.DL4JAnomalyService dl4jAnomalyService) {
        this.restClientService = restClientService;
        this.schemeSimulatorService = schemeSimulatorService;
        this.aerospikeGraphCacheService = aerospikeGraphCacheService;
        this.droolsRulesService = droolsRulesService;
        this.dl4jAnomalyService = dl4jAnomalyService;
    }

    /**
     * Score transaction using ML model
     * Optimized for high throughput with connection pooling
     *
     * @param txnId    Transaction ID
     * @param features Feature map
     * @return Scoring result with score and latency
     */
    public ScoringResult scoreTransaction(Long txnId, Map<String, Object> features) {
        // 0. Check Aerospike cache first for ultra-fast response
        if (cacheEnabled && aerospikeGraphCacheService != null) {
            Map<String, Object> cached = aerospikeGraphCacheService.getXGBoostScore(txnId);
            if (cached != null) {
                Double cachedScore = (Double) cached.get("score");
                Long cachedLatency = (Long) cached.get("latencyMs");
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedRiskDetails = (Map<String, Object>) cached.get("riskDetails");
                logger.debug("Cache HIT for txn {} - score: {}", txnId, cachedScore);
                return new ScoringResult(txnId, cachedScore, cachedLatency != null ? cachedLatency : 0L,
                        cachedRiskDetails != null ? cachedRiskDetails : new HashMap<>());
            }
        }

        // 1. Run Scheme Simulators (Local check)
        com.posgateway.aml.service.risk.SchemeSimulatorService.MerchantRiskAssessment assessment = null;
        try {
            String merchantId = (String) features.getOrDefault("merchant_id", "UNKNOWN");
            assessment = schemeSimulatorService.assessMerchant(merchantId);

            // Enrich features for XGBoost
            features.put("vfmp_stage", assessment.getVfmpResult().getStage().name());
            features.put("hecm_stage", assessment.getHecmResult().getStage().name());
            features.put("merchant_fraud_rate", assessment.getVfmpResult().getFraudRate());
            features.put("merchant_cb_ratio", assessment.getHecmResult().getRatio());

        } catch (Exception e) {
            logger.warn("Error running scheme simulators for txn {}: {}", txnId, e.getMessage());
        }

        if (!scoringEnabled) {
            logger.debug("Scoring service disabled, returning default score");
            return new ScoringResult(txnId, 0.0, 0L);
        }

        logger.debug("Scoring transaction {} with {} features", txnId, features.size());

        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("txn_id", txnId);
            payload.put("features", features);

            String scoringUrl = scoringServiceUrl + "/score";
            Map<String, Object> response = restClientService.postRequestWithRetry(
                    scoringUrl, payload, maxRetries);

            // Prepare Risk Details
            Map<String, Object> riskDetails = new HashMap<>();
            if (assessment != null) {
                riskDetails.putAll(assessment.toRiskDetails());
            }

            if (response != null) {
                Double score = extractDouble(response.get("score"));
                Long latencyMs = extractLong(response.get("latency_ms"));

                // Use our own latency if service doesn't provide it
                if (latencyMs == null || latencyMs == 0) {
                    latencyMs = System.currentTimeMillis() - startTime;
                }

                // Add ML score to riskDetails as requested
                riskDetails.put("ml_score", score);

                // --- KIE DROOLS RULES ENGINE INTEGRATION (PHASE 4) ---
                if (droolsRulesService != null) {
                    com.posgateway.aml.rules.RuleEvaluationResult ruleResult = droolsRulesService.evaluate(txnId,
                            features, score);

                    // Add rule details to risk response
                    riskDetails.put("rule_decision", ruleResult.getDecision());
                    riskDetails.put("rules_triggered", ruleResult.getTriggeredRules());
                    riskDetails.put("rule_reasons", ruleResult.getReasons());
                    riskDetails.put("sar_required", ruleResult.isSarRequired());

                    // CRITICAL: Regulatory Rules Override ML Score
                    if ("BLOCK".equals(ruleResult.getDecision())) {
                        score = 1.0; // Force High Risk
                        logger.warn("Regulatory Rule BLOCK override for txn {}: {}", txnId, ruleResult.getReasons());
                    } else if ("HOLD".equals(ruleResult.getDecision()) && score < 0.7) {
                        score = 0.85; // Force Review if Model missed it
                        logger.info("Regulatory Rule HOLD override for txn {}: {}", txnId, ruleResult.getReasons());
                    }
                }
                // -----------------------------------------------------

                // --- DL4J DEEP ANOMALY DETECTION (PHASE 5) ---
                if (dl4jAnomalyService != null) {
                    try {
                        com.posgateway.aml.service.deeplearning.DL4JAnomalyService.AnomalyResult anomalyResult = dl4jAnomalyService
                                .detectAnomaly(txnId, features);

                        riskDetails.put("anomaly_score", anomalyResult.getAnomalyScore());
                        riskDetails.put("is_anomaly", anomalyResult.isAnomaly());
                        riskDetails.put("anomaly_reason", anomalyResult.getReason());

                        if (anomalyResult.isAnomaly() && anomalyResult.getAnomalyScore() > 0.95) {
                            logger.warn("High Anomaly Score (Novel Pattern) detected for txn {}: {}", txnId,
                                    anomalyResult.getAnomalyScore());
                            if (!riskDetails.containsKey("rule_decision")
                                    || !"BLOCK".equals(riskDetails.get("rule_decision"))) {
                                // If not blocked by rules, mark for review
                                riskDetails.put("requires_enhanced_review", true);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("DL4J Anomaly Detection failed for txn {}", txnId, e);
                    }
                }
                // -----------------------------------------------------

                // Cache result in Aerospike for fast subsequent lookups
                if (cacheEnabled && aerospikeGraphCacheService != null) {
                    aerospikeGraphCacheService.cacheXGBoostScore(txnId, score, latencyMs, riskDetails);
                }

                logger.info("Transaction {} scored: score={}, latency={}ms", txnId, score, latencyMs);
                return new ScoringResult(txnId, score, latencyMs, riskDetails);
            }

            logger.warn("Empty response from scoring service for transaction {}", txnId);
            long latencyMs = System.currentTimeMillis() - startTime;

            riskDetails.put("ml_score", 0.0);
            return new ScoringResult(txnId, 0.0, latencyMs, riskDetails);

        } catch (Exception e) {
            logger.error("Error scoring transaction {}: {}", txnId, e.getMessage());
            long latencyMs = System.currentTimeMillis() - startTime;
            return new ScoringResult(txnId, 0.0, latencyMs);
        }
    }

    private Double extractDouble(Object value) {
        // Early return for null
        if (value == null) {
            return 0.0;
        }
        // Optimize instanceof check - Number is faster than individual type checks
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        // Fallback to string parsing only if not a Number
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Long extractLong(Object value) {
        // Early return for null
        if (value == null) {
            return 0L;
        }
        // Optimize instanceof check - Number is faster than individual type checks
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        // Fallback to string parsing only if not a Number
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Scoring Result
     */
    public static class ScoringResult {
        private final Long txnId;
        private final Double score;
        private final Long latencyMs;
        private final Map<String, Object> riskDetails;

        public ScoringResult(Long txnId, Double score, Long latencyMs) {
            this(txnId, score, latencyMs, new HashMap<>());
        }

        public ScoringResult(Long txnId, Double score, Long latencyMs, Map<String, Object> riskDetails) {
            this.txnId = txnId;
            this.score = score;
            this.latencyMs = latencyMs;
            this.riskDetails = riskDetails;
        }

        public Long getTxnId() {
            return txnId;
        }

        public Double getScore() {
            return score;
        }

        public Long getLatencyMs() {
            return latencyMs;
        }

        public Map<String, Object> getRiskDetails() {
            return riskDetails;
        }
    }
}
