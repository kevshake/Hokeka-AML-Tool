package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashStructuringDetectionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RegulatoryExchangeRateService exchangeRateService;

    private CashStructuringDetectionService service;

    @BeforeEach
    void setUp() {
        service = new CashStructuringDetectionService(
                transactionRepository,
                exchangeRateService,
                new BigDecimal("15000"),
                new BigDecimal("0.80"),
                2);
    }

    @Test
    void nonCashTransactionIsNeverCtrOrStructuring() {
        TransactionEntity transaction = transaction(1L, false, 2_500_000L, "KES", LocalDateTime.now());

        CashStructuringDetectionService.Assessment result = service.assess(transaction);

        assertEquals("NOT_CASH", result.status());
        assertFalse(result.ctrRequired());
        assertFalse(result.structuringSuspected());
        verifyNoInteractions(exchangeRateService, transactionRepository);
    }

    @Test
    void reportableCashUsesApprovedUsdConversion() {
        TransactionEntity transaction = transaction(1L, true, 2_100_000L, "KES", LocalDateTime.now());
        when(exchangeRateService.convertToUsd(any(), eq("KES"), any()))
                .thenReturn(RegulatoryExchangeRateService.ConversionResult.available(
                        new BigDecimal("15750.0000"),
                        new BigDecimal("0.0075"),
                        "CBK_REFERENCE_RATE",
                        transaction.getTxnTs().minusHours(1)));

        CashStructuringDetectionService.Assessment result = service.assess(transaction);

        assertEquals("REPORTABLE", result.status());
        assertTrue(result.ctrRequired());
        assertFalse(result.structuringSuspected());
        assertEquals("CBK_REFERENCE_RATE", result.evidence().get("rateSource"));
    }

    @Test
    void repeatedNearThresholdCashIsStructuring() {
        LocalDateTime now = LocalDateTime.now();
        TransactionEntity first = transaction(1L, true, 170_000_000L, "KES", now.minusHours(3));
        TransactionEntity second = transaction(2L, true, 180_000_000L, "KES", now);
        when(transactionRepository.findByPanHashAndTxnTsBetweenOrderByTxnTsAsc(
                eq("acct-1"), any(), any())).thenReturn(List.of(first, second));
        when(exchangeRateService.convertToUsd(any(), eq("KES"), any()))
                .thenAnswer(invocation -> {
                    BigDecimal amount = invocation.getArgument(0);
                    return RegulatoryExchangeRateService.ConversionResult.available(
                            amount.multiply(new BigDecimal("0.0075")),
                            new BigDecimal("0.0075"),
                            "CBK_REFERENCE_RATE",
                            now.minusHours(4));
                });

        CashStructuringDetectionService.Assessment result = service.assess(second);

        assertEquals("STRUCTURING_SUSPECTED", result.status());
        assertTrue(result.structuringSuspected());
        assertFalse(result.ctrRequired());
        assertEquals(2, result.transactionCount24h());
        assertEquals(List.of(1L, 2L), result.evidence().get("structuringTransactionIds"));
    }

    @Test
    void unavailableFxIsExposedAndNeverTreatedAsClean() {
        TransactionEntity transaction = transaction(1L, true, 1_800_000L, "KES", LocalDateTime.now());
        when(exchangeRateService.convertToUsd(any(), eq("KES"), any()))
                .thenReturn(RegulatoryExchangeRateService.ConversionResult.unavailable("RATE_STALE"));

        CashStructuringDetectionService.Assessment result = service.assess(transaction);

        assertEquals("FX_UNAVAILABLE", result.status());
        assertEquals("RATE_STALE", result.evidence().get("fxUnavailableReason"));
        assertFalse(result.ctrRequired());
        assertFalse(result.structuringSuspected());
    }

    private static TransactionEntity transaction(
            Long id, boolean cash, long amountCents, String currency, LocalDateTime at) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(id);
        transaction.setPanHash("acct-1");
        transaction.setCashTransaction(cash);
        transaction.setAmountCents(amountCents);
        transaction.setCurrency(currency);
        transaction.setTxnTs(at);
        return transaction;
    }
}
