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

    /** Authentication profile: HS256 for shared-secret JWS, RSA for PS256 over nested JWE. */
    private String authMode = "HS256";

    /** Visa/Verifi RSA public key or X.509 certificate used to verify PS256 signatures. */
    private String jwsVerificationPublicKey = "";

    /** Merchant PKCS#8 or PKCS#1 RSA private key used to decrypt nested JWE claims. */
    private String jweDecryptionPrivateKey = "";

    /** Optional expected signing key identifier from the outer JWS header. */
    private String jwsKeyId = "";

    /** Optional expected encryption key identifier from the inner JWE header. */
    private String jweKeyId = "";

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

    /** Verifi partner ID returned on Ping API responses (verifiEntityInfo.partnerId). */
    private Long partnerId;

    /** Verifi client ID returned on Ping API responses (verifiEntityInfo.clientId). */
    private Long clientId;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getAuthMode() { return authMode; }
    public void setAuthMode(String authMode) { this.authMode = authMode; }

    public String getJwsVerificationPublicKey() { return jwsVerificationPublicKey; }
    public void setJwsVerificationPublicKey(String jwsVerificationPublicKey) { this.jwsVerificationPublicKey = jwsVerificationPublicKey; }

    public String getJweDecryptionPrivateKey() { return jweDecryptionPrivateKey; }
    public void setJweDecryptionPrivateKey(String jweDecryptionPrivateKey) { this.jweDecryptionPrivateKey = jweDecryptionPrivateKey; }

    public String getJwsKeyId() { return jwsKeyId; }
    public void setJwsKeyId(String jwsKeyId) { this.jwsKeyId = jwsKeyId; }

    public String getJweKeyId() { return jweKeyId; }
    public void setJweKeyId(String jweKeyId) { this.jweKeyId = jweKeyId; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public boolean isSignatureRequired() { return signatureRequired; }
    public void setSignatureRequired(boolean signatureRequired) { this.signatureRequired = signatureRequired; }

    public boolean isAutoCreateCases() { return autoCreateCases; }
    public void setAutoCreateCases(boolean autoCreateCases) { this.autoCreateCases = autoCreateCases; }

    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
}
