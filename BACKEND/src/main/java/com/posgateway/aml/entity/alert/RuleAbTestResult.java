package com.posgateway.aml.entity.alert;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Rule A/B Test Result Entity
 * Records individual test results
 */
@Entity
@Table(name = "rule_ab_test_results", indexes = {
    @Index(name = "idx_ab_result_test", columnList = "test_id"),
    @Index(name = "idx_ab_result_variant", columnList = "variant")
})
public class RuleAbTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false)
    private RuleAbTest test;

    @Column(name = "variant", nullable = false, length = 1)
    private String variant; // A or B

    @Column(name = "is_true_positive", nullable = false)
    private Boolean isTruePositive;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        this.recordedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RuleAbTest getTest() {
        return test;
    }

    public void setTest(RuleAbTest test) {
        this.test = test;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public Boolean isTruePositive() {
        return isTruePositive;
    }

    public void setTruePositive(Boolean isTruePositive) {
        this.isTruePositive = isTruePositive;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }
}

