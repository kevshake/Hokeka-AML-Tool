package com.posgateway.aml.service.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.jev.JevProperties;
import com.posgateway.aml.entity.jev.JevDecisionAudit;
import com.posgateway.aml.repository.jev.JevDailySpendRepository;
import com.posgateway.aml.repository.jev.JevDecisionAuditRepository;
import com.posgateway.aml.repository.jev.JevEngineSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JevDecisionGatewayTest {

    @Mock private OpenRouterClient openRouterClient;
    @Mock private JevFeatureMaskingService maskingService;
    @Mock private JevPromptTemplateService promptTemplateService;
    @Mock private JevEngineConfigService engineConfigService;
    @Mock private JevBudgetService budgetService;
    @Mock private JevAuditService auditService;
    @Mock private JevDecisionAuditRepository auditRepository;
    @Mock private JevEngineSettingRepository engineSettingRepository;
    @Mock private JevDailySpendRepository dailySpendRepository;

    private JevProperties properties;
    private JevDecisionGateway gateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new JevProperties();
        properties.setApiKey("test-key");
        properties.setModel("test/model");

        when(maskingService.maskFeatures(any())).thenAnswer(i -> i.getArgument(0));
        when(promptTemplateService.resolveSystemPrompt(any(), anyString())).thenReturn("system");
        when(engineConfigService.isEngineEnabled(any())).thenReturn(true);
        when(engineConfigService.isAdvisoryOnly(any())).thenReturn(true);
        when(engineConfigService.promptVersion(any())).thenReturn("v1");
        when(budgetService.isBudgetExceeded(any())).thenReturn(false);

        JevAuditService realAudit = new JevAuditService(auditRepository, objectMapper);
        when(auditRepository.save(any())).thenAnswer(i -> {
            JevDecisionAudit a = i.getArgument(0);
            a.setId(42L);
            return a;
        });

        gateway = new JevDecisionGateway(
                properties, openRouterClient, maskingService, promptTemplateService,
                engineConfigService, budgetService, realAudit, objectMapper);
    }

    @Test
    void disabledWhenNoModel() {
        properties.setModel("");
        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals("JEV not configured (missing OPENROUTER_API_KEY or JEV_MODEL)", outcome.getFallbackReason());
        verifyNoInteractions(openRouterClient);
    }

    @Test
    void successParsesRecommendation() throws Exception {
        when(openRouterClient.chatCompletion(anyString(), anyString(), anyString(), any()))
                .thenReturn(new OpenRouterClient.OpenRouterResponse(
                        "{\"recommendation\":\"REVIEW\",\"riskScore\":55,\"confidence\":0.8,"
                                + "\"reasons\":[\"velocity spike\"],\"citedSignals\":[\"pan_velocity_1h\"]}",
                        "test/model", 100, 50, 0.001, 120));

        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertFalse(outcome.isFallback());
        assertEquals(JevRecommendation.REVIEW, outcome.getRecommendation());
        assertEquals(55.0, outcome.getRiskScore());
        assertEquals("ALLOW", outcome.getFinalDecision());
        assertFalse(outcome.isAiApplied());
    }

    @Test
    void malformedJsonFallsBack() throws Exception {
        when(openRouterClient.chatCompletion(anyString(), anyString(), anyString(), any()))
                .thenReturn(new OpenRouterClient.OpenRouterResponse(
                        "not json", "test/model", 10, 5, null, 50));

        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals("Invalid JSON from model", outcome.getFallbackReason());
    }

    @Test
    void openRouterErrorFallsBack() throws Exception {
        when(openRouterClient.chatCompletion(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("timeout"));

        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertTrue(outcome.getFallbackReason().contains("OpenRouter error"));
    }

    @Test
    void budgetExceededFallsBack() {
        when(budgetService.isBudgetExceeded(1L)).thenReturn(true);
        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals("Daily budget exceeded", outcome.getFallbackReason());
    }

    @Test
    void apiKeyNeverInAuditFeatures() throws Exception {
        when(openRouterClient.chatCompletion(anyString(), anyString(), anyString(), any()))
                .thenReturn(new OpenRouterClient.OpenRouterResponse(
                        "{\"recommendation\":\"APPROVE\",\"riskScore\":10,\"confidence\":0.9,"
                                + "\"reasons\":[],\"citedSignals\":[]}",
                        "test/model", 10, 10, null, 30));

        gateway.decide(sampleContext());

        ArgumentCaptor<JevDecisionAudit> captor = ArgumentCaptor.forClass(JevDecisionAudit.class);
        verify(auditRepository).save(captor.capture());
        String serialized = captor.getValue().getRawResponse();
        assertNotNull(serialized);
        assertFalse(serialized.contains("test-key"));
    }

    private static JevDecisionContext sampleContext() {
        return JevDecisionContext.builder(JevEngineType.TRANSACTION_RISK)
                .pspId(1L)
                .baselineDecision("ALLOW")
                .feature("score", 0.62)
                .transactionId(99L)
                .advisoryOnly(true)
                .build();
    }
}
