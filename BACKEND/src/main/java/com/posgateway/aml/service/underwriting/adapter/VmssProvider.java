package com.posgateway.aml.service.underwriting.adapter;

import com.posgateway.aml.model.underwriting.ProviderResult;
import com.posgateway.aml.service.underwriting.MerchantVerificationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Visa Merchant Screening Service (VMSS) inquiry (fail-closed adapter). CEMEA acquirers
 * (Kenya is in-region) must inquire before signing. Returns UNAVAILABLE until credentialed.
 */
@Component
public class VmssProvider extends AbstractExternalVerificationProvider {

    @Value("${underwriting.vmss.enabled:false}")
    private boolean enabled;
    @Value("${underwriting.vmss.api-url:}")
    private String apiUrl;
    @Value("${underwriting.vmss.api-key:}")
    private String apiKey;

    @Override public String name() { return "VMSS"; }
    @Override public String stage() { return "CARD_NETWORK_SCREENING"; }

    @Override
    public boolean isConfigured() {
        return enabled && apiUrl != null && !apiUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    protected ProviderResult doVerify(MerchantVerificationContext ctx) {
        return ProviderResult.unavailable(name(), ctx.entityRef(),
                "VMSS enabled but live client not yet wired — credential the adapter to activate");
    }
}
