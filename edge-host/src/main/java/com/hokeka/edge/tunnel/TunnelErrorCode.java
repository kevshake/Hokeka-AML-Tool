package com.hokeka.edge.tunnel;

/**
 * Tunnel error codes with their HTTP status and default retry flag (EDGE_DATA_RESIDENCY_CONTRACT.md
 * §7.11, {@code tunnel/error.schema.json}). Codes only the Control Plane raises are listed so a
 * received error can be named, but the Edge never sends them.
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
    BACKFILL_REJECTED(409, false),
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

    /** @param httpStatus HTTP status of the code; @param retryable default retry flag */
    TunnelErrorCode(int httpStatus, boolean retryable) {
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    /** The code's HTTP status (§7.11 table). */
    public int httpStatus() {
        return httpStatus;
    }

    /** Whether the caller may retry (§7.11 table). */
    public boolean retryable() {
        return retryable;
    }
}
