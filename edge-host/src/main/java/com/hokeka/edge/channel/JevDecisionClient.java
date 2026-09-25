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
 * Edge → Control Plane JEV decision client. The edge never holds an OpenRouter key;
 * all AI calls are proxied through the Control Plane over mTLS.
 */
@Component
@ConditionalOnProperty(prefix = "hokeka.controlplane", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JevDecisionClient {

    private static final Logger log = LoggerFactory.getLogger(JevDecisionClient.class);

    private final SecureChannel channel;
    private final ActivationService activation;
    private final ObjectMapper mapper = new ObjectMapper();

    public JevDecisionClient(SecureChannel channel, ActivationService activation) {
        this.channel = channel;
        this.activation = activation;
    }

    /**
     * Request JEV advisory for a borderline edge decision. Never throws — returns the baseline on failure.
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
                    .uri("/edge/decision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return baseline;
            }
            JsonNode node = mapper.readTree(response);
            List<String> aiReasons = extractStringList(node.path("reasons"));
            List<String> mergedReasons = new java.util.ArrayList<>(baseline.reasons());
            if (node.path("aiRecommendation").isTextual()) {
                mergedReasons.add("JEV recommendation: " + node.path("aiRecommendation").asText()
                        + (node.path("confidence").isNumber()
                        ? " (confidence=" + node.path("confidence").asDouble() + ")" : ""));
            }
            mergedReasons.addAll(aiReasons);

            // Advisory only — action stays on deterministic baseline
            return new EdgeRuleInterpreter.Decision(
                    baseline.action(),
                    baseline.score(),
                    baseline.triggeredRuleIds(),
                    mergedReasons);
        } catch (Exception e) {
            log.debug("JEV edge decision unavailable, using rules baseline: {}", e.getMessage());
            return baseline;
        }
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
