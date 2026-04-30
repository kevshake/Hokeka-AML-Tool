package com.posgateway.aml.entity.limits;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Global Limits Entity
 * Stores system-wide transaction limits
 */
@Entity
@Table(name = "global_limits", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Data
public class GlobalLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "limit_type", nullable = false, length = 50)
    private String limitType; // VOLUME, COUNT, VELOCITY

    @Column(name = "limit_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal limitValue;

    @Column(name = "period", nullable = false, length = 20)
    private String period; // DAY, HOUR, MINUTE, WEEK, MONTH

    @Column(name = "current_usage", precision = 19, scale = 2)
    private BigDecimal currentUsage = BigDecimal.ZERO;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "ACTIVE";
        }
        if (currentUsage == null) {
            currentUsage = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate usage percentage
     */
    public double getUsagePercentage() {
        if (limitValue == null || limitValue.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        if (currentUsage == null) {
            return 0.0;
        }
        return currentUsage.divide(limitValue, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    // Explicit accessors for build environments without Lombok processing
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLimitType() {
        return limitType;
    }

    public void setLimitType(String limitType) {
        this.limitType = limitType;
    }

    public BigDecimal getLimitValue() {
        return limitValue;
    }

    public void setLimitValue(BigDecimal limitValue) {
        this.limitValue = limitValue;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public BigDecimal getCurrentUsage() {
        return currentUsage;
    }
}

