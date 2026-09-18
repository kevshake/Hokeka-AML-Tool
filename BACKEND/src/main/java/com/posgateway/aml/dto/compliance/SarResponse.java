package com.posgateway.aml.dto.compliance;

import java.time.LocalDateTime;
import java.util.Map;

public class SarResponse {
    private Long reportId;
    private Long merchantId;
    private Long caseId;
    private String status;
    private String priority;
    private LocalDateTime filingDate;
    private String narrative;
    private Map<String, Object> suspectInfo;
    private Map<String, Object> activityInfo;
    private LocalDateTime createdAt;
    private String createdBy;

    public SarResponse() {
    }

    public SarResponse(Long reportId, Long merchantId, Long caseId, String status, String priority,
            LocalDateTime filingDate, String narrative, Map<String, Object> suspectInfo,
            Map<String, Object> activityInfo, LocalDateTime createdAt, String createdBy) {
        this.reportId = reportId;
        this.merchantId = merchantId;
        this.caseId = caseId;
        this.status = status;
        this.priority = priority;
        this.filingDate = filingDate;
        this.narrative = narrative;
        this.suspectInfo = suspectInfo;
        this.activityInfo = activityInfo;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public LocalDateTime getFilingDate() {
        return filingDate;
    }

    public void setFilingDate(LocalDateTime filingDate) {
        this.filingDate = filingDate;
    }

    public String getNarrative() {
        return narrative;
    }

    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    public Map<String, Object> getSuspectInfo() {
        return suspectInfo;
    }

    public void setSuspectInfo(Map<String, Object> suspectInfo) {
        this.suspectInfo = suspectInfo;
    }

    public Map<String, Object> getActivityInfo() {
        return activityInfo;
    }

    public void setActivityInfo(Map<String, Object> activityInfo) {
        this.activityInfo = activityInfo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public static SarResponseBuilder builder() {
        return new SarResponseBuilder();
    }

    public static class SarResponseBuilder {
        private Long reportId;
        private Long merchantId;
        private Long caseId;
        private String status;
        private String priority;
        private LocalDateTime filingDate;
        private String narrative;
        private Map<String, Object> suspectInfo;
        private Map<String, Object> activityInfo;
        private LocalDateTime createdAt;
        private String createdBy;

        SarResponseBuilder() {
        }

        public SarResponseBuilder reportId(Long reportId) {
            this.reportId = reportId;
            return this;
        }

        public SarResponseBuilder merchantId(Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public SarResponseBuilder caseId(Long caseId) {
            this.caseId = caseId;
            return this;
        }

        public SarResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public SarResponseBuilder priority(String priority) {
            this.priority = priority;
            return this;
        }

        public SarResponseBuilder filingDate(LocalDateTime filingDate) {
            this.filingDate = filingDate;
            return this;
        }

        public SarResponseBuilder narrative(String narrative) {
            this.narrative = narrative;
            return this;
        }

        public SarResponseBuilder suspectInfo(Map<String, Object> suspectInfo) {
            this.suspectInfo = suspectInfo;
            return this;
        }

        public SarResponseBuilder activityInfo(Map<String, Object> activityInfo) {
            this.activityInfo = activityInfo;
            return this;
        }

        public SarResponseBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public SarResponseBuilder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public SarResponse build() {
            return new SarResponse(reportId, merchantId, caseId, status, priority, filingDate, narrative, suspectInfo,
                    activityInfo, createdAt, createdBy);
        }

        public String toString() {
            return "SarResponse.SarResponseBuilder(reportId=" + this.reportId + ", merchantId=" + this.merchantId
                    + ", caseId=" + this.caseId + ", status=" + this.status + ", priority=" + this.priority
                    + ", filingDate=" + this.filingDate + ", narrative=" + this.narrative + ", suspectInfo="
                    + this.suspectInfo + ", activityInfo=" + this.activityInfo + ", createdAt=" + this.createdAt
                    + ", createdBy=" + this.createdBy + ")";
        }
    }
}
