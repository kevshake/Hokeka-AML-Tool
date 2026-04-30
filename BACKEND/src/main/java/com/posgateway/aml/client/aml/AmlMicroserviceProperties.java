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
}
