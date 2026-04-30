package com.posgateway.aml.service;

import com.posgateway.aml.config.FraudProperties;
import com.posgateway.aml.model.RiskAssessment;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fraud Detection Service
 * Performs fraud risk assessment on transactions
 * All thresholds and rules are configurable via FraudProperties
 */
@Service
public class FraudDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(FraudDetectionService.class);

    private final FraudProperties fraudProperties;

    @Autowired
    public FraudDetectionService(FraudProperties fraudProperties) {
        this.fraudProperties = fraudProperties;
    }

    /**
     * Assess fraud risk for a transaction
     * 
     * @param transaction The transaction to assess
     * @return RiskAssessment containing fraud risk score and level
     */
    public RiskAssessment assessFraudRisk(Transaction transaction) {
        if (!fraudProperties.isEnabled()) {
            logger.debug("Fraud detection disabled, returning low risk");
            return createLowRiskAssessment(transaction.getTransactionId());
        }

        logger.debug("Assessing fraud risk for transaction: {}", transaction.getTransactionId());

        int fraudScore = 0;
        List<String> riskFactors = new ArrayList<>();

        // Device fingerprint check
        fraudScore += assessDeviceRisk(transaction, riskFactors);

        // IP address risk check
        fraudScore += assessIpRisk(transaction, riskFactors);

        // Behavioral pattern check
        fraudScore += assessBehavioralRisk(transaction, riskFactors);

        // Velocity check (if enabled)
        if (fraudProperties.getVelocity().isCheckEnabled()) {
            fraudScore += assessVelocityRisk(transaction, riskFactors);
        }

        // Determine risk level based on configurable threshold
        RiskLevel riskLevel = determineRiskLevel(fraudScore);

        RiskAssessment assessment = new RiskAssessment();
        assessment.setTransactionId(transaction.getTransactionId());
        assessment.setFraudScore(fraudScore);
        assessment.setFraudRiskLevel(riskLevel);
        assessment.setRiskFactors(riskFactors);
        assessment.setAssessedAt(LocalDateTime.now());

        logger.info("Fraud risk assessment completed for transaction {}: Score={}, Level={}", 
            transaction.getTransactionId(), fraudScore, riskLevel);

        return assessment;
    }

    private int assessDeviceRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;
        
        // Device fingerprint validation - cache to avoid repeated method calls
        String deviceFingerprint = transaction.getDeviceFingerprint();
        if (deviceFingerprint == null || deviceFingerprint.isEmpty()) {
            score += 10;
            riskFactors.add("Missing device fingerprint");
        }

        // Device reputation check (would integrate with device intelligence service)
        // Placeholder for now
        
        return score;
    }

    private int assessIpRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;
        
        // IP address validation - cache to avoid repeated method calls
        String ipAddress = transaction.getIpAddress();
        if (ipAddress == null || ipAddress.isEmpty()) {
            score += 10;
            riskFactors.add("Missing IP address");
        }

        // IP reputation check (would integrate with IP intelligence service)
        // Placeholder for now
        
        return score;
    }

    private int assessBehavioralRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;
        
        // Behavioral pattern analysis
        // Example: Unusual transaction time, unusual merchant category, etc.
        
        return score;
    }

    private int assessVelocityRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;
        
        // Velocity check based on configurable window and max transactions
        // This would query the database for recent transactions
        // Placeholder for now
        
        return score;
    }

    private RiskLevel determineRiskLevel(int fraudScore) {
        // Cache threshold calculation for performance
        int threshold = fraudProperties.getScoring().getThreshold();
        int mediumThreshold = (int) (threshold * 0.7);
        
        // Use early return pattern for better performance
        if (fraudScore >= threshold) {
            return RiskLevel.HIGH;
        }
        if (fraudScore >= mediumThreshold) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private RiskAssessment createLowRiskAssessment(String transactionId) {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setTransactionId(transactionId);
        assessment.setFraudScore(0);
        assessment.setFraudRiskLevel(RiskLevel.LOW);
        assessment.setAssessedAt(LocalDateTime.now());
        return assessment;
    }
}

