package com.posgateway.aml.entity.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Join row linking one alert to a transaction (many transactions per alert).
 */
@Entity
@Table(name = "alert_transactions")
@IdClass(AlertTransactionLink.AlertTransactionId.class)
public class AlertTransactionLink {

    @Id
    @Column(name = "alert_id")
    private Long alertId;

    @Id
    @Column(name = "txn_id")
    private Long txnId;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt = LocalDateTime.now();

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(LocalDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }

    public static class AlertTransactionId implements Serializable {
        private Long alertId;
        private Long txnId;

        public AlertTransactionId() {
        }

        public AlertTransactionId(Long alertId, Long txnId) {
            this.alertId = alertId;
            this.txnId = txnId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AlertTransactionId that)) {
                return false;
            }
            return Objects.equals(alertId, that.alertId) && Objects.equals(txnId, that.txnId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(alertId, txnId);
        }
    }
}
