package com.posgateway.aml.service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

/** Produces deterministic HMAC lookup keys without storing searchable PII. */
@Service
public class PiiLookupHasher {

    private final byte[] key;

    public PiiLookupHasher(@Value("${security.pii.lookup-hmac-key:}") String key) {
        this.key = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
    }

    public String hashIdentifier(String value) {
        if (value == null || value.isBlank()) return null;
        if (key.length < 32) {
            throw new IllegalStateException("PII_LOOKUP_HMAC_KEY must be at least 32 characters");
        }
        try {
            String normalized = value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash PII lookup value", ex);
        }
    }

    public boolean isConfigured() {
        return key.length >= 32;
    }
}
