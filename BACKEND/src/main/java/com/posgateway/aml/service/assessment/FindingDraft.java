package com.posgateway.aml.service.assessment;

import com.posgateway.aml.entity.assessment.FindingPhase;
import com.posgateway.aml.entity.assessment.FindingSourceType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable builder input for {@link FindingRecorder}; maps to {@link com.posgateway.aml.entity.assessment.Finding}.
 */
public final class FindingDraft {

    private UUID assessmentId;
    private Long txnId;
    private Long partyId;
    private FindingSourceType sourceType;
    private String sourceId;
    private String sourceVersion;
    private FindingPhase phase = FindingPhase.CP_SYNC;
    private boolean shadow = true;
    private String nature;
    private String severity;
    private boolean triggered;
    private Double score;
    private String proposedAction;
    private List<Map<String, Object>> proposedActions;
    private Map<String, Object> evidence;
    private List<Map<String, Object>> featureReferences;
    private String explanation;

    public static FindingDraft create() {
        return new FindingDraft();
    }

    public FindingDraft assessmentId(UUID assessmentId) {
        this.assessmentId = assessmentId;
        return this;
    }

    public FindingDraft txnId(Long txnId) {
        this.txnId = txnId;
        return this;
    }

    public FindingDraft partyId(Long partyId) {
        this.partyId = partyId;
        return this;
    }

    public FindingDraft sourceType(FindingSourceType sourceType) {
        this.sourceType = sourceType;
        return this;
    }

    public FindingDraft sourceId(String sourceId) {
        this.sourceId = sourceId;
        return this;
    }

    public FindingDraft sourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
        return this;
    }

    public FindingDraft phase(FindingPhase phase) {
        this.phase = phase;
        return this;
    }

    public FindingDraft shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public FindingDraft nature(String nature) {
        this.nature = nature;
        return this;
    }

    public FindingDraft severity(String severity) {
        this.severity = severity;
        return this;
    }

    public FindingDraft triggered(boolean triggered) {
        this.triggered = triggered;
        return this;
    }

    public FindingDraft score(Double score) {
        this.score = score;
        return this;
    }

    public FindingDraft proposedAction(String proposedAction) {
        this.proposedAction = proposedAction;
        return this;
    }

    public FindingDraft proposedActions(List<Map<String, Object>> proposedActions) {
        this.proposedActions = proposedActions;
        return this;
    }

    public FindingDraft evidence(Map<String, Object> evidence) {
        this.evidence = evidence;
        return this;
    }

    public FindingDraft featureReferences(List<Map<String, Object>> featureReferences) {
        this.featureReferences = featureReferences;
        return this;
    }

    public FindingDraft explanation(String explanation) {
        this.explanation = explanation;
        return this;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public Long getTxnId() {
        return txnId;
    }

    public Long getPartyId() {
        return partyId;
    }

    public FindingSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public FindingPhase getPhase() {
        return phase;
    }

    public boolean isShadow() {
        return shadow;
    }

    public String getNature() {
        return nature;
    }

    public String getSeverity() {
        return severity;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public Double getScore() {
        return score;
    }

    public String getProposedAction() {
        return proposedAction;
    }

    public List<Map<String, Object>> getProposedActions() {
        return proposedActions;
    }

    public Map<String, Object> getEvidence() {
        return evidence;
    }

    public List<Map<String, Object>> getFeatureReferences() {
        return featureReferences;
    }

    public String getExplanation() {
        return explanation;
    }
}
