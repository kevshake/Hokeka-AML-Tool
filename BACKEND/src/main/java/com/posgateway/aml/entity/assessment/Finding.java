package com.posgateway.aml.entity.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "findings")
public class Finding {
    @Id
    private UUID id;
    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;
    @Column(name = "txn_id")
    private Long txnId;
    @Column(name = "source_type", nullable = false)
    private String sourceType;
    @Column(name = "source_id")
    private String sourceId;
    @Column(name = "source_version")
    private String sourceVersion;
    @Column(nullable = false)
    private String phase;
    @Column(nullable = false)
    private boolean shadow = true;
    @Column(nullable = false)
    private boolean triggered;
    private String nature;
    private String severity;
    private Double score;
    @Column(name = "proposed_action")
    private String proposedAction;
    @JdbcTypeCode(SqlTypes.JSON)
    private String evidence;
    private String explanation;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID assessmentId) { this.assessmentId = assessmentId; }
    public Long getTxnId() { return txnId; }
    public void setTxnId(Long txnId) { this.txnId = txnId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public boolean isShadow() { return shadow; }
    public void setShadow(boolean shadow) { this.shadow = shadow; }
    public boolean isTriggered() { return triggered; }
    public void setTriggered(boolean triggered) { this.triggered = triggered; }
    public String getNature() { return nature; }
    public void setNature(String nature) { this.nature = nature; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getProposedAction() { return proposedAction; }
    public void setProposedAction(String proposedAction) { this.proposedAction = proposedAction; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
