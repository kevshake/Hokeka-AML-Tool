package com.posgateway.aml.service.edge;

import com.posgateway.aml.dto.edge.EdgeDecisionRequest;
import com.posgateway.aml.dto.edge.EdgeDecisionResponse;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.ai.decision.AiDisclosureSanitizer;
import com.posgateway.aml.service.ai.decision.AiDecisionGateway;
import com.posgateway.aml.service.ai.decision.AiDecisionOutcome;
import com.posgateway.aml.service.ai.decision.AiRecommendation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EdgeDecisionServiceTest {

    @Mock private AiDecisionGateway gateway;
    @Mock private PspRepository pspRepository;
    @InjectMocks private EdgeDecisionService service;

    @Test
    void inlineModeWaitsForJev() {
        EdgeNode node = new EdgeNode();
        node.setEdgeId("edge-1");
        node.setPspId(10L);

        Psp psp = new Psp();
        psp.setAiInlineMode(true);
        psp.setAiInlineBudgetMs(800);
        when(pspRepository.findById(10L)).thenReturn(Optional.of(psp));

        AiDecisionOutcome outcome = AiDecisionOutcome.builder()
                .fallback(false)
                .recommendation(AiRecommendation.REVIEW)
                .confidence(0.77)
                .reasons(java.util.List.of("borderline velocity"))
                .finalDecision("ALLOW")
                .aiApplied(false)
                .auditId(5L)
                .build();
        when(gateway.decide(any(), any())).thenReturn(outcome);

        EdgeDecisionResponse response = service.handle(node,
                new EdgeDecisionRequest("TRANSACTION_RISK", "ALLOW", Map.of("amount", 5000), false));

        assertEquals("ALLOW", response.finalDecision());
        assertEquals("REVIEW", response.aiRecommendation());
        assertTrue(response.advisory());
        verify(gateway).decide(any(), eq(java.time.Duration.ofMillis(800)));
    }

    @Test
    void inlineOffUsesAsyncPath() {
        EdgeNode node = new EdgeNode();
        node.setPspId(10L);
        Psp psp = new Psp();
        psp.setAiInlineMode(false);
        when(pspRepository.findById(10L)).thenReturn(Optional.of(psp));

        EdgeDecisionResponse response = service.handle(node,
                new EdgeDecisionRequest("TRANSACTION_RISK", "ALERT", Map.of(), false));

        assertEquals("ALERT", response.finalDecision());
        assertTrue(response.fallback());
        verify(gateway).decideAsync(any());
        verify(gateway, never()).decide(any(), any());
        AiDisclosureSanitizer.assertPspSafe("edge inline-off fallback",
                response.fallbackReason());
    }

    @Test
    void edgeResponseSanitizesInternalFallbackReasons() {
        EdgeNode node = new EdgeNode();
        node.setEdgeId("edge-1");
        node.setPspId(10L);

        Psp psp = new Psp();
        psp.setAiInlineMode(true);
        psp.setAiInlineBudgetMs(500);
        when(pspRepository.findById(10L)).thenReturn(Optional.of(psp));

        AiDecisionOutcome outcome = AiDecisionOutcome.fallback("ALERT",
                "Laya error: TimeoutException");
        when(gateway.decide(any(), any())).thenReturn(outcome);

        EdgeDecisionResponse response = service.handle(node,
                new EdgeDecisionRequest("TRANSACTION_RISK", "ALERT", Map.of(), false));

        AiDisclosureSanitizer.assertPspSafe("edge decision fallback", response.fallbackReason());
        assertFalse(response.fallbackReason().toLowerCase().contains("openrouter"));
    }
}
