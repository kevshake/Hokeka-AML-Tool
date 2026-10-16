package com.hokeka.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.channel.EdgeMetricsAggregator;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.ledger.EdgeLedger;
import com.hokeka.edge.ledger.EvaluationRecorder;
import com.hokeka.edge.ledger.LedgerEvaluation;
import com.hokeka.edge.ledger.LedgerProperties;
import com.hokeka.edge.store.NoOpFeatureStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class EdgeControllerFailClosedTest {

    private final List<LedgerEvaluation> recorded = new CopyOnWriteArrayList<>();

    /** Deliberate test double (intentional-stub): captures ledger rows instead of writing them. */
    private EvaluationRecorder capturingRecorder() {
        ObjectMapper mapper = new ObjectMapper();
        return new EvaluationRecorder(new EdgeLedger(new LedgerProperties(), mapper, "edge"),
                ContractSchemas.fromDirectory(Path.of(System.getProperty("contracts.dir", "../docs/contracts"))),
                mapper) {
            @Override
            public LedgerEvaluation record(LedgerEvaluation ev) {
                recorded.add(ev);
                return ev;
            }
        };
    }

    @Test
    void evaluateReturnsHoldWhenFeatureStoreUnavailableAndFailClosedEnabled() {
        EdgeEngine engine = mock(EdgeEngine.class);
        EdgeMetricsAggregator metrics = new EdgeMetricsAggregator("psp", "edge", 64, 1_000L);
        EdgeController controller = new EdgeController(engine, metrics, new NoOpFeatureStore(), null,
                capturingRecorder(), "edge", "site");
        ReflectionTestUtils.setField(controller, "failClosedOnStoreUnavailable", true);

        EdgeEvaluation decision = (EdgeEvaluation) controller.evaluate(
                Map.of("pan_hash", "card-hash", "amount_cents", 1000)).getBody();

        assertEquals(EdgeRuleInterpreter.Action.HOLD, decision.action());
        assertEquals(1, decision.reasons().size());
        assertEquals(true, decision.reasons().get(0).contains("fail-closed"));
        assertEquals("FEATURE_STORE_UNAVAILABLE", recorded.get(0).failClosedCode());
        assertEquals("none", recorded.get(0).evaluator());
    }

    @Test
    void evaluateProceedsWhenFailClosedDisabled() {
        EdgeEngine engine = mock(EdgeEngine.class);
        EdgeRuleInterpreter.Decision allow = new EdgeRuleInterpreter.Decision(
                EdgeRuleInterpreter.Action.ALLOW, 0, java.util.List.of(), java.util.List.of());
        org.mockito.Mockito.when(engine.evaluateWithProvenance(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new EdgeEngine.Outcome(allow, "java", null));

        EdgeMetricsAggregator metrics = new EdgeMetricsAggregator("psp", "edge", 64, 1_000L);
        EdgeController controller = new EdgeController(engine, metrics, new NoOpFeatureStore(), null,
                capturingRecorder(), "edge", "site");
        ReflectionTestUtils.setField(controller, "failClosedOnStoreUnavailable", false);

        EdgeEvaluation decision = (EdgeEvaluation) controller.evaluate(
                Map.of("pan_hash", "card-hash", "amount_cents", 1000)).getBody();

        assertEquals(EdgeRuleInterpreter.Action.ALLOW, decision.action());
        assertEquals(null, recorded.get(0).failClosedCode());
    }

    @Test
    void engineGatesReportTheirFailClosedCode() {
        EdgeEngine unauthorized = new EdgeEngine(new com.hokeka.edge.activation.AuthorizationGate() {
            @Override
            public boolean authorized() {
                return false;
            }

            @Override
            public String state() {
                return STATE_UNAUTHORIZED;
            }

            @Override
            public String reason() {
                return "pending";
            }
        });
        assertEquals("NODE_NOT_AUTHORIZED", unauthorized.evaluateWithProvenance(Map.of()).failClosedCode());

        EdgeEngine noBundle = new EdgeEngine(com.hokeka.edge.activation.AuthorizationGate.alwaysAuthorized());
        EdgeEngine.Outcome outcome = noBundle.evaluateWithProvenance(Map.of());
        assertEquals("NO_BUNDLE", outcome.failClosedCode());
        assertEquals("none", outcome.evaluator());
        assertEquals(EdgeRuleInterpreter.Action.HOLD, outcome.decision().action());
    }
}
