package com.posgateway.aml.dto.edge;

/**
 * Response to a successful activation. Carries everything the edge must pin to talk HSE-1 with the
 * control plane, and nothing else — in particular it never echoes the enrollment code.
 *
 * @param edgeId                  the activated node's machine identity
 * @param status                  the node's lifecycle status after activation (ACTIVE)
 * @param controlPlaneSigningKey  base64 raw Ed25519 public key — the edge pins this to verify
 *                                bundle envelopes
 * @param controlPlaneIngestKey   base64 raw X25519 public key — the edge seals metrics to this
 * @param bundleContext           HSE-1 context string for rule bundles (contract §3)
 * @param metricsContext          HSE-1 context string for metrics uploads (contract §3)
 * @param maxClockSkewSeconds     replay-envelope skew tolerance the control plane enforces
 */
public record EdgeEnrollResponse(
        String edgeId,
        String status,
        String controlPlaneSigningKey,
        String controlPlaneIngestKey,
        String bundleContext,
        String metricsContext,
        long maxClockSkewSeconds) {
}
