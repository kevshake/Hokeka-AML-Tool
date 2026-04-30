package com.posgateway.aml.entity.billing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cost Metrics Entity
 * Stores cost metrics for billing calculations
 * The billing engine reads from this table
 */
@Entity
@Table(name = "cost_metrics")
public class CostMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Long metricId;

    @Column(name = "metric_date", unique = true, nullable = false)
    private LocalDate metricDate;

    @Column(name = "fixed_costs_monthly", precision = 12, scale = 2)
    private BigDecimal fixedCostsMonthly;

    @Column(name = "variable_cost_per_check", precision = 10, scale = 4)
    private BigDecimal variableCostPerCheck;

    @Column(name = "manual_review_cost", precision = 10, scale = 4)
    private BigDecimal manualReviewCost;

    @Column(name = "data_feed_cost", precision = 10, scale = 4)
    private BigDecimal dataFeedCost;

    @Column(name = "target_margin", precision = 5, scale = 4)
    private BigDecimal targetMargin;

    @Column(name = "actual_margin", precision = 5, scale = 4)
    private BigDecimal actualMargin;

    @Column(name = "total_checks_processed")
    private Long totalChecksProcessed = 0L;

    @Column(name = "total_revenue", precision = 12, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_costs", precision = 12, scale = 2)
    private BigDecimal totalCosts = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Constructors
    public CostMetrics() {
    }

    // Business methods

    /**
     * Calculate total variable cost per check
     */
    public BigDecimal getTotalVariableCost() {
        BigDecimal total = BigDecimal.ZERO;
        if (variableCostPerCheck != null)
            total = total.add(variableCostPerCheck);
        if (dataFeedCost != null)
            total = total.add(dataFeedCost);
        return total;
    }

    /**
     * Calculate minimum viable price per check based on target margin
     */
    public BigDecimal getMinimumViablePrice() {
        if (targetMargin == null || targetMargin.compareTo(BigDecimal.ONE) >= 0) {
            return getTotalVariableCost();
        }
        // Price = Variable Cost / (1 - Target Margin)
        return getTotalVariableCost().divide(
                BigDecimal.ONE.subtract(targetMargin),
                4,
                java.math.RoundingMode.CEILING);
    }

    /**
     * Convert to JSON for audit trail
     */
    public String toJson() {
        return String.format(
                "{\"metricDate\":\"%s\",\"fixedCosts\":%.2f,\"variableCost\":%.4f,\"targetMargin\":%.4f}",
                metricDate, fixedCostsMonthly, getTotalVariableCost(), targetMargin);
    }

    // Getters and Setters
    public Long getMetricId() {
        return metricId;
    }

    public void setMetricId(Long metricId) {
        this.metricId = metricId;
    }

    public LocalDate getMetricDate() {
        return metricDate;
    }

    public void setMetricDate(LocalDate metricDate) {
        this.metricDate = metricDate;
    }

    public BigDecimal getFixedCostsMonthly() {
        return fixedCostsMonthly;
    }

    public void setFixedCostsMonthly(BigDecimal fixedCostsMonthly) {
        this.fixedCostsMonthly = fixedCostsMonthly;
    }

    public BigDecimal getVariableCostPerCheck() {
        return variableCostPerCheck;
    }

    public void setVariableCostPerCheck(BigDecimal variableCostPerCheck) {
        this.variableCostPerCheck = variableCostPerCheck;
    }

    public BigDecimal getManualReviewCost() {
        return manualReviewCost;
    }

    public void setManualReviewCost(BigDecimal manualReviewCost) {
        this.manualReviewCost = manualReviewCost;
    }

    public BigDecimal getDataFeedCost() {
        return dataFeedCost;
    }

    public void setDataFeedCost(BigDecimal dataFeedCost) {
        this.dataFeedCost = dataFeedCost;
    }

    public BigDecimal getTargetMargin() {
        return targetMargin;
    }

    public void setTargetMargin(BigDecimal targetMargin) {
        this.targetMargin = targetMargin;
    }

    public BigDecimal getActualMargin() {
        return actualMargin;
    }

    public void setActualMargin(BigDecimal actualMargin) {
        this.actualMargin = actualMargin;
    }

    public Long getTotalChecksProcessed() {
        return totalChecksProcessed;
    }

    public void setTotalChecksProcessed(Long totalChecksProcessed) {
        this.totalChecksProcessed = totalChecksProcessed;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public BigDecimal getTotalCosts() {
        return totalCosts;
    }

    public void setTotalCosts(BigDecimal totalCosts) {
        this.totalCosts = totalCosts;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
