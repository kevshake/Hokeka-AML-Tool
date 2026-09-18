package com.posgateway.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.TransactionFeaturesRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.kafka.KafkaOutboxService;
import com.posgateway.aml.service.limits.TransactionLimitEnforcementService;
import com.posgateway.aml.service.psp.WebhookOutboxService;
import com.posgateway.aml.service.sanctions.RealTimeTransactionScreeningService;
import com.posgateway.aml.service.security.PaymentBlacklistService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionEngineTest {

    @Test
    void persistsFinalDecisionAndRegulatoryEvidenceOnTransactionAndAlert() {
        ConfigService configService = mock(ConfigService.class);
        AlertRepository alertRepository = mock(AlertRepository.class);
        TransactionFeaturesRepository featuresRepository = mock(TransactionFeaturesRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        KafkaOutboxService outboxService = mock(KafkaOutboxService.class);
        TransactionLimitEnforcementService limitService = mock(TransactionLimitEnforcementService.class);
        PaymentBlacklistService blacklistService = mock(PaymentBlacklistService.class);
        RealTimeTransactionScreeningService screeningService = mock(RealTimeTransactionScreeningService.class);

        when(limitService.checkLimits(any())).thenReturn(Optional.empty());
        when(screeningService.screenTransaction(any()))
                .thenReturn(RealTimeTransactionScreeningService.TransactionScreeningResult.clear(50L));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setAlertId(70L);
            return alert;
        });

        DecisionEngine engine = new DecisionEngine(
                configService, alertRepository, featuresRepository, transactionRepository,
                new ObjectMapper(), outboxService, limitService, blacklistService);
        ReflectionTestUtils.setField(engine, "realTimeScreeningService", screeningService);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(50L);
        transaction.setMerchantId("4");
        transaction.setPspId(2L);
        transaction.setAmountCents(95000000L);
        transaction.setCurrency("KES");

        Map<String, Object> riskDetails = Map.of(
                "rule_decision", "HOLD",
                "rules_triggered", List.of("CBK_STRUCTURING_KES"),
                "rule_reasons", List.of("Potential structuring"),
                "sar_required", true,
                "ctr_required", false);

        DecisionEngine.DecisionResult result = engine.evaluate(
                transaction, 0.75, Map.of(), 8L, riskDetails);

        assertEquals("HOLD", result.getAction());
        assertEquals("HOLD", transaction.getDecision());
        assertEquals("HIGH", transaction.getRiskLevel());
        assertTrue(transaction.isSarRequired());
        assertEquals("[\"CBK_STRUCTURING_KES\"]", transaction.getTriggeredRules());
        verify(transactionRepository).save(transaction);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        assertTrue(alertCaptor.getValue().isSarRequired());
        assertEquals(transaction.getTriggeredRules(), alertCaptor.getValue().getTriggeredRules());
        verify(outboxService).enqueue(
                org.mockito.ArgumentMatchers.eq("alert.generated:70"),
                org.mockito.ArgumentMatchers.eq("alerts.generated"),
                org.mockito.ArgumentMatchers.eq("2"),
                org.mockito.ArgumentMatchers.contains("CBK_STRUCTURING_KES"));
    }

    /**
     * Alert creation enqueues durable RISK_ALERT webhook deliveries in the same transaction.
     */
    @Test
    void createAlertEnqueuesRiskAlertWebhookForTheTransactionsPsp() {
        ConfigService configService = mock(ConfigService.class);
        AlertRepository alertRepository = mock(AlertRepository.class);
        TransactionFeaturesRepository featuresRepository = mock(TransactionFeaturesRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        KafkaOutboxService outboxService = mock(KafkaOutboxService.class);
        TransactionLimitEnforcementService limitService = mock(TransactionLimitEnforcementService.class);
        PaymentBlacklistService blacklistService = mock(PaymentBlacklistService.class);
        RealTimeTransactionScreeningService screeningService = mock(RealTimeTransactionScreeningService.class);
        WebhookOutboxService webhookOutboxService = mock(WebhookOutboxService.class);

        when(limitService.checkLimits(any())).thenReturn(Optional.empty());
        when(screeningService.screenTransaction(any()))
                .thenReturn(RealTimeTransactionScreeningService.TransactionScreeningResult.clear(50L));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setAlertId(72L);
            return alert;
        });

        DecisionEngine engine = new DecisionEngine(
                configService, alertRepository, featuresRepository, transactionRepository,
                new ObjectMapper(), outboxService, limitService, blacklistService);
        ReflectionTestUtils.setField(engine, "realTimeScreeningService", screeningService);
        ReflectionTestUtils.setField(engine, "webhookOutboxService", webhookOutboxService);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(50L);
        transaction.setMerchantId("4");
        transaction.setPspId(2L);
        transaction.setAmountCents(95000000L);
        transaction.setCurrency("KES");

        Map<String, Object> riskDetails = Map.of(
                "rule_decision", "HOLD",
                "rules_triggered", List.of("CBK_STRUCTURING_KES"),
                "rule_reasons", List.of("Potential structuring"),
                "sar_required", true,
                "ctr_required", false);

        engine.evaluate(transaction, 0.75, Map.of(), 8L, riskDetails);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(webhookOutboxService).enqueueForPsp(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq("RISK_ALERT"),
                payloadCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("webhook.risk_alert:72"));
        assertEquals(72L, payloadCaptor.getValue().get("alertId"));
    }

    @Test
    void holdsWhenSanctionsScreeningIsUnavailable() {
        ConfigService configService = mock(ConfigService.class);
        AlertRepository alertRepository = mock(AlertRepository.class);
        TransactionFeaturesRepository featuresRepository = mock(TransactionFeaturesRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        KafkaOutboxService outboxService = mock(KafkaOutboxService.class);
        TransactionLimitEnforcementService limitService = mock(TransactionLimitEnforcementService.class);
        PaymentBlacklistService blacklistService = mock(PaymentBlacklistService.class);
        RealTimeTransactionScreeningService screeningService = mock(RealTimeTransactionScreeningService.class);

        when(limitService.checkLimits(any())).thenReturn(Optional.empty());
        when(screeningService.screenTransaction(any())).thenReturn(
                new RealTimeTransactionScreeningService.TransactionScreeningResult(
                        51L, List.of(), false, true));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setAlertId(71L);
            return alert;
        });

        DecisionEngine engine = new DecisionEngine(
                configService, alertRepository, featuresRepository, transactionRepository,
                new ObjectMapper(), outboxService, limitService, blacklistService);
        ReflectionTestUtils.setField(engine, "realTimeScreeningService", screeningService);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(51L);
        transaction.setMerchantId("4");
        transaction.setPspId(2L);
        transaction.setAmountCents(1000L);

        DecisionEngine.DecisionResult result = engine.evaluate(
                transaction, 0.1, Map.of(), 5L, Map.of());

        assertEquals("HOLD", result.getAction());
        assertEquals("HOLD", transaction.getDecision());
        assertEquals("HIGH", transaction.getRiskLevel());
        assertTrue(result.getReasons().get(0).contains("SANCTIONS_SCREENING_UNAVAILABLE"));
        verify(transactionRepository).save(transaction);
        verify(alertRepository).save(any(Alert.class));
    }
}
