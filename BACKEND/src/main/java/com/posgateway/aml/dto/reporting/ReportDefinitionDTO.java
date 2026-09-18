package com.posgateway.aml.dto.reporting;

import com.posgateway.aml.entity.reporting.ReportCategory;
import com.posgateway.aml.entity.reporting.ReportType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for Report Definition responses
 */
public class ReportDefinitionDTO {
    private Long id;
    private String reportCode;
    private String reportName;
    private ReportCategory reportCategory;
    private String categoryDisplayName;
    private String description;
    private ReportType reportType;
    private String baseEntity;
    private Boolean requiresApproval;
    private String regulatoryTemplate;
    private Integer retentionDays;
    private Boolean enabled;
    private Integer currentVersion;
    private List<Map<String, Object>> parameters;
    private List<Map<String, Object>> filters;
    private List<Map<String, Object>> columns;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReportCode() { return reportCode; }
    public void setReportCode(String reportCode) { this.reportCode = reportCode; }

    public String getReportName() { return reportName; }
    public void setReportName(String reportName) { this.reportName = reportName; }

    public ReportCategory getReportCategory() { return reportCategory; }
    public void setReportCategory(ReportCategory reportCategory) { this.reportCategory = reportCategory; }

    public String getCategoryDisplayName() { return categoryDisplayName; }
    public void setCategoryDisplayName(String categoryDisplayName) { this.categoryDisplayName = categoryDisplayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ReportType getReportType() { return reportType; }
    public void setReportType(ReportType reportType) { this.reportType = reportType; }

    public String getBaseEntity() { return baseEntity; }
    public void setBaseEntity(String baseEntity) { this.baseEntity = baseEntity; }

    public Boolean getRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(Boolean requiresApproval) { this.requiresApproval = requiresApproval; }

    public String getRegulatoryTemplate() { return regulatoryTemplate; }
    public void setRegulatoryTemplate(String regulatoryTemplate) { this.regulatoryTemplate = regulatoryTemplate; }

    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Integer getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Integer currentVersion) { this.currentVersion = currentVersion; }

    public List<Map<String, Object>> getParameters() { return parameters; }
    public void setParameters(List<Map<String, Object>> parameters) { this.parameters = parameters; }

    public List<Map<String, Object>> getFilters() { return filters; }
    public void setFilters(List<Map<String, Object>> filters) { this.filters = filters; }

    public List<Map<String, Object>> getColumns() { return columns; }
    public void setColumns(List<Map<String, Object>> columns) { this.columns = columns; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
