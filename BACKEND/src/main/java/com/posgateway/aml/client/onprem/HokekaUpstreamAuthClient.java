package com.posgateway.aml.client.onprem;

import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import com.posgateway.aml.dto.onprem.OnPremLeaseRequest;
import com.posgateway.aml.dto.onprem.OnPremLeaseResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Real HTTP client used by on-prem instances to obtain/renew leases from Hokeka central.
 * Protected by Resilience4j circuit breaker + retry ({@code hokeka-auth}).
 */
@Component
@ConditionalOnProperty(name = "hokeka.auth.enabled", havingValue = "true")
public class HokekaUpstreamAuthClient {

    private static final Logger log = LoggerFactory.getLogger(HokekaUpstreamAuthClient.class);
    private static final String CB_NAME = "hokeka-auth";

    private final HokekaAuthProperties properties;
    private final WebClient webClient;
    private final Duration timeout;

    public HokekaUpstreamAuthClient(HokekaAuthProperties properties) {
        this.properties = properties;
        this.timeout = Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds()));
        String base = trimTrailingSlash(properties.getUpstreamUrl());
        this.webClient = WebClient.builder()
                .baseUrl(base)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(timeout)))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "HokekaOnPremAuth/1.0")
                .build();
    }

    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    public OnPremLeaseResponse requestLease() {
        validateConfig();
        OnPremLeaseRequest body = new OnPremLeaseRequest(
                properties.getClientId(),
                properties.getClientSecret(),
                properties.getInstanceId(),
                blankToNull(properties.getHostname()),
                blankToNull(properties.getAgentVersion()));

        try {
            OnPremLeaseResponse response = webClient.post()
                    .uri("/api/v1/onprem/auth/lease")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(OnPremLeaseResponse.class)
                    .block(timeout.plusSeconds(2));
            if (response == null || response.leaseToken() == null || response.validUntil() == null) {
                throw new UpstreamAuthException("Upstream returned an empty or incomplete lease");
            }
            return response;
        } catch (WebClientResponseException e) {
            log.warn("Upstream lease auth failed HTTP {} body={}",
                    e.getStatusCode().value(), truncate(e.getResponseBodyAsString()));
            throw new UpstreamAuthException(
                    "Upstream refused lease: HTTP " + e.getStatusCode().value(), e);
        } catch (UpstreamAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Upstream lease auth unreachable: {}", e.getMessage());
            throw new UpstreamAuthException("Upstream unreachable: " + e.getMessage(), e);
        }
    }

    private void validateConfig() {
        if (!StringUtils.hasText(properties.getUpstreamUrl())
                || !StringUtils.hasText(properties.getClientId())
                || !StringUtils.hasText(properties.getClientSecret())
                || !StringUtils.hasText(properties.getInstanceId())) {
            throw new UpstreamAuthException(
                    "hokeka.auth.upstream-url / client-id / client-secret / instance-id must be set");
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }

    public static class UpstreamAuthException extends RuntimeException {
        public UpstreamAuthException(String message) {
            super(message);
        }

        public UpstreamAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
