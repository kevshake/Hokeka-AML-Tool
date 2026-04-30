package com.posgateway.aml.dto.reporting;

import com.posgateway.aml.entity.reporting.ReportCategory;

import java.util.List;

/**
 * DTO for Report Category responses
 */
public class ReportCategoryDTO {
    private ReportCategory category;
    private String displayName;
    private String description;
    private Integer reportCount;
    private List<ReportDefinitionDTO> reports;

    // Getters and Setters
    public ReportCategory getCategory() { return category; }
    public void setCategory(ReportCategory category) { this.category = category; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getReportCount() { return reportCount; }
    public void setReportCount(Integer reportCount) { this.reportCount = reportCount; }

    public List<ReportDefinitionDTO> getReports() { return reports; }
    public void setReports(List<ReportDefinitionDTO> reports) { this.reports = reports; }
}
