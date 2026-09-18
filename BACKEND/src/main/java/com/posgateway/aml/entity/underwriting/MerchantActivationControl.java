package com.posgateway.aml.entity.underwriting;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Progressive-activation controls applied to a merchant as a result of underwriting.
 *
 * <p>A merchant approved with controls (or in EDD) is not given full capability on day one:
 * this row records the graduated state, the controls in force, the reduced daily limit
 * actually applied during the monitoring window, and when heightened monitoring ends —
 * so the restriction is both enforced (via the reduced limit) and auditable.
 */
@Entity
@Table(name = "merchant_activation_controls",
        indexes = @Index(name = "idx_mac_merchant", columnList = "merchant_id"))
public class MerchantActivationControl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    /** Graduated state: NORMAL / WATCH / RESTRICTED. */
    @Column(name = "state", nullable = false, length = 24)
    private String state;

    /** Comma-separated controls in force (e.g. LOWER_TRANSACTION_LIMITS,DELAYED_SETTLEMENT). */
    @Column(name = "controls", columnDefinition = "text")
    private String controls;

    @Column(name = "original_daily_limit", precision = 19, scale = 2)
    private BigDecimal originalDailyLimit;

    @Column(name = "reduced_daily_limit", precision = 19, scale = 2)
    private BigDecimal reducedDailyLimit;

    /** Heightened-monitoring window end; after this the merchant can be graduated up. */
    @Column(name = "monitoring_until")
    private LocalDateTime monitoringUntil;

    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getControls() { return controls; }
    public void setControls(String controls) { this.controls = controls; }
    public BigDecimal getOriginalDailyLimit() { return originalDailyLimit; }
    public void setOriginalDailyLimit(BigDecimal originalDailyLimit) { this.originalDailyLimit = originalDailyLimit; }
    public BigDecimal getReducedDailyLimit() { return reducedDailyLimit; }
    public void setReducedDailyLimit(BigDecimal reducedDailyLimit) { this.reducedDailyLimit = reducedDailyLimit; }
    public LocalDateTime getMonitoringUntil() { return monitoringUntil; }
    public void setMonitoringUntil(LocalDateTime monitoringUntil) { this.monitoringUntil = monitoringUntil; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
