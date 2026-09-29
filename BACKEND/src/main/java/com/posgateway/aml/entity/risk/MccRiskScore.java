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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
    public String getMcc() { return mcc; }
    public void setMcc(String mcc) { this.mcc = mcc; }
    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
    public String getSourceDocumentVersion() { return sourceDocumentVersion; }
    public void setSourceDocumentVersion(String sourceDocumentVersion) {
        this.sourceDocumentVersion = sourceDocumentVersion;
    }
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(OffsetDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public OffsetDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(OffsetDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
}
