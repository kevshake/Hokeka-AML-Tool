package com.posgateway.aml.service.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.jev.JevProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Laya Studio client for typed AML decisions ({@code POST /v1/systemone}).
 * Only {@link JevDecisionGateway} should call this for production decisions.
 */
@Component
public class JevDecisionsClient {

    private static final Logger log = LoggerFactory.getLogger(JevDecisionsClient.class);
    private static final double PROBABILITY_SUM_TOLERANCE = 0.05;

    public record DecisionsRequest(
            Map<String, Object> state,
            Map<String, Object> questions,
            String sessionId,
            Map<String, Object> trace
    ) {}

    public record DecisionsResponse(
            String id,
            String modelSnapshot,
            Map<String, JsonNode> answers,
            int inputTokens,
            int outputTokens,
            double costUsd,
            long latencyMs
    ) {}

    private final JevProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public JevDecisionsClient(JevProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(LayaAskClient.normalizeBaseUrl(properties.getApiBaseUrl()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public DecisionsResponse decide(DecisionsRequest request,
                                    Map<String, String> expectedQuestionTypes,
                                    Duration timeout) throws Exception {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Hokeka AI not configured (missing LAYA_API_KEY)");
        }

        Map<String, Object> state = LayaStateTrimmer.trim(request.state(), properties.getLang(), objectMapper);

        Map<String, Object> body = new LinkedHashMap<>();
        String model = JevPinnedModel.requestModel(properties.getModel());
        if (model != null) {
            body.put("model", model);
        }
        if (properties.getLang() != null && !properties.getLang().isBlank()) {
            body.put("lang", properties.getLang().trim());
        }
        body.put("state", state);
        body.put("questions", request.questions());

        int maxAttempts = properties.getMaxRetries() + 1;
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return executeOnce(body, expectedQuestionTypes, timeout);
            } catch (WebClientResponseException e) {
                lastError = e;
                if (!isRetryable(e.getStatusCode()) || attempt >= maxAttempts) {
                    throw e;
                }
                backoff(attempt);
            } catch (Exception e) {
                lastError = e;
                if (attempt >= maxAttempts) {
                    throw e;
                }
                backoff(attempt);
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("Laya systemone call failed");
    }

    private DecisionsResponse executeOnce(Map<String, Object> body,
                                          Map<String, String> expectedQuestionTypes,
                                          Duration timeout) throws Exception {
        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = webClient.post()
                    .uri("/v1/systemone")
                    .headers(this::applyAuth)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(timeout)
                    .block();
        } catch (WebClientResponseException e) {
            log.warn("Laya systemone HTTP {}", e.getStatusCode().value());
            throw e;
        }
        long latencyMs = System.currentTimeMillis() - start;

        if (response == null) {
            throw new IllegalStateException("Laya systemone returned empty response");
        }

        String modelSnapshot = response.path("model").asText(null);
        if (!JevPinnedModel.isValidResponseModel(modelSnapshot)) {
            JsonNode routing = response.path("routing");
            modelSnapshot = routing.path("model").asText(modelSnapshot);
        }
        if (!JevPinnedModel.isValidResponseModel(modelSnapshot)) {
            throw new IllegalStateException("Missing model in Laya response");
        }

        JsonNode answersNode = response.path("answers");
        validateAnswers(answersNode, expectedQuestionTypes);

        Map<String, JsonNode> answers = new LinkedHashMap<>();
        answersNode.fields().forEachRemaining(entry -> answers.put(entry.getKey(), entry.getValue()));

        JsonNode usage = response.path("usage");
        int inputTokens = usage.path("input_tokens").asInt(0);
        int outputTokens = usage.path("output_tokens").asInt(0);
        String id = response.path("id").asText(null);

        return new DecisionsResponse(id, modelSnapshot, answers, inputTokens, outputTokens, 0.0, latencyMs);
    }

    private void applyAuth(HttpHeaders headers) {
        headers.setBearerAuth(properties.getApiKey());
        if (properties.isSwissDataResidency()) {
            headers.set("X-Laya-Swiss-Only", "true");
        }
    }

    static void validateAnswers(JsonNode answersNode, Map<String, String> expectedQuestionTypes) {
        if (answersNode == null || !answersNode.isObject()) {
            throw new IllegalStateException("Missing answers object");
        }
        for (Map.Entry<String, String> expected : expectedQuestionTypes.entrySet()) {
            JsonNode answer = answersNode.path(expected.getKey());
            if (answer.isMissingNode()) {
                throw new IllegalStateException("Missing answer for question: " + expected.getKey());
            }
            String type = answer.path("type").asText(null);
            if (!expected.getValue().equals(type)) {
                throw new IllegalStateException("Wrong type for " + expected.getKey() + ": " + type);
            }
            switch (type) {
                case "noul" -> validateNoul(answer);
                case "choice" -> validateChoice(answer);
                case "score" -> validateScore(answer);
                default -> throw new IllegalStateException("Unknown answer type: " + type);
            }
        }
    }

    private static void validateNoul(JsonNode answer) {
        if (!answer.has("noul")) {
            throw new IllegalStateException("Missing noul value");
        }
        double noul = answer.path("noul").asDouble(Double.NaN);
        if (Double.isNaN(noul) || noul < 0.0 || noul > 1.0) {
            throw new IllegalStateException("noul out of range: " + noul);
        }
    }

    private static void validateChoice(JsonNode answer) {
        if (!answer.has("choice") || answer.path("choice").asText("").isBlank()) {
            throw new IllegalStateException("Missing choice value");
        }
        validateProbabilities(answer.path("probabilities"));
    }

    private static void validateScore(JsonNode answer) {
        if (!answer.has("score")) {
            throw new IllegalStateException("Missing score value");
        }
        JsonNode legend = answer.path("legend");
        if (!legend.isObject() || legend.isEmpty()) {
            throw new IllegalStateException("Missing score legend");
        }
        int maxIndex = legend.size() - 1;
        double score = answer.path("score").asDouble(Double.NaN);
        if (Double.isNaN(score) || score < 0.0 || score > maxIndex) {
            throw new IllegalStateException("score out of range: " + score);
        }
        validateProbabilities(answer.path("probabilities"));
    }

    private static void validateProbabilities(JsonNode probabilities) {
        if (!probabilities.isObject() || probabilities.isEmpty()) {
            throw new IllegalStateException("Missing probabilities");
        }
        double sum = 0.0;
        var fields = probabilities.fields();
        while (fields.hasNext()) {
            sum += fields.next().getValue().asDouble(0.0);
        }
        if (Math.abs(sum - 1.0) > PROBABILITY_SUM_TOLERANCE) {
            throw new IllegalStateException("probabilities sum to " + sum);
        }
    }

    private static boolean isRetryable(HttpStatusCode status) {
        int code = status.value();
        return code == 429 || code >= 500;
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** @deprecated use {@link LayaAskClient#normalizeBaseUrl(String)} */
    @Deprecated
    static String normalizeDecisionsBaseUrl(String baseUrl) {
        return LayaAskClient.normalizeBaseUrl(baseUrl);
    }
}
