package com.posgateway.aml.entity.reporting;

import com.posgateway.aml.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Regulatory Submission Entity - Tracks regulatory filing submissions
 */
@Entity
@Table(name = "regulatory_submissions", indexes = {
    @Index(name = "idx_reg_sub_psp", columnList = "psp_id"),
    @Index(name = "idx_reg_sub_status", columnList = "status"),
    @Index(name = "idx_reg_sub_regulator", columnList = "regulator_code"),
    @Index(name = "idx_reg_sub_type", columnList = "submission_type"),
    @Index(name = "idx_reg_sub_period", columnList = "filing_period_start, filing_period_end"),
    @Index(name = "idx_reg_sub_deadline", columnList = "filing_deadline")
})
public class RegulatorySubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_reference", nullable = false, unique = true, length = 100)
    private String submissionReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "regulator_code", nullable = false, length = 50)
    private String regulatorCode;

    @Column(name = "submission_type", nullable = false, length = 50)
    private String submissionType;

    @Column(name = "jurisdiction", nullable = false, length = 50)
    private String jurisdiction;

    @Column(name = "filing_period_start", nullable = false)
    private LocalDate filingPeriodStart;

    @Column(name = "filing_period_end", nullable = false)
    private LocalDate filingPeriodEnd;

    @Column(name = "filing_deadline")
    private LocalDate filingDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prepared_by")
    private User preparedBy;

    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filed_by")
    private User filedBy;

    @Column(name = "filed_at")
    private LocalDateTime filedAt;

    @Column(name = "regulator_reference", length = 100)
    private String regulatorReference;

    @Column(name = "filing_receipt", columnDefinition = "TEXT")
    private String filingReceipt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amended_submission_id")
    private RegulatorySubmission amendedSubmission;

    @Column(name = "amendment_reason", columnDefinition = "TEXT")
    private String amendmentReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_data", columnDefinition = "jsonb")
    private Map<String, Object> submittedData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_paths", columnDefinition = "jsonb")
    private Map<String, String> attachmentPaths;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSubmissionReference() {
        return submissionReference;
    }

    public void setSubmissionReference(String submissionReference) {
        this.submissionReference = submissionReference;
    }

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public String getRegulatorCode() {
        return regulatorCode;
    }

    public void setRegulatorCode(String regulatorCode) {
        this.regulatorCode = regulatorCode;
    }

    public String getSubmissionType() {
        return submissionType;
    }

    public void setSubmissionType(String submissionType) {
        this.submissionType = submissionType;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public LocalDate getFilingPeriodStart() {
        return filingPeriodStart;
    }

    public void setFilingPeriodStart(LocalDate filingPeriodStart) {
        this.filingPeriodStart = filingPeriodStart;
    }

    public LocalDate getFilingPeriodEnd() {
        return filingPeriodEnd;
    }

    public void setFilingPeriodEnd(LocalDate filingPeriodEnd) {
        this.filingPeriodEnd = filingPeriodEnd;
    }

    public LocalDate getFilingDeadline() {
        return filingDeadline;
    }

    public void setFilingDeadline(LocalDate filingDeadline) {
        this.filingDeadline = filingDeadline;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    public User getPreparedBy() {
        return preparedBy;
    }

    public void setPreparedBy(User preparedBy) {
        this.preparedBy = preparedBy;
    }

    public LocalDateTime getPreparedAt() {
        return preparedAt;
    }

    public void setPreparedAt(LocalDateTime preparedAt) {
        this.preparedAt = preparedAt;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(User approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public User getFiledBy() {
        return filedBy;
    }

    public void setFiledBy(User filedBy) {
        this.filedBy = filedBy;
    }

    public LocalDateTime getFiledAt() {
        return filedAt;
    }

    public void setFiledAt(LocalDateTime filedAt) {
        this.filedAt = filedAt;
    }

    public String getRegulatorReference() {
        return regulatorReference;
    }

    public void setRegulatorReference(String regulatorReference) {
        this.regulatorReference = regulatorReference;
    }

    public String getFilingReceipt() {
        return filingReceipt;
    }

    public void setFilingReceipt(String filingReceipt) {
        this.filingReceipt = filingReceipt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public RegulatorySubmission getAmendedSubmission() {
        return amendedSubmission;
    }

    public void setAmendedSubmission(RegulatorySubmission amendedSubmission) {
        this.amendedSubmission = amendedSubmission;
    }

    public String getAmendmentReason() {
        return amendmentReason;
    }

    public void setAmendmentReason(String amendmentReason) {
        this.amendmentReason = amendmentReason;
    }

    public Map<String, Object> getSubmittedData() {
        return submittedData;
    }

    public void setSubmittedData(Map<String, Object> submittedData) {
        this.submittedData = submittedData;
    }

    public Map<String, String> getAttachmentPaths() {
        return attachmentPaths;
    }

    public void setAttachmentPaths(Map<String, String> attachmentPaths) {
        this.attachmentPaths = attachmentPaths;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods
    public boolean isLateFiling() {
        if (filingDeadline == null || filedAt == null) {
            return false;
        }
        return filedAt.toLocalDate().isAfter(filingDeadline);
    }

    public Long getDaysUntilDeadline() {
        if (filingDeadline == null) {
            return null;
        }
        LocalDate now = LocalDate.now();
        if (filedAt != null) {
            return java.time.temporal.ChronoUnit.DAYS.between(now, filingDeadline);
        }
        return java.time.temporal.ChronoUnit.DAYS.between(now, filingDeadline);
    }

    public boolean canEdit() {
        return status.canEdit();
    }

    public boolean canFile() {
        return status.canFile();
    }
}
