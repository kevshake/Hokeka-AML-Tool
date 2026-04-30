package com.posgateway.aml.dto.psp;

public class PspResponse {
    private Long id;
    private String pspCode;
    private String legalName;
    private String status;
    private String billingPlan;
    private String brandingTheme;

    public PspResponse() {
    }

    public PspResponse(Long id, String pspCode, String legalName, String status, String billingPlan, String brandingTheme) {
        this.id = id;
        this.pspCode = pspCode;
        this.legalName = legalName;
        this.status = status;
        this.billingPlan = billingPlan;
        this.brandingTheme = brandingTheme;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBillingPlan() {
        return billingPlan;
    }

    public void setBillingPlan(String billingPlan) {
        this.billingPlan = billingPlan;
    }

    public String getBrandingTheme() {
        return brandingTheme;
    }

    public void setBrandingTheme(String brandingTheme) {
        this.brandingTheme = brandingTheme;
    }

    public static PspResponseBuilder builder() {
        return new PspResponseBuilder();
    }

    public static class PspResponseBuilder {
        private Long id;
        private String pspCode;
        private String legalName;
        private String status;
        private String billingPlan;
        private String brandingTheme;

        PspResponseBuilder() {
        }

        public PspResponseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public PspResponseBuilder pspCode(String pspCode) {
            this.pspCode = pspCode;
            return this;
        }

        public PspResponseBuilder legalName(String legalName) {
            this.legalName = legalName;
            return this;
        }

        public PspResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public PspResponseBuilder billingPlan(String billingPlan) {
            this.billingPlan = billingPlan;
            return this;
        }

        public PspResponseBuilder brandingTheme(String brandingTheme) {
            this.brandingTheme = brandingTheme;
            return this;
        }

        public PspResponse build() {
            return new PspResponse(id, pspCode, legalName, status, billingPlan, brandingTheme);
        }

        public String toString() {
            return "PspResponse.PspResponseBuilder(id=" + this.id + ", pspCode=" + this.pspCode + ", legalName="
                    + this.legalName + ", status=" + this.status + ", billingPlan=" + this.billingPlan + ", brandingTheme=" + this.brandingTheme + ")";
        }
    }
}
