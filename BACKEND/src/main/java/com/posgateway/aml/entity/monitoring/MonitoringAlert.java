package com.posgateway.aml.entity.monitoring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.posgateway.aml.entity.merchant.Merchant;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Alert raised by periodic rescreening when merchant risk status changes.
 * Persisted to {@code monitoring_alerts} (distinct from transaction-scoring {@code alerts}).
 */
@Entity
@Table(name = "monitoring_alerts", indexes = {
        @Index(name = "idx_monitoring_alerts_merchant", columnList = "merchant_id"),
        @Index(name = "idx_monitoring_alerts_created", columnList = "created_at")
})
public class MonitoringAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long alertId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "alert_type", nullable = false, length = 100)
    private String alertType;

    @Column(name = "alert_severity", nullable = false, length = 20)
    private String alertSeverity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alert_details", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> alertDetails;

    @Column(name = "acknowledged")
    private Boolean acknowledged = false;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolution", length = 50)
    private String resolution;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (acknowledged == null) {
            acknowledged = false;
        }
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    @JsonIgnore
    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public Long getMerchantId() {
        return merchant != null ? merchant.getMerchantId() : null;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getAlertSeverity() {
        return alertSeverity;
    }

    public void setAlertSeverity(String alertSeverity) {
        this.alertSeverity = alertSeverity;
    }

    public Map<String, Object> getAlertDetails() {
        return alertDetails;
    }

    public void setAlertDetails(Map<String, Object> alertDetails) {
        this.alertDetails = alertDetails;
    }

    public Boolean getAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(Boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public String getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(String acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
