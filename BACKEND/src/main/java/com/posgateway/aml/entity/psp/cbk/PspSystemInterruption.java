package com.posgateway.aml.entity.psp.cbk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CBK GDI #8 – System Stability / Service Interruptions (daily).
 * Maps to table psp_system_interruptions.
 */
@Entity
@Table(name = "psp_system_interruptions", indexes = {
        @Index(name = "idx_psp_system_interruptions_psp_id", columnList = "psp_id")
})
public class PspSystemInterruption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    @Column(name = "reporting_date")
    private LocalDate reportingDate;

    @Column(name = "sub_county_code", length = 32)
    private String subCountyCode;

    @Column(name = "system_owner_flag", length = 8)
    private String systemOwnerFlag;

    @Column(name = "third_party_owned_category", length = 64)
    private String thirdPartyOwnedCategory;

    @Column(name = "third_party_name", length = 256)
    private String thirdPartyName;

    @Column(name = "product_type", length = 64)
    private String productType;

    @Column(name = "system_unavailability_type_code", length = 32)
    private String systemUnavailabilityTypeCode;

    @Column(name = "third_party_system_affected", length = 256)
    private String thirdPartySystemAffected;

    @Column(name = "service_interruption_cause_code", length = 32)
    private String serviceInterruptionCauseCode;

    @Column(name = "severity_interruption_code", length = 32)
    private String severityInterruptionCode;

    @Column(name = "recovery_time_code", length = 32)
    private String recoveryTimeCode;

    @Column(name = "remedial_status_code", length = 32)
    private String remedialStatusCode;

    @Column(name = "system_uptime_percentage", precision = 5, scale = 2)
    private BigDecimal systemUptimePercentage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PspSystemInterruption() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

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

    public static PspSystemInterruptionBuilder builder() { return new PspSystemInterruptionBuilder(); }

    public static class PspSystemInterruptionBuilder {
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

        PspSystemInterruptionBuilder() {}

        public PspSystemInterruptionBuilder id(Long id) { this.id = id; return this; }
        public PspSystemInterruptionBuilder pspId(Long pspId) { this.pspId = pspId; return this; }
        public PspSystemInterruptionBuilder reportingDate(LocalDate reportingDate) { this.reportingDate = reportingDate; return this; }
        public PspSystemInterruptionBuilder subCountyCode(String subCountyCode) { this.subCountyCode = subCountyCode; return this; }
        public PspSystemInterruptionBuilder systemOwnerFlag(String systemOwnerFlag) { this.systemOwnerFlag = systemOwnerFlag; return this; }
        public PspSystemInterruptionBuilder thirdPartyOwnedCategory(String thirdPartyOwnedCategory) { this.thirdPartyOwnedCategory = thirdPartyOwnedCategory; return this; }
        public PspSystemInterruptionBuilder thirdPartyName(String thirdPartyName) { this.thirdPartyName = thirdPartyName; return this; }
        public PspSystemInterruptionBuilder productType(String productType) { this.productType = productType; return this; }
        public PspSystemInterruptionBuilder systemUnavailabilityTypeCode(String systemUnavailabilityTypeCode) { this.systemUnavailabilityTypeCode = systemUnavailabilityTypeCode; return this; }
        public PspSystemInterruptionBuilder thirdPartySystemAffected(String thirdPartySystemAffected) { this.thirdPartySystemAffected = thirdPartySystemAffected; return this; }
        public PspSystemInterruptionBuilder serviceInterruptionCauseCode(String serviceInterruptionCauseCode) { this.serviceInterruptionCauseCode = serviceInterruptionCauseCode; return this; }
        public PspSystemInterruptionBuilder severityInterruptionCode(String severityInterruptionCode) { this.severityInterruptionCode = severityInterruptionCode; return this; }
        public PspSystemInterruptionBuilder recoveryTimeCode(String recoveryTimeCode) { this.recoveryTimeCode = recoveryTimeCode; return this; }
        public PspSystemInterruptionBuilder remedialStatusCode(String remedialStatusCode) { this.remedialStatusCode = remedialStatusCode; return this; }
        public PspSystemInterruptionBuilder systemUptimePercentage(BigDecimal systemUptimePercentage) { this.systemUptimePercentage = systemUptimePercentage; return this; }
        public PspSystemInterruptionBuilder startedAt(LocalDateTime startedAt) { this.startedAt = startedAt; return this; }
        public PspSystemInterruptionBuilder resolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; return this; }
        public PspSystemInterruptionBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PspSystemInterruptionBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PspSystemInterruption build() {
            PspSystemInterruption e = new PspSystemInterruption();
            e.id = this.id;
            e.pspId = this.pspId;
            e.reportingDate = this.reportingDate;
            e.subCountyCode = this.subCountyCode;
            e.systemOwnerFlag = this.systemOwnerFlag;
            e.thirdPartyOwnedCategory = this.thirdPartyOwnedCategory;
            e.thirdPartyName = this.thirdPartyName;
            e.productType = this.productType;
            e.systemUnavailabilityTypeCode = this.systemUnavailabilityTypeCode;
            e.thirdPartySystemAffected = this.thirdPartySystemAffected;
            e.serviceInterruptionCauseCode = this.serviceInterruptionCauseCode;
            e.severityInterruptionCode = this.severityInterruptionCode;
            e.recoveryTimeCode = this.recoveryTimeCode;
            e.remedialStatusCode = this.remedialStatusCode;
            e.systemUptimePercentage = this.systemUptimePercentage;
            e.startedAt = this.startedAt;
            e.resolvedAt = this.resolvedAt;
            e.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
            e.updatedAt = this.updatedAt != null ? this.updatedAt : LocalDateTime.now();
            return e;
        }
    }
}
