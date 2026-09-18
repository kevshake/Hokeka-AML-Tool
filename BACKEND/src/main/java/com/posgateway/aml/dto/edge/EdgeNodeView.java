package com.posgateway.aml.dto.edge;

import com.posgateway.aml.entity.edge.EdgeNode;

import java.time.Duration;
import java.time.Instant;

/**
 * Admin/UI projection of an {@link EdgeNode}.
 *
 * <p>Deliberately omits {@code enrollmentCodeHash}: the digest is a credential artefact and there is
 * no operational reason to surface it. The pinned public keys <i>are</i> included — they are public
 * by construction and operators need them to verify a node's identity out of band.
 */
public record EdgeNodeView(
        Long id,
        Long pspId,
        String edgeId,
        String displayName,
        String status,
        boolean enrollmentCodeOutstanding,
        Instant enrollmentCodeExpiresAt,
        String edgeX25519PublicKey,
        String edgeEd25519PublicKey,
        Instant lastSeenAt,
        Long lastBundleVersion,
        Instant activatedAt,
        Instant suspendedAt,
        Instant revokedAt,
        String createdBy,
        String approvedBy,
        String hostname,
        String agentVersion,
        Instant createdAt,
        String health,
        Long secondsSinceLastSeen) {

    /** A node is considered stale when it has not polled for more than this. */
    private static final Duration HEALTHY_WINDOW = Duration.ofMinutes(5);

    public static EdgeNodeView of(EdgeNode n) {
        Instant lastSeen = n.getLastSeenAt();
        Long since = lastSeen == null ? null : Duration.between(lastSeen, Instant.now()).getSeconds();
        return new EdgeNodeView(
                n.getId(),
                n.getPspId(),
                n.getEdgeId(),
                n.getDisplayName(),
                n.getStatus().name(),
                n.getEnrollmentCodeHash() != null,
                n.getEnrollmentCodeExpiresAt(),
                n.getEdgeX25519PublicKey(),
                n.getEdgeEd25519PublicKey(),
                lastSeen,
                n.getLastBundleVersion(),
                n.getActivatedAt(),
                n.getSuspendedAt(),
                n.getRevokedAt(),
                n.getCreatedBy(),
                n.getApprovedBy(),
                n.getHostname(),
                n.getAgentVersion(),
                n.getCreatedAt(),
                health(n, since),
                since);
    }

    private static String health(EdgeNode n, Long secondsSinceLastSeen) {
        return switch (n.getStatus()) {
            case PENDING -> "AWAITING_APPROVAL";
            case APPROVED -> "AWAITING_ACTIVATION";
            case SUSPENDED -> "SUSPENDED";
            case REVOKED -> "REVOKED";
            case REJECTED -> "REJECTED";
            case ACTIVE -> {
                if (secondsSinceLastSeen == null) {
                    yield "NEVER_SEEN";
                }
                yield secondsSinceLastSeen <= HEALTHY_WINDOW.getSeconds() ? "HEALTHY" : "STALE";
            }
        };
    }
}
