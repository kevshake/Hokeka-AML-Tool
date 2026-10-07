package com.posgateway.aml.entity.ai;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "ai_decision_audit")
public class AiDecisionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "engine_code", nullable = false, length = 64)
    private String engineCode;

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;

    @Column(name = "model_id", length = 128)
    private String modelId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_features", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> requestFeatures = new LinkedHashMap<>();

    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_response", columnDefinition = "jsonb")
    private Map<String, Object> parsedResponse;

    @Column(name = "recommendation", length = 32)
    private String recommendation;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "confidence")
    private Double confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasons", columnDefinition = "jsonb")
    private List<String> reasons = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cited_signals", columnDefinition = "jsonb")
    private List<String> citedSignals = new ArrayList<>();

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "estimated_cost_usd", precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd;

    @Column(name = "fallback_reason", length = 256)
    private String fallbackReason;

    @Column(name = "ai_applied", nullable = false)
    private boolean aiApplied;

    @Column(name = "baseline_decision", length = 32)
    private String baselineDecision;

    @Column(name = "final_decision", length = 32)
    private String finalDecision;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "screening_hit_id", length = 128)
    private String screeningHitId;

    @Column(name = "edge_id", length = 128)
    private String edgeId;

    @Column(name = "decision_point", length = 64)
    private String decisionPoint;

    @Column(name = "question_config_version", length = 64)
    private String questionConfigVersion;

    @Column(name = "provider_request_id", length = 128)
    private String providerRequestId;

    @Column(name = "model_snapshot", length = 128)
    private String modelSnapshot;

    @Column(name = "state_hash", length = 64)
    private String stateHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers_json", columnDefinition = "jsonb")
    private Map<String, Object> answersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "thresholds_json", columnDefinition = "jsonb")
    private Map<String, Object> thresholdsJson;

    @Column(name = "branch_taken", length = 64)
    private String branchTaken;

    @Column(name = "shadow_mode", nullable = false)
    private boolean shadowMode = true;

    @Column(name = "would_apply", nullable = false)
    private boolean wouldApply;

    @Column(name = "usage_cost_usd", precision = 12, scale = 6)
    private BigDecimal usageCostUsd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public String getEngineCode() {
        return engineCode;
    }

    public void setEngineCode(String engineCode) {
        this.engineCode = engineCode;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public Map<String, Object> getRequestFeatures() {
        return requestFeatures;
    }

    public void setRequestFeatures(Map<String, Object> requestFeatures) {
        this.requestFeatures = requestFeatures;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public Map<String, Object> getParsedResponse() {
        return parsedResponse;
    }

    public void setParsedResponse(Map<String, Object> parsedResponse) {
        this.parsedResponse = parsedResponse;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }

    public List<String> getCitedSignals() {
        return citedSignals;
    }

    public void setCitedSignals(List<String> citedSignals) {
        this.citedSignals = citedSignals;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public void setEstimatedCostUsd(BigDecimal estimatedCostUsd) {
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public boolean isAiApplied() {
        return aiApplied;
    }

    public void setAiApplied(boolean aiApplied) {
        this.aiApplied = aiApplied;
    }

    public String getBaselineDecision() {
        return baselineDecision;
    }

    public void setBaselineDecision(String baselineDecision) {
        this.baselineDecision = baselineDecision;
    }

    public String getFinalDecision() {
        return finalDecision;
    }

    public void setFinalDecision(String finalDecision) {
        this.finalDecision = finalDecision;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getScreeningHitId() {
        return screeningHitId;
    }

    public void setScreeningHitId(String screeningHitId) {
        this.screeningHitId = screeningHitId;
    }

    public String getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    public String getDecisionPoint() {
        return decisionPoint;
    }

    public void setDecisionPoint(String decisionPoint) {
        this.decisionPoint = decisionPoint;
    }

    public String getQuestionConfigVersion() {
        return questionConfigVersion;
    }

    public void setQuestionConfigVersion(String questionConfigVersion) {
        this.questionConfigVersion = questionConfigVersion;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public void setProviderRequestId(String providerRequestId) {
        this.providerRequestId = providerRequestId;
    }

    public String getModelSnapshot() {
        return modelSnapshot;
    }

    public void setModelSnapshot(String modelSnapshot) {
        this.modelSnapshot = modelSnapshot;
    }

    public String getStateHash() {
        return stateHash;
    }

    public void setStateHash(String stateHash) {
        this.stateHash = stateHash;
    }

    public Map<String, Object> getAnswersJson() {
        return answersJson;
    }

    public void setAnswersJson(Map<String, Object> answersJson) {
        this.answersJson = answersJson;
    }

    public Map<String, Object> getThresholdsJson() {
        return thresholdsJson;
    }

    public void setThresholdsJson(Map<String, Object> thresholdsJson) {
        this.thresholdsJson = thresholdsJson;
    }

    public String getBranchTaken() {
        return branchTaken;
    }

    public void setBranchTaken(String branchTaken) {
        this.branchTaken = branchTaken;
    }

    public boolean isShadowMode() {
        return shadowMode;
    }

    public void setShadowMode(boolean shadowMode) {
        this.shadowMode = shadowMode;
    }

    public boolean isWouldApply() {
        return wouldApply;
    }

    public void setWouldApply(boolean wouldApply) {
        this.wouldApply = wouldApply;
    }

    public BigDecimal getUsageCostUsd() {
        return usageCostUsd;
    }

    public void setUsageCostUsd(BigDecimal usageCostUsd) {
        this.usageCostUsd = usageCostUsd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
