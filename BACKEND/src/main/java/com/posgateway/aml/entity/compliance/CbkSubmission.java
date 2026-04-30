package com.posgateway.aml.entity.compliance;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.Instant;

/**
 * CBK (Central Bank of Kenya) regulatory report submission record.
 *
 * <p>Persisted whenever a PSP submits a CBK regulatory report (CTR, STR,
 * quarterly summary, etc.) via the compliance reporting UI. This row is the
 * audit trail; the actual remote CBK API call is wired in
 * {@code CbkReportService} (currently a TODO stub).
 *
 * <p>PSP isolation is enforced by the {@code pspId} column — every query
 * MUST filter by {@code pspId}.
 */
@Entity
@Table(
        name = "cbk_submissions",
        indexes = {
                @Index(name = "idx_cbk_submissions_psp_period", columnList = "psp_id, period"),
                @Index(name = "idx_cbk_submissions_psp_status", columnList = "psp_id, status"),
                @Index(name = "idx_cbk_submissions_reference", columnList = "reference_number")
        }
)
@Audited
public class CbkSubmission {

    public enum Status {
        DRAFT,
        SUBMITTED,
        ACCEPTED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "report_type", nullable = false, length = 64)
    private String reportType;

    /** e.g. "2026-Q1", "2026-04", "monthly", "daily" — free-form period code. */
    @Column(name = "period", nullable = false, length = 32)
    private String period;

    /** Inclusive start of the reporting window (ISO date as string for FE round-trip). */
    @Column(name = "period_from", length = 32)
    private String periodFrom;

    /** Inclusive end of the reporting window. */
    @Column(name = "period_to", length = 32)
    private String periodTo;

    @Column(name = "reference_number", nullable = false, unique = true, length = 64)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** {@code platform_users.id} — kept loose (no FK) to avoid Envers cascading. */
    @Column(name = "submitted_by")
    private Long submittedBy;

    @Lob
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Lob
    @Column(name = "regulator_response", columnDefinition = "TEXT")
    private String regulatorResponse;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    public CbkSubmission() {
    }

    // ---- Getters / Setters --------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getPeriodFrom() { return periodFrom; }
    public void setPeriodFrom(String periodFrom) { this.periodFrom = periodFrom; }

    public String getPeriodTo() { return periodTo; }
    public void setPeriodTo(String periodTo) { this.periodTo = periodTo; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getRegulatorResponse() { return regulatorResponse; }
    public void setRegulatorResponse(String regulatorResponse) { this.regulatorResponse = regulatorResponse; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
