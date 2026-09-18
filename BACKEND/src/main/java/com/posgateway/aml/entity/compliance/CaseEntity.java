package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.entity.User;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDateTime;

/**
 * Case Generic Entity Link
 * Links non-transaction entities (Customer, Merchant, Device, IP, etc.) to a
 * case.
 */
@Entity
@Table(name = "case_entities")
@Audited
public class CaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ComplianceCase complianceCase;

    @Column(name = "entity_type", nullable = false)
    private String entityType; // MERCHANT, CUSTOMER, DEVICE_ID, IP_ADDRESS, CARD_HASH

    @Column(name = "entity_reference", nullable = false)
    private String entityReference; // The ID value (e.g. merchantId, customerId string, IP string)

    @Column(columnDefinition = "TEXT")
    private String description; // "Merchant Profile", "User Device"

    @Column(name = "linked_at", nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_by")
    private User linkedBy; // Null for system

    public CaseEntity() {
    }

    public CaseEntity(ComplianceCase complianceCase, String entityType, String entityReference, String description,
            User linkedBy) {
        this.complianceCase = complianceCase;
        this.entityType = entityType;
        this.entityReference = entityReference;
        this.description = description;
        this.linkedBy = linkedBy;
        this.linkedAt = LocalDateTime.now();
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

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityReference() {
        return entityReference;
    }

    public void setEntityReference(String entityReference) {
        this.entityReference = entityReference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(LocalDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }

    public User getLinkedBy() {
        return linkedBy;
    }

    public void setLinkedBy(User linkedBy) {
        this.linkedBy = linkedBy;
    }
}
