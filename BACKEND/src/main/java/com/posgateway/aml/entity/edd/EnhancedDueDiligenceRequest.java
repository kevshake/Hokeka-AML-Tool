package com.posgateway.aml.entity.edd;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Enhanced Due Diligence (EDD) request tracking.
 *
 * <p>One row per merchant currently undergoing EDD. The set of boolean fields
 * mirrors the old in-memory {@code EddStatus} structure that lived inside
 * {@link com.posgateway.aml.service.edd.EnhancedDueDiligenceService} —
 * preserved here so existing callers keep working without DTO churn.
 */
@Entity
@Table(name = "edd_requests", indexes = {
        @Index(name = "idx_edd_requests_merchant", columnList = "merchant_id"),
        @Index(name = "idx_edd_requests_status",   columnList = "status")
})
public class EnhancedDueDiligenceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Soft FK to merchants(merchant_id). One active EDD row per merchant. */
    @Column(name = "merchant_id", nullable = false, unique = true)
    private Long merchantId;

    /** Lifecycle status: IN_PROGRESS / COMPLETED. */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "IN_PROGRESS";

    @Column(name = "source_of_funds_verified",   nullable = false)
    private boolean sourceOfFundsVerified;

    @Column(name = "source_of_wealth_verified",  nullable = false)
    private boolean sourceOfWealthVerified;

    @Column(name = "site_visit_completed",       nullable = false)
    private boolean siteVisitCompleted;

    // Kenyan-specific (Phase 29)
    @Column(name = "senior_management_approval", nullable = false)
    private boolean seniorManagementApproval;

    @Column(name = "family_associate_checks",    nullable = false)
    private boolean familyAssociateChecks;

    @Column(name = "transaction_purpose_review", nullable = false)
    private boolean transactionPurposeReview;

    @Column(name = "initiated_at", nullable = false)
    private LocalDateTime initiatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public EnhancedDueDiligenceRequest() {
    }

    public EnhancedDueDiligenceRequest(Long merchantId) {
        this.merchantId = merchantId;
        this.initiatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isSourceOfFundsVerified() { return sourceOfFundsVerified; }
    public void setSourceOfFundsVerified(boolean v) { this.sourceOfFundsVerified = v; }

    public boolean isSourceOfWealthVerified() { return sourceOfWealthVerified; }
    public void setSourceOfWealthVerified(boolean v) { this.sourceOfWealthVerified = v; }

    public boolean isSiteVisitCompleted() { return siteVisitCompleted; }
    public void setSiteVisitCompleted(boolean v) { this.siteVisitCompleted = v; }

    public boolean isSeniorManagementApproval() { return seniorManagementApproval; }
    public void setSeniorManagementApproval(boolean v) { this.seniorManagementApproval = v; }

    public boolean isFamilyAssociateChecks() { return familyAssociateChecks; }
    public void setFamilyAssociateChecks(boolean v) { this.familyAssociateChecks = v; }

    public boolean isTransactionPurposeReview() { return transactionPurposeReview; }
    public void setTransactionPurposeReview(boolean v) { this.transactionPurposeReview = v; }

    public LocalDateTime getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(LocalDateTime v) { this.initiatedAt = v; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime v) { this.completedAt = v; }
}
