package com.hokeka.edge.tunnel;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Local settings of the Edge Tunnel client (EDGE_DATA_RESIDENCY_CONTRACT.md §7), bound from
 * {@code hokeka.tunnel.*} ({@code application.yml} maps them onto the {@code EDGE_TUNNEL_*}
 * variables). They can only narrow what the Control Plane allows (§6.3, §7.6.1).
 */
@ConfigurationProperties(prefix = "hokeka.tunnel")
public class TunnelProperties {

    /** {@code EDGE_TUNNEL_ENABLED}: false = the node never connects (§7.6.1). */
    private boolean enabled = true;

    /** {@code EDGE_TUNNEL_OPS}: comma-separated op names this node serves; blank = all implemented. */
    private String ops = "";

    /**
     * {@code EDGE_TUNNEL_URL}: the tunnel endpoint. Blank = derived from {@code CONTROLPLANE_URL}
     * ({@code https://host} → {@code wss://host/api/v1/edge/tunnel}). Must be {@code wss://}.
     */
    private String url = "";

    /** Local in-flight cap (≤ 16); the effective cap is the minimum with the config and hello.ack. */
    private int maxInflight = 16;

    /** Heartbeat used until hello.ack names the Control Plane's (§7.2 default 20 s). */
    private Duration heartbeatInterval = Duration.ofSeconds(20);

    /** Idle timeout used until hello.ack names the Control Plane's (§7.2 default 60 s). */
    private Duration idleTimeout = Duration.ofSeconds(60);

    /** How long the Edge waits for hello.ack after sending hello. */
    private Duration helloTimeout = Duration.ofSeconds(10);

    /** WebSocket connect (TCP + TLS + upgrade) timeout. */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** Reconnect backoff floor (§7.2: full jitter from 1 s ...). */
    private Duration backoffMin = Duration.ofSeconds(1);

    /** Reconnect backoff ceiling (§7.2: ... to 60 s). */
    private Duration backoffMax = Duration.ofSeconds(60);

    /**
     * After close 4409 (a newer session of this node replaced this one) the node does not
     * reconnect for this long, so two processes sharing one identity do not take turns replacing
     * each other in a loop.
     */
    private Duration supersededHoldOff = Duration.ofMinutes(10);

    /** How often a gated client (disabled, unauthorised, after 4403) re-checks whether it may connect. */
    private Duration gateRecheckInterval = Duration.ofSeconds(5);

    /** Export streams end with STREAM_STALLED after this long without credit (§7.8: 60 s). */
    private Duration creditStallTimeout = Duration.ofSeconds(60);

    /** Seal/replay/binding failures within {@link #sealFailureWindow} that close the session (4401). */
    private int sealFailureLimit = 3;

    /** Window for {@link #sealFailureLimit} (§3.3: 3 in 60 s). */
    private Duration sealFailureWindow = Duration.ofSeconds(60);

    /** {@code retryAfterMs} sent with EDGE_BUSY. */
    private long busyRetryAfterMs = 500;

    /** Agent version reported in hello and the upgrade header; blank = the jar's version. */
    private String agentVersion = "";

    /**
     * The op names of {@code EDGE_TUNNEL_OPS}, trimmed (op names are case-sensitive, e.g.
     * {@code txn.batchGet}); empty when unset, meaning every implemented op.
     */
    public Set<String> opsFilter() {
        Set<String> out = new LinkedHashSet<>();
        if (ops == null || ops.isBlank()) {
            return out;
        }
        for (String op : ops.split(",")) {
            String t = op.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** @return whether the tunnel may connect at all */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled whether the tunnel may connect at all */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return the raw {@code EDGE_TUNNEL_OPS} value */
    public String getOps() {
        return ops;
    }

    /** @param ops the raw {@code EDGE_TUNNEL_OPS} value */
    public void setOps(String ops) {
        this.ops = ops;
    }

    /** @return the configured tunnel URL (may be blank) */
    public String getUrl() {
        return url;
    }

    /** @param url the tunnel URL */
    public void setUrl(String url) {
        this.url = url;
    }

    /** @return the local in-flight cap */
    public int getMaxInflight() {
        return maxInflight;
    }

    /** @param maxInflight the local in-flight cap */
    public void setMaxInflight(int maxInflight) {
        this.maxInflight = maxInflight;
    }

    /** @return the default heartbeat interval */
    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /** @param heartbeatInterval the default heartbeat interval */
    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    /** @return the default idle timeout */
    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    /** @param idleTimeout the default idle timeout */
    public void setIdleTimeout(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    /** @return the hello.ack wait */
    public Duration getHelloTimeout() {
        return helloTimeout;
    }

    /** @param helloTimeout the hello.ack wait */
    public void setHelloTimeout(Duration helloTimeout) {
        this.helloTimeout = helloTimeout;
    }

    /** @return the connect timeout */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /** @param connectTimeout the connect timeout */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /** @return the backoff floor */
    public Duration getBackoffMin() {
        return backoffMin;
    }

    /** @param backoffMin the backoff floor */
    public void setBackoffMin(Duration backoffMin) {
        this.backoffMin = backoffMin;
    }

    /** @return the backoff ceiling */
    public Duration getBackoffMax() {
        return backoffMax;
    }

    /** @param backoffMax the backoff ceiling */
    public void setBackoffMax(Duration backoffMax) {
        this.backoffMax = backoffMax;
    }

    /** @return the hold-off after close 4409 */
    public Duration getSupersededHoldOff() {
        return supersededHoldOff;
    }

    /** @param supersededHoldOff the hold-off after close 4409 */
    public void setSupersededHoldOff(Duration supersededHoldOff) {
        this.supersededHoldOff = supersededHoldOff;
    }

    /** @return how often a gated client re-checks */
    public Duration getGateRecheckInterval() {
        return gateRecheckInterval;
    }

    /** @param gateRecheckInterval how often a gated client re-checks */
    public void setGateRecheckInterval(Duration gateRecheckInterval) {
        this.gateRecheckInterval = gateRecheckInterval;
    }

    /** @return the export credit stall timeout */
    public Duration getCreditStallTimeout() {
        return creditStallTimeout;
    }

    /** @param creditStallTimeout the export credit stall timeout */
    public void setCreditStallTimeout(Duration creditStallTimeout) {
        this.creditStallTimeout = creditStallTimeout;
    }

    /** @return the seal-failure close threshold */
    public int getSealFailureLimit() {
        return sealFailureLimit;
    }

    /** @param sealFailureLimit the seal-failure close threshold */
    public void setSealFailureLimit(int sealFailureLimit) {
        this.sealFailureLimit = sealFailureLimit;
    }

    /** @return the seal-failure window */
    public Duration getSealFailureWindow() {
        return sealFailureWindow;
    }

    /** @param sealFailureWindow the seal-failure window */
    public void setSealFailureWindow(Duration sealFailureWindow) {
        this.sealFailureWindow = sealFailureWindow;
    }

    /** @return retryAfterMs for EDGE_BUSY */
    public long getBusyRetryAfterMs() {
        return busyRetryAfterMs;
    }

    /** @param busyRetryAfterMs retryAfterMs for EDGE_BUSY */
    public void setBusyRetryAfterMs(long busyRetryAfterMs) {
        this.busyRetryAfterMs = busyRetryAfterMs;
    }

    /** @return the configured agent version (may be blank) */
    public String getAgentVersion() {
        return agentVersion;
    }

    /** @param agentVersion the agent version */
    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }
}
