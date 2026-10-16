package com.posgateway.aml.entity.sanctions;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted record of a sanctions/PEP screening run for regulator evidence and cross-linking to txns/alerts.
 */
@Entity
@Table(name = "screening_activities")
public class ScreeningActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "merchant_id")
    private Long merchantId;

    /** Legacy cloud transactions.txn_id (no foreign key since V264). */
    @Column(name = "txn_id")
    private Long txnId;

    /** Decision record of the screened transaction (V264, ID-only reference). */
    @Column(name = "edge_assessment_id")
    private UUID edgeAssessmentId;

    /** The PSP's transaction id; never card-shaped. */
    @Column(name = "txn_ref", length = 128)
    private String txnRef;

    /** Edge Site of the screened transaction. */
    @Column(name = "site_id", length = 64)
    private String siteId;

    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    @Column(name = "subject_name", length = 512)
    private String subjectName;

    @Column(name = "screening_id", nullable = false)
    private UUID screeningId;

    @Column(name = "is_hit", nullable = false)
    private boolean hit;

    @Column(name = "is_blocking", nullable = false)
    private boolean blocking;

    @Column(name = "match_count", nullable = false)
    private int matchCount;

    @Column(name = "highest_score")
    private Double highestScore;

    @Column(name = "list_version", length = 128)
    private String listVersion;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private Map<String, Object> evidence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (screeningId == null) {
            screeningId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    /** Decision record id of the screened transaction. */
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
        this.txnRef = com.posgateway.aml.model.TransactionReference.safeTxnRef(txnRef);
    }

    /** Edge Site of the screened transaction. */
    public String getSiteId() {
        return siteId;
    }

    /** Sets the Edge Site. */
    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    /** Copies an ID-only transaction reference onto the activity. */
    public void applyTransactionReference(com.posgateway.aml.model.TransactionReference reference) {
        if (reference == null) {
            return;
        }
        this.txnId = reference.legacyTransactionId();
        this.edgeAssessmentId = reference.edgeAssessmentId();
        setTxnRef(reference.txnRef());
        this.siteId = reference.siteId();
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public UUID getScreeningId() {
        return screeningId;
    }

    public void setScreeningId(UUID screeningId) {
        this.screeningId = screeningId;
    }

    public boolean isHit() {
        return hit;
    }

    public void setHit(boolean hit) {
        this.hit = hit;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public void setBlocking(boolean blocking) {
        this.blocking = blocking;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(int matchCount) {
        this.matchCount = matchCount;
    }

    public Double getHighestScore() {
        return highestScore;
    }

    public void setHighestScore(Double highestScore) {
        this.highestScore = highestScore;
    }

    public String getListVersion() {
        return listVersion;
    }

    public void setListVersion(String listVersion) {
        this.listVersion = listVersion;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> getEvidence() {
        return evidence;
    }

    public void setEvidence(Map<String, Object> evidence) {
        this.evidence = evidence;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
