package com.posgateway.aml.entity.rules;

import com.posgateway.aml.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "rule_versions", uniqueConstraints =
        @UniqueConstraint(name = "uq_rule_version_number", columnNames = {"rule_id", "version_number"}))
public class RuleVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private RuleDefinition rule;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private RuleLifecycleStatus lifecycleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 24)
    private RuleChangeType changeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshot = new LinkedHashMap<>();

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "change_summary", nullable = false, columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "psp_id")
    private Long pspId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_version_id")
    private RuleVersion supersedesVersion;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (submittedAt == null) submittedAt = now;
    }

    public Long getId() { return id; }
    public RuleDefinition getRule() { return rule; }
    public void setRule(RuleDefinition rule) { this.rule = rule; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public RuleLifecycleStatus getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(RuleLifecycleStatus lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public RuleChangeType getChangeType() { return changeType; }
    public void setChangeType(RuleChangeType changeType) { this.changeType = changeType; }
    public Map<String, Object> getSnapshot() { return snapshot; }
    public void setSnapshot(Map<String, Object> snapshot) { this.snapshot = snapshot; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }
    public RuleVersion getSupersedesVersion() { return supersedesVersion; }
    public void setSupersedesVersion(RuleVersion supersedesVersion) { this.supersedesVersion = supersedesVersion; }
}
