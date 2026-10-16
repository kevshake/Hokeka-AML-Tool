package com.posgateway.aml.entity.compliance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.posgateway.aml.model.TransactionReference;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * One transaction a SAR is about, referenced by ID only (V264, residency contract I4 and plan
 * conflict 7). A SAR draft persists these references and nothing else about the transaction: its
 * transaction table is rendered from the Edge when viewed and stored only inside the filed document
 * (work package R9). {@code txn_id} is the legacy cloud id (no foreign key) kept until R12 / R15.
 */
@Entity
@Table(name = "sar_transactions")
@Audited
public class SarTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sar_id", nullable = false)
    private SuspiciousActivityReport sar;

    /** Legacy cloud transactions.txn_id; null for Edge decision records. */
    @Column(name = "txn_id")
    private Long txnId;

    @Column(name = "edge_assessment_id")
    private UUID edgeAssessmentId;

    @Column(name = "txn_ref", length = 128)
    private String txnRef;

    @Column(name = "site_id", length = 64)
    private String siteId;

    /** JPA constructor. */
    public SarTransaction() {
    }

    /** A reference of {@code sar} to one transaction. */
    public SarTransaction(SuspiciousActivityReport sar, TransactionReference reference) {
        this.sar = sar;
        applyTransactionReference(reference);
    }

    /** Row id. */
    public Long getId() {
        return id;
    }

    /** Sets the row id. */
    public void setId(Long id) {
        this.id = id;
    }

    /** The owning SAR. */
    public SuspiciousActivityReport getSar() {
        return sar;
    }

    /** Sets the owning SAR. */
    public void setSar(SuspiciousActivityReport sar) {
        this.sar = sar;
    }

    /** Legacy cloud transaction id, if any. */
    public Long getTxnId() {
        return txnId;
    }

    /** Sets the legacy cloud transaction id. */
    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    /** Decision record id of the transaction. */
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

    /** Edge Site of the transaction. */
    public String getSiteId() {
        return siteId;
    }

    /** Sets the Edge Site. */
    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    /** The transaction as an ID-only reference. */
    @JsonIgnore
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
