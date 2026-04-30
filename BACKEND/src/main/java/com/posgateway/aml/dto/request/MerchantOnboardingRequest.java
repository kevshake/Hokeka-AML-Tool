package com.posgateway.aml.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for merchant onboarding
 */
public class MerchantOnboardingRequest {

    private Long pspId; // Added for multi-tenancy

    // Basic Information
    @NotBlank(message = "Legal name is required")
    @Size(max = 500, message = "Legal name must not exceed 500 characters")
    private String legalName;

    @Size(max = 500, message = "Trading name must not exceed 500 characters")
    private String tradingName;

    @NotBlank(message = "Country is required")
    @Size(min = 3, max = 3, message = "Country must be 3-letter ISO code")
    private String country;

    @NotBlank(message = "Registration number is required")
    @Size(max = 100, message = "Registration number must not exceed 100 characters")
    private String registrationNumber;

    @Size(max = 100)
    private String taxId;

    // Business Details
    @NotBlank(message = "MCC is required")
    @Size(max = 10)
    private String mcc;

    @Size(max = 50)
    private String businessType; // CORPORATION, LLC, PARTNERSHIP, SOLE_PROPRIETOR

    @Min(value = 0, message = "Expected monthly volume must be positive")
    private Long expectedMonthlyVolume; // in cents

    @Size(max = 50)
    private String transactionChannel; // ONLINE, IN_STORE, MOBILE

    @Size(max = 500)
    private String website;

    // Address
    private String addressStreet;
    private String addressCity;
    private String addressState;
    private String addressPostalCode;
    private String addressCountry;

    // Operational Data
    private List<String> operatingCountries;

    @Past(message = "Registration date must be in the past")
    private LocalDate registrationDate;

    // Beneficial Owners
    @NotEmpty(message = "At least one beneficial owner is required")
    @Valid
    private List<BeneficialOwnerRequest> beneficialOwners;

    public MerchantOnboardingRequest() {
    }

    public MerchantOnboardingRequest(Long pspId, String legalName, String tradingName, String country,
            String registrationNumber, String taxId, String mcc, String businessType, Long expectedMonthlyVolume,
            String transactionChannel, String website, String addressStreet, String addressCity, String addressState,
            String addressPostalCode, String addressCountry, List<String> operatingCountries,
            LocalDate registrationDate, List<BeneficialOwnerRequest> beneficialOwners) {
        this.pspId = pspId;
        this.legalName = legalName;
        this.tradingName = tradingName;
        this.country = country;
        this.registrationNumber = registrationNumber;
        this.taxId = taxId;
        this.mcc = mcc;
        this.businessType = businessType;
        this.expectedMonthlyVolume = expectedMonthlyVolume;
        this.transactionChannel = transactionChannel;
        this.website = website;
        this.addressStreet = addressStreet;
        this.addressCity = addressCity;
        this.addressState = addressState;
        this.addressPostalCode = addressPostalCode;
        this.addressCountry = addressCountry;
        this.operatingCountries = operatingCountries;
        this.registrationDate = registrationDate;
        this.beneficialOwners = beneficialOwners;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
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

    public List<String> getOperatingCountries() {
        return operatingCountries;
    }

    public void setOperatingCountries(List<String> operatingCountries) {
        this.operatingCountries = operatingCountries;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
    }

    public List<BeneficialOwnerRequest> getBeneficialOwners() {
        return beneficialOwners;
    }

    public void setBeneficialOwners(List<BeneficialOwnerRequest> beneficialOwners) {
        this.beneficialOwners = beneficialOwners;
    }

    public static MerchantOnboardingRequestBuilder builder() {
        return new MerchantOnboardingRequestBuilder();
    }

    public static class MerchantOnboardingRequestBuilder {
        private Long pspId;
        private String legalName;
        private String tradingName;
        private String country;
        private String registrationNumber;
        private String taxId;
        private String mcc;
        private String businessType;
        private Long expectedMonthlyVolume;
        private String transactionChannel;
        private String website;
        private String addressStreet;
        private String addressCity;
        private String addressState;
        private String addressPostalCode;
        private String addressCountry;
        private List<String> operatingCountries;
        private LocalDate registrationDate;
        private List<BeneficialOwnerRequest> beneficialOwners;

        MerchantOnboardingRequestBuilder() {
        }

        public MerchantOnboardingRequestBuilder pspId(Long pspId) {
            this.pspId = pspId;
            return this;
        }

        public MerchantOnboardingRequestBuilder legalName(String legalName) {
            this.legalName = legalName;
            return this;
        }

        public MerchantOnboardingRequestBuilder tradingName(String tradingName) {
            this.tradingName = tradingName;
            return this;
        }

        public MerchantOnboardingRequestBuilder country(String country) {
            this.country = country;
            return this;
        }

        public MerchantOnboardingRequestBuilder registrationNumber(String registrationNumber) {
            this.registrationNumber = registrationNumber;
            return this;
        }

        public MerchantOnboardingRequestBuilder taxId(String taxId) {
            this.taxId = taxId;
            return this;
        }

        public MerchantOnboardingRequestBuilder mcc(String mcc) {
            this.mcc = mcc;
            return this;
        }

        public MerchantOnboardingRequestBuilder businessType(String businessType) {
            this.businessType = businessType;
            return this;
        }

        public MerchantOnboardingRequestBuilder expectedMonthlyVolume(Long expectedMonthlyVolume) {
            this.expectedMonthlyVolume = expectedMonthlyVolume;
            return this;
        }

        public MerchantOnboardingRequestBuilder transactionChannel(String transactionChannel) {
            this.transactionChannel = transactionChannel;
            return this;
        }

        public MerchantOnboardingRequestBuilder website(String website) {
            this.website = website;
            return this;
        }

        public MerchantOnboardingRequestBuilder addressStreet(String addressStreet) {
            this.addressStreet = addressStreet;
            return this;
        }

        public MerchantOnboardingRequestBuilder addressCity(String addressCity) {
            this.addressCity = addressCity;
            return this;
        }

        public MerchantOnboardingRequestBuilder addressState(String addressState) {
            this.addressState = addressState;
            return this;
        }

        public MerchantOnboardingRequestBuilder addressPostalCode(String addressPostalCode) {
            this.addressPostalCode = addressPostalCode;
            return this;
        }

        public MerchantOnboardingRequestBuilder addressCountry(String addressCountry) {
            this.addressCountry = addressCountry;
            return this;
        }

        public MerchantOnboardingRequestBuilder operatingCountries(List<String> operatingCountries) {
            this.operatingCountries = operatingCountries;
            return this;
        }

        public MerchantOnboardingRequestBuilder registrationDate(LocalDate registrationDate) {
            this.registrationDate = registrationDate;
            return this;
        }

        public MerchantOnboardingRequestBuilder beneficialOwners(List<BeneficialOwnerRequest> beneficialOwners) {
            this.beneficialOwners = beneficialOwners;
            return this;
        }

        public MerchantOnboardingRequest build() {
            return new MerchantOnboardingRequest(pspId, legalName, tradingName, country, registrationNumber, taxId, mcc,
                    businessType, expectedMonthlyVolume, transactionChannel, website, addressStreet, addressCity,
                    addressState, addressPostalCode, addressCountry, operatingCountries, registrationDate,
                    beneficialOwners);
        }

        public String toString() {
            return "MerchantOnboardingRequest.MerchantOnboardingRequestBuilder(pspId=" + this.pspId + ", legalName="
                    + this.legalName + ", tradingName=" + this.tradingName + ", country=" + this.country
                    + ", registrationNumber=" + this.registrationNumber + ", taxId=" + this.taxId + ", mcc=" + this.mcc
                    + ", businessType=" + this.businessType + ", expectedMonthlyVolume=" + this.expectedMonthlyVolume
                    + ", transactionChannel=" + this.transactionChannel + ", website=" + this.website
                    + ", addressStreet=" + this.addressStreet + ", addressCity=" + this.addressCity + ", addressState="
                    + this.addressState + ", addressPostalCode=" + this.addressPostalCode + ", addressCountry="
                    + this.addressCountry + ", operatingCountries=" + this.operatingCountries + ", registrationDate="
                    + this.registrationDate + ", beneficialOwners=" + this.beneficialOwners + ")";
        }
    }
}
