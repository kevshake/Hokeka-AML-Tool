package com.posgateway.aml.entity.merchant;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Merchant entity for AML screening and onboarding
 */
@Entity
@Table(name = "merchants", indexes = {
        @Index(name = "idx_merchant_status", columnList = "status"),
        @Index(name = "idx_merchant_country", columnList = "address_country"),
        @Index(name = "idx_merchant_mcc", columnList = "mcc")
})
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "merchant_id")
    private Long merchantId;

    // Basic Information
    @Column(name = "legal_name", nullable = false, length = 500)
    private String legalName;

    @Column(name = "trading_name", length = 500)
    private String tradingName;

    @Column(name = "country", nullable = false, length = 3)
    private String country;

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.posgateway.aml.config.security.PiiMaskingSerializer.class)
    @Column(name = "registration_number", nullable = false, length = 100)
    private String registrationNumber;

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.posgateway.aml.config.security.PiiMaskingSerializer.class)
    @Column(name = "tax_id", length = 100)
    private String taxId;

    // Kenyan Specific Fields (Phase 29)
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.posgateway.aml.config.security.PiiMaskingSerializer.class)
    @Column(name = "kra_pin", length = 50)
    private String kraPin;

    @Column(name = "cr12_number", length = 100)
    private String cr12Number;

    @Column(name = "is_pep", nullable = false)
    private boolean isPep = false;

    // Business Details
    @Column(name = "mcc", nullable = false, length = 10)
    private String mcc;

    @Column(name = "business_type", length = 50)
    private String businessType;

    @Column(name = "expected_monthly_volume")
    private Long expectedMonthlyVolume;

    @Column(name = "transaction_channel", length = 50)
    private String transactionChannel;

    @Column(name = "website", length = 500)
    private String website;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    // Address
    @Column(name = "address_street", length = 500)
    private String addressStreet;

    @Column(name = "address_city", length = 200)
    private String addressCity;

    @Column(name = "address_state", length = 100)
    private String addressState;

    @Column(name = "address_postal_code", length = 20)
    private String addressPostalCode;

    @Column(name = "address_country", length = 3)
    private String addressCountry;

    // Operational Data
    @Column(name = "operating_countries", columnDefinition = "text[]")
    private String[] operatingCountries;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    // Status
    @Column(name = "status", nullable = false, length = 50)
    private String status = "PENDING_SCREENING";

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "last_screened_at")
    private LocalDateTime lastScreenedAt;

    @Column(name = "next_screening_due")
    private LocalDate nextScreeningDue;

    // Enhanced Dashboard Fields
    @Column(name = "kyc_status", length = 50)
    private String kycStatus = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(name = "contract_status", length = 50)
    private String contractStatus = "NO_CONTRACT"; // ACTIVE, EXPIRED, NO_CONTRACT

    @Column(name = "daily_limit", precision = 19, scale = 2)
    private java.math.BigDecimal dailyLimit = java.math.BigDecimal.ZERO;

    @Column(name = "current_usage", precision = 19, scale = 2)
    private java.math.BigDecimal currentUsage = java.math.BigDecimal.ZERO;

    @Column(name = "risk_level", length = 20)
    private String riskLevel = "UNKNOWN"; // LOW, MEDIUM, HIGH, CRITICAL

    // Flagright Scoring Fields
    @Column(name = "krs")
    private Double krs = 0.0;

    @Column(name = "cra")
    private Double cra = 0.0;

    // Relationships
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "psp_id", nullable = false) // Merchant MUST belong to a PSP
    private com.posgateway.aml.entity.psp.Psp psp;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BeneficialOwner> beneficialOwners = new ArrayList<>();

    public Merchant() {
    }

    public Merchant(Long merchantId, String legalName, String tradingName, String country, String registrationNumber,
            String taxId, String kraPin, String cr12Number, boolean isPep, String mcc, String businessType,
            Long expectedMonthlyVolume, String transactionChannel, String website, String contactEmail,
            String addressStreet, String addressCity, String addressState, String addressPostalCode,
            String addressCountry, String[] operatingCountries, LocalDate registrationDate, String status,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime lastScreenedAt, LocalDate nextScreeningDue,
            String kycStatus, String contractStatus, java.math.BigDecimal dailyLimit,

            java.math.BigDecimal currentUsage, String riskLevel, Double krs, Double cra, com.posgateway.aml.entity.psp.Psp psp,
            List<BeneficialOwner> beneficialOwners) {
        this.merchantId = merchantId;
        this.legalName = legalName;
        this.tradingName = tradingName;
        this.country = country;
        this.registrationNumber = registrationNumber;
        this.taxId = taxId;
        this.kraPin = kraPin;
        this.cr12Number = cr12Number;
        this.isPep = isPep;
        this.mcc = mcc;
        this.businessType = businessType;
        this.expectedMonthlyVolume = expectedMonthlyVolume;
        this.transactionChannel = transactionChannel;
        this.website = website;
        this.contactEmail = contactEmail;
        this.addressStreet = addressStreet;
        this.addressCity = addressCity;
        this.addressState = addressState;
        this.addressPostalCode = addressPostalCode;
        this.addressCountry = addressCountry;
        this.operatingCountries = operatingCountries;
        this.registrationDate = registrationDate;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastScreenedAt = lastScreenedAt;
        this.nextScreeningDue = nextScreeningDue;
        this.kycStatus = kycStatus;
        this.contractStatus = contractStatus;
        this.dailyLimit = dailyLimit;
        this.currentUsage = currentUsage;
        this.riskLevel = riskLevel;
        this.krs = krs != null ? krs : 0.0;
        this.cra = cra != null ? cra : 0.0;
        this.psp = psp;
        this.beneficialOwners = beneficialOwners;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getTradingName() {
        return tradingName;
    }

    public void setTradingName(String tradingName) {
        this.tradingName = tradingName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getKraPin() {
        return kraPin;
    }

    public void setKraPin(String kraPin) {
        this.kraPin = kraPin;
    }

    public String getCr12Number() {
        return cr12Number;
    }

    public void setCr12Number(String cr12Number) {
        this.cr12Number = cr12Number;
    }

    public boolean isPep() {
        return isPep;
    }

    public void setPep(boolean pep) {
        isPep = pep;
    }

    public String getMcc() {
        return mcc;
    }

    public void setMcc(String mcc) {
        this.mcc = mcc;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public Long getExpectedMonthlyVolume() {
        return expectedMonthlyVolume;
    }

    public void setExpectedMonthlyVolume(Long expectedMonthlyVolume) {
        this.expectedMonthlyVolume = expectedMonthlyVolume;
    }

    public String getTransactionChannel() {
        return transactionChannel;
    }

    public void setTransactionChannel(String transactionChannel) {
        this.transactionChannel = transactionChannel;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getAddressStreet() {
        return addressStreet;
    }

    public void setAddressStreet(String addressStreet) {
        this.addressStreet = addressStreet;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public void setAddressCity(String addressCity) {
        this.addressCity = addressCity;
    }

    public String getAddressState() {
        return addressState;
    }

    public void setAddressState(String addressState) {
        this.addressState = addressState;
    }

    public String getAddressPostalCode() {
        return addressPostalCode;
    }

    public void setAddressPostalCode(String addressPostalCode) {
        this.addressPostalCode = addressPostalCode;
    }

    public String getAddressCountry() {
        return addressCountry;
    }

    public void setAddressCountry(String addressCountry) {
        this.addressCountry = addressCountry;
    }

    public String[] getOperatingCountries() {
        return operatingCountries;
    }

    public void setOperatingCountries(String[] operatingCountries) {
        this.operatingCountries = operatingCountries;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastScreenedAt() {
        return lastScreenedAt;
    }

    public void setLastScreenedAt(LocalDateTime lastScreenedAt) {
        this.lastScreenedAt = lastScreenedAt;
    }

    public LocalDate getNextScreeningDue() {
        return nextScreeningDue;
    }

    public void setNextScreeningDue(LocalDate nextScreeningDue) {
        this.nextScreeningDue = nextScreeningDue;
    }

    public String getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(String kycStatus) {
        this.kycStatus = kycStatus;
    }

    public String getContractStatus() {
        return contractStatus;
    }

    public void setContractStatus(String contractStatus) {
        this.contractStatus = contractStatus;
    }

    public java.math.BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(java.math.BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public java.math.BigDecimal getCurrentUsage() {
        return currentUsage;
    }

    public void setCurrentUsage(java.math.BigDecimal currentUsage) {
        this.currentUsage = currentUsage;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getKrs() {
        return krs;
    }

    public void setKrs(Double krs) {
        this.krs = krs;
    }

    public Double getCra() {
        return cra;
    }

    public void setCra(Double cra) {
        this.cra = cra;
    }

    public com.posgateway.aml.entity.psp.Psp getPsp() {
        return psp;
    }

    public void setPsp(com.posgateway.aml.entity.psp.Psp psp) {
        this.psp = psp;
    }

    public List<BeneficialOwner> getBeneficialOwners() {
        return beneficialOwners;
    }

    public void setBeneficialOwners(List<BeneficialOwner> beneficialOwners) {
        this.beneficialOwners = beneficialOwners;
    }

    public static MerchantBuilder builder() {
        return new MerchantBuilder();
    }

    public static class MerchantBuilder {
        private Long merchantId;
        private String legalName;
        private String tradingName;
        private String country;
        private String registrationNumber;
        private String taxId;
        private String kraPin;
        private String cr12Number;
        private boolean isPep; // Default false
        private String mcc;
        private String businessType;
        private Long expectedMonthlyVolume;
        private String transactionChannel;
        private String website;
        private String contactEmail;
        private String addressStreet;
        private String addressCity;
        private String addressState;
        private String addressPostalCode;
        private String addressCountry;
        private String[] operatingCountries;
        private LocalDate registrationDate;
        private String status; // Default PENDING_SCREENING
        private LocalDateTime createdAt; // Default now
        private LocalDateTime updatedAt; // Default now
        private LocalDateTime lastScreenedAt;
        private LocalDate nextScreeningDue;
        private String kycStatus;
        private String contractStatus;
        private java.math.BigDecimal dailyLimit;
        private java.math.BigDecimal currentUsage;
        private String riskLevel;
        private Double krs;
        private Double cra;
        private com.posgateway.aml.entity.psp.Psp psp;
        private List<BeneficialOwner> beneficialOwners; // Default new ArrayList

        MerchantBuilder() {
        }

        public MerchantBuilder merchantId(Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public MerchantBuilder legalName(String legalName) {
            this.legalName = legalName;
            return this;
        }

        public MerchantBuilder tradingName(String tradingName) {
            this.tradingName = tradingName;
            return this;
        }

        public MerchantBuilder country(String country) {
            this.country = country;
            return this;
        }

        public MerchantBuilder registrationNumber(String registrationNumber) {
            this.registrationNumber = registrationNumber;
            return this;
        }

        public MerchantBuilder taxId(String taxId) {
            this.taxId = taxId;
            return this;
        }

        public MerchantBuilder kraPin(String kraPin) {
            this.kraPin = kraPin;
            return this;
        }

        public MerchantBuilder cr12Number(String cr12Number) {
            this.cr12Number = cr12Number;
            return this;
        }

        public MerchantBuilder isPep(boolean isPep) {
            this.isPep = isPep;
            return this;
        }

        public MerchantBuilder mcc(String mcc) {
            this.mcc = mcc;
            return this;
        }

        public MerchantBuilder businessType(String businessType) {
            this.businessType = businessType;
            return this;
        }

        public MerchantBuilder expectedMonthlyVolume(Long expectedMonthlyVolume) {
            this.expectedMonthlyVolume = expectedMonthlyVolume;
            return this;
        }

        public MerchantBuilder transactionChannel(String transactionChannel) {
            this.transactionChannel = transactionChannel;
            return this;
        }

        public MerchantBuilder website(String website) {
            this.website = website;
            return this;
        }

        public MerchantBuilder contactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
            return this;
        }

        public MerchantBuilder addressStreet(String addressStreet) {
            this.addressStreet = addressStreet;
            return this;
        }

        public MerchantBuilder addressCity(String addressCity) {
            this.addressCity = addressCity;
            return this;
        }

        public MerchantBuilder addressState(String addressState) {
            this.addressState = addressState;
            return this;
        }

        public MerchantBuilder addressPostalCode(String addressPostalCode) {
            this.addressPostalCode = addressPostalCode;
            return this;
        }

        public MerchantBuilder addressCountry(String addressCountry) {
            this.addressCountry = addressCountry;
            return this;
        }

        public MerchantBuilder operatingCountries(String[] operatingCountries) {
            this.operatingCountries = operatingCountries;
            return this;
        }

        public MerchantBuilder registrationDate(LocalDate registrationDate) {
            this.registrationDate = registrationDate;
            return this;
        }

        public MerchantBuilder status(String status) {
            this.status = status;
            return this;
        }

        public MerchantBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public MerchantBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public MerchantBuilder lastScreenedAt(LocalDateTime lastScreenedAt) {
            this.lastScreenedAt = lastScreenedAt;
            return this;
        }

        public MerchantBuilder nextScreeningDue(LocalDate nextScreeningDue) {
            this.nextScreeningDue = nextScreeningDue;
            return this;
        }

        public MerchantBuilder psp(com.posgateway.aml.entity.psp.Psp psp) {
            this.psp = psp;
            return this;
        }

        public MerchantBuilder kycStatus(String kycStatus) {
            this.kycStatus = kycStatus;
            return this;
        }

        public MerchantBuilder contractStatus(String contractStatus) {
            this.contractStatus = contractStatus;
            return this;
        }

        public MerchantBuilder dailyLimit(java.math.BigDecimal dailyLimit) {
            this.dailyLimit = dailyLimit;
            return this;
        }

        public MerchantBuilder currentUsage(java.math.BigDecimal currentUsage) {
            this.currentUsage = currentUsage;
            return this;
        }

        public MerchantBuilder riskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public MerchantBuilder krs(Double krs) {
            this.krs = krs;
            return this;
        }

        public MerchantBuilder cra(Double cra) {
            this.cra = cra;
            return this;
        }

        public MerchantBuilder beneficialOwners(List<BeneficialOwner> beneficialOwners) {
            this.beneficialOwners = beneficialOwners;
            return this;
        }

        public Merchant build() {
            // Apply defaults if not set
            // Note: For primitives like boolean isPep, default is false. The Builder
            // initializes primitives to Java defaults (false, 0)
            // But if the user logic expected defaults even if builder called, we need to be
            // careful.
            // In typical Lombok @Builder.Default, the default expression is used if the
            // value is NOT provided to the builder.
            // Since I cannot detect "not provided" for primitives easily without flags,
            // I'll rely on the field initializers in the class if I was using a class
            // instance,
            // but here I'm constructing from fields.
            //
            // Best approach: Initialize builder fields with defaults.
            if (this.status == null)
                this.status = "PENDING_SCREENING";
            if (this.createdAt == null)
                this.createdAt = LocalDateTime.now();
            if (this.updatedAt == null)
                this.updatedAt = LocalDateTime.now();
            if (this.beneficialOwners == null)
                this.beneficialOwners = new ArrayList<>();
            if (this.kycStatus == null)
                this.kycStatus = "PENDING";
            if (this.contractStatus == null)
                this.contractStatus = "NO_CONTRACT";
            if (this.dailyLimit == null)
                this.dailyLimit = java.math.BigDecimal.ZERO;
            if (this.currentUsage == null)
                this.currentUsage = java.math.BigDecimal.ZERO;
            if (this.riskLevel == null)
                this.riskLevel = "UNKNOWN";

            return new Merchant(merchantId, legalName, tradingName, country, registrationNumber, taxId, kraPin,
                    cr12Number, isPep, mcc, businessType, expectedMonthlyVolume, transactionChannel, website,
                    contactEmail, addressStreet, addressCity, addressState, addressPostalCode, addressCountry,
                    operatingCountries, registrationDate, status, createdAt, updatedAt, lastScreenedAt,
                    nextScreeningDue, kycStatus, contractStatus, dailyLimit, currentUsage, riskLevel, krs, cra, psp,
                    beneficialOwners);
        }

        public String toString() {
            return "Merchant.MerchantBuilder(merchantId=" + this.merchantId + ", legalName=" + this.legalName
                    + ", tradingName=" + this.tradingName + ", country=" + this.country + ", registrationNumber="
                    + this.registrationNumber + ", taxId=" + this.taxId + ", kraPin=" + this.kraPin + ", cr12Number="
                    + this.cr12Number + ", isPep=" + this.isPep + ", mcc=" + this.mcc + ", businessType="
                    + this.businessType + ", expectedMonthlyVolume=" + this.expectedMonthlyVolume
                    + ", transactionChannel=" + this.transactionChannel + ", website=" + this.website
                    + ", contactEmail=" + this.contactEmail + ", addressStreet=" + this.addressStreet + ", addressCity="
                    + this.addressCity + ", addressState=" + this.addressState + ", addressPostalCode="
                    + this.addressPostalCode + ", addressCountry=" + this.addressCountry + ", operatingCountries="
                    + java.util.Arrays.deepToString(this.operatingCountries) + ", registrationDate="
                    + this.registrationDate + ", status=" + this.status + ", createdAt=" + this.createdAt
                    + ", updatedAt=" + this.updatedAt + ", lastScreenedAt=" + this.lastScreenedAt
                    + ", nextScreeningDue=" + this.nextScreeningDue + ", psp=" + this.psp + ", beneficialOwners="
                    + this.beneficialOwners + ")";
        }
    }

    /**
     * Check if merchant is new (registered within configured months)
     */
    public boolean isNew() {
        if (createdAt == null) {
            return true;
        }
        // Consider merchant new if created within last 30 days
        return createdAt.isAfter(LocalDateTime.now().minusDays(30));
    }

    /**
     * Update next screening due date (7 days from now - weekly)
     */
    public void updateNextScreeningDue() {
        this.lastScreenedAt = LocalDateTime.now();
        this.nextScreeningDue = LocalDate.now().plusDays(7);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
