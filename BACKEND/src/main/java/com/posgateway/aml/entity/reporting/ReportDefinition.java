package com.posgateway.aml.entity.reporting;

import com.posgateway.aml.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Report Definition Entity - Versioned SQL definitions for each report
 */
@Entity
@Table(name = "report_definitions", indexes = {
    @Index(name = "idx_report_defs_report", columnList = "report_id"),
    @Index(name = "idx_report_defs_active", columnList = "is_active")
})
public class ReportDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "sql_query", nullable = false, columnDefinition = "TEXT")
    private String sqlQuery;

    @Column(name = "count_query", columnDefinition = "TEXT")
    private String countQuery;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private List<Map<String, Object>> parameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters", columnDefinition = "jsonb")
    private List<Map<String, Object>> filters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "columns", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> columns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "aggregations", columnDefinition = "jsonb")
    private List<Map<String, Object>> aggregations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "group_by_fields", columnDefinition = "jsonb")
    private List<String> groupByFields;

    @Column(name = "order_by_default", length = 255)
    private String orderByDefault;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getSqlQuery() {
        return sqlQuery;
    }

    public void setSqlQuery(String sqlQuery) {
        this.sqlQuery = sqlQuery;
    }

    public String getCountQuery() {
        return countQuery;
    }

    public void setCountQuery(String countQuery) {
        this.countQuery = countQuery;
    }

    public List<Map<String, Object>> getParameters() {
        return parameters;
    }

    public void setParameters(List<Map<String, Object>> parameters) {
        this.parameters = parameters;
    }

    public List<Map<String, Object>> getFilters() {
        return filters;
    }

    public void setFilters(List<Map<String, Object>> filters) {
        this.filters = filters;
    }

    public List<Map<String, Object>> getColumns() {
        return columns;
    }

    public void setColumns(List<Map<String, Object>> columns) {
        this.columns = columns;
    }

    public List<Map<String, Object>> getAggregations() {
        return aggregations;
    }

    public void setAggregations(List<Map<String, Object>> aggregations) {
        this.aggregations = aggregations;
    }

    public List<String> getGroupByFields() {
        return groupByFields;
    }

    public void setGroupByFields(List<String> groupByFields) {
        this.groupByFields = groupByFields;
    }

    public String getOrderByDefault() {
        return orderByDefault;
    }

    public void setOrderByDefault(String orderByDefault) {
        this.orderByDefault = orderByDefault;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
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
}
