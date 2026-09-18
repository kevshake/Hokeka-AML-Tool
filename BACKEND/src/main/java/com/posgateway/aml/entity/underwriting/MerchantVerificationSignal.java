package com.posgateway.aml.entity.underwriting;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Persisted, append-only merchant-verification signal — the durable evidence record
 * behind an underwriting decision. One row per normalized signal collected during a
 * verification run, so an investigator can later reconstruct exactly which checks fired,
 * from which source, and whether each forced manual review.
 */
@Entity
@Table(name = "merchant_verification_signals",
        indexes = {
                @Index(name = "idx_mvs_merchant", columnList = "merchant_id"),
                @Index(name = "idx_mvs_run", columnList = "run_id")
        })
public class MerchantVerificationSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    /** Correlates all signals produced by a single verification run. */
    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "signal_code", nullable = false, length = 80)
    private String signalCode;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "source", nullable = false, length = 48)
    private String source;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "requires_manual_review")
    private Boolean requiresManualReview = false;

    @Column(name = "evidence_reference", length = 255)
    private String evidenceReference;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "observed_at")
    private LocalDateTime observedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (observedAt == null) observedAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getSignalCode() { return signalCode; }
    public void setSignalCode(String signalCode) { this.signalCode = signalCode; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Boolean getRequiresManualReview() { return requiresManualReview; }
    public void setRequiresManualReview(Boolean requiresManualReview) { this.requiresManualReview = requiresManualReview; }
    public String getEvidenceReference() { return evidenceReference; }
    public void setEvidenceReference(String evidenceReference) { this.evidenceReference = evidenceReference; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime observedAt) { this.observedAt = observedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
