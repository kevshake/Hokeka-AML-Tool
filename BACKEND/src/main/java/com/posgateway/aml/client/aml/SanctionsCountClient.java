package com.posgateway.aml.client.aml;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Thin client for {@code aml-microservice}'s {@code GET /internal/v1/sanctions/count}.
 *
 * <p>Returns the live count of records in the {@code sanctions} Aerospike set,
 * or -1 when Aerospike is unavailable. The microservice itself returns -1 in
 * that case; we additionally return -1 here whenever the AML microservice is
 * disabled or the circuit breaker is open so that the dashboard never claims
 * "0 sanctions records" (which would be misleading).
 */
@Component
public class SanctionsCountClient {

    private static final Logger log = LoggerFactory.getLogger(SanctionsCountClient.class);
    private static final String CB_NAME = "amlMicroservice";
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    private final AmlMicroserviceProperties properties;
    private final WebClient webClient;

    public SanctionsCountClient(AmlMicroserviceProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient));
        if (properties.getInternalAuthKey() != null && !properties.getInternalAuthKey().isEmpty()) {
            builder.defaultHeader(INTERNAL_AUTH_HEADER, properties.getInternalAuthKey());
        }
        this.webClient = builder.build();
    }

    /** Returns the sanctions record count, or -1 if unavailable. */
    public long getCount() {
        if (!properties.isEnabled()) return -1L;
        return doGetCount();
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "countFallback")
    public long doGetCount() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = webClient.get()
                    .uri("/internal/v1/sanctions/count")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(properties.getReadTimeoutMs() + 100L));
            if (body == null) return -1L;
            Object raw = body.get("count");
            if (raw instanceof Number n) return n.longValue();
            if (raw != null) {
                try { return Long.parseLong(raw.toString()); } catch (NumberFormatException ignored) {}
            }
            return -1L;
        } catch (Exception e) {
            log.warn("Sanctions count call failed: {}", e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unused")
    private long countFallback(Throwable t) {
        log.debug("Sanctions count fallback engaged: {}", t.getMessage());
        return -1L;
    }
}
