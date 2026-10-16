package com.posgateway.aml.dto.alert;

import com.posgateway.aml.dto.assessment.FindingResponseDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Enriched alert payload for GET /alerts/{id} (transactions, findings, rule version).
 */
public class AlertDetailResponseDTO {

    private Long alertId;
    private String action;
    private String status;
    private String severity;
    private String reason;
    private Long ruleInstanceId;
    private Long ruleVersionId;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private List<Long> transactionIds = new ArrayList<>();
    private List<FindingResponseDTO> findings = new ArrayList<>();

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getRuleInstanceId() {
        return ruleInstanceId;
    }

    public void setRuleInstanceId(Long ruleInstanceId) {
        this.ruleInstanceId = ruleInstanceId;
    }

    public Long getRuleVersionId() {
        return ruleVersionId;
    }

    public void setRuleVersionId(Long ruleVersionId) {
        this.ruleVersionId = ruleVersionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public List<Long> getTransactionIds() {
        return transactionIds;
    }

    public void setTransactionIds(List<Long> transactionIds) {
        this.transactionIds = transactionIds;
    }

    public List<FindingResponseDTO> getFindings() {
        return findings;
    }

    public void setFindings(List<FindingResponseDTO> findings) {
        this.findings = findings;
    }
}
