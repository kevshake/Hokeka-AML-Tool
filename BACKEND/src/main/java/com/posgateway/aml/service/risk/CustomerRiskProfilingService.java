package com.posgateway.aml.service.risk;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.risk.HighRiskCountryRepository;
import com.posgateway.aml.service.cache.KycDataCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Customer Risk Profiling Service
 * Calculates customer risk ratings and triggers EDD
 */
@Service
public class CustomerRiskProfilingService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerRiskProfilingService.class);

    private final ComplianceCaseRepository caseRepository;
    private final TransactionRepository transactionRepository;
    private final KycDataCacheService kycCacheService; // Aerospike cache for fast lookups
    private final HighRiskCountryRepository highRiskCountryRepository;

    @Value("${risk.edd.threshold:0.7}")
    private double eddThreshold;

    @Value("${risk.high-value.threshold:50000}")
    private BigDecimal highValueThreshold;

    @Autowired
    public CustomerRiskProfilingService(ComplianceCaseRepository caseRepository,
            TransactionRepository transactionRepository,
            KycDataCacheService kycCacheService,
            HighRiskCountryRepository highRiskCountryRepository) {
        this.caseRepository = caseRepository;
        this.transactionRepository = transactionRepository;
        this.kycCacheService = kycCacheService;
        this.highRiskCountryRepository = highRiskCountryRepository;
    }

    /**
     * Calculate customer risk rating
     * Uses Aerospike cache for fast lookups
     */
    public CustomerRiskRating calculateRiskRating(String merchantId) {
        // Fast Aerospike cache lookup first
        try {
            Long merchantIdLong = Long.parseLong(merchantId);
            java.util.Map<String, Object> cachedRating = kycCacheService.getCachedRiskRating(merchantIdLong);
            if (cachedRating != null) {
                CustomerRiskRating rating = new CustomerRiskRating();
                rating.setMerchantId(merchantId);
                rating.setRiskLevel((String) cachedRating.get("riskLevel"));
                rating.setRiskScore(((Number) cachedRating.get("riskScore")).doubleValue());
                logger.debug("Risk rating from cache for merchant {}: {} ({})", merchantId,
                        rating.getRiskLevel(), rating.getRiskScore());
                return rating;
            }
        } catch (NumberFormatException e) {
            // Continue with database calculation
        }

        // Fallback to database calculation
        CustomerRiskRating rating = new CustomerRiskRating();
        rating.setMerchantId(merchantId);

        // Get case history
        // Get case history
        long caseCount = 0;
        long highPriorityCases = 0;
        try {
            Long merchantIdLong = Long.parseLong(merchantId);
            List<ComplianceCase> cases = caseRepository.findByMerchantId(merchantIdLong);
            caseCount = cases.size();
            highPriorityCases = cases.stream()
                    .filter(c -> c.getPriority().ordinal() >= 2)
                    .count();
        } catch (NumberFormatException e) {
            logger.debug("Merchant ID '{}' is not numeric, skipping case history lookup", merchantId);
        }

        // Get transaction history
        List<TransactionEntity> transactions = transactionRepository.findByMerchantId(merchantId);
        BigDecimal totalAmount = transactions.stream()
                .map(tx -> tx.getAmountCents() != null
                        ? BigDecimal.valueOf(tx.getAmountCents()).divide(new BigDecimal("100"))
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate risk score
        double riskScore = 0.0;

        // Case-based risk
        if (caseCount > 0) {
            riskScore += Math.min(caseCount * 0.2, 0.5); // Max 0.5 from cases
        }
        if (highPriorityCases > 0) {
            riskScore += Math.min(highPriorityCases * 0.3, 0.4); // Max 0.4 from high priority
        }

        // Transaction-based risk
        if (totalAmount.compareTo(highValueThreshold) > 0) {
            riskScore += 0.3;
        }

        // Determine risk level
        if (riskScore >= 0.7) {
            rating.setRiskLevel("HIGH");
        } else if (riskScore >= 0.4) {
            rating.setRiskLevel("MEDIUM");
        } else {
            rating.setRiskLevel("LOW");
        }

        rating.setRiskScore(riskScore);
        rating.setCaseCount(caseCount);
        rating.setHighPriorityCaseCount(highPriorityCases);
        rating.setTotalTransactionAmount(totalAmount);

        // Cache the result in Aerospike for future fast lookups
        try {
            Long merchantIdLong = Long.parseLong(merchantId);
            kycCacheService.cacheRiskRating(merchantIdLong, rating.getRiskLevel(), rating.getRiskScore());
        } catch (NumberFormatException e) {
            // Skip caching if merchantId is not a number
        }

        return rating;
    }

    /**
     * Check if EDD is required
     */
    public boolean isEddRequired(String merchantId) {
        CustomerRiskRating rating = calculateRiskRating(merchantId);
        return rating.getRiskScore() >= eddThreshold || rating.getRiskLevel().equals("HIGH");
    }

    /**
     * Calculate PEP risk score
     */
    public double calculatePepRiskScore(boolean isPep, String country) {
        double score = 0.0;

        if (isPep) {
            score += 0.5;
        }

        // Add country risk
        if (isHighRiskCountry(country)) {
            score += 0.3;
        }

        return Math.min(score, 1.0);
    }

    /**
     * Calculate geographic risk score
     */
    public double calculateGeographicRiskScore(String country) {
        // TODO: Implement based on country risk database
        return isHighRiskCountry(country) ? 0.5 : 0.1;
    }

    /**
     * Check if country is high risk
     */
    private boolean isHighRiskCountry(String country) {
        if (country == null)
            return false;
        return highRiskCountryRepository.existsByCountryCode(country);
    }

    /**
     * Customer Risk Rating DTO
     */
    public static class CustomerRiskRating {
        private String merchantId;
        private String riskLevel; // LOW, MEDIUM, HIGH
        private double riskScore;
        private long caseCount;
        private long highPriorityCaseCount;
        private BigDecimal totalTransactionAmount;
        private boolean eddRequired;

        // Getters and Setters
        public String getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public double getRiskScore() {
            return riskScore;
        }

        public void setRiskScore(double riskScore) {
            this.riskScore = riskScore;
        }

        public long getCaseCount() {
            return caseCount;
        }

        public void setCaseCount(long caseCount) {
            this.caseCount = caseCount;
        }

        public long getHighPriorityCaseCount() {
            return highPriorityCaseCount;
        }

        public void setHighPriorityCaseCount(long highPriorityCaseCount) {
            this.highPriorityCaseCount = highPriorityCaseCount;
        }

        public BigDecimal totalTransactionAmount() {
            return totalTransactionAmount;
        }

        public void setTotalTransactionAmount(BigDecimal totalTransactionAmount) {
            this.totalTransactionAmount = totalTransactionAmount;
        }

        public boolean isEddRequired() {
            return eddRequired;
        }

        public void setEddRequired(boolean eddRequired) {
            this.eddRequired = eddRequired;
        }
    }
}
