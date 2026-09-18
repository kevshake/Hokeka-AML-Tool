package com.posgateway.aml.service.underwriting.adapter;

import com.posgateway.aml.model.underwriting.ProviderResult;
import com.posgateway.aml.service.underwriting.ExternalVerificationProvider;
import com.posgateway.aml.service.underwriting.MerchantVerificationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for external verification adapters. Enforces the fail-closed contract in one place:
 * if the provider is not configured, {@link #verify} returns UNAVAILABLE (forcing manual
 * review) and {@link #doVerify} is never reached; if a call throws, it becomes an ERROR
 * result (also manual review). A subclass only implements the real call in {@link #doVerify},
 * and until a vendor is credentialed {@link #isConfigured} stays false so nothing fabricates
 * a clearance.
 */
public abstract class AbstractExternalVerificationProvider implements ExternalVerificationProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public ProviderResult verify(MerchantVerificationContext context) {
        String ref = context.entityRef();
        if (!isConfigured()) {
            log.debug("{} not configured — returning UNAVAILABLE (manual review) for merchant {}", name(), ref);
            return ProviderResult.unavailable(name(), ref, name() + " is not configured");
        }
        try {
            return doVerify(context);
        } catch (Exception e) {
            log.warn("{} verification failed for merchant {}: {}", name(), ref, e.getMessage());
            return ProviderResult.error(name(), ref, e.getMessage());
        }
    }

    /**
     * Perform the real provider call. Only invoked when {@link #isConfigured()} is true.
     * A subclass that has an enabled flag but no live client yet must return
     * {@link ProviderResult#unavailable} — never a fabricated pass.
     */
    protected abstract ProviderResult doVerify(MerchantVerificationContext context);
}
