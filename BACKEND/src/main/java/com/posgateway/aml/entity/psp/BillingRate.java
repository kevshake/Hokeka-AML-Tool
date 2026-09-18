package com.posgateway.aml.entity.psp;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Billing Rate Entity
 * Configurable pricing for different services
 */
@Entity
@Table(name = "billing_rates")
public class BillingRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rate_id")
    private Long rateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "psp_id")
    private Psp psp; // NULL = default rate for all PSPs

    // Service Pricing
    @Column(name = "service_type", nullable = false, length = 100)
    private String serviceType;

    @Column(name = "pricing_model", nullable = false, length = 50)
    private String pricingModel; // PER_REQUEST, TIERED, SUBSCRIPTION, HYBRID

    // Per-Request Pricing
    @Column(name = "base_rate", precision = 10, scale = 4)
    private BigDecimal baseRate;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    // Tiered Pricing (JSON configuration)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tier_config", columnDefinition = "jsonb")
    private Map<String, Object> tierConfig;

    // Subscription Pricing
    @Column(name = "monthly_fee", precision = 10, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "included_requests")
    private Integer includedRequests;

    @Column(name = "overage_rate", precision = 10, scale = 4)
    private BigDecimal overageRate;

    // Validity Period
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Metadata
    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public BillingRate() {
    }

    public BillingRate(Long rateId, Psp psp, String serviceType, String pricingModel, BigDecimal baseRate,
            String currency, Map<String, Object> tierConfig, BigDecimal monthlyFee, Integer includedRequests,
            BigDecimal overageRate, LocalDate effectiveFrom, LocalDate effectiveTo, Boolean isActive,
            String description, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.rateId = rateId;
        this.psp = psp;
        this.serviceType = serviceType;
        this.pricingModel = pricingModel;
        this.baseRate = baseRate;
        this.currency = currency != null ? currency : "USD";
        this.tierConfig = tierConfig;
        this.monthlyFee = monthlyFee;
        this.includedRequests = includedRequests;
        this.overageRate = overageRate;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.isActive = isActive != null ? isActive : true;
        this.description = description;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public Long getRateId() {
        return rateId;
    }

    public void setRateId(Long rateId) {
        this.rateId = rateId;
    }

    public Psp getPsp() {
        return psp;
    }

    public void setPsp(Psp psp) {
        this.psp = psp;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getPricingModel() {
        return pricingModel;
    }

    public void setPricingModel(String pricingModel) {
        this.pricingModel = pricingModel;
    }

    public BigDecimal getBaseRate() {
        return baseRate;
    }

    public void setBaseRate(BigDecimal baseRate) {
        this.baseRate = baseRate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Map<String, Object> getTierConfig() {
        return tierConfig;
    }

    public void setTierConfig(Map<String, Object> tierConfig) {
        this.tierConfig = tierConfig;
    }

    public BigDecimal getMonthlyFee() {
        return monthlyFee;
    }

    public void setMonthlyFee(BigDecimal monthlyFee) {
        this.monthlyFee = monthlyFee;
    }

    public Integer getIncludedRequests() {
        return includedRequests;
    }

    public void setIncludedRequests(Integer includedRequests) {
        this.includedRequests = includedRequests;
    }

    public BigDecimal getOverageRate() {
        return overageRate;
    }

    public void setOverageRate(BigDecimal overageRate) {
        this.overageRate = overageRate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    // Helper Methods
    public boolean isEffective(LocalDate date) {
        if (!isActive)
            return false;
        if (date.isBefore(effectiveFrom))
            return false;
        if (effectiveTo != null && date.isAfter(effectiveTo))
            return false;
        return true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static BillingRateBuilder builder() {
        return new BillingRateBuilder();
    }

    public static class BillingRateBuilder {
        private Long rateId;
        private Psp psp;
        private String serviceType;
        private String pricingModel;
        private BigDecimal baseRate;
        private String currency = "USD";
        private Map<String, Object> tierConfig;
        private BigDecimal monthlyFee;
        private Integer includedRequests;
        private BigDecimal overageRate;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private Boolean isActive = true;
        private String description;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        BillingRateBuilder() {
        }

        public BillingRateBuilder rateId(Long rateId) {
            this.rateId = rateId;
            return this;
        }

        public BillingRateBuilder psp(Psp psp) {
            this.psp = psp;
            return this;
        }

        public BillingRateBuilder serviceType(String serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        public BillingRateBuilder pricingModel(String pricingModel) {
            this.pricingModel = pricingModel;
            return this;
        }

        public BillingRateBuilder baseRate(BigDecimal baseRate) {
            this.baseRate = baseRate;
            return this;
        }

        public BillingRateBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public BillingRateBuilder tierConfig(Map<String, Object> tierConfig) {
            this.tierConfig = tierConfig;
            return this;
        }

        public BillingRateBuilder monthlyFee(BigDecimal monthlyFee) {
            this.monthlyFee = monthlyFee;
            return this;
        }

        public BillingRateBuilder includedRequests(Integer includedRequests) {
            this.includedRequests = includedRequests;
            return this;
        }

        public BillingRateBuilder overageRate(BigDecimal overageRate) {
            this.overageRate = overageRate;
            return this;
        }

        public BillingRateBuilder effectiveFrom(LocalDate effectiveFrom) {
            this.effectiveFrom = effectiveFrom;
            return this;
        }

        public BillingRateBuilder effectiveTo(LocalDate effectiveTo) {
            this.effectiveTo = effectiveTo;
            return this;
        }

        public BillingRateBuilder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public BillingRateBuilder description(String description) {
            this.description = description;
            return this;
        }

        public BillingRateBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public BillingRateBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public BillingRate build() {
            return new BillingRate(rateId, psp, serviceType, pricingModel, baseRate, currency, tierConfig, monthlyFee,
                    includedRequests, overageRate, effectiveFrom, effectiveTo, isActive, description, createdAt,
                    updatedAt);
        }

        public String toString() {
            return "BillingRate.BillingRateBuilder(rateId=" + this.rateId + ", psp=" + this.psp + ", serviceType="
                    + this.serviceType + ", pricingModel=" + this.pricingModel + ", baseRate=" + this.baseRate
                    + ", currency=" + this.currency + ", tierConfig=" + this.tierConfig + ", monthlyFee="
                    + this.monthlyFee + ", includedRequests=" + this.includedRequests + ", overageRate="
                    + this.overageRate + ", effectiveFrom=" + this.effectiveFrom + ", effectiveTo=" + this.effectiveTo
                    + ", isActive=" + this.isActive + ", description=" + this.description + ", createdAt="
                    + this.createdAt + ", updatedAt=" + this.updatedAt + ")";
        }
    }
}
