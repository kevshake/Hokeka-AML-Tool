package com.posgateway.aml.service.limits;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.limits.GlobalLimit;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.limits.GlobalLimitRepository;
import com.posgateway.aml.repository.limits.MerchantTransactionLimitRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionLimitEnforcementServiceTest {

    private final MerchantTransactionLimitRepository merchantLimits = mock(MerchantTransactionLimitRepository.class);
    private final GlobalLimitRepository globalLimits = mock(GlobalLimitRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final TransactionLimitEnforcementService service =
            new TransactionLimitEnforcementService(merchantLimits, globalLimits, transactions);

    @Test
    void blocksWhenTenantTransactionLimitIsExceeded() {
        TransactionEntity transaction = transaction(42L, 15_000L);
        GlobalLimit limit = activeLimit(new BigDecimal("100.00"));

        when(merchantLimits.findByMerchant_MerchantId(10L)).thenReturn(Optional.empty());
        when(globalLimits.findByNameAndPspId(LimitsManagementService.AML_TRANSACTION_LIMIT, 42L))
                .thenReturn(Optional.of(limit));
        when(globalLimits.findByNameAndPspId(LimitsManagementService.AML_DAILY_LIMIT, 42L))
                .thenReturn(Optional.empty());
        when(globalLimits.findByNameAndPspIdIsNull(LimitsManagementService.AML_DAILY_LIMIT))
                .thenReturn(Optional.empty());

        var breach = service.checkLimits(transaction);

        assertThat(breach).isPresent();
        assertThat(breach.orElseThrow().action()).isEqualTo("BLOCK");
        assertThat(breach.orElseThrow().reasons()).anyMatch(reason -> reason.contains("PSP AML per-transaction"));
    }

    @Test
    void blocksWhenTransactionHasNoPspScope() {
        TransactionEntity transaction = transaction(null, 1_000L);
        when(merchantLimits.findByMerchant_MerchantId(10L)).thenReturn(Optional.empty());
        when(globalLimits.findByNameAndPspIdIsNull(any())).thenReturn(Optional.empty());

        var breach = service.checkLimits(transaction);

        assertThat(breach).isPresent();
        assertThat(breach.orElseThrow().reasons())
                .contains("Transaction PSP scope is unavailable; limits cannot be evaluated safely");
    }

    private TransactionEntity transaction(Long pspId, long amountCents) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setMerchantId("10");
        transaction.setPspId(pspId);
        transaction.setAmountCents(amountCents);
        transaction.setTxnTs(LocalDateTime.now());
        return transaction;
    }

    private GlobalLimit activeLimit(BigDecimal value) {
        GlobalLimit limit = new GlobalLimit();
        limit.setLimitValue(value);
        limit.setStatus("ACTIVE");
        return limit;
    }
}
