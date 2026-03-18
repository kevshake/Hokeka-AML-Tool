package com.posgateway.aml.dto.reporting;

import com.posgateway.aml.entity.reporting.ExecutionStatus;
import com.posgateway.aml.entity.reporting.TriggerType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for Report Execution responses
 */
public class ReportExecutionDTO {
    private Long id;
    private String executionId;
    private Long reportId;
    private String reportName;
    private String reportCode;
    private Long pspId;
    private Long triggeredBy;
    private String triggeredByName;
    private TriggerType triggerType;
    private Map<String, Object> parameters;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private Map<String, Object> filtersApplied;
    private ExecutionStatus status;
    private Integer progressPercent;
    private Long totalRecords;
    private String filePath;
    private List<String> fileFormats;
    private Map<String, Long> fileSizes;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer executionTimeMs;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public String getReportName() { return reportName; }
    public void setReportName(String reportName) { this.reportName = reportName; }

    public String getReportCode() { return reportCode; }
    public void setReportCode(String reportCode) { this.reportCode = reportCode; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public Long getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(Long triggeredBy) { this.triggeredBy = triggeredBy; }

    public String getTriggeredByName() { return triggeredByName; }
    public void setTriggeredByName(String triggeredByName) { this.triggeredByName = triggeredByName; }

    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

    public LocalDateTime getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDateTime dateFrom) { this.dateFrom = dateFrom; }

    public LocalDateTime getDateTo() { return dateTo; }
    public void setDateTo(LocalDateTime dateTo) { this.dateTo = dateTo; }

    public Map<String, Object> getFiltersApplied() { return filtersApplied; }
    public void setFiltersApplied(Map<String, Object> filtersApplied) { this.filtersApplied = filtersApplied; }

    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }

    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }

    public Long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Long totalRecords) { this.totalRecords = totalRecords; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public List<String> getFileFormats() { return fileFormats; }
    public void setFileFormats(List<String> fileFormats) { this.fileFormats = fileFormats; }

    public Map<String, Long> getFileSizes() { return fileSizes; }
    public void setFileSizes(Map<String, Long> fileSizes) { this.fileSizes = fileSizes; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public Integer getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Integer executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
