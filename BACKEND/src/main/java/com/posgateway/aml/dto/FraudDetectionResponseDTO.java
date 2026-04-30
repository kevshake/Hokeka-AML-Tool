package com.posgateway.aml.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Fraud Detection Response DTO
 * Response DTO for fraud detection results
 */
public class FraudDetectionResponseDTO {

    private Long txnId;
    // Score removed as per requirement, moved to riskDetails
    private String action;
    private List<String> reasons;
    private Long latencyMs;
    private java.util.Map<String, Object> riskDetails;

    // Getters and Setters
    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    @JsonProperty("scores")
    public java.util.Map<String, Object> getRiskDetails() {
        return riskDetails;
    }

    public void setRiskDetails(java.util.Map<String, Object> riskDetails) {
        this.riskDetails = riskDetails;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }
}
