package com.posgateway.aml.integration.verifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * Verifies Verifi/PSP partner webhook signatures.
 * Supports Butter-style HMAC-SHA256: {@code HMAC(secret, jsonBody + "+" + createdAt)}.
 */
@Component
public class VerifiWebhookSignatureVerifier {

    private final ObjectMapper objectMapper;

    public VerifiWebhookSignatureVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean verifyHmacSha256(Object body, String providedSignature, String createdAt, String signingKey) {
        if (providedSignature == null || providedSignature.isBlank()
                || signingKey == null || signingKey.isBlank()
                || createdAt == null || createdAt.isBlank()) {
            return false;
        }
        try {
            String payload = objectMapper.writeValueAsString(body);
            String message = payload + "+" + createdAt;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String calculated = HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            return calculated.equalsIgnoreCase(providedSignature.trim());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean verifyApiKey(Map<String, String> headers, String expectedApiKey) {
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            return false;
        }
        String provided = firstNonBlank(
                headers.get("X-Api-Key"),
                headers.get("X-Verifi-Api-Key"),
                headers.get("Authorization"));
        if (provided != null && provided.startsWith("Bearer ")) {
            provided = provided.substring(7);
        }
        return expectedApiKey.equals(provided);
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
