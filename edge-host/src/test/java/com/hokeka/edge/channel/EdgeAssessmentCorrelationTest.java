package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeController;
import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.EdgeEvaluation;
import com.hokeka.edge.EdgeRuleInterpreter;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.activation.EdgeIdentity;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.ledger.EdgeLedger;
import com.hokeka.edge.ledger.EvaluationRecorder;
import com.hokeka.edge.ledger.LedgerEvaluation;
import com.hokeka.edge.ledger.LedgerProperties;
import com.hokeka.edge.store.NoOpFeatureStore;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every /edge/evaluate response carries a fresh UUID edgeAssessmentId, and the ledger row handed
 * to the recorder carries the same id, the node/site ids and the contract decision. (Delivery of the
 * resulting decision records is covered against a real ledger by OutboxShipperIntegrationTest.)
 * Also covers the AI advisory call reaching /api/v1/edge/decision.
 */
class EdgeAssessmentCorrelationTest {

    private HttpServer server;
    private final List<String> paths = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private SecureChannel channelToLocalServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            paths.add(ex.getRequestURI().getPath());
            ex.getRequestBody().readAllBytes();
            byte[] out = "{\"reasons\":[\"cp\"],\"aiApplied\":true,\"fallback\":false}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        SecureChannel channel = mock(SecureChannel.class);
        when(channel.mutualTlsClient()).thenAnswer(inv ->
                RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build());
        return channel;
    }

    private ActivationService authorizedActivation() {
        ActivationService activation = mock(ActivationService.class);
        when(activation.authorized()).thenReturn(true);
        when(activation.loadOrCreateIdentity()).thenReturn(
                new EdgeIdentity("edge-7", "psp-1", "", "", "", "", "", EdgeIdentity.STATUS_ACTIVE));
        return activation;
    }

    @Test
    void evaluateMintsAFreshUuidAndRecordsTheRowUnderTheSameId() {
        List<LedgerEvaluation> recorded = new CopyOnWriteArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        // Deliberate test double (intentional-stub): captures rows instead of writing them to Postgres.
        EvaluationRecorder capturing = new EvaluationRecorder(new EdgeLedger(new LedgerProperties(), mapper, "edge-7"),
                ContractSchemas.fromDirectory(Path.of(System.getProperty("contracts.dir", "../docs/contracts"))),
                mapper) {
            @Override
            public LedgerEvaluation record(LedgerEvaluation ev) {
                recorded.add(ev);
                return ev;
            }
        };
        EdgeEngine engine = mock(EdgeEngine.class);
        when(engine.evaluateWithProvenance(any())).thenReturn(new EdgeEngine.Outcome(new EdgeRuleInterpreter.Decision(
                EdgeRuleInterpreter.Action.HOLD, 70, List.of(5L), List.of("velocity"), List.of(51L)), "java", null));
        when(engine.activeVersion()).thenReturn(12L);
        when(engine.activeBundleHash()).thenReturn("sha256:" + "0".repeat(64));
        EdgeController controller = new EdgeController(engine, new EdgeMetricsAggregator("psp", "edge", 64, 1_000L),
                new NoOpFeatureStore(), null, capturing, "edge-7", "site-7");
        ReflectionTestUtils.setField(controller, "failClosedOnStoreUnavailable", false);

        EdgeEvaluation first = (EdgeEvaluation) controller.evaluate(Map.of("pan_hash", "h", "amount_cents", 125000,
                "merchant_id", "m-1")).getBody();
        EdgeEvaluation second = (EdgeEvaluation) controller.evaluate(Map.of("pan_hash", "h")).getBody();

        UUID.fromString(first.edgeAssessmentId());
        assertNotEquals(first.edgeAssessmentId(), second.edgeAssessmentId());
        assertEquals(12L, first.ruleBundleVersion());
        assertEquals(2, recorded.size());
        LedgerEvaluation row = recorded.get(0);
        assertEquals(first.edgeAssessmentId(), row.edgeAssessmentId().toString());
        assertEquals("edge-7", row.nodeId());
        assertEquals("site-7", row.siteId());
        assertEquals("HOLD", row.decision());
        assertEquals(List.of(5L), row.triggeredRuleIds());
        assertEquals(List.of(51L), row.ruleVersionIds());
        assertEquals("java", row.evaluator());
        assertEquals("h", row.panHash());
        assertTrue(!row.features().containsKey("pan_hash"));
    }

    @Test
    void aiAdvisoryCallsTheControlPlaneApiPath() throws Exception {
        HokekaAiDecisionClient client = new HokekaAiDecisionClient(channelToLocalServer(), authorizedActivation());
        EdgeRuleInterpreter.Decision baseline = new EdgeRuleInterpreter.Decision(
                EdgeRuleInterpreter.Action.HOLD, 40, List.of(), List.of("baseline"));

        EdgeRuleInterpreter.Decision enriched = client.enrichBorderline(baseline, Map.of("amount_cents", 1));

        assertEquals(List.of("/api/v1/edge/decision"), paths);
        assertEquals(EdgeRuleInterpreter.Action.HOLD, enriched.action());
        assertTrue(enriched.reasons().contains("cp"));
    }
}
