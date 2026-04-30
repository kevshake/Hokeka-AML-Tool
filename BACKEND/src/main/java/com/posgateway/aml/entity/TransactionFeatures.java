package com.posgateway.aml.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Transaction Features Entity
 * Stores features used for model scoring and historical data
 */
@Entity
@Table(name = "transaction_features", indexes = {
    @Index(name = "idx_features_label", columnList = "label"),
    @Index(name = "idx_features_scored_at", columnList = "scored_at")
})
public class TransactionFeatures {

    @Id
    @Column(name = "txn_id")
    private Long txnId;

    @OneToOne
    @JoinColumn(name = "txn_id", referencedColumnName = "txn_id")
    private TransactionEntity transaction;

    @Column(name = "feature_json", columnDefinition = "JSONB")
    private String featureJson; // JSON string representation of features

    @Column(name = "score")
    private Double score;

    @Column(name = "action_taken")
    private String actionTaken;

    @Column(name = "label")
    private Short label; // 1 = fraud, 0 = good, NULL = unknown

    @Column(name = "scored_at")
    private LocalDateTime scoredAt;

    @PrePersist
    protected void onCreate() {
        if (scoredAt == null) {
            scoredAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    public TransactionEntity getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionEntity transaction) {
        this.transaction = transaction;
    }

    public String getFeatureJson() {
        return featureJson;
    }

    public void setFeatureJson(String featureJson) {
        this.featureJson = featureJson;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }

    public Short getLabel() {
        return label;
    }

    public void setLabel(Short label) {
        this.label = label;
    }

    public LocalDateTime getScoredAt() {
        return scoredAt;
    }

    public void setScoredAt(LocalDateTime scoredAt) {
        this.scoredAt = scoredAt;
    }
}

