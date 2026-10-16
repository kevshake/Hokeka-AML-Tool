package com.posgateway.aml.entity.compliance;

import com.posgateway.aml.model.TransactionReference;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Join row linking a canonical {@code alerts} record to one or more transactions, by ID only (V264):
 * the decision record id / txnRef / site, plus the legacy cloud txn id for legacy-ingest rows (no
 * foreign key). Supports consolidated alerts that accumulate multiple txn hits under one investigation.
 */
@Entity
@Table(name = "alert_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_alert_transactions_pair", columnNames = {"alert_id", "txn_id"})
})
public class AlertTransactionLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    /** Legacy cloud transactions.txn_id; null for Edge decision records. */
    @Column(name = "txn_id")
    private Long txnId;

    @Column(name = "edge_assessment_id")
    private UUID edgeAssessmentId;

    @Column(name = "txn_ref", length = 128)
    private String txnRef;

    @Column(name = "site_id", length = 64)
    private String siteId;

    @Column(name = "primary_link", nullable = false)
    private boolean primaryLink;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    /** Stamps the link time. */
    @PrePersist
    void onCreate() {
        if (linkedAt == null) {
            linkedAt = LocalDateTime.now();
        }
    }

    /** Row id. */
    public Long getId() {
        return id;
    }

    /** Sets the row id. */
    public void setId(Long id) {
        this.id = id;
    }

    /** The linked alert. */
    public Long getAlertId() {
        return alertId;
    }

    /** Sets the linked alert. */
    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    /** Legacy cloud transaction id, if any. */
    public Long getTxnId() {
        return txnId;
    }

    /** Sets the legacy cloud transaction id. */
    public void setTxnId(Long txnId) {
        this.txnId = txnId;
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

    /** True for the alert's triggering transaction. */
    public boolean isPrimaryLink() {
        return primaryLink;
    }

    /** Marks the triggering transaction. */
    public void setPrimaryLink(boolean primaryLink) {
        this.primaryLink = primaryLink;
    }

    /** When the link was made. */
    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }

    /** Sets the link time. */
    public void setLinkedAt(LocalDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }

    /** The linked transaction as an ID-only reference. */
    public TransactionReference getTransactionReference() {
        return new TransactionReference(txnId, edgeAssessmentId, txnRef, siteId);
    }

    /** Copies an ID-only reference onto the row. */
    public void applyTransactionReference(TransactionReference reference) {
        if (reference == null) {
            return;
        }
        this.txnId = reference.legacyTransactionId();
        this.edgeAssessmentId = reference.edgeAssessmentId();
        setTxnRef(reference.txnRef());
        this.siteId = reference.siteId();
    }
}
