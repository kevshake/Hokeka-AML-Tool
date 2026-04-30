package com.posgateway.aml.entity.alert;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Alert Tuning Recommendation Entity
 * Stores ML-based tuning recommendations for rules
 */
@Entity
@Table(name = "alert_tuning_recommendations", indexes = {
    @Index(name = "idx_tuning_rule", columnList = "rule_name"),
    @Index(name = "idx_tuning_status", columnList = "status")
})
public class AlertTuningRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    @Column(name = "false_positive_rate")
    private Double falsePositiveRate;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "priority", length = 50)
    private String priority; // HIGH, MEDIUM, LOW

    @Column(name = "status", nullable = false, length = 50)
    private String status; // PENDING, APPLIED, REJECTED

    @Column(name = "applied_by")
    private Long appliedBy;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Double getFalsePositiveRate() {
        return falsePositiveRate;
    }

    public void setFalsePositiveRate(Double falsePositiveRate) {
        this.falsePositiveRate = falsePositiveRate;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAppliedBy() {
        return appliedBy;
    }

    public void setAppliedBy(Long appliedBy) {
        this.appliedBy = appliedBy;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

