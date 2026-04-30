package com.posgateway.aml.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Risk Assessment Result
 * Contains AML and Fraud risk analysis results
 */
public class RiskAssessment {

    private String transactionId;
    private Integer amlRiskScore;
    private Integer fraudScore;
    private RiskLevel amlRiskLevel;
    private RiskLevel fraudRiskLevel;
    private String decision;
    private List<String> riskFactors = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    private LocalDateTime assessedAt;

    public RiskAssessment() {
        this.assessedAt = LocalDateTime.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Integer getAmlRiskScore() {
        return amlRiskScore;
    }

    public void setAmlRiskScore(Integer amlRiskScore) {
        this.amlRiskScore = amlRiskScore;
    }

    public Integer getFraudScore() {
        return fraudScore;
    }

    public void setFraudScore(Integer fraudScore) {
        this.fraudScore = fraudScore;
    }

    public RiskLevel getAmlRiskLevel() {
        return amlRiskLevel;
    }

    public void setAmlRiskLevel(RiskLevel amlRiskLevel) {
        this.amlRiskLevel = amlRiskLevel;
    }

    public RiskLevel getFraudRiskLevel() {
        return fraudRiskLevel;
    }

    public void setFraudRiskLevel(RiskLevel fraudRiskLevel) {
        this.fraudRiskLevel = fraudRiskLevel;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public List<String> getRiskFactors() {
        return riskFactors;
    }

    public void setRiskFactors(List<String> riskFactors) {
        this.riskFactors = riskFactors;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public LocalDateTime getAssessedAt() {
        return assessedAt;
    }

    public void setAssessedAt(LocalDateTime assessedAt) {
        this.assessedAt = assessedAt;
    }
}

