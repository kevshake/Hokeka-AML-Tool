package com.posgateway.aml.integration.cbk;

/**
 * Carries the per-PSP parameters needed to build a CBK GDI submission.
 * Built by callers from a Psp entity or configuration.
 *
 * <p>clientId / clientSecret may be null if the PSP uses the global fallback
 * credentials configured in {@link CbkProperties}.
 *
 * <p>{@link #liveEffective} is the AND of: global {@code cbk.allow-live},
 * per-PSP {@code cbkAllowLive}, and per-PSP {@code cbkEnvironment="live"}.
 * The resolver computes it; downstream code never recomputes — it just routes
 * to the live URL when this is true and to preprod otherwise.
 */
public final class PspCbkContext {

    private final Long pspId;
    private final String institutionCode;
    private final String clientId;
    private final String clientSecret;
    private final boolean liveEffective;

    public PspCbkContext(Long pspId, String institutionCode, String clientId, String clientSecret,
                         boolean liveEffective) {
        if (pspId == null) throw new IllegalArgumentException("pspId must not be null");
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new IllegalArgumentException("institutionCode must not be blank");
        }
        this.pspId = pspId;
        this.institutionCode = institutionCode;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.liveEffective = liveEffective;
    }

    /** Backwards-compatible constructor — defaults to preprod. */
    public PspCbkContext(Long pspId, String institutionCode, String clientId, String clientSecret) {
        this(pspId, institutionCode, clientId, clientSecret, false);
    }

    public Long getPspId() { return pspId; }
    public String getInstitutionCode() { return institutionCode; }
    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public boolean isLiveEffective() { return liveEffective; }

    @Override
    public String toString() {
        return "PspCbkContext{pspId=" + pspId
                + ", institutionCode='" + institutionCode + "'"
                + ", env=" + (liveEffective ? "LIVE" : "preprod") + "}";
    }
}
