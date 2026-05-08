package com.posgateway.aml.entity.risk;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Country risk score entity backing the {@code country_risk_scores} table.
 *
 * <p>Stores a normalised 0.0–1.0 risk score per ISO-3166 alpha-2 country
 * code, e.g. derived from the FATF high-risk list, EU AML high-risk list,
 * or internal country-risk policy. The migration that creates this table
 * is owned by the migration agent (see report).
 */
@Entity
@Table(name = "country_risk_scores", indexes = {
        @Index(name = "idx_country_risk_code", columnList = "country_code", unique = true)
})
public class CountryRiskScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, unique = true, length = 3)
    private String countryCode;

    @Column(name = "country_name", length = 128)
    private String countryName;

    /** 0.0 (low risk) to 1.0 (highest risk). */
    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    /** e.g. FATF, EU_HIGH_RISK, INTERNAL */
    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CountryRiskScore() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
