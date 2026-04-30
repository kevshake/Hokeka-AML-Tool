package com.posgateway.aml.entity.merchant;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Merchant risk score entity
 */
@Entity
@Table(name = "merchant_risk_scores")
public class MerchantRiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "score_id")
    private Long scoreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    // Risk Score
    @Column(name = "total_score", nullable = false)
    private Integer totalScore;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    // Component Scores
    @Column(name = "sanctions_score")
    private Integer sanctionsScore;

    @Column(name = "pep_score")
    private Integer pepScore;

    @Column(name = "country_risk_score")
    private Integer countryRiskScore;

    @Column(name = "industry_risk_score")
    private Integer industryRiskScore;

    @Column(name = "volume_risk_score")
    private Integer volumeRiskScore;

    @Column(name = "business_age_score")
    private Integer businessAgeScore;

    // Decision
    @Column(name = "decision", length = 50)
    private String decision; // APPROVE, REVIEW, REJECT

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    // Metadata
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt = LocalDateTime.now();

    @Column(name = "calculated_by", length = 100)
    private String calculatedBy;

    @Column(name = "rules_version", length = 50)
    private String rulesVersion;

    public MerchantRiskScore() {
    }

    public MerchantRiskScore(Long scoreId, Merchant merchant, Integer totalScore, String riskLevel,
            Integer sanctionsScore, Integer pepScore, Integer countryRiskScore, Integer industryRiskScore,
            Integer volumeRiskScore, Integer businessAgeScore, String decision, String decisionReason,
            LocalDateTime calculatedAt, String calculatedBy, String rulesVersion) {
        this.scoreId = scoreId;
        this.merchant = merchant;
        this.totalScore = totalScore;
        this.riskLevel = riskLevel;
        this.sanctionsScore = sanctionsScore;
        this.pepScore = pepScore;
        this.countryRiskScore = countryRiskScore;
        this.industryRiskScore = industryRiskScore;
        this.volumeRiskScore = volumeRiskScore;
        this.businessAgeScore = businessAgeScore;
        this.decision = decision;
        this.decisionReason = decisionReason;
        this.calculatedAt = calculatedAt != null ? calculatedAt : LocalDateTime.now();
        this.calculatedBy = calculatedBy;
        this.rulesVersion = rulesVersion;
    }

    public Long getScoreId() {
        return scoreId;
    }

    public void setScoreId(Long scoreId) {
        this.scoreId = scoreId;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getSanctionsScore() {
        return sanctionsScore;
    }

    public void setSanctionsScore(Integer sanctionsScore) {
        this.sanctionsScore = sanctionsScore;
    }

    public Integer getPepScore() {
        return pepScore;
    }

    public void setPepScore(Integer pepScore) {
        this.pepScore = pepScore;
    }

    public Integer getCountryRiskScore() {
        return countryRiskScore;
    }

    public void setCountryRiskScore(Integer countryRiskScore) {
        this.countryRiskScore = countryRiskScore;
    }

    public Integer getIndustryRiskScore() {
        return industryRiskScore;
    }

    public void setIndustryRiskScore(Integer industryRiskScore) {
        this.industryRiskScore = industryRiskScore;
    }

    public Integer getVolumeRiskScore() {
        return volumeRiskScore;
    }

    public void setVolumeRiskScore(Integer volumeRiskScore) {
        this.volumeRiskScore = volumeRiskScore;
    }

    public Integer getBusinessAgeScore() {
        return businessAgeScore;
    }

    public void setBusinessAgeScore(Integer businessAgeScore) {
        this.businessAgeScore = businessAgeScore;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public String getCalculatedBy() {
        return calculatedBy;
    }

    public void setCalculatedBy(String calculatedBy) {
        this.calculatedBy = calculatedBy;
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    public void setRulesVersion(String rulesVersion) {
        this.rulesVersion = rulesVersion;
    }

    public static MerchantRiskScoreBuilder builder() {
        return new MerchantRiskScoreBuilder();
    }

    public static class MerchantRiskScoreBuilder {
        private Long scoreId;
        private Merchant merchant;
        private Integer totalScore;
        private String riskLevel;
        private Integer sanctionsScore;
        private Integer pepScore;
        private Integer countryRiskScore;
        private Integer industryRiskScore;
        private Integer volumeRiskScore;
        private Integer businessAgeScore;
        private String decision;
        private String decisionReason;
        private LocalDateTime calculatedAt = LocalDateTime.now();
        private String calculatedBy;
        private String rulesVersion;

        MerchantRiskScoreBuilder() {
        }

        public MerchantRiskScoreBuilder scoreId(Long scoreId) {
            this.scoreId = scoreId;
            return this;
        }

        public MerchantRiskScoreBuilder merchant(Merchant merchant) {
            this.merchant = merchant;
            return this;
        }

        public MerchantRiskScoreBuilder totalScore(Integer totalScore) {
            this.totalScore = totalScore;
            return this;
        }

        public MerchantRiskScoreBuilder riskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public MerchantRiskScoreBuilder sanctionsScore(Integer sanctionsScore) {
            this.sanctionsScore = sanctionsScore;
            return this;
        }

        public MerchantRiskScoreBuilder pepScore(Integer pepScore) {
            this.pepScore = pepScore;
            return this;
        }

        public MerchantRiskScoreBuilder countryRiskScore(Integer countryRiskScore) {
            this.countryRiskScore = countryRiskScore;
            return this;
        }

        public MerchantRiskScoreBuilder industryRiskScore(Integer industryRiskScore) {
            this.industryRiskScore = industryRiskScore;
            return this;
        }

        public MerchantRiskScoreBuilder volumeRiskScore(Integer volumeRiskScore) {
            this.volumeRiskScore = volumeRiskScore;
            return this;
        }

        public MerchantRiskScoreBuilder businessAgeScore(Integer businessAgeScore) {
            this.businessAgeScore = businessAgeScore;
            return this;
        }

        public MerchantRiskScoreBuilder decision(String decision) {
            this.decision = decision;
            return this;
        }

        public MerchantRiskScoreBuilder decisionReason(String decisionReason) {
            this.decisionReason = decisionReason;
            return this;
        }

        public MerchantRiskScoreBuilder calculatedAt(LocalDateTime calculatedAt) {
            this.calculatedAt = calculatedAt;
            return this;
        }

        public MerchantRiskScoreBuilder calculatedBy(String calculatedBy) {
            this.calculatedBy = calculatedBy;
            return this;
        }

        public MerchantRiskScoreBuilder rulesVersion(String rulesVersion) {
            this.rulesVersion = rulesVersion;
            return this;
        }

        public MerchantRiskScore build() {
            return new MerchantRiskScore(scoreId, merchant, totalScore, riskLevel, sanctionsScore, pepScore,
                    countryRiskScore, industryRiskScore, volumeRiskScore, businessAgeScore, decision, decisionReason,
                    calculatedAt, calculatedBy, rulesVersion);
        }

        public String toString() {
            return "MerchantRiskScore.MerchantRiskScoreBuilder(scoreId=" + this.scoreId + ", merchant=" + this.merchant
                    + ", totalScore=" + this.totalScore + ", riskLevel=" + this.riskLevel + ", sanctionsScore="
                    + this.sanctionsScore + ", pepScore=" + this.pepScore + ", countryRiskScore="
                    + this.countryRiskScore + ", industryRiskScore=" + this.industryRiskScore + ", volumeRiskScore="
                    + this.volumeRiskScore + ", businessAgeScore=" + this.businessAgeScore + ", decision="
                    + this.decision + ", decisionReason=" + this.decisionReason + ", calculatedAt=" + this.calculatedAt
                    + ", calculatedBy=" + this.calculatedBy + ", rulesVersion=" + this.rulesVersion + ")";
        }
    }
}
