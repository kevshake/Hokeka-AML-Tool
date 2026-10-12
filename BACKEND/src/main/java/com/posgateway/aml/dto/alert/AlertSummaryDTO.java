package com.posgateway.aml.dto.alert;

import java.time.LocalDateTime;

/**
 * Compact alert row for transaction detail and list endpoints.
 */
public class AlertSummaryDTO {

    private Long alertId;
    private String action;
    private String status;
    private String severity;
    private String reason;
    private Long ruleInstanceId;
    private Long ruleVersionId;
    private LocalDateTime createdAt;

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
}
