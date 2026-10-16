package com.posgateway.aml.entity.edge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.envers.Audited;

import java.time.Instant;

/**
 * Registry row for one PSP on-prem edge engine ("edge node") in the Hokeka control plane.
 *
 * <p>Implements the enrollment/authorisation lifecycle of
 * {@code docs/architecture/edge-channel-contract.md} §7. This is the authorisation record that
 * gates rule-bundle distribution: only a row in {@link EdgeNodeStatus#ACTIVE} is ever served a
 * sealed bundle, and revocation stops distribution immediately.
 *
 * <p><b>Security invariants encoded here</b>
 * <ul>
 *   <li>The enrollment code is <b>never</b> stored — only a SHA-256 hash of it
 *       ({@link #enrollmentCodeHash}). The raw code is returned exactly once, at request time.</li>
 *   <li>The edge's X25519 (encryption) and Ed25519 (signing) public keys are <b>pinned</b> at first
 *       successful activation (trust-on-first-activation). A later activation for the same
 *       {@link #edgeId} presenting different keys is refused.</li>
 *   <li>Audited with Hibernate Envers — this is a security-relevant authorisation record and the
 *       repo audits comparable entities ({@code RuleDefinition}, the compliance-case family).</li>
 * </ul>
 *
 * <p>Keys are stored base64 (standard alphabet) rather than {@code bytea} so the row is trivially
 * readable in the admin UI/API and portable across the Postgres/H2 test split; they are public keys,
 * so there is no confidentiality requirement — only integrity, which the pinning check enforces.
 */
@Entity
@Table(name = "edge_nodes",
        uniqueConstraints = @UniqueConstraint(name = "uq_edge_nodes_edge_id", columnNames = {"edge_id"}),
        indexes = {
                @Index(name = "idx_edge_nodes_psp_id", columnList = "psp_id"),
                @Index(name = "idx_edge_nodes_status", columnList = "status"),
                @Index(name = "idx_edge_nodes_code_hash", columnList = "enrollment_code_hash")
        })
@Audited
public class EdgeNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning PSP (tenant). Never null — every edge node belongs to exactly one PSP. */
    @Column(name = "psp_id", nullable = false)
    private Long pspId;

    /**
     * Stable machine identity of the node, asserted by the edge at activation and thereafter
     * carried in its mTLS client certificate CN. Globally unique.
     */
    @Column(name = "edge_id", length = 128)
    private String edgeId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EdgeNodeStatus status = EdgeNodeStatus.PENDING;

    /** SHA-256 (hex) of the single-use enrollment code. Cleared once the code is consumed. */
    @Column(name = "enrollment_code_hash", length = 64)
    private String enrollmentCodeHash;

    @Column(name = "enrollment_code_expires_at")
    private Instant enrollmentCodeExpiresAt;

    /** Base64 of the edge's raw 32-byte X25519 public key (RFC-7748 little-endian). Pinned. */
    @Column(name = "edge_x25519_public_key", length = 128)
    private String edgeX25519PublicKey;

    /** Base64 of the edge's raw 32-byte Ed25519 public key (RFC-8032 compressed). Pinned. */
    @Column(name = "edge_ed25519_public_key", length = 128)
    private String edgeEd25519PublicKey;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /** Rule-bundle version last successfully served to this node (drift/attestation tracking). */
    @Column(name = "last_bundle_version")
    private Long lastBundleVersion;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    /** Username of the PSP/admin user that requested the node. */
    @Column(name = "created_by", length = 128)
    private String createdBy;

    /** Username of the platform admin that approved (or rejected) the node. */
    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    /** Self-reported host name of the on-prem machine (informational, set at activation). */
    @Column(name = "hostname", length = 255)
    private String hostname;

    /** Self-reported edge-host software version (informational, refreshed on each poll). */
    @Column(name = "agent_version", length = 64)
    private String agentVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ── getters / setters ────────────────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }

    public String getEdgeId() { return edgeId; }
    public void setEdgeId(String edgeId) { this.edgeId = edgeId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public EdgeNodeStatus getStatus() { return status; }
    public void setStatus(EdgeNodeStatus status) { this.status = status; }

    public String getEnrollmentCodeHash() { return enrollmentCodeHash; }
    public void setEnrollmentCodeHash(String enrollmentCodeHash) { this.enrollmentCodeHash = enrollmentCodeHash; }

    public Instant getEnrollmentCodeExpiresAt() { return enrollmentCodeExpiresAt; }
    public void setEnrollmentCodeExpiresAt(Instant enrollmentCodeExpiresAt) {
        this.enrollmentCodeExpiresAt = enrollmentCodeExpiresAt;
    }

    public String getEdgeX25519PublicKey() { return edgeX25519PublicKey; }
    public void setEdgeX25519PublicKey(String edgeX25519PublicKey) { this.edgeX25519PublicKey = edgeX25519PublicKey; }

    public String getEdgeEd25519PublicKey() { return edgeEd25519PublicKey; }
    public void setEdgeEd25519PublicKey(String edgeEd25519PublicKey) {
        this.edgeEd25519PublicKey = edgeEd25519PublicKey;
    }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public Long getLastBundleVersion() { return lastBundleVersion; }
    public void setLastBundleVersion(Long lastBundleVersion) { this.lastBundleVersion = lastBundleVersion; }

    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public Instant getSuspendedAt() { return suspendedAt; }
    public void setSuspendedAt(Instant suspendedAt) { this.suspendedAt = suspendedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getAgentVersion() { return agentVersion; }
    public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
