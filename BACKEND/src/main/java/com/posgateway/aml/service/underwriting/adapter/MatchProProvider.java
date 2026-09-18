package com.posgateway.aml.service.underwriting.adapter;

import com.posgateway.aml.model.underwriting.ProviderResult;
import com.posgateway.aml.service.underwriting.MerchantVerificationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mastercard MATCH Pro terminated-merchant screening (fail-closed adapter).
 * Requires a Mastercard MATCH program contract + credentials; until then it returns
 * UNAVAILABLE so the merchant is parked for manual review rather than passed.
 */
@Component
public class MatchProProvider extends AbstractExternalVerificationProvider {

    @Value("${underwriting.matchpro.enabled:false}")
    private boolean enabled;
    @Value("${underwriting.matchpro.api-url:}")
    private String apiUrl;
    @Value("${underwriting.matchpro.api-key:}")
    private String apiKey;

    @Override public String name() { return "MATCH_PRO"; }
    @Override public String stage() { return "CARD_NETWORK_SCREENING"; }

    @Override
    public boolean isConfigured() {
        return enabled && apiUrl != null && !apiUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    protected ProviderResult doVerify(MerchantVerificationContext ctx) {
        return ProviderResult.unavailable(name(), ctx.entityRef(),
                "MATCH Pro enabled but live client not yet wired — credential the adapter to activate");
    }
}
