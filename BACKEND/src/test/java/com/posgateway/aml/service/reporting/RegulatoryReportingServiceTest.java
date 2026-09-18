package com.posgateway.aml.service.reporting;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegulatoryReportingServiceTest {

    @Test
    void ctrUsesOnlyCashTransactionsWithCompletedReportableEvidence() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        PspIsolationService isolation = mock(PspIsolationService.class);
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        when(isolation.getCurrentUserPspId()).thenReturn(7L);

        TransactionEntity reportable = transaction(1L, true, true, "REPORTABLE", new BigDecimal("15500"));
        TransactionEntity nonCash = transaction(2L, false, true, "REPORTABLE", new BigDecimal("20000"));
        TransactionEntity rawHighAmount = transaction(3L, true, false, null, null);
        when(transactions.findByPspIdAndTxnTsBetween(7L, start, end))
                .thenReturn(List.of(reportable, nonCash, rawHighAmount));

        RegulatoryReportingService service = new RegulatoryReportingService(
                transactions, mock(MerchantRepository.class), mock(PspRepository.class), isolation);
        ReflectionTestUtils.setField(service, "ctrThreshold", new BigDecimal("15000"));

        RegulatoryReportingService.CurrencyTransactionReport report = service.generateCtr(start, end);

        assertEquals(1, report.getTransactionCount());
        assertEquals(1L, report.getTransactions().get(0).getTxnId());
        assertEquals(new BigDecimal("15500"), report.getTransactionDetails().get(0).getUsdEquivalent());
    }

    private TransactionEntity transaction(Long id, boolean cash, boolean ctr,
                                          String status, BigDecimal amountUsd) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(id);
        transaction.setPspId(7L);
        transaction.setMerchantId("10");
        transaction.setAmountCents(2_000_000L);
        transaction.setCurrency("KES");
        transaction.setTxnTs(LocalDateTime.now());
        transaction.setCashTransaction(cash);
        transaction.setCtrRequired(ctr);
        transaction.setCtrEvaluationStatus(status);
        transaction.setCtrUsdEquivalent(amountUsd);
        transaction.setCtrThresholdUsd(new BigDecimal("15000"));
        transaction.setCtrRateSource("CENTRAL_BANK_FEED");
        return transaction;
    }
}
