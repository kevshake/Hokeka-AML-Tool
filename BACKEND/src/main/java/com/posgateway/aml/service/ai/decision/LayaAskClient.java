package com.posgateway.aml.service.ai.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.ai.AiDecisionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Laya {@code POST /v1/ask} for operator-only generation tasks (rule suggestion).
 * AML typed decisions use {@link LayaSystemOneClient} ({@code /v1/systemone}).
 */
@Component
public class LayaAskClient {

    private static final Logger log = LoggerFactory.getLogger(LayaAskClient.class);

    public record AskResponse(
            String id,
            String reply,
            int inputTokens,
            int outputTokens,
            long latencyMs
    ) {}

    private final AiDecisionProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public LayaAskClient(AiDecisionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(normalizeBaseUrl(properties.getApiBaseUrl()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public AskResponse ask(String prompt, Duration timeout) throws Exception {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Laya not configured (missing LAYA_API_KEY)");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", prompt);

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = webClient.post()
                    .uri("/v1/ask")
                    .headers(headers -> applyAuth(headers))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(timeout)
                    .block();
        } catch (WebClientResponseException e) {
            log.warn("Laya ask HTTP {}", e.getStatusCode().value());
            throw e;
        }
        long latencyMs = System.currentTimeMillis() - start;
        if (response == null) {
            throw new IllegalStateException("Laya ask returned empty response");
        }
        JsonNode usage = response.path("usage");
        JsonNode llm = response.path("llm");
        int inputTokens = usage.path("input_tokens").asInt(llm.path("input_tokens").asInt(0));
        int outputTokens = usage.path("output_tokens").asInt(llm.path("output_tokens").asInt(0));
        return new AskResponse(
                response.path("id").asText(null),
                response.path("reply").asText(""),
                inputTokens,
                outputTokens,
                latencyMs);
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.laya.studio";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private void applyAuth(HttpHeaders headers) {
        headers.setBearerAuth(properties.getApiKey());
    }

    public static String redactSecrets(String text, AiDecisionProperties properties) {
        if (text == null || text.isBlank() || properties.getApiKey() == null) {
            return text;
        }
        return text.replace(properties.getApiKey(), "[REDACTED]");
    }
}
