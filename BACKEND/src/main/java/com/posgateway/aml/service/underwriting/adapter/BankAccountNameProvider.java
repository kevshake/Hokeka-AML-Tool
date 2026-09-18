package com.posgateway.aml.service.underwriting.adapter;

import com.posgateway.aml.model.underwriting.ProviderResult;
import com.posgateway.aml.service.underwriting.MerchantVerificationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Settlement bank-account-name verification (account-name enquiry / open banking /
 * microdeposit) confirming the settlement account exists and its name matches the legal
 * merchant (fail-closed adapter). Returns UNAVAILABLE until credentialed.
 */
@Component
public class BankAccountNameProvider extends AbstractExternalVerificationProvider {

    @Value("${underwriting.bankverify.enabled:false}")
    private boolean enabled;
    @Value("${underwriting.bankverify.api-url:}")
    private String apiUrl;
    @Value("${underwriting.bankverify.api-key:}")
    private String apiKey;

    @Override public String name() { return "BANK_ACCOUNT_NAME"; }
    @Override public String stage() { return "SETTLEMENT_VERIFICATION"; }

    @Override
    public boolean isConfigured() {
        return enabled && apiUrl != null && !apiUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    protected ProviderResult doVerify(MerchantVerificationContext ctx) {
        return ProviderResult.unavailable(name(), ctx.entityRef(),
                "Bank account-name verification enabled but live client not yet wired — credential the adapter to activate");
    }
}
