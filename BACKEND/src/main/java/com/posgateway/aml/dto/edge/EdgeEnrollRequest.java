package com.posgateway.aml.dto.edge;

/**
 * Body of {@code POST /api/v1/edge/enroll} — first-boot activation (contract §5).
 *
 * <p><b>Wire-compatible with the edge host.</b> The field names mirror
 * {@code edge-host/src/main/java/com/hokeka/edge/activation/ActivationService.EnrollmentRequest}
 * exactly: {@code edgeId}, {@code pspId}, {@code enrollmentCode}, {@code x25519PublicKey},
 * {@code ed25519PublicKey}. {@code hostname} and {@code agentVersion} are optional extras the
 * current edge host does not send; they arrive as {@code null} and are simply not recorded.
 *
 * <p>The enrollment code is the credential; the two public keys are what gets <b>pinned</b>.
 *
 * @param edgeId            the node's stable machine identity (also its mTLS client-cert CN)
 * @param pspId             the PSP the edge believes it belongs to, as configured on the edge host.
 *                          Advisory only — the authoritative owner is the PSP recorded against the
 *                          enrollment code, which the edge cannot influence.
 * @param enrollmentCode    the single-use code issued when the node was requested
 * @param x25519PublicKey   base64 X.509 SubjectPublicKeyInfo (44 B) or raw 32-byte X25519 key
 * @param ed25519PublicKey  base64 X.509 SubjectPublicKeyInfo (44 B) or raw 32-byte Ed25519 key
 * @param hostname          self-reported host name, informational, optional
 * @param agentVersion      self-reported edge-host version, informational, optional
 */
public record EdgeEnrollRequest(
        String edgeId,
        String pspId,
        String enrollmentCode,
        String x25519PublicKey,
        String ed25519PublicKey,
        String hostname,
        String agentVersion) {
}
