package com.posgateway.aml.dto.reporting;

import java.util.List;
import java.util.Map;

/**
 * DTO for Report Preview responses
 */
public class ReportPreviewDTO {
    private String reportType;
    private String reportName;
    private List<Map<String, Object>> columns;
    private List<Map<String, Object>> data;
    private Long totalCount;
    private Boolean hasMore;
    private String sampleQuery;

    // Getters and Setters
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getReportName() { return reportName; }
    public void setReportName(String reportName) { this.reportName = reportName; }

    public List<Map<String, Object>> getColumns() { return columns; }
    public void setColumns(List<Map<String, Object>> columns) { this.columns = columns; }

    public List<Map<String, Object>> getData() { return data; }
    public void setData(List<Map<String, Object>> data) { this.data = data; }

    public Long getTotalCount() { return totalCount; }
    public void setTotalCount(Long totalCount) { this.totalCount = totalCount; }

    public Boolean getHasMore() { return hasMore; }
    public void setHasMore(Boolean hasMore) { this.hasMore = hasMore; }

    public String getSampleQuery() { return sampleQuery; }
    public void setSampleQuery(String sampleQuery) { this.sampleQuery = sampleQuery; }
}
