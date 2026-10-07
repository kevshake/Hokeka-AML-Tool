package com.posgateway.aml.dto.assessment;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FindingResponseDTO {

    private UUID findingId;
    private UUID assessmentId;
    private Long txnId;
    private Long partyId;
    private String sourceType;
    private String sourceId;
    private String sourceVersion;
    private String phase;
    private boolean shadow;
    private String nature;
    private String severity;
    private boolean triggered;
    private Double score;
    private String proposedAction;
    private List<Map<String, Object>> proposedActions;
    private Map<String, Object> evidence;
    private List<Map<String, Object>> featureReferences;
    private String explanation;
    private Instant createdAt;

    public UUID getFindingId() {
        return findingId;
    }

    public void setFindingId(UUID findingId) {
        this.findingId = findingId;
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
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

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
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
