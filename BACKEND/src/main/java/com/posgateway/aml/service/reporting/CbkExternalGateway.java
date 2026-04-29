package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.cbk.CbkReportDTO;
import com.posgateway.aml.entity.reporting.RegulatorySubmission;
import com.posgateway.aml.entity.reporting.SubmissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Outbound gateway for the CBK regulatory reporting service.
 *
 * Per HOK-130 / HOK-133, every outbound CBK call must originate from this backend
 * (never from the frontend or PSP code paths). This component is the single egress
 * point: callers persist the {@link RegulatorySubmission} first, then invoke
 * {@link #submit(RegulatorySubmission, CbkReportDTO)} which POSTs to the configured
 * CBK endpoint and updates the submission status / acknowledgement reference based
 * on the response.
 *
 * The gateway is unconditional — when {@code cbk.reporting.base-url} is unset
 * the call is treated as a no-op and the submission stays in {@code PENDING_REVIEW}.
 */
@Component
public class CbkExternalGateway {

    private static final Logger logger = LoggerFactory.getLogger(CbkExternalGateway.class);

    private final WebClient webClient;
    private final String baseUrl;
    private final String submitPath;
    private final String apiKey;
    private final Duration timeout;

    public CbkExternalGateway(WebClient.Builder webClientBuilder,
                              @Value("${cbk.reporting.base-url:}") String baseUrl,
                              @Value("${cbk.reporting.submit-path:/v1/reports}") String submitPath,
                              @Value("${cbk.reporting.api-key:}") String apiKey,
                              @Value("${cbk.reporting.timeout-ms:8000}") long timeoutMs) {
        this.webClient = webClientBuilder.build();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.submitPath = submitPath == null || submitPath.isBlank() ? "/v1/reports" : submitPath.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    public boolean isEnabled() {
        return !baseUrl.isEmpty();
    }

    /**
     * POST the persisted submission to the external CBK reporting service.
     * Mutates {@code submission} in place: status becomes {@link SubmissionStatus#FILED}
     * with {@code filedAt} + {@code regulatorReference} on success, or
     * {@link SubmissionStatus#REJECTED} with {@code rejectionReason} on failure.
     * Caller is expected to persist the entity again.
     *
     * When the gateway is disabled the call is a no-op so local dev / unconfigured
     * environments don't break the tx.
     */
    public boolean submit(RegulatorySubmission submission, CbkReportDTO report) {
        if (!isEnabled()) {
            logger.info("CBK gateway disabled (cbk.reporting.base-url unset); leaving submission {} as PENDING_REVIEW",
                    submission.getSubmissionReference());
            return false;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("submissionReference", submission.getSubmissionReference());
        payload.put("regulatorCode", submission.getRegulatorCode());
        payload.put("submissionType", submission.getSubmissionType());
        payload.put("jurisdiction", submission.getJurisdiction());
        payload.put("filingPeriodStart", submission.getFilingPeriodStart());
        payload.put("filingPeriodEnd", submission.getFilingPeriodEnd());
        payload.put("pspId", submission.getPspId());
        payload.put("report", report);

        String url = baseUrl + submitPath;
        try {
            WebClient.RequestBodySpec spec = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            if (!apiKey.isEmpty()) {
                spec = (WebClient.RequestBodySpec) spec.header("X-API-Key", apiKey);
            }
            String body = spec
                    .bodyValue(payload)
                    .exchangeToMono(resp -> {
                        HttpStatusCode code = resp.statusCode();
                        return resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(b -> code.value() + "::" + b);
                    })
                    .timeout(timeout)
                    .block();
            String[] parts = body == null ? new String[]{"0", ""} : body.split("::", 2);
            int statusCode;
            try {
                statusCode = Integer.parseInt(parts[0]);
            } catch (NumberFormatException nfe) {
                statusCode = 0;
            }
            String responseBody = parts.length > 1 ? parts[1] : "";
            if (statusCode >= 200 && statusCode < 300) {
                submission.setStatus(SubmissionStatus.FILED);
                submission.setFiledAt(LocalDateTime.now());
                submission.setRegulatorReference(extractAck(responseBody, submission.getSubmissionReference()));
                submission.setFilingReceipt(truncate(responseBody, 4000));
                logger.info("CBK accepted submission {} via {} (HTTP {})",
                        submission.getSubmissionReference(), url, statusCode);
                return true;
            } else {
                submission.setStatus(SubmissionStatus.REJECTED);
                submission.setRejectionReason("CBK HTTP " + statusCode + ": "
                        + truncate(responseBody, 480));
                logger.warn("CBK rejected submission {} via {} (HTTP {})",
                        submission.getSubmissionReference(), url, statusCode);
                return false;
            }
        } catch (Exception ex) {
            submission.setStatus(SubmissionStatus.REJECTED);
            submission.setRejectionReason("CBK gateway error: " + truncate(ex.getMessage(), 480));
            logger.warn("CBK gateway POST {} failed for submission {}: {}",
                    url, submission.getSubmissionReference(), ex.getMessage());
            return false;
        }
    }

    private static String extractAck(String body, String fallback) {
        if (body == null) return fallback;
        int idx = body.indexOf("\"acknowledgementReference\"");
        if (idx < 0) return fallback;
        int colon = body.indexOf(':', idx);
        if (colon < 0) return fallback;
        int q1 = body.indexOf('"', colon + 1);
        if (q1 < 0) return fallback;
        int q2 = body.indexOf('"', q1 + 1);
        if (q2 < 0) return fallback;
        return body.substring(q1 + 1, q2);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
