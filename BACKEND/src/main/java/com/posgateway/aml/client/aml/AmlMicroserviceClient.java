package com.posgateway.aml.client.aml;

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
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the {@code aml-microservice}'s {@code /internal/v1/aml/score} endpoint.
 *
 * <p>Acts as a "layer 0" cache in front of the local {@code FraudDetectionOrchestrator}
 * pipeline. Wrapped in a Resilience4j circuit-breaker + retry — when the microservice is
 * down, slow, or returns an error, the fallback returns {@code null} and the caller MUST
 * fall through to the local pipeline. Treat {@code null} as "service unavailable, run
 * local scoring".
 */
@Component
public class AmlMicroserviceClient {

    private static final Logger log = LoggerFactory.getLogger(AmlMicroserviceClient.class);
    private static final String CB_NAME = "amlMicroservice";
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    private final AmlMicroserviceProperties properties;
    private final WebClient webClient;

    public AmlMicroserviceClient(AmlMicroserviceProperties properties) {
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
    }

    /**
     * Score a transaction via the AML microservice.
     *
     * @return microservice response, or {@code null} when the call is suppressed by the
     *         circuit-breaker / fallback. Callers MUST run the local pipeline on null.
     */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "scoreFallback")
    @Retry(name = CB_NAME)
    public AmlScoreResponse score(AmlScoreRequest req) {
        if (!properties.isEnabled()) {
            return null;
        }
        try {
            return webClient.post()
                    .uri("/internal/v1/aml/score")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(AmlScoreResponse.class)
                    .block(Duration.ofMillis(properties.getReadTimeoutMs() + 100L));
        } catch (Exception e) {
            log.warn("AML microservice score() failed for txnId={} pspId={}: {}",
                    req != null ? req.transactionId() : null,
                    req != null ? req.pspId() : null,
                    e.getMessage());
            throw e;
        }
    }

    /**
     * Resilience4j fallback. Signature MUST match {@link #score(AmlScoreRequest)} plus
     * the trailing {@link Throwable}.
     */
    @SuppressWarnings("unused")
    private AmlScoreResponse scoreFallback(AmlScoreRequest req, Throwable t) {
        log.debug("AML microservice fallback engaged (txnId={}, reason={})",
                req != null ? req.transactionId() : null, t.getMessage());
        return null;
    }
}
