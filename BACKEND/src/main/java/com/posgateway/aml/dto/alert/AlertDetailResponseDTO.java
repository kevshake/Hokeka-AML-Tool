package com.posgateway.aml.dto.alert;

import com.posgateway.aml.dto.assessment.FindingResponseDTO;
import com.posgateway.aml.model.TransactionReference;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enriched alert payload for GET /alerts/{id} (transactions, findings, rule version).
 *
 * <p>Transactions are returned by ID only (residency contract I4, plan R8):
 * <ul>
 *   <li>{@code transactionRefs}: every linked transaction as decision record id / txnRef / site
 *       (and the legacy cloud id for legacy-ingest rows);</li>
 *   <li>{@code transactionIds}: the legacy cloud ids only, kept for existing callers until R12;</li>
 *   <li>{@code transactionDetails}: always null from this endpoint. The cloud holds no transaction
 *       detail; the Console fills it by pulling the referenced transactions live from the PSP's Edge
 *       (work package R6).</li>
 * </ul>
 */
public class AlertDetailResponseDTO {

    private Long alertId;
    private List<TransactionReference> transactionRefs = new ArrayList<>();
    private List<Map<String, Object>> transactionDetails;
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

    /** Linked transactions by ID reference. */
    public List<TransactionReference> getTransactionRefs() {
        return transactionRefs;
    }

    /** Sets the linked transaction references. */
    public void setTransactionRefs(List<TransactionReference> transactionRefs) {
        this.transactionRefs = transactionRefs;
    }

    /** Null from the cloud; filled by the R6 live pull from the Edge. */
    public List<Map<String, Object>> getTransactionDetails() {
        return transactionDetails;
    }

    /** Sets transaction details pulled live from the Edge (R6); never persisted. */
    public void setTransactionDetails(List<Map<String, Object>> transactionDetails) {
        this.transactionDetails = transactionDetails;
    }

    public List<FindingResponseDTO> getFindings() {
        return findings;
    }

    public void setFindings(List<FindingResponseDTO> findings) {
        this.findings = findings;
    }
}
