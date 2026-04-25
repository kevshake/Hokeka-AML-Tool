package com.posgateway.aml.service;

import com.posgateway.aml.model.AerospikeTransaction;
import com.posgateway.aml.repository.AerospikeTransactionRepository;
import com.posgateway.aml.service.graph.AerospikeGraphCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-speed AML transaction lookup and XGBoost score retrieval via Aerospike.
 *
 * All reads bypass PostgreSQL. This is the sub-millisecond read path used by
 * real-time AML decisioning, compliance dashboards, and the scoring pipeline.
 */
@Service
public class AerospikeTransactionLookupService {

    private static final Logger logger = LoggerFactory.getLogger(AerospikeTransactionLookupService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AerospikeTransactionRepository txnRepo;
    private final AerospikeGraphCacheService graphCache;
    private final XGBoostExplainabilityService explainabilityService;
    private final ScoringService scoringService;
    private final FeatureExtractionService featureExtractionService;
    private final com.posgateway.aml.repository.TransactionRepository pgTxnRepo;

    @Autowired
    public AerospikeTransactionLookupService(
            AerospikeTransactionRepository txnRepo,
            @Autowired(required = false) AerospikeGraphCacheService graphCache,
            @Autowired(required = false) XGBoostExplainabilityService explainabilityService,
            @Autowired(required = false) ScoringService scoringService,
            @Autowired(required = false) FeatureExtractionService featureExtractionService,
            @Autowired(required = false) com.posgateway.aml.repository.TransactionRepository pgTxnRepo) {
        this.txnRepo = txnRepo;
        this.graphCache = graphCache;
        this.explainabilityService = explainabilityService;
        this.scoringService = scoringService;
        this.featureExtractionService = featureExtractionService;
        this.pgTxnRepo = pgTxnRepo;
    }

    // -------------------------------------------------------------------------
    // Single transaction lookup
    // -------------------------------------------------------------------------

    public TransactionLookupResult lookupTransaction(String txnId) {
        long t0 = System.currentTimeMillis();
        AerospikeTransaction txn = txnRepo.findById(txnId);
        if (txn == null) {
            logger.debug("Transaction {} not found in Aerospike", txnId);
            return null;
        }

        Map<String, Object> scoreData = null;
        Map<String, Object> riskDecision = null;
        Map<String, Object> graphMetrics = null;

        if (graphCache != null) {
            try {
                Long numericId = Long.parseLong(txnId);
                scoreData = graphCache.getXGBoostScore(numericId);
                riskDecision = graphCache.getRiskDecision(numericId);
            } catch (NumberFormatException ignored) {}
            if (txn.getMerchantId() != null) {
                graphMetrics = graphCache.getGraphMetrics(txn.getMerchantId());
            }
        }

        long latencyMs = System.currentTimeMillis() - t0;
        return new TransactionLookupResult(txn, scoreData, riskDecision, graphMetrics, latencyMs);
    }

    public Map<String, Object> lookupScore(Long txnId) {
        if (graphCache == null) return null;
        return graphCache.getXGBoostScore(txnId);
    }

    /**
     * Trigger on-demand XGBoost scoring for a transaction that has a PostgreSQL
     * record but no Aerospike cache entry yet (backfill path).
     */
    public ScoringService.ScoringResult scoreOnDemand(Long txnId) {
        if (scoringService == null || featureExtractionService == null || pgTxnRepo == null) {
            logger.warn("On-demand scoring unavailable — missing service dependencies");
            return null;
        }
        return pgTxnRepo.findById(txnId).map(entity -> {
            Map<String, Object> features = featureExtractionService.extractFeatures(entity);
            return scoringService.scoreTransaction(txnId, features);
        }).orElse(null);
    }

    public List<Map<String, Object>> getFeatureImportance() {
        if (explainabilityService == null) return List.of();
        return explainabilityService.getFeatureImportance();
    }

    // -------------------------------------------------------------------------
    // List lookups via secondary indexes
    // -------------------------------------------------------------------------

    public List<TransactionLookupResult> lookupByMerchant(String merchantId, String dateStr) {
        String date = (dateStr != null && !dateStr.isBlank()) ? dateStr : LocalDate.now().format(DATE_FMT);
        return enrichList(txnRepo.findByMerchantAndDate(merchantId, date));
    }

    public List<TransactionLookupResult> lookupByCard(String panHash, String dateStr) {
        String date = (dateStr != null && !dateStr.isBlank()) ? dateStr : LocalDate.now().format(DATE_FMT);
        return enrichList(txnRepo.findByCardAndDate(panHash, date));
    }

    public List<TransactionLookupResult> lookupByMerchantAndStatus(String merchantId, String status) {
        return enrichList(txnRepo.findByMerchantAndStatus(merchantId, status));
    }

    private List<TransactionLookupResult> enrichList(List<AerospikeTransaction> txns) {
        List<TransactionLookupResult> results = new ArrayList<>(txns.size());
        for (AerospikeTransaction txn : txns) {
            Map<String, Object> scoreData = null;
            if (graphCache != null) {
                try {
                    scoreData = graphCache.getXGBoostScore(Long.parseLong(txn.getTxnId()));
                } catch (NumberFormatException ignored) {}
            }
            results.add(new TransactionLookupResult(txn, scoreData, null, null, 0L));
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Result DTO
    // -------------------------------------------------------------------------

    public static class TransactionLookupResult {
        private final AerospikeTransaction transaction;
        private final Map<String, Object> xgboostScore;
        private final Map<String, Object> riskDecision;
        private final Map<String, Object> graphMetrics;
        private final long lookupLatencyMs;

        public TransactionLookupResult(AerospikeTransaction transaction,
                Map<String, Object> xgboostScore, Map<String, Object> riskDecision,
                Map<String, Object> graphMetrics, long lookupLatencyMs) {
            this.transaction = transaction;
            this.xgboostScore = xgboostScore;
            this.riskDecision = riskDecision;
            this.graphMetrics = graphMetrics;
            this.lookupLatencyMs = lookupLatencyMs;
        }

        public AerospikeTransaction getTransaction() { return transaction; }
        public Map<String, Object> getXgboostScore() { return xgboostScore; }
        public Map<String, Object> getRiskDecision() { return riskDecision; }
        public Map<String, Object> getGraphMetrics() { return graphMetrics; }
        public long getLookupLatencyMs() { return lookupLatencyMs; }

        public Double getMlScore() {
            if (xgboostScore == null) return null;
            Object v = xgboostScore.get("score");
            return v instanceof Number ? ((Number) v).doubleValue() : null;
        }

        public Map<String, Object> toSummaryMap() {
            Map<String, Object> map = new HashMap<>();
            if (transaction != null) {
                map.put("txnId",       transaction.getTxnId());
                map.put("merchantId",  transaction.getMerchantId());
                map.put("amount",      transaction.getAmount());
                map.put("currency",    transaction.getCurrency());
                map.put("status",      transaction.getStatus());
                map.put("countryCode", transaction.getCountryCode());
                map.put("timestamp",   transaction.getTimestamp());
            }
            if (xgboostScore != null) {
                map.put("mlScore",  xgboostScore.get("score"));
                map.put("scoredAt", xgboostScore.get("scoredAt"));
            }
            if (riskDecision != null) {
                map.put("riskDecision", riskDecision.get("decision"));
                map.put("finalScore",   riskDecision.get("finalScore"));
            }
            map.put("lookupLatencyMs", lookupLatencyMs);
            return map;
        }
    }
}
