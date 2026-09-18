package com.posgateway.aml.client.regulator;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configurable FRC goAML XML transport.
 *
 * <p>FRC publishes the goAML XML schema and portal upload workflow, but does
 * not publish a universal API URL. Deployments therefore supply the endpoint
 * and credentials provisioned to their reporting institution. An unconfigured
 * deployment fails closed and leaves the filing pending.
 */
@Service
@ConditionalOnProperty(name = "regulators.frc.enabled", havingValue = "true")
public class FrcSubmissionClient implements RegulatorSubmissionClient {

    private static final Logger log = LoggerFactory.getLogger(FrcSubmissionClient.class);
    private static final String REGULATOR = "FRC";
    private static final String CB_NAME = "regulator-frc";
    private static final long MAX_XML_BYTES = 25L * 1024L * 1024L;
    private static final Pattern XML_REFERENCE = Pattern.compile(
            "<\\s*(?:submissionReference|reference|requestId|reportReference)\\s*>\\s*([^<]+)\\s*<",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_STATUS = Pattern.compile(
            "<\\s*(?:status|submissionStatus|processingStatus)\\s*>\\s*([^<]+)\\s*<",
            Pattern.CASE_INSENSITIVE);

    private final String endpoint;
    private final String bearerToken;
    private final String apiKey;
    private final String apiKeyHeader;
    private final String schemaPath;
    private final Duration timeout;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public FrcSubmissionClient(
            @Value("${regulators.frc.endpoint:}") String endpoint,
            @Value("${regulators.frc.bearer-token:}") String bearerToken,
            @Value("${regulators.frc.api-key:}") String apiKey,
            @Value("${regulators.frc.api-key-header:X-API-Key}") String apiKeyHeader,
            @Value("${regulators.frc.schema-path:}") String schemaPath,
            @Value("${regulators.frc.timeout:30s}") Duration timeout,
            ObjectMapper objectMapper) {
        this.endpoint = trim(endpoint);
        this.bearerToken = trim(bearerToken);
        this.apiKey = trim(apiKey);
        this.apiKeyHeader = apiKeyHeader == null || apiKeyHeader.isBlank()
                ? "X-API-Key" : apiKeyHeader.trim();
        this.schemaPath = trim(schemaPath);
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(timeout)))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE + ", " + MediaType.APPLICATION_XML_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "HokekaAML/1.0")
                .build();
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    public SubmissionResult submit(RegulatorySubmission submission)
            throws RegulatorSubmissionDisabledException {
        validateConfiguration();
        String xml = loadXml(submission);
        validateXml(xml);
        String idempotencyKey = FincenSubmissionClient.idempotencyKey(submission);

        try {
            WebClient.RequestBodySpec request = webClient.post()
                    .uri(endpoint)
                    .header("X-Idempotency-Key", idempotencyKey);
            if (!bearerToken.isBlank()) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            }
            if (!apiKey.isBlank()) {
                request.header(apiKeyHeader, apiKey);
            }
            ResponseEntity<String> httpResponse = request.bodyValue(xml)
                    .retrieve()
                    .toEntity(String.class)
                    .block(timeout.plusSeconds(2));
            if (httpResponse == null) {
                throw new RegulatorTransportException(
                        "FRC returned no HTTP response", null, "EMPTY_RESPONSE", null);
            }
            String response = httpResponse.getBody();

            String reference = extractReference(response);
            return new SubmissionResult(
                    reference,
                    extractStatus(response),
                    Instant.now(),
                    REGULATOR,
                    httpResponse.getStatusCode().value(),
                    response);
        } catch (WebClientResponseException failure) {
            String responseBody = failure.getResponseBodyAsString();
            throw new RegulatorTransportException(
                    "FRC returned HTTP " + failure.getStatusCode().value(),
                    failure.getStatusCode().value(),
                    extractStatus(responseBody),
                    responseBody,
                    failure);
        } catch (RuntimeException failure) {
            log.warn("FRC submission failed localRef={} endpoint={} cause={}",
                    submission.getSubmissionReference(), endpoint, failure.getMessage());
            if (failure instanceof RegulatorTransportException transportFailure) {
                throw transportFailure;
            }
            throw new RuntimeException("FRC goAML submission failed: " + failure.getMessage(), failure);
        }
    }

    @Override
    public String regulator() {
        return REGULATOR;
    }

    private void validateConfiguration() throws RegulatorSubmissionDisabledException {
        if (endpoint.isBlank()) {
            throw new RegulatorSubmissionDisabledException(
                    REGULATOR, "regulators.frc.endpoint is not configured");
        }
        if (bearerToken.isBlank() && apiKey.isBlank()) {
            throw new RegulatorSubmissionDisabledException(
                    REGULATOR, "FRC goAML transport credentials are not configured");
        }
        if (schemaPath.isBlank()) {
            throw new RegulatorSubmissionDisabledException(
                    REGULATOR, "regulators.frc.schema-path is required for pre-submission XSD validation");
        }
    }

    private String loadXml(RegulatorySubmission submission) {
        Object inline = submission.getSubmittedData() != null
                ? submission.getSubmittedData().get("xmlPayload") : null;
        if (inline instanceof String xml && !xml.isBlank()) {
            enforceSize(xml);
            return xml;
        }

        Map<String, String> attachments = submission.getAttachmentPaths();
        String pathValue = attachments != null
                ? firstNonBlank(attachments.get("xml"), attachments.get("primary")) : null;
        if (pathValue == null) {
            throw new IllegalStateException("Submission has no generated goAML XML artifact");
        }
        try {
            Path path = Path.of(pathValue).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IllegalStateException("goAML XML artifact is not readable: " + path);
            }
            long size = Files.size(path);
            if (size <= 0 || size > MAX_XML_BYTES) {
                throw new IllegalStateException("goAML XML artifact size is invalid: " + size);
            }
            String xml = Files.readString(path);
            enforceSize(xml);
            return xml;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to read goAML XML artifact", failure);
        }
    }

    private void validateXml(String xml) throws RegulatorSubmissionDisabledException {
        if (xml == null || xml.isBlank() || !xml.stripLeading().startsWith("<?xml")) {
            throw new IllegalStateException("goAML payload must be a non-empty XML document");
        }
        if (schemaPath.isBlank()) return;
        try {
            Path xsd = Path.of(schemaPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(xsd) || !Files.isReadable(xsd)) {
                throw new RegulatorSubmissionDisabledException(
                        REGULATOR, "regulators.frc.schema-path is not readable");
            }
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.newSchema(xsd.toFile()).newValidator()
                    .validate(new StreamSource(new StringReader(xml)));
        } catch (RegulatorSubmissionDisabledException failure) {
            throw failure;
        } catch (Exception validationFailure) {
            throw new IllegalStateException(
                    "goAML XML failed configured XSD validation: " + validationFailure.getMessage(),
                    validationFailure);
        }
    }

    private String extractReference(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode json = objectMapper.readTree(body);
            for (String field : List.of("submissionReference", "reference", "requestId", "reportReference", "id")) {
                JsonNode value = json.get(field);
                if (value != null && !value.isNull() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
        } catch (Exception ignored) {
            Matcher matcher = XML_REFERENCE.matcher(body);
            if (matcher.find()) return matcher.group(1).trim();
        }
        return null;
    }

    private String extractStatus(String body) {
        // Fail-closed: a 2xx whose body is empty or unparseable is NOT evidence of acceptance.
        // Return a non-accepting "UNKNOWN" so the filing stays pending (the orchestrator only
        // marks FILED on a recognized acceptance status + a real reference), instead of a
        // fabricated "RECEIVED" that reads like a genuine regulator acknowledgment.
        if (body == null || body.isBlank()) return "UNKNOWN";
        try {
            JsonNode json = objectMapper.readTree(body);
            for (String field : List.of("status", "submissionStatus", "processingStatus")) {
                JsonNode status = json.get(field);
                if (status != null && !status.asText().isBlank()) {
                    return status.asText();
                }
            }
        } catch (Exception ignored) {
            Matcher matcher = XML_STATUS.matcher(body);
            if (matcher.find()) return matcher.group(1).trim();
        }
        return "UNKNOWN";
    }

    private static void enforceSize(String xml) {
        if (xml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_XML_BYTES) {
            throw new IllegalStateException("goAML XML exceeds the 25 MB transport limit");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
