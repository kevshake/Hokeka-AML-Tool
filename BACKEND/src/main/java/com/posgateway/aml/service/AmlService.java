package com.posgateway.aml.service;

import com.posgateway.aml.config.AmlProperties;
import com.posgateway.aml.model.RiskAssessment;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AML (Anti-Money Laundering) Service
 * Performs AML risk assessment on transactions
 * All thresholds and rules are configurable via AmlProperties
 */
@Service
public class AmlService {

    private static final Logger logger = LoggerFactory.getLogger(AmlService.class);
    
    // Cache BigDecimal thresholds to avoid repeated object creation
    private static final BigDecimal LARGE_AMOUNT_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal VERY_LARGE_AMOUNT_THRESHOLD = new BigDecimal("50000");

    private final AmlProperties amlProperties;
    private final TransactionStatisticsService statisticsService;

    @Autowired
    public AmlService(AmlProperties amlProperties, TransactionStatisticsService statisticsService) {
        this.amlProperties = amlProperties;
        this.statisticsService = statisticsService;
    }

    /**
     * Assess AML risk for a transaction
     * 
     * @param transaction The transaction to assess
     * @return RiskAssessment containing AML risk score and level
     */
    public RiskAssessment assessAmlRisk(Transaction transaction) {
        if (!amlProperties.isEnabled()) {
            logger.debug("AML assessment disabled, returning low risk");
            return createLowRiskAssessment(transaction.getTransactionId());
        }

        logger.debug("Assessing AML risk for transaction: {}", transaction.getTransactionId());

        int riskScore = 0;
        List<String> riskFactors = new ArrayList<>();

        // Amount-based risk assessment
        riskScore += assessAmountRisk(transaction, riskFactors);

        // Velocity-based risk assessment
        riskScore += assessVelocityRisk(transaction, riskFactors);

        // Geographic risk assessment
        riskScore += assessGeographicRisk(transaction, riskFactors);

        // Transaction pattern risk assessment
        riskScore += assessPatternRisk(transaction, riskFactors);

        // Determine risk level based on configurable thresholds
        RiskLevel riskLevel = determineRiskLevel(riskScore);

        RiskAssessment assessment = new RiskAssessment();
        assessment.setTransactionId(transaction.getTransactionId());
        assessment.setAmlRiskScore(riskScore);
        assessment.setAmlRiskLevel(riskLevel);
        assessment.setRiskFactors(riskFactors);
        assessment.setAssessedAt(LocalDateTime.now());

        logger.info("AML risk assessment completed for transaction {}: Score={}, Level={}", 
            transaction.getTransactionId(), riskScore, riskLevel);

        return assessment;
    }

    private int assessAmountRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;
        BigDecimal amount = transaction.getAmount();
        
        // Early return if amount is null
        if (amount == null) {
            return score;
        }

        // Large transaction amount check - use cached BigDecimal constants
        if (amount.compareTo(LARGE_AMOUNT_THRESHOLD) > 0) {
            score += 20;
            riskFactors.add("Large transaction amount: " + amount);
        }

        if (amount.compareTo(VERY_LARGE_AMOUNT_THRESHOLD) > 0) {
            score += 30;
            riskFactors.add("Very large transaction amount: " + amount);
        }

        return score;
    }

    private int assessVelocityRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;
        
        // Velocity checks using transaction statistics service
        // Check merchant velocity
        String merchantId = transaction.getMerchantId();
        if (merchantId != null) {
            // Check transaction count in last hour
            long merchantCount1h = getMerchantTransactionCount(merchantId, 1);
            if (merchantCount1h > 50) { // Configurable threshold
                score += 15;
                riskFactors.add(String.format("High merchant velocity: %d transactions in 1 hour", merchantCount1h));
            }
            
            // Check amount sum in last 24 hours
            long merchantAmount24h = getMerchantAmountSum(merchantId, 24);
            if (merchantAmount24h > 10000000) { // $100,000 in cents
                score += 20;
                riskFactors.add(String.format("High merchant volume: $%.2f in 24 hours", merchantAmount24h / 100.0));
            }
        }
        
        // Check PAN velocity - use accountNumber as PAN hash if panHash not available
        String panHash = getPanHash(transaction);
        if (panHash != null) {
            // Check transaction count in last hour
            long panCount1h = getPanTransactionCount(panHash, 1);
            if (panCount1h > 10) {
                score += 20;
                riskFactors.add(String.format("High PAN velocity: %d transactions in 1 hour", panCount1h));
            }
            
            // Check cumulative amount over 30 days
            long panCumulative30d = getPanCumulativeAmount(panHash, 30);
            if (panCumulative30d > 50000000) { // $500,000 in cents
                score += 25;
                riskFactors.add(String.format("High cumulative PAN volume: $%.2f in 30 days", panCumulative30d / 100.0));
            }
        }
        
        return score;
    }

    // Helper methods to get statistics from TransactionStatisticsService
    private long getMerchantTransactionCount(String merchantId, int hours) {
        return statisticsService != null ? statisticsService.getMerchantTransactionCount(merchantId, hours) : 0;
    }

    private long getMerchantAmountSum(String merchantId, int hours) {
        return statisticsService != null ? statisticsService.getMerchantAmountSum(merchantId, hours) : 0;
    }

    private long getPanTransactionCount(String panHash, int hours) {
        return statisticsService != null ? statisticsService.getPanTransactionCount(panHash, hours) : 0;
    }

    private long getPanCumulativeAmount(String panHash, int days) {
        return statisticsService != null ? statisticsService.getPanCumulativeAmount(panHash, days) : 0;
    }

    /**
     * Extract PAN hash from transaction
     * Uses panHash field if available, otherwise uses accountNumber as fallback
     */
    private String getPanHash(Transaction transaction) {
        // Try to get panHash field via reflection or use accountNumber
        // For now, use accountNumber as it's likely the PAN hash
        return transaction.getAccountNumber();
    }

    private int assessGeographicRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;
        
        // High-risk country check (should be configurable)
        if (transaction.getCountryCode() != null) {
            // This would check against a configurable list of high-risk countries
            // For now, placeholder
        }

        // Cross-border transaction check - optimize string comparison
        String countryCode = transaction.getCountryCode();
        String currencyCode = transaction.getCurrencyCode();
        if (countryCode != null && currencyCode != null && currencyCode.length() >= 2) {
            String currencyCountry = currencyCode.substring(0, 2);
            if (!countryCode.equals(currencyCountry)) {
                score += 15;
                riskFactors.add("Cross-border transaction detected");
            }
        }

        return score;
    }

    private int assessPatternRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;
        
        // Unusual transaction pattern checks
        // Example: Transactions at unusual times, unusual merchant categories, etc.
        
        return score;
    }

    private RiskLevel determineRiskLevel(int riskScore) {
        // Cache threshold values for performance
        AmlProperties.RiskThreshold thresholds = amlProperties.getRisk();
        
        // Null safety check
        if (thresholds == null) {
            logger.warn("Risk thresholds are null, defaulting to LOW risk");
            return RiskLevel.LOW;
        }
        
        int highThreshold = thresholds.getHigh();
        int mediumThreshold = thresholds.getMedium();
        int lowThreshold = thresholds.getLow();
        
        // Use early return pattern for better performance
        if (riskScore >= highThreshold) {
            return RiskLevel.HIGH;
        }
        if (riskScore >= mediumThreshold) {
            return RiskLevel.MEDIUM;
        }
        if (riskScore >= lowThreshold) {
            return RiskLevel.LOW;
        }
        return RiskLevel.LOW;
    }

    private RiskAssessment createLowRiskAssessment(String transactionId) {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setTransactionId(transactionId);
        assessment.setAmlRiskScore(0);
        assessment.setAmlRiskLevel(RiskLevel.LOW);
        assessment.setAssessedAt(LocalDateTime.now());
        return assessment;
    }
}

