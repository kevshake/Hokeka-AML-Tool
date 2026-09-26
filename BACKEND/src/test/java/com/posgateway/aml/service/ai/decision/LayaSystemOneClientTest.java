package com.posgateway.aml.service.ai.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.ai.AiDecisionProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LayaSystemOneClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockLayaServer mockServer;
    private AiDecisionProperties properties;
    private LayaSystemOneClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockLayaServer();
        mockServer.start();
        properties = new AiDecisionProperties();
        properties.setApiKey("lsk_test_key");
        properties.setApiBaseUrl(mockServer.baseUrl());
        properties.setMaxRetries(0);
        client = new LayaSystemOneClient(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    @Test
    void successParsesSystemoneResponse() throws Exception {
        mockServer.setResponse(200, """
                {"id":"id-1","model":"english","answers":{"same_entity":{"type":"noul","noul":0.4,"confidence":0.8}},
                "usage":{"input_tokens":120,"output_tokens":0}}
                """);
        Map<String, String> expected = Map.of("same_entity", "noul");
        LayaSystemOneClient.DecisionsResponse response = client.decide(
                new LayaSystemOneClient.DecisionsRequest(Map.of("k", "v"), Map.of(
                        "same_entity", Map.of("type", "noul", "instructions", "test")),
                        null, null),
                expected,
                Duration.ofSeconds(2));
        assertEquals("id-1", response.id());
        assertEquals("english", response.modelSnapshot());
        assertEquals(120, response.inputTokens());
        assertEquals("/v1/systemone", mockServer.lastRequestPath());
    }

    @Test
    void http401SurfacesAsClientError() {
        mockServer.setResponse(401, "{\"error\":{\"message\":\"bad key\"}}");
        assertThrows(WebClientResponseException.class, () -> client.decide(
                new LayaSystemOneClient.DecisionsRequest(Map.of(), Map.of(
                        "same_entity", Map.of("type", "noul", "instructions", "test")),
                        null, null),
                Map.of("same_entity", "noul"),
                Duration.ofSeconds(2)));
    }

    @Test
    void timeoutFailsClosed() {
        mockServer.setDelayMs(2000);
        mockServer.setResponse(200, "{\"model\":\"english\",\"answers\":{\"same_entity\":{\"type\":\"noul\",\"noul\":0.5}}}");
        assertThrows(Exception.class, () -> client.decide(
                new LayaSystemOneClient.DecisionsRequest(Map.of(), Map.of(
                        "same_entity", Map.of("type", "noul", "instructions", "test")),
                        null, null),
                Map.of("same_entity", "noul"),
                Duration.ofMillis(50)));
    }

    @Test
    void missingApiKeyRejectedBeforeHttp() {
        properties.setApiKey("");
        assertThrows(IllegalStateException.class, () -> client.decide(
                new LayaSystemOneClient.DecisionsRequest(Map.of(), Map.of(), null, null),
                Map.of(),
                Duration.ofSeconds(1)));
    }

    static final class MockLayaServer {
        private HttpServer server;
        private volatile int status = 200;
        private volatile String body = "{}";
        private volatile int delayMs;
        private volatile String lastBody;
        private volatile String lastPath;
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
            this.status = status;
            this.body = body;
        }

        void setDelayMs(int delayMs) {
            this.delayMs = delayMs;
        }

        String lastRequestBody() {
            return lastBody;
        }

        String lastRequestPath() {
            return lastPath;
        }

        private void handle(HttpExchange exchange) throws IOException {
            requests.incrementAndGet();
            lastPath = exchange.getRequestURI().getPath();
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
