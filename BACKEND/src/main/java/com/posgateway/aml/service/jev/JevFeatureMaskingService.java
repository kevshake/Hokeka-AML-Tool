package com.posgateway.aml.service.jev;

import com.posgateway.aml.service.security.PiiLookupHasher;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Masks PII before features are sent to Laya. Reuses HMAC hashing where applicable.
 */
@Service
public class JevFeatureMaskingService {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "pan", "pan_hash", "card_number", "account_number", "account_no",
            "national_id", "passport", "ssn", "tax_id", "email", "phone",
            "customer_name", "beneficiary_name", "sender_name", "receiver_name",
            "iban", "swift", "routing_number", "settlement_account"
    );

    private static final Pattern PAN_PATTERN = Pattern.compile("\\b\\d{13,19}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final PiiLookupHasher piiLookupHasher;

    public JevFeatureMaskingService(PiiLookupHasher piiLookupHasher) {
        this.piiLookupHasher = piiLookupHasher;
    }

    public Map<String, Object> maskFeatures(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null) {
                continue;
            }
            String normalizedKey = key.toLowerCase(Locale.ROOT);
            if (SENSITIVE_KEYS.contains(normalizedKey)) {
                masked.put(key, hashOrMask(value));
            } else if (value instanceof String s) {
                masked.put(key, maskStringValue(s));
            } else if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                masked.put(key, maskFeatures(nestedMap));
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }

    private Object hashOrMask(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        if (s.isBlank()) {
            return s;
        }
        if (piiLookupHasher.isConfigured()) {
            try {
                return piiLookupHasher.hashIdentifier(s);
            } catch (Exception ignored) {
                // fall through to truncation mask
            }
        }
        return "[REDACTED:" + s.length() + "chars]";
    }

    private String maskStringValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = PAN_PATTERN.matcher(value).replaceAll("[PAN_REDACTED]");
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("[EMAIL_REDACTED]");
        return masked;
    }
}
