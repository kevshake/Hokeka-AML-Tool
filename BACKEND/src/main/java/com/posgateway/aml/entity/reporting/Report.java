package com.posgateway.aml.entity.reporting;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Report Entity - Master registry of all available reports
 */
@Entity
@Table(name = "reports", indexes = {
    @Index(name = "idx_reports_category", columnList = "report_category"),
    @Index(name = "idx_reports_type", columnList = "report_type"),
    @Index(name = "idx_reports_enabled", columnList = "enabled")
})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_code", nullable = false, unique = true, length = 50)
    private String reportCode;

    @Column(name = "report_name", nullable = false, length = 255)
    private String reportName;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_category", nullable = false, length = 50)
    private ReportCategory reportCategory;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 20)
    private ReportType reportType = ReportType.DYNAMIC;

    @Column(name = "base_entity", length = 100)
    private String baseEntity;

    @Column(name = "requires_approval")
    private Boolean requiresApproval = false;

    @Column(name = "regulatory_template", length = 50)
    private String regulatoryTemplate;

    @Column(name = "retention_days")
    private Integer retentionDays = 2555; // 7 years default

    @Column(name = "enabled")
    private Boolean enabled = true;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReportDefinition> definitions;

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

    public String getReportCode() {
        return reportCode;
    }

    public void setReportCode(String reportCode) {
        this.reportCode = reportCode;
    }

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public ReportCategory getReportCategory() {
        return reportCategory;
    }

    public void setReportCategory(ReportCategory reportCategory) {
        this.reportCategory = reportCategory;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }

    public String getBaseEntity() {
        return baseEntity;
    }

    public void setBaseEntity(String baseEntity) {
        this.baseEntity = baseEntity;
    }

    public Boolean getRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(Boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public String getRegulatoryTemplate() {
        return regulatoryTemplate;
    }

    public void setRegulatoryTemplate(String regulatoryTemplate) {
        this.regulatoryTemplate = regulatoryTemplate;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<ReportDefinition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<ReportDefinition> definitions) {
        this.definitions = definitions;
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

    // Helper methods
    public ReportDefinition getActiveDefinition() {
        if (definitions == null) return null;
        return definitions.stream()
            .filter(ReportDefinition::getIsActive)
            .findFirst()
            .orElse(null);
    }
}
