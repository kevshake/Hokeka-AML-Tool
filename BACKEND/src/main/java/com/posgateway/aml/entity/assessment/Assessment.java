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

    @Column(name = "context_hash", length = 128)
    private String contextHash;

    @Column(name = "decision", length = 16)
    private String decision;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // ── Decision record v2 (V262; docs/EDGE_DATA_RESIDENCY_CONTRACT.md §4). Class-D fields only. ──

    /** 2 for a decision record v2; 1 / null for v1 envelopes and cloud assessments. */
    @Column(name = "record_version")
    private Integer recordVersion;

    @Column(name = "edge_node_id", length = 64)
    private String edgeNodeId;

    @Column(name = "site_id", length = 64)
    private String siteId;

    /** The PSP's opaque transaction reference (never PAN-like; Luhn guard on ingest). */
    @Column(name = "txn_ref", length = 128)
    private String txnRef;

    /** Evaluation time, truncated to the second. Drives the retention purge. */
    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Column(name = "merchant_id", length = 64)
    private String merchantId;

    @Column(name = "score")
    private Integer score;

    @Column(name = "ctr_required")
    private Boolean ctrRequired;

    @Column(name = "sanctions_hit")
    private Boolean sanctionsHit;

    @Column(name = "fail_closed_code", length = 32)
    private String failClosedCode;

    @Column(name = "bundle_version")
    private Long bundleVersion;

    @Column(name = "evaluator", length = 16)
    private String evaluator;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "triggered_rule_ids", columnDefinition = "jsonb")
    private java.util.List<Long> triggeredRuleIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_version_ids", columnDefinition = "jsonb")
    private java.util.List<Long> ruleVersionIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "watchlist_entry_ids", columnDefinition = "jsonb")
    private java.util.List<String> watchlistEntryIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "typology_codes", columnDefinition = "jsonb")
    private java.util.List<String> typologyCodes;

    /** SHA-256 of the canonical record: same digest = duplicate, different = CONFLICT. */
    @Column(name = "record_digest", length = 64)
    private String recordDigest;

    @Column(name = "received_at")
    private Instant receivedAt;

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

    /** Decision record format version (2 for v2 records). */
    public Integer getRecordVersion() { return recordVersion; }

    /** Sets the decision record format version. */
    public void setRecordVersion(Integer recordVersion) { this.recordVersion = recordVersion; }

    /** Edge node that evaluated the transaction. */
    public String getEdgeNodeId() { return edgeNodeId; }

    /** Sets the evaluating Edge node. */
    public void setEdgeNodeId(String edgeNodeId) { this.edgeNodeId = edgeNodeId; }

    /** Edge Site of the evaluating node. */
    public String getSiteId() { return siteId; }

    /** Sets the Edge Site of the evaluating node. */
    public void setSiteId(String siteId) { this.siteId = siteId; }

    /** PSP transaction reference. */
    public String getTxnRef() { return txnRef; }

    /** Sets the PSP transaction reference. */
    public void setTxnRef(String txnRef) { this.txnRef = txnRef; }

    /** Evaluation time (second precision). */
    public Instant getEvaluatedAt() { return evaluatedAt; }

    /** Sets the evaluation time. */
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    /** Cloud merchant id the record names, if any. */
    public String getMerchantId() { return merchantId; }

    /** Sets the cloud merchant id. */
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    /** Sum of triggered rule scores. */
    public Integer getScore() { return score; }

    /** Sets the score. */
    public void setScore(Integer score) { this.score = score; }

    /** Whether the Edge flagged a CTR. */
    public Boolean getCtrRequired() { return ctrRequired; }

    /** Sets the CTR flag. */
    public void setCtrRequired(Boolean ctrRequired) { this.ctrRequired = ctrRequired; }

    /** Whether the Edge found a sanctions/PEP match. */
    public Boolean getSanctionsHit() { return sanctionsHit; }

    /** Sets the sanctions flag. */
    public void setSanctionsHit(Boolean sanctionsHit) { this.sanctionsHit = sanctionsHit; }

    /** Fail-closed gate that answered HOLD, if any. */
    public String getFailClosedCode() { return failClosedCode; }

    /** Sets the fail-closed code. */
    public void setFailClosedCode(String failClosedCode) { this.failClosedCode = failClosedCode; }

    /** Rule bundle version in force (-1 before the first bundle). */
    public Long getBundleVersion() { return bundleVersion; }

    /** Sets the rule bundle version. */
    public void setBundleVersion(Long bundleVersion) { this.bundleVersion = bundleVersion; }

    /** Evaluator that produced the decision (native / java / none). */
    public String getEvaluator() { return evaluator; }

    /** Sets the evaluator. */
    public void setEvaluator(String evaluator) { this.evaluator = evaluator; }

    /** Triggered rule ids. */
    public java.util.List<Long> getTriggeredRuleIds() { return triggeredRuleIds; }

    /** Sets the triggered rule ids. */
    public void setTriggeredRuleIds(java.util.List<Long> triggeredRuleIds) { this.triggeredRuleIds = triggeredRuleIds; }

    /** Rule version ids of the triggered rules. */
    public java.util.List<Long> getRuleVersionIds() { return ruleVersionIds; }

    /** Sets the rule version ids. */
    public void setRuleVersionIds(java.util.List<Long> ruleVersionIds) { this.ruleVersionIds = ruleVersionIds; }

    /** Public watchlist entry ids of a sanctions hit. */
    public java.util.List<String> getWatchlistEntryIds() { return watchlistEntryIds; }

    /** Sets the watchlist entry ids. */
    public void setWatchlistEntryIds(java.util.List<String> watchlistEntryIds) { this.watchlistEntryIds = watchlistEntryIds; }

    /** Typology codes. */
    public java.util.List<String> getTypologyCodes() { return typologyCodes; }

    /** Sets the typology codes. */
    public void setTypologyCodes(java.util.List<String> typologyCodes) { this.typologyCodes = typologyCodes; }

    /** Canonical record digest. */
    public String getRecordDigest() { return recordDigest; }

    /** Sets the canonical record digest. */
    public void setRecordDigest(String recordDigest) { this.recordDigest = recordDigest; }

    /** When the cloud received the record. */
    public Instant getReceivedAt() { return receivedAt; }

    /** Sets when the cloud received the record. */
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
