package com.posgateway.aml.integration.mpesa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the Safaricom Daraja (M-Pesa) API.
 * Bound from {@code mpesa.*} in application.properties.
 */
@Configuration
@ConfigurationProperties(prefix = "mpesa")
public class MpesaProperties {

    private String consumerKey = "";
    private String consumerSecret = "";

    /** Business short code (PayBill or Till number). */
    private String shortCode = "174379";

    /** Lipa na M-Pesa Online passkey (from Daraja portal). */
    private String passkey = "";

    /** Public URL that Safaricom will POST the STK push callback to. */
    private String callbackUrl = "";

    /** "sandbox" or "production". */
    private String environment = "sandbox";

    /**
     * Returns the Daraja base URL for the active environment.
     */
    public String getBaseUrl() {
        return "production".equalsIgnoreCase(environment)
                ? "https://api.safaricom.co.ke"
                : "https://sandbox.safaricom.co.ke";
    }

    // ─── Getters / Setters ───────────────────────────────────────────────────

    public String getConsumerKey() { return consumerKey; }
    public void setConsumerKey(String consumerKey) { this.consumerKey = consumerKey; }

    public String getConsumerSecret() { return consumerSecret; }
    public void setConsumerSecret(String consumerSecret) { this.consumerSecret = consumerSecret; }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public String getPasskey() { return passkey; }
    public void setPasskey(String passkey) { this.passkey = passkey; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
}
