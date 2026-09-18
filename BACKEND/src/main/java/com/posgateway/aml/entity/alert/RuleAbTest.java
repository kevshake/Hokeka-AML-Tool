package com.posgateway.aml.entity.alert;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Rule A/B Test Entity
 * Manages A/B testing for rule changes
 */
@Entity
@Table(name = "rule_ab_tests", indexes = {
    @Index(name = "idx_ab_test_rule", columnList = "rule_name"),
    @Index(name = "idx_ab_test_status", columnList = "status")
})
public class RuleAbTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    @Column(name = "variant_a", columnDefinition = "TEXT", nullable = false)
    private String variantA; // Original rule configuration

    @Column(name = "variant_b", columnDefinition = "TEXT", nullable = false)
    private String variantB; // New rule configuration

    @Column(name = "traffic_split_percent", nullable = false)
    private Integer trafficSplitPercent; // Percentage of traffic to variant A

    @Column(name = "status", nullable = false, length = 50)
    private String status; // ACTIVE, COMPLETED, CANCELLED

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

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

    public String getVariantA() {
        return variantA;
    }

    public void setVariantA(String variantA) {
        this.variantA = variantA;
    }

    public String getVariantB() {
        return variantB;
    }

    public void setVariantB(String variantB) {
        this.variantB = variantB;
    }

    public Integer getTrafficSplitPercent() {
        return trafficSplitPercent;
    }

    public void setTrafficSplitPercent(Integer trafficSplitPercent) {
        this.trafficSplitPercent = trafficSplitPercent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

