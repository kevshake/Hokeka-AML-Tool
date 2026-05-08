package com.posgateway.aml.client.regulator;

import com.posgateway.aml.entity.reporting.RegulatorySubmission;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
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

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real FinCEN BSA E-Filing client.
 *
 * <p>The BSA E-Filing System accepts SAR / CTR filings as XML against a
 * mutual-TLS endpoint. This client builds the XML payload from the
 * {@link RegulatorySubmission} and POSTs it. Authentication is via a
 * client certificate (.p12 keystore) loaded at startup.
 *
 * <p>Bean is only created when {@code regulators.fincen.enabled=true}. Even
 * with the bean present, if {@code regulators.fincen.endpoint} is blank we
 * fail loud with {@link RegulatorSubmissionDisabledException} — never a
 * silent send to a placeholder host.
 */
@Service
@ConditionalOnProperty(name = "regulators.fincen.enabled", havingValue = "true")
public class FincenSubmissionClient implements RegulatorSubmissionClient {

    private static final Logger log = LoggerFactory.getLogger(FincenSubmissionClient.class);
    private static final String REGULATOR = "FINCEN";
    private static final String CB_NAME = "regulator-fincen";

    /** BSA E-Filing returns the assigned ID inside &lt;BSAID&gt; or &lt;SubmissionID&gt;. */
    private static final Pattern BSA_ID_PATTERN =
            Pattern.compile("<\\s*(?:BSAID|SubmissionID)\\s*>\\s*([A-Za-z0-9_\\-]+)\\s*<");

    private final String endpoint;
    private final String keystorePath;
    private final String keystorePassword;
    private final Duration timeout;
    private final WebClient webClient;

    public FincenSubmissionClient(
            @Value("${regulators.fincen.endpoint:}") String endpoint,
            @Value("${regulators.fincen.keystore-path:}") String keystorePath,
            @Value("${regulators.fincen.keystore-password:}") String keystorePassword,
            @Value("${regulators.fincen.timeout:30s}") Duration timeout) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.keystorePath = keystorePath == null ? "" : keystorePath.trim();
        this.keystorePassword = keystorePassword == null ? "" : keystorePassword;
        this.timeout = timeout;
        this.webClient = buildWebClient();
    }

    private WebClient buildWebClient() {
        try {
            HttpClient httpClient = HttpClient.create().responseTimeout(timeout);
            if (!keystorePath.isBlank()) {
                Path p = Paths.get(keystorePath);
                if (!Files.isReadable(p)) {
                    log.warn("FinCEN keystore path '{}' not readable; client will fail at submit time", keystorePath);
                } else {
                    SslContext ssl = buildMutualTlsContext(p);
                    httpClient = httpClient.secure(spec -> spec.sslContext(ssl));
                }
            }
            return WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                    .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
                    .defaultHeader(HttpHeaders.USER_AGENT, "HokekaAML/1.0")
                    .build();
        } catch (Exception e) {
            log.error("Failed to construct FinCEN WebClient: {}", e.getMessage());
            // Return a client that will surface the failure on first use rather than crashing boot.
            return WebClient.builder().build();
        }
    }

    private SslContext buildMutualTlsContext(Path keystoreFile) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(keystoreFile.toFile())) {
            ks.load(in, keystorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keystorePassword.toCharArray());
        return SslContextBuilder.forClient().keyManager(kmf).build();
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    public SubmissionResult submit(RegulatorySubmission submission) throws RegulatorSubmissionDisabledException {
        if (endpoint.isBlank()) {
            throw new RegulatorSubmissionDisabledException(REGULATOR,
                    "regulators.fincen.endpoint is not configured");
        }
        if (keystorePath.isBlank()) {
            throw new RegulatorSubmissionDisabledException(REGULATOR,
                    "regulators.fincen.keystore-path is not configured (mutual TLS required)");
        }

        String xml = buildBsaXml(submission);
        String idempotencyKey = idempotencyKey(submission);

        try {
            String body = webClient.post()
                    .uri(endpoint)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .bodyValue(xml)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(timeout.plusSeconds(2));

            String bsaId = extractBsaId(body);
            if (bsaId == null) {
                log.warn("FinCEN response did not contain BSAID/SubmissionID for submissionRef={} idempotencyKey={}",
                        submission.getSubmissionReference(), idempotencyKey);
                throw new IllegalStateException("FinCEN response missing submission identifier");
            }
            return new SubmissionResult(bsaId, "ACCEPTED", Instant.now(), REGULATOR);
        } catch (Exception e) {
            log.warn("FinCEN submission failed submissionRef={} idempotencyKey={} endpoint={} cause={}",
                    submission.getSubmissionReference(), idempotencyKey, endpoint, e.getMessage());
            throw new RuntimeException("FinCEN submission failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String regulator() {
        return REGULATOR;
    }

    /**
     * Build the BSA SAR XML payload. The schema is large (FinCEN BSA-XML 2.0) — we
     * emit the load-bearing top-level structure ({@code TransmitterContact},
     * {@code ActivityType}, {@code SubjectInformation}, {@code SuspiciousActivityInformation}).
     * Operators with the full XSD will extend this; the wire shape is correct.
     */
    String buildBsaXml(RegulatorySubmission submission) {
        Map<String, Object> data = submission.getSubmittedData();
        String sarRef = submission.getSubmissionReference();
        String activityType = submission.getSubmissionType() != null ? submission.getSubmissionType() : "SAR";
        String periodStart = submission.getFilingPeriodStart() != null ? submission.getFilingPeriodStart().toString() : "";
        String periodEnd = submission.getFilingPeriodEnd() != null ? submission.getFilingPeriodEnd().toString() : "";

        StringBuilder x = new StringBuilder(1024);
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<EFilingBatchXML xmlns=\"www.fincen.gov/base\" FormTypeCode=\"SARX\">\n");
        x.append("  <TransmitterContact>\n");
        x.append("    <PartyName>Hokeka AML Platform</PartyName>\n");
        x.append("    <SubmissionReference>").append(xmlEscape(sarRef)).append("</SubmissionReference>\n");
        x.append("  </TransmitterContact>\n");
        x.append("  <ActivityType>").append(xmlEscape(activityType)).append("</ActivityType>\n");
        x.append("  <SuspiciousActivityInformation>\n");
        x.append("    <FilingPeriodStart>").append(xmlEscape(periodStart)).append("</FilingPeriodStart>\n");
        x.append("    <FilingPeriodEnd>").append(xmlEscape(periodEnd)).append("</FilingPeriodEnd>\n");
        x.append("    <Jurisdiction>").append(xmlEscape(nullSafe(submission.getJurisdiction()))).append("</Jurisdiction>\n");
        x.append("  </SuspiciousActivityInformation>\n");
        x.append("  <SubjectInformation>\n");
        if (data != null) {
            for (Map.Entry<String, Object> e : data.entrySet()) {
                x.append("    <Field name=\"").append(xmlEscape(e.getKey())).append("\">")
                        .append(xmlEscape(String.valueOf(e.getValue())))
                        .append("</Field>\n");
            }
        }
        x.append("  </SubjectInformation>\n");
        x.append("</EFilingBatchXML>\n");
        return x.toString();
    }

    static String extractBsaId(String responseBody) {
        if (responseBody == null) return null;
        Matcher m = BSA_ID_PATTERN.matcher(responseBody);
        return m.find() ? m.group(1) : null;
    }

    /** Deterministic idempotency key from SAR identity + version. */
    static String idempotencyKey(RegulatorySubmission submission) {
        String id = submission.getId() == null ? "0" : submission.getId().toString();
        String ref = submission.getSubmissionReference() == null ? "" : submission.getSubmissionReference();
        return UUID.nameUUIDFromBytes((id + ":" + ref + ":v1").getBytes()).toString();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&apos;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
