package com.posgateway.aml.controller;

import com.posgateway.aml.client.aml.AmlMicroserviceClient;
import com.posgateway.aml.client.aml.AmlMicroserviceProperties;
import com.posgateway.aml.client.aml.AmlScoreRequest;
import com.posgateway.aml.client.aml.AmlScoreResponse;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.service.FeatureExtractionService;
import com.posgateway.aml.service.FraudDetectionOrchestrator;
import com.posgateway.aml.service.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * AML check endpoint.
 *
 * <p>POST {@code /api/v1/aml/check}
 *
 * <p>Architecture (post-Aerospike-removal): a request first hops to the standalone
 * {@code aml-microservice} (which owns Aerospike). On success the cached score is
 * returned with {@code cacheLayer=L1_AEROSPIKE}. If the microservice is disabled,
 * down, slow, or returns null (circuit-breaker tripped) we fall through to the
 * existing in-process {@link FraudDetectionOrchestrator} pipeline unchanged.
 */
@PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE_OFFICER','PSP_ADMIN','PSP_USER')")
@RestController
@RequestMapping("/aml")
public class AmlCheckController {

    private static final Logger logger = LoggerFactory.getLogger(AmlCheckController.class);

    private final FraudDetectionOrchestrator orchestrator;
    private final ScoringService scoringService;
    private final FeatureExtractionService featureExtractionService;
    private final AmlMicroserviceClient amlMicroserviceClient;
    private final AmlMicroserviceProperties amlMicroserviceProperties;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration LOCAL_SCORE_TTL = Duration.ofHours(1);

    @Autowired
    public AmlCheckController(
            @Autowired(required = false) FraudDetectionOrchestrator orchestrator,
            @Autowired(required = false) ScoringService scoringService,
            @Autowired(required = false) FeatureExtractionService featureExtractionService,
            AmlMicroserviceClient amlMicroserviceClient,
            AmlMicroserviceProperties amlMicroserviceProperties,
            RedisTemplate<String, Object> redisTemplate) {
        this.orchestrator = orchestrator;
        this.scoringService = scoringService;
        this.featureExtractionService = featureExtractionService;
        this.amlMicroserviceClient = amlMicroserviceClient;
        this.amlMicroserviceProperties = amlMicroserviceProperties;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> check(@RequestBody Map<String, Object> request) {
        long t0 = System.currentTimeMillis();

        String txnId = extractString(request, "txnId", "TXN-" + System.currentTimeMillis());
        String merchantId = extractString(request, "merchantId", "UNKNOWN");
        String currency   = extractString(request, "currency", "USD");
        String panHash    = extractString(request, "panHash", "");
        String country    = extractString(request, "countryCode", "");
        Long amountCents  = extractLong(request, "amountCents", 0L);
        Long pspId        = extractLong(request, "pspId", null);

        logger.info("AML check request: txnId={} pspId={} merchant={} amount={}",
                txnId, pspId, merchantId, amountCents);

        // 1. Layer-0 cache: hop to aml-microservice (only when explicitly enabled & pspId is present).
        if (amlMicroserviceProperties.isEnabled() && pspId != null) {
            try {
                AmlScoreRequest req = new AmlScoreRequest(
                        txnId,
                        pspId,
                        merchantId,
                        BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100)),
                        amountCents,
                        currency,
                        extractString(request, "transactionType", null),
                        country.isEmpty() ? null : country,
                        extractString(request, "customerId", null),
                        panHash.isEmpty() ? null : panHash);
                AmlScoreResponse msResp = amlMicroserviceClient.score(req);
                if (msResp != null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("txnId",      msResp.transactionId());
                    response.put("pspId",      msResp.pspId());
                    response.put("score",      msResp.riskScore());
                    response.put("decision",   msResp.decision());
                    response.put("riskLevel",  msResp.riskLevel());
                    response.put("reasons",    java.util.List.of("Score served from " + msResp.cacheLayer()));
                    response.put("cacheLayer", msResp.cacheLayer() != null ? msResp.cacheLayer() : "L1_AEROSPIKE");
                    response.put("latencyMs",  System.currentTimeMillis() - t0);
                    return ResponseEntity.ok(response);
                }
            } catch (Exception e) {
                logger.warn("aml-microservice score() threw, falling back to local pipeline: {}", e.getMessage());
            }
        }

        // Build a lightweight TransactionEntity for the in-process scoring pipeline
        TransactionEntity entity = new TransactionEntity();
        entity.setMerchantId(merchantId);
        entity.setCurrency(currency);
        entity.setPanHash(panHash.isEmpty() ? null : panHash);
        entity.setMerchantCountry(country.isEmpty() ? null : country);
        entity.setAmountCents(amountCents);
        entity.setTxnTs(LocalDateTime.now());

        Map<String, Object> response = new HashMap<>();
        response.put("txnId", txnId);
        response.put("cacheLayer", "LOCAL");

        // 2. Full scoring path: feature extraction → XGBoost → decision engine
        if (orchestrator != null) {
            try {
                FraudDetectionOrchestrator.FraudDetectionResult result =
                        orchestrator.processTransaction(entity);
                response.put("score",       result.getScore());
                response.put("decision",    result.getAction());
                response.put("reasons",     result.getReasons());
                response.put("riskDetails", result.getRiskDetails());
                response.put("latencyMs",   result.getLatencyMs());
            } catch (Exception e) {
                logger.error("FraudDetectionOrchestrator failed for txnId {}: {}", txnId, e.getMessage());
                response.put("score",     0.0);
                response.put("decision",  "ERROR");
                response.put("reasons",   java.util.List.of("Scoring pipeline error: " + e.getMessage()));
                response.put("latencyMs", System.currentTimeMillis() - t0);
            }
        } else if (scoringService != null && featureExtractionService != null) {
            try {
                Map<String, Object> features = featureExtractionService.extractFeatures(entity);
                @SuppressWarnings("unchecked")
                Map<String, Object> extraFeatures = (Map<String, Object>) request.getOrDefault("features", Map.of());
                features.putAll(extraFeatures);
                features.put("merchant_id", merchantId);

                ScoringService.ScoringResult scored = scoringService.scoreTransaction(
                        entity.getTxnId() != null ? entity.getTxnId() : 0L, features);
                response.put("score",       scored.getScore());
                response.put("decision",    deriveDecision(scored.getScore()));
                response.put("reasons",     java.util.List.of());
                response.put("riskDetails", scored.getRiskDetails());
                response.put("latencyMs",   scored.getLatencyMs());
            } catch (Exception e) {
                logger.error("ScoringService failed for txnId {}: {}", txnId, e.getMessage());
                response.put("score",     0.0);
                response.put("decision",  "ERROR");
                response.put("latencyMs", System.currentTimeMillis() - t0);
            }
        } else {
            response.put("score",     0.0);
            response.put("decision",  "APPROVED");
            response.put("reasons",   java.util.List.of("Scoring service unavailable"));
            response.put("latencyMs", System.currentTimeMillis() - t0);
        }

        // Writeback the local-pipeline score to Redis so the next call within
        // the TTL window short-circuits at the layer-0 cache. Best-effort —
        // a Redis outage MUST NOT break scoring.
        try {
            redisTemplate.opsForValue().set("aml:score:" + txnId, response, LOCAL_SCORE_TTL);
        } catch (Exception ex) {
            logger.debug("Redis writeback failed for txnId {}: {}", txnId, ex.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    private String deriveDecision(Double score) {
        if (score == null) return "APPROVED";
        if (score >= 0.85) return "DECLINED";
        if (score >= 0.5)  return "MANUAL_REVIEW";
        return "APPROVED";
    }

    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    private Long extractLong(Map<String, Object> map, String key, Long defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v != null) {
            try { return Long.parseLong(v.toString()); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
