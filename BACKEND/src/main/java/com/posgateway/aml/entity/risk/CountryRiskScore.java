package com.posgateway.aml.entity.risk;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Country risk score entity backing the {@code country_risk_scores} table.
 *
 * <p>The natural primary key is the ISO 3166-1 alpha-2 country code. The
 * {@code risk_score} column is an integer 0-100 (FATF-style tiering, higher
 * = riskier); legacy callers that work with a 0.0-1.0 scale derive it from
 * the tier rather than persisting a duplicate column.
 *
 * <p>Created by V130__deferred_phase6_tables.sql.
 */
@Entity
@Table(name = "country_risk_scores")
public class CountryRiskScore {

    @Id
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "country_name", nullable = false, length = 255)
    private String countryName;

    /** 0-100 (higher = riskier). */
    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    /** LOW / MEDIUM / HIGH / VERY_HIGH */
    @Column(name = "risk_tier", nullable = false, length = 16)
    private String riskTier;

    @Column(name = "fatf_listed", nullable = false)
    private Boolean fatfListed = false;

    /** BLACKLIST / GREYLIST / ENHANCED_DD / null */
    @Column(name = "fatf_status", length = 64)
    private String fatfStatus;

    @Column(name = "last_reviewed_at", nullable = false)
    private OffsetDateTime lastReviewedAt = OffsetDateTime.now();

    @Column(name = "source", nullable = false, length = 64)
    private String source = "FATF";

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public CountryRiskScore() {}

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public String getRiskTier() { return riskTier; }
    public void setRiskTier(String riskTier) { this.riskTier = riskTier; }
    public Boolean getFatfListed() { return fatfListed; }
    public void setFatfListed(Boolean fatfListed) { this.fatfListed = fatfListed; }
    public String getFatfStatus() { return fatfStatus; }
    public void setFatfStatus(String fatfStatus) { this.fatfStatus = fatfStatus; }
    public OffsetDateTime getLastReviewedAt() { return lastReviewedAt; }
    public void setLastReviewedAt(OffsetDateTime lastReviewedAt) { this.lastReviewedAt = lastReviewedAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
