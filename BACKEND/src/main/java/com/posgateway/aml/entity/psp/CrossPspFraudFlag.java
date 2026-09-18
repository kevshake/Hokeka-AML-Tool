package com.posgateway.aml.entity.psp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Cross-PSP Fraud Flag — shares fraud intelligence across all PSPs.
 *
 * <p>When a merchant is confirmed as fraudulent by one PSP (via alert
 * disposition TRUE_POSITIVE), a flag is created here. Other PSPs' transaction
 * screening checks this table and can block/flag transactions involving the
 * same merchant, PAN, terminal, email, or name.
 *
 * <p>{@code entity_value} stores the actual identifier (e.g. merchant "MERCH-123",
 * PAN hash "a1b2c3...", email "fraud@example.com") and {@code entity_type}
 * distinguishes the type.
 */
@Entity
@Table(name = "cross_psp_fraud_flags", indexes = {
        @Index(name = "idx_cross_psp_entity_value", columnList = "entity_value"),
        @Index(name = "idx_cross_psp_entity_type",  columnList = "entity_type"),
        @Index(name = "idx_cross_psp_risk_level",   columnList = "risk_level")
})
public class CrossPspFraudFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_value", nullable = false)
    private String entityValue;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType; // MERCHANT_ID, PAN_HASH, TERMINAL, EMAIL, NAME

    @Column(name = "flag_count", nullable = false)
    private Integer flagCount = 1;

    @Column(name = "source_psp_id")
    private Long sourcePspId;

    @Column(name = "source_alert_id")
    private Long sourceAlertId;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel = "HIGH";

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "first_flagged", nullable = false)
    private LocalDateTime firstFlagged;

    @Column(name = "last_flagged", nullable = false)
    private LocalDateTime lastFlagged;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public CrossPspFraudFlag() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (firstFlagged == null) firstFlagged = LocalDateTime.now();
        if (lastFlagged == null) lastFlagged = LocalDateTime.now();
    }

    // ── Builder ──────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CrossPspFraudFlag flag = new CrossPspFraudFlag();

        public Builder entityValue(String v) { flag.entityValue = v; return this; }
        public Builder entityType(String v) { flag.entityType = v; return this; }
        public Builder flagCount(Integer v) { flag.flagCount = v; return this; }
        public Builder sourcePspId(Long v) { flag.sourcePspId = v; return this; }
        public Builder sourceAlertId(Long v) { flag.sourceAlertId = v; return this; }
        public Builder riskLevel(String v) { flag.riskLevel = v; return this; }
        public Builder reason(String v) { flag.reason = v; return this; }

        public CrossPspFraudFlag build() { return flag; }
    }

    // ── Getters / Setters ────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getEntityValue() { return entityValue; }
    public String getEntityType() { return entityType; }
    public Integer getFlagCount() { return flagCount; }
    public Long getSourcePspId() { return sourcePspId; }
    public Long getSourceAlertId() { return sourceAlertId; }
    public String getRiskLevel() { return riskLevel; }
    public String getReason() { return reason; }
    public LocalDateTime getFirstFlagged() { return firstFlagged; }
    public LocalDateTime getLastFlagged() { return lastFlagged; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setEntityValue(String entityValue) { this.entityValue = entityValue; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public void setFlagCount(Integer flagCount) { this.flagCount = flagCount; }
    public void setSourcePspId(Long sourcePspId) { this.sourcePspId = sourcePspId; }
    public void setSourceAlertId(Long sourceAlertId) { this.sourceAlertId = sourceAlertId; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setReason(String reason) { this.reason = reason; }
    public void setFirstFlagged(LocalDateTime firstFlagged) { this.firstFlagged = firstFlagged; }
    public void setLastFlagged(LocalDateTime lastFlagged) { this.lastFlagged = lastFlagged; }
}