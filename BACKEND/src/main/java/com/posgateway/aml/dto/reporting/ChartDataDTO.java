package com.posgateway.aml.dto.reporting;

/**
 * DTO for Chart Data requests and responses
 */
public class ChartDataDTO {
    private String reportType;
    private String chartType;
    private Object data;
    private Object labels;
    private Object datasets;
    private Object options;

    // Getters and Setters
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getChartType() { return chartType; }
    public void setChartType(String chartType) { this.chartType = chartType; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public Object getLabels() { return labels; }
    public void setLabels(Object labels) { this.labels = labels; }

    public Object getDatasets() { return datasets; }
    public void setDatasets(Object datasets) { this.datasets = datasets; }

    public Object getOptions() { return options; }
    public void setOptions(Object options) { this.options = options; }
}
