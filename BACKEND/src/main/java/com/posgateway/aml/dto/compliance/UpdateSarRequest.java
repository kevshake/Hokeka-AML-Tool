package com.posgateway.aml.dto.compliance;

import java.util.Map;

public class UpdateSarRequest {
    private String status;
    private String priority;
    private String narrative;
    private Map<String, Object> suspectInfo;
    private Map<String, Object> activityInfo;

    public UpdateSarRequest() {
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
