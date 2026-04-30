package com.posgateway.aml.entity.reporting;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Report Execution Entity - Tracks report execution status and results
 */
@Entity
@Table(name = "report_executions", indexes = {
    @Index(name = "idx_report_exec_report", columnList = "report_id"),
    @Index(name = "idx_report_exec_psp", columnList = "psp_id"),
    @Index(name = "idx_report_exec_status", columnList = "status"),
    @Index(name = "idx_report_exec_dates", columnList = "date_from, date_to"),
    @Index(name = "idx_report_exec_created", columnList = "created_at")
})
public class ReportExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "execution_id", nullable = false, unique = true, length = 100)
    private String executionId;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "triggered_by")
    private Long triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @Column(name = "date_from")
    private LocalDateTime dateFrom;

    @Column(name = "date_to")
    private LocalDateTime dateTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters_applied", columnDefinition = "jsonb")
    private Map<String, Object> filtersApplied;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Column(name = "progress_percent")
    private Integer progressPercent = 0;

    @Column(name = "total_records")
    private Long totalRecords;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_formats", columnDefinition = "jsonb")
    private List<String> fileFormats;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_sizes", columnDefinition = "jsonb")
    private Map<String, Long> fileSizes;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
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

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public Long getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(Long triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public LocalDateTime getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDateTime dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDateTime getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDateTime dateTo) {
        this.dateTo = dateTo;
    }

    public Map<String, Object> getFiltersApplied() {
        return filtersApplied;
    }

    public void setFiltersApplied(Map<String, Object> filtersApplied) {
        this.filtersApplied = filtersApplied;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(Long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public List<String> getFileFormats() {
        return fileFormats;
    }

    public void setFileFormats(List<String> fileFormats) {
        this.fileFormats = fileFormats;
    }

    public Map<String, Long> getFileSizes() {
        return fileSizes;
    }

    public void setFileSizes(Map<String, Long> fileSizes) {
        this.fileSizes = fileSizes;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Integer executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Helper methods
    public boolean isCompleted() {
        return status == ExecutionStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }

    public boolean isRunning() {
        return status == ExecutionStatus.RUNNING;
    }

    public boolean canRetry() {
        return isFailed() && retryCount < 3;
    }
}
