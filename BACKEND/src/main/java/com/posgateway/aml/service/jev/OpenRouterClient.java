package com.posgateway.aml.service.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.jev.JevProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Low-level OpenRouter HTTP client. Only {@link JevDecisionGateway} should call this.
 */
@Component
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

    public record OpenRouterResponse(
            String content,
            String modelUsed,
            Integer inputTokens,
            Integer outputTokens,
            Double estimatedCostUsd,
            long latencyMs
    ) {}

    private final JevProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenRouterClient(JevProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public OpenRouterResponse chatCompletion(String model, String systemPrompt, String userPrompt,
                                             Duration timeout) throws Exception {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("JEV not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", properties.getMaxTokens());
        body.put("temperature", properties.getTemperature());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("response_format", Map.of("type", "json_object"));

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = webClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .header("HTTP-Referer", properties.getHttpReferer())
                    .header("X-Title", properties.getAppTitle())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(timeout)
                    .block();
        } catch (WebClientResponseException e) {
            log.warn("OpenRouter HTTP {} (model={})", e.getStatusCode().value(), model);
            throw e;
        }
        long latencyMs = System.currentTimeMillis() - start;

        if (response == null) {
            throw new IllegalStateException("OpenRouter returned empty response");
        }

        String content = extractContent(response);
        JsonNode usage = response.path("usage");
        Integer inputTokens = usage.path("prompt_tokens").asInt(0);
        Integer outputTokens = usage.path("completion_tokens").asInt(0);
        Double cost = usage.has("total_cost") ? usage.path("total_cost").asDouble() : null;
        String modelUsed = response.path("model").asText(model);

        return new OpenRouterResponse(content, modelUsed, inputTokens, outputTokens, cost, latencyMs);
    }

    private static String extractContent(JsonNode response) {
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).path("message").path("content").asText(null);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://openrouter.ai/api/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Redact API key from any string before logging. */
    public static String redactSecrets(String text, JevProperties properties) {
        if (text == null || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return text;
        }
        return text.replace(properties.getApiKey(), "[REDACTED]");
    }
}
