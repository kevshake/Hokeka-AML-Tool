package com.posgateway.aml.entity.residency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One maker-checker change request for a PSP data residency setting (table
 * {@code psp_data_setting_changes}, V261).
 *
 * <p>Lifecycle: {@code PENDING_APPROVAL} → {@code REJECTED}, or → {@code APPLIED} (approved and in
 * force at once), or → {@code APPROVED} (an approved retention reduction waiting for its
 * {@code effectiveAt}, at least 30 days after approval) → {@code APPLIED}. A newer approved
 * retention change turns a waiting reduction into {@code SUPERSEDED}.
 */
@Entity
@Table(name = "psp_data_setting_changes")
public class PspDataSettingChange {

    /** The settings that go through maker-checker. */
    public enum Setting { RETENTION_YEARS, SUPPORT_ACCESS, CROSS_PSP_OPT_IN }

    /** Change request states (DB CHECK in V261). */
    public enum Status { PENDING_APPROVAL, APPROVED, APPLIED, REJECTED, SUPERSEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Enumerated(EnumType.STRING)
    @Column(name = "setting", nullable = false, length = 32)
    private Setting setting;

    @Column(name = "old_value", nullable = false, length = 16)
    private String oldValue;

    @Column(name = "new_value", nullable = false, length = 16)
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private Status status;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "requested_by_username", nullable = false, length = 255)
    private String requestedByUsername;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_by_username", length = 255)
    private String reviewedByUsername;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    /** When an approved change takes effect (approval time, or approval + 30 days for a reduction). */
    @Column(name = "effective_at")
    private Instant effectiveAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    /** Row id. */
    public Long getId() { return id; }

    /** Sets the row id (tests only; the database assigns it). */
    public void setId(Long id) { this.id = id; }

    /** Owning PSP. */
    public Long getPspId() { return pspId; }

    /** Sets the owning PSP. */
    public void setPspId(Long pspId) { this.pspId = pspId; }

    /** Which setting this request changes. */
    public Setting getSetting() { return setting; }

    /** Sets which setting this request changes. */
    public void setSetting(Setting setting) { this.setting = setting; }

    /** Value in force when the request was made. */
    public String getOldValue() { return oldValue; }

    /** Sets the value in force when the request was made. */
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    /** Requested value. */
    public String getNewValue() { return newValue; }

    /** Sets the requested value. */
    public void setNewValue(String newValue) { this.newValue = newValue; }

    /** Current lifecycle state. */
    public Status getStatus() { return status; }

    /** Moves the request to another lifecycle state. */
    public void setStatus(Status status) { this.status = status; }

    /** Maker's justification. */
    public String getReason() { return reason; }

    /** Sets the maker's justification. */
    public void setReason(String reason) { this.reason = reason; }

    /** Maker's user id (null for a principal without a database id). */
    public Long getRequestedBy() { return requestedBy; }

    /** Sets the maker's user id. */
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }

    /** Maker's username; the maker-checker distinctness check uses it. */
    public String getRequestedByUsername() { return requestedByUsername; }

    /** Sets the maker's username. */
    public void setRequestedByUsername(String requestedByUsername) { this.requestedByUsername = requestedByUsername; }

    /** When the request was made. */
    public Instant getRequestedAt() { return requestedAt; }

    /** Sets when the request was made. */
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    /** Checker's user id. */
    public Long getReviewedBy() { return reviewedBy; }

    /** Sets the checker's user id. */
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }

    /** Checker's username. */
    public String getReviewedByUsername() { return reviewedByUsername; }

    /** Sets the checker's username. */
    public void setReviewedByUsername(String reviewedByUsername) { this.reviewedByUsername = reviewedByUsername; }

    /** When the checker decided. */
    public Instant getReviewedAt() { return reviewedAt; }

    /** Sets when the checker decided. */
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    /** Checker's justification. */
    public String getReviewReason() { return reviewReason; }

    /** Sets the checker's justification. */
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }

    /** When the approved value takes effect. */
    public Instant getEffectiveAt() { return effectiveAt; }

    /** Sets when the approved value takes effect. */
    public void setEffectiveAt(Instant effectiveAt) { this.effectiveAt = effectiveAt; }

    /** When the value was written to the PSP row. */
    public Instant getAppliedAt() { return appliedAt; }

    /** Sets when the value was written to the PSP row. */
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
}
