package com.posgateway.aml.integration.verifi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for Visa/Verifi Rapid Dispute Resolution (RDR) webhook integration.
 * Bound from {@code verifi.rdr.*} in application.properties.
 */
@Component
@ConfigurationProperties(prefix = "verifi.rdr")
public class VerifiRdrProperties {

    /** Master switch for RDR webhook processing. */
    private boolean enabled = false;

    /** HMAC signing key shared with Verifi or your PSP/gateway partner. */
    private String webhookSecret = "";

    /**
     * Public callback URL registered with Verifi (informational; used in startup logs).
     * Example: https://api.example.com/api/v1/integrations/verifi/rdr
     */
    private String callbackUrl = "";

    /** Optional API key header value for partners that use X-Api-Key instead of HMAC. */
    private String apiKey = "";

    /** When true, reject webhooks with invalid signatures. When false, log and accept (dev only). */
    private boolean signatureRequired = true;

    /** Auto-create compliance cases for accepted RDR / fraud-category disputes. */
    private boolean autoCreateCases = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public boolean isSignatureRequired() { return signatureRequired; }
    public void setSignatureRequired(boolean signatureRequired) { this.signatureRequired = signatureRequired; }

    public boolean isAutoCreateCases() { return autoCreateCases; }
    public void setAutoCreateCases(boolean autoCreateCases) { this.autoCreateCases = autoCreateCases; }
}
