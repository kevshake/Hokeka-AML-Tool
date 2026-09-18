package com.posgateway.aml.service.limits;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.limits.MerchantTransactionLimit;
import com.posgateway.aml.entity.limits.GlobalLimit;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.limits.GlobalLimitRepository;
import com.posgateway.aml.repository.limits.MerchantTransactionLimitRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Enforces per-merchant transaction limits configured in {@code merchant_transaction_limits}.
 */
@Service
public class TransactionLimitEnforcementService {

    private final MerchantTransactionLimitRepository merchantLimitRepository;
    private final GlobalLimitRepository globalLimitRepository;
    private final TransactionRepository transactionRepository;

    public TransactionLimitEnforcementService(MerchantTransactionLimitRepository merchantLimitRepository,
                                              GlobalLimitRepository globalLimitRepository,
                                              TransactionRepository transactionRepository) {
        this.merchantLimitRepository = merchantLimitRepository;
        this.globalLimitRepository = globalLimitRepository;
        this.transactionRepository = transactionRepository;
    }

    public Optional<LimitBreach> checkLimits(TransactionEntity transaction) {
        if (transaction == null || transaction.getMerchantId() == null || transaction.getAmountCents() == null) {
            return Optional.empty();
        }
        Long merchantId;
        try {
            merchantId = Long.parseLong(transaction.getMerchantId());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        MerchantTransactionLimit limits = merchantLimitRepository.findByMerchant_MerchantId(merchantId).orElse(null);

        BigDecimal txnAmount = BigDecimal.valueOf(transaction.getAmountCents())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        List<String> reasons = new ArrayList<>();
        if (transaction.getPspId() == null) {
            reasons.add("Transaction PSP scope is unavailable; limits cannot be evaluated safely");
        }

        if (limits != null && "ACTIVE".equalsIgnoreCase(limits.getStatus())) {
            if (limits.getPerTransactionLimit() != null
                    && txnAmount.compareTo(limits.getPerTransactionLimit()) > 0) {
                reasons.add(String.format("Merchant per-transaction limit exceeded: %s > %s",
                        txnAmount, limits.getPerTransactionLimit()));
            }

            // Honour a temporary daily override (set via TransactionLimitService.setTemporaryLimit)
            // while it is active; once expired the configured daily limit applies again.
            checkMerchantPeriodLimit(transaction, limits.effectiveDailyLimit(LocalDateTime.now()),
                    LocalDateTime.now().toLocalDate().atStartOfDay(), "daily", reasons);
            checkMerchantPeriodLimit(transaction, limits.getWeeklyLimit(),
                    LocalDateTime.now().toLocalDate().minusDays(LocalDateTime.now().getDayOfWeek().getValue() - 1L).atStartOfDay(),
                    "weekly", reasons);
            checkMerchantPeriodLimit(transaction, limits.getMonthlyLimit(),
                    LocalDateTime.now().toLocalDate().withDayOfMonth(1).atStartOfDay(), "monthly", reasons);
        }

        effectiveLimit(LimitsManagementService.AML_TRANSACTION_LIMIT, transaction.getPspId())
                .filter(this::isActive)
                .filter(limit -> limit.getLimitValue() != null && txnAmount.compareTo(limit.getLimitValue()) > 0)
                .ifPresent(limit -> reasons.add(String.format(
                        "PSP AML per-transaction limit exceeded: %s > %s", txnAmount, limit.getLimitValue())));

        effectiveLimit(LimitsManagementService.AML_DAILY_LIMIT, transaction.getPspId())
                .filter(this::isActive)
                .filter(limit -> transaction.getPspId() != null)
                .ifPresent(limit -> {
                    LocalDateTime now = LocalDateTime.now();
                    BigDecimal cents = transactionRepository.sumAmountByPspAndPeriod(
                            transaction.getPspId(), now.toLocalDate().atStartOfDay(), now.plusNanos(1));
                    BigDecimal dailyVolume = cents.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    if (limit.getLimitValue() != null && dailyVolume.compareTo(limit.getLimitValue()) > 0) {
                        reasons.add(String.format("PSP AML daily limit exceeded: %s > %s",
                                dailyVolume, limit.getLimitValue()));
                    }
                });

        if (reasons.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LimitBreach("BLOCK", reasons));
    }

    private void checkMerchantPeriodLimit(TransactionEntity transaction, BigDecimal configuredLimit,
            LocalDateTime periodStart, String periodName, List<String> reasons) {
        if (configuredLimit == null) return;
        Long cents = transactionRepository.sumAmountByMerchantInTimeWindow(
                transaction.getMerchantId(), periodStart, LocalDateTime.now());
        BigDecimal volume = BigDecimal.valueOf(cents == null ? 0L : cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (volume.compareTo(configuredLimit) > 0) {
            reasons.add(String.format("Merchant %s limit exceeded: %s > %s",
                    periodName, volume, configuredLimit));
        }
    }

    private Optional<GlobalLimit> effectiveLimit(String name, Long pspId) {
        if (pspId != null) {
            Optional<GlobalLimit> tenantLimit = globalLimitRepository.findByNameAndPspId(name, pspId);
            if (tenantLimit.isPresent()) return tenantLimit;
        }
        return globalLimitRepository.findByNameAndPspIdIsNull(name);
    }

    private boolean isActive(GlobalLimit limit) {
        return "ACTIVE".equalsIgnoreCase(limit.getStatus());
    }

    public record LimitBreach(String action, List<String> reasons) {}
}
