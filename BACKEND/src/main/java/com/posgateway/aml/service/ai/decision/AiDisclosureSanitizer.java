package com.posgateway.aml.service.ai.decision;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ensures PSP-facing surfaces never reveal AI vendor, model, prompt, token, or cost details.
 * Operator audit paths keep internal {@link AiDecisionAudit} fields unchanged in storage.
 */
public final class AiDisclosureSanitizer {

    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile("openrouter", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bjev\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\blaya\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("typesafe", Pattern.CASE_INSENSITIVE),
            Pattern.compile("anthropic", Pattern.CASE_INSENSITIVE),
            Pattern.compile("openai", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bprompt\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btoken\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcost\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("claude", Pattern.CASE_INSENSITIVE),
            Pattern.compile("gpt-", Pattern.CASE_INSENSITIVE));

    private AiDisclosureSanitizer() {
    }

    public static boolean containsForbiddenDisclosure(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (Pattern pattern : FORBIDDEN_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return containsConfiguredModelId(text);
    }

    public static void assertPspSafe(String label, String text) {
        if (containsForbiddenDisclosure(text)) {
            throw new AssertionError(label + " must not disclose AI vendor/model details: " + text);
        }
    }

    public static void assertPspSafeMap(String label, Map<String, Object> map) {
        assertPspSafe(label, map.toString());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().toLowerCase(Locale.ROOT) : "";
            if (key.contains("model") || key.contains("prompt") || key.contains("token")
                    || key.contains("cost") || key.contains("openrouter") || key.contains("jev")) {
                throw new AssertionError(label + " must not expose operator field: " + entry.getKey());
            }
        }
    }

    private static boolean containsConfiguredModelId(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("/") && (lower.contains("claude") || lower.contains("gpt") || lower.contains("meta/"));
    }
}
