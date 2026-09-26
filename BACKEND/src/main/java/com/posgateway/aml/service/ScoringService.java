package com.posgateway.aml.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.posgateway.aml.service.rules.RulesExecutionService rulesExecutionService;
    private final com.posgateway.aml.service.deeplearning.DL4JAnomalyService dl4jAnomalyService;
    private final com.posgateway.aml.client.aml.AmlMicroserviceClient amlMicroserviceClient;

    @Autowired(required = false)
    private com.posgateway.aml.service.ai.decision.AiEngineAdvisor aiEngineAdvisor;

    @Value("${scoring.service.enabled:true}")
    private boolean scoringEnabled;

    @Value("${hokeka.ai.fraud-scoring.borderline-low:0.35}")
    private double fraudScoringBorderlineLow;

    @Value("${hokeka.ai.fraud-scoring.borderline-high:0.70}")
    private double fraudScoringBorderlineHigh;

    @Value("${scoring.service.url:http://localhost:8000}")
    private String scoringServiceUrl;

    @Value("${scoring.service.retry.max:3}")
    private int maxRetries;

    @Value("${scoring.cache.enabled:true}")
    private boolean cacheEnabled;

    @Autowired
    public ScoringService(RestClientService restClientService,
            com.posgateway.aml.service.risk.SchemeSimulatorService schemeSimulatorService,
            RedisTemplate<String, Object> redisTemplate,
            com.posgateway.aml.service.rules.RulesExecutionService rulesExecutionService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.posgateway.aml.service.deeplearning.DL4JAnomalyService dl4jAnomalyService,
            com.posgateway.aml.client.aml.AmlMicroserviceClient amlMicroserviceClient) {
        this.restClientService = restClientService;
        this.schemeSimulatorService = schemeSimulatorService;
        this.redisTemplate = redisTemplate;
        this.rulesExecutionService = rulesExecutionService;
        this.dl4jAnomalyService = dl4jAnomalyService;
        this.amlMicroserviceClient = amlMicroserviceClient;
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
        // 0. L1 Cache: Check AML Microservice (Aerospike) first for sub-ms cache hits
        // This is the fastest path — returns cached risk score from Aerospike when available.
        if (amlMicroserviceClient != null) {
            String merchantId = (String) features.getOrDefault("merchant_id",
                    features.getOrDefault("merchantId", ""));
            String customerId = (String) features.getOrDefault("customer_id",
                    features.getOrDefault("customerId", ""));
            // The microservice namespaces its Aerospike cache by pspId and REQUIRES it: a
            // null pspId is a 400 that would both never hit the cache AND count as a failure
            // on the shared "amlMicroservice" circuit breaker (which also guards the velocity/
            // device/IP enrichment reads). pspId is carried in the feature map by
            // FeatureExtractionService; only attempt the L1 read when we actually have it.
            Long pspId = null;
            Object rawPspId = features.getOrDefault("pspId", features.get("psp_id"));
            if (rawPspId instanceof Number pspNum) {
                pspId = pspNum.longValue();
            }
            try {
                var resp = pspId == null ? null : amlMicroserviceClient.score(
                        new com.posgateway.aml.client.aml.AmlScoreRequest(
                                txnId != null ? "TXN-" + txnId : null,
                                pspId,
                                merchantId,
                                null, // amount — not needed for cache check
                                null, null, null, null, customerId, null));
                if (resp != null && resp.cacheLayer() != null
                        && "L1_AEROSPIKE".equals(resp.cacheLayer())) {
                    logger.debug("Aerospike L1 cache HIT for txn {} — score={}, indicators={}",
                            txnId, resp.riskScore(), resp.indicators());
                    Map<String, Object> riskDetails = new HashMap<>();
                    double cachedScore = resp.riskScore();
                    riskDetails.put("ml_score", cachedScore);
                    riskDetails.put("cache_layer", resp.cacheLayer());
                    riskDetails.put("source", resp.source());
                    riskDetails.put("model_explanation_status", "UNAVAILABLE_CACHE_RESULT");
                    if (resp.indicators() != null && !resp.indicators().isEmpty()) {
                        riskDetails.put("microservice_indicators", resp.indicators());
                        for (String ind : resp.indicators()) {
                            if ("SANCTIONS_FLAGGED".equals(ind)) {
                                riskDetails.put("sanctions_block", true);
                            } else if ("SANCTIONS_REVIEW".equals(ind)) {
                                riskDetails.put("sanctions_review", true);
                            }
                        }
                    }
                    com.posgateway.aml.rules.RuleEvaluationResult ruleResult =
                            rulesExecutionService.evaluateRules(txnId, features, cachedScore);
                    applyRuleResultToScore(ruleResult, riskDetails);
                    cachedScore = resolveScoreAfterRules(cachedScore, ruleResult);
                    maybeConsultAiForFraudScoring(txnId, cachedScore, features, riskDetails);
                    return new ScoringResult(txnId, cachedScore, resp.processingTimeMs(), riskDetails);
                }
            } catch (Exception e) {
                logger.debug("Aerospike L1 cache check failed for txn {}: {} — falling through",
                        txnId, e.getMessage());
            }
        }

        // 1. L2 Cache: Check Redis cache second
        if (cacheEnabled) {
            Object raw = redisTemplate.opsForValue().get("graph:xgboost:" + txnId);
            if (raw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cached = (Map<String, Object>) raw;
                Object scoreObj = cached.get("score");
                Object latencyObj = cached.get("latencyMs");
                Double cachedScore = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : null;
                Long cachedLatency = latencyObj instanceof Number ? ((Number) latencyObj).longValue() : 0L;
                Object detailsObj = cached.get("riskDetails");
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedRiskDetails = detailsObj instanceof Map
                        ? (Map<String, Object>) detailsObj : new HashMap<>();
                logger.debug("Cache HIT for txn {} - score: {}", txnId, cachedScore);
                return new ScoringResult(txnId, cachedScore, cachedLatency, cachedRiskDetails);
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
            logger.debug("Scoring service disabled, evaluating rules only");
            return evaluateRulesOnly(txnId, features, assessment);
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
                Double score = extractOptionalDouble(response.get("score"));
                if (score == null) {
                    throw new IllegalStateException("Scoring response did not contain a numeric score");
                }
                Long latencyMs = extractLong(response.get("latency_ms"));

                // Use our own latency if service doesn't provide it
                if (latencyMs == null || latencyMs == 0) {
                    latencyMs = System.currentTimeMillis() - startTime;
                }

                // Add ML score to riskDetails as requested
                riskDetails.put("ml_score", score);
                copyModelEvidence(response, riskDetails);

                // --- Unified rules engine (SpEL from DB + Drools DRL) ---
                com.posgateway.aml.rules.RuleEvaluationResult ruleResult =
                        rulesExecutionService.evaluateRules(txnId, features, score);

                applyRuleResultToScore(ruleResult, riskDetails);
                score = resolveScoreAfterRules(score, ruleResult);

                // --- DL4J DEEP ANOMALY DETECTION (PHASE 5) ---
                if (dl4jAnomalyService != null && dl4jAnomalyService.isEnabled()) {
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
                        riskDetails.put("anomaly_status", "UNAVAILABLE");
                        riskDetails.put("requires_enhanced_review", true);
                        forceHoldUnlessBlocked(riskDetails,
                                "ANOMALY_MODEL_UNAVAILABLE: manual review required");
                        score = Math.max(score, 0.85);
                    }
                }
                // -----------------------------------------------------

                // Cache result in Redis for fast subsequent lookups (5 min TTL)
                if (cacheEnabled) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("score", score);
                    entry.put("latencyMs", latencyMs);
                    entry.put("scoredAt", System.currentTimeMillis());
                    entry.put("riskDetails", riskDetails != null ? riskDetails : new HashMap<>());
                    redisTemplate.opsForValue().set("graph:xgboost:" + txnId, entry, Duration.ofMinutes(5));
                }

                logger.info("Transaction {} scored: score={}, latency={}ms", txnId, score, latencyMs);
                maybeConsultAiForFraudScoring(txnId, score, features, riskDetails);
                return new ScoringResult(txnId, score, latencyMs, riskDetails);
            }

            logger.warn("Empty response from scoring service for transaction {}", txnId);
            long latencyMs = System.currentTimeMillis() - startTime;
            return evaluateRulesWithScoringUnavailable(txnId, features, assessment, latencyMs);

        } catch (Exception e) {
            logger.error("Error scoring transaction {}: {}", txnId, e.getMessage());
            long latencyMs = System.currentTimeMillis() - startTime;
            return evaluateRulesWithScoringUnavailable(txnId, features, assessment, latencyMs);
        }
    }

    private ScoringResult evaluateRulesOnly(Long txnId, Map<String, Object> features,
            com.posgateway.aml.service.risk.SchemeSimulatorService.MerchantRiskAssessment assessment) {
        return evaluateRulesOnly(txnId, features, assessment, 0L);
    }

    private ScoringResult evaluateRulesOnly(Long txnId, Map<String, Object> features,
            com.posgateway.aml.service.risk.SchemeSimulatorService.MerchantRiskAssessment assessment,
            long latencyMs) {
        Map<String, Object> riskDetails = new HashMap<>();
        if (assessment != null) {
            riskDetails.putAll(assessment.toRiskDetails());
        }
        double score = 0.0;
        com.posgateway.aml.rules.RuleEvaluationResult ruleResult =
                rulesExecutionService.evaluateRules(txnId, features, score);
        applyRuleResultToScore(ruleResult, riskDetails);
        score = resolveScoreAfterRules(score, ruleResult);
        riskDetails.put("ml_score", score);
        riskDetails.put("model_explanation_status", "NOT_APPLICABLE_RULES_ONLY");
        return new ScoringResult(txnId, score, latencyMs, riskDetails);
    }

    private ScoringResult evaluateRulesWithScoringUnavailable(
            Long txnId,
            Map<String, Object> features,
            com.posgateway.aml.service.risk.SchemeSimulatorService.MerchantRiskAssessment assessment,
            long latencyMs) {
        ScoringResult rulesOnly = evaluateRulesOnly(txnId, features, assessment, latencyMs);
        Map<String, Object> riskDetails = rulesOnly.getRiskDetails();
        forceHoldUnlessBlocked(riskDetails, "ML_SCORING_UNAVAILABLE: manual review required");
        riskDetails.put("model_explanation_status", "UNAVAILABLE");
        return new ScoringResult(txnId, Math.max(rulesOnly.getScore(), 0.85), latencyMs, riskDetails);
    }

    private void copyModelEvidence(Map<String, Object> response, Map<String, Object> riskDetails) {
        copyIfPresent(response, riskDetails, "model_version");
        copyIfPresent(response, riskDetails, "explanation");
        copyIfPresent(response, riskDetails, "feature_contributions");
        copyIfPresent(response, riskDetails, "feature_importance");
        boolean explanationAvailable = riskDetails.containsKey("explanation")
                || riskDetails.containsKey("feature_contributions")
                || riskDetails.containsKey("feature_importance");
        riskDetails.put("model_explanation_status", explanationAvailable ? "AVAILABLE" : "UNAVAILABLE");
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private void forceHoldUnlessBlocked(Map<String, Object> riskDetails, String reason) {
        if (!"BLOCK".equals(riskDetails.get("rule_decision"))) {
            riskDetails.put("rule_decision", "HOLD");
        }
        java.util.List<String> reasons = riskDetails.get("rule_reasons") instanceof java.util.List<?>
                ? new java.util.ArrayList<>((java.util.List<String>) riskDetails.get("rule_reasons"))
                : new java.util.ArrayList<>();
        if (!reasons.contains(reason)) {
            reasons.add(reason);
        }
        riskDetails.put("rule_reasons", reasons);
    }

    private void applyRuleResultToScore(com.posgateway.aml.rules.RuleEvaluationResult ruleResult,
                                        Map<String, Object> riskDetails) {
        riskDetails.put("rule_decision", ruleResult.getDecision());
        riskDetails.put("rules_triggered", ruleResult.getTriggeredRules());
        riskDetails.put("rule_reasons", ruleResult.getReasons());
        riskDetails.put("sar_required", ruleResult.isSarRequired());
        riskDetails.put("ctr_required", ruleResult.isCtrRequired());
        riskDetails.put("regulatory_evidence", ruleResult.getRegulatoryEvidence());
        // Persist the summed rule score_impact so it is recorded on the transaction and available to
        // the decision/reporting layers (it was previously computed and thrown away).
        riskDetails.put("rule_score_total", ruleResult.getScoreImpact());
    }

    private void maybeConsultAiForFraudScoring(Long txnId,
                                                Double score,
                                                Map<String, Object> features,
                                                Map<String, Object> riskDetails) {
        if (aiEngineAdvisor == null || score == null || txnId == null) {
            return;
        }
        if (score < fraudScoringBorderlineLow || score >= fraudScoringBorderlineHigh) {
            return;
        }
        Map<String, Object> aiFeatures = new HashMap<>();
        aiFeatures.put("score", score);
        aiFeatures.put("borderlineBand", fraudScoringBorderlineLow + "-" + fraudScoringBorderlineHigh);
        if (features != null) {
            aiFeatures.putAll(features);
        }
        if (riskDetails != null) {
            aiFeatures.put("rule_decision", riskDetails.get("rule_decision"));
            aiFeatures.put("rules_triggered", riskDetails.get("rules_triggered"));
            aiFeatures.put("ml_score", riskDetails.get("ml_score"));
            aiFeatures.put("source", riskDetails.get("source"));
            aiFeatures.put("cache_layer", riskDetails.get("cache_layer"));
        }
        Long pspId = null;
        Object rawPspId = features != null ? features.getOrDefault("pspId", features.get("psp_id")) : null;
        if (rawPspId instanceof Number pspNum) {
            pspId = pspNum.longValue();
        }
        String baseline = riskDetails != null && riskDetails.get("rule_decision") != null
                ? String.valueOf(riskDetails.get("rule_decision"))
                : "REVIEW";
        aiEngineAdvisor.adviseAsync(
                com.posgateway.aml.service.ai.decision.AiEngineType.FRAUD_SCORING,
                pspId,
                baseline,
                aiFeatures,
                txnId,
                null,
                null,
                null);
    }

    private double resolveScoreAfterRules(double score, com.posgateway.aml.rules.RuleEvaluationResult ruleResult) {
        double resolved = score;
        if ("BLOCK".equals(ruleResult.getDecision())) {
            resolved = 1.0;
        } else if ("HOLD".equals(ruleResult.getDecision()) && score < 0.7) {
            resolved = 0.85;
        } else if ("REVIEW".equals(ruleResult.getDecision()) && score < 0.5) {
            resolved = 0.65;
        }
        // Blend the summed rule score_impact as a bounded, escalate-only risk contribution so a stack
        // of high-score_impact rules actually raises the outcome instead of the value being discarded.
        // Capped at +0.25 (100 points → 0.25) and can only raise the score, never lower it.
        double contribution = Math.min(0.25, Math.max(0.0, ruleResult.getScoreImpact()) / 400.0);
        return Math.min(1.0, Math.max(resolved, score + contribution));
    }

    private Double extractOptionalDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
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
