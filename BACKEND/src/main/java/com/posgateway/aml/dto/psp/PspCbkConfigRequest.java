package com.posgateway.aml.dto.psp;

/**
 * Admin-only request body for {@code PUT /psps/{id}/cbk-config}.
 *
 * <p>Mutating this resource changes the regulatory destination (live vs preprod
 * CBK GDI) for a PSP, which is a platform-level decision, not a tenant-level
 * one. The endpoint is restricted to {@code SUPER_ADMIN} / {@code ADMIN};
 * {@code PSP_ADMIN} can NOT call it on their own PSP.
 *
 * <p>All fields are optional and merged into the existing PSP — fields left
 * null are preserved.
 */
public class PspCbkConfigRequest {

    /** CBK-issued institution code (e.g. "0800015"). */
    private String cbkInstitutionCode;

    /** Master per-PSP toggle to participate in scheduled CBK submissions. */
    private Boolean cbkReportingEnabled;

    /** Per-PSP OAuth2 client_id (overrides global cbk.client-id when set). */
    private String cbkClientId;

    /** Per-PSP OAuth2 client_secret (overrides global cbk.client-secret when set). */
    private String cbkClientSecret;

    /** "preprod" (default, sandbox) or "live" (production GDI host). */
    private String cbkEnvironment;

    /**
     * Per-PSP live-allow flag. Even when set true, live submissions only fire
     * if the platform-level kill switch ({@code cbk.allow-live}) is also true.
     */
    private Boolean cbkAllowLive;

    public PspCbkConfigRequest() { }

    public String getCbkInstitutionCode() { return cbkInstitutionCode; }
    public void setCbkInstitutionCode(String cbkInstitutionCode) { this.cbkInstitutionCode = cbkInstitutionCode; }

    public Boolean getCbkReportingEnabled() { return cbkReportingEnabled; }
    public void setCbkReportingEnabled(Boolean cbkReportingEnabled) { this.cbkReportingEnabled = cbkReportingEnabled; }

    public String getCbkClientId() { return cbkClientId; }
    public void setCbkClientId(String cbkClientId) { this.cbkClientId = cbkClientId; }

    public String getCbkClientSecret() { return cbkClientSecret; }
    public void setCbkClientSecret(String cbkClientSecret) { this.cbkClientSecret = cbkClientSecret; }

    public String getCbkEnvironment() { return cbkEnvironment; }
    public void setCbkEnvironment(String cbkEnvironment) { this.cbkEnvironment = cbkEnvironment; }

    public Boolean getCbkAllowLive() { return cbkAllowLive; }
    public void setCbkAllowLive(Boolean cbkAllowLive) { this.cbkAllowLive = cbkAllowLive; }
}
