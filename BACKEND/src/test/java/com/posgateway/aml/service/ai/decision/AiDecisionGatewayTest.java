package com.posgateway.aml.service.ai.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.ai.AiDecisionProperties;
import com.posgateway.aml.entity.ai.AiDecisionAudit;
import com.posgateway.aml.repository.ai.AiDecisionAuditRepository;
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
class AiDecisionGatewayTest {

    @Mock private AiDecisionAuditRepository auditRepository;
    @Mock private com.posgateway.aml.repository.ai.AiEngineSettingRepository engineSettingRepository;
    @Mock private com.posgateway.aml.repository.ai.AiDailySpendRepository dailySpendRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockDecisionsServer mockServer;
    private AiDecisionProperties properties;
    private AiDecisionGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockDecisionsServer();
        mockServer.start();

        properties = new AiDecisionProperties();
        properties.setApiKey("test-key");
        properties.setApiBaseUrl(mockServer.baseUrl());
        properties.setShadowMode(true);
        properties.setPromoted(false);

        when(auditRepository.save(any())).thenAnswer(inv -> {
            AiDecisionAudit audit = inv.getArgument(0);
            audit.setId(99L);
            return audit;
        });

        AiQuestionConfigService questionConfig = new AiQuestionConfigService(objectMapper);
        AiStateBuilderService stateBuilder = new AiStateBuilderService();
        AiBandsService bandsService = new AiBandsService(properties);
        AiBranchEvaluator branchEvaluator = new AiBranchEvaluator(bandsService);
        LayaSystemOneClient decisionsClient = new LayaSystemOneClient(properties, objectMapper);
        AiBudgetService budgetService = new AiBudgetService(properties, dailySpendRepository);
        AiEngineConfigService engineConfig = new AiEngineConfigService(engineSettingRepository);
        when(engineSettingRepository.findById(any())).thenReturn(java.util.Optional.empty());
        AiAuditService auditService = new AiAuditService(auditRepository, objectMapper);

        gateway = new AiDecisionGateway(
                properties,
                decisionsClient,
                questionConfig,
                stateBuilder,
                branchEvaluator,
                new AiTightenOnlyAuthority(),
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
        AiDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals(AiBranch.ESCALATE_HUMAN, outcome.getBranch());
        assertEquals("Hokeka AI not configured (missing LAYA_API_KEY)", outcome.getFallbackReason());
        assertEquals("ALLOW", outcome.getFinalDecision());
    }

    @Test
    void nullBaselineFailsClosedToReview() {
        properties.setApiKey("");
        AiDecisionContext ctx = AiDecisionContext.builder(AiEngineType.ALERT_TRIAGE)
                .baselineDecision(null)
                .build();
        AiDecisionOutcome outcome = gateway.decide(ctx);
        assertEquals("REVIEW", outcome.getFinalDecision());
    }

    @Test
    void happyPathParsesDp1Answers() throws Exception {
        mockServer.setResponse(200, loadResource("/hokeka-ai/test/smoke_response.json"));

        AiDecisionOutcome outcome = gateway.decide(sampleContext());
        assertFalse(outcome.isFallback());
        assertEquals(AiBranch.ESCALATE_UP, outcome.getBranch());
        assertEquals("lay-dec-1790353126-R935rN0YT2flXe74SiEJ", outcome.getRequestId());
        assertEquals("english", outcome.getModelSnapshot());
        assertEquals("ALLOW", outcome.getFinalDecision());
        assertFalse(outcome.isAiApplied());
        assertTrue(outcome.isShadowMode());
        assertNotNull(outcome.getAnswers().get("laundering_suspicion"));
        assertEquals("noul", outcome.getAnswers().get("laundering_suspicion").path("type").asText());
    }

    @Test
    void unauthorizedEscalatesHuman() {
        mockServer.setResponse(401, "{\"error\":\"bad key\"}");
        AiDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals(AiBranch.ESCALATE_HUMAN, outcome.getBranch());
        assertEquals("ALLOW", outcome.getFinalDecision());
    }

    @Test
    void serverErrorRetriesThenEscalates() {
        mockServer.setSequentialResponses(
                response(500, "error"),
                response(500, "error"));
        AiDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals(AiBranch.ESCALATE_HUMAN, outcome.getBranch());
        assertTrue(mockServer.requestCount() >= 2);
    }

    @Test
    void malformedBodyEscalatesHuman() {
        mockServer.setResponse(200, "{\"model\":\"english\",\"answers\":{}}");
        AiDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals(AiBranch.ESCALATE_HUMAN, outcome.getBranch());
    }

    @Test
    void wrongModelSnapshotEscalatesHuman() {
        mockServer.setResponse(200, "{\"model\":\"other/model-1\",\"answers\":{\"laundering_suspicion\":{\"type\":\"noul\",\"noul\":0.5}}}");
        AiDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
    }

    @Test
    void shadowModeNeverMutatesDecisionEvenWhenPromoted() throws Exception {
        mockServer.setResponse(200, loadResource("/hokeka-ai/test/smoke_response.json"));
        properties.setPromoted(true);
        properties.setShadowMode(true);

        AiDecisionOutcome outcome = gateway.decide(sampleContext());
        assertEquals("ALLOW", outcome.getFinalDecision());
        assertFalse(outcome.isAiApplied());
    }

    @Test
    void promotedNonShadowWouldApplyButStillRespectsTightenOnly() throws Exception {
        mockServer.setResponse(200, loadResource("/hokeka-ai/test/smoke_response.json"));
        properties.setPromoted(true);
        properties.setShadowMode(false);

        AiDecisionContext ctx = AiDecisionContext.builder(AiEngineType.ALERT_TRIAGE)
                .pspId(1L)
                .baselineDecision("BLOCK")
                .feature("severity", "CRITICAL")
                .feature("score", 0.95)
                .build();
        AiDecisionOutcome outcome = gateway.decide(ctx);
        assertEquals("BLOCK", outcome.getFinalDecision());
        assertFalse(outcome.isAiApplied());
    }

    @Test
    void allowlistStripsForbiddenFields() throws Exception {
        mockServer.setResponse(200, loadResource("/hokeka-ai/test/smoke_response.json"));
        AiDecisionContext ctx = AiDecisionContext.builder(AiEngineType.ALERT_TRIAGE)
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
    void lowConfidenceFallsBackToRules() throws Exception {
        mockServer.setResponse(200, loadResource("/hokeka-ai/test/smoke_response.json"));
        properties.setMinConfidenceToApply(1.1);

        AiDecisionOutcome outcome = gateway.decide(sampleContext());
        assertTrue(outcome.isFallback());
        assertEquals(AiBranch.ESCALATE_HUMAN, outcome.getBranch());
        assertEquals("ALLOW", outcome.getFinalDecision());
        assertTrue(outcome.getFallbackReason().toLowerCase().contains("confidence"));
    }

    @Test
    void nonDecisionEngineEscalatesWithoutCallingApi() {
        AiDecisionContext ctx = AiDecisionContext.builder(AiEngineType.RULE_SUGGESTION)
                .baselineDecision("PREVIEW")
                .build();
        AiDecisionOutcome outcome = gateway.decide(ctx);
        assertTrue(outcome.isFallback());
        assertEquals(0, mockServer.requestCount());
    }

    private static AiDecisionContext sampleContext() {
        return AiDecisionContext.builder(AiEngineType.ALERT_TRIAGE)
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
            server.createContext("/v1/systemone", this::handle);
            server.start();
        }

        void stop() {
            if (server != null) {
                server.stop(0);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
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
