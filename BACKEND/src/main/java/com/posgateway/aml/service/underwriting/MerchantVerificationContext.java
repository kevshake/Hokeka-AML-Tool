package com.posgateway.aml.service.underwriting;

import com.posgateway.aml.entity.merchant.Merchant;

/**
 * Immutable input handed to every verification check/adapter for one underwriting run.
 * Carries the merchant under review and the run correlation id.
 */
public final class MerchantVerificationContext {

    private final Merchant merchant;
    private final String runId;

    public MerchantVerificationContext(Merchant merchant, String runId) {
        this.merchant = merchant;
        this.runId = runId;
    }

    public Merchant getMerchant() { return merchant; }
    public String getRunId() { return runId; }

    /** Convenience: the merchant's persisted id as an entity reference string. */
    public String entityRef() {
        return merchant != null && merchant.getMerchantId() != null
                ? String.valueOf(merchant.getMerchantId()) : "unknown";
    }
}
