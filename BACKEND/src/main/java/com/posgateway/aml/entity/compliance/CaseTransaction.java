package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.User;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDateTime;

/**
 * Case Transaction Entity
 * Links transactions to cases for timeline view
 */
@Entity
@Table(name = "case_transactions")
@Audited
public class CaseTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ComplianceCase complianceCase;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;

    @Column(name = "relationship_type")
    private String relationshipType; // PRIMARY, RELATED, SUSPICIOUS_PATTERN

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private User addedBy;

    public CaseTransaction() {
    }

    public CaseTransaction(Long id, ComplianceCase complianceCase, TransactionEntity transaction,
            String relationshipType, LocalDateTime addedAt, User addedBy) {
        this.id = id;
        this.complianceCase = complianceCase;
        this.transaction = transaction;
        this.relationshipType = relationshipType;
        this.addedAt = addedAt;
        this.addedBy = addedBy;
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

    public TransactionEntity getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionEntity transaction) {
        this.transaction = transaction;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }

    public User getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(User addedBy) {
        this.addedBy = addedBy;
    }

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }
}
