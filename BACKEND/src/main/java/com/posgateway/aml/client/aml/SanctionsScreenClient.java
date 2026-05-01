package com.posgateway.aml.client.aml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for {@code aml-microservice}'s {@code /internal/v1/sanctions/screen}
 * endpoint. Uses the shared {@code amlMicroservice} Resilience4j instance configured
 * in {@code application.properties} — the screening call shares the same SLO as the
 * AML score call (sub-500ms, 2 retries).
 *
 * <p>A small Caffeine cache (60s TTL, 1k entries) deduplicates rapid repeats of the
 * same name from the FE / orchestrators. The cache is keyed on
 * {@code lower(name) + "|" + nullSafe(type)} so distinct entity-type screens don't
 * collide.
 *
 * <p>The fallback returns {@code null}; callers MUST treat null as "screening
 * unavailable" and degrade gracefully (controller returns {@code UNAVAILABLE}).
 */
@Component
public class SanctionsScreenClient {

    private static final Logger log = LoggerFactory.getLogger(SanctionsScreenClient.class);
    private static final String CB_NAME = "amlMicroservice";
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    private final AmlMicroserviceProperties properties;
    private final WebClient webClient;
    private final Cache<String, BackendSanctionsScreenResponse> cache;

    public SanctionsScreenClient(AmlMicroserviceProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient));
        if (properties.getInternalAuthKey() != null && !properties.getInternalAuthKey().isEmpty()) {
            builder.defaultHeader(INTERNAL_AUTH_HEADER, properties.getInternalAuthKey());
        }
        this.webClient = builder.build();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(1024)
                .build();
    }

    /**
     * Screen a name. Returns {@code null} when the AML microservice is disabled or
     * the circuit breaker is open — callers MUST handle null as "unavailable".
     */
    public BackendSanctionsScreenResponse screen(BackendSanctionsScreenRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) return null;
        if (!properties.isEnabled()) return null;

        String cacheKey = req.name().toLowerCase() + "|" + (req.type() == null ? "" : req.type());
        BackendSanctionsScreenResponse cached = cache.getIfPresent(cacheKey);
        if (cached != null) return cached;

        BackendSanctionsScreenResponse fresh = doScreen(req);
        if (fresh != null) cache.put(cacheKey, fresh);
        return fresh;
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "screenFallback")
    @Retry(name = CB_NAME)
    public BackendSanctionsScreenResponse doScreen(BackendSanctionsScreenRequest req) {
        try {
            return webClient.post()
                    .uri("/internal/v1/sanctions/screen")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(BackendSanctionsScreenResponse.class)
                    .block(Duration.ofMillis(properties.getReadTimeoutMs() + 100L));
        } catch (Exception e) {
            log.warn("Sanctions screen call failed for name='{}': {}", req.name(), e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unused")
    private BackendSanctionsScreenResponse screenFallback(BackendSanctionsScreenRequest req, Throwable t) {
        log.debug("Sanctions screen fallback engaged (name='{}', reason={})",
                req != null ? req.name() : null, t.getMessage());
        return null;
    }

    // ---------- DTOs (mirror the microservice's wire format) ----------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BackendSanctionsScreenRequest(String name, String type, Long pspId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BackendSanctionsScreenResponse(
            String name,
            String status,
            List<MatchDto> matches,
            Instant checkedAt
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record MatchDto(
                String matchedName,
                double similarityScore,
                String listName,
                String entityId
        ) {}
    }
}
