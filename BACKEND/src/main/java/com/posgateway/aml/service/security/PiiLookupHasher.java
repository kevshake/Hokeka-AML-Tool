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

    /**
     * The PAN lookup key stored as {@code transactions.pan_hash} and used by the payment blacklist:
     * the HMAC of {@link #hashIdentifier} when the key is configured, otherwise (dev/test only — the
     * production profile requires the key) the unkeyed SHA-256 hex digest of the raw PAN. Shared by
     * transaction ingest and the blacklist API so a blacklisted PAN matches ingested transactions.
     */
    public String hashPan(String pan) {
        if (pan == null || pan.isBlank()) return null;
        if (isConfigured()) {
            return hashIdentifier(pan);
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(pan.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("PAN hashing failed; refusing to persist raw PAN", e);
        }
    }
}
