package com.posgateway.aml.dto.psp;

public class PspRegistrationRequest {
    private String pspCode;
    private String legalName;
    private String tradingName;
    private String country;
    private String registrationNumber;
    private String taxId;
    private String contactEmail;
    private String contactPhone;
    private String contactAddress;
    private String billingPlan;
    private String currency;
    private Integer paymentTerms;
    private String brandingTheme;
    private String logoUrl;

    public PspRegistrationRequest() {
    }

    public PspRegistrationRequest(String pspCode, String legalName, String tradingName, String country,
            String registrationNumber, String taxId, String contactEmail, String contactPhone, String contactAddress,
            String billingPlan, String currency, Integer paymentTerms, String brandingTheme, String logoUrl) {
        this.pspCode = pspCode;
        this.legalName = legalName;
        this.tradingName = tradingName;
        this.country = country;
        this.registrationNumber = registrationNumber;
        this.taxId = taxId;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.contactAddress = contactAddress;
        this.billingPlan = billingPlan;
        this.currency = currency;
        this.paymentTerms = paymentTerms;
        this.brandingTheme = brandingTheme;
        this.logoUrl = logoUrl;
    }

    public String getPspCode() {
        return pspCode;
    }

    public void setPspCode(String pspCode) {
        this.pspCode = pspCode;
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

    public String getContactAddress() {
        return contactAddress;
    }

    public void setContactAddress(String contactAddress) {
        this.contactAddress = contactAddress;
    }

    public String getBillingPlan() {
        return billingPlan;
    }

    public void setBillingPlan(String billingPlan) {
        this.billingPlan = billingPlan;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(Integer paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public String getBrandingTheme() {
        return brandingTheme;
    }

    public void setBrandingTheme(String brandingTheme) {
        this.brandingTheme = brandingTheme;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public static PspRegistrationRequestBuilder builder() {
        return new PspRegistrationRequestBuilder();
    }

    public static class PspRegistrationRequestBuilder {
        private String pspCode;
        private String legalName;
        private String tradingName;
        private String country;
        private String registrationNumber;
        private String taxId;
        private String contactEmail;
        private String contactPhone;
        private String contactAddress;
        private String billingPlan;
        private String currency;
        private Integer paymentTerms;
        private String brandingTheme;
        private String logoUrl;

        PspRegistrationRequestBuilder() {
        }

        public PspRegistrationRequestBuilder pspCode(String pspCode) {
            this.pspCode = pspCode;
            return this;
        }

        public PspRegistrationRequestBuilder legalName(String legalName) {
            this.legalName = legalName;
            return this;
        }

        public PspRegistrationRequestBuilder tradingName(String tradingName) {
            this.tradingName = tradingName;
            return this;
        }

        public PspRegistrationRequestBuilder country(String country) {
            this.country = country;
            return this;
        }

        public PspRegistrationRequestBuilder registrationNumber(String registrationNumber) {
            this.registrationNumber = registrationNumber;
            return this;
        }

        public PspRegistrationRequestBuilder taxId(String taxId) {
            this.taxId = taxId;
            return this;
        }

        public PspRegistrationRequestBuilder contactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
            return this;
        }

        public PspRegistrationRequestBuilder contactPhone(String contactPhone) {
            this.contactPhone = contactPhone;
            return this;
        }

        public PspRegistrationRequestBuilder contactAddress(String contactAddress) {
            this.contactAddress = contactAddress;
            return this;
        }

        public PspRegistrationRequestBuilder billingPlan(String billingPlan) {
            this.billingPlan = billingPlan;
            return this;
        }

        public PspRegistrationRequestBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public PspRegistrationRequestBuilder paymentTerms(Integer paymentTerms) {
            this.paymentTerms = paymentTerms;
            return this;
        }

        public PspRegistrationRequestBuilder brandingTheme(String brandingTheme) {
            this.brandingTheme = brandingTheme;
            return this;
        }

        public PspRegistrationRequestBuilder logoUrl(String logoUrl) {
            this.logoUrl = logoUrl;
            return this;
        }

        public PspRegistrationRequest build() {
            return new PspRegistrationRequest(pspCode, legalName, tradingName, country, registrationNumber, taxId,
                    contactEmail, contactPhone, contactAddress, billingPlan, currency, paymentTerms, brandingTheme, logoUrl);
        }

        public String toString() {
            return "PspRegistrationRequest.PspRegistrationRequestBuilder(pspCode=" + this.pspCode + ", legalName="
                    + this.legalName + ", tradingName=" + this.tradingName + ", country=" + this.country
                    + ", registrationNumber=" + this.registrationNumber + ", taxId=" + this.taxId + ", contactEmail="
                    + this.contactEmail + ", contactPhone=" + this.contactPhone + ", contactAddress="
                    + this.contactAddress + ", billingPlan=" + this.billingPlan + ", currency=" + this.currency
                    + ", paymentTerms=" + this.paymentTerms + ")";
        }
    }
}
