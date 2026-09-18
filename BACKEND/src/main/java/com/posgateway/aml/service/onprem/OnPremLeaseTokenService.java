package com.posgateway.aml.service.onprem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and verifies compact HMAC-SHA256 lease tokens
 * ({@code base64url(payload).base64url(hmac)}). No external JWT library required.
 */
@Service
public class OnPremLeaseTokenService {

    private static final String ALG = "HmacSHA256";

    private final HokekaAuthProperties properties;
    private final ObjectMapper objectMapper;

    public OnPremLeaseTokenService(HokekaAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String issueToken(String instanceId, Long pspId, Instant validUntil, Instant nextCheckAt,
                             int approvedDays, String jti) {
        requireSigningSecret();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("typ", "hokeka-onprem-lease");
        claims.put("jti", jti != null ? jti : UUID.randomUUID().toString());
        claims.put("instanceId", instanceId);
        claims.put("pspId", pspId);
        claims.put("validUntil", validUntil.getEpochSecond());
        claims.put("nextCheckAt", nextCheckAt.getEpochSecond());
        claims.put("approvedDays", approvedDays);
        claims.put("iat", Instant.now().getEpochSecond());
        try {
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(claims));
            String sig = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(hmac(payload));
            return payload + "." + sig;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise lease claims", e);
        }
    }

    public LeaseClaims verify(String token) {
        requireSigningSecret();
        if (!StringUtils.hasText(token) || !token.contains(".")) {
            throw new IllegalArgumentException("Malformed lease token");
        }
        int dot = token.indexOf('.');
        String payload = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        byte[] expected = hmac(payload);
        byte[] provided = Base64.getUrlDecoder().decode(sig);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new IllegalArgumentException("Lease token signature invalid");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(payload), Map.class);
            if (!"hokeka-onprem-lease".equals(String.valueOf(claims.get("typ")))) {
                throw new IllegalArgumentException("Unexpected lease token type");
            }
            return new LeaseClaims(
                    String.valueOf(claims.get("jti")),
                    String.valueOf(claims.get("instanceId")),
                    toLong(claims.get("pspId")),
                    Instant.ofEpochSecond(toLong(claims.get("validUntil"))),
                    Instant.ofEpochSecond(toLong(claims.get("nextCheckAt"))),
                    toInt(claims.get("approvedDays")),
                    Instant.ofEpochSecond(toLong(claims.get("iat"))));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Lease token payload invalid", e);
        }
    }

    public boolean hasSigningSecret() {
        return StringUtils.hasText(properties.getLeaseSigningSecret());
    }

    private void requireSigningSecret() {
        if (!hasSigningSecret()) {
            throw new IllegalStateException(
                    "hokeka.auth.lease-signing-secret is required to issue or verify leases");
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(
                    properties.getLeaseSigningSecret().getBytes(StandardCharsets.UTF_8), ALG));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    public record LeaseClaims(
            String jti,
            String instanceId,
            Long pspId,
            Instant validUntil,
            Instant nextCheckAt,
            int approvedDays,
            Instant issuedAt
    ) {
    }
}
