package com.posgateway.aml.entity.compliance;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

/**
 * Case Alert Entity
 * Represents an alert (Rule Trigger, ML score, Sanctions Hit) that contributed
 * to a case.
 */
@Entity
@Table(name = "case_alerts")
@Audited
public class CaseAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ComplianceCase complianceCase;

    @Column(nullable = false)
    private String alertType; // RULE, ML_SCORE, SANCTIONS, MANUAL

    private String ruleName; // e.g. "CTR_THRESHOLD_10K"
    private String ruleId;

    private String modelVersion; // e.g. "XGBoost-v2.1"
    private String ruleVersion; // e.g. "Policy-2023-Q4"

    private Double score; // e.g. 0.95 (ML Score)

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String rawData; // JSON snapshot of triggering data

    @Column(nullable = false)
    private LocalDateTime triggeredAt;

    public CaseAlert() {
    }

    public CaseAlert(ComplianceCase complianceCase, String alertType, String ruleName, String ruleId,
            String modelVersion, String ruleVersion, Double score,
            String description, String rawData, LocalDateTime triggeredAt) {
        this.complianceCase = complianceCase;
        this.alertType = alertType;
        this.ruleName = ruleName;
        this.ruleId = ruleId;
        this.modelVersion = modelVersion;
        this.ruleVersion = ruleVersion;
        this.score = score;
        this.description = description;
        this.rawData = rawData;
        this.triggeredAt = triggeredAt;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public ComplianceCase getComplianceCase() {
        return complianceCase;
    }

    public void setComplianceCase(ComplianceCase complianceCase) {
        this.complianceCase = complianceCase;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public LocalDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(LocalDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }
}
