package com.posgateway.aml.config.onprem;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dual-role auth properties for Hokeka on-prem service licensing.
 *
 * <ul>
 *   <li><b>Central (cloud)</b> — uses {@link #leaseSigningSecret} to mint/verify lease tokens.</li>
 *   <li><b>On-prem</b> — when {@link #enabled} is true, authenticates to
 *       {@link #upstreamUrl} with client credentials and fail-closes without a valid lease.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "hokeka.auth")
public class HokekaAuthProperties {

    /**
     * When true, this JVM is an on-prem PSP instance that must obtain leases from upstream.
     * When false (default), only the central lease-issuing APIs are active.
     */
    private boolean enabled = false;

    /** Base URL of the central Hokeka BACKEND, e.g. {@code https://api.hokeka.com}. */
    private String upstreamUrl = "";

    private String clientId = "";

    private String clientSecret = "";

    /** Stable instance identity for this on-prem deployment. */
    private String instanceId = "";

    /**
     * HMAC-SHA256 secret used by central to sign leases. On-prem may also set this to the same
     * value to verify persisted lease tokens offline after restart.
     */
    private String leaseSigningSecret = "";

    /** Local JSON file used by on-prem to persist the last successful lease. */
    private String leaseStorePath = "./data/onprem-lease.json";

    /** HTTP timeout talking to upstream (seconds). */
    private int timeoutSeconds = 15;

    /** Daily check interval in days (server still assigns a jittered clock time). */
    private int checkIntervalDays = 1;

    /** Optional hostname reported on lease renewals. */
    private String hostname = "";

    /** Optional agent/build version reported on lease renewals. */
    private String agentVersion = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUpstreamUrl() {
        return upstreamUrl;
    }

    public void setUpstreamUrl(String upstreamUrl) {
        this.upstreamUrl = upstreamUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getLeaseSigningSecret() {
        return leaseSigningSecret;
    }

    public void setLeaseSigningSecret(String leaseSigningSecret) {
        this.leaseSigningSecret = leaseSigningSecret;
    }

    public String getLeaseStorePath() {
        return leaseStorePath;
    }

    public void setLeaseStorePath(String leaseStorePath) {
        this.leaseStorePath = leaseStorePath;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getCheckIntervalDays() {
        return checkIntervalDays;
    }

    public void setCheckIntervalDays(int checkIntervalDays) {
        this.checkIntervalDays = checkIntervalDays;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }
}
