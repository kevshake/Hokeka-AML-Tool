package com.posgateway.aml.service.jev;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trims decision state for Laya's per-question context window (512 English / 1024 multilingual tokens).
 * Decisive sections stay first; optional sections drop when the serialized state is too large.
 */
final class LayaStateTrimmer {

    private static final int ENGLISH_MAX_CHARS = 2048;
    private static final int MULTILINGUAL_MAX_CHARS = 4096;

    private LayaStateTrimmer() {
    }

    static Map<String, Object> trim(Map<String, Object> state, String langHint, ObjectMapper objectMapper) {
        if (state == null || state.isEmpty()) {
            return Map.of();
        }
        int maxChars = isEnglish(langHint) ? ENGLISH_MAX_CHARS : MULTILINGUAL_MAX_CHARS;
        Map<String, Object> working = new LinkedHashMap<>(state);
        if (serializedLength(working, objectMapper) <= maxChars) {
            return working;
        }

        List<String> dropOrder = List.of(
                "activity_window_7d",
                "activity_window_30d",
                "supporting_signals",
                "historical_context",
                "narrative_excerpt");
        for (String key : dropOrder) {
            working.remove(key);
            if (serializedLength(working, objectMapper) <= maxChars) {
                return working;
            }
        }

        truncateLongStrings(working, maxChars, objectMapper);
        return working;
    }

    private static void truncateLongStrings(Map<String, Object> map, int maxChars, ObjectMapper objectMapper) {
        for (Map.Entry<String, Object> entry : new ArrayList<>(map.entrySet())) {
            Object value = entry.getValue();
            if (value instanceof String s && s.length() > 256) {
                map.put(entry.getKey(), s.substring(0, 256));
            } else if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                truncateLongStrings(nestedMap, maxChars, objectMapper);
            }
        }
        while (serializedLength(map, objectMapper) > maxChars && !map.isEmpty()) {
            String lastKey = null;
            for (String k : map.keySet()) {
                lastKey = k;
            }
            if (lastKey == null) {
                break;
            }
            map.remove(lastKey);
        }
    }

    private static int serializedLength(Map<String, Object> state, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(state).length();
        } catch (JsonProcessingException e) {
            return state.toString().length();
        }
    }

    private static boolean isEnglish(String langHint) {
        if (langHint == null || langHint.isBlank()) {
            return true;
        }
        String lang = langHint.toLowerCase(Locale.ROOT);
        return lang.startsWith("en");
    }
}
