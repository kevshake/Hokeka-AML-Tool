package com.posgateway.aml.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssessmentDetailResponseDTO {

    private UUID assessmentId;
    private Long pspId;
    private String triggerType;
    private String triggerRef;
    private Long txnId;
    private UUID parentAssessmentId;
    private UUID edgeAssessmentId;
    private Map<String, Object> versions;
    private String contextHash;
    private String decision;
    private Long latencyMs;
    private Instant createdAt;
    private List<FindingResponseDTO> findings;

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(UUID assessmentId) {
        this.assessmentId = assessmentId;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
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

    public List<FindingResponseDTO> getFindings() {
        return findings;
    }

    public void setFindings(List<FindingResponseDTO> findings) {
        this.findings = findings;
    }
}
