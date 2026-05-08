package com.posgateway.aml.entity.reporting;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Append-only attempt log for regulator submissions. Every call to
 * {@code RegulatorySubmissionService.dispatchSubmission(...)} writes a row
 * here regardless of outcome (success, regulator-disabled, exception),
 * keyed by an idempotency key so retries can be coalesced.
 *
 * <p>{@code request_body} is truncated above 64KB with a {@code __TRUNCATED__}
 * marker — full bodies live in the regulator-side audit, not in our DB.
 */
@Entity
@Table(name = "regulatory_submission_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uq_rsa_attempt",
                columnNames = {"submission_id", "regulator", "attempt_no"})
})
public class RegulatorySubmissionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "regulator", nullable = false, length = 16)
    private String regulator;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    public RegulatorySubmissionAttempt() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public String getRegulator() { return regulator; }
    public void setRegulator(String regulator) { this.regulator = regulator; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
}
