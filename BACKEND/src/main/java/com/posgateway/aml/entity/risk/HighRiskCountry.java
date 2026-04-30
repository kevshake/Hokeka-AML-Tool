package com.posgateway.aml.entity.risk;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * High Risk Country Entity
 * Stores countries identified as high risk for AML/CTF
 */
@Entity
@Table(name = "high_risk_countries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HighRiskCountry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 3, unique = true)
    private String countryCode;

    @Column(name = "country_name", length = 100)
    private String countryName;

    @Builder.Default
    @Column(name = "risk_level")
    private String riskLevel = "HIGH"; // HIGH, CRITICAL

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Column(name = "added_by")
    private String addedBy;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }

    // Explicit accessors for compatibility
    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }
}
