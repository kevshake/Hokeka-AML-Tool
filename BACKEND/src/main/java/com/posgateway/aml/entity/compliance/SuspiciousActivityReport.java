package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.model.SarType;
import jakarta.persistence.*;

import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Suspicious Activity Report (SAR) Entity
 * Represents a formal report to be filed with regulatory authorities
 */
@Entity
@Table(name = "suspicious_activity_reports")
@Audited
public class SuspiciousActivityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String sarReference; // e.g., SAR-2023-0001

    @Column(name = "psp_id")
    private Long pspId; // Added for multi-tenancy filtering

    // Workflow Tracking
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SarStatus status = SarStatus.DRAFT;

    // Approval Workflow
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private User createdBy; // Analyst

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private User reviewedBy; // Compliance Officer

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private User approvedBy; // MLRO

    private LocalDateTime approvedAt;

    // Filing Tracking
    private String filingReferenceNumber; // From regulator
    private LocalDateTime filedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filed_by_user_id")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private User filedBy;

    @Column(columnDefinition = "TEXT")
    private String filingReceipt; // Path to receipt or receipt content

    // Regulatory Requirements
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SarType sarType = SarType.INITIAL;

    @Column(nullable = false)
    private String jurisdiction; // US, UK, EU, etc.

    private LocalDateTime filingDeadline;

    // SAR Content
    @Column(nullable = false)
    private String suspiciousActivityType; // Structuring, Terrorist Financing, etc.

    @Column(columnDefinition = "TEXT", nullable = false)
    private String narrative; // Detailed explanation

    private BigDecimal totalSuspiciousAmount;

    // Related Entities
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private ComplianceCase complianceCase;

    @ManyToMany
    @JoinTable(name = "sar_transactions", joinColumns = @JoinColumn(name = "sar_id"), inverseJoinColumns = @JoinColumn(name = "txn_id"))
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private List<TransactionEntity> suspiciousTransactions;

    // Amendment Tracking
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amends_sar_id")
    private SuspiciousActivityReport amendsSar; // If this SAR corrects a previous one

    private String amendmentReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public SuspiciousActivityReport() {
    }

    public SuspiciousActivityReport(Long id, String sarReference, Long pspId, SarStatus status, User createdBy,
            User reviewedBy, User approvedBy, LocalDateTime approvedAt, String filingReferenceNumber,
            LocalDateTime filedAt, User filedBy, String filingReceipt, SarType sarType, String jurisdiction,
            LocalDateTime filingDeadline, String suspiciousActivityType, String narrative,
            BigDecimal totalSuspiciousAmount, ComplianceCase complianceCase,
            List<TransactionEntity> suspiciousTransactions, SuspiciousActivityReport amendsSar, String amendmentReason,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.sarReference = sarReference;
        this.pspId = pspId;
        this.status = status != null ? status : SarStatus.DRAFT;
        this.createdBy = createdBy;
        this.reviewedBy = reviewedBy;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.filingReferenceNumber = filingReferenceNumber;
        this.filedAt = filedAt;
        this.filedBy = filedBy;
        this.filingReceipt = filingReceipt;
        this.sarType = sarType != null ? sarType : SarType.INITIAL;
        this.jurisdiction = jurisdiction;
        this.filingDeadline = filingDeadline;
        this.suspiciousActivityType = suspiciousActivityType;
        this.narrative = narrative;
        this.totalSuspiciousAmount = totalSuspiciousAmount;
        this.complianceCase = complianceCase;
        this.suspiciousTransactions = suspiciousTransactions;
        this.amendsSar = amendsSar;
        this.amendmentReason = amendmentReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSarReference() {
        return sarReference;
    }

    public void setSarReference(String sarReference) {
        this.sarReference = sarReference;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public SarStatus getStatus() {
        return status;
    }

    public void setStatus(SarStatus status) {
        this.status = status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
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

    public String getFilingReferenceNumber() {
        return filingReferenceNumber;
    }

    public void setFilingReferenceNumber(String filingReferenceNumber) {
        this.filingReferenceNumber = filingReferenceNumber;
    }

    public LocalDateTime getFiledAt() {
        return filedAt;
    }

    public void setFiledAt(LocalDateTime filedAt) {
        this.filedAt = filedAt;
    }

    public User getFiledBy() {
        return filedBy;
    }

    public void setFiledBy(User filedBy) {
        this.filedBy = filedBy;
    }

    public String getFilingReceipt() {
        return filingReceipt;
    }

    public void setFilingReceipt(String filingReceipt) {
        this.filingReceipt = filingReceipt;
    }

    public SarType getSarType() {
        return sarType;
    }

    public void setSarType(SarType sarType) {
        this.sarType = sarType;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public LocalDateTime getFilingDeadline() {
        return filingDeadline;
    }

    public void setFilingDeadline(LocalDateTime filingDeadline) {
        this.filingDeadline = filingDeadline;
    }

    public String getSuspiciousActivityType() {
        return suspiciousActivityType;
    }

    public void setSuspiciousActivityType(String suspiciousActivityType) {
        this.suspiciousActivityType = suspiciousActivityType;
    }

    public String getNarrative() {
        return narrative;
    }

    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    public BigDecimal getTotalSuspiciousAmount() {
        return totalSuspiciousAmount;
    }

    public void setTotalSuspiciousAmount(BigDecimal totalSuspiciousAmount) {
        this.totalSuspiciousAmount = totalSuspiciousAmount;
    }

    public ComplianceCase getComplianceCase() {
        return complianceCase;
    }

    public void setComplianceCase(ComplianceCase complianceCase) {
        this.complianceCase = complianceCase;
    }

    public List<TransactionEntity> getSuspiciousTransactions() {
        return suspiciousTransactions;
    }

    public void setSuspiciousTransactions(List<TransactionEntity> suspiciousTransactions) {
        this.suspiciousTransactions = suspiciousTransactions;
    }

    public SuspiciousActivityReport getAmendsSar() {
        return amendsSar;
    }

    public void setAmendsSar(SuspiciousActivityReport amendsSar) {
        this.amendsSar = amendsSar;
    }

    public String getAmendmentReason() {
        return amendmentReason;
    }

    public void setAmendmentReason(String amendmentReason) {
        this.amendmentReason = amendmentReason;
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

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null)
            status = SarStatus.DRAFT;
        if (sarType == null)
            sarType = SarType.INITIAL;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static SuspiciousActivityReportBuilder builder() {
        return new SuspiciousActivityReportBuilder();
    }

    public static class SuspiciousActivityReportBuilder {
        private Long id;
        private String sarReference;
        private Long pspId;
        private SarStatus status = SarStatus.DRAFT;
        private User createdBy;
        private User reviewedBy;
        private User approvedBy;
        private LocalDateTime approvedAt;
        private String filingReferenceNumber;
        private LocalDateTime filedAt;
        private User filedBy;
        private String filingReceipt;
        private SarType sarType = SarType.INITIAL;
        private String jurisdiction;
        private LocalDateTime filingDeadline;
        private String suspiciousActivityType;
        private String narrative;
        private BigDecimal totalSuspiciousAmount;
        private ComplianceCase complianceCase;
        private List<TransactionEntity> suspiciousTransactions;
        private SuspiciousActivityReport amendsSar;
        private String amendmentReason;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        SuspiciousActivityReportBuilder() {
        }

        public SuspiciousActivityReportBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public SuspiciousActivityReportBuilder sarReference(String sarReference) {
            this.sarReference = sarReference;
            return this;
        }

        public SuspiciousActivityReportBuilder pspId(Long pspId) {
            this.pspId = pspId;
            return this;
        }

        public SuspiciousActivityReportBuilder status(SarStatus status) {
            this.status = status;
            return this;
        }

        public SuspiciousActivityReportBuilder createdBy(User createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public SuspiciousActivityReportBuilder reviewedBy(User reviewedBy) {
            this.reviewedBy = reviewedBy;
            return this;
        }

        public SuspiciousActivityReportBuilder approvedBy(User approvedBy) {
            this.approvedBy = approvedBy;
            return this;
        }

        public SuspiciousActivityReportBuilder approvedAt(LocalDateTime approvedAt) {
            this.approvedAt = approvedAt;
            return this;
        }

        public SuspiciousActivityReportBuilder filingReferenceNumber(String filingReferenceNumber) {
            this.filingReferenceNumber = filingReferenceNumber;
            return this;
        }

        public SuspiciousActivityReportBuilder filedAt(LocalDateTime filedAt) {
            this.filedAt = filedAt;
            return this;
        }

        public SuspiciousActivityReportBuilder filedBy(User filedBy) {
            this.filedBy = filedBy;
            return this;
        }

        public SuspiciousActivityReportBuilder filingReceipt(String filingReceipt) {
            this.filingReceipt = filingReceipt;
            return this;
        }

        public SuspiciousActivityReportBuilder sarType(SarType sarType) {
            this.sarType = sarType;
            return this;
        }

        public SuspiciousActivityReportBuilder jurisdiction(String jurisdiction) {
            this.jurisdiction = jurisdiction;
            return this;
        }

        public SuspiciousActivityReportBuilder filingDeadline(LocalDateTime filingDeadline) {
            this.filingDeadline = filingDeadline;
            return this;
        }

        public SuspiciousActivityReportBuilder suspiciousActivityType(String suspiciousActivityType) {
            this.suspiciousActivityType = suspiciousActivityType;
            return this;
        }

        public SuspiciousActivityReportBuilder narrative(String narrative) {
            this.narrative = narrative;
            return this;
        }

        public SuspiciousActivityReportBuilder totalSuspiciousAmount(BigDecimal totalSuspiciousAmount) {
            this.totalSuspiciousAmount = totalSuspiciousAmount;
            return this;
        }

        public SuspiciousActivityReportBuilder complianceCase(ComplianceCase complianceCase) {
            this.complianceCase = complianceCase;
            return this;
        }

        public SuspiciousActivityReportBuilder suspiciousTransactions(List<TransactionEntity> suspiciousTransactions) {
            this.suspiciousTransactions = suspiciousTransactions;
            return this;
        }

        public SuspiciousActivityReportBuilder amendsSar(SuspiciousActivityReport amendsSar) {
            this.amendsSar = amendsSar;
            return this;
        }

        public SuspiciousActivityReportBuilder amendmentReason(String amendmentReason) {
            this.amendmentReason = amendmentReason;
            return this;
        }

        public SuspiciousActivityReportBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public SuspiciousActivityReportBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public SuspiciousActivityReport build() {
            return new SuspiciousActivityReport(id, sarReference, pspId, status, createdBy, reviewedBy, approvedBy,
                    approvedAt, filingReferenceNumber, filedAt, filedBy, filingReceipt, sarType, jurisdiction,
                    filingDeadline, suspiciousActivityType, narrative, totalSuspiciousAmount, complianceCase,
                    suspiciousTransactions, amendsSar, amendmentReason, createdAt, updatedAt);
        }

        public String toString() {
            return "SuspiciousActivityReport.SuspiciousActivityReportBuilder(id=" + this.id + ", sarReference="
                    + this.sarReference + ", pspId=" + this.pspId + ", status=" + this.status + ", createdBy="
                    + this.createdBy + ", reviewedBy=" + this.reviewedBy + ", approvedBy=" + this.approvedBy
                    + ", approvedAt=" + this.approvedAt + ", filingReferenceNumber=" + this.filingReferenceNumber
                    + ", filedAt=" + this.filedAt + ", filedBy=" + this.filedBy + ", filingReceipt="
                    + this.filingReceipt + ", sarType=" + this.sarType + ", jurisdiction=" + this.jurisdiction
                    + ", filingDeadline=" + this.filingDeadline + ", suspiciousActivityType="
                    + this.suspiciousActivityType + ", narrative=" + this.narrative + ", totalSuspiciousAmount="
                    + this.totalSuspiciousAmount + ", complianceCase=" + this.complianceCase
                    + ", suspiciousTransactions=" + this.suspiciousTransactions + ", amendsSar=" + this.amendsSar
                    + ", amendmentReason=" + this.amendmentReason + ", createdAt=" + this.createdAt + ", updatedAt="
                    + this.updatedAt + ")";
        }
    }
}
