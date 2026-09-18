package com.posgateway.aml.entity.billing;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Billing Calculation Entity
 * Stores calculated billing records for audit trail
 */
@Entity
@Table(name = "billing_calculations")
public class BillingCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calculation_id")
    private Long calculationId;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "tier_code", length = 20)
    private String tierCode;

    @Column(name = "subscription_fee", precision = 10, scale = 2)
    private BigDecimal subscriptionFee = BigDecimal.ZERO;

    @Column(name = "check_count")
    private Integer checkCount = 0;

    @Column(name = "base_usage_cost", precision = 10, scale = 2)
    private BigDecimal baseUsageCost = BigDecimal.ZERO;

    @Column(name = "volume_discount_amount", precision = 10, scale = 2)
    private BigDecimal volumeDiscountAmount = BigDecimal.ZERO;

    @Column(name = "total_usage_cost", precision = 10, scale = 2)
    private BigDecimal totalUsageCost = BigDecimal.ZERO;

    @Column(name = "minimum_adjustment", precision = 10, scale = 2)
    private BigDecimal minimumAdjustment = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cost_metrics_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> costMetricsSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_details", columnDefinition = "jsonb")
    private Map<String, Object> calculationDetails;

    @Column(name = "status", length = 20)
    private String status = "CALCULATED";

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt = LocalDateTime.now();

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructors
    public BillingCalculation() {
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final BillingCalculation calc = new BillingCalculation();

        public Builder pspId(Long pspId) {
            calc.pspId = pspId;
            return this;
        }

        public Builder periodStart(LocalDate periodStart) {
            calc.periodStart = periodStart;
            return this;
        }

        public Builder periodEnd(LocalDate periodEnd) {
            calc.periodEnd = periodEnd;
            return this;
        }

        public Builder tierCode(String tierCode) {
            calc.tierCode = tierCode;
            return this;
        }

        public Builder subscriptionFee(BigDecimal fee) {
            calc.subscriptionFee = fee;
            return this;
        }

        public Builder checkCount(Integer count) {
            calc.checkCount = count;
            return this;
        }

        public Builder baseUsageCost(BigDecimal cost) {
            calc.baseUsageCost = cost;
            return this;
        }

        public Builder volumeDiscountAmount(BigDecimal discount) {
            calc.volumeDiscountAmount = discount;
            return this;
        }

        public Builder totalUsageCost(BigDecimal cost) {
            calc.totalUsageCost = cost;
            return this;
        }

        public Builder minimumAdjustment(BigDecimal adj) {
            calc.minimumAdjustment = adj;
            return this;
        }

        public Builder totalAmount(BigDecimal total) {
            calc.totalAmount = total;
            return this;
        }

        public Builder currency(String currency) {
            calc.currency = currency;
            return this;
        }

        public Builder costMetricsSnapshot(Map<String, Object> snapshot) {
            calc.costMetricsSnapshot = snapshot;
            return this;
        }

        public Builder calculationDetails(Map<String, Object> details) {
            calc.calculationDetails = details;
            return this;
        }

        public BillingCalculation build() {
            return calc;
        }
    }

    // Getters and Setters
    public Long getCalculationId() {
        return calculationId;
    }

    public void setCalculationId(Long calculationId) {
        this.calculationId = calculationId;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public String getTierCode() {
        return tierCode;
    }

    public void setTierCode(String tierCode) {
        this.tierCode = tierCode;
    }

    public BigDecimal getSubscriptionFee() {
        return subscriptionFee;
    }

    public void setSubscriptionFee(BigDecimal subscriptionFee) {
        this.subscriptionFee = subscriptionFee;
    }

    public Integer getCheckCount() {
        return checkCount;
    }

    public void setCheckCount(Integer checkCount) {
        this.checkCount = checkCount;
    }

    public BigDecimal getBaseUsageCost() {
        return baseUsageCost;
    }

    public void setBaseUsageCost(BigDecimal baseUsageCost) {
        this.baseUsageCost = baseUsageCost;
    }

    public BigDecimal getVolumeDiscountAmount() {
        return volumeDiscountAmount;
    }

    public void setVolumeDiscountAmount(BigDecimal volumeDiscountAmount) {
        this.volumeDiscountAmount = volumeDiscountAmount;
    }

    public BigDecimal getTotalUsageCost() {
        return totalUsageCost;
    }

    public void setTotalUsageCost(BigDecimal totalUsageCost) {
        this.totalUsageCost = totalUsageCost;
    }

    public BigDecimal getMinimumAdjustment() {
        return minimumAdjustment;
    }

    public void setMinimumAdjustment(BigDecimal minimumAdjustment) {
        this.minimumAdjustment = minimumAdjustment;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Map<String, Object> getCostMetricsSnapshot() {
        return costMetricsSnapshot;
    }

    public void setCostMetricsSnapshot(Map<String, Object> costMetricsSnapshot) {
        this.costMetricsSnapshot = costMetricsSnapshot;
    }

    public Map<String, Object> getCalculationDetails() {
        return calculationDetails;
    }

    public void setCalculationDetails(Map<String, Object> calculationDetails) {
        this.calculationDetails = calculationDetails;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
