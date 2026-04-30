package com.posgateway.aml.service;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.service.DecisionEngine.DecisionResult;
import com.posgateway.aml.service.ScoringService.ScoringResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Fraud Detection Orchestrator
 * Orchestrates the complete fraud detection flow:
 * 1. Extract features
 * 2. Score transaction
 * 3. Make decision
 * 4. Return result
 */
@Service
public class FraudDetectionOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(FraudDetectionOrchestrator.class);

    private final FeatureExtractionService featureExtractionService;
    private final ScoringService scoringService;
    private final DecisionEngine decisionEngine;

    @Autowired
    public FraudDetectionOrchestrator(FeatureExtractionService featureExtractionService,
            ScoringService scoringService,
            DecisionEngine decisionEngine) {
        this.featureExtractionService = featureExtractionService;
        this.scoringService = scoringService;
        this.decisionEngine = decisionEngine;
    }

    /**
     * Process transaction through complete fraud detection pipeline
     * 
     * @param transaction Transaction to process
     * @return Fraud detection result with decision and score
     */
    @Transactional
    public FraudDetectionResult processTransaction(TransactionEntity transaction) {
        logger.info("Processing transaction {} through fraud detection pipeline",
                transaction.getTxnId());

        long startTime = System.currentTimeMillis();

        // Step 1: Extract features
        Map<String, Object> features = featureExtractionService.extractFeatures(transaction);
        logger.debug("Extracted {} features for transaction {}",
                features.size(), transaction.getTxnId());

        // Step 2: Score transaction
        ScoringResult scoringResult = scoringService.scoreTransaction(
                transaction.getTxnId(), features);
        Double score = scoringResult.getScore();

        // Step 3: Make decision
        DecisionResult decision = decisionEngine.evaluate(transaction, score, features);

        // Enrich risk details with stored Risk Scores (KRS, TRS, CRA)
        Map<String, Object> finalRiskDetails = scoringResult.getRiskDetails();
        if (finalRiskDetails == null) finalRiskDetails = new java.util.HashMap<>();
        
        if (transaction.getKrs() != null) finalRiskDetails.put("krsScore", transaction.getKrs());
        if (transaction.getTrs() != null) finalRiskDetails.put("trsScore", transaction.getTrs());
        if (transaction.getCra() != null) finalRiskDetails.put("craScore", transaction.getCra());
        finalRiskDetails.put("mlScore", score); // Ensure ML score is also there

        long latencyMs = System.currentTimeMillis() - startTime;

        FraudDetectionResult result = new FraudDetectionResult();
        result.setTxnId(transaction.getTxnId());
        result.setScore(score);
        result.setAction(decision.getAction());
        result.setReasons(decision.getReasons());
        result.setLatencyMs(latencyMs);
        result.setRiskDetails(finalRiskDetails);

        logger.info("Fraud detection completed for transaction {}: action={}, score={}, latency={}ms",
                transaction.getTxnId(), decision.getAction(), score, latencyMs);

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
        private java.util.Map<String, Object> riskDetails;

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

        public java.util.Map<String, Object> getRiskDetails() {
            return riskDetails;
        }

        public void setRiskDetails(java.util.Map<String, Object> riskDetails) {
            this.riskDetails = riskDetails;
        }
    }
}
