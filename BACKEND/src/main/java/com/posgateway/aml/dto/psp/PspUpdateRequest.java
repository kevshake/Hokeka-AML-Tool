package com.posgateway.aml.dto.psp;

public class PspUpdateRequest {
    private String legalName;
    private String tradingName;
    private String country;
    private String registrationNumber;
    private String taxId;
    private String contactEmail;
    private String contactPhone;
    private String contactAddress;
    private String billingPlan;
    private String billingCycle;
    private String currency;
    private Integer paymentTerms;
    private Boolean isTestMode;
    private String brandingTheme;
    private String logoUrl;
    private String primaryColor;
    private String secondaryColor;
    private String accentColor;

    public PspUpdateRequest() {}

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public String getTradingName() { return tradingName; }
    public void setTradingName(String tradingName) { this.tradingName = tradingName; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactAddress() { return contactAddress; }
    public void setContactAddress(String contactAddress) { this.contactAddress = contactAddress; }

    public String getBillingPlan() { return billingPlan; }
    public void setBillingPlan(String billingPlan) { this.billingPlan = billingPlan; }

    public String getBillingCycle() { return billingCycle; }
    public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(Integer paymentTerms) { this.paymentTerms = paymentTerms; }

    public Boolean getIsTestMode() { return isTestMode; }
    public void setIsTestMode(Boolean isTestMode) { this.isTestMode = isTestMode; }

    public String getBrandingTheme() { return brandingTheme; }
    public void setBrandingTheme(String brandingTheme) { this.brandingTheme = brandingTheme; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }
}
