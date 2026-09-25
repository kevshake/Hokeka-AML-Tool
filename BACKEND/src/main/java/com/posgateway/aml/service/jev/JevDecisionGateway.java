package com.posgateway.aml.service.jev;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.jev.JevProperties;
import com.posgateway.aml.entity.jev.JevDecisionAudit;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single gateway for all JEV/OpenRouter AI decisions on the Control Plane.
 * On any failure, callers receive a fallback outcome using the deterministic baseline.
 */
@Service
public class JevDecisionGateway {

    private static final Logger log = LoggerFactory.getLogger(JevDecisionGateway.class);
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?})\\s*```");

    private final JevProperties properties;
    private final OpenRouterClient openRouterClient;
    private final JevFeatureMaskingService maskingService;
    private final JevPromptTemplateService promptTemplateService;
    private final JevEngineConfigService engineConfigService;
    private final JevBudgetService budgetService;
    private final JevAuditService auditService;
    private final ObjectMapper objectMapper;

    public JevDecisionGateway(JevProperties properties,
                              OpenRouterClient openRouterClient,
                              JevFeatureMaskingService maskingService,
                              JevPromptTemplateService promptTemplateService,
                              JevEngineConfigService engineConfigService,
                              JevBudgetService budgetService,
                              JevAuditService auditService,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.openRouterClient = openRouterClient;
        this.maskingService = maskingService;
        this.promptTemplateService = promptTemplateService;
        this.engineConfigService = engineConfigService;
        this.budgetService = budgetService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public String configuredModel() {
        return properties.isConfigured() ? properties.getModel() : null;
    }

    /**
     * Synchronous decision with default timeout.
     */
    public JevDecisionOutcome decide(JevDecisionContext context) {
        return decide(context, properties.getTimeout());
    }

    /**
     * Synchronous decision with custom timeout (inline edge mode).
     */
    @CircuitBreaker(name = "jevOpenRouter", fallbackMethod = "decideFallback")
    public JevDecisionOutcome decide(JevDecisionContext context, Duration timeout) {
        String baseline = context.getBaselineDecision() != null ? context.getBaselineDecision() : "ALLOW";

        if (!properties.isConfigured()) {
            return auditAndReturn(context, baseline,
                    JevDecisionOutcome.fallback(baseline, "JEV not configured (missing OPENROUTER_API_KEY or JEV_MODEL)"),
                    null, null, null, null);
        }
        if (!engineConfigService.isEngineEnabled(context.getEngine())) {
            return auditAndReturn(context, baseline,
                    JevDecisionOutcome.fallback(baseline, "Engine disabled"),
                    null, null, null, null);
        }
        if (context.getPspId() != null && budgetService.isBudgetExceeded(context.getPspId())) {
            return auditAndReturn(context, baseline,
                    JevDecisionOutcome.fallback(baseline, "Daily budget exceeded"),
                    null, null, null, null);
        }

        String promptVersion = engineConfigService.promptVersion(context.getEngine());
        String systemPrompt = promptTemplateService.resolveSystemPrompt(context.getEngine(), promptVersion);
        Map<String, Object> masked = maskingService.maskFeatures(context.getFeatures());
        String userPrompt = buildUserPrompt(context, masked);

        OpenRouterClient.OpenRouterResponse apiResponse = null;
        String raw = null;
        try {
            apiResponse = openRouterClient.chatCompletion(properties.getModel(), systemPrompt, userPrompt, timeout);
            raw = apiResponse.content();
        } catch (Exception primary) {
            if (properties.getFallbackModel() != null && !properties.getFallbackModel().isBlank()) {
                try {
                    apiResponse = openRouterClient.chatCompletion(
                            properties.getFallbackModel(), systemPrompt, userPrompt, timeout);
                    raw = apiResponse.content();
                } catch (Exception fallbackEx) {
                    log.warn("JEV OpenRouter failed for engine {}: {}",
                            context.getEngine(), fallbackEx.getClass().getSimpleName());
                    return auditAndReturn(context, baseline,
                            JevDecisionOutcome.fallback(baseline, "OpenRouter error: " + fallbackEx.getClass().getSimpleName()),
                            null, null, promptVersion, null);
                }
            } else {
                log.warn("JEV OpenRouter failed for engine {}: {}",
                        context.getEngine(), primary.getClass().getSimpleName());
                return auditAndReturn(context, baseline,
                        JevDecisionOutcome.fallback(baseline, "OpenRouter error: " + primary.getClass().getSimpleName()),
                        null, null, promptVersion, null);
            }
        }

        Map<String, Object> parsed;
        try {
            parsed = parseJsonResponse(raw);
        } catch (Exception e) {
            log.warn("JEV invalid JSON for engine {}: {}", context.getEngine(), e.getMessage());
            return auditAndReturn(context, baseline,
                    JevDecisionOutcome.fallback(baseline, "Invalid JSON from model"),
                    raw, null, promptVersion, apiResponse);
        }

        JevDecisionOutcome outcome = mapParsedToOutcome(parsed, baseline, context);
        if (context.getPspId() != null && apiResponse != null) {
            long tokens = (long) apiResponse.inputTokens() + apiResponse.outputTokens();
            BigDecimal cost = apiResponse.estimatedCostUsd() != null
                    ? BigDecimal.valueOf(apiResponse.estimatedCostUsd()) : BigDecimal.ZERO;
            budgetService.recordSpend(context.getPspId(), tokens, cost);
        }
        return auditAndReturn(context, baseline, outcome, raw, parsed, promptVersion, apiResponse);
    }

    @SuppressWarnings("unused")
    private JevDecisionOutcome decideFallback(JevDecisionContext context, Duration timeout, Throwable t) {
        String baseline = context.getBaselineDecision() != null ? context.getBaselineDecision() : "ALLOW";
        return auditAndReturn(context, baseline,
                JevDecisionOutcome.fallback(baseline, "Circuit breaker open"),
                null, null, engineConfigService.promptVersion(context.getEngine()), null);
    }

    @Async
    public void decideAsync(JevDecisionContext context) {
        decide(context);
    }

    private JevDecisionOutcome mapParsedToOutcome(Map<String, Object> parsed, String baseline,
                                                  JevDecisionContext context) {
        JevRecommendation rec = JevRecommendation.fromString(stringVal(parsed, "recommendation"));
        if (rec == null) {
            return JevDecisionOutcome.fallback(baseline, "Missing recommendation");
        }

        Double riskScore = numberVal(parsed, "riskScore");
        Double confidence = numberVal(parsed, "confidence");
        List<String> reasons = listVal(parsed, "reasons");
        List<String> signals = listVal(parsed, "citedSignals");

        boolean advisory = context.isAdvisoryOnly() || engineConfigService.isAdvisoryOnly(context.getEngine());
        String finalDecision = baseline;
        boolean aiApplied = false;

        // AI is advisory for regulated paths — never override baseline on sanctions/hard blocks
        if (!advisory && !isHardBaseline(baseline)) {
            finalDecision = mapRecommendationToAction(rec);
            aiApplied = !finalDecision.equals(baseline);
        }

        return JevDecisionOutcome.builder()
                .fallback(false)
                .recommendation(rec)
                .riskScore(riskScore)
                .confidence(confidence)
                .reasons(reasons)
                .citedSignals(signals)
                .finalDecision(finalDecision)
                .aiApplied(aiApplied)
                .rawParsed(parsed)
                .build();
    }

    private static boolean isHardBaseline(String baseline) {
        if (baseline == null) return false;
        return switch (baseline.toUpperCase()) {
            case "BLOCK", "HOLD", "SANCTIONS_MATCH" -> true;
            default -> false;
        };
    }

    private static String mapRecommendationToAction(JevRecommendation rec) {
        return switch (rec) {
            case APPROVE -> "ALLOW";
            case REVIEW -> "ALERT";
            case DECLINE -> "BLOCK";
            case ESCALATE -> "HOLD";
        };
    }

    private JevDecisionOutcome auditAndReturn(JevDecisionContext context,
                                              String baseline,
                                              JevDecisionOutcome outcome,
                                              String raw,
                                              Map<String, Object> parsed,
                                              String promptVersion,
                                              OpenRouterClient.OpenRouterResponse apiResponse) {
        Map<String, Object> masked = maskingService.maskFeatures(context.getFeatures());
        String model = apiResponse != null ? apiResponse.modelUsed() : properties.getModel();
        JevDecisionAudit persisted = auditService.persist(
                context, promptVersion, model, masked, raw, parsed, outcome, apiResponse);
        return JevDecisionOutcome.builder()
                .fallback(outcome.isFallback())
                .fallbackReason(outcome.getFallbackReason())
                .recommendation(outcome.getRecommendation())
                .riskScore(outcome.getRiskScore())
                .confidence(outcome.getConfidence())
                .reasons(outcome.getReasons())
                .citedSignals(outcome.getCitedSignals())
                .finalDecision(outcome.getFinalDecision() != null ? outcome.getFinalDecision() : baseline)
                .aiApplied(outcome.isAiApplied())
                .auditId(persisted.getId())
                .rawParsed(outcome.getRawParsed())
                .build();
    }

    private String buildUserPrompt(JevDecisionContext context, Map<String, Object> masked) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("engine", context.getEngine().name());
        payload.put("baselineDecision", context.getBaselineDecision());
        payload.put("features", masked);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return masked.toString();
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

    private static String stringVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Double numberVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> listVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        }
        return List.of();
    }
}
