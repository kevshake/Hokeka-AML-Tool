package com.posgateway.aml.dto.edge;

import java.time.Instant;

/**
 * Response to a node request. The {@code enrollmentCode} is the <b>only</b> time the raw code is
 * ever emitted — the control plane stores nothing but its SHA-256 digest, so it cannot be shown
 * again, re-sent, or recovered by an administrator.
 */
public record EdgeNodeCreatedResponse(
        EdgeNodeView node,
        String enrollmentCode,
        Instant enrollmentCodeExpiresAt,
        String notice) {

    public static EdgeNodeCreatedResponse of(EdgeNodeView node, String code, Instant expiresAt) {
        return new EdgeNodeCreatedResponse(node, code, expiresAt,
                "Copy this enrollment code now — it is single-use, expires in 24 hours, and cannot "
                        + "be retrieved again.");
    }
}
