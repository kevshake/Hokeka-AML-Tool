package com.posgateway.aml.dto.alert;

import java.util.ArrayList;
import java.util.List;

/**
 * API payload for GET /transactions/{id}/alerts.
 */
public class TransactionAlertsResponseDTO {

    private Long txnId;
    /** Decision record id of the transaction (R6: alerts are looked up by ID reference). */
    private java.util.UUID edgeAssessmentId;
    /** Where the transaction itself lives: {@code EDGE} or {@code CLOUD_LEGACY} (R6). */
    private String source;
    private List<AlertSummaryDTO> alerts = new ArrayList<>();

    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    /** Decision record id of the transaction. */
    public java.util.UUID getEdgeAssessmentId() {
        return edgeAssessmentId;
    }

    /** Sets the decision record id. */
    public void setEdgeAssessmentId(java.util.UUID edgeAssessmentId) {
        this.edgeAssessmentId = edgeAssessmentId;
    }

    /** {@code EDGE} or {@code CLOUD_LEGACY}. */
    public String getSource() {
        return source;
    }

    /** Sets the transaction source. */
    public void setSource(String source) {
        this.source = source;
    }

    public List<AlertSummaryDTO> getAlerts() {
        return alerts;
    }

    public void setAlerts(List<AlertSummaryDTO> alerts) {
        this.alerts = alerts;
    }
}
