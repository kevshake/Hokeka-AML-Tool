package com.posgateway.aml.service;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.TransactionFeatures;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.TransactionFeaturesRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decision Engine
 * Applies configurable thresholds and rules from database
 * Determines actions: BLOCK, HOLD, ALERT, ALLOW
 */
@Service
public class DecisionEngine {

    private static final Logger logger = LoggerFactory.getLogger(DecisionEngine.class);

    private final ConfigService configService;
    private final AlertRepository alertRepository;
    private final TransactionFeaturesRepository featuresRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public DecisionEngine(ConfigService configService, 
                         AlertRepository alertRepository,
                         TransactionFeaturesRepository featuresRepository,
                         ObjectMapper objectMapper) {
        this.configService = configService;
        this.alertRepository = alertRepository;
        this.featuresRepository = featuresRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluate transaction and determine action
     * 
     * @param transaction Transaction entity
     * @param score Fraud score from ML model
     * @param features Feature map
     * @return Decision result
     */
    @Transactional
    public DecisionResult evaluate(TransactionEntity transaction, Double score, Map<String, Object> features) {
        logger.info("Evaluating transaction {} with score {}", transaction.getTxnId(), score);

        // Check hard rules first (before scoring)
        DecisionResult hardRuleResult = checkHardRules(transaction);
        if (hardRuleResult != null) {
            logger.info("Hard rule triggered for transaction {}: {}", 
                transaction.getTxnId(), hardRuleResult.getAction());
            saveFeaturesAndDecision(transaction, score, features, hardRuleResult);
            return hardRuleResult;
        }

        // Apply model-based thresholds (from database)
        Double blockThreshold = configService.getFraudBlockThreshold();
        Double holdThreshold = configService.getFraudHoldThreshold();

        // Optimize decision logic with early returns and cached thresholds
        // Cache threshold comparisons for better performance
        final boolean isBlock = score >= blockThreshold;
        final boolean isHold = score >= holdThreshold;
        
        // Early return for BLOCK (highest priority)
        if (isBlock) {
            List<String> reasons = new ArrayList<>();
            reasons.add(String.format("Score %.3f >= block threshold %.3f", score, blockThreshold));
            takeBlockAction(transaction, score, reasons);
            DecisionResult decision = new DecisionResult("BLOCK", score, reasons);
            checkAmlRules(transaction, features, decision, reasons);
            saveFeaturesAndDecision(transaction, score, features, decision);
            logger.info("Decision for transaction {}: {} (score={})", 
                transaction.getTxnId(), decision.getAction(), score);
            return decision;
        }
        
        // Check HOLD (medium priority)
        if (isHold) {
            List<String> reasons = new ArrayList<>();
            reasons.add(String.format("Score %.3f >= hold threshold %.3f", score, holdThreshold));
            takeHoldAction(transaction, score, reasons);
            DecisionResult decision = new DecisionResult("HOLD", score, reasons);
            checkAmlRules(transaction, features, decision, reasons);
            saveFeaturesAndDecision(transaction, score, features, decision);
            logger.info("Decision for transaction {}: {} (score={})", 
                transaction.getTxnId(), decision.getAction(), score);
            return decision;
        }
        
        // Default to ALLOW (lowest priority)
        List<String> reasons = new ArrayList<>();
        reasons.add(String.format("Score %.3f < hold threshold %.3f", score, holdThreshold));
        DecisionResult decision = new DecisionResult("ALLOW", score, reasons);

        // Check AML rules
        checkAmlRules(transaction, features, decision, reasons);
        saveFeaturesAndDecision(transaction, score, features, decision);
        logger.info("Decision for transaction {}: {} (score={})", 
            transaction.getTxnId(), decision.getAction(), score);
        return decision;
    }

    private DecisionResult checkHardRules(TransactionEntity transaction) {
        // Hard rules that block immediately (configurable from DB)
        if (!configService.isBlacklistEnabled()) {
            return null;
        }

        // Check PAN blacklist (would query blacklist table)
        // Check terminal blacklist (would query blacklist table)
        // Check high-risk MCC + high amount
        // These would be implemented with actual blacklist tables

        // Real-time sanctions screening (highest priority)
        DecisionResult sanctionsResult = checkSanctionsScreening(transaction);
        if (sanctionsResult != null) {
            return sanctionsResult;
        }

        return null; // No hard rule triggered
    }

    @Autowired(required = false)
    private com.posgateway.aml.service.sanctions.RealTimeTransactionScreeningService realTimeScreeningService;

    private DecisionResult checkSanctionsScreening(TransactionEntity transaction) {
        if (realTimeScreeningService == null) {
            return null; // Service not available
        }
        
        try {
            com.posgateway.aml.service.sanctions.RealTimeTransactionScreeningService.TransactionScreeningResult result = 
                realTimeScreeningService.screenTransaction(transaction);
            
            if (result.hasMatches() && result.shouldBlock()) {
                List<String> reasons = new ArrayList<>();
                reasons.add("SANCTIONS_MATCH: Transaction involves sanctioned entity");
                for (var match : result.getMatches()) {
                    reasons.add(String.format("Match: %s (%s) - %s", 
                        match.getScreenedName(), 
                        match.getEntityType(),
                        match.getScreeningResult().getStatus()));
                }
                
                logger.warn("Transaction {} blocked due to sanctions match: {}",
                    transaction.getTxnId(), reasons);
                
                return new DecisionResult("BLOCK", 1.0, reasons);
            }
        } catch (Exception e) {
            logger.error("Error during real-time sanctions screening for transaction {}: {}",
                transaction.getTxnId(), e.getMessage(), e);
            // Don't block on screening errors - fail open for availability
        }
        
        return null;
    }

    private void checkAmlRules(TransactionEntity transaction, Map<String, Object> features, 
                               DecisionResult decision, List<String> reasons) {
        Long amlThreshold = configService.getAmlHighValueThreshold();
        
        // Cache amountCents to avoid repeated method calls - early return if null
        Long amountCents = transaction.getAmountCents();
        if (amountCents == null) {
            return; // Cannot check AML rules without amount
        }
        
        // Cache threshold calculation
        final long thresholdValue = amlThreshold != null ? amlThreshold : Long.MAX_VALUE;
        
        // Check single transaction amount
        if (amountCents >= thresholdValue) {
            reasons.add(String.format("AML: Amount %d >= threshold %d", amountCents, thresholdValue));
            
            // Escalate to compliance - use equals for string comparison
            String currentAction = decision.getAction();
            if (!"BLOCK".equals(currentAction)) {
                decision.setAction("ALERT");
                reasons.add("AML escalation required");
            }
        }

        // Check cumulative amounts (from features) - optimize with early return
        Object cumulativeDebits = features.get("cumulative_debits_30d");
        if (cumulativeDebits == null) {
            return; // No cumulative data to check
        }
        
        // Optimize type check and calculation
        if (cumulativeDebits instanceof Number) {
            double cumulative = ((Number) cumulativeDebits).doubleValue();
            long cumulativeThreshold = thresholdValue * 10L; // 10x single transaction threshold
            if (cumulative >= cumulativeThreshold) {
                reasons.add(String.format("AML: Cumulative 30d amount %.2f >= threshold %d", 
                    cumulative, cumulativeThreshold));
                decision.setAction("ALERT");
            }
        }
    }

    private void takeBlockAction(TransactionEntity transaction, Double score, List<String> reasons) {
        // Block transaction - decline with response code
        transaction.setAcquirerResponse("05"); // Generic decline
        
        // Create alert
        createAlert(transaction, score, "BLOCK", String.join("; ", reasons));
        
        // Optionally add PAN to temporary blocklist
        // This would be implemented with a blocklist service
    }

    private void takeHoldAction(TransactionEntity transaction, Double score, List<String> reasons) {
        // Hold for review - soft decline or accept with flag
        transaction.setAcquirerResponse("01"); // Refer to card issuer
        
        // Create alert for manual review
        createAlert(transaction, score, "HOLD", String.join("; ", reasons));
    }

    private void createAlert(TransactionEntity transaction, Double score, String action, String reason) {
        Alert alert = new Alert();
        alert.setTxnId(transaction.getTxnId());
        alert.setScore(score);
        alert.setAction(action);
        alert.setReason(reason);
        alert.setStatus("open");
        
        alertRepository.save(alert);
        logger.info("Created alert for transaction {}: {} - {}", 
            transaction.getTxnId(), action, reason);
    }

    private void saveFeaturesAndDecision(TransactionEntity transaction, Double score, 
                                        Map<String, Object> features, DecisionResult decision) {
        try {
            TransactionFeatures txnFeatures = new TransactionFeatures();
            txnFeatures.setTxnId(transaction.getTxnId());
            txnFeatures.setScore(score);
            txnFeatures.setActionTaken(decision.getAction());
            
            // Store features as JSON
            String featureJson = objectMapper.writeValueAsString(features);
            txnFeatures.setFeatureJson(featureJson);
            
            featuresRepository.save(txnFeatures);
        } catch (Exception e) {
            logger.error("Failed to save transaction features for {}", transaction.getTxnId(), e);
        }
    }

    /**
     * Decision Result
     */
    public static class DecisionResult {
        private String action; // BLOCK, HOLD, ALERT, ALLOW
        private Double score;
        private List<String> reasons;

        public DecisionResult(String action, Double score, List<String> reasons) {
            this.action = action;
            this.score = score;
            this.reasons = reasons;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public List<String> getReasons() {
            return reasons;
        }

        public void setReasons(List<String> reasons) {
            this.reasons = reasons;
        }
    }
}

