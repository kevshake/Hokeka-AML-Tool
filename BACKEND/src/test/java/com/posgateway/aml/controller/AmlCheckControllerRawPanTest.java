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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** /aml/check refuses a raw card number in panHash before any scoring, cache or store. */
class AmlCheckControllerRawPanTest {

    @Test
    void rawPanInPanHashIsBadRequestAndNothingIsCalled() {
        FraudDetectionOrchestrator orchestrator = mock(FraudDetectionOrchestrator.class);
        AmlMicroserviceClient microserviceClient = mock(AmlMicroserviceClient.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        AmlCheckController controller = new AmlCheckController(orchestrator, mock(ScoringService.class),
                mock(FeatureExtractionService.class), microserviceClient, new AmlMicroserviceProperties(),
                redisTemplate);

        ResponseEntity<Map<String, Object>> response = controller.check(Map.of(
                "txnId", "txn-raw", "pspId", 2L, "panHash", "4111 1111 1111 1111", "amountCents", 100L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("not a raw card number");
        assertThat(response.getBody().toString()).doesNotContain("4111");
        verifyNoInteractions(orchestrator, microserviceClient, redisTemplate);
    }
}
