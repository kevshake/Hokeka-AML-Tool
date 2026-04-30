package com.posgateway.aml.service.kyc;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.risk.CustomerRiskProfilingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Trigger-Based KYC Update Service
 * Triggers KYC updates based on risk changes or large transactions
 */
@Service
public class TriggerBasedKycService {

    private static final Logger logger = LoggerFactory.getLogger(TriggerBasedKycService.class);

    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerRiskProfilingService riskProfilingService;
    private final PeriodicKycRefreshService periodicRefreshService;

    @Value("${kyc.trigger.large-transaction.threshold:50000}")
    private BigDecimal largeTransactionThreshold;

    @Value("${kyc.trigger.risk-change.enabled:true}")
    private boolean riskChangeTriggerEnabled;

    @Value("${kyc.trigger.volume-increase.percent:50}")
    private int volumeIncreasePercent;

    @Autowired
    public TriggerBasedKycService(
            MerchantRepository merchantRepository,
            TransactionRepository transactionRepository,
            CustomerRiskProfilingService riskProfilingService,
            PeriodicKycRefreshService periodicRefreshService) {
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
        this.riskProfilingService = riskProfilingService;
        this.periodicRefreshService = periodicRefreshService;
    }

    /**
     * Check if transaction should trigger KYC update
     */
    @Transactional
    public boolean checkAndTriggerKycUpdate(TransactionEntity transaction) {
        if (transaction.getMerchantId() == null) {
            return false;
        }

        try {
            Long merchantIdLong = Long.parseLong(transaction.getMerchantId());
            Merchant merchant = merchantRepository.findById(merchantIdLong).orElse(null);
            
            if (merchant == null) {
                return false;
            }

            List<String> triggers = new ArrayList<>();

            // Trigger 1: Large transaction
            if (transaction.getAmountCents() != null) {
                BigDecimal amount = BigDecimal.valueOf(transaction.getAmountCents()).divide(BigDecimal.valueOf(100));
                if (amount.compareTo(largeTransactionThreshold) >= 0) {
                    triggers.add("LARGE_TRANSACTION: " + amount);
                    triggerKycUpdate(merchant, "Large transaction detected: " + amount);
                    return true;
                }
            }

            // Trigger 2: Volume increase
            if (checkVolumeIncrease(merchant)) {
                triggers.add("VOLUME_INCREASE");
                triggerKycUpdate(merchant, "Significant volume increase detected");
                return true;
            }

            // Trigger 3: Risk level change
            if (riskChangeTriggerEnabled) {
                String previousRiskLevel = merchant.getRiskLevel();
                com.posgateway.aml.service.risk.CustomerRiskProfilingService.CustomerRiskRating rating = 
                    riskProfilingService.calculateRiskRating(String.valueOf(merchant.getMerchantId()));
                merchant = merchantRepository.findById(merchantIdLong).orElse(null);
                if (merchant != null) {
                    String newRiskLevel = rating.getRiskLevel();
                    if (previousRiskLevel != null && !previousRiskLevel.equals(newRiskLevel)) {
                        triggers.add("RISK_LEVEL_CHANGE: " + previousRiskLevel + " -> " + newRiskLevel);
                        triggerKycUpdate(merchant, "Risk level changed from " + previousRiskLevel + " to " + newRiskLevel);
                        return true;
                    }
                }
            }

            return false;
        } catch (NumberFormatException e) {
            logger.debug("Merchant ID {} is not a valid number", transaction.getMerchantId());
            return false;
        }
    }

    /**
     * Check for significant volume increase
     */
    private boolean checkVolumeIncrease(Merchant merchant) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastMonth = now.minusMonths(1);
        LocalDateTime thisMonthStart = now.minusDays(30);

        // Get transaction volumes
        List<TransactionEntity> lastMonthTxs = transactionRepository.findByMerchantIdAndTimestampBetween(
                merchant.getMerchantId().toString(), lastMonth, thisMonthStart);
        List<TransactionEntity> thisMonthTxs = transactionRepository.findByMerchantIdAndTimestampBetween(
                merchant.getMerchantId().toString(), thisMonthStart, now);

        if (lastMonthTxs.isEmpty() || thisMonthTxs.isEmpty()) {
            return false;
        }

        BigDecimal lastMonthVolume = lastMonthTxs.stream()
                .filter(tx -> tx.getAmountCents() != null)
                .map(tx -> BigDecimal.valueOf(tx.getAmountCents()).divide(BigDecimal.valueOf(100)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal thisMonthVolume = thisMonthTxs.stream()
                .filter(tx -> tx.getAmountCents() != null)
                .map(tx -> BigDecimal.valueOf(tx.getAmountCents()).divide(BigDecimal.valueOf(100)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (lastMonthVolume.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        // Calculate percentage increase
        BigDecimal increase = thisMonthVolume.subtract(lastMonthVolume);
        BigDecimal percentIncrease = increase.divide(lastMonthVolume, 2, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return percentIncrease.compareTo(BigDecimal.valueOf(volumeIncreasePercent)) >= 0;
    }

    /**
     * Trigger KYC update for merchant
     */
    private void triggerKycUpdate(Merchant merchant, String reason) {
        logger.info("Triggering KYC update for merchant {}: {}", merchant.getMerchantId(), reason);
        
        // Trigger periodic refresh service
        periodicRefreshService.refreshMerchantKycManually(merchant.getMerchantId());
        
        // TODO: Create alert or notification
    }

    /**
     * Manually trigger KYC update for a merchant
     */
    @Transactional
    public void triggerKycUpdateManually(Long merchantId, String reason) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found: " + merchantId));
        
        triggerKycUpdate(merchant, reason);
    }
}

