package com.posgateway.aml.entity.compliance;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Join row linking a canonical {@code alerts} record to one or more {@code transactions}.
 * Supports consolidated alerts that accumulate multiple txn hits under one investigation.
 */
@Entity
@Table(name = "alert_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_alert_transactions_pair", columnNames = {"alert_id", "txn_id"})
})
public class AlertTransactionLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(name = "txn_id", nullable = false)
    private Long txnId;

    @Column(name = "primary_link", nullable = false)
    private boolean primaryLink;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    void onCreate() {
        if (linkedAt == null) {
            linkedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public boolean isPrimaryLink() {
        return primaryLink;
    }

    public void setPrimaryLink(boolean primaryLink) {
        this.primaryLink = primaryLink;
    }

    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(LocalDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }
}
