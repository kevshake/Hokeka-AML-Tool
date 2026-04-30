package com.posgateway.aml.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Model Metrics Entity
 * Stores monitoring metrics for model performance
 */
@Entity
@Table(name = "model_metrics", indexes = {
    @Index(name = "idx_metrics_date", columnList = "date")
})
public class ModelMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "auc")
    private Double auc;

    @Column(name = "precision_at_100")
    private Double precisionAt100;

    @Column(name = "avg_latency_ms")
    private Double avgLatencyMs;

    @Column(name = "drift_score")
    private Double driftScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Double getAuc() {
        return auc;
    }

    public void setAuc(Double auc) {
        this.auc = auc;
    }

    public Double getPrecisionAt100() {
        return precisionAt100;
    }

    public void setPrecisionAt100(Double precisionAt100) {
        this.precisionAt100 = precisionAt100;
    }

    public Double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(Double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public Double getDriftScore() {
        return driftScore;
    }

    public void setDriftScore(Double driftScore) {
        this.driftScore = driftScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

