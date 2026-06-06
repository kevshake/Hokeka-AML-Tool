package com.posgateway.aml.dto.psp;

import java.time.LocalDateTime;

public class PspResponse {
    private Long id;
    private String pspCode;
    private String legalName;
    private String tradingName;
    private String country;
    private String contactEmail;
    private String contactPhone;
    private String contactAddress;
    private String registrationNumber;
    private String taxId;
    private String status;
    private Boolean isTestMode;
    private String billingPlan;
    private String billingCycle;
    private String currency;
    private Integer paymentTerms;
    private Boolean cbkReportingEnabled;
    private String cbkEnvironment;
    private String logoUrl;
    private String primaryColor;
    private String secondaryColor;
    private String accentColor;
    private String fontFamily;
    private String brandingTheme;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime onboardedAt;

    public PspResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPspCode() { return pspCode; }
    public void setPspCode(String pspCode) { this.pspCode = pspCode; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public String getTradingName() { return tradingName; }
    public void setTradingName(String tradingName) { this.tradingName = tradingName; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactAddress() { return contactAddress; }
    public void setContactAddress(String contactAddress) { this.contactAddress = contactAddress; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getIsTestMode() { return isTestMode; }
    public void setIsTestMode(Boolean isTestMode) { this.isTestMode = isTestMode; }

    public String getBillingPlan() { return billingPlan; }
    public void setBillingPlan(String billingPlan) { this.billingPlan = billingPlan; }

    public String getBillingCycle() { return billingCycle; }
    public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(Integer paymentTerms) { this.paymentTerms = paymentTerms; }

    public Boolean getCbkReportingEnabled() { return cbkReportingEnabled; }
    public void setCbkReportingEnabled(Boolean cbkReportingEnabled) { this.cbkReportingEnabled = cbkReportingEnabled; }

    public String getCbkEnvironment() { return cbkEnvironment; }
    public void setCbkEnvironment(String cbkEnvironment) { this.cbkEnvironment = cbkEnvironment; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public String getBrandingTheme() { return brandingTheme; }
    public void setBrandingTheme(String brandingTheme) { this.brandingTheme = brandingTheme; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getOnboardedAt() { return onboardedAt; }
    public void setOnboardedAt(LocalDateTime onboardedAt) { this.onboardedAt = onboardedAt; }
}
