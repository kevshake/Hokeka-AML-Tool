package com.posgateway.aml.integration.cbk;

/**
 * Captures the outcome of a single CBK GDI submission attempt.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code success} - true if a 2xx response and regulator request ID were received.</li>
 *   <li>{@code requestId} — the {@code RequestNo} value from the CBK response body, or null.</li>
 *   <li>{@code httpStatus} — the HTTP status code returned, or -1 if no response was received.</li>
 *   <li>{@code body} — first 500 characters of the response body (for diagnostics).</li>
 *   <li>{@code errorMessage} — exception message or error detail when success is false.</li>
 *   <li>{@code durationMs} — wall-clock time of the HTTP exchange in milliseconds.</li>
 * </ul>
 */
public final class CbkSubmissionResult {

    private final boolean success;
    private final String requestId;
    private final int httpStatus;
    private final String body;
    private final String errorMessage;
    private final long durationMs;
    private final int sourceRecordCount;

    private CbkSubmissionResult(boolean success, String requestId, int httpStatus,
                                String body, String errorMessage, long durationMs,
                                int sourceRecordCount) {
        this.success = success;
        this.requestId = requestId;
        this.httpStatus = httpStatus;
        this.body = body;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.sourceRecordCount = Math.max(0, sourceRecordCount);
    }

    public static CbkSubmissionResult ok(String requestId, int httpStatus, String body, long durationMs) {
        return ok(requestId, httpStatus, body, durationMs, 0);
    }

    public static CbkSubmissionResult ok(String requestId, int httpStatus, String body,
                                         long durationMs, int sourceRecordCount) {
        return new CbkSubmissionResult(
                true, requestId, httpStatus, excerpt(body), null, durationMs, sourceRecordCount);
    }

    public static CbkSubmissionResult failure(String errorMessage, int httpStatus, String body, long durationMs) {
        return failure(errorMessage, httpStatus, body, durationMs, 0);
    }

    public static CbkSubmissionResult failure(String errorMessage, int httpStatus, String body,
                                              long durationMs, int sourceRecordCount) {
        return new CbkSubmissionResult(
                false, null, httpStatus, excerpt(body), errorMessage, durationMs, sourceRecordCount);
    }

    public static CbkSubmissionResult failure(String errorMessage, long durationMs) {
        return new CbkSubmissionResult(false, null, -1, null, errorMessage, durationMs, 0);
    }

    private static String excerpt(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    public boolean isSuccess() { return success; }
    public String getRequestId() { return requestId; }
    public int getHttpStatus() { return httpStatus; }
    public String getBody() { return body; }
    public String getErrorMessage() { return errorMessage; }
    public long getDurationMs() { return durationMs; }
    public int getSourceRecordCount() { return sourceRecordCount; }

    @Override
    public String toString() {
        return "CbkSubmissionResult{success=" + success
                + ", requestId='" + requestId + '\''
                + ", httpStatus=" + httpStatus
                + ", durationMs=" + durationMs
                + (errorMessage != null ? ", error='" + errorMessage + '\'' : "")
                + '}';
    }
}
