package com.posgateway.aml.entity.residency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One Edge limit (table {@code edge_limits}, V261), distributed in the Edge config document's
 * {@code limits[]} section. The row id is the contract's {@code limitId}.
 */
@Entity
@Table(name = "edge_limits")
public class EdgeLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    /** CARD, MERCHANT or PSP. */
    @Column(name = "scope", nullable = false, length = 16)
    private String scope;

    /** COUNT or AMOUNT. */
    @Column(name = "metric", nullable = false, length = 16)
    private String metric;

    /** ISO 8601 window: PT1H, P1D, P7D or P30D. */
    @Column(name = "window_code", nullable = false, length = 8)
    private String windowCode;

    /** Required for AMOUNT limits. */
    @Column(name = "currency", length = 3)
    private String currency;

    /** Count, or amount in minor units. */
    @Column(name = "max_value", nullable = false)
    private Long maxValue;

    /** ALERT, HOLD or BLOCK. */
    @Column(name = "action", nullable = false, length = 8)
    private String action;

    @Column(name = "merchant_id", length = 64)
    private String merchantId;

    @Column(name = "mcc", length = 4)
    private String mcc;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Row id = contract limitId. */
    public Long getId() { return id; }

    /** Sets the row id (tests only). */
    public void setId(Long id) { this.id = id; }

    /** Owning PSP. */
    public Long getPspId() { return pspId; }

    /** Sets the owning PSP. */
    public void setPspId(Long pspId) { this.pspId = pspId; }

    /** Limit scope. */
    public String getScope() { return scope; }

    /** Sets the limit scope. */
    public void setScope(String scope) { this.scope = scope; }

    /** Limit metric. */
    public String getMetric() { return metric; }

    /** Sets the limit metric. */
    public void setMetric(String metric) { this.metric = metric; }

    /** Limit window code. */
    public String getWindowCode() { return windowCode; }

    /** Sets the limit window code. */
    public void setWindowCode(String windowCode) { this.windowCode = windowCode; }

    /** Currency of an AMOUNT limit. */
    public String getCurrency() { return currency; }

    /** Sets the currency of an AMOUNT limit. */
    public void setCurrency(String currency) { this.currency = currency; }

    /** Maximum count or minor-unit amount. */
    public Long getMaxValue() { return maxValue; }

    /** Sets the maximum count or minor-unit amount. */
    public void setMaxValue(Long maxValue) { this.maxValue = maxValue; }

    /** Action when the limit is exceeded. */
    public String getAction() { return action; }

    /** Sets the action when the limit is exceeded. */
    public void setAction(String action) { this.action = action; }

    /** Merchant the limit is restricted to, if any. */
    public String getMerchantId() { return merchantId; }

    /** Restricts the limit to one merchant. */
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    /** MCC the limit is restricted to, if any. */
    public String getMcc() { return mcc; }

    /** Restricts the limit to one MCC. */
    public void setMcc(String mcc) { this.mcc = mcc; }

    /** Whether the limit is distributed. */
    public boolean isActive() { return active; }

    /** Activates or retires the limit. */
    public void setActive(boolean active) { this.active = active; }

    /** Creation time. */
    public Instant getCreatedAt() { return createdAt; }

    /** Sets the creation time. */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** Creator username. */
    public String getCreatedBy() { return createdBy; }

    /** Sets the creator username. */
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    /** Last change time. */
    public Instant getUpdatedAt() { return updatedAt; }

    /** Sets the last change time. */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
