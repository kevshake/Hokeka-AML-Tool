package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.model.ActivityType;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDateTime;

/**
 * Case Activity Entity
 * Represents an activity/event in a compliance case lifecycle
 */
@Entity
@Table(name = "case_activities")
@Audited
public class CaseActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ComplianceCase complianceCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String details; // JSON for structured data

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", nullable = false)
    private User performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    // Related entities
    @Column(name = "related_entity_id")
    private Long relatedEntityId; // Note ID, Evidence ID, etc.

    @Column(name = "related_entity_type")
    private String relatedEntityType;

    public CaseActivity() {
    }

    public CaseActivity(Long id, ComplianceCase complianceCase, ActivityType activityType, String description,
            String details, User performedBy, LocalDateTime performedAt, Long relatedEntityId,
            String relatedEntityType) {
        this.id = id;
        this.complianceCase = complianceCase;
        this.activityType = activityType;
        this.description = description;
        this.details = details;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
        this.relatedEntityId = relatedEntityId;
        this.relatedEntityType = relatedEntityType;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ComplianceCase getComplianceCase() {
        return complianceCase;
    }

    public void setComplianceCase(ComplianceCase complianceCase) {
        this.complianceCase = complianceCase;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(User performedBy) {
        this.performedBy = performedBy;
    }

    public LocalDateTime getPerformedAt() {
        return performedAt;
    }

    public void setPerformedAt(LocalDateTime performedAt) {
        this.performedAt = performedAt;
    }

    public Long getRelatedEntityId() {
        return relatedEntityId;
    }

    public void setRelatedEntityId(Long relatedEntityId) {
        this.relatedEntityId = relatedEntityId;
    }

    public String getRelatedEntityType() {
        return relatedEntityType;
    }

    public void setRelatedEntityType(String relatedEntityType) {
        this.relatedEntityType = relatedEntityType;
    }

    @PrePersist
    protected void onCreate() {
        if (performedAt == null) {
            performedAt = LocalDateTime.now();
        }
    }

    public static CaseActivityBuilder builder() {
        return new CaseActivityBuilder();
    }

    public static class CaseActivityBuilder {
        private Long id;
        private ComplianceCase complianceCase;
        private ActivityType activityType;
        private String description;
        private String details;
        private User performedBy;
        private LocalDateTime performedAt;
        private Long relatedEntityId;
        private String relatedEntityType;

        CaseActivityBuilder() {
        }

        public CaseActivityBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CaseActivityBuilder complianceCase(ComplianceCase complianceCase) {
            this.complianceCase = complianceCase;
            return this;
        }

        public CaseActivityBuilder activityType(ActivityType activityType) {
            this.activityType = activityType;
            return this;
        }

        public CaseActivityBuilder description(String description) {
            this.description = description;
            return this;
        }

        public CaseActivityBuilder details(String details) {
            this.details = details;
            return this;
        }

        public CaseActivityBuilder performedBy(User performedBy) {
            this.performedBy = performedBy;
            return this;
        }

        public CaseActivityBuilder performedAt(LocalDateTime performedAt) {
            this.performedAt = performedAt;
            return this;
        }

        public CaseActivityBuilder relatedEntityId(Long relatedEntityId) {
            this.relatedEntityId = relatedEntityId;
            return this;
        }

        public CaseActivityBuilder relatedEntityType(String relatedEntityType) {
            this.relatedEntityType = relatedEntityType;
            return this;
        }

        public CaseActivity build() {
            return new CaseActivity(id, complianceCase, activityType, description, details, performedBy, performedAt,
                    relatedEntityId, relatedEntityType);
        }
    }
}
