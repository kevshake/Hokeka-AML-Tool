package com.posgateway.aml.edge.tunnel;

/**
 * The Edge Tunnel error codes of {@code docs/EDGE_DATA_RESIDENCY_CONTRACT.md} §7.11, each with the
 * HTTP status and retry flag the contract (and {@code tunnel/error.schema.json}) binds to it.
 *
 * <p>The same body is the Control Plane's JSON error to the Console on tunnel-backed endpoints, so
 * {@link #httpStatus()} is what an R6 controller answers with.
 */
public enum TunnelErrorCode {

    INVALID_PARAMS(400, false),
    INVALID_CURSOR(400, false),
    PAGE_LIMIT_EXCEEDED(400, false),
    UNSUPPORTED_FRAME(400, false),
    CREDIT_VIOLATION(400, false),
    SEAL_INVALID(401, false),
    REPLAY_DETECTED(401, false),
    BINDING_MISMATCH(401, false),
    OP_NOT_ALLOWED(403, false),
    TENANT_MISMATCH(403, false),
    SUPPORT_ACCESS_DISABLED(403, false),
    BACKFILL_NOT_ALLOWED(403, false),
    NOT_FOUND(404, false),
    STREAM_STALLED(408, true),
    BACKFILL_REJECTED(409, true),
    FRAME_TOO_LARGE(413, false),
    UNSUPPORTED_VERSION(426, false),
    EDGE_BUSY(429, true),
    CANCELLED(499, false),
    INTERNAL(500, true),
    LEDGER_UNAVAILABLE(503, true),
    EDGE_OFFLINE(503, true),
    DEADLINE_EXCEEDED(504, true),
    EDGE_TIMEOUT(504, true);

    private final int httpStatus;
    private final boolean retryable;

    /** Binds the contract's HTTP status and default retry flag to the code. */
    TunnelErrorCode(int httpStatus, boolean retryable) {
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    /** HTTP status the contract assigns to this code (§7.11 table). */
    public int httpStatus() {
        return httpStatus;
    }

    /** Default {@code retryable} flag for a cloud-raised error with this code. */
    public boolean retryable() {
        return retryable;
    }

    /**
     * Parses a code received from the Edge. Unknown codes map to {@link #INTERNAL} so a newer Edge
     * can never make the cloud throw while handling its error (§12.2: unknown enum values are
     * treated conservatively).
     */
    public static TunnelErrorCode parse(String code) {
        if (code == null) {
            return INTERNAL;
        }
        try {
            return TunnelErrorCode.valueOf(code);
        } catch (IllegalArgumentException e) {
            return INTERNAL;
        }
    }
}
