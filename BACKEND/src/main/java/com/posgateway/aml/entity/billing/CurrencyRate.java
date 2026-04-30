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

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
