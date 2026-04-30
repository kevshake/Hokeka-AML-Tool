package com.posgateway.aml.service;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.service.DecisionEngine.DecisionResult;
import com.posgateway.aml.service.ScoringService.ScoringResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Async Fraud Detection Orchestrator
 * High-throughput async version of fraud detection pipeline
 * Processes transactions asynchronously for maximum throughput
 */
@Service
public class AsyncFraudDetectionOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(AsyncFraudDetectionOrchestrator.class);

    private final FeatureExtractionService featureExtractionService;
    private final ScoringService scoringService;
    private final DecisionEngine decisionEngine;

    @Value("${throughput.enable.async.processing:true}")
    private boolean asyncEnabled;

    @Autowired
    public AsyncFraudDetectionOrchestrator(FeatureExtractionService featureExtractionService,
                                          ScoringService scoringService,
                                          DecisionEngine decisionEngine) {
        this.featureExtractionService = featureExtractionService;
        this.scoringService = scoringService;
        this.decisionEngine = decisionEngine;
    }

    /**
     * Process transaction asynchronously through complete fraud detection pipeline
     * 
     * @param transaction Transaction to process
     * @return CompletableFuture with fraud detection result
     */
    @Async("transactionExecutor")
    @CircuitBreaker(name = "fraudDetection", fallbackMethod = "fallbackProcessTransaction")
    public CompletableFuture<FraudDetectionResult> processTransactionAsync(TransactionEntity transaction) {
        logger.debug("Processing transaction {} asynchronously", transaction.getTxnId());

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Extract features (can be parallelized)
            CompletableFuture<Map<String, Object>> featuresFuture = 
                CompletableFuture.supplyAsync(() -> 
                    featureExtractionService.extractFeatures(transaction));

            // Step 2: Score transaction (depends on features)
            CompletableFuture<ScoringResult> scoringFuture = featuresFuture.thenCompose(features ->
                CompletableFuture.supplyAsync(() ->
                    scoringService.scoreTransaction(transaction.getTxnId(), features)));

            // Step 3: Make decision (depends on score)
            CompletableFuture<DecisionResult> decisionFuture = scoringFuture.thenCompose(scoringResult ->
                featuresFuture.thenCompose(features ->
                    CompletableFuture.supplyAsync(() ->
                        decisionEngine.evaluate(transaction, scoringResult.getScore(), features))));

            // Combine results
            return decisionFuture.thenCombine(scoringFuture, (decision, scoringResult) -> {
                long latencyMs = System.currentTimeMillis() - startTime;

                FraudDetectionResult result = new FraudDetectionResult();
                result.setTxnId(transaction.getTxnId());
                result.setScore(scoringResult.getScore());
                result.setAction(decision.getAction());
                result.setReasons(decision.getReasons());
                result.setLatencyMs(latencyMs);

                logger.debug("Async fraud detection completed for transaction {}: action={}, score={}, latency={}ms",
                    transaction.getTxnId(), decision.getAction(), scoringResult.getScore(), latencyMs);

                return result;
            });

        } catch (Exception e) {
            logger.error("Error in async fraud detection for transaction {}", transaction.getTxnId(), e);
            return CompletableFuture.completedFuture(createErrorResult(transaction.getTxnId()));
        }
    }

    /**
     * Fallback method when circuit breaker is open
     */
    @SuppressWarnings("unused")
    private CompletableFuture<FraudDetectionResult> fallbackProcessTransaction(
            TransactionEntity transaction, Exception ex) {
        logger.warn("Circuit breaker open, using fallback for transaction {}", transaction.getTxnId());
        return CompletableFuture.completedFuture(createErrorResult(transaction.getTxnId()));
    }

    private FraudDetectionResult createErrorResult(Long txnId) {
        FraudDetectionResult result = new FraudDetectionResult();
        result.setTxnId(txnId);
        result.setScore(0.0);
        result.setAction("ALLOW"); // Fail open for availability
        result.setLatencyMs(0L);
        return result;
    }

    /**
     * Fraud Detection Result
     */
    public static class FraudDetectionResult {
        private Long txnId;
        private Double score;
        private String action;
        private java.util.List<String> reasons;
        private Long latencyMs;

        // Getters and Setters
        public Long getTxnId() {
            return txnId;
        }

        public void setTxnId(Long txnId) {
            this.txnId = txnId;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public java.util.List<String> getReasons() {
            return reasons;
        }

        public void setReasons(java.util.List<String> reasons) {
            this.reasons = reasons;
        }

        public Long getLatencyMs() {
            return latencyMs;
        }

        public void setLatencyMs(Long latencyMs) {
            this.latencyMs = latencyMs;
        }
    }
}

