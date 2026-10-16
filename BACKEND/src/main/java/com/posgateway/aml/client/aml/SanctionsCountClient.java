package com.posgateway.aml.client.aml;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Thin client for {@code aml-microservice}'s {@code GET /internal/v1/sanctions/count}.
 *
 * <p>Returns the live count of records in the {@code sanctions} Aerospike set, or -1 when it cannot
 * be determined (microservice disabled, unreachable, or answering 503 because Aerospike is down), so
 * the dashboard never claims "0 sanctions records" when the truth is "unknown".
 *
 * <p><b>Timeout.</b> This probe used to share {@code aml.microservice.read-timeout-ms} (400 ms, sized
 * for inline screening) and blocked for only 500 ms, while the microservice answered by streaming the
 * whole set through an Aerospike query — so it timed out on every call and the {@code sanctionsData}
 * health indicator stayed DOWN although the watchlist was loaded. The microservice now answers from
 * set metadata, and this client has its own {@code aml.microservice.count-timeout-ms} (default 3 s).
 *
 * <p><b>No circuit breaker.</b> The count is a health probe: it must reflect the live state on every
 * call, and its failures must never open the {@code sanctionsScreening} breaker that protects real
 * screening traffic. (The former {@code @CircuitBreaker} was also inert — it sat on a self-invoked
 * method that the Spring proxy never intercepted.)
 */
@Component
public class SanctionsCountClient {

    private static final Logger log = LoggerFactory.getLogger(SanctionsCountClient.class);
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    static final String COUNT_PATH = "/internal/v1/sanctions/count";

    private final AmlMicroserviceProperties properties;
    private final WebClient webClient;

    public SanctionsCountClient(AmlMicroserviceProperties properties) {
        this.properties = properties;
        int timeoutMs = countTimeoutMs(properties);
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient));
        if (properties.getInternalAuthKey() != null && !properties.getInternalAuthKey().isEmpty()) {
            builder.defaultHeader(INTERNAL_AUTH_HEADER, properties.getInternalAuthKey());
        }
        this.webClient = builder.build();
    }

    /** Effective count timeout: the dedicated setting, never below the screening read timeout. */
    static int countTimeoutMs(AmlMicroserviceProperties properties) {
        return Math.max(properties.getCountTimeoutMs(), properties.getReadTimeoutMs());
    }

    /**
     * Returns the sanctions record count, or -1 if unavailable. Never throws: callers are health
     * checks and dashboards that must degrade to "unknown", not fail.
     */
    public long getCount() {
        if (!properties.isEnabled()) return -1L;
        try {
            return fetchCount();
        } catch (WebClientResponseException e) {
            // 503 {"count":-1,"status":"UNAVAILABLE"} = Aerospike down; 401/403 = internal key mismatch.
            log.warn("Sanctions count unavailable: aml-microservice answered HTTP {}", e.getStatusCode().value());
            return -1L;
        } catch (Exception e) {
            log.warn("Sanctions count unavailable: {}", e.getMessage());
            return -1L;
        }
    }

    /** Performs the HTTP call and parses {@code {"count": n}}; -1 for a missing/garbled body. */
    long fetchCount() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = webClient.get()
                .uri(COUNT_PATH)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofMillis(countTimeoutMs(properties) + 250L));
        if (body == null) return -1L;
        Object raw = body.get("count");
        if (raw instanceof Number n) return n.longValue();
        if (raw != null) {
            try {
                return Long.parseLong(raw.toString());
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        return -1L;
    }
}
