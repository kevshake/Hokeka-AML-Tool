package com.posgateway.aml.entity.billing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Currency Rate Entity
 * Stores exchange rates for multi-currency billing
 */
@Entity
@Table(name = "currency_rates")
public class CurrencyRate {

    @Id
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "currency_name", length = 50)
    private String currencyName;

    @Column(name = "rate_to_usd", precision = 12, scale = 6, nullable = false)
    private BigDecimal rateToUsd;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "rate_source", length = 160)
    private String rateSource;

    @Column(name = "effective_at")
    private LocalDateTime effectiveAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "regulatory_approved", nullable = false)
    private boolean regulatoryApproved;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // Constructors
    public CurrencyRate() {
    }

    public CurrencyRate(String currencyCode, BigDecimal rateToUsd) {
        this.currencyCode = currencyCode;
        this.rateToUsd = rateToUsd;
    }

    // Business methods

    /**
     * Convert amount from USD to this currency
     */
    public BigDecimal fromUsd(BigDecimal usdAmount) {
        if (rateToUsd == null || rateToUsd.compareTo(BigDecimal.ZERO) == 0) {
            return usdAmount;
        }
        return usdAmount.divide(rateToUsd, 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Convert amount from this currency to USD
     */
    public BigDecimal toUsd(BigDecimal localAmount) {
        if (rateToUsd == null) {
            return localAmount;
        }
        return localAmount.multiply(rateToUsd);
    }

    // Getters and Setters
    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public void setCurrencyName(String currencyName) {
        this.currencyName = currencyName;
    }

    public BigDecimal getRateToUsd() {
        return rateToUsd;
    }

    public void setRateToUsd(BigDecimal rateToUsd) {
        this.rateToUsd = rateToUsd;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRateSource() {
        return rateSource;
    }

    public void setRateSource(String rateSource) {
        this.rateSource = rateSource;
    }

    public LocalDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(LocalDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRegulatoryApproved() {
        return regulatoryApproved;
    }

    public void setRegulatoryApproved(boolean regulatoryApproved) {
        this.regulatoryApproved = regulatoryApproved;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
