package com.posgateway.aml.dto.reporting;

import com.posgateway.aml.entity.reporting.ExecutionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for Report History filter requests
 */
public class ReportHistoryFilterRequest {
    private Long reportId;
    private ExecutionStatus status;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private Long pspId;
    private String searchTerm;
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortDirection;

    // Getters and Setters
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }

    public LocalDateTime getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDateTime dateFrom) { this.dateFrom = dateFrom; }

    public LocalDateTime getDateTo() { return dateTo; }
    public void setDateTo(LocalDateTime dateTo) { this.dateTo = dateTo; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getSearchTerm() { return searchTerm; }
    public void setSearchTerm(String searchTerm) { this.searchTerm = searchTerm; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
}
