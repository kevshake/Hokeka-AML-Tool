package com.posgateway.aml.integration.cbk;

/**
 * Carries the per-PSP parameters needed to build a CBK GDI submission.
 * Built by callers from a Psp entity or configuration.
 *
 * <p>clientId / clientSecret may be null if the PSP uses the global fallback
 * credentials configured in {@link CbkProperties}.
 */
public final class PspCbkContext {

    private final Long pspId;
    private final String institutionCode;
    private final String clientId;
    private final String clientSecret;

    public PspCbkContext(Long pspId, String institutionCode, String clientId, String clientSecret) {
        if (pspId == null) throw new IllegalArgumentException("pspId must not be null");
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new IllegalArgumentException("institutionCode must not be blank");
        }
        this.pspId = pspId;
        this.institutionCode = institutionCode;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public Long getPspId() { return pspId; }
    public String getInstitutionCode() { return institutionCode; }
    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }

    @Override
    public String toString() {
        return "PspCbkContext{pspId=" + pspId + ", institutionCode='" + institutionCode + "'}";
    }
}
