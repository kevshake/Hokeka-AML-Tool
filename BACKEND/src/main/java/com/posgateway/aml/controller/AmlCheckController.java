package com.posgateway.aml.controller;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.model.AerospikeTransaction;
import com.posgateway.aml.repository.AerospikeTransactionRepository;
import com.posgateway.aml.service.AerospikeTransactionLookupService;
import com.posgateway.aml.service.FeatureExtractionService;
import com.posgateway.aml.service.FraudDetectionOrchestrator;
import com.posgateway.aml.service.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * AML check endpoint.
 *
 * POST /api/v1/aml/check
 *
 * Accepts a transaction payload, runs it through the fraud detection pipeline
 * (feature extraction → XGBoost scoring via Aerospike cache → decision engine),
 * and returns a risk score + decision in <100 ms p99.
 */
@RestController
@RequestMapping("/aml")
public class AmlCheckController {

    private static final Logger logger = LoggerFactory.getLogger(AmlCheckController.class);

    private final AerospikeTransactionRepository aerospikeRepo;
    private final FraudDetectionOrchestrator orchestrator;
    private final ScoringService scoringService;
    private final FeatureExtractionService featureExtractionService;
    private final AerospikeTransactionLookupService lookupService;

    @Autowired
    public AmlCheckController(
            AerospikeTransactionRepository aerospikeRepo,
            @Autowired(required = false) FraudDetectionOrchestrator orchestrator,
            @Autowired(required = false) ScoringService scoringService,
            @Autowired(required = false) FeatureExtractionService featureExtractionService,
            @Autowired(required = false) AerospikeTransactionLookupService lookupService) {
        this.aerospikeRepo = aerospikeRepo;
        this.orchestrator = orchestrator;
        this.scoringService = scoringService;
        this.featureExtractionService = featureExtractionService;
        this.lookupService = lookupService;
    }

    /**
     * POST /api/v1/aml/check
     *
     * Request body (all fields optional except txnId):
     * {
     *   "txnId": "TXN-001",           // string — used as Aerospike key
     *   "merchantId": "MERCH-42",
     *   "amountCents": 125000,         // long, cents
     *   "currency": "USD",
     *   "panHash": "abc123...",
     *   "countryCode": "KE",
     *   "features": { ... }            // optional pre-computed feature overrides
     * }
     *
     * Response:
     * {
     *   "txnId": "TXN-001",
     *   "score": 0.73,
     *   "decision": "MANUAL_REVIEW",
     *   "reasons": [...],
     *   "latencyMs": 12,
     *   "riskDetails": { ... },
     *   "aerospikeCacheHit": false
     * }
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> check(@RequestBody Map<String, Object> request) {
        long t0 = System.currentTimeMillis();

        String txnId = extractString(request, "txnId", "TXN-" + System.currentTimeMillis());
        String merchantId = extractString(request, "merchantId", "UNKNOWN");
        String currency   = extractString(request, "currency", "USD");
        String panHash    = extractString(request, "panHash", "");
        String country    = extractString(request, "countryCode", "");
        Long amountCents  = extractLong(request, "amountCents", 0L);

        logger.info("AML check request: txnId={} merchant={} amount={}", txnId, merchantId, amountCents);

        // Build a lightweight TransactionEntity for the scoring pipeline
        TransactionEntity entity = new TransactionEntity();
        entity.setMerchantId(merchantId);
        entity.setCurrency(currency);
        entity.setPanHash(panHash.isEmpty() ? null : panHash);
        entity.setMerchantCountry(country.isEmpty() ? null : country);
        entity.setAmountCents(amountCents);
        entity.setTxnTs(LocalDateTime.now());

        Map<String, Object> response = new HashMap<>();
        response.put("txnId", txnId);
        boolean cacheHit = false;

        // 1. Try Aerospike cache lookup first (fastest path)
        if (lookupService != null) {
            try {
                Long numericId = Long.parseLong(txnId);
                Map<String, Object> cached = lookupService.lookupScore(numericId);
                if (cached != null) {
                    cacheHit = true;
                    response.put("score",      cached.get("score"));
                    response.put("decision",   deriveDecision((Double) cached.get("score")));
                    response.put("reasons",    java.util.List.of("Score retrieved from Aerospike cache"));
                    response.put("riskDetails", cached);
                    response.put("aerospikeCacheHit", true);
                    response.put("latencyMs",  System.currentTimeMillis() - t0);
                    return ResponseEntity.ok(response);
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2. Full scoring path: feature extraction → XGBoost → decision engine
        if (orchestrator != null) {
            try {
                FraudDetectionOrchestrator.FraudDetectionResult result =
                        orchestrator.processTransaction(entity);
                response.put("score",             result.getScore());
                response.put("decision",          result.getAction());
                response.put("reasons",           result.getReasons());
                response.put("riskDetails",       result.getRiskDetails());
                response.put("aerospikeCacheHit", cacheHit);
                response.put("latencyMs",         result.getLatencyMs());
            } catch (Exception e) {
                logger.error("FraudDetectionOrchestrator failed for txnId {}: {}", txnId, e.getMessage());
                response.put("score",     0.0);
                response.put("decision",  "ERROR");
                response.put("reasons",   java.util.List.of("Scoring pipeline error: " + e.getMessage()));
                response.put("latencyMs", System.currentTimeMillis() - t0);
            }
        } else if (scoringService != null && featureExtractionService != null) {
            // Fallback: direct scoring without orchestrator
            try {
                Map<String, Object> features = featureExtractionService.extractFeatures(entity);
                @SuppressWarnings("unchecked")
                Map<String, Object> extraFeatures = (Map<String, Object>) request.getOrDefault("features", Map.of());
                features.putAll(extraFeatures);
                features.put("merchant_id", merchantId);

                ScoringService.ScoringResult scored = scoringService.scoreTransaction(
                        entity.getTxnId() != null ? entity.getTxnId() : 0L, features);
                response.put("score",             scored.getScore());
                response.put("decision",          deriveDecision(scored.getScore()));
                response.put("reasons",           java.util.List.of());
                response.put("riskDetails",       scored.getRiskDetails());
                response.put("aerospikeCacheHit", cacheHit);
                response.put("latencyMs",         scored.getLatencyMs());
            } catch (Exception e) {
                logger.error("ScoringService failed for txnId {}: {}", txnId, e.getMessage());
                response.put("score",     0.0);
                response.put("decision",  "ERROR");
                response.put("latencyMs", System.currentTimeMillis() - t0);
            }
        } else {
            // No scoring available — return safe default
            response.put("score",             0.0);
            response.put("decision",          "APPROVED");
            response.put("reasons",           java.util.List.of("Scoring service unavailable"));
            response.put("aerospikeCacheHit", false);
            response.put("latencyMs",         System.currentTimeMillis() - t0);
        }

        // 3. Cache transaction in Aerospike for future lookups
        try {
            AerospikeTransaction at = new AerospikeTransaction();
            at.setTxnId(txnId);
            at.setMerchantId(merchantId);
            at.setCurrency(currency);
            at.setAccountNumber(panHash.isEmpty() ? null : panHash);
            at.setCountryCode(country.isEmpty() ? null : country);
            at.setAmount(BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100)));
            at.setStatus(String.valueOf(response.getOrDefault("decision", "PENDING")));
            at.setTimestamp(System.currentTimeMillis());

            String dateStr = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            at.setMerchantDateKey(merchantId + "#" + dateStr);
            at.setCardDateKey((panHash.isEmpty() ? "UNKNOWN" : panHash) + "#" + dateStr);
            at.setStatusCountryKey(at.getStatus() + "#" + (country.isEmpty() ? "UNKNOWN" : country));
            at.setMerchantStatusKey(merchantId + "#" + at.getStatus());
            aerospikeRepo.save(at);
        } catch (Exception e) {
            logger.warn("Failed to cache AML check result in Aerospike: {}", e.getMessage());
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
