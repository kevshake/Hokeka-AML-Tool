package com.posgateway.aml.client.regulator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.reporting.RegulatorySubmission;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * FCA / NCA SAR Online JSON submission client. Authenticates with an API
 * key plus an HMAC-SHA256 signature of the request body (header
 * {@code X-Signature}, {@code X-API-Key}).
 */
@Service
@ConditionalOnProperty(name = "regulators.fca.enabled", havingValue = "true")
public class FcaSubmissionClient implements RegulatorSubmissionClient {

    private static final Logger log = LoggerFactory.getLogger(FcaSubmissionClient.class);
    private static final String REGULATOR = "FCA";
    private static final String CB_NAME = "regulator-fca";

    private final String endpoint;
    private final String apiKey;
    private final String hmacSecret;
    private final Duration timeout;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public FcaSubmissionClient(
            @Value("${regulators.fca.endpoint:}") String endpoint,
            @Value("${regulators.fca.api-key:}") String apiKey,
            @Value("${regulators.fca.hmac-secret:}") String hmacSecret,
            @Value("${regulators.fca.timeout:15s}") Duration timeout,
            ObjectMapper objectMapper) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.apiKey = apiKey == null ? "" : apiKey;
        this.hmacSecret = hmacSecret == null ? "" : hmacSecret;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(timeout)))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "HokekaAML/1.0")
                .build();
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    public SubmissionResult submit(RegulatorySubmission submission) throws RegulatorSubmissionDisabledException {
        if (endpoint.isBlank()) {
            throw new RegulatorSubmissionDisabledException(REGULATOR,
                    "regulators.fca.endpoint is not configured");
        }
        if (apiKey.isBlank() || hmacSecret.isBlank()) {
            throw new RegulatorSubmissionDisabledException(REGULATOR,
                    "regulators.fca.api-key / hmac-secret not configured");
        }

        String idempotencyKey = FincenSubmissionClient.idempotencyKey(submission);
        String body;
        try {
            body = objectMapper.writeValueAsString(buildPayload(submission, idempotencyKey));
        } catch (JsonProcessingException e) {
            log.warn("FCA payload serialization failed submissionRef={}: {}",
                    submission.getSubmissionReference(), e.getMessage());
            throw new RuntimeException("FCA payload serialization failed", e);
        }

        String signature = hmacSha256Hex(hmacSecret, body);

        try {
            ResponseEntity<String> response = webClient.post()
                    .uri(endpoint)
                    .header("X-API-Key", apiKey)
                    .header("X-Signature", signature)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .bodyValue(body)
                    .retrieve()
                    .toEntity(String.class)
                    .block(timeout.plusSeconds(2));
            if (response == null) {
                throw new RegulatorTransportException(
                        "FCA returned no HTTP response", null, "EMPTY_RESPONSE", null);
            }
            FcaResponse resp = parseResponse(response.getBody());

            String status = resp != null && resp.status != null ? resp.status : "RECEIVED";
            return new SubmissionResult(
                    resp != null ? resp.submissionReference : null,
                    status,
                    Instant.now(),
                    REGULATOR,
                    response.getStatusCode().value(),
                    response.getBody());
        } catch (WebClientResponseException failure) {
            String responseBody = failure.getResponseBodyAsString();
            FcaResponse response = parseResponse(responseBody);
            throw new RegulatorTransportException(
                    "FCA returned HTTP " + failure.getStatusCode().value(),
                    failure.getStatusCode().value(),
                    response != null ? response.status : "HTTP_ERROR",
                    responseBody,
                    failure);
        } catch (Exception e) {
            log.warn("FCA submission failed submissionRef={} idempotencyKey={} endpoint={} cause={}",
                    submission.getSubmissionReference(), idempotencyKey, endpoint, e.getMessage());
            if (e instanceof RegulatorTransportException transportFailure) {
                throw transportFailure;
            }
            throw new RuntimeException("FCA submission failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String regulator() {
        return REGULATOR;
    }

    private Map<String, Object> buildPayload(RegulatorySubmission s, String idempotencyKey) {
        Map<String, Object> p = new HashMap<>();
        p.put("submissionReference", s.getSubmissionReference());
        p.put("idempotencyKey", idempotencyKey);
        p.put("regulatorCode", s.getRegulatorCode());
        p.put("submissionType", s.getSubmissionType());
        p.put("jurisdiction", s.getJurisdiction());
        p.put("filingPeriodStart", s.getFilingPeriodStart() != null ? s.getFilingPeriodStart().toString() : null);
        p.put("filingPeriodEnd", s.getFilingPeriodEnd() != null ? s.getFilingPeriodEnd().toString() : null);
        p.put("data", s.getSubmittedData());
        p.put("attachments", s.getAttachmentPaths());
        return p;
    }

    static String hmacSha256Hex(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sig);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 unavailable", e);
        }
    }

    private FcaResponse parseResponse(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return objectMapper.readValue(body, FcaResponse.class);
        } catch (JsonProcessingException failure) {
            log.warn("Unable to parse FCA response body: {}", failure.getMessage());
            return null;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static final class FcaResponse {
        public String submissionReference;
        public String status;
    }
}
