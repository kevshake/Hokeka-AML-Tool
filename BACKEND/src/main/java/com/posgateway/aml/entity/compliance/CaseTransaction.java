package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.model.TransactionReference;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Case Transaction Entity: a transaction linked to a case for the timeline and network views,
 * referenced by ID only (V264, residency contract I4). The transaction's details stay on the PSP's
 * Edge; {@code transaction_id} is the legacy cloud id (no foreign key) kept until R12 / R15.
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

    /** Legacy cloud transactions.txn_id; null for Edge decision records. */
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "edge_assessment_id")
    private UUID edgeAssessmentId;

    @Column(name = "txn_ref", length = 128)
    private String txnRef;

    @Column(name = "site_id", length = 64)
    private String siteId;

    @Column(name = "relationship_type")
    private String relationshipType; // PRIMARY, RELATED, SUSPICIOUS_PATTERN

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private User addedBy;

    /** JPA constructor. */
    public CaseTransaction() {
    }

    /** A link of {@code reference} to {@code complianceCase}. */
    public CaseTransaction(ComplianceCase complianceCase, TransactionReference reference,
                           String relationshipType, User addedBy) {
        this.complianceCase = complianceCase;
        applyTransactionReference(reference);
        this.relationshipType = relationshipType;
        this.addedBy = addedBy;
    }

    /** Row id. */
    public Long getId() {
        return id;
    }

    /** Sets the row id. */
    public void setId(Long id) {
        this.id = id;
    }

    /** The case. */
    public ComplianceCase getComplianceCase() {
        return complianceCase;
    }

    /** Sets the case. */
    public void setComplianceCase(ComplianceCase complianceCase) {
        this.complianceCase = complianceCase;
    }

    /** Legacy cloud transaction id, if any. */
    public Long getTransactionId() {
        return transactionId;
    }

    /** Sets the legacy cloud transaction id. */
    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    /** Decision record id of the linked transaction. */
    public UUID getEdgeAssessmentId() {
        return edgeAssessmentId;
    }

    /** Sets the decision record id. */
    public void setEdgeAssessmentId(UUID edgeAssessmentId) {
        this.edgeAssessmentId = edgeAssessmentId;
    }

    /** The PSP's transaction id. */
    public String getTxnRef() {
        return txnRef;
    }

    /** Sets the PSP's transaction id; a card-shaped value is dropped. */
    public void setTxnRef(String txnRef) {
        this.txnRef = TransactionReference.safeTxnRef(txnRef);
    }

    /** Edge Site of the linked transaction. */
    public String getSiteId() {
        return siteId;
    }

    /** Sets the Edge Site. */
    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    /** PRIMARY, RELATED, SUSPICIOUS_PATTERN, TRIGGERING_TRANSACTION. */
    public String getRelationshipType() {
        return relationshipType;
    }

    /** Sets the relationship type. */
    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    /** When the transaction was linked. */
    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    /** Sets the link time. */
    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }

    /** The user who linked it (null = system). */
    public User getAddedBy() {
        return addedBy;
    }

    /** Sets the linking user. */
    public void setAddedBy(User addedBy) {
        this.addedBy = addedBy;
    }

    /** The linked transaction as an ID-only reference. */
    public TransactionReference getTransactionReference() {
        return new TransactionReference(transactionId, edgeAssessmentId, txnRef, siteId);
    }

    /** Copies an ID-only reference onto the row. */
    public void applyTransactionReference(TransactionReference reference) {
        if (reference == null) {
            return;
        }
        this.transactionId = reference.legacyTransactionId();
        this.edgeAssessmentId = reference.edgeAssessmentId();
        setTxnRef(reference.txnRef());
        this.siteId = reference.siteId();
    }

    /** Stamps the link time. */
    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }
}
