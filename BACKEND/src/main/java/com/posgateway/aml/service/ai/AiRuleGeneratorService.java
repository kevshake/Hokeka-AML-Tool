package com.posgateway.aml.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.jev.JevProperties;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.service.jev.JevPromptTemplateService;
import com.posgateway.aml.service.jev.OpenRouterChatClient;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates a {@link RuleDefinition} from a natural-language operator prompt via
 * OpenRouter chat completions (not the Jev Decisions API).
 */
@Service
public class AiRuleGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AiRuleGeneratorService.class);
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?})\\s*```");

    private static final Set<String> ALLOWED_RULE_TYPES = Set.of("DROOLS_DRL", "SPEL", "JAVA_BEAN");
    private static final Set<String> ALLOWED_ACTIONS = Set.of("BLOCK", "HOLD", "ALERT", "ALLOW");
    private static final Set<String> ALLOWED_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final OpenRouterChatClient chatClient;
    private final JevPromptTemplateService promptTemplateService;
    private final JevProperties jevProperties;
    private final ObjectMapper objectMapper;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    @SuppressWarnings("unused")
    private final DynamicRuleConverter converter;

    private final boolean enabled;

    @Autowired
    public AiRuleGeneratorService(
            OpenRouterChatClient chatClient,
            JevPromptTemplateService promptTemplateService,
            JevProperties jevProperties,
            DynamicRuleConverter converter,
            ObjectMapper objectMapper,
            @Value("${ai.rule-generator.enabled:false}") boolean enabled) {
        this.chatClient = chatClient;
        this.promptTemplateService = promptTemplateService;
        this.jevProperties = jevProperties;
        this.converter = converter;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private volatile String lastErrorDetail;
    private volatile Long lastAuditId;

    public String getLastErrorDetail() {
        return lastErrorDetail;
    }

    public Long getLastAuditId() {
        return lastAuditId;
    }

    public RuleDefinition generateRuleFromText(String prompt) {
        lastErrorDetail = null;
        lastAuditId = null;

        if (!enabled) {
            log.info("AI rule generator disabled — set AI_RULE_GENERATOR_ENABLED=true and configure OPENROUTER_API_KEY + JEV_CHAT_MODEL");
            return null;
        }
        if (!jevProperties.isChatConfigured()) {
            lastErrorDetail = "Chat LLM not configured (OPENROUTER_API_KEY and JEV_CHAT_MODEL required)";
            log.error("AI rule generator enabled but {}", lastErrorDetail);
            return null;
        }
        if (prompt == null || prompt.isBlank()) {
            lastErrorDetail = "prompt is empty";
            log.error("AI rule generator received empty prompt");
            return null;
        }

        String systemPrompt = promptTemplateService.resolveSystemPrompt(
                com.posgateway.aml.service.jev.JevEngineType.RULE_SUGGESTION, "v1");
        try {
            OpenRouterChatClient.ChatResponse response = chatClient.chatCompletion(
                    systemPrompt,
                    prompt,
                    jevProperties.getTimeout());
            Map<String, Object> parsed = parseJsonResponse(response.content());
            JsonNode tree = objectMapper.valueToTree(parsed);
            return validateAndMap(tree, prompt);
        } catch (Exception e) {
            lastErrorDetail = "Chat LLM error: " + e.getClass().getSimpleName();
            log.error("AI rule generator: {}", lastErrorDetail);
            return null;
        }
    }

    private Map<String, Object> parseJsonResponse(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty response");
        }
        String json = stripCodeFences(raw.trim());
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private static String stripCodeFences(String s) {
        Matcher m = CODE_FENCE.matcher(s);
        if (m.find()) {
            return m.group(1);
        }
        int first = s.indexOf('{');
        int last = s.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return s.substring(first, last + 1);
        }
        return s;
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
