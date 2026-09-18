package com.posgateway.aml.dto;

public class SystemSettingsRequest {
    private Boolean maintenanceMode;
    private Boolean debugLogging;
    private Integer riskThresholdHigh;
    private Integer riskThresholdMedium;
    private Integer auditRetentionDays;
    private Boolean allowCrossBorderTxns;

    public SystemSettingsRequest() {
    }

    public Boolean getMaintenanceMode() { return maintenanceMode; }
    public void setMaintenanceMode(Boolean maintenanceMode) { this.maintenanceMode = maintenanceMode; }

    public Boolean getDebugLogging() { return debugLogging; }
    public void setDebugLogging(Boolean debugLogging) { this.debugLogging = debugLogging; }

    public Integer getRiskThresholdHigh() { return riskThresholdHigh; }
    public void setRiskThresholdHigh(Integer riskThresholdHigh) { this.riskThresholdHigh = riskThresholdHigh; }

    public Integer getRiskThresholdMedium() { return riskThresholdMedium; }
    public void setRiskThresholdMedium(Integer riskThresholdMedium) { this.riskThresholdMedium = riskThresholdMedium; }

    public Integer getAuditRetentionDays() { return auditRetentionDays; }
    public void setAuditRetentionDays(Integer auditRetentionDays) { this.auditRetentionDays = auditRetentionDays; }

    public Boolean getAllowCrossBorderTxns() { return allowCrossBorderTxns; }
    public void setAllowCrossBorderTxns(Boolean allowCrossBorderTxns) { this.allowCrossBorderTxns = allowCrossBorderTxns; }
}
