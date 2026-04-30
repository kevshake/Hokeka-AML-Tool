package com.posgateway.aml.service.risk;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.RiskLevel;
// actually we use RiskAssessmentService usually, but let's stick to simple logic for now
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

// @RequiredArgsConstructor removed
@Service
public class TransactionLimitService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TransactionLimitService.class);

    private final com.posgateway.aml.service.notification.NotificationService notificationService;

    public TransactionLimitService(com.posgateway.aml.service.notification.NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private static final BigDecimal LIMIT_LOW_RISK = new BigDecimal("100000.00"); // 100k
    private static final BigDecimal LIMIT_MEDIUM_RISK = new BigDecimal("50000.00"); // 50k
    private static final BigDecimal LIMIT_HIGH_RISK = new BigDecimal("10000.00"); // 10k
    private static final BigDecimal LIMIT_CRITICAL = BigDecimal.ZERO;

    /**
     * Get Daily Transaction Limit based on Risk Level
     */
    public BigDecimal getDailyLimit(Merchant merchant, RiskLevel riskLevel) {
        if (riskLevel == null)
            return LIMIT_LOW_RISK;

        return switch (riskLevel) {
            case LOW -> LIMIT_LOW_RISK;
            case MEDIUM -> LIMIT_MEDIUM_RISK;
            case HIGH -> LIMIT_HIGH_RISK;
            case CRITICAL -> LIMIT_CRITICAL;
        };
    }

    /**
     * Check if transaction amount exceeds limit
     */
    public boolean isLimitExceeded(Merchant merchant, RiskLevel riskLevel, BigDecimal currentDailyVolume,
            BigDecimal transactionAmount) {
        BigDecimal limit = getDailyLimit(merchant, riskLevel);
        return currentDailyVolume.add(transactionAmount).compareTo(limit) > 0;
    }

    /**
     * Set temporary limit for merchant
     */
    @org.springframework.transaction.annotation.Transactional
    public void setTemporaryLimit(Merchant merchant, java.math.BigDecimal amount, java.time.LocalDateTime expiry) {
        log.info("Setting temporary limit for merchant {}: {} until {}", merchant.getMerchantId(), amount, expiry);
        // In a real system, this would be stored in a separate table or field
        notificationService.sendSystemAlert("compliance-team",
                String.format("Temporary limit set for Merchant %s: %s", merchant.getMerchantId(), amount));
    }

    /**
     * Check for limit breach and alert
     */
    public void checkAndAlertOnBreach(Merchant merchant, RiskLevel riskLevel, java.math.BigDecimal currentVolume) {
        BigDecimal limit = getDailyLimit(merchant, riskLevel);
        if (currentVolume.compareTo(limit) > 0) {
            String message = String.format("Transaction limit breached for Merchant %s. Current: %s, Limit: %s",
                    merchant.getMerchantId(), currentVolume, limit);

            log.warn(message);
            notificationService.sendAlert(message, "HIGH");
        }
    }
}
