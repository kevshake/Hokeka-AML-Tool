package com.posgateway.aml.dto.psp;

/**
 * Read-side projection of CBK-related PSP configuration. Returned by
 * {@code GET /psps/{id}/cbk-config} and {@code PUT /psps/{id}/cbk-config}.
 *
 * <p>The {@code cbkClientSecret} field is intentionally omitted from this
 * response — secrets are never round-tripped to the FE. The presence of a
 * configured secret is signalled by {@link #hasClientSecret}.
 */
public class PspCbkConfigResponse {

    private Long pspId;
    private String pspCode;
    private String legalName;

    private String cbkInstitutionCode;
    private Boolean cbkReportingEnabled;
    private String cbkEnvironment;
    private Boolean cbkAllowLive;
    private String cbkClientId;
    private boolean hasClientSecret;

    /** Effective live status — combines per-PSP fields with global allow-live. */
    private boolean liveEffective;

    public PspCbkConfigResponse() { }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getPspCode() { return pspCode; }
    public void setPspCode(String pspCode) { this.pspCode = pspCode; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public String getCbkInstitutionCode() { return cbkInstitutionCode; }
    public void setCbkInstitutionCode(String cbkInstitutionCode) { this.cbkInstitutionCode = cbkInstitutionCode; }

    public Boolean getCbkReportingEnabled() { return cbkReportingEnabled; }
    public void setCbkReportingEnabled(Boolean cbkReportingEnabled) { this.cbkReportingEnabled = cbkReportingEnabled; }

    public String getCbkEnvironment() { return cbkEnvironment; }
    public void setCbkEnvironment(String cbkEnvironment) { this.cbkEnvironment = cbkEnvironment; }

    public Boolean getCbkAllowLive() { return cbkAllowLive; }
    public void setCbkAllowLive(Boolean cbkAllowLive) { this.cbkAllowLive = cbkAllowLive; }

    public String getCbkClientId() { return cbkClientId; }
    public void setCbkClientId(String cbkClientId) { this.cbkClientId = cbkClientId; }

    public boolean isHasClientSecret() { return hasClientSecret; }
    public void setHasClientSecret(boolean hasClientSecret) { this.hasClientSecret = hasClientSecret; }

    public boolean isLiveEffective() { return liveEffective; }
    public void setLiveEffective(boolean liveEffective) { this.liveEffective = liveEffective; }
}
