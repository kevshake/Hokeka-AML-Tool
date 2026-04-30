package com.posgateway.aml.service.kyc;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.risk.CustomerRiskProfilingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Periodic KYC Refresh Service
 * Scheduled service for risk-based KYC refresh
 */
@Service
public class PeriodicKycRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(PeriodicKycRefreshService.class);

    private final MerchantRepository merchantRepository;
    private final CustomerRiskProfilingService riskProfilingService;
    private final KycCompletenessService completenessService;

    @Value("${kyc.refresh.enabled:true}")
    private boolean refreshEnabled;

    @Value("${kyc.refresh.high-risk.days:90}")
    private int highRiskRefreshDays;

    @Value("${kyc.refresh.medium-risk.days:180}")
    private int mediumRiskRefreshDays;

    @Value("${kyc.refresh.low-risk.days:365}")
    private int lowRiskRefreshDays;

    @Autowired
    public PeriodicKycRefreshService(
            MerchantRepository merchantRepository,
            CustomerRiskProfilingService riskProfilingService,
            KycCompletenessService completenessService) {
        this.merchantRepository = merchantRepository;
        this.riskProfilingService = riskProfilingService;
        this.completenessService = completenessService;
    }

    /**
     * Scheduled task to refresh KYC based on risk
     */
    @Scheduled(cron = "${kyc.refresh.cron:0 0 2 * * *}") // Daily at 2 AM
    @Transactional
    public void performPeriodicKycRefresh() {
        if (!refreshEnabled) {
            logger.info("Periodic KYC refresh is disabled");
            return;
        }

        logger.info("Starting periodic KYC refresh...");

        List<Merchant> activeMerchants = merchantRepository.findByStatus("ACTIVE");
        int refreshed = 0;
        int skipped = 0;

        for (Merchant merchant : activeMerchants) {
            try {
                if (shouldRefreshKyc(merchant)) {
                    refreshMerchantKyc(merchant);
                    refreshed++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                logger.error("Error refreshing KYC for merchant {}: {}",
                        merchant.getMerchantId(), e.getMessage(), e);
            }
        }

        logger.info("KYC refresh complete: {} refreshed, {} skipped", refreshed, skipped);
    }

    /**
     * Check if merchant's KYC should be refreshed
     */
    private boolean shouldRefreshKyc(Merchant merchant) {
        // Get merchant risk level
        String riskLevel = merchant.getRiskLevel();
        if (riskLevel == null) {
            riskLevel = "MEDIUM"; // Default
        }

        // Get last KYC refresh date (use registration date or last screening as proxy)
        LocalDate lastRefreshDate = merchant.getRegistrationDate();
        if (lastRefreshDate == null) {
            // Never refreshed, should refresh
            return true;
        }

        // Calculate days since last refresh
        long daysSinceRefresh = java.time.temporal.ChronoUnit.DAYS.between(lastRefreshDate, LocalDate.now());

        // Determine refresh interval based on risk
        int refreshInterval = getRefreshIntervalForRisk(riskLevel);

        return daysSinceRefresh >= refreshInterval;
    }

    /**
     * Get refresh interval in days based on risk level
     */
    private int getRefreshIntervalForRisk(String riskLevel) {
        switch (riskLevel.toUpperCase()) {
            case "HIGH":
            case "CRITICAL":
                return highRiskRefreshDays;
            case "MEDIUM":
                return mediumRiskRefreshDays;
            case "LOW":
                return lowRiskRefreshDays;
            default:
                return mediumRiskRefreshDays;
        }
    }

    /**
     * Refresh merchant KYC
     */
    private void refreshMerchantKyc(Merchant merchant) {
        logger.info("Refreshing KYC for merchant {} (Risk: {})", 
                merchant.getMerchantId(), merchant.getRiskLevel());

        // Re-assess risk profile
        riskProfilingService.calculateRiskRating(String.valueOf(merchant.getMerchantId()));

        // Re-calculate completeness
        completenessService.calculateCompletenessScore(merchant.getMerchantId());

        // Update last refresh date (would need to add this field)
        // merchant.setLastKycRefreshDate(LocalDate.now());
        // merchantRepository.save(merchant);

        logger.debug("KYC refresh completed for merchant {}", merchant.getMerchantId());
    }

    /**
     * Manually trigger KYC refresh for a merchant
     */
    @Transactional
    public void refreshMerchantKycManually(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found: " + merchantId));

        refreshMerchantKyc(merchant);
    }

    /**
     * Get merchants requiring KYC refresh
     */
    public List<Merchant> getMerchantsRequiringRefresh() {
        return merchantRepository.findByStatus("ACTIVE").stream()
                .filter(this::shouldRefreshKyc)
                .collect(Collectors.toList());
    }
}

