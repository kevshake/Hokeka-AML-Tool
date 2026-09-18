package com.posgateway.aml.integration.cbk.records;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-record fields for CBK endpoint #8 — System Stability / Service Interruption.
 * Wrapper key: {@code SCH_SY_STABIL_SRVCE_INT}
 * Schedule: Daily.
 */
public final class SystemStabilityRecord {

    @JsonProperty("PSP_ID")
    private String pspId;

    @JsonProperty("REPORTING_DATE")
    private String reportingDate;

    @JsonProperty("SUB_COUNTY_CODE")
    private String subCountyCode;

    @JsonProperty("SYSTEM_OWNER_FLAG")
    private String systemOwnerFlag;

    @JsonProperty("THIRD_PARTY_OWNED_CATEGORY")
    private String thirdPartyOwnedCategory;

    @JsonProperty("THIRD_PARTY_NAME")
    private String thirdPartyName;

    @JsonProperty("PRODUCT_TYPE")
    private String productType;

    @JsonProperty("SYSTEM_UNAVAILABILITY_TYPE_COD")
    private String systemUnavailabilityTypeCod;

    @JsonProperty("THIRD_PARTY_SYSTEM_AFFECTED")
    private String thirdPartySystemAffected;

    @JsonProperty("SERVICE_INTERRUPTION_CAUSE_COD")
    private String serviceInterruptionCauseCod;

    @JsonProperty("SEVERITY_INTERRUPTION_CODE")
    private String severityInterruptionCode;

    @JsonProperty("RECOVERY_TIME_CODE")
    private String recoveryTimeCode;

    @JsonProperty("REMEDIAL_STATUS_CODE")
    private String remedialStatusCode;

    @JsonProperty("SYSTEM_UPTIME_PERCENTAGE")
    private String systemUptimePercentage;

    public SystemStabilityRecord() {}

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getReportingDate() { return reportingDate; }
    public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }

    public String getSubCountyCode() { return subCountyCode; }
    public void setSubCountyCode(String subCountyCode) { this.subCountyCode = subCountyCode; }

    public String getSystemOwnerFlag() { return systemOwnerFlag; }
    public void setSystemOwnerFlag(String systemOwnerFlag) { this.systemOwnerFlag = systemOwnerFlag; }

    public String getThirdPartyOwnedCategory() { return thirdPartyOwnedCategory; }
    public void setThirdPartyOwnedCategory(String thirdPartyOwnedCategory) { this.thirdPartyOwnedCategory = thirdPartyOwnedCategory; }

    public String getThirdPartyName() { return thirdPartyName; }
    public void setThirdPartyName(String thirdPartyName) { this.thirdPartyName = thirdPartyName; }

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }

    public String getSystemUnavailabilityTypeCod() { return systemUnavailabilityTypeCod; }
    public void setSystemUnavailabilityTypeCod(String systemUnavailabilityTypeCod) { this.systemUnavailabilityTypeCod = systemUnavailabilityTypeCod; }

    public String getThirdPartySystemAffected() { return thirdPartySystemAffected; }
    public void setThirdPartySystemAffected(String thirdPartySystemAffected) { this.thirdPartySystemAffected = thirdPartySystemAffected; }

    public String getServiceInterruptionCauseCod() { return serviceInterruptionCauseCod; }
    public void setServiceInterruptionCauseCod(String serviceInterruptionCauseCod) { this.serviceInterruptionCauseCod = serviceInterruptionCauseCod; }

    public String getSeverityInterruptionCode() { return severityInterruptionCode; }
    public void setSeverityInterruptionCode(String severityInterruptionCode) { this.severityInterruptionCode = severityInterruptionCode; }

    public String getRecoveryTimeCode() { return recoveryTimeCode; }
    public void setRecoveryTimeCode(String recoveryTimeCode) { this.recoveryTimeCode = recoveryTimeCode; }

    public String getRemedialStatusCode() { return remedialStatusCode; }
    public void setRemedialStatusCode(String remedialStatusCode) { this.remedialStatusCode = remedialStatusCode; }

    public String getSystemUptimePercentage() { return systemUptimePercentage; }
    public void setSystemUptimePercentage(String systemUptimePercentage) { this.systemUptimePercentage = systemUptimePercentage; }
}
