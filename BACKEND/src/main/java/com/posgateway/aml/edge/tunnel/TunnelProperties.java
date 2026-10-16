package com.posgateway.aml.edge.tunnel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code edge.tunnel.*} settings of the Edge Tunnel server (contract §7). Every default is the
 * contract value; the knobs exist so tests can shorten timers and so operators can name the
 * Control Plane instance.
 */
@ConfigurationProperties(prefix = "edge.tunnel")
public class TunnelProperties {

    /** Master switch for the WebSocket endpoint, registry and relay. */
    private boolean enabled = true;

    /**
     * Name of this Control Plane instance ({@code hello.ack.cpInstance}, registry {@code cpInstance},
     * Redis channel {@code tunnel:cp:{cpInstance}}). Blank = derived from the host name plus a
     * random suffix at startup, so two instances never collide.
     */
    private String instanceId = "";

    /** §7.2: Edge ping interval announced in {@code hello.ack}. */
    private long heartbeatIntervalMs = 20_000;

    /** §7.2: a session with no received frame for this long is dead (close 4408). */
    private long idleTimeoutMs = 60_000;

    /** §7.2: the Edge must send {@code hello} within this many ms of the upgrade (else 4408). */
    private long helloTimeoutMs = 10_000;

    /** §7.5: hard per-session in-flight cap. */
    private int maxInflight = 16;

    /** §7.12: registry entries whose {@code lastPongAt} is older than this are ignored/reaped. */
    private long registryStaleMs = 60_000;

    /** §7.5: the cloud waits {@code deadlineMs + this} before answering {@code EDGE_TIMEOUT}. */
    private long deadlineGraceMs = 1_000;

    /** {@code retryAfterMs} carried by a cloud-raised {@code EDGE_BUSY}. */
    private long busyRetryAfterMs = 500;

    /** §7.1: maximum encoded frame size. */
    private int maxFrameBytes = 1_048_576;

    /** §3.3: seal/replay failures within {@link #sealFailureWindowMs} that close the session (4401). */
    private int sealFailureLimit = 3;

    /** §3.3: window for {@link #sealFailureLimit}. */
    private long sealFailureWindowMs = 60_000;

    /** Period of the housekeeping tick (hello/idle timeouts, node status re-check, reaping). */
    private long housekeepingIntervalMs = 1_000;

    /** How often a live session's node is re-checked for ACTIVE status (revocation drops it). */
    private long nodeRecheckIntervalMs = 30_000;

    /**
     * HMAC-SHA256 secret for composite cursors (§7.13). Must be identical on every instance; blank
     * = derived from the Control Plane signing key, which every instance already shares.
     */
    private String cursorSecret = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }

    public long getIdleTimeoutMs() { return idleTimeoutMs; }
    public void setIdleTimeoutMs(long idleTimeoutMs) { this.idleTimeoutMs = idleTimeoutMs; }

    public long getHelloTimeoutMs() { return helloTimeoutMs; }
    public void setHelloTimeoutMs(long helloTimeoutMs) { this.helloTimeoutMs = helloTimeoutMs; }

    public int getMaxInflight() { return maxInflight; }
    public void setMaxInflight(int maxInflight) { this.maxInflight = maxInflight; }

    public long getRegistryStaleMs() { return registryStaleMs; }
    public void setRegistryStaleMs(long registryStaleMs) { this.registryStaleMs = registryStaleMs; }

    public long getDeadlineGraceMs() { return deadlineGraceMs; }
    public void setDeadlineGraceMs(long deadlineGraceMs) { this.deadlineGraceMs = deadlineGraceMs; }

    public long getBusyRetryAfterMs() { return busyRetryAfterMs; }
    public void setBusyRetryAfterMs(long busyRetryAfterMs) { this.busyRetryAfterMs = busyRetryAfterMs; }

    public int getMaxFrameBytes() { return maxFrameBytes; }
    public void setMaxFrameBytes(int maxFrameBytes) { this.maxFrameBytes = maxFrameBytes; }

    public int getSealFailureLimit() { return sealFailureLimit; }
    public void setSealFailureLimit(int sealFailureLimit) { this.sealFailureLimit = sealFailureLimit; }

    public long getSealFailureWindowMs() { return sealFailureWindowMs; }
    public void setSealFailureWindowMs(long sealFailureWindowMs) { this.sealFailureWindowMs = sealFailureWindowMs; }

    public long getHousekeepingIntervalMs() { return housekeepingIntervalMs; }
    public void setHousekeepingIntervalMs(long housekeepingIntervalMs) {
        this.housekeepingIntervalMs = housekeepingIntervalMs;
    }

    public long getNodeRecheckIntervalMs() { return nodeRecheckIntervalMs; }
    public void setNodeRecheckIntervalMs(long nodeRecheckIntervalMs) {
        this.nodeRecheckIntervalMs = nodeRecheckIntervalMs;
    }

    public String getCursorSecret() { return cursorSecret; }
    public void setCursorSecret(String cursorSecret) { this.cursorSecret = cursorSecret; }
}
