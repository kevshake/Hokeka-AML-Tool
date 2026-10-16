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

    /** Returns {@code enabled}. */
    public boolean isEnabled() { return enabled; }
    /** Sets {@code enabled}. */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Returns {@code instanceId}. */
    public String getInstanceId() { return instanceId; }
    /** Sets {@code instanceId}. */
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    /** Returns {@code heartbeatIntervalMs}. */
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    /** Sets {@code heartbeatIntervalMs}. */
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }

    /** Returns {@code idleTimeoutMs}. */
    public long getIdleTimeoutMs() { return idleTimeoutMs; }
    /** Sets {@code idleTimeoutMs}. */
    public void setIdleTimeoutMs(long idleTimeoutMs) { this.idleTimeoutMs = idleTimeoutMs; }

    /** Returns {@code helloTimeoutMs}. */
    public long getHelloTimeoutMs() { return helloTimeoutMs; }
    /** Sets {@code helloTimeoutMs}. */
    public void setHelloTimeoutMs(long helloTimeoutMs) { this.helloTimeoutMs = helloTimeoutMs; }

    /** Returns {@code maxInflight}. */
    public int getMaxInflight() { return maxInflight; }
    /** Sets {@code maxInflight}. */
    public void setMaxInflight(int maxInflight) { this.maxInflight = maxInflight; }

    /** Returns {@code registryStaleMs}. */
    public long getRegistryStaleMs() { return registryStaleMs; }
    /** Sets {@code registryStaleMs}. */
    public void setRegistryStaleMs(long registryStaleMs) { this.registryStaleMs = registryStaleMs; }

    /** Returns {@code deadlineGraceMs}. */
    public long getDeadlineGraceMs() { return deadlineGraceMs; }
    /** Sets {@code deadlineGraceMs}. */
    public void setDeadlineGraceMs(long deadlineGraceMs) { this.deadlineGraceMs = deadlineGraceMs; }

    /** Returns {@code busyRetryAfterMs}. */
    public long getBusyRetryAfterMs() { return busyRetryAfterMs; }
    /** Sets {@code busyRetryAfterMs}. */
    public void setBusyRetryAfterMs(long busyRetryAfterMs) { this.busyRetryAfterMs = busyRetryAfterMs; }

    /** Returns {@code maxFrameBytes}. */
    public int getMaxFrameBytes() { return maxFrameBytes; }
    /** Sets {@code maxFrameBytes}. */
    public void setMaxFrameBytes(int maxFrameBytes) { this.maxFrameBytes = maxFrameBytes; }

    /** Returns {@code sealFailureLimit}. */
    public int getSealFailureLimit() { return sealFailureLimit; }
    /** Sets {@code sealFailureLimit}. */
    public void setSealFailureLimit(int sealFailureLimit) { this.sealFailureLimit = sealFailureLimit; }

    /** Returns {@code sealFailureWindowMs}. */
    public long getSealFailureWindowMs() { return sealFailureWindowMs; }
    /** Sets {@code sealFailureWindowMs}. */
    public void setSealFailureWindowMs(long sealFailureWindowMs) { this.sealFailureWindowMs = sealFailureWindowMs; }

    /** Returns {@code housekeepingIntervalMs}. */
    public long getHousekeepingIntervalMs() { return housekeepingIntervalMs; }
    /** Sets {@code housekeepingIntervalMs}. */
    public void setHousekeepingIntervalMs(long housekeepingIntervalMs) {
        this.housekeepingIntervalMs = housekeepingIntervalMs;
    }

    /** Returns {@code nodeRecheckIntervalMs}. */
    public long getNodeRecheckIntervalMs() { return nodeRecheckIntervalMs; }
    /** Sets {@code nodeRecheckIntervalMs}. */
    public void setNodeRecheckIntervalMs(long nodeRecheckIntervalMs) {
        this.nodeRecheckIntervalMs = nodeRecheckIntervalMs;
    }

    /** Returns {@code cursorSecret}. */
    public String getCursorSecret() { return cursorSecret; }
    /** Sets {@code cursorSecret}. */
    public void setCursorSecret(String cursorSecret) { this.cursorSecret = cursorSecret; }
}
