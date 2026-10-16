package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * One §7.11 error body: {@code {code, httpStatus, message, retryable, retryAfterMs?, lastSeenAt?,
 * siteId?, param?}}. Used both as the body of a sealed {@code error} frame and as the Control
 * Plane's JSON error to the Console.
 *
 * <p>{@code message} is operator text and MUST NOT contain a transaction value: every message the
 * cloud builds is a fixed phrase plus, at most, a site id or a count.
 */
public record TunnelError(TunnelErrorCode code, int httpStatus, String message, boolean retryable,
                          Long retryAfterMs, Instant lastSeenAt, String siteId, String param) {

    /** A cloud-raised error with the contract's status and retry flag for {@code code}. */
    public static TunnelError of(TunnelErrorCode code, String message) {
        return new TunnelError(code, code.httpStatus(), message, code.retryable(), null, null, null, null);
    }

    /** {@code EDGE_OFFLINE} for one site; {@code lastSeenAt} is REQUIRED by the schema. */
    public static TunnelError offline(String siteId, Instant lastSeenAt) {
        return new TunnelError(TunnelErrorCode.EDGE_OFFLINE, 503,
                siteId == null ? "no tunnel session for this PSP" : "no tunnel session for site " + siteId,
                true, null, lastSeenAt == null ? Instant.EPOCH : lastSeenAt, siteId, null);
    }

    /** {@code EDGE_BUSY} for one site; {@code retryAfterMs} is REQUIRED by the schema. */
    public static TunnelError busy(String siteId, int maxInflight, long retryAfterMs) {
        return new TunnelError(TunnelErrorCode.EDGE_BUSY, 429,
                maxInflight + " requests already in flight on every node of site " + siteId,
                true, retryAfterMs, null, siteId, null);
    }

    /** {@code EDGE_TIMEOUT}: no answer within {@code deadlineMs + 1 s}. */
    public static TunnelError timeout(String siteId) {
        return new TunnelError(TunnelErrorCode.EDGE_TIMEOUT, 504,
                "no answer from site " + siteId + " within the deadline", true, null, null, siteId, null);
    }

    /** {@code INVALID_PARAMS} naming the JSON pointer of the offending parameter, never its value. */
    public static TunnelError invalidParams(String pointer) {
        return new TunnelError(TunnelErrorCode.INVALID_PARAMS, 400, "params fail the op schema or limits",
                false, null, null, null, pointer);
    }

    /** Returns a copy tagged with the site that raised it (keeps every other field). */
    public TunnelError withSite(String site) {
        return new TunnelError(code, httpStatus, message, retryable, retryAfterMs, lastSeenAt, site, param);
    }

    /** Renders the §7.11 JSON body (field order as in the schema). */
    public ObjectNode toJson() {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("code", code.name());
        n.put("httpStatus", httpStatus);
        n.put("message", message == null ? code.name() : message);
        n.put("retryable", retryable);
        if (retryAfterMs != null) {
            n.put("retryAfterMs", retryAfterMs);
        }
        if (lastSeenAt != null) {
            n.put("lastSeenAt", TunnelTime.formatSeconds(lastSeenAt));
        }
        if (siteId != null) {
            n.put("siteId", siteId);
        }
        if (param != null) {
            n.put("param", param);
        }
        return n;
    }

    /**
     * Reads an error body received from the Edge (already schema-validated). The Edge's message is
     * kept because the schema forbids PAN-like values in it; it is still never logged by the cloud.
     */
    public static TunnelError fromJson(JsonNode n) {
        TunnelErrorCode code = TunnelErrorCode.parse(n.path("code").asText(null));
        return new TunnelError(code,
                n.path("httpStatus").asInt(code.httpStatus()),
                n.path("message").asText(code.name()),
                n.path("retryable").asBoolean(code.retryable()),
                n.hasNonNull("retryAfterMs") ? n.get("retryAfterMs").asLong() : null,
                n.hasNonNull("lastSeenAt") ? Instant.parse(n.get("lastSeenAt").asText()) : null,
                n.hasNonNull("siteId") ? n.get("siteId").asText() : null,
                n.hasNonNull("param") ? n.get("param").asText() : null);
    }
}
