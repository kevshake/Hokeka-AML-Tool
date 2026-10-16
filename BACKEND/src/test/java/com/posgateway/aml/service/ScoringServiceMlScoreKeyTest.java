package com.posgateway.aml.service;

import com.posgateway.aml.rules.RuleEvaluationResult;
import com.posgateway.aml.service.deeplearning.DL4JAnomalyService;
import com.posgateway.aml.service.risk.RiskDetailKeys;
import com.posgateway.aml.service.risk.SchemeSimulatorService;
import com.posgateway.aml.service.rules.RulesExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the ML score is written and read under one canonical key ("ml_score") across the
 * scoring pipeline, including the anomaly-model input and the legacy camelCase alias.
 */
class ScoringServiceMlScoreKeyTest {

    @Test
    void readMlScorePrefersCanonicalKeyAndFallsBackToAlias() {
        assertEquals(0.4, RiskDetailKeys.readMlScore(Map.of("ml_score", 0.4, "mlScore", 0.9)), 1e-9);
        assertEquals(0.9, RiskDetailKeys.readMlScore(Map.of("mlScore", 0.9)), 1e-9);
        assertNull(RiskDetailKeys.readMlScore(Map.of("score", 0.9)));
        assertNull(RiskDetailKeys.readMlScore(null));
    }

    @Test
    void withMlScoreCopiesFeaturesWithoutMutatingCaller() {
        Map<String, Object> features = new HashMap<>(Map.of("amount", 10));
        Map<String, Object> enriched = ScoringService.withMlScore(features, Map.of("ml_score", 0.62));

        assertEquals(0.62, (double) enriched.get("ml_score"), 1e-9);
        assertEquals(10, enriched.get("amount"));
        assertFalse(features.containsKey("ml_score"));
    }

    @Test
    void anomalyModelReceivesRawModelScoreUnderCanonicalKey() {
        RestClientService restClient = mock(RestClientService.class);
        SchemeSimulatorService simulator = mock(SchemeSimulatorService.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        RulesExecutionService rules = mock(RulesExecutionService.class);
        DL4JAnomalyService dl4j = mock(DL4JAnomalyService.class);

        when(restClient.postRequestWithRetry(anyString(), anyMap(), anyInt()))
                .thenReturn(new HashMap<>(Map.of("score", 0.42, "latency_ms", 3)));
        when(rules.evaluateRules(anyLong(), anyMap(), anyDouble())).thenReturn(
                new RuleEvaluationResult(9L, "ALLOW", List.of(), List.of(), false, false, 0, 1));
        when(dl4j.isEnabled()).thenReturn(true);
        when(dl4j.detectAnomaly(eq(9L), any()))
                .thenReturn(new DL4JAnomalyService.AnomalyResult(9L, 0.1, false, "normal"));

        ScoringService service = new ScoringService(restClient, simulator, redis, rules, dl4j, null);
        ReflectionTestUtils.setField(service, "scoringEnabled", true);
        ReflectionTestUtils.setField(service, "cacheEnabled", false);
        ReflectionTestUtils.setField(service, "scoringServiceUrl", "http://scoring");
        ReflectionTestUtils.setField(service, "maxRetries", 1);

        Map<String, Object> features = new HashMap<>(Map.of("merchant_id", "M1", "amount", 100));
        ScoringService.ScoringResult result = service.scoreTransaction(9L, features);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> anomalyInput = ArgumentCaptor.forClass(Map.class);
        verify(dl4j).detectAnomaly(eq(9L), anomalyInput.capture());
        assertEquals(0.42, (double) anomalyInput.getValue().get(RiskDetailKeys.ML_SCORE), 1e-9);
        assertEquals(0.42, RiskDetailKeys.readMlScore(result.getRiskDetails()), 1e-9);
    }
}
