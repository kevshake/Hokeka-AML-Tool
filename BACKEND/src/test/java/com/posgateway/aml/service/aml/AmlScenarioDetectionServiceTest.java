package com.posgateway.aml.service.aml;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.compliance.CashStructuringDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AmlScenarioDetectionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CashStructuringDetectionService cashStructuringDetectionService;

    private AmlScenarioDetectionService service;
    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUp() {
        service = new AmlScenarioDetectionService(
                transactionRepository, cashStructuringDetectionService);
        ReflectionTestUtils.setField(service, "funnelMinTransactions", 5);
        ReflectionTestUtils.setField(service, "funnelTimeWindowHours", 24);
        ReflectionTestUtils.setField(service, "funnelMinimumUsd", new BigDecimal("10000"));
        ReflectionTestUtils.setField(
                service, "funnelMinimumPassThroughRatio", new BigDecimal("0.70"));
        ReflectionTestUtils.setField(service, "funnelMinimumSourceIndicators", 2);
        end = LocalDateTime.of(2026, 7, 16, 12, 0);
        start = end.minusDays(1);
    }

    @Test
    void detectsEvidenceBackedInboundOutboundPassThrough() {
        List<TransactionEntity> transactions = List.of(
                transaction(1L, "INBOUND", "4000", "device-a", end.minusHours(6)),
                transaction(2L, "CREDIT", "4000", "device-b", end.minusHours(5)),
                transaction(3L, "INBOUND", "4000", "device-c", end.minusHours(4)),
                transaction(4L, "OUTBOUND", "5000", "device-a", end.minusHours(3)),
                transaction(5L, "DEBIT", "4000", "device-a", end.minusHours(2)));
        when(transactionRepository.findByMerchantIdAndTimestampBetween(
                "merchant-1", start, end)).thenReturn(transactions);

        List<AmlScenarioDetectionService.FunnelAccountDetection> result =
                service.detectFunnelAccounts("merchant-1", start, end);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("12000"), result.get(0).getTotalReceived());
        assertEquals(new BigDecimal("9000"), result.get(0).getTotalTransferred());
        assertEquals(new BigDecimal("0.7500"), result.get(0).getPassThroughRatio());
        assertEquals(3, result.get(0).getDistinctSourceIndicators());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), result.get(0).getTransactionIds());
    }

    @Test
    void doesNotAggregateMixedCurrenciesWithoutUsdEvidence() {
        List<TransactionEntity> transactions = List.of(
                transaction(1L, "INBOUND", "4000", "device-a", end.minusHours(6)),
                transaction(2L, "INBOUND", "4000", "device-b", end.minusHours(5)),
                transaction(3L, "INBOUND", "4000", "device-c", end.minusHours(4)),
                transaction(4L, "OUTBOUND", "5000", "device-a", end.minusHours(3)),
                transaction(5L, "OUTBOUND", null, "device-a", end.minusHours(2)));
        when(transactionRepository.findByMerchantIdAndTimestampBetween(
                "merchant-1", start, end)).thenReturn(transactions);

        assertTrue(service.detectFunnelAccounts("merchant-1", start, end).isEmpty());
    }

    private TransactionEntity transaction(
            Long id,
            String direction,
            String usd,
            String device,
            LocalDateTime timestamp) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(id);
        transaction.setPanHash("account-1");
        transaction.setDirection(direction);
        transaction.setCtrUsdEquivalent(usd == null ? null : new BigDecimal(usd));
        transaction.setDeviceFingerprint(device);
        transaction.setTxnTs(timestamp);
        return transaction;
    }
}
