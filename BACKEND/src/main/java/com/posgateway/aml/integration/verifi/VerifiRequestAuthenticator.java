package com.posgateway.aml.integration.verifi;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Authenticates inbound Verifi API 3.0 requests.
 * Primary: JWT JWS Bearer token. Fallbacks: legacy partner HMAC headers and API key (dev only).
 */
@Component
public class VerifiRequestAuthenticator {

    public static final String API_VERSION = "3.0";
    public static final String HEADER_API_VERSION = "x-verifi-api-version";
    public static final String HEADER_CORRELATION_ID = "x-verifi-correlation-id";
    public static final String HEADER_RETRY_COUNT = "x-verifi-retry-count";

    private final VerifiJwsAuthenticator jwsAuthenticator;
    private final VerifiWebhookSignatureVerifier legacyVerifier;

    public VerifiRequestAuthenticator(VerifiJwsAuthenticator jwsAuthenticator,
                                      VerifiWebhookSignatureVerifier legacyVerifier) {
        this.jwsAuthenticator = jwsAuthenticator;
        this.legacyVerifier = legacyVerifier;
    }

    /**
     * @return {@code true} when API version is supported; {@code false} when merchant must return HTTP 501
     */
    public boolean isApiVersionSupported(Map<String, String> headers) {
        String version = firstHeader(headers, HEADER_API_VERSION);
        return version == null || API_VERSION.equals(version.trim());
    }

    public boolean isAuthenticated(Map<String, String> headers, Object body, VerifiRdrProperties properties) {
        if (!properties.isSignatureRequired()) {
            return true;
        }

        String authorization = firstHeader(headers, "Authorization");
        if (authorization != null && jwsAuthenticator.verifyBearerToken(authorization, properties)) {
            return true;
        }

        // RSA mode is certificate-authenticated and must never downgrade to a shared API key or legacy HMAC.
        if ("RSA".equalsIgnoreCase(properties.getAuthMode())) return false;

        if (legacyVerifier.verifyApiKey(headers, properties.getApiKey())) {
            return true;
        }

        String signature = firstHeader(headers,
                "X-Butter-Webhook-Signature",
                "X-Verifi-Webhook-Signature",
                "X-Webhook-Signature");
        String createdAt = firstHeader(headers,
                "X-Butter-Webhook-Created",
                "X-Verifi-Webhook-Created",
                "X-Webhook-Created");
        return legacyVerifier.verifyHmacSha256(body, signature, createdAt, properties.getWebhookSecret());
    }

    public static String correlationId(Map<String, String> headers) {
        return firstHeader(headers, HEADER_CORRELATION_ID);
    }

    public static String firstHeader(Map<String, String> headers, String... names) {
        for (String name : names) {
            String value = headers.get(name);
            if (value == null) {
                value = headers.entrySet().stream()
                        .filter(e -> e.getKey().equalsIgnoreCase(name))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
            }
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
