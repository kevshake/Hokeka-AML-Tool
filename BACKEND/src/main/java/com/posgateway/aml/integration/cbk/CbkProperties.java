package com.posgateway.aml.integration.cbk;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the CBK GDI integration.
 * Bound from {@code cbk.*} in application.properties.
 *
 * <p><b>Environment lock:</b> the live CBK GDI environment is intentionally
 * disabled in this build. {@link #getActiveEnvironment()} always returns
 * {@code preprod} regardless of what {@code cbk.environment} is set to. To
 * promote to live, set {@link #allowLive} to {@code true} via
 * {@code cbk.allow-live=true} (or the {@code CBK_ALLOW_LIVE} env var) — and
 * only do that once the institution code, scopes, and per-PSP credentials are
 * provisioned for production.
 */
@Configuration
@ConfigurationProperties(prefix = "cbk")
public class CbkProperties {

    private static final Logger log = LoggerFactory.getLogger(CbkProperties.class);

    /** Whether CBK GDI submissions are enabled. */
    private boolean enabled = false;

    /** Requested environment: "live" or "preprod". May be overridden by the live-lock. */
    private String environment = "preprod";

    /**
     * Hard safety lock. While {@code false} (the default) the integration
     * always uses the {@code preprod} host/scope/URL prefix even if
     * {@code cbk.environment=live} is configured. Flip this to {@code true}
     * only when going to production for real.
     */
    private boolean allowLive = false;

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
     * Effective environment after the live-lock is applied. While
     * {@link #allowLive} is {@code false} this always returns {@code "preprod"};
     * any attempt to configure {@code cbk.environment=live} is logged as a
     * warning and silently coerced to preprod.
     */
    public String getActiveEnvironment() {
        if ("live".equalsIgnoreCase(environment) && !allowLive) {
            return "preprod";
        }
        return environment;
    }

    /**
     * Returns the active hostname based on the resolved environment.
     */
    public String getActiveHost() {
        return "live".equalsIgnoreCase(getActiveEnvironment()) ? host.getLive() : host.getPreprod();
    }

    /**
     * Returns the OAuth2 scope for the active environment.
     */
    public String getActiveScope() {
        return "live".equalsIgnoreCase(getActiveEnvironment()) ? scope.getLive() : scope.getPreprod();
    }

    /**
     * Returns the URL prefix appended before API paths.
     * Live = "" (no prefix); pre-prod = "/preprod".
     */
    public String getPostPrefix() {
        return "live".equalsIgnoreCase(getActiveEnvironment()) ? "" : "/preprod";
    }

    /**
     * Returns the full base URL: {@code https://<activeHost>}.
     */
    public String getActiveBaseUrl() {
        return "https://" + getActiveHost();
    }

    @PostConstruct
    void announceEnvironment() {
        if (!enabled) {
            return;
        }
        if ("live".equalsIgnoreCase(environment) && !allowLive) {
            log.warn("CBK GDI: requested environment=live but cbk.allow-live=false. " +
                    "Coercing to preprod. Set CBK_ALLOW_LIVE=true to actually go live.");
        } else if ("live".equalsIgnoreCase(getActiveEnvironment())) {
            log.warn("CBK GDI: live environment ENABLED. Submissions will hit {}.", getActiveBaseUrl());
        } else {
            log.info("CBK GDI: locked to TEST/preprod environment ({}). " +
                    "Live is disabled (cbk.allow-live=false).", getActiveBaseUrl());
        }
    }

    // ---- standard getters/setters ----

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public boolean isAllowLive() { return allowLive; }
    public void setAllowLive(boolean allowLive) { this.allowLive = allowLive; }

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
