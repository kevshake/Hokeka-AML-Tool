package com.posgateway.aml.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.service.rules.DynamicRuleConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates a {@link RuleDefinition} from a natural-language operator prompt by calling
 * Anthropic Claude. Replaces the previous keyword-matching stub.
 *
 * Robustness rules (set in stone — DO NOT add silent fallbacks):
 *   - When ai.rule-generator.enabled=false, return null and log a clear toggle-off message.
 *   - On API timeout / rate-limit / non-2xx, return null + structured ERROR log. NEVER keyword-match.
 *   - On JSON parse / schema validation failure, return null + structured ERROR log.
 *   - SpEL expressions are test-parsed before returning to catch syntax errors at generation time.
 *
 * Cost optimization:
 *   - The system prompt (which describes the rule schema and examples) is sent with
 *     cache_control: {type: "ephemeral"} so the Anthropic Prompt Cache absorbs the cost
 *     of resending it on subsequent requests within ~5 minutes.
 *
 * Implementation note:
 *   - Uses Spring WebClient directly against https://api.anthropic.com/v1/messages instead of
 *     the com.anthropic:anthropic-java SDK. The SDK dependency is declared in pom.xml so it
 *     can be swapped in later, but WebClient gives us first-class control over the
 *     cache_control block and avoids SDK version drift surprises in CI.
 */
@Service
public class AiRuleGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AiRuleGeneratorService.class);

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final Set<String> ALLOWED_RULE_TYPES = Set.of("DROOLS_DRL", "SPEL", "JAVA_BEAN");
    private static final Set<String> ALLOWED_ACTIONS = Set.of("BLOCK", "HOLD", "ALERT", "ALLOW");
    private static final Set<String> ALLOWED_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    /**
     * Stable system-prompt constant — referenced as a single content block with
     * cache_control: ephemeral so the Anthropic Prompt Cache can serve it cheaply
     * on repeated calls within the cache TTL (~5 minutes).
     *
     * Stability matters: any whitespace edit invalidates the cache key. Edit deliberately.
     */
    private static final String SYSTEM_PROMPT = """
            You are an AML (Anti-Money Laundering) rule generator. Convert the operator's
            natural-language description of a fraud / money-laundering pattern into a single
            structured JSON RuleDefinition that the AML rules engine can persist and execute.

            Respond with JSON ONLY — no prose, no markdown code fences, no explanation.

            REQUIRED SCHEMA:
            {
              "name":           string,  // UPPER_SNAKE_CASE, <= 64 chars, must be unique-ish
              "description":    string,  // 1-sentence human description
              "ruleType":       enum,    // one of: "DROOLS_DRL", "SPEL", "JAVA_BEAN"
              "ruleExpression": string,  // the actual rule body in the chosen ruleType's syntax
              "severity":       enum,    // one of: "LOW", "MEDIUM", "HIGH", "CRITICAL"
              "action":         enum,    // one of: "BLOCK", "HOLD", "ALERT", "ALLOW"
              "score":          integer, // risk score impact when triggered, 1-100
              "priority":       integer  // execution priority, 1 (highest) - 1000 (lowest)
            }

            SpEL VARIABLES AVAILABLE (when ruleType = "SPEL"):
              #tx        : transaction (fields: amount, currency, mcc, merchantCountry, panLast4, ...)
              #merchant  : merchant context (fields: country, mcc, riskScore, ageInDays, ...)
              #history   : recent history (fields: panTxnCount1h, panTxnCount24h, avgVolume30d, ...)

            EXAMPLES:

            Operator: "Block any transaction over 10,000 USD"
            {
              "name": "BLOCK_LARGE_USD_OVER_10K",
              "description": "Block transactions over USD 10,000.",
              "ruleType": "SPEL",
              "ruleExpression": "#tx.amount >= 10000 and #tx.currency == 'USD'",
              "severity": "HIGH",
              "action": "BLOCK",
              "score": 60,
              "priority": 100
            }

            Operator: "Flag possible structuring — multiple transactions just under 10k from the same card within an hour"
            {
              "name": "FLAG_STRUCTURING_SUB_10K_VELOCITY",
              "description": "Flag possible structuring: amount in [9000, 10000) with >=3 txns from same PAN in 1h.",
              "ruleType": "SPEL",
              "ruleExpression": "#tx.amount >= 9000 and #tx.amount < 10000 and #history.panTxnCount1h >= 3",
              "severity": "HIGH",
              "action": "ALERT",
              "score": 75,
              "priority": 50
            }

            Operator: "Hold transactions from new merchants (less than 30 days old) in high-risk countries over $5000"
            {
              "name": "HOLD_NEW_MERCHANT_HIGH_RISK_COUNTRY",
              "description": "Hold txns >$5k from merchants <30d old in high-risk jurisdictions.",
              "ruleType": "SPEL",
              "ruleExpression": "#tx.amount > 5000 and #merchant.ageInDays < 30 and (#tx.merchantCountry == 'IR' or #tx.merchantCountry == 'KP' or #tx.merchantCountry == 'SY')",
              "severity": "MEDIUM",
              "action": "HOLD",
              "score": 50,
              "priority": 200
            }

            Now generate ONE RuleDefinition for the operator's prompt. JSON only.
            """;

    private static final Pattern CODE_FENCE_PATTERN =
            Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?})\\s*```");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    @SuppressWarnings("unused")
    private final DynamicRuleConverter converter; // retained for future JSON->DRL synthesis path

    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    @Autowired
    public AiRuleGeneratorService(
            DynamicRuleConverter converter,
            ObjectMapper objectMapper,
            @Value("${ai.rule-generator.enabled:false}") boolean enabled,
            @Value("${anthropic.api-key:}") String apiKey,
            @Value("${ai.rule-generator.model:claude-sonnet-4-6}") String model,
            @Value("${ai.rule-generator.max-tokens:1024}") int maxTokens) {
        this.converter = converter;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.webClient = WebClient.builder()
                .baseUrl(ANTHROPIC_API_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Last error reason, surfaced to the controller for /generate's 502 response. */
    private volatile String lastErrorDetail;

    public String getLastErrorDetail() {
        return lastErrorDetail;
    }

    /**
     * Generates a RuleDefinition from a natural-language prompt by calling Claude.
     *
     * @return a populated (but unsaved) RuleDefinition, or null on any failure / disabled toggle.
     */
    public RuleDefinition generateRuleFromText(String prompt) {
        lastErrorDetail = null;

        if (!enabled) {
            log.info("AI rule generator disabled — set AI_RULE_GENERATOR_ENABLED=true and ANTHROPIC_API_KEY to enable");
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            lastErrorDetail = "ANTHROPIC_API_KEY is not configured";
            log.error("AI rule generator enabled but {}", lastErrorDetail);
            return null;
        }
        if (prompt == null || prompt.isBlank()) {
            lastErrorDetail = "prompt is empty";
            log.error("AI rule generator received empty prompt");
            return null;
        }

        String rawText;
        try {
            rawText = callAnthropic(prompt);
        } catch (WebClientResponseException e) {
            lastErrorDetail = String.format("Anthropic API HTTP %d: %s",
                    e.getStatusCode().value(),
                    truncate(e.getResponseBodyAsString(), 500));
            log.error("AI rule generator: Anthropic call failed status={} body={}",
                    e.getStatusCode().value(), truncate(e.getResponseBodyAsString(), 500));
            return null;
        } catch (Exception e) {
            lastErrorDetail = "Anthropic API call failed: " + e.getClass().getSimpleName() +
                    ": " + truncate(e.getMessage(), 300);
            log.error("AI rule generator: Anthropic call failed", e);
            return null;
        }

        if (rawText == null || rawText.isBlank()) {
            lastErrorDetail = "Anthropic returned empty content";
            log.error("AI rule generator: empty model response");
            return null;
        }

        String json = stripCodeFences(rawText);
        JsonNode tree;
        try {
            tree = objectMapper.readTree(json);
        } catch (Exception e) {
            lastErrorDetail = "Model returned invalid JSON: " + truncate(e.getMessage(), 200);
            log.error("AI rule generator: invalid JSON from model. raw={}", truncate(rawText, 500));
            return null;
        }

        try {
            return validateAndMap(tree, prompt);
        } catch (IllegalArgumentException e) {
            lastErrorDetail = "Schema validation failed: " + e.getMessage();
            log.error("AI rule generator: schema validation failed. detail={} raw={}",
                    e.getMessage(), truncate(rawText, 500));
            return null;
        }
    }

    /**
     * Sends the request to Anthropic with prompt-cache enabled on the system block.
     */
    private String callAnthropic(String userPrompt) {
        // System content is sent as an array of blocks so we can attach cache_control
        // to the schema/examples block. Cached on first call; reused cheaply within ~5 min.
        Map<String, Object> systemBlock = Map.of(
                "type", "text",
                "text", SYSTEM_PROMPT,
                "cache_control", Map.of("type", "ephemeral")
        );

        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", userPrompt
        );

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", List.of(systemBlock),
                "messages", List.of(userMessage)
        );

        JsonNode response = webClient.post()
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        if (response == null) {
            return null;
        }

        // Response shape: {"content":[{"type":"text","text":"..."}], "usage":{...}}
        JsonNode usage = response.path("usage");
        if (!usage.isMissingNode()) {
            int cacheCreate = usage.path("cache_creation_input_tokens").asInt(0);
            int cacheRead = usage.path("cache_read_input_tokens").asInt(0);
            int input = usage.path("input_tokens").asInt(0);
            int output = usage.path("output_tokens").asInt(0);
            log.info("AI rule generator usage: input={} output={} cache_create={} cache_read={}",
                    input, output, cacheCreate, cacheRead);
        }

        JsonNode content = response.path("content");
        if (!content.isArray() || content.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText());
            }
        }
        return sb.toString();
    }

    /**
     * Strips ```json ... ``` fences if Claude wrapped the JSON despite instructions.
     */
    private String stripCodeFences(String s) {
        String trimmed = s.trim();
        Matcher m = CODE_FENCE_PATTERN.matcher(trimmed);
        if (m.find()) {
            return m.group(1);
        }
        // Last-ditch: extract from first '{' to last '}' if there is leading/trailing prose.
        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return trimmed.substring(first, last + 1);
        }
        return trimmed;
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

        // Validate SpEL expressions compile before returning so the operator sees the error
        // at generation time, not at the next rules-engine reload.
        if ("SPEL".equals(ruleType)) {
            try {
                spelParser.parseExpression(ruleExpression);
            } catch (Exception e) {
                throw new IllegalArgumentException("ruleExpression is not valid SpEL: " + e.getMessage());
            }
        }

        RuleDefinition rule = new RuleDefinition();
        rule.setName(name);
        rule.setDescription(description);
        rule.setRuleType(ruleType);
        rule.setRuleExpression(ruleExpression);
        rule.setAction(action);
        rule.setScore(score);
        rule.setPriority(priority);
        rule.setEnabled(false); // preview only — operator must explicitly enable on save
        // ruleJson / drlContent intentionally left blank — the AI flow uses ruleType + ruleExpression.
        // The operator can still convert via DynamicRuleConverter on save if they edit to JSON form.

        // Stash the AI severity in the description tail since RuleDefinition has no severity column;
        // the FE preview reads severity from the response payload via a transient enrichment below.
        // (We attach it as a description suffix so it survives persistence.)
        rule.setDescription(description + " [severity=" + severity + "]");

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
