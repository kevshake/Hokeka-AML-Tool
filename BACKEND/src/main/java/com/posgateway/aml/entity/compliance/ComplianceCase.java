package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import jakarta.persistence.*;
import jakarta.persistence.Index;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Compliance Case Entity
 * Represents an investigation case for suspicious activity
 * 
 * @Audited: Hibernate Envers automatically tracks all changes
 */
@Entity
@Table(name = "compliance_cases", indexes = {
        @Index(name = "idx_case_merchant", columnList = "merchant_id"),
        @Index(name = "idx_case_psp", columnList = "psp_id"),
        @Index(name = "idx_case_status", columnList = "status"),
        @Index(name = "idx_case_created", columnList = "createdAt")
})
@Audited
public class ComplianceCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String caseReference; // e.g., CASE-2023-0001

    @Column(name = "merchant_id")
    private Long merchantId; // optional merchant association for filtering (matches Merchant.merchantId
                             // type)

    @Column(name = "psp_id")
    private Long pspId; // Added for multi-tenancy filtering

    @Column(columnDefinition = "TEXT")
    private String description;

    // NEW: Case workflow
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status = CaseStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CasePriority priority = CasePriority.MEDIUM;

    private LocalDateTime slaDeadline; // NEW: SLA tracking

    private Integer daysOpen; // NEW: Case aging

    // NEW: Assignment tracking
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    @Audited(targetAuditMode = org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED)
    private User assignedTo;

    @Column(name = "assigned_by_user_id")
    private Long assignedBy;

    private LocalDateTime assignedAt;

    // NEW: Escalation tracking
    private Boolean escalated = false;

    @Column(name = "escalated_to_user_id")
    private Long escalatedTo;

    private String escalationReason;

    private LocalDateTime escalatedAt;

    // NEW: Case queue
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id")
    private CaseQueue queue;

    // NEW: Case relationships
    @ManyToMany
    @JoinTable(name = "case_relationships", joinColumns = @JoinColumn(name = "case_id"), inverseJoinColumns = @JoinColumn(name = "related_case_id"))
    @Audited
    private Set<ComplianceCase> relatedCases;

    // NEW: Evidence and documentation
    @OneToMany(mappedBy = "complianceCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @Audited(targetAuditMode = org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED)
    private List<CaseEvidence> evidence;

    @OneToMany(mappedBy = "complianceCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseNote> notes;

    // NEW: Decision tracking
    private String resolution; // CLEARED, SAR_FILED, BLOCKED, etc.

    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_by", columnDefinition = "bigint")
    private Long resolvedBy;

    private LocalDateTime resolvedAt;

    // NEW: Alerts triggering this case
    @OneToMany(mappedBy = "complianceCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseAlert> alerts;

    // NEW: Decisions made on this case
    @OneToMany(mappedBy = "complianceCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CaseDecision> decisions;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // NEW: Retention & Archival
    @Column(nullable = false)
    private Boolean archived = false;

    private LocalDateTime archivedAt;

    @Column(name = "archive_reference")
    private String archiveReference; // Pointer to cold storage (e.g. S3 path/ARN)

    public ComplianceCase() {
    }

    public ComplianceCase(Long id, String caseReference, Long merchantId, Long pspId, String description,
            CaseStatus status, CasePriority priority, LocalDateTime slaDeadline, Integer daysOpen, User assignedTo,
            Long assignedBy, LocalDateTime assignedAt, Boolean escalated, Long escalatedTo, String escalationReason,
            LocalDateTime escalatedAt, Set<ComplianceCase> relatedCases, List<CaseEvidence> evidence,
            List<CaseNote> notes, String resolution, String resolutionNotes, Long resolvedBy, LocalDateTime resolvedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.caseReference = caseReference;
        this.merchantId = merchantId;
        this.pspId = pspId;
        this.description = description;
        this.status = status != null ? status : CaseStatus.NEW;
        this.priority = priority != null ? priority : CasePriority.MEDIUM;
        this.slaDeadline = slaDeadline;
        this.daysOpen = daysOpen;
        this.assignedTo = assignedTo;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
        this.escalated = escalated != null ? escalated : false;
        this.escalatedTo = escalatedTo;
        this.escalationReason = escalationReason;
        this.escalatedAt = escalatedAt;
        this.relatedCases = relatedCases;
        this.evidence = evidence;
        this.notes = notes;
        this.resolution = resolution;
        this.resolutionNotes = resolutionNotes;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCaseReference() {
        return caseReference;
    }

    public void setCaseReference(String caseReference) {
        this.caseReference = caseReference;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public CasePriority getPriority() {
        return priority;
    }

    public void setPriority(CasePriority priority) {
        this.priority = priority;
    }

    public LocalDateTime getSlaDeadline() {
        return slaDeadline;
    }

    public void setSlaDeadline(LocalDateTime slaDeadline) {
        this.slaDeadline = slaDeadline;
    }

    public Integer getDaysOpen() {
        return daysOpen;
    }

    public void setDaysOpen(Integer daysOpen) {
        this.daysOpen = daysOpen;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Long assignedBy) {
        this.assignedBy = assignedBy;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Boolean getEscalated() {
        return escalated;
    }

    public void setEscalated(Boolean escalated) {
        this.escalated = escalated;
    }

    public Long getEscalatedTo() {
        return escalatedTo;
    }

    public void setEscalatedTo(Long escalatedTo) {
        this.escalatedTo = escalatedTo;
    }

    public String getEscalationReason() {
        return escalationReason;
    }

    public void setEscalationReason(String escalationReason) {
        this.escalationReason = escalationReason;
    }

    public LocalDateTime getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(LocalDateTime escalatedAt) {
        this.escalatedAt = escalatedAt;
    }

    public CaseQueue getQueue() {
        return queue;
    }

    public void setQueue(CaseQueue queue) {
        this.queue = queue;
    }

    public Set<ComplianceCase> getRelatedCases() {
        return relatedCases;
    }

    public void setRelatedCases(Set<ComplianceCase> relatedCases) {
        this.relatedCases = relatedCases;
    }

    public List<CaseEvidence> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<CaseEvidence> evidence) {
        this.evidence = evidence;
    }

    public List<CaseNote> getNotes() {
        return notes;
    }

    public void setNotes(List<CaseNote> notes) {
        this.notes = notes;
    }

    public List<CaseAlert> getAlerts() {
        return alerts;
    }

    public void setAlerts(List<CaseAlert> alerts) {
        this.alerts = alerts;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public Long getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(Long resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
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

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public String getArchiveReference() {
        return archiveReference;
    }

    public void setArchiveReference(String archiveReference) {
        this.archiveReference = archiveReference;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null)
            status = CaseStatus.NEW;
        if (priority == null)
            priority = CasePriority.MEDIUM;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static ComplianceCaseBuilder builder() {
        return new ComplianceCaseBuilder();
    }

    public static class ComplianceCaseBuilder {
        private Long id;
        private String caseReference;
        private Long merchantId;
        private Long pspId;
        private String description;
        private CaseStatus status = CaseStatus.NEW;
        private CasePriority priority = CasePriority.MEDIUM;
        private LocalDateTime slaDeadline;
        private Integer daysOpen;
        private User assignedTo;
        private Long assignedBy;
        private LocalDateTime assignedAt;
        private Boolean escalated = false;
        private Long escalatedTo;
        private String escalationReason;
        private LocalDateTime escalatedAt;
        private Set<ComplianceCase> relatedCases;
        private List<CaseEvidence> evidence;
        private List<CaseNote> notes;
        private String resolution;
        private String resolutionNotes;
        private Long resolvedBy;
        private LocalDateTime resolvedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        ComplianceCaseBuilder() {
        }

        public ComplianceCaseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ComplianceCaseBuilder caseReference(String caseReference) {
            this.caseReference = caseReference;
            return this;
        }

        public ComplianceCaseBuilder merchantId(Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public ComplianceCaseBuilder pspId(Long pspId) {
            this.pspId = pspId;
            return this;
        }

        public ComplianceCaseBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ComplianceCaseBuilder status(CaseStatus status) {
            this.status = status;
            return this;
        }

        public ComplianceCaseBuilder priority(CasePriority priority) {
            this.priority = priority;
            return this;
        }

        public ComplianceCaseBuilder slaDeadline(LocalDateTime slaDeadline) {
            this.slaDeadline = slaDeadline;
            return this;
        }

        public ComplianceCaseBuilder daysOpen(Integer daysOpen) {
            this.daysOpen = daysOpen;
            return this;
        }

        public ComplianceCaseBuilder assignedTo(User assignedTo) {
            this.assignedTo = assignedTo;
            return this;
        }

        public ComplianceCaseBuilder assignedBy(Long assignedBy) {
            this.assignedBy = assignedBy;
            return this;
        }

        public ComplianceCaseBuilder assignedAt(LocalDateTime assignedAt) {
            this.assignedAt = assignedAt;
            return this;
        }

        public ComplianceCaseBuilder escalated(Boolean escalated) {
            this.escalated = escalated;
            return this;
        }

        public ComplianceCaseBuilder escalatedTo(Long escalatedTo) {
            this.escalatedTo = escalatedTo;
            return this;
        }

        public ComplianceCaseBuilder escalationReason(String escalationReason) {
            this.escalationReason = escalationReason;
            return this;
        }

        public ComplianceCaseBuilder escalatedAt(LocalDateTime escalatedAt) {
            this.escalatedAt = escalatedAt;
            return this;
        }

        public ComplianceCaseBuilder relatedCases(Set<ComplianceCase> relatedCases) {
            this.relatedCases = relatedCases;
            return this;
        }

        public ComplianceCaseBuilder evidence(List<CaseEvidence> evidence) {
            this.evidence = evidence;
            return this;
        }

        public ComplianceCaseBuilder notes(List<CaseNote> notes) {
            this.notes = notes;
            return this;
        }

        public ComplianceCaseBuilder resolution(String resolution) {
            this.resolution = resolution;
            return this;
        }

        public ComplianceCaseBuilder resolutionNotes(String resolutionNotes) {
            this.resolutionNotes = resolutionNotes;
            return this;
        }

        public ComplianceCaseBuilder resolvedBy(Long resolvedBy) {
            this.resolvedBy = resolvedBy;
            return this;
        }

        public ComplianceCaseBuilder resolvedAt(LocalDateTime resolvedAt) {
            this.resolvedAt = resolvedAt;
            return this;
        }

        public ComplianceCaseBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ComplianceCaseBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ComplianceCase build() {
            return new ComplianceCase(id, caseReference, merchantId, pspId, description, status, priority, slaDeadline,
                    daysOpen, assignedTo, assignedBy, assignedAt, escalated, escalatedTo, escalationReason, escalatedAt,
                    relatedCases, evidence, notes, resolution, resolutionNotes, resolvedBy, resolvedAt, createdAt,
                    updatedAt);
        }

        public String toString() {
            return "ComplianceCase.ComplianceCaseBuilder(id=" + this.id + ", caseReference=" + this.caseReference
                    + ", merchantId=" + this.merchantId + ", pspId=" + this.pspId + ", description=" + this.description
                    + ", status=" + this.status + ", priority=" + this.priority + ", slaDeadline=" + this.slaDeadline
                    + ", daysOpen=" + this.daysOpen + ", assignedTo=" + this.assignedTo + ", assignedBy="
                    + this.assignedBy + ", assignedAt=" + this.assignedAt + ", escalated=" + this.escalated
                    + ", escalatedTo=" + this.escalatedTo + ", escalationReason=" + this.escalationReason
                    + ", escalatedAt=" + this.escalatedAt + ", relatedCases=" + this.relatedCases + ", evidence="
                    + this.evidence + ", notes=" + this.notes + ", resolution=" + this.resolution + ", resolutionNotes="
                    + this.resolutionNotes + ", resolvedBy=" + this.resolvedBy + ", resolvedAt=" + this.resolvedAt
                    + ", createdAt=" + this.createdAt + ", updatedAt=" + this.updatedAt + ")";
        }
    }
}
