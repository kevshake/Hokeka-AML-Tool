package com.posgateway.aml.dto.reporting;

import com.posgateway.aml.entity.reporting.SubmissionStatus;

import java.time.LocalDate;

/**
 * DTO for Regulatory Submission requests
 */
public class RegulatorySubmissionRequest {
    private Long reportId;
    private Long executionId;
    private String regulatorCode;
    private String submissionType;
    private String jurisdiction;
    private LocalDate filingPeriodStart;
    private LocalDate filingPeriodEnd;
    private LocalDate filingDeadline;
    private Long pspId;

    // Getters and Setters
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }

    public String getRegulatorCode() { return regulatorCode; }
    public void setRegulatorCode(String regulatorCode) { this.regulatorCode = regulatorCode; }

    public String getSubmissionType() { return submissionType; }
    public void setSubmissionType(String submissionType) { this.submissionType = submissionType; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public LocalDate getFilingPeriodStart() { return filingPeriodStart; }
    public void setFilingPeriodStart(LocalDate filingPeriodStart) { this.filingPeriodStart = filingPeriodStart; }

    public LocalDate getFilingPeriodEnd() { return filingPeriodEnd; }
    public void setFilingPeriodEnd(LocalDate filingPeriodEnd) { this.filingPeriodEnd = filingPeriodEnd; }

    public LocalDate getFilingDeadline() { return filingDeadline; }
    public void setFilingDeadline(LocalDate filingDeadline) { this.filingDeadline = filingDeadline; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
}
