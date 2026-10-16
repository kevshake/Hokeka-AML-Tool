package com.posgateway.aml.entity.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mcc_risk_scores")
public class MccRiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "mcc", nullable = false, length = 8)
    private String mcc;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Column(name = "source_document_version", length = 64)
    private String sourceDocumentVersion;

    @Column(name = "effective_from")
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    /** Returns the persisted row id. */
    public Long getId() { return id; }
    /** Sets the persisted row id. */
    public void setId(Long id) { this.id = id; }
    /** Returns the owning PSP, or null for a shared score. */
    public Long getPspId() { return pspId; }
    /** Sets the owning PSP, or null for a shared score. */
    public void setPspId(Long pspId) { this.pspId = pspId; }
    /** Returns the merchant category code. */
    public String getMcc() { return mcc; }
    /** Sets the merchant category code. */
    public void setMcc(String mcc) { this.mcc = mcc; }
    /** Returns the configured risk score. */
    public Double getRiskScore() { return riskScore; }
    /** Sets the configured risk score. */
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
    /** Returns the source document version supporting the score. */
    public String getSourceDocumentVersion() { return sourceDocumentVersion; }
    /** Sets the source document version supporting the score. */
    public void setSourceDocumentVersion(String sourceDocumentVersion) {
        this.sourceDocumentVersion = sourceDocumentVersion;
    }
    /** Returns the first instant when the score is effective. */
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    /** Sets the first instant when the score is effective. */
    public void setEffectiveFrom(OffsetDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    /** Returns the exclusive end of the score's effective period. */
    public OffsetDateTime getEffectiveTo() { return effectiveTo; }
    /** Sets the exclusive end of the score's effective period. */
    public void setEffectiveTo(OffsetDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
}
