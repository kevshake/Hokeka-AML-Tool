package com.hokeka.edge;

import com.hokeka.edge.channel.EdgeMetricsAggregator;
import com.hokeka.edge.store.NoOpFeatureStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class EdgeControllerFailClosedTest {

    @Test
    void evaluateReturnsHoldWhenFeatureStoreUnavailableAndFailClosedEnabled() {
        EdgeEngine engine = mock(EdgeEngine.class);
        EdgeMetricsAggregator metrics = new EdgeMetricsAggregator("psp", "edge", 64, 1_000L);
        EdgeController controller = new EdgeController(engine, metrics, new NoOpFeatureStore(), null);
        ReflectionTestUtils.setField(controller, "failClosedOnStoreUnavailable", true);

        EdgeRuleInterpreter.Decision decision = controller.evaluate(
                Map.of("pan_hash", "card-hash", "amount_cents", 1000)).getBody();

        assertEquals(EdgeRuleInterpreter.Action.HOLD, decision.action());
        assertEquals(1, decision.reasons().size());
        assertEquals(true, decision.reasons().get(0).contains("fail-closed"));
    }

    @Test
    void evaluateProceedsWhenFailClosedDisabled() {
        EdgeEngine engine = mock(EdgeEngine.class);
        EdgeRuleInterpreter.Decision allow = new EdgeRuleInterpreter.Decision(
                EdgeRuleInterpreter.Action.ALLOW, 0, java.util.List.of(), java.util.List.of());
        org.mockito.Mockito.when(engine.evaluate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(allow);

        EdgeMetricsAggregator metrics = new EdgeMetricsAggregator("psp", "edge", 64, 1_000L);
        EdgeController controller = new EdgeController(engine, metrics, new NoOpFeatureStore(), null);
        ReflectionTestUtils.setField(controller, "failClosedOnStoreUnavailable", false);

        EdgeRuleInterpreter.Decision decision = controller.evaluate(
                Map.of("pan_hash", "card-hash", "amount_cents", 1000)).getBody();

        assertEquals(EdgeRuleInterpreter.Action.ALLOW, decision.action());
    }
}
