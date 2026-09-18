package com.posgateway.aml.service.underwriting.adapter;

import com.posgateway.aml.model.underwriting.ProviderResult;
import com.posgateway.aml.service.underwriting.MerchantVerificationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Authoritative company-registry verification (e.g. Kenya BRS) confirming the entity
 * exists, is active, and its directors/registration match the application (fail-closed
 * adapter). Returns UNAVAILABLE until credentialed.
 *
 * <p>Note: the existing OpenCorporates integration provides secondary registry
 * intelligence during onboarding; this adapter is the slot for a primary authoritative
 * registry (BRS) once credentialed.
 */
@Component
public class RegistryVerificationProvider extends AbstractExternalVerificationProvider {

    @Value("${underwriting.registry.enabled:false}")
    private boolean enabled;
    @Value("${underwriting.registry.api-url:}")
    private String apiUrl;
    @Value("${underwriting.registry.api-key:}")
    private String apiKey;

    @Override public String name() { return "REGISTRY"; }
    @Override public String stage() { return "LEGAL_ENTITY_VERIFICATION"; }

    @Override
    public boolean isConfigured() {
        return enabled && apiUrl != null && !apiUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    protected ProviderResult doVerify(MerchantVerificationContext ctx) {
        return ProviderResult.unavailable(name(), ctx.entityRef(),
                "Registry verification enabled but live client not yet wired — credential the adapter to activate");
    }
}
