package com.posgateway.aml.dto.psp.cbk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PspSystemInterruptionDto {

    private Long id;
    private Long pspId;
    private LocalDate reportingDate;
    private String subCountyCode;
    private String systemOwnerFlag;
    private String thirdPartyOwnedCategory;
    private String thirdPartyName;
    private String productType;
    private String systemUnavailabilityTypeCode;
    private String thirdPartySystemAffected;
    private String serviceInterruptionCauseCode;
    private String severityInterruptionCode;
    private String recoveryTimeCode;
    private String remedialStatusCode;
    private BigDecimal systemUptimePercentage;
    private LocalDateTime startedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PspSystemInterruptionDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public LocalDate getReportingDate() { return reportingDate; }
    public void setReportingDate(LocalDate reportingDate) { this.reportingDate = reportingDate; }

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

    public String getSystemUnavailabilityTypeCode() { return systemUnavailabilityTypeCode; }
    public void setSystemUnavailabilityTypeCode(String systemUnavailabilityTypeCode) { this.systemUnavailabilityTypeCode = systemUnavailabilityTypeCode; }

    public String getThirdPartySystemAffected() { return thirdPartySystemAffected; }
    public void setThirdPartySystemAffected(String thirdPartySystemAffected) { this.thirdPartySystemAffected = thirdPartySystemAffected; }

    public String getServiceInterruptionCauseCode() { return serviceInterruptionCauseCode; }
    public void setServiceInterruptionCauseCode(String serviceInterruptionCauseCode) { this.serviceInterruptionCauseCode = serviceInterruptionCauseCode; }

    public String getSeverityInterruptionCode() { return severityInterruptionCode; }
    public void setSeverityInterruptionCode(String severityInterruptionCode) { this.severityInterruptionCode = severityInterruptionCode; }

    public String getRecoveryTimeCode() { return recoveryTimeCode; }
    public void setRecoveryTimeCode(String recoveryTimeCode) { this.recoveryTimeCode = recoveryTimeCode; }

    public String getRemedialStatusCode() { return remedialStatusCode; }
    public void setRemedialStatusCode(String remedialStatusCode) { this.remedialStatusCode = remedialStatusCode; }

    public BigDecimal getSystemUptimePercentage() { return systemUptimePercentage; }
    public void setSystemUptimePercentage(BigDecimal systemUptimePercentage) { this.systemUptimePercentage = systemUptimePercentage; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
