package com.posgateway.aml.service.pagination;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

/**
 * Encodes and decodes opaque keyset cursors as Base64 JSON for stable DESC pagination on (time, id).
 */
public final class KeysetCursorCodec {

    private KeysetCursorCodec() {
    }

    /** Tuple carried inside a cursor token for timestamp + surrogate key ordering. */
    public record CursorToken(LocalDateTime sortTime, Long sortId) {
    }

    /** Serializes a cursor token into an opaque URL-safe string. */
    public static String encode(ObjectMapper mapper, LocalDateTime sortTime, Long sortId) {
        try {
            String json = mapper.writeValueAsString(Map.of(
                    "t", sortTime.toString(),
                    "id", sortId));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode keyset cursor", e);
        }
    }

    /** Parses an opaque cursor string back into sort time and id, or null when absent/invalid. */
    public static CursorToken decode(ObjectMapper mapper, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(cursor);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(raw, Map.class);
            LocalDateTime t = LocalDateTime.parse(String.valueOf(map.get("t")));
            Long id = Long.valueOf(String.valueOf(map.get("id")));
            return new CursorToken(t, id);
        } catch (Exception e) {
            return null;
        }
    }
}
