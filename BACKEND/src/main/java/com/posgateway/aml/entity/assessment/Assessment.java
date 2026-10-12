package com.posgateway.aml.entity.assessment;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "assessments", indexes = {
        @Index(name = "idx_assessments_psp_created", columnList = "psp_id, created_at"),
        @Index(name = "idx_assessments_trigger_ref", columnList = "trigger_type, trigger_ref"),
        @Index(name = "idx_assessments_txn_created", columnList = "txn_id, created_at")
})
public class Assessment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private AssessmentTriggerType triggerType;

    @Column(name = "trigger_ref", nullable = false, length = 128)
    private String triggerRef;

    @Column(name = "txn_id")
    private Long txnId;

    @Column(name = "parent_assessment_id")
    private UUID parentAssessmentId;

    @Column(name = "edge_assessment_id")
    private UUID edgeAssessmentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "versions", columnDefinition = "jsonb")
    private Map<String, Object> versions;

    @Column(name = "context_hash", length = 64)
    private String contextHash;

    @Column(name = "decision", length = 16)
    private String decision;

    @Column(name = "latency_ms")
    private Long latencyMs;

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

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public AssessmentTriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(AssessmentTriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggerRef() {
        return triggerRef;
    }

    public void setTriggerRef(String triggerRef) {
        this.triggerRef = triggerRef;
    }

    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    public UUID getParentAssessmentId() {
        return parentAssessmentId;
    }

    public void setParentAssessmentId(UUID parentAssessmentId) {
        this.parentAssessmentId = parentAssessmentId;
    }

    public UUID getEdgeAssessmentId() {
        return edgeAssessmentId;
    }

    public void setEdgeAssessmentId(UUID edgeAssessmentId) {
        this.edgeAssessmentId = edgeAssessmentId;
    }

    public Map<String, Object> getVersions() {
        return versions;
    }

    public void setVersions(Map<String, Object> versions) {
        this.versions = versions;
    }

    public String getContextHash() {
        return contextHash;
    }

    public void setContextHash(String contextHash) {
        this.contextHash = contextHash;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
