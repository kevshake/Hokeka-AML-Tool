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
@Table(name = "assessments")
public class Assessment {
    @Id
    private UUID id;
    @Column(name = "psp_id")
    private Long pspId;
    @Column(name = "txn_id")
    private Long txnId;
    @Column(nullable = false)
    private String trigger;
    @Column(name = "trigger_ref")
    private String triggerRef;
    @Column(name = "parent_assessment_id")
    private java.util.UUID parentAssessmentId;
    @Column(name = "edge_assessment_id", length = 128)
    private String edgeAssessmentId;
    @Column(name = "context_hash")
    private String contextHash;
    @JdbcTypeCode(SqlTypes.JSON)
    private String versions;
    private String decision;
    @Column(name = "latency_ms")
    private Long latencyMs;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
    public Long getTxnId() { return txnId; }
    public void setTxnId(Long txnId) { this.txnId = txnId; }
    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
    public String getTriggerRef() { return triggerRef; }
    public void setTriggerRef(String triggerRef) { this.triggerRef = triggerRef; }
    public java.util.UUID getParentAssessmentId() { return parentAssessmentId; }
    public void setParentAssessmentId(java.util.UUID parentAssessmentId) { this.parentAssessmentId = parentAssessmentId; }
    public String getEdgeAssessmentId() { return edgeAssessmentId; }
    public void setEdgeAssessmentId(String edgeAssessmentId) { this.edgeAssessmentId = edgeAssessmentId; }
    public String getContextHash() { return contextHash; }
    public void setContextHash(String contextHash) { this.contextHash = contextHash; }
    public String getVersions() { return versions; }
    public void setVersions(String versions) { this.versions = versions; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
