package com.posgateway.aml.dto.reporting;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for Report Generation requests
 */
public class ReportGenerateRequest {
    private String reportType;
    private Map<String, Object> parameters;
    private Map<String, Object> filters;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private String outputFormat;
    private Long pspId;

    // Getters and Setters
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public Map<String, Object> getFilters() { return filters; }
    public void setFilters(Map<String, Object> filters) { this.filters = filters; }

    public LocalDateTime getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDateTime dateFrom) { this.dateFrom = dateFrom; }

    public LocalDateTime getDateTo() { return dateTo; }
    public void setDateTo(LocalDateTime dateTo) { this.dateTo = dateTo; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
}
