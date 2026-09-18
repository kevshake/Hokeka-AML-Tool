package com.posgateway.aml.service.underwriting.adapter;

import com.posgateway.aml.model.underwriting.ProviderResult;
import com.posgateway.aml.service.underwriting.MerchantVerificationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Smile ID identity / document / biometric verification for owners & controllers
 * (fail-closed adapter). African government-data + document + selfie/liveness. Returns
 * UNAVAILABLE until credentialed.
 */
@Component
public class SmileIdProvider extends AbstractExternalVerificationProvider {

    @Value("${underwriting.smileid.enabled:false}")
    private boolean enabled;
    @Value("${underwriting.smileid.partner-id:}")
    private String partnerId;
    @Value("${underwriting.smileid.api-key:}")
    private String apiKey;

    @Override public String name() { return "SMILE_ID"; }
    @Override public String stage() { return "IDENTITY_BIOMETRIC"; }

    @Override
    public boolean isConfigured() {
        return enabled && partnerId != null && !partnerId.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    protected ProviderResult doVerify(MerchantVerificationContext ctx) {
        return ProviderResult.unavailable(name(), ctx.entityRef(),
                "Smile ID enabled but live client not yet wired — credential the adapter to activate");
    }
}
