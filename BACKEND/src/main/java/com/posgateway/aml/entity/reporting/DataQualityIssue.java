package com.posgateway.aml.entity.reporting;

import com.posgateway.aml.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Data Quality Issue Entity - Tracks data quality issues for reporting
 */
@Entity
@Table(name = "data_quality_issues", indexes = {
    @Index(name = "idx_dq_issues_type", columnList = "issue_type"),
    @Index(name = "idx_dq_issues_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_dq_issues_psp", columnList = "psp_id"),
    @Index(name = "idx_dq_issues_status", columnList = "status"),
    @Index(name = "idx_dq_issues_severity", columnList = "severity"),
    @Index(name = "idx_dq_issues_created", columnList = "created_at")
})
public class DataQualityIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_type", nullable = false, length = 50)
    private String issueType;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "expected_format", length = 255)
    private String expectedFormat;

    @Column(name = "actual_value", columnDefinition = "TEXT")
    private String actualValue;

    @Column(name = "severity", length = 20)
    private String severity = "WARNING";

    @Column(name = "status", length = 20)
    private String status = "OPEN";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getExpectedFormat() {
        return expectedFormat;
    }

    public void setExpectedFormat(String expectedFormat) {
        this.expectedFormat = expectedFormat;
    }

    public String getActualValue() {
        return actualValue;
    }

    public void setActualValue(String actualValue) {
        this.actualValue = actualValue;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public User getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(User resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Helper methods
    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    public boolean isResolved() {
        return "RESOLVED".equals(status);
    }

    public void resolve(User user, String notes) {
        this.status = "RESOLVED";
        this.resolvedBy = user;
        this.resolvedAt = LocalDateTime.now();
        this.resolutionNotes = notes;
    }
}
