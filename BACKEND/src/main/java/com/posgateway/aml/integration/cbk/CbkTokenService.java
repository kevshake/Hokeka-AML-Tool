package com.posgateway.aml.integration.cbk;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe OAuth2 token cache for the CBK GDI gateway.
 *
 * <p>Tokens are cached per PSP (keyed on {@code pspId}).  A cached token is
 * reused until it is within {@code cbk.token-buffer-seconds} of its reported
 * expiry; at that point the next call transparently fetches a fresh token.
 *
 * <p>Token endpoint: {@code https://{activeHost}/oauth2/v1/token}
 * (pre-prod: {@code https://{preprodHost}/oauth2/v1/token} — no /preprod prefix
 * on the token URL itself, only on data-submission URLs).
 */
@Service
public class CbkTokenService {

    private static final Logger log = LoggerFactory.getLogger(CbkTokenService.class);

    private final CbkProperties properties;
    private final WebClient webClient;

    /** Per-PSP token cache. */
    private final ConcurrentHashMap<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public CbkTokenService(CbkProperties properties) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

        this.webClient = WebClient.builder()
                // Token endpoint lives on the active host — same host regardless of env,
                // but note: pre-prod uses gdi.centralbank.go.ke, live uses gdicbk.centralbank.go.ke
                .baseUrl(properties.getActiveBaseUrl())
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Returns a valid Bearer token for the given PSP.
     *
     * <p>If {@code clientId} / {@code clientSecret} are null or blank the global
     * fallback credentials from {@link CbkProperties} are used.
     *
     * @param pspId        PSP identifier (cache key)
     * @param clientId     per-PSP OAuth2 client_id, or null to use global
     * @param clientSecret per-PSP OAuth2 client_secret, or null to use global
     * @return access_token string
     * @throws RuntimeException if token acquisition fails
     */
    public String getToken(Long pspId, String clientId, String clientSecret) {
        CachedToken cached = tokenCache.get(pspId);
        if (cached != null && !cached.isExpired(properties.getTokenBufferSeconds())) {
            return cached.accessToken;
        }
        // Fetch fresh token — synchronised per pspId to avoid thundering herd.
        synchronized (("cbk-token-" + pspId).intern()) {
            cached = tokenCache.get(pspId);
            if (cached != null && !cached.isExpired(properties.getTokenBufferSeconds())) {
                return cached.accessToken;
            }
            CachedToken fresh = fetchToken(pspId, effectiveClientId(clientId), effectiveClientSecret(clientSecret));
            tokenCache.put(pspId, fresh);
            return fresh.accessToken;
        }
    }

    // ---- private helpers ----

    private String effectiveClientId(String perPsp) {
        return (perPsp != null && !perPsp.isBlank()) ? perPsp : properties.getClientId();
    }

    private String effectiveClientSecret(String perPsp) {
        return (perPsp != null && !perPsp.isBlank()) ? perPsp : properties.getClientSecret();
    }

    private CachedToken fetchToken(Long pspId, String clientId, String clientSecret) {
        log.debug("Fetching CBK OAuth2 token for PSP {}", pspId);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", properties.getActiveScope());
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        TokenResponse resp;
        try {
            resp = webClient.post()
                    .uri("/oauth2/v1/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block(Duration.ofMillis(properties.getReadTimeoutMs() + 1000L));
        } catch (Exception e) {
            log.error("CBK token fetch failed for PSP {}: {}", pspId, e.getMessage());
            throw new CbkGdiException("Token fetch failed for PSP " + pspId + ": " + e.getMessage(), e);
        }

        if (resp == null || resp.accessToken == null || resp.accessToken.isBlank()) {
            throw new CbkGdiException("CBK returned empty access_token for PSP " + pspId);
        }

        long expiresIn = (resp.expiresIn != null ? resp.expiresIn : 3600L);
        Instant expiry = Instant.now().plusSeconds(expiresIn);
        log.debug("CBK token obtained for PSP {} (expires_in={}s)", pspId, expiresIn);
        return new CachedToken(resp.accessToken, expiry);
    }

    // ---- inner types ----

    private static final class CachedToken {
        final String accessToken;
        final Instant expiryTime;

        CachedToken(String accessToken, Instant expiryTime) {
            this.accessToken = accessToken;
            this.expiryTime = expiryTime;
        }

        boolean isExpired(int bufferSeconds) {
            return Instant.now().isAfter(expiryTime.minusSeconds(bufferSeconds));
        }
    }

    /** Jackson-mapped token response from CBK. */
    private static final class TokenResponse {
        @com.fasterxml.jackson.annotation.JsonProperty("access_token")
        String accessToken;

        @com.fasterxml.jackson.annotation.JsonProperty("expires_in")
        Long expiresIn;
    }

    /** Unchecked exception thrown when CBK token acquisition fails unrecoverably. */
    public static class CbkGdiException extends RuntimeException {
        public CbkGdiException(String message) { super(message); }
        public CbkGdiException(String message, Throwable cause) { super(message, cause); }
    }
}
