package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeController;
import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.EdgeEvaluation;
import com.hokeka.edge.EdgeRuleInterpreter;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.activation.EdgeIdentity;
import com.hokeka.edge.store.NoOpFeatureStore;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every /edge/evaluate response carries a UUID edgeAssessmentId, and the same id is shipped to the
 * Control Plane's /api/v1/edge/assessments in a transaction-free envelope (at-least-once). Also
 * covers the AI advisory call reaching /api/v1/edge/decision.
 */
class EdgeAssessmentCorrelationTest {

    private HttpServer server;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private SecureChannel channelToLocalServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            paths.add(ex.getRequestURI().getPath());
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{\"reasons\":[\"cp\"]}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status.get(), out.length);
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

    private EdgeController controller(EdgeAssessmentShipper shipper, EdgeRuleInterpreter.Decision decision) {
        EdgeEngine engine = mock(EdgeEngine.class);
        when(engine.evaluate(any())).thenReturn(decision);
        when(engine.activeVersion()).thenReturn(12L);
        when(engine.activeBundleHash()).thenReturn("sha256:abc");
        EdgeController controller = new EdgeController(engine, new EdgeMetricsAggregator("psp", "edge", 64, 1_000L),
                new NoOpFeatureStore(), null, shipper);
        ReflectionTestUtils.setField(controller, "failClosedOnStoreUnavailable", false);
        return controller;
    }

    @Test
    void evaluateMintsUuidAndShipsTransactionFreeEnvelopeWithSameId() throws Exception {
        ControlPlaneProperties props = new ControlPlaneProperties();
        props.setEnabled(true);
        EdgeAssessmentShipper shipper = new EdgeAssessmentShipper(props, channelToLocalServer(), authorizedActivation());
        EdgeController controller = controller(shipper, new EdgeRuleInterpreter.Decision(
                EdgeRuleInterpreter.Action.HOLD, 70, List.of(5L), List.of("velocity"), List.of(51L)));

        EdgeEvaluation first = controller.evaluate(Map.of("pan_hash", "h", "amount_cents", 125000,
                "merchant_id", "m-1")).getBody();
        EdgeEvaluation second = controller.evaluate(Map.of("pan_hash", "h")).getBody();

        UUID.fromString(first.edgeAssessmentId());
        assertNotEquals(first.edgeAssessmentId(), second.edgeAssessmentId());
        assertEquals(12L, first.ruleBundleVersion());
        assertEquals(2, shipper.backlog());

        shipper.ship();

        assertEquals(0, shipper.backlog());
        assertEquals(List.of(EdgeAssessmentShipper.ASSESSMENTS_PATH, EdgeAssessmentShipper.ASSESSMENTS_PATH), paths);
        JsonNode env = new ObjectMapper().readTree(bodies.get(0));
        assertEquals(first.edgeAssessmentId(), env.path("edgeAssessmentId").asText());
        assertEquals("HOLD", env.path("decision").asText());
        assertEquals(5L, env.path("findings").get(0).path("ruleId").asLong());
        assertEquals(51L, env.path("findings").get(0).path("ruleVersionId").asLong());
        assertEquals("sha256:abc", env.path("bundleManifestHash").asText());
        assertTrue(env.path("featureSnapshot").isEmpty(), "no transaction data may leave the edge");
        assertFalse(bodies.get(0).contains("125000") || bodies.get(0).contains("m-1") || bodies.get(0).contains("pan"));
    }

    @Test
    void controlPlaneOutageKeepsEnvelopesAndRejectedEnvelopesAreDropped() throws Exception {
        ControlPlaneProperties props = new ControlPlaneProperties();
        props.setEnabled(true);
        EdgeAssessmentShipper shipper = new EdgeAssessmentShipper(props, channelToLocalServer(), authorizedActivation());
        EdgeController controller = controller(shipper, new EdgeRuleInterpreter.Decision(
                EdgeRuleInterpreter.Action.ALLOW, 0, List.of(), List.of()));
        controller.evaluate(Map.of());

        status.set(503);
        shipper.ship();
        assertEquals(1, shipper.backlog(), "5xx keeps the envelope for the next run");

        status.set(400);
        shipper.ship();
        assertEquals(0, shipper.backlog());
        assertEquals(1, shipper.dropped());
    }

    @Test
    void bufferIsBoundedAndDisabledControlPlaneShipsNothing() {
        ControlPlaneProperties props = new ControlPlaneProperties();
        props.setEnabled(true);
        props.setAssessmentBufferSize(2);
        EdgeAssessmentShipper shipper = new EdgeAssessmentShipper(props, mock(SecureChannel.class), authorizedActivation());
        EdgeEvaluation e = new EdgeEvaluation(EdgeRuleInterpreter.Action.ALLOW, 0, List.of(), List.of(), List.of(),
                UUID.randomUUID().toString(), 1L, "");
        shipper.enqueue(e, 1);
        shipper.enqueue(e, 1);
        shipper.enqueue(e, 1);
        assertEquals(2, shipper.backlog());
        assertEquals(1, shipper.dropped());

        props.setEnabled(false);
        EdgeAssessmentShipper off = new EdgeAssessmentShipper(props, mock(SecureChannel.class), authorizedActivation());
        off.enqueue(e, 1);
        assertEquals(0, off.backlog());
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
