package com.posgateway.aml.entity.assessment;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "findings", indexes = {
        @Index(name = "idx_findings_assessment", columnList = "assessment_id"),
        @Index(name = "idx_findings_txn", columnList = "txn_id"),
        @Index(name = "idx_findings_party", columnList = "party_id"),
        @Index(name = "idx_findings_source", columnList = "source_type, source_id")
})
public class Finding {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "txn_id")
    private Long txnId;

    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "counterparty_party_id")
    private Long counterpartyPartyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private FindingSourceType sourceType;

    @Column(name = "source_id", length = 128)
    private String sourceId;

    @Column(name = "source_version", length = 256)
    private String sourceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 16)
    private FindingPhase phase = FindingPhase.CP_SYNC;

    @Column(name = "shadow", nullable = false)
    private boolean shadow = true;

    @Column(name = "nature", length = 32)
    private String nature;

    @Column(name = "severity", length = 16)
    private String severity;

    @Column(name = "triggered", nullable = false)
    private boolean triggered;

    @Column(name = "score")
    private Double score;

    @Column(name = "proposed_action", length = 16)
    private String proposedAction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed_actions", columnDefinition = "jsonb")
    private List<Map<String, Object>> proposedActions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private Map<String, Object> evidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_references", columnDefinition = "jsonb")
    private List<Map<String, Object>> featureReferences;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(UUID assessmentId) {
        this.assessmentId = assessmentId;
    }

    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    public Long getPartyId() {
        return partyId;
    }

    public void setPartyId(Long partyId) {
        this.partyId = partyId;
    }

    public Long getCounterpartyPartyId() {
        return counterpartyPartyId;
    }

    public void setCounterpartyPartyId(Long counterpartyPartyId) {
        this.counterpartyPartyId = counterpartyPartyId;
    }

    public FindingSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(FindingSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public FindingPhase getPhase() {
        return phase;
    }

    public void setPhase(FindingPhase phase) {
        this.phase = phase;
    }

    public boolean isShadow() {
        return shadow;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    public String getNature() {
        return nature;
    }

    public void setNature(String nature) {
        this.nature = nature;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getProposedAction() {
        return proposedAction;
    }

    public void setProposedAction(String proposedAction) {
        this.proposedAction = proposedAction;
    }

    public List<Map<String, Object>> getProposedActions() {
        return proposedActions;
    }

    public void setProposedActions(List<Map<String, Object>> proposedActions) {
        this.proposedActions = proposedActions;
    }

    public Map<String, Object> getEvidence() {
        return evidence;
    }

    public void setEvidence(Map<String, Object> evidence) {
        this.evidence = evidence;
    }

    public List<Map<String, Object>> getFeatureReferences() {
        return featureReferences;
    }

    public void setFeatureReferences(List<Map<String, Object>> featureReferences) {
        this.featureReferences = featureReferences;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
