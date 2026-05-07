package com.posgateway.aml.integration.cbk;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the CBK GDI integration.
 * Bound from {@code cbk.*} in application.properties.
 */
@Configuration
@ConfigurationProperties(prefix = "cbk")
public class CbkProperties {

    /** Whether CBK GDI submissions are enabled. */
    private boolean enabled = false;

    /** "live" or "preprod". */
    private String environment = "preprod";

    private Host host = new Host();
    private Scope scope = new Scope();

    /** Global fallback client_id (overridden per-PSP when CBK issues per-PSP creds). */
    private String clientId = "";

    /** Global fallback client_secret. */
    private String clientSecret = "";

    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;

    /** Seconds to subtract from expires_in so we refresh before the token actually expires. */
    private int tokenBufferSeconds = 10;

    // ---- nested records ----

    public static class Host {
        private String live = "gdicbk.centralbank.go.ke";
        private String preprod = "gdi.centralbank.go.ke";

        public String getLive() { return live; }
        public void setLive(String live) { this.live = live; }

        public String getPreprod() { return preprod; }
        public void setPreprod(String preprod) { this.preprod = preprod; }
    }

    public static class Scope {
        private String live = "";
        private String preprod = "";

        public String getLive() { return live; }
        public void setLive(String live) { this.live = live; }

        public String getPreprod() { return preprod; }
        public void setPreprod(String preprod) { this.preprod = preprod; }
    }

    // ---- computed helpers ----

    /**
     * Returns the active hostname based on {@code environment}.
     * "live" → gdicbk host; anything else → gdi host.
     */
    public String getActiveHost() {
        return "live".equalsIgnoreCase(environment) ? host.getLive() : host.getPreprod();
    }

    /**
     * Returns the OAuth2 scope for the active environment.
     */
    public String getActiveScope() {
        return "live".equalsIgnoreCase(environment) ? scope.getLive() : scope.getPreprod();
    }

    /**
     * Returns the URL prefix appended before API paths.
     * Live = "" (no prefix); pre-prod = "/preprod".
     */
    public String getPostPrefix() {
        return "live".equalsIgnoreCase(environment) ? "" : "/preprod";
    }

    /**
     * Returns the full base URL: {@code https://<activeHost>}.
     */
    public String getActiveBaseUrl() {
        return "https://" + getActiveHost();
    }

    // ---- standard getters/setters ----

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public Host getHost() { return host; }
    public void setHost(Host host) { this.host = host; }

    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public int getTokenBufferSeconds() { return tokenBufferSeconds; }
    public void setTokenBufferSeconds(int tokenBufferSeconds) { this.tokenBufferSeconds = tokenBufferSeconds; }
}
