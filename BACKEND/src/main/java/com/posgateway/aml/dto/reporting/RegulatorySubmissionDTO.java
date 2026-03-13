package com.posgateway.aml.dto.reporting;

import com.posgateway.aml.entity.reporting.SubmissionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for Regulatory Submission responses
 */
public class RegulatorySubmissionDTO {
    private Long id;
    private String submissionReference;
    private Long reportId;
    private String reportName;
    private String reportCode;
    private Long executionId;
    private String regulatorCode;
    private String submissionType;
    private String jurisdiction;
    private LocalDate filingPeriodStart;
    private LocalDate filingPeriodEnd;
    private LocalDate filingDeadline;
    private SubmissionStatus status;
    private Long preparedBy;
    private String preparedByName;
    private LocalDateTime preparedAt;
    private Long reviewedBy;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private Long approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private Long filedBy;
    private String filedByName;
    private LocalDateTime filedAt;
    private String regulatorReference;
    private String filingReceipt;
    private String rejectionReason;
    private Long amendedSubmissionId;
    private String amendmentReason;
    private Map<String, Object> submittedData;
    private Map<String, String> attachmentPaths;
    private Long pspId;
    private Boolean isLateFiling;
    private Long daysUntilDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSubmissionReference() { return submissionReference; }
    public void setSubmissionReference(String submissionReference) { this.submissionReference = submissionReference; }

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public String getReportName() { return reportName; }
    public void setReportName(String reportName) { this.reportName = reportName; }

    public String getReportCode() { return reportCode; }
    public void setReportCode(String reportCode) { this.reportCode = reportCode; }

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

    public SubmissionStatus getStatus() { return status; }
    public void setStatus(SubmissionStatus status) { this.status = status; }

    public Long getPreparedBy() { return preparedBy; }
    public void setPreparedBy(Long preparedBy) { this.preparedBy = preparedBy; }

    public String getPreparedByName() { return preparedByName; }
    public void setPreparedByName(String preparedByName) { this.preparedByName = preparedByName; }

    public LocalDateTime getPreparedAt() { return preparedAt; }
    public void setPreparedAt(LocalDateTime preparedAt) { this.preparedAt = preparedAt; }

    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }

    public String getApprovedByName() { return approvedByName; }
    public void setApprovedByName(String approvedByName) { this.approvedByName = approvedByName; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public Long getFiledBy() { return filedBy; }
    public void setFiledBy(Long filedBy) { this.filedBy = filedBy; }

    public String getFiledByName() { return filedByName; }
    public void setFiledByName(String filedByName) { this.filedByName = filedByName; }

    public LocalDateTime getFiledAt() { return filedAt; }
    public void setFiledAt(LocalDateTime filedAt) { this.filedAt = filedAt; }

    public String getRegulatorReference() { return regulatorReference; }
    public void setRegulatorReference(String regulatorReference) { this.regulatorReference = regulatorReference; }

    public String getFilingReceipt() { return filingReceipt; }
    public void setFilingReceipt(String filingReceipt) { this.filingReceipt = filingReceipt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Long getAmendedSubmissionId() { return amendedSubmissionId; }
    public void setAmendedSubmissionId(Long amendedSubmissionId) { this.amendedSubmissionId = amendedSubmissionId; }

    public String getAmendmentReason() { return amendmentReason; }
    public void setAmendmentReason(String amendmentReason) { this.amendmentReason = amendmentReason; }

    public Map<String, Object> getSubmittedData() { return submittedData; }
    public void setSubmittedData(Map<String, Object> submittedData) { this.submittedData = submittedData; }

    public Map<String, String> getAttachmentPaths() { return attachmentPaths; }
    public void setAttachmentPaths(Map<String, String> attachmentPaths) { this.attachmentPaths = attachmentPaths; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public Boolean getIsLateFiling() { return isLateFiling; }
    public void setIsLateFiling(Boolean isLateFiling) { this.isLateFiling = isLateFiling; }

    public Long getDaysUntilDeadline() { return daysUntilDeadline; }
    public void setDaysUntilDeadline(Long daysUntilDeadline) { this.daysUntilDeadline = daysUntilDeadline; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
