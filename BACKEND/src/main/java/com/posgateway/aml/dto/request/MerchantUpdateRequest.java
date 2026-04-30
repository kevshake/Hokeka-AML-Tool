package com.posgateway.aml.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public class MerchantUpdateRequest {

    @Size(max = 100)
    private String legalName;

    @Size(max = 100)
    private String tradingName;

    private String businessType;
    private String mcc;
    private Long expectedMonthlyVolume;
    private String website;

    // Address fields
    private String addressStreet;
    private String addressCity;
    private String addressState;
    private String addressPostalCode;
    private String addressCountry;

    private String contactEmail;
    private String contactPhone;

    private List<String> operatingCountries;

    private List<BeneficialOwnerRequest> newBeneficialOwners;

    public MerchantUpdateRequest() {
    }

    public MerchantUpdateRequest(String legalName, String tradingName, String businessType, String mcc,
            Long expectedMonthlyVolume, String website, String addressStreet, String addressCity, String addressState,
            String addressPostalCode, String addressCountry, String contactEmail, String contactPhone,
            List<String> operatingCountries, List<BeneficialOwnerRequest> newBeneficialOwners) {
        this.legalName = legalName;
        this.tradingName = tradingName;
        this.businessType = businessType;
        this.mcc = mcc;
        this.expectedMonthlyVolume = expectedMonthlyVolume;
        this.website = website;
        this.addressStreet = addressStreet;
        this.addressCity = addressCity;
        this.addressState = addressState;
        this.addressPostalCode = addressPostalCode;
        this.addressCountry = addressCountry;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.operatingCountries = operatingCountries;
        this.newBeneficialOwners = newBeneficialOwners;
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

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getMcc() {
        return mcc;
    }

    public void setMcc(String mcc) {
        this.mcc = mcc;
    }

    public Long getExpectedMonthlyVolume() {
        return expectedMonthlyVolume;
    }

    public void setExpectedMonthlyVolume(Long expectedMonthlyVolume) {
        this.expectedMonthlyVolume = expectedMonthlyVolume;
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

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public List<String> getOperatingCountries() {
        return operatingCountries;
    }

    public void setOperatingCountries(List<String> operatingCountries) {
        this.operatingCountries = operatingCountries;
    }

    public List<BeneficialOwnerRequest> getNewBeneficialOwners() {
        return newBeneficialOwners;
    }

    public void setNewBeneficialOwners(List<BeneficialOwnerRequest> newBeneficialOwners) {
        this.newBeneficialOwners = newBeneficialOwners;
    }

    public static MerchantUpdateRequestBuilder builder() {
        return new MerchantUpdateRequestBuilder();
    }

    public static class MerchantUpdateRequestBuilder {
        private String legalName;
        private String tradingName;
        private String businessType;
        private String mcc;
        private Long expectedMonthlyVolume;
        private String website;
        private String addressStreet;
        private String addressCity;
        private String addressState;
        private String addressPostalCode;
        private String addressCountry;
        private String contactEmail;
        private String contactPhone;
        private List<String> operatingCountries;
        private List<BeneficialOwnerRequest> newBeneficialOwners;

        MerchantUpdateRequestBuilder() {
        }

        public MerchantUpdateRequestBuilder legalName(String legalName) {
            this.legalName = legalName;
            return this;
        }

        public MerchantUpdateRequestBuilder tradingName(String tradingName) {
            this.tradingName = tradingName;
            return this;
        }

        public MerchantUpdateRequestBuilder businessType(String businessType) {
            this.businessType = businessType;
            return this;
        }

        public MerchantUpdateRequestBuilder mcc(String mcc) {
            this.mcc = mcc;
            return this;
        }

        public MerchantUpdateRequestBuilder expectedMonthlyVolume(Long expectedMonthlyVolume) {
            this.expectedMonthlyVolume = expectedMonthlyVolume;
            return this;
        }

        public MerchantUpdateRequestBuilder website(String website) {
            this.website = website;
            return this;
        }

        public MerchantUpdateRequestBuilder addressStreet(String addressStreet) {
            this.addressStreet = addressStreet;
            return this;
        }

        public MerchantUpdateRequestBuilder addressCity(String addressCity) {
            this.addressCity = addressCity;
            return this;
        }

        public MerchantUpdateRequestBuilder addressState(String addressState) {
            this.addressState = addressState;
            return this;
        }

        public MerchantUpdateRequestBuilder addressPostalCode(String addressPostalCode) {
            this.addressPostalCode = addressPostalCode;
            return this;
        }

        public MerchantUpdateRequestBuilder addressCountry(String addressCountry) {
            this.addressCountry = addressCountry;
            return this;
        }

        public MerchantUpdateRequestBuilder contactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
            return this;
        }

        public MerchantUpdateRequestBuilder contactPhone(String contactPhone) {
            this.contactPhone = contactPhone;
            return this;
        }

        public MerchantUpdateRequestBuilder operatingCountries(List<String> operatingCountries) {
            this.operatingCountries = operatingCountries;
            return this;
        }

        public MerchantUpdateRequestBuilder newBeneficialOwners(List<BeneficialOwnerRequest> newBeneficialOwners) {
            this.newBeneficialOwners = newBeneficialOwners;
            return this;
        }

        public MerchantUpdateRequest build() {
            return new MerchantUpdateRequest(legalName, tradingName, businessType, mcc, expectedMonthlyVolume, website,
                    addressStreet, addressCity, addressState, addressPostalCode, addressCountry, contactEmail,
                    contactPhone, operatingCountries, newBeneficialOwners);
        }

        public String toString() {
            return "MerchantUpdateRequest.MerchantUpdateRequestBuilder(legalName=" + this.legalName + ", tradingName="
                    + this.tradingName + ", businessType=" + this.businessType + ", mcc=" + this.mcc
                    + ", expectedMonthlyVolume=" + this.expectedMonthlyVolume + ", website=" + this.website
                    + ", addressStreet=" + this.addressStreet + ", addressCity=" + this.addressCity + ", addressState="
                    + this.addressState + ", addressPostalCode=" + this.addressPostalCode + ", addressCountry="
                    + this.addressCountry + ", contactEmail=" + this.contactEmail + ", contactPhone="
                    + this.contactPhone + ", operatingCountries=" + this.operatingCountries + ", newBeneficialOwners="
                    + this.newBeneficialOwners + ")";
        }
    }
}
