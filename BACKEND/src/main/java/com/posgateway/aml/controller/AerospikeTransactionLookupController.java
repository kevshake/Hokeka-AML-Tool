package com.posgateway.aml.controller;

import com.posgateway.aml.service.AerospikeTransactionLookupService;
import com.posgateway.aml.service.AerospikeTransactionLookupService.TransactionLookupResult;
import com.posgateway.aml.service.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-speed Aerospike transaction lookup and XGBoost score endpoints.
 *
 * All reads go directly to Aerospike — no PostgreSQL involved.
 * Full URL (with context-path /api/v1): /api/v1/aerospike/transactions/...
 */
@RestController
@RequestMapping("/aerospike/transactions")
public class AerospikeTransactionLookupController {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeTransactionLookupController.class);

    private final AerospikeTransactionLookupService lookupService;

    @Autowired
    public AerospikeTransactionLookupController(AerospikeTransactionLookupService lookupService) {
        this.lookupService = lookupService;
    }

    /**
     * GET /api/v1/aerospike/transactions/{txnId}
     * Flat summary: transaction + cached score + risk decision.
     */
    @GetMapping("/{txnId}")
    public ResponseEntity<Map<String, Object>> getTransaction(@PathVariable String txnId) {
        TransactionLookupResult result = lookupService.lookupTransaction(txnId);
        if (result == null) {
            Map<String, Object> notFound = new HashMap<>();
            notFound.put("txnId", txnId);
            notFound.put("message", "Transaction not found in Aerospike cache");
            return ResponseEntity.status(404).body(notFound);
        }
        return ResponseEntity.ok(result.toSummaryMap());
    }

    /**
     * GET /api/v1/aerospike/transactions/{txnId}/full
     * Full nested response: transaction + score + decision + graph metrics.
     */
    @GetMapping("/{txnId}/full")
    public ResponseEntity<Map<String, Object>> getTransactionFull(@PathVariable String txnId) {
        TransactionLookupResult result = lookupService.lookupTransaction(txnId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("transaction",   result.getTransaction());
        body.put("xgboostScore",  result.getXgboostScore());
        body.put("riskDecision",  result.getRiskDecision());
        body.put("graphMetrics",  result.getGraphMetrics());
        body.put("lookupLatencyMs", result.getLookupLatencyMs());
        return ResponseEntity.ok(body);
    }

    /**
     * GET /api/v1/aerospike/transactions/{txnId}/score
     * Score-only. Returns 204 if score not yet cached.
     */
    @GetMapping("/{txnId}/score")
    public ResponseEntity<Map<String, Object>> getScore(@PathVariable String txnId) {
        try {
            Map<String, Object> score = lookupService.lookupScore(Long.parseLong(txnId));
            return score != null ? ResponseEntity.ok(score) : ResponseEntity.noContent().build();
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /api/v1/aerospike/transactions/{txnId}/score
     * On-demand scoring: fetches from PG, extracts features, calls XGBoost, caches result.
     */
    @PostMapping("/{txnId}/score")
    public ResponseEntity<Map<String, Object>> triggerScore(@PathVariable Long txnId) {
        ScoringService.ScoringResult result = lookupService.scoreOnDemand(txnId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("txnId",       result.getTxnId());
        body.put("score",       result.getScore());
        body.put("latencyMs",   result.getLatencyMs());
        body.put("riskDetails", result.getRiskDetails());
        body.put("cached",      true);
        return ResponseEntity.ok(body);
    }

    /**
     * GET /api/v1/aerospike/transactions/by-merchant/{merchantId}?date=yyyyMMdd
     * All transactions for a merchant on a given date (today if omitted).
     */
    @GetMapping("/by-merchant/{merchantId}")
    public ResponseEntity<List<Map<String, Object>>> getByMerchant(
            @PathVariable String merchantId,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(
                lookupService.lookupByMerchant(merchantId, date)
                        .stream().map(TransactionLookupResult::toSummaryMap).toList());
    }

    /**
     * GET /api/v1/aerospike/transactions/by-card/{panHash}?date=yyyyMMdd
     */
    @GetMapping("/by-card/{panHash}")
    public ResponseEntity<List<Map<String, Object>>> getByCard(
            @PathVariable String panHash,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(
                lookupService.lookupByCard(panHash, date)
                        .stream().map(TransactionLookupResult::toSummaryMap).toList());
    }

    /**
     * GET /api/v1/aerospike/transactions/by-merchant/{merchantId}/status/{status}
     */
    @GetMapping("/by-merchant/{merchantId}/status/{status}")
    public ResponseEntity<List<Map<String, Object>>> getByMerchantAndStatus(
            @PathVariable String merchantId,
            @PathVariable String status) {
        return ResponseEntity.ok(
                lookupService.lookupByMerchantAndStatus(merchantId, status)
                        .stream().map(TransactionLookupResult::toSummaryMap).toList());
    }

    /**
     * GET /api/v1/aerospike/transactions/model/feature-importance
     * XGBoost feature weights for compliance audit.
     */
    @GetMapping("/model/feature-importance")
    public ResponseEntity<List<Map<String, Object>>> getFeatureImportance() {
        return ResponseEntity.ok(lookupService.getFeatureImportance());
    }
}
