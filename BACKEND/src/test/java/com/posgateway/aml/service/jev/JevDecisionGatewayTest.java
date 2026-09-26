package com.posgateway.aml.service.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.jev.JevProperties;
import com.posgateway.aml.entity.jev.JevDecisionAudit;
import com.posgateway.aml.repository.jev.JevDecisionAuditRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JevDecisionGatewayTest {

    @Mock private JevDecisionAuditRepository auditRepository;
    @Mock private com.posgateway.aml.repository.jev.JevEngineSettingRepository engineSettingRepository;
    @Mock private com.posgateway.aml.repository.jev.JevDailySpendRepository dailySpendRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockDecisionsServer mockServer;
    private JevProperties properties;
    private JevDecisionGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockDecisionsServer();
        mockServer.start();

        properties = new JevProperties();
        properties.setApiKey("test-key");
        properties.setDecisionsBaseUrl(mockServer.baseUrl());
        properties.setShadowMode(true);
        properties.setPromoted(false);

        when(auditRepository.save(any())).thenAnswer(inv -> {
            JevDecisionAudit audit = inv.getArgument(0);
            audit.setId(99L);
            return audit;
        });

        JevQuestionConfigService questionConfig = new JevQuestionConfigService(objectMapper);
        JevStateBuilderService stateBuilder = new JevStateBuilderService();
        JevBandsService bandsService = new JevBandsService(properties);
        JevBranchEvaluator branchEvaluator = new JevBranchEvaluator(bandsService);
        JevDecisionsClient decisionsClient = new JevDecisionsClient(properties, objectMapper);
        JevBudgetService budgetService = new JevBudgetService(properties, dailySpendRepository);
        JevEngineConfigService engineConfig = new JevEngineConfigService(engineSettingRepository);
        when(engineSettingRepository.findById(any())).thenReturn(java.util.Optional.empty());
        JevAuditService auditService = new JevAuditService(auditRepository, objectMapper);

        gateway = new JevDecisionGateway(
                properties,
                decisionsClient,
                questionConfig,
                stateBuilder,
                branchEvaluator,
                new JevTightenOnlyAuthority(),
                engineConfig,
                budgetService,
                auditService,
                objectMapper);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    @Test
    void disabledWhenNoApiKey() {
        properties.setApiKey("");
        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals(JevBranch.ESCALATE_HUMAN, outcome.getBranch());
        assertEquals("JEV not configured (missing OPENROUTER_API_KEY)", outcome.getFallbackReason());
        assertEquals("ALLOW", outcome.getFinalDecision());
    }

    @Test
    void nullBaselineFailsClosedToReview() {
        properties.setApiKey("");
        JevDecisionContext ctx = JevDecisionContext.builder(JevEngineType.ALERT_TRIAGE)
                .baselineDecision(null)
                .build();
        JevDecisionOutcome outcome = gateway.decide(ctx);
        assertEquals("REVIEW", outcome.getFinalDecision());
    }

    @Test
    void happyPathParsesDp1Answers() throws Exception {
        mockServer.setResponse(200, loadResource("/jev/test/smoke_response.json"));

        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertFalse(outcome.isFallback());
        assertEquals(JevBranch.ESCALATE_UP, outcome.getBranch());
        assertEquals("gen-dec-1790353126-R935rN0YT2flXe74SiEJ", outcome.getRequestId());
        assertTrue(outcome.getModelSnapshot().startsWith(JevPinnedModel.SNAPSHOT_PREFIX));
        assertEquals("ALLOW", outcome.getFinalDecision());
        assertFalse(outcome.isAiApplied());
        assertTrue(outcome.isShadowMode());
        assertNotNull(outcome.getAnswers().get("laundering_suspicion"));
        assertEquals("noul", outcome.getAnswers().get("laundering_suspicion").path("type").asText());
    }

    @Test
    void unauthorizedEscalatesHuman() {
        mockServer.setResponse(401, "{\"error\":\"bad key\"}");
        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals(JevBranch.ESCALATE_HUMAN, outcome.getBranch());
        assertEquals("ALLOW", outcome.getFinalDecision());
    }

    @Test
    void serverErrorRetriesThenEscalates() {
        mockServer.setSequentialResponses(
                response(500, "error"),
                response(500, "error"));
        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals(JevBranch.ESCALATE_HUMAN, outcome.getBranch());
        assertTrue(mockServer.requestCount() >= 2);
    }

    @Test
    void malformedBodyEscalatesHuman() {
        mockServer.setResponse(200, "{\"model\":\"typesafe/jev-1.13-20260917\",\"answers\":{}}");
        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals(JevBranch.ESCALATE_HUMAN, outcome.getBranch());
    }

    @Test
    void wrongModelSnapshotEscalatesHuman() {
        mockServer.setResponse(200, "{\"model\":\"other/model-1\",\"answers\":{\"laundering_suspicion\":{\"type\":\"noul\",\"noul\":0.5}}}");
        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
    }

    @Test
    void shadowModeNeverMutatesDecisionEvenWhenPromoted() throws Exception {
        mockServer.setResponse(200, loadResource("/jev/test/smoke_response.json"));
        properties.setPromoted(true);
        properties.setShadowMode(true);

        JevDecisionOutcome outcome = gateway.decide(sampleContext());
        assertEquals("ALLOW", outcome.getFinalDecision());
        assertFalse(outcome.isAiApplied());
    }

    @Test
    void promotedNonShadowWouldApplyButStillRespectsTightenOnly() throws Exception {
        mockServer.setResponse(200, loadResource("/jev/test/smoke_response.json"));
        properties.setPromoted(true);
        properties.setShadowMode(false);

        JevDecisionContext ctx = JevDecisionContext.builder(JevEngineType.ALERT_TRIAGE)
                .pspId(1L)
                .baselineDecision("BLOCK")
                .feature("severity", "CRITICAL")
                .feature("score", 0.95)
                .build();
        JevDecisionOutcome outcome = gateway.decide(ctx);
        assertEquals("BLOCK", outcome.getFinalDecision());
        assertFalse(outcome.isAiApplied());
    }

    @Test
    void allowlistStripsForbiddenFields() throws Exception {
        mockServer.setResponse(200, loadResource("/jev/test/smoke_response.json"));
        JevDecisionContext ctx = JevDecisionContext.builder(JevEngineType.ALERT_TRIAGE)
                .pspId(1L)
                .baselineDecision("ALLOW")
                .feature("pan_hash", "secret-hash")
                .feature("terminal_id", "term-1")
                .feature("description", "free text about customer John Doe")
                .feature("score", 0.62)
                .build();
        gateway.decide(ctx);
        String body = mockServer.lastRequestBody();
        assertNotNull(body);
        assertFalse(body.contains("secret-hash"));
        assertFalse(body.contains("term-1"));
        assertFalse(body.contains("John Doe"));
        assertFalse(body.contains("pan_hash"));
    }

    @Test
    void nonDecisionEngineEscalatesWithoutCallingApi() {
        JevDecisionContext ctx = JevDecisionContext.builder(JevEngineType.RULE_SUGGESTION)
                .baselineDecision("PREVIEW")
                .build();
        JevDecisionOutcome outcome = gateway.decide(ctx);
        assertTrue(outcome.isFallback());
        assertEquals(0, mockServer.requestCount());
    }

    private static JevDecisionContext sampleContext() {
        return JevDecisionContext.builder(JevEngineType.ALERT_TRIAGE)
                .pspId(1L)
                .baselineDecision("ALLOW")
                .feature("score", 0.62)
                .feature("severity", "MEDIUM")
                .transactionId(99L)
                .advisoryOnly(true)
                .build();
    }

    private String loadResource(String path) throws IOException {
        try (var in = getClass().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static MockResponse response(int status, String body) {
        return new MockResponse(status, body);
    }

    private record MockResponse(int status, String body) {}

    static final class MockDecisionsServer {
        private HttpServer server;
        private volatile MockResponse next = response(200, "{}");
        private java.util.Queue<MockResponse> queue;
        private volatile String lastBody;
        private final AtomicInteger requests = new AtomicInteger();

        void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/alpha/decisions", this::handle);
            server.start();
        }

        void stop() {
            if (server != null) {
                server.stop(0);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/alpha";
        }

        void setResponse(int status, String body) {
            this.next = response(status, body);
            this.queue = null;
        }

        void setSequentialResponses(MockResponse... responses) {
            this.queue = new java.util.ArrayDeque<>(java.util.Arrays.asList(responses));
        }

        int requestCount() {
            return requests.get();
        }

        String lastRequestBody() {
            return lastBody;
        }

        private void handle(HttpExchange exchange) throws IOException {
            requests.incrementAndGet();
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            MockResponse response = queue != null && !queue.isEmpty() ? queue.poll() : next;
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
