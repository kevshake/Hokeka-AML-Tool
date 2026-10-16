package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeRuleInterpreter;
import com.hokeka.edge.activation.ActivationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edge → Control Plane Hokeka AI advisory client. The edge never holds vendor credentials;
 * all AI decisioning is proxied through the Control Plane over mTLS.
 */
@Component
@ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HokekaAiDecisionClient {

    private static final Logger log = LoggerFactory.getLogger(HokekaAiDecisionClient.class);

    /**
     * Control Plane decision endpoint. The Control Plane serves its API under the {@code /api/v1}
     * context path, like {@code /api/v1/edge/bundle} and {@code /api/v1/edge/metrics}; the former
     * {@code /edge/decision} never reached it, so every advisory call silently fell back.
     */
    static final String DECISION_PATH = "/api/v1/edge/decision";

    private final SecureChannel channel;
    private final ActivationService activation;
    private final ObjectMapper mapper = new ObjectMapper();

    public HokekaAiDecisionClient(SecureChannel channel, ActivationService activation) {
        this.channel = channel;
        this.activation = activation;
    }

    /**
     * Request Hokeka AI advisory for a borderline edge decision. Never throws — returns the baseline on failure.
     */
    public EdgeRuleInterpreter.Decision enrichBorderline(EdgeRuleInterpreter.Decision baseline,
                                                         Map<String, Object> features) {
        if (!activation.authorized()) {
            return baseline;
        }
        if (!isBorderline(baseline)) {
            return baseline;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("engine", "TRANSACTION_RISK");
            body.put("baselineDecision", baseline.action().name());
            body.put("features", features);
            body.put("async", false);

            String json = mapper.writeValueAsString(body);
            String response = channel.mutualTlsClient().post()
                    .uri(DECISION_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return baseline;
            }
            return mergeAdvisory(baseline, mapper.readTree(response));
        } catch (Exception e) {
            log.debug("Hokeka AI advisory unavailable, using rules baseline: {}", e.getMessage());
            return baseline;
        }
    }

    /**
     * Folds the Control Plane's advisory into the baseline decision. Advisory only: the action, score
     * and triggered rules always stay on the deterministic baseline.
     *
     * <p>Only an advisory the AI layer actually produced ({@code aiApplied=true}, {@code fallback=false})
     * is merged. With no AI configured, or inline mode off, the Control Plane answers a fallback
     * carrying a placeholder {@code aiRecommendation} (ESCALATE); appending that as
     * "Hokeka AI recommendation: ESCALATE" told PSP operators an AI had reviewed a transaction it never saw.
     */
    static EdgeRuleInterpreter.Decision mergeAdvisory(EdgeRuleInterpreter.Decision baseline, JsonNode node) {
        if (node == null || !node.path("aiApplied").asBoolean(false) || node.path("fallback").asBoolean(false)) {
            return baseline;
        }
        List<String> mergedReasons = new java.util.ArrayList<>(baseline.reasons());
        if (node.path("aiRecommendation").isTextual()) {
            mergedReasons.add("Hokeka AI recommendation: " + node.path("aiRecommendation").asText()
                    + (node.path("confidence").isNumber()
                    ? " (confidence=" + node.path("confidence").asDouble() + ")" : ""));
        }
        mergedReasons.addAll(extractStringList(node.path("reasons")));
        // Keep the triggered rule VERSION ids too: the assessment envelope cites them, and the
        // 4-argument constructor silently reset them to an empty list.
        return new EdgeRuleInterpreter.Decision(
                baseline.action(),
                baseline.score(),
                baseline.triggeredRuleIds(),
                mergedReasons,
                baseline.triggeredRuleVersionIds());
    }

    private static boolean isBorderline(EdgeRuleInterpreter.Decision decision) {
        return decision.action() == EdgeRuleInterpreter.Action.ALERT
                || decision.action() == EdgeRuleInterpreter.Action.HOLD;
    }

    private static List<String> extractStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        node.forEach(n -> out.add(n.asText()));
        return out;
    }
}
