package com.posgateway.aml.service;

import com.posgateway.aml.model.RiskAssessment;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.model.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Risk Assessment Service
 * Orchestrates AML and Fraud detection services to provide comprehensive risk
 * assessment
 */
@Service
public class RiskAssessmentService {

    private static final Logger logger = LoggerFactory.getLogger(RiskAssessmentService.class);

    private final AmlService amlService;
    private final FraudDetectionService fraudDetectionService;
    private final com.posgateway.aml.service.risk.RiskRulesEngine riskRulesEngine;
    private final com.posgateway.aml.repository.MerchantRepository merchantRepository;
    private final com.posgateway.aml.service.analytics.LinkAnalysisService linkAnalysisService;
    private final com.posgateway.aml.service.analytics.BehavioralProfilingService behavioralProfilingService;

    @Autowired
    public RiskAssessmentService(AmlService amlService,
            FraudDetectionService fraudDetectionService,
            com.posgateway.aml.service.risk.RiskRulesEngine riskRulesEngine,
            com.posgateway.aml.repository.MerchantRepository merchantRepository,
            com.posgateway.aml.service.analytics.LinkAnalysisService linkAnalysisService,
            com.posgateway.aml.service.analytics.BehavioralProfilingService behavioralProfilingService) {
        this.amlService = amlService;
        this.fraudDetectionService = fraudDetectionService;
        this.riskRulesEngine = riskRulesEngine;
        this.merchantRepository = merchantRepository;
        this.linkAnalysisService = linkAnalysisService;
        this.behavioralProfilingService = behavioralProfilingService;
    }

    /**
     * Perform comprehensive risk assessment combining AML and Fraud detection
     * 
     * @param transaction The transaction to assess
     * @return Combined RiskAssessment with AML and Fraud scores
     */
    public RiskAssessment assessRisk(Transaction transaction) {
        // ... (existing null checks)
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }

        logger.info("Starting comprehensive risk assessment for transaction: {}",
                transaction.getTransactionId());

        // Perform AML risk assessment
        RiskAssessment amlAssessment = amlService.assessAmlRisk(transaction);

        // Perform Fraud risk assessment
        RiskAssessment fraudAssessment = fraudDetectionService.assessFraudRisk(transaction);

        // Combine assessments
        RiskAssessment combinedAssessment = combineAssessments(amlAssessment, fraudAssessment);

        // Run Rule Engine
        com.posgateway.aml.entity.merchant.Merchant merchant = null;
        if (transaction.getMerchantId() != null) {
            merchant = merchantRepository.findById(Long.parseLong(transaction.getMerchantId())).orElse(null);
        }

        if (merchant != null) {
            // Advanced Analysis (Phase 11)
            // Advanced Analysis (Phase 11 & 29)
            java.util.List<String> linkedBlocked = linkAnalysisService.findLinkedBlockedEntities(transaction, merchant);
            boolean isLinked = !linkedBlocked.isEmpty();
            boolean isAnomaly = behavioralProfilingService.isAmountAnomaly(transaction);

            // Kenya Specific Logic (Stubbed for Rule Engine)
            // In a real system, these would come from specialized checks
            boolean isStructuring = transaction.getAmount().remainder(new java.math.BigDecimal("99000"))
                    .compareTo(java.math.BigDecimal.ZERO) == 0; // Simple example based on scenario
            boolean isThirdParty = false; // Stub: Would require name matching logic

            // Pass facts to engine
            java.util.Map<String, Object> extraFacts = new java.util.HashMap<>();
            extraFacts.put("isLinkedToBlocked", isLinked);
            extraFacts.put("isBehavioralAnomaly", isAnomaly);
            extraFacts.put("isStructuringSuspected", isStructuring);
            extraFacts.put("isThirdPartySuspected", isThirdParty);

            java.util.List<String> triggeredRules = riskRulesEngine.evaluateRisk(transaction, merchant, extraFacts);

            if (!triggeredRules.isEmpty()) {
                combinedAssessment.getRiskFactors().addAll(triggeredRules);
                // If critical rules triggered
                if (triggeredRules.contains("SANCTIONS_MATCH")
                        || triggeredRules.contains("MCC_MISMATCH_SUSPECTED_LAUNDERING")
                        || triggeredRules.contains("LINKED_TO_BLOCKED_ENTITY")
                        || triggeredRules.contains("PEP_TRANSACTION")) {
                    combinedAssessment.setAmlRiskLevel(RiskLevel.HIGH);
                }
            }

        }

        // Determine final decision
        String decision = determineDecision(combinedAssessment);
        combinedAssessment.setDecision(decision);

        logger.info("Risk assessment completed for transaction {}: Decision={}, AML Score={}, Fraud Score={}",
                transaction.getTransactionId(), decision,
                combinedAssessment.getAmlRiskScore(),
                combinedAssessment.getFraudScore());

        return combinedAssessment;
    }

    private RiskAssessment combineAssessments(RiskAssessment amlAssessment,
            RiskAssessment fraudAssessment) {
        // Null safety checks
        if (amlAssessment == null && fraudAssessment == null) {
            logger.warn("Both assessments are null, returning empty assessment");
            return new RiskAssessment();
        }
        if (amlAssessment == null) {
            logger.warn("AML assessment is null, using fraud assessment only");
            return fraudAssessment;
        }
        if (fraudAssessment == null) {
            logger.warn("Fraud assessment is null, using AML assessment only");
            return amlAssessment;
        }

        RiskAssessment combined = new RiskAssessment();
        combined.setTransactionId(amlAssessment.getTransactionId());
        combined.setAmlRiskScore(amlAssessment.getAmlRiskScore());
        combined.setFraudScore(fraudAssessment.getFraudScore());
        combined.setAmlRiskLevel(amlAssessment.getAmlRiskLevel());
        combined.setFraudRiskLevel(fraudAssessment.getFraudRiskLevel());

        // Combine risk factors - null safety
        if (amlAssessment.getRiskFactors() != null) {
            combined.getRiskFactors().addAll(amlAssessment.getRiskFactors());
        }
        if (fraudAssessment.getRiskFactors() != null) {
            combined.getRiskFactors().addAll(fraudAssessment.getRiskFactors());
        }

        return combined;
    }

    private String determineDecision(RiskAssessment assessment) {
        // Null safety check
        if (assessment == null) {
            logger.warn("Assessment is null, defaulting to APPROVED");
            return TransactionStatus.APPROVED.name();
        }

        // Decision logic based on risk levels using optimized switch for better
        // performance
        // High risk in either AML or Fraud triggers review
        RiskLevel amlLevel = assessment.getAmlRiskLevel();
        RiskLevel fraudLevel = assessment.getFraudRiskLevel();

        // Null safety for risk levels - default to LOW if null
        if (amlLevel == null) {
            logger.debug("AML risk level is null, defaulting to LOW");
            amlLevel = RiskLevel.LOW;
        }
        if (fraudLevel == null) {
            logger.debug("Fraud risk level is null, defaulting to LOW");
            fraudLevel = RiskLevel.LOW;
        }

        // Determine highest risk level (most critical)
        RiskLevel highestRisk = (amlLevel.ordinal() > fraudLevel.ordinal()) ? amlLevel : fraudLevel;

        // Use switch for better performance with enum
        return switch (highestRisk) {
            case HIGH -> TransactionStatus.FLAGGED.name();
            case MEDIUM -> TransactionStatus.UNDER_REVIEW.name();
            case LOW -> TransactionStatus.APPROVED.name();
            default -> TransactionStatus.UNDER_REVIEW.name();
        };
    }
}
