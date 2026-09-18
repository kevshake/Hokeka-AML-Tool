package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.rules.RuleFeatureEnrichmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureExtractionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RuleFeatureEnrichmentService ruleFeatureEnrichmentService;

    @Test
    void usesPreEventHistoryForRealZScoreAndSeparatesCreditsFromDebits() {
        LocalDateTime transactionTime = LocalDateTime.of(2026, 7, 15, 12, 0);
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(99L);
        transaction.setTxnTs(transactionTime);
        transaction.setPanHash("pan-1");
        transaction.setAmountCents(40_000L);
        transaction.setCurrency("KES");

        when(transactionRepository.findRecentAmountsByPanBefore(
                eq("pan-1"), eq(transactionTime.minusDays(30)), eq(transactionTime), any(Pageable.class)))
                .thenReturn(List.of(10_000L, 20_000L, 30_000L));
        when(transactionRepository.findLastTransactionTimeByPanBefore("pan-1", transactionTime))
                .thenReturn(transactionTime.minusHours(1));
        when(transactionRepository.sumInboundAmountByPanInWindow(
                "pan-1", transactionTime.minusDays(30), transactionTime))
                .thenReturn(30_000L);
        when(transactionRepository.sumOutboundAmountByPanInWindow(
                "pan-1", transactionTime.minusDays(30), transactionTime))
                .thenReturn(70_000L);
        when(transactionRepository.countHighValueByPanInWindow(
                "pan-1", 1_000_000L, transactionTime.minusDays(7), transactionTime))
                .thenReturn(2L);

        com.posgateway.aml.service.enrichment.IpReputationService ipReputationService =
                new com.posgateway.aml.service.enrichment.IpReputationService();
        org.springframework.test.util.ReflectionTestUtils.setField(ipReputationService, "enabled", true);
        ipReputationService.refreshRanges();
        FeatureExtractionService service = new FeatureExtractionService(
                transactionRepository, new ObjectMapper(), null, ruleFeatureEnrichmentService,
                ipReputationService);

        Map<String, Object> features = service.extractFeatures(transaction);

        assertEquals(200.0, (Double) features.get("avg_amount_by_pan_30d"), 0.0001);
        assertEquals(2.4494897428, (Double) features.get("zscore_amount_vs_pan_history"), 0.0001);
        assertEquals(300.0, (Double) features.get("cumulative_credits_30d"), 0.0001);
        assertEquals(700.0, (Double) features.get("cumulative_debits_30d"), 0.0001);
        assertEquals(2L, features.get("num_high_value_txn_7d"));
        assertEquals(60L, features.get("time_since_last_txn_for_pan_minutes"));
        verify(transactionRepository).findRecentAmountsByPanBefore(
                eq("pan-1"), eq(transactionTime.minusDays(30)), eq(transactionTime), any(Pageable.class));
    }
}
