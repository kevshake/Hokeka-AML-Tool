package com.posgateway.aml.service;

import com.posgateway.aml.config.AmlProperties;
import com.posgateway.aml.model.RiskAssessment;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    /**
     * FATF high-risk / blacklisted country codes (ISO 3166-1 alpha-2).
     * Used as a compile-time fallback when the high_risk_countries table is
     * unavailable (e.g. cold start before Flyway migration, test context).
     */
    private static final Set<String> FATF_HIGH_RISK_COUNTRIES = Set.of(
            "KP", "IR", "MM", "SY", "YE", "SD", "LY", "SO", "CF", "SS", "VE", "AF", "IQ", "ML", "BF"
    );

    private final AmlProperties amlProperties;
    private final TransactionStatisticsService statisticsService;
    private final HighRiskCountryRepository highRiskCountryRepository;

    @Autowired
    public AmlService(AmlProperties amlProperties,
                      TransactionStatisticsService statisticsService,
                      HighRiskCountryRepository highRiskCountryRepository) {
        this.amlProperties = amlProperties;
        this.statisticsService = statisticsService;
        this.highRiskCountryRepository = highRiskCountryRepository;
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
     * Extract PAN hash from transaction. {@link Transaction} doesn't expose a
     * dedicated {@code panHash} field — the upstream PSP submits the masked /
     * tokenized PAN in {@code accountNumber}, so that's what the velocity
     * counters key on. Returning a null/empty value disables PAN-level
     * aggregation for that transaction (fail-open by design — fraud rules
     * elsewhere still apply).
     */
    private String getPanHash(Transaction transaction) {
        String acct = transaction.getAccountNumber();
        return (acct == null || acct.isBlank()) ? null : acct;
    }

    private int assessGeographicRisk(Transaction transaction, List<String> riskFactors) {
        int score = 0;

        String countryCode = transaction.getCountryCode();

        // High-risk country check: DB-first with static FATF fallback.
        // Also sourced from the high_risk_countries table (FATF blacklist / greylist / regulator-flagged).
        if (countryCode != null) {
            String normalised = countryCode.toUpperCase();
            boolean highRisk = false;
            try {
                highRisk = highRiskCountryRepository.existsByCountryCode(normalised);
                if (highRisk) {
                    highRiskCountryRepository.findByCountryCode(normalised).ifPresent(c ->
                            riskFactors.add("High-risk jurisdiction: " + c.getCountryCode()));
                }
            } catch (Exception ex) {
                logger.warn("high_risk_countries lookup failed for {}: {}; using static FATF list",
                        normalised, ex.getMessage());
                highRisk = FATF_HIGH_RISK_COUNTRIES.contains(normalised);
            }
            if (highRisk) {
                score += 40;
                if (riskFactors.stream().noneMatch(f -> f.contains(normalised))) {
                    riskFactors.add("Transaction involves FATF high-risk country: " + normalised);
                }
            }
        }

        // Cross-border transaction check — currency's leading two chars are
        // the ISO 4217 country alpha-2 in the common case (e.g. USD→US, KES→KE).
        String currencyCode = transaction.getCurrencyCode();
        if (countryCode != null && currencyCode != null && currencyCode.length() >= 2) {
            String currencyCountry = currencyCode.substring(0, 2);
            if (!countryCode.equalsIgnoreCase(currencyCountry)) {
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

