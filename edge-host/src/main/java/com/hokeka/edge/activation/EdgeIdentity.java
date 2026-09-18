package com.hokeka.edge.activation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The credentials file written on first boot (mode 600). Holds this node's pinned keypairs — the
 * X25519 pair that decrypts rule bundles and the Ed25519 pair that signs metrics uploads. The
 * private halves never leave the host.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EdgeIdentity(
        String edgeId,
        String pspId,
        /** base64 PKCS#8 */
        String x25519PrivateKey,
        /** base64 SPKI, the value registered with the control plane */
        String x25519PublicKey,
        /** base64 PKCS#8 */
        String ed25519PrivateKey,
        /** base64 SPKI, the value registered with the control plane */
        String ed25519PublicKey,
        String createdAt,
        /** Last known lifecycle state as reported by the control plane. */
        String status
) {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";

    public EdgeIdentity withStatus(String newStatus) {
        return new EdgeIdentity(edgeId, pspId, x25519PrivateKey, x25519PublicKey,
                ed25519PrivateKey, ed25519PublicKey, createdAt, newStatus);
    }
}
