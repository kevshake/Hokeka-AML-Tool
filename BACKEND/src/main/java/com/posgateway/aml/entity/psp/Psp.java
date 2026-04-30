package com.posgateway.aml.entity.psp;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PSP (Payment Service Provider) Entity
 * Represents a client organization using the AML screening service
 */
@Entity
@Table(name = "psps")
public class Psp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "psp_code", unique = true, nullable = false, length = 50)
    private String pspCode; // e.g., "MPESA_KE", "PAYPAL_US"

    // Company Details
    @Column(name = "legal_name", nullable = false, length = 500)
    private String legalName;

    @Column(name = "trading_name", length = 500)
    private String tradingName;

    @Column(name = "country", nullable = false, length = 3)
    private String country;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "tax_id", length = 100)
    private String taxId;

    // Contact Information
    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "contact_address", columnDefinition = "text")
    private String contactAddress;

    // Billing Configuration
    @Column(name = "billing_plan", length = 50)
    private String billingPlan = "PAY_AS_YOU_GO"; // PAY_AS_YOU_GO, SUBSCRIPTION

    @Column(name = "billing_cycle", length = 20)
    private String billingCycle = "MONTHLY"; // MONTHLY, QUARTERLY, YEARLY

    @Column(name = "payment_terms")
    private Integer paymentTerms = 30; // Days

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    // Status
    @Column(name = "status", nullable = false, length = 50)
    private String status = "PENDING"; // PENDING, ACTIVE, SUSPENDED, TERMINATED

    @Column(name = "is_test_mode")
    private Boolean isTestMode = false;

    // Theming
    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(name = "primary_color", length = 20)
    private String primaryColor;

    @Column(name = "secondary_color", length = 20)
    private String secondaryColor;

    @Column(name = "accent_color", length = 20)
    private String accentColor;

    @Column(name = "font_family", length = 100)
    private String fontFamily;

    @Column(name = "font_size", length = 20)
    private String fontSize; // e.g., "14px", "1rem"

    @Column(name = "button_radius", length = 20)
    private String buttonRadius; // e.g., "4px", "0.5rem"

    @Column(name = "button_style", length = 50)
    private String buttonStyle; // e.g., "filled", "outline", "ghost"

    @Column(name = "nav_style", length = 50)
    private String navStyle; // e.g., "drawer", "topbar"

    @Column(name = "branding_theme", length = 50)
    private String brandingTheme = "default"; // e.g., "default", "burgundy", "emerald", "purple"

    // Timestamps
    @Column(name = "onboarded_at")
    private LocalDateTime onboardedAt = LocalDateTime.now();

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Relationships
    @OneToMany(mappedBy = "psp", cascade = CascadeType.ALL)
    @com.fasterxml.jackson.annotation.JsonManagedReference("psp-users")
    private List<com.posgateway.aml.entity.User> users = new ArrayList<>();

    public Psp() {
    }

    public Psp(Long pspId, String pspCode, String legalName, String tradingName, String country,
            String registrationNumber, String taxId, String contactEmail, String contactPhone, String contactAddress,
            String billingPlan, String billingCycle, Integer paymentTerms, String currency, String status,
            Boolean isTestMode, String logoUrl, String primaryColor, String secondaryColor, String accentColor,
            String fontFamily, String fontSize, String buttonRadius, String buttonStyle, String navStyle, String brandingTheme,
            LocalDateTime onboardedAt, LocalDateTime activatedAt, LocalDateTime suspendedAt, LocalDateTime terminatedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt, List<com.posgateway.aml.entity.User> users) {
        this.pspId = pspId;
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
        this.billingCycle = billingCycle;
        this.paymentTerms = paymentTerms;
        this.currency = currency;
        this.status = status;
        this.isTestMode = isTestMode;
        this.logoUrl = logoUrl;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.accentColor = accentColor;
        this.fontFamily = fontFamily;
        this.fontSize = fontSize;
        this.buttonRadius = buttonRadius;
        this.buttonStyle = buttonStyle;
        this.navStyle = navStyle;
        this.brandingTheme = brandingTheme;
        this.onboardedAt = onboardedAt;
        this.activatedAt = activatedAt;
        this.suspendedAt = suspendedAt;
        this.terminatedAt = terminatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.users = users;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
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

    public String getBillingCycle() {
        return billingCycle;
    }

    public void setBillingCycle(String billingCycle) {
        this.billingCycle = billingCycle;
    }

    public Integer getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(Integer paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsTestMode() {
        return isTestMode;
    }

    public void setIsTestMode(Boolean isTestMode) {
        this.isTestMode = isTestMode;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public String getFontSize() {
        return fontSize;
    }

    public void setFontSize(String fontSize) {
        this.fontSize = fontSize;
    }

    public String getButtonRadius() {
        return buttonRadius;
    }

    public void setButtonRadius(String buttonRadius) {
        this.buttonRadius = buttonRadius;
    }

    public String getButtonStyle() {
        return buttonStyle;
    }

    public void setButtonStyle(String buttonStyle) {
        this.buttonStyle = buttonStyle;
    }

    public String getNavStyle() {
        return navStyle;
    }

    public void setNavStyle(String navStyle) {
        this.navStyle = navStyle;
    }

    public String getBrandingTheme() {
        return brandingTheme;
    }

    public void setBrandingTheme(String brandingTheme) {
        this.brandingTheme = brandingTheme;
    }

    public LocalDateTime getOnboardedAt() {
        return onboardedAt;
    }

    public void setOnboardedAt(LocalDateTime onboardedAt) {
        this.onboardedAt = onboardedAt;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public LocalDateTime getSuspendedAt() {
        return suspendedAt;
    }

    public void setSuspendedAt(LocalDateTime suspendedAt) {
        this.suspendedAt = suspendedAt;
    }

    public LocalDateTime getTerminatedAt() {
        return terminatedAt;
    }

    public void setTerminatedAt(LocalDateTime terminatedAt) {
        this.terminatedAt = terminatedAt;
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

    public List<com.posgateway.aml.entity.User> getUsers() {
        return users;
    }

    public void setUsers(List<com.posgateway.aml.entity.User> users) {
        this.users = users;
    }

    public static PspBuilder builder() {
        return new PspBuilder();
    }

    public static class PspBuilder {
        private Long pspId;
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
        private String billingCycle;
        private Integer paymentTerms;
        private String currency;
        private String status;
        private Boolean isTestMode;
        private String logoUrl;
        private String primaryColor;
        private String secondaryColor;
        private String accentColor;
        private String fontFamily;
        private String fontSize;
        private String buttonRadius;
        private String buttonStyle;
        private String navStyle;
        private String brandingTheme;
        private LocalDateTime onboardedAt;
        private LocalDateTime activatedAt;
        private LocalDateTime suspendedAt;
        private LocalDateTime terminatedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<com.posgateway.aml.entity.User> users;

        PspBuilder() {
        }

        public PspBuilder pspId(Long pspId) {
            this.pspId = pspId;
            return this;
        }

        public PspBuilder pspCode(String pspCode) {
            this.pspCode = pspCode;
            return this;
        }

        public PspBuilder legalName(String legalName) {
            this.legalName = legalName;
            return this;
        }

        public PspBuilder tradingName(String tradingName) {
            this.tradingName = tradingName;
            return this;
        }

        public PspBuilder country(String country) {
            this.country = country;
            return this;
        }

        public PspBuilder registrationNumber(String registrationNumber) {
            this.registrationNumber = registrationNumber;
            return this;
        }

        public PspBuilder taxId(String taxId) {
            this.taxId = taxId;
            return this;
        }

        public PspBuilder contactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
            return this;
        }

        public PspBuilder contactPhone(String contactPhone) {
            this.contactPhone = contactPhone;
            return this;
        }

        public PspBuilder contactAddress(String contactAddress) {
            this.contactAddress = contactAddress;
            return this;
        }

        public PspBuilder billingPlan(String billingPlan) {
            this.billingPlan = billingPlan;
            return this;
        }

        public PspBuilder billingCycle(String billingCycle) {
            this.billingCycle = billingCycle;
            return this;
        }

        public PspBuilder paymentTerms(Integer paymentTerms) {
            this.paymentTerms = paymentTerms;
            return this;
        }

        public PspBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public PspBuilder status(String status) {
            this.status = status;
            return this;
        }

        public PspBuilder isTestMode(Boolean isTestMode) {
            this.isTestMode = isTestMode;
            return this;
        }

        public PspBuilder logoUrl(String logoUrl) {
            this.logoUrl = logoUrl;
            return this;
        }

        public PspBuilder primaryColor(String primaryColor) {
            this.primaryColor = primaryColor;
            return this;
        }

        public PspBuilder secondaryColor(String secondaryColor) {
            this.secondaryColor = secondaryColor;
            return this;
        }

        public PspBuilder accentColor(String accentColor) {
            this.accentColor = accentColor;
            return this;
        }

        public PspBuilder fontFamily(String fontFamily) {
            this.fontFamily = fontFamily;
            return this;
        }

        public PspBuilder fontSize(String fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public PspBuilder buttonRadius(String buttonRadius) {
            this.buttonRadius = buttonRadius;
            return this;
        }

        public PspBuilder buttonStyle(String buttonStyle) {
            this.buttonStyle = buttonStyle;
            return this;
        }

        public PspBuilder navStyle(String navStyle) {
            this.navStyle = navStyle;
            return this;
        }

        public PspBuilder onboardedAt(LocalDateTime onboardedAt) {
            this.onboardedAt = onboardedAt;
            return this;
        }

        public PspBuilder activatedAt(LocalDateTime activatedAt) {
            this.activatedAt = activatedAt;
            return this;
        }

        public PspBuilder suspendedAt(LocalDateTime suspendedAt) {
            this.suspendedAt = suspendedAt;
            return this;
        }

        public PspBuilder terminatedAt(LocalDateTime terminatedAt) {
            this.terminatedAt = terminatedAt;
            return this;
        }

        public PspBuilder brandingTheme(String brandingTheme) {
            this.brandingTheme = brandingTheme;
            return this;
        }

        public PspBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public PspBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public PspBuilder users(List<com.posgateway.aml.entity.User> users) {
            this.users = users;
            return this;
        }

        public Psp build() {
            // Defaults
            if (this.billingPlan == null)
                this.billingPlan = "PAY_AS_YOU_GO";
            if (this.billingCycle == null)
                this.billingCycle = "MONTHLY";
            if (this.paymentTerms == null)
                this.paymentTerms = 30;
            if (this.currency == null)
                this.currency = "USD";
            if (this.status == null)
                this.status = "PENDING";
            if (this.isTestMode == null)
                this.isTestMode = false;
            if (this.brandingTheme == null)
                this.brandingTheme = "default";
            if (this.onboardedAt == null)
                this.onboardedAt = LocalDateTime.now();
            if (this.createdAt == null)
                this.createdAt = LocalDateTime.now();
            if (this.updatedAt == null)
                this.updatedAt = LocalDateTime.now();
            if (this.users == null)
                this.users = new ArrayList<>();

            return new Psp(pspId, pspCode, legalName, tradingName, country, registrationNumber, taxId, contactEmail,
                    contactPhone, contactAddress, billingPlan, billingCycle, paymentTerms, currency, status, isTestMode,
                    logoUrl, primaryColor, secondaryColor, accentColor, fontFamily, fontSize, buttonRadius, buttonStyle,
                    navStyle, brandingTheme, onboardedAt, activatedAt, suspendedAt, terminatedAt, createdAt, updatedAt, users);
        }

        public String toString() {
            return "Psp.PspBuilder(pspId=" + this.pspId + ", pspCode=" + this.pspCode + ", legalName=" + this.legalName
                    + ", tradingName=" + this.tradingName + ", country=" + this.country + ", registrationNumber="
                    + this.registrationNumber + ", taxId=" + this.taxId + ", contactEmail=" + this.contactEmail
                    + ", contactPhone=" + this.contactPhone + ", contactAddress=" + this.contactAddress
                    + ", billingPlan=" + this.billingPlan + ", billingCycle=" + this.billingCycle + ", paymentTerms="
                    + this.paymentTerms + ", currency=" + this.currency + ", status=" + this.status + ", isTestMode="
                    + this.isTestMode + ", logoUrl=" + this.logoUrl + ", primaryColor=" + this.primaryColor
                    + ", secondaryColor=" + this.secondaryColor + ", accentColor=" + this.accentColor + ", fontFamily="
                    + this.fontFamily + ", fontSize=" + this.fontSize + ", buttonRadius=" + this.buttonRadius
                    + ", buttonStyle=" + this.buttonStyle + ", navStyle=" + this.navStyle + ", brandingTheme=" + this.brandingTheme + ", onboardedAt="
                    + this.onboardedAt + ", activatedAt=" + this.activatedAt + ", suspendedAt=" + this.suspendedAt
                    + ", terminatedAt=" + this.terminatedAt + ", createdAt=" + this.createdAt + ", updatedAt="
                    + this.updatedAt + ", users=" + this.users + ")";
        }
    }

    // Helper Methods
    public String getPspCode() {
        return pspCode;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public void activate() {
        this.status = "ACTIVE";
        this.activatedAt = LocalDateTime.now();
    }

    public void suspend(String reason) {
        this.status = "SUSPENDED";
        this.suspendedAt = LocalDateTime.now();
    }

    public void terminate() {
        this.status = "TERMINATED";
        this.terminatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
