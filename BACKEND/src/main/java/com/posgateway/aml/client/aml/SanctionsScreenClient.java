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
 * <p>Disabled, failed, and circuit-broken calls return an explicit
 * {@code UNAVAILABLE} response.
 */
@Component
public class SanctionsScreenClient {

    private static final Logger log = LoggerFactory.getLogger(SanctionsScreenClient.class);
    // W14-5 fix: was "amlMicroservice", shared with the unrelated AML risk-scoring path
    // (ScoringService). SanctionsController legitimately 503s for its own UNAVAILABLE status, and
    // Resilience4j breaker instances are shared by name application-wide -- a burst of sanctions
    // 503s opened the shared breaker and degraded AML risk scoring too. Dedicated breaker, same
    // settings, configured in application.properties alongside the original.
    private static final String CB_NAME = "sanctionsScreening";
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
     * Screen a name. Disabled or failed upstream calls return status
     * {@code UNAVAILABLE}.
     */
    public BackendSanctionsScreenResponse screen(BackendSanctionsScreenRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("A non-blank name is required for sanctions screening");
        }
        if (!properties.isEnabled()) {
            return BackendSanctionsScreenResponse.unavailable(req.name());
        }

        String cacheKey = req.name().toLowerCase() + "|" + (req.type() == null ? "" : req.type());
        BackendSanctionsScreenResponse cached = cache.getIfPresent(cacheKey);
        if (cached != null) return cached;

        BackendSanctionsScreenResponse fresh = doScreen(req);
        if (!fresh.isUnavailable()) cache.put(cacheKey, fresh);
        return fresh;
    }

    /** Verify HTTP reachability and Aerospike availability in aml-microservice. */
    public boolean isAvailable() {
        if (!properties.isEnabled()) return false;
        try {
            CountResponse response = webClient.get()
                    .uri("/internal/v1/sanctions/count")
                    .retrieve()
                    .bodyToMono(CountResponse.class)
                    .block(Duration.ofMillis(properties.getReadTimeoutMs() + 100L));
            return response != null && response.count() >= 0;
        } catch (Exception e) {
            log.warn("Sanctions health check failed: {}", e.getMessage());
            return false;
        }
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "screenFallback")
    @Retry(name = CB_NAME)
    public BackendSanctionsScreenResponse doScreen(BackendSanctionsScreenRequest req) {
        try {
            BackendSanctionsScreenResponse response = webClient.post()
                    .uri("/internal/v1/sanctions/screen")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(BackendSanctionsScreenResponse.class)
                    .block(Duration.ofMillis(properties.getReadTimeoutMs() + 100L));
            return response != null
                    ? response
                    : BackendSanctionsScreenResponse.unavailable(req.name());
        } catch (Exception e) {
            log.warn("Sanctions screen call failed for name='{}': {}", req.name(), e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unused")
    private BackendSanctionsScreenResponse screenFallback(BackendSanctionsScreenRequest req, Throwable t) {
        log.debug("Sanctions screen fallback engaged (name='{}', reason={})",
                req != null ? req.name() : null, t.getMessage());
        return BackendSanctionsScreenResponse.unavailable(
                req != null ? req.name() : null);
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
        public static BackendSanctionsScreenResponse unavailable(String name) {
            return new BackendSanctionsScreenResponse(
                    name, "UNAVAILABLE", List.of(), Instant.now());
        }

        public boolean isUnavailable() {
            return status == null || "UNAVAILABLE".equalsIgnoreCase(status);
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record MatchDto(
                String matchedName,
                double similarityScore,
                String listName,
                String entityId,
                String pepLevel
        ) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CountResponse(long count) {}
}
