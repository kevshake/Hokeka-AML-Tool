package com.posgateway.aml.service.risk;

import com.posgateway.aml.entity.limits.MerchantTransactionLimit;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.repository.limits.MerchantTransactionLimitRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// @RequiredArgsConstructor removed
@Service
public class TransactionLimitService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TransactionLimitService.class);

    private final com.posgateway.aml.service.notification.NotificationService notificationService;
    private final MerchantTransactionLimitRepository merchantLimitRepository;

    public TransactionLimitService(com.posgateway.aml.service.notification.NotificationService notificationService,
            MerchantTransactionLimitRepository merchantLimitRepository) {
        this.notificationService = notificationService;
        this.merchantLimitRepository = merchantLimitRepository;
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
     * Set a temporary daily limit for a merchant.
     *
     * <p>Persists the override onto the merchant's {@code merchant_transaction_limits}
     * row (creating the row if none exists) so {@link com.posgateway.aml.service.limits.TransactionLimitEnforcementService}
     * enforces it on the live transaction path until {@code expiry}. Once expired the
     * override is ignored and the merchant's configured daily limit applies again.
     *
     * @param merchant the merchant to constrain (must be persisted, non-null id)
     * @param amount   the temporary daily cap; must be non-null and &gt;= 0
     * @param expiry   when the override stops applying; must be in the future
     * @param setBy    id of the user setting the override (audit), may be null
     */
    @org.springframework.transaction.annotation.Transactional
    public void setTemporaryLimit(Merchant merchant, BigDecimal amount, LocalDateTime expiry, Long setBy) {
        if (merchant == null || merchant.getMerchantId() == null) {
            throw new IllegalArgumentException("A persisted merchant is required to set a temporary limit");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Temporary limit amount must be non-null and non-negative");
        }
        if (expiry == null || !expiry.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Temporary limit expiry must be in the future");
        }

        MerchantTransactionLimit limit = merchantLimitRepository
                .findByMerchant_MerchantId(merchant.getMerchantId())
                .orElseGet(() -> {
                    MerchantTransactionLimit created = new MerchantTransactionLimit();
                    created.setMerchant(merchant);
                    created.setStatus("ACTIVE");
                    return created;
                });
        limit.setTemporaryDailyLimit(amount);
        limit.setTemporaryLimitExpiresAt(expiry);
        limit.setTemporaryLimitSetBy(setBy);
        if (!"ACTIVE".equalsIgnoreCase(limit.getStatus())) {
            limit.setStatus("ACTIVE");
        }
        merchantLimitRepository.save(limit);

        log.info("Temporary daily limit persisted for merchant {}: {} until {} (by user {})",
                merchant.getMerchantId(), amount, expiry, setBy);
        notificationService.sendSystemAlert("compliance-team",
                String.format("Temporary daily limit set for Merchant %s: %s until %s",
                        merchant.getMerchantId(), amount, expiry));
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
