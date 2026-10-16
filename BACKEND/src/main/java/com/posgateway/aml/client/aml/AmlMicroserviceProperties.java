package com.posgateway.aml.client.aml;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Tunables for the AML microservice client (see {@link AmlMicroserviceClient}).
 *
 * <p>Bound from {@code aml.microservice.*} properties.
 */
@Configuration
@ConfigurationProperties(prefix = "aml.microservice")
public class AmlMicroserviceProperties {

    private String baseUrl = "http://aml-ms:8091";
    private boolean enabled = false;
    private String internalAuthKey = "";
    private int connectTimeoutMs = 200;
    private int readTimeoutMs = 400;
    /**
     * Timeout for the sanctions count probe ({@code GET /internal/v1/sanctions/count}) used by the
     * {@code sanctionsData} health indicator, the startup check and coverage reporting. Separate from
     * {@link #readTimeoutMs}, which is sized for the latency-critical screening path.
     */
    private int countTimeoutMs = 3000;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getInternalAuthKey() { return internalAuthKey; }
    public void setInternalAuthKey(String internalAuthKey) { this.internalAuthKey = internalAuthKey; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    /** Timeout for the sanctions count probe, in milliseconds. */
    public int getCountTimeoutMs() { return countTimeoutMs; }
    /** Sets the sanctions count probe timeout, in milliseconds. */
    public void setCountTimeoutMs(int countTimeoutMs) { this.countTimeoutMs = countTimeoutMs; }
}
