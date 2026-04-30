package com.posgateway.aml.dto.compliance;

import java.util.Map;

public class CreateSarRequest {
    private Long merchantId;
    private Long caseId;
    private String priority;
    private String narrative;
    private Map<String, Object> suspectInfo;
    private Map<String, Object> activityInfo;

    public CreateSarRequest() {
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

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
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
}
