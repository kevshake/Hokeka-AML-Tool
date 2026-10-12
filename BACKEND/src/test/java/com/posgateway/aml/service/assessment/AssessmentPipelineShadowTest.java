package com.posgateway.aml.service.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.TransactionFeaturesRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.ConfigService;
import com.posgateway.aml.service.DecisionEngine;
import com.posgateway.aml.service.kafka.KafkaOutboxService;
import com.posgateway.aml.service.limits.TransactionLimitEnforcementService;
import com.posgateway.aml.service.sanctions.RealTimeTransactionScreeningService;
import com.posgateway.aml.entity.assessment.FindingSourceType;
import com.posgateway.aml.service.security.PaymentBlacklistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Shadow ledger hooks must not change DecisionEngine outcomes.
 */
class AssessmentPipelineShadowTest {

    @AfterEach
    void clearScope() {
        AssessmentRecordingScope.clear();
    }

    @Test
    void decisionUnchangedWhenAssessmentRecorderPresent() {
        ConfigService configService = mock(ConfigService.class);
        AlertRepository alertRepository = mock(AlertRepository.class);
        TransactionFeaturesRepository featuresRepository = mock(TransactionFeaturesRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        KafkaOutboxService outboxService = mock(KafkaOutboxService.class);
        TransactionLimitEnforcementService limitService = mock(TransactionLimitEnforcementService.class);
        PaymentBlacklistService blacklistService = mock(PaymentBlacklistService.class);
        RealTimeTransactionScreeningService screeningService = mock(RealTimeTransactionScreeningService.class);
        FindingRecorder findingRecorder = mock(FindingRecorder.class);
        AssessmentEngineRecorder engineRecorder = new AssessmentEngineRecorder(findingRecorder);

        when(limitService.checkLimits(any())).thenReturn(Optional.empty());
        when(configService.isBlacklistEnabled()).thenReturn(false);
        when(screeningService.screenTransaction(any()))
                .thenReturn(RealTimeTransactionScreeningService.TransactionScreeningResult.clear(1L));
        when(configService.getFraudBlockThreshold()).thenReturn(0.95);
        when(configService.getFraudHoldThreshold()).thenReturn(0.75);
        when(configService.getAmlHighValueThreshold()).thenReturn(Long.MAX_VALUE);

        DecisionEngine engine = new DecisionEngine(
                configService, alertRepository, featuresRepository, transactionRepository,
                new ObjectMapper(), outboxService, limitService, blacklistService);
        ReflectionTestUtils.setField(engine, "realTimeScreeningService", screeningService);
        ReflectionTestUtils.setField(engine, "assessmentEngineRecorder", engineRecorder);

        UUID assessmentId = UUID.randomUUID();
        AssessmentRecordingScope.set(new AssessmentRecordingScope.Scope(assessmentId, 2L, 10L, 5L));

        TransactionEntity transaction = new TransactionEntity();
        transaction.setTxnId(10L);
        transaction.setPspId(2L);
        transaction.setMerchantId("5");
        transaction.setAmountCents(1000L);

        DecisionEngine.DecisionResult result = engine.evaluate(
                transaction, 0.1, Map.of(), 5L, Map.of());

        assertEquals("ALLOW", result.getAction());

        ArgumentCaptor<FindingDraft> captor = ArgumentCaptor.forClass(FindingDraft.class);
        verify(findingRecorder, atLeastOnce()).record(captor.capture());
        assertEquals(FindingSourceType.LIMIT, captor.getAllValues().get(0).getSourceType());
    }
}
