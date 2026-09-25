package com.posgateway.aml.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.service.jev.JevDecisionContext;
import com.posgateway.aml.service.jev.JevDecisionGateway;
import com.posgateway.aml.service.jev.JevDecisionOutcome;
import com.posgateway.aml.service.jev.JevEngineType;
import com.posgateway.aml.service.rules.DynamicRuleConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates a {@link RuleDefinition} from a natural-language operator prompt via the
 * central {@link JevDecisionGateway} (OpenRouter). Replaces the previous direct Anthropic client.
 *
 * <p>Robustness rules:
 * <ul>
 *   <li>When {@code ai.rule-generator.enabled=false}, return null.</li>
 *   <li>On API timeout / error / invalid JSON, return null + structured ERROR log.</li>
 *   <li>SpEL expressions are test-parsed before returning.</li>
 *   <li>Generated rules are preview-only (enabled=false) until admin approval.</li>
 * </ul>
 */
@Service
public class AiRuleGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AiRuleGeneratorService.class);

    private static final Set<String> ALLOWED_RULE_TYPES = Set.of("DROOLS_DRL", "SPEL", "JAVA_BEAN");
    private static final Set<String> ALLOWED_ACTIONS = Set.of("BLOCK", "HOLD", "ALERT", "ALLOW");
    private static final Set<String> ALLOWED_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final JevDecisionGateway jevGateway;
    private final ObjectMapper objectMapper;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    @SuppressWarnings("unused")
    private final DynamicRuleConverter converter;

    private final boolean enabled;

    @Autowired
    public AiRuleGeneratorService(
            JevDecisionGateway jevGateway,
            DynamicRuleConverter converter,
            ObjectMapper objectMapper,
            @Value("${ai.rule-generator.enabled:false}") boolean enabled) {
        this.jevGateway = jevGateway;
        this.converter = converter;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private volatile String lastErrorDetail;

    public String getLastErrorDetail() {
        return lastErrorDetail;
    }

    public RuleDefinition generateRuleFromText(String prompt) {
        lastErrorDetail = null;

        if (!enabled) {
            log.info("AI rule generator disabled — set AI_RULE_GENERATOR_ENABLED=true and configure JEV (OPENROUTER_API_KEY, JEV_MODEL)");
            return null;
        }
        if (!jevGateway.isConfigured()) {
            lastErrorDetail = "JEV not configured (OPENROUTER_API_KEY and JEV_MODEL required)";
            log.error("AI rule generator enabled but {}", lastErrorDetail);
            return null;
        }
        if (prompt == null || prompt.isBlank()) {
            lastErrorDetail = "prompt is empty";
            log.error("AI rule generator received empty prompt");
            return null;
        }

        JevDecisionContext ctx = JevDecisionContext.builder(JevEngineType.RULE_SUGGESTION)
                .baselineDecision("PREVIEW")
                .feature("operatorPrompt", prompt)
                .advisoryOnly(true)
                .build();

        JevDecisionOutcome outcome = jevGateway.decide(ctx);
        if (outcome.isFallback()) {
            lastErrorDetail = outcome.getFallbackReason();
            log.error("AI rule generator: JEV fallback — {}", lastErrorDetail);
            return null;
        }

        Map<String, Object> parsed = outcome.getRawParsed();
        if (parsed == null || parsed.isEmpty()) {
            lastErrorDetail = "JEV returned empty rule payload";
            log.error("AI rule generator: {}", lastErrorDetail);
            return null;
        }

        try {
            JsonNode tree = objectMapper.valueToTree(parsed);
            return validateAndMap(tree, prompt);
        } catch (IllegalArgumentException e) {
            lastErrorDetail = "Schema validation failed: " + e.getMessage();
            log.error("AI rule generator: schema validation failed. detail={}", e.getMessage());
            return null;
        }
    }

    private RuleDefinition validateAndMap(JsonNode tree, String originalPrompt) {
        String name = requireString(tree, "name");
        String description = optionalString(tree, "description",
                "Generated from prompt: " + truncate(originalPrompt, 120));
        String ruleType = requireString(tree, "ruleType");
        String ruleExpression = requireString(tree, "ruleExpression");
        String severity = optionalString(tree, "severity", "MEDIUM");
        String action = optionalString(tree, "action", "ALERT");
        int score = tree.path("score").asInt(50);
        int priority = tree.path("priority").asInt(100);

        if (!ALLOWED_RULE_TYPES.contains(ruleType)) {
            throw new IllegalArgumentException("ruleType must be one of " + ALLOWED_RULE_TYPES + ", got: " + ruleType);
        }
        if (!ALLOWED_ACTIONS.contains(action)) {
            throw new IllegalArgumentException("action must be one of " + ALLOWED_ACTIONS + ", got: " + action);
        }
        if (!ALLOWED_SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("severity must be one of " + ALLOWED_SEVERITIES + ", got: " + severity);
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be 0-100, got: " + score);
        }

        if ("SPEL".equals(ruleType)) {
            try {
                spelParser.parseExpression(ruleExpression);
            } catch (Exception e) {
                throw new IllegalArgumentException("ruleExpression is not valid SpEL: " + e.getMessage());
            }
        }

        RuleDefinition rule = new RuleDefinition();
        rule.setName(name);
        rule.setDescription(description + " [severity=" + severity + "]");
        rule.setRuleType(ruleType);
        rule.setRuleExpression(ruleExpression);
        rule.setAction(action);
        rule.setScore(score);
        rule.setPriority(priority);
        rule.setEnabled(false);
        return rule;
    }

    private static String requireString(JsonNode tree, String field) {
        JsonNode n = tree.get(field);
        if (n == null || n.isNull() || !n.isTextual() || n.asText().isBlank()) {
            throw new IllegalArgumentException("missing required field: " + field);
        }
        return n.asText().trim();
    }

    private static String optionalString(JsonNode tree, String field, String fallback) {
        JsonNode n = tree.get(field);
        if (n == null || n.isNull() || !n.isTextual() || n.asText().isBlank()) {
            return fallback;
        }
        return n.asText().trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
