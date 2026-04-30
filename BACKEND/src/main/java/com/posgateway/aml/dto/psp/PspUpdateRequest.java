package com.posgateway.aml.dto.psp;

public class PspUpdateRequest {
    private String tradingName;
    private String contactEmail;
    private String contactPhone;
    private String contactAddress;
    private String brandingTheme;
    private String logoUrl;

    public PspUpdateRequest() {
    }

    public PspUpdateRequest(String tradingName, String contactEmail, String contactPhone, String contactAddress, String brandingTheme, String logoUrl) {
        this.tradingName = tradingName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.contactAddress = contactAddress;
        this.brandingTheme = brandingTheme;
        this.logoUrl = logoUrl;
    }

    public String getTradingName() {
        return tradingName;
    }

    public void setTradingName(String tradingName) {
        this.tradingName = tradingName;
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
}
