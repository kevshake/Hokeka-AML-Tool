package com.posgateway.aml.service.ai.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads versioned Jev question definitions from {@code resources/hokeka-ai/questions/v1.json}.
 */
@Service
public class AiQuestionConfigService {

    private final ObjectMapper objectMapper;
    private final JsonNode root;
    private final String version;
    private final String configId;

    public AiQuestionConfigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try (InputStream in = new ClassPathResource("hokeka-ai/questions/v1.json").getInputStream()) {
            this.root = objectMapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load hokeka-ai/questions/v1.json", e);
        }
        this.version = root.path("version").asText("unknown");
        this.configId = root.path("config_id").asText("hokeka-ai-questions");
    }

    public String getVersion() {
        return version;
    }

    public String getConfigId() {
        return configId;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> questionsFor(AiDecisionPoint decisionPoint) {
        JsonNode dpNode = root.path("decision_points").path(decisionPoint.getConfigKey()).path("questions");
        if (dpNode.isMissingNode() || dpNode.isEmpty()) {
            throw new IllegalStateException("No questions configured for " + decisionPoint);
        }
        return objectMapper.convertValue(dpNode, Map.class);
    }

    public Map<String, String> questionTypesFor(AiDecisionPoint decisionPoint) {
        JsonNode dpNode = root.path("decision_points").path(decisionPoint.getConfigKey()).path("questions");
        Map<String, String> types = new LinkedHashMap<>();
        dpNode.fields().forEachRemaining(entry ->
                types.put(entry.getKey(), entry.getValue().path("type").asText()));
        return types;
    }
}
