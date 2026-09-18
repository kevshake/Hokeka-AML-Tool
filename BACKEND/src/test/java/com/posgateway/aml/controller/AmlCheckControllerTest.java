package com.posgateway.aml.controller;

import com.posgateway.aml.client.aml.AmlMicroserviceClient;
import com.posgateway.aml.client.aml.AmlMicroserviceProperties;
import com.posgateway.aml.service.FeatureExtractionService;
import com.posgateway.aml.service.FraudDetectionOrchestrator;
import com.posgateway.aml.service.ScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AmlCheckControllerTest {

    @Test
    void scoringFailureReturnsServiceUnavailableAndFailClosedDecision() {
        FraudDetectionOrchestrator orchestrator = mock(FraudDetectionOrchestrator.class);
        AmlMicroserviceClient microserviceClient = mock(AmlMicroserviceClient.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        when(orchestrator.processTransaction(any())).thenThrow(new IllegalStateException("model offline"));

        AmlCheckController controller = new AmlCheckController(
                orchestrator,
                mock(ScoringService.class),
                mock(FeatureExtractionService.class),
                microserviceClient,
                new AmlMicroserviceProperties(),
                redisTemplate);

        ResponseEntity<Map<String, Object>> response = controller.check(Map.of(
                "txnId", "txn-1",
                "pspId", 2L,
                "merchantId", "merchant-1",
                "amountCents", 5_000L));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("HOLD", response.getBody().get("decision"));
        assertEquals("CRITICAL", response.getBody().get("riskLevel"));
        assertEquals(1.0, response.getBody().get("score"));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void missingLocalScoringPathReturnsServiceUnavailable() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        AmlCheckController controller = new AmlCheckController(
                null,
                null,
                null,
                mock(AmlMicroserviceClient.class),
                new AmlMicroserviceProperties(),
                redisTemplate);

        ResponseEntity<Map<String, Object>> response = controller.check(Map.of("txnId", "txn-2"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("HOLD", response.getBody().get("decision"));
        assertEquals(1.0, response.getBody().get("score"));
        verifyNoInteractions(redisTemplate);
    }
}
