package com.posgateway.aml.dto.alert;

import java.util.ArrayList;
import java.util.List;

/**
 * API payload for GET /transactions/{id}/alerts.
 */
public class TransactionAlertsResponseDTO {

    private Long txnId;
    private List<AlertSummaryDTO> alerts = new ArrayList<>();

    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    public List<AlertSummaryDTO> getAlerts() {
        return alerts;
    }

    public void setAlerts(List<AlertSummaryDTO> alerts) {
        this.alerts = alerts;
    }
}
