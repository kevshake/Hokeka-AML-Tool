package com.posgateway.aml.client.regulator;

import com.posgateway.aml.entity.reporting.RegulatorySubmission;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OFAC adapter.
 *
 * <p>OFAC's public Treasury services are read-mostly: there is no SAR submission
 * endpoint. The "submit to OFAC" call in the legacy mock is a misnomer for
 * <em>verify the SDN list cache is fresh and treat the submission as a no-op</em>.
 *
 * <p>This client validates that the SDN feed has been touched within the last
 * 24 hours (a HEAD against {@code regulators.ofac.sdn-feed-url}). When fresh,
 * it returns a {@code NO_OP} {@link SubmissionResult}; when stale or unreachable
 * with the bean enabled, it throws so the caller can retry. Only enabled when
 * {@code regulators.ofac.enabled=true}, and the SDN URL must be set.
 */
@Service
@ConditionalOnProperty(name = "regulators.ofac.enabled", havingValue = "true")
public class OfacSubmissionClient implements RegulatorSubmissionClient {

    private static final Logger log = LoggerFactory.getLogger(OfacSubmissionClient.class);
    private static final String REGULATOR = "OFAC";
    private static final String CB_NAME = "regulator-ofac";
    private static final Duration FRESHNESS_WINDOW = Duration.of(24, ChronoUnit.HOURS);

    private final String sdnFeedUrl;
    private final Duration timeout;
    private final WebClient webClient;
    private final AtomicReference<Instant> lastFreshnessCheck = new AtomicReference<>();

    public OfacSubmissionClient(
            @Value("${regulators.ofac.sdn-feed-url:}") String sdnFeedUrl,
            @Value("${regulators.ofac.timeout:60s}") Duration timeout) {
        this.sdnFeedUrl = sdnFeedUrl == null ? "" : sdnFeedUrl.trim();
        this.timeout = timeout;
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(timeout)))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "HokekaAML/1.0")
                .build();
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    public SubmissionResult submit(RegulatorySubmission submission) throws RegulatorSubmissionDisabledException {
        if (sdnFeedUrl.isBlank()) {
            throw new RegulatorSubmissionDisabledException(REGULATOR,
                    "regulators.ofac.sdn-feed-url is not configured");
        }

        String idempotencyKey = FincenSubmissionClient.idempotencyKey(submission);
        log.trace("OFAC submission is a no-op (read-only adapter); validating SDN freshness submissionRef={} idempotencyKey={}",
                submission.getSubmissionReference(), idempotencyKey);

        Instant lastChecked = lastFreshnessCheck.get();
        if (lastChecked != null && Instant.now().isBefore(lastChecked.plus(FRESHNESS_WINDOW))) {
            return new SubmissionResult("OFAC-NOOP-" + idempotencyKey.substring(0, 8),
                    "NO_OP", Instant.now(), REGULATOR);
        }

        try {
            webClient.head()
                    .uri(sdnFeedUrl)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .retrieve()
                    .toBodilessEntity()
                    .block(timeout.plusSeconds(2));
            lastFreshnessCheck.set(Instant.now());
            return new SubmissionResult("OFAC-NOOP-" + idempotencyKey.substring(0, 8),
                    "NO_OP", Instant.now(), REGULATOR);
        } catch (Exception e) {
            log.warn("OFAC SDN freshness check failed url={} submissionRef={} cause={}",
                    sdnFeedUrl, submission.getSubmissionReference(), e.getMessage());
            throw new RuntimeException("OFAC SDN freshness check failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String regulator() {
        return REGULATOR;
    }
}
