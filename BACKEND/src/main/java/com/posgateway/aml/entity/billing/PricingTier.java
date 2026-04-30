package com.posgateway.aml.entity.billing;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pricing Tier Entity
 * Defines subscription tiers with pricing and features
 */
@Entity
@Table(name = "pricing_tiers")
public class PricingTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tier_id")
    private Integer tierId;

    @Column(name = "tier_code", unique = true, nullable = false, length = 20)
    private String tierCode;

    @Column(name = "tier_name", nullable = false, length = 100)
    private String tierName;

    @Column(name = "monthly_fee_usd", precision = 10, scale = 2)
    private BigDecimal monthlyFeeUsd = BigDecimal.ZERO;

    @Column(name = "per_check_price_usd", precision = 10, scale = 4, nullable = false)
    private BigDecimal perCheckPriceUsd;

    @Column(name = "monthly_minimum_usd", precision = 10, scale = 2)
    private BigDecimal monthlyMinimumUsd;

    @Column(name = "max_checks_per_month")
    private Integer maxChecksPerMonth;

    @Column(name = "included_checks")
    private Integer includedChecks = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "volume_discounts", columnDefinition = "jsonb")
    private Map<String, Object> volumeDiscounts = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb")
    private List<String> features;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Constructors
    public PricingTier() {
    }

    // Business methods

    /**
     * Get volume discounts as a map of threshold to discount percentage
     */
    public Map<Integer, BigDecimal> getVolumeDiscountsMap() {
        Map<Integer, BigDecimal> result = new HashMap<>();
        if (volumeDiscounts != null) {
            volumeDiscounts.forEach((key, value) -> {
                try {
                    int threshold = Integer.parseInt(key);
                    BigDecimal discount = new BigDecimal(value.toString());
                    result.put(threshold, discount);
                } catch (NumberFormatException e) {
                    // Skip invalid entries
                }
            });
        }
        return result;
    }

    /**
     * Calculate effective rate for given volume with discounts applied
     */
    public BigDecimal getEffectiveRate(int volume) {
        BigDecimal discount = getApplicableDiscount(volume);
        return perCheckPriceUsd.multiply(BigDecimal.ONE.subtract(discount));
    }

    /**
     * Get applicable discount percentage for given volume
     */
    public BigDecimal getApplicableDiscount(int volume) {
        Map<Integer, BigDecimal> bands = getVolumeDiscountsMap();
        BigDecimal maxDiscount = BigDecimal.ZERO;

        for (Map.Entry<Integer, BigDecimal> entry : bands.entrySet()) {
            if (volume >= entry.getKey() && entry.getValue().compareTo(maxDiscount) > 0) {
                maxDiscount = entry.getValue();
            }
        }
        return maxDiscount;
    }

    // Getters and Setters
    public Integer getTierId() {
        return tierId;
    }

    public void setTierId(Integer tierId) {
        this.tierId = tierId;
    }

    public String getTierCode() {
        return tierCode;
    }

    public void setTierCode(String tierCode) {
        this.tierCode = tierCode;
    }

    public String getTierName() {
        return tierName;
    }

    public void setTierName(String tierName) {
        this.tierName = tierName;
    }

    public BigDecimal getMonthlyFeeUsd() {
        return monthlyFeeUsd;
    }

    public void setMonthlyFeeUsd(BigDecimal monthlyFeeUsd) {
        this.monthlyFeeUsd = monthlyFeeUsd;
    }

    public BigDecimal getPerCheckPriceUsd() {
        return perCheckPriceUsd;
    }

    public void setPerCheckPriceUsd(BigDecimal perCheckPriceUsd) {
        this.perCheckPriceUsd = perCheckPriceUsd;
    }

    public BigDecimal getMonthlyMinimumUsd() {
        return monthlyMinimumUsd;
    }

    public void setMonthlyMinimumUsd(BigDecimal monthlyMinimumUsd) {
        this.monthlyMinimumUsd = monthlyMinimumUsd;
    }

    public Integer getMaxChecksPerMonth() {
        return maxChecksPerMonth;
    }

    public void setMaxChecksPerMonth(Integer maxChecksPerMonth) {
        this.maxChecksPerMonth = maxChecksPerMonth;
    }

    public Integer getIncludedChecks() {
        return includedChecks;
    }

    public void setIncludedChecks(Integer includedChecks) {
        this.includedChecks = includedChecks;
    }

    public Map<String, Object> getVolumeDiscounts() {
        return volumeDiscounts;
    }

    public void setVolumeDiscounts(Map<String, Object> volumeDiscounts) {
        this.volumeDiscounts = volumeDiscounts;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
