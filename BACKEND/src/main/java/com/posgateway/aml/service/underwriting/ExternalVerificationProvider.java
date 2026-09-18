package com.posgateway.aml.service.underwriting;

import com.posgateway.aml.model.underwriting.ProviderResult;

/**
 * Adapter contract for an external merchant-verification provider (card-network screening
 * such as MATCH Pro / VMSS, identity/biometric verification, authoritative registry lookup,
 * bank-account-name verification, etc.).
 *
 * <p>Implementations MUST be fail-closed: when the provider is not configured / not
 * credentialed / unreachable they return {@link ProviderResult#unavailable} (which forces
 * manual review), never a fabricated clearance. This is what lets the platform ship the
 * full underwriting flow before any vendor contract exists — the flow runs, and every
 * un-credentialed stage visibly parks the merchant for a human instead of passing it.
 */
public interface ExternalVerificationProvider {

    /** Stable provider name, e.g. {@code "MATCH_PRO"}, {@code "VMSS"}, {@code "SMILE_ID"}. */
    String name();

    /** Which verification stage this provider serves (for reporting/ordering). */
    String stage();

    /** True when real credentials/config are present so the provider can actually call out. */
    boolean isConfigured();

    /** Run the check. Must return UNAVAILABLE (not a clear) when {@link #isConfigured()} is false. */
    ProviderResult verify(MerchantVerificationContext context);
}
