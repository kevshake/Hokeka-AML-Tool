package com.posgateway.aml.service.edge;

import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Owns the PSP edge-node enrollment and authorisation lifecycle of
 * {@code docs/architecture/edge-channel-contract.md} §7:
 *
 * <pre>PENDING → APPROVED → ACTIVE → SUSPENDED / REVOKED</pre>
 *
 * <p>This is the single authorisation decision point for edge rule-bundle distribution:
 * {@link #isAuthorized(String)} is consulted before a single byte of a bundle is produced, and a
 * revoked node fails that check immediately and permanently.
 *
 * <h2>Security properties</h2>
 * <ul>
 *   <li><b>Enrollment codes are never stored.</b> {@link #requestNode} returns the raw code exactly
 *       once; only its SHA-256 digest is persisted, and the digest is erased the moment the code is
 *       consumed, which is what makes the code single-use.</li>
 *   <li><b>Constant-time verification.</b> The candidate digest is compared to the stored digest
 *       with {@link MessageDigest#isEqual}, which is the JDK's time-constant comparison.</li>
 *   <li><b>Trust-on-first-activation key pinning.</b> The first successful activation records the
 *       edge's X25519/Ed25519 public keys permanently. A later activation for the same edge id
 *       presenting different keys is refused (409), never silently re-pinned.</li>
 *   <li><b>Irreversible revocation.</b> {@link EdgeNodeStatus#REVOKED} and
 *       {@link EdgeNodeStatus#REJECTED} are terminal; every transition method refuses to move out
 *       of them.</li>
 *   <li><b>PSP tenant isolation.</b> Every admin-facing method routes through
 *       {@link PspIsolationService#validatePspAccess(Long)}, so a PSP user can only ever see or
 *       manage nodes owned by its own PSP; platform administrators are unrestricted.</li>
 *   <li><b>PSP-before-edge ordering.</b> An edge node cannot be approved or activated unless its
 *       owning PSP is itself {@code ACTIVE} — an unapproved or suspended PSP can never bring an
 *       edge online.</li>
 * </ul>
 */
@Service
public class EdgeEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EdgeEnrollmentService.class);

    /** Contract §7: enrollment codes are single-use with a 24 h TTL. */
    public static final Duration ENROLLMENT_CODE_TTL = Duration.ofHours(24);

    /** 256 bits of entropy, base64url-encoded (43 chars, unpadded). */
    private static final int ENROLLMENT_CODE_BYTES = 32;

    /** Raw X25519 / Ed25519 public keys are always exactly 32 bytes. */
    private static final int PUBLIC_KEY_BYTES = 32;

    /** RFC 8410 X.509 SubjectPublicKeyInfo prefix length for both Ed25519 and X25519. */
    private static final int SPKI_PREFIX_BYTES = 12;

    private final EdgeNodeRepository edgeNodeRepository;
    private final PspRepository pspRepository;
    private final PspIsolationService pspIsolationService;
    private final SecureRandom random = new SecureRandom();

    public EdgeEnrollmentService(EdgeNodeRepository edgeNodeRepository,
                                 PspRepository pspRepository,
                                 PspIsolationService pspIsolationService) {
        this.edgeNodeRepository = edgeNodeRepository;
        this.pspRepository = pspRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /** The raw enrollment code is returned once and never again — only its digest is stored. */
    public record EnrollmentRequestResult(EdgeNode node, String rawEnrollmentCode, Instant expiresAt) {}

    // ── request / approve / reject ───────────────────────────────────────────────────────────────

    /**
     * Create a node in {@link EdgeNodeStatus#PENDING} and issue a single-use, 24 h enrollment code.
     *
     * @return the persisted node plus the <b>raw</b> code — show it to the operator once; it cannot
     *         be recovered afterwards.
     */
    @Transactional
    public EnrollmentRequestResult requestNode(Long pspId, String displayName, String createdBy) {
        if (pspId == null) {
            throw EdgeEnrollmentException.badRequest("pspId is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw EdgeEnrollmentException.badRequest("displayName is required");
        }
        pspIsolationService.validatePspAccess(pspId);
        requirePspExists(pspId);

        String rawCode = generateEnrollmentCode();
        Instant expiresAt = Instant.now().plus(ENROLLMENT_CODE_TTL);

        EdgeNode node = new EdgeNode();
        node.setPspId(pspId);
        node.setDisplayName(displayName.trim());
        node.setStatus(EdgeNodeStatus.PENDING);
        node.setEnrollmentCodeHash(sha256Hex(rawCode));
        node.setEnrollmentCodeExpiresAt(expiresAt);
        node.setCreatedBy(createdBy);

        EdgeNode saved = edgeNodeRepository.save(node);
        log.info("Edge node {} requested for PSP {} by {} (PENDING, code expires {})",
                saved.getId(), pspId, createdBy, expiresAt);
        return new EnrollmentRequestResult(saved, rawCode, expiresAt);
    }

    /** PENDING → APPROVED. Requires the owning PSP to be ACTIVE. */
    @Transactional
    public EdgeNode approve(Long id, String adminUser) {
        EdgeNode node = loadForTenant(id);
        if (node.getStatus() != EdgeNodeStatus.PENDING) {
            throw EdgeEnrollmentException.conflict(
                    "only a PENDING edge node can be approved (node is " + node.getStatus() + ")");
        }
        requirePspActive(node.getPspId(), "approve");
        node.setStatus(EdgeNodeStatus.APPROVED);
        node.setApprovedBy(adminUser);
        log.info("Edge node {} approved by {}", id, adminUser);
        return edgeNodeRepository.save(node);
    }

    /** PENDING → REJECTED (terminal). The enrollment code is destroyed. */
    @Transactional
    public EdgeNode reject(Long id, String adminUser) {
        EdgeNode node = loadForTenant(id);
        if (node.getStatus() != EdgeNodeStatus.PENDING) {
            throw EdgeEnrollmentException.conflict(
                    "only a PENDING edge node can be rejected (node is " + node.getStatus() + ")");
        }
        node.setStatus(EdgeNodeStatus.REJECTED);
        node.setApprovedBy(adminUser);
        destroyEnrollmentCode(node);
        log.info("Edge node {} rejected by {}", id, adminUser);
        return edgeNodeRepository.save(node);
    }

    // ── activation (machine-to-machine, no user session) ─────────────────────────────────────────

    /**
     * Complete first-boot activation: validate the single-use code, pin the edge's public keys and
     * move the node to {@link EdgeNodeStatus#ACTIVE}.
     *
     * <p>Deliberately <b>not</b> tenant-checked via the security context — this is the
     * machine-to-machine path and there is no logged-in user. The enrollment code <i>is</i> the
     * credential, and it binds the activation to exactly one pre-authorised node row.
     *
     * <p><b>Tenant self-assertion is verified, not merely logged.</b> {@code assertedPspId} is the
     * PSP the edge host has in its own configuration. It grants no privilege — the owning PSP comes
     * from the enrollment code, which the edge cannot influence — but a disagreement means the
     * node's local config contradicts the control plane, so its metrics labelling, logs and support
     * diagnostics would all reference the wrong tenant. That is unacceptable in a compliance
     * product, and activation is precisely the moment it is cheapest to fix: an operator is present
     * and the fix is a one-line config change. So a mismatch is refused with {@code 403} naming
     * both values, <b>before any state is mutated</b> — the enrollment code is not consumed, no keys
     * are pinned, and the node stays APPROVED so the operator can simply correct the config and
     * retry. A blank/absent assertion is not a mismatch and is allowed through.
     *
     * @param enrollmentCode raw single-use code issued by {@link #requestNode}
     * @param edgeId         the node's stable machine identity (must be globally unique)
     * @param assertedPspId  the PSP id configured on the edge host; must match the node's owner
     * @param x25519PubB64   base64 X.509 SPKI or the raw 32-byte X25519 public key
     * @param ed25519PubB64  base64 X.509 SPKI or the raw 32-byte Ed25519 public key
     */
    @Transactional
    public EdgeNode activate(String enrollmentCode, String edgeId, String assertedPspId,
                             String x25519PubB64, String ed25519PubB64, String hostname,
                             String agentVersion) {
        if (enrollmentCode == null || enrollmentCode.isBlank()) {
            throw EdgeEnrollmentException.forbidden("enrollment code required");
        }
        if (edgeId == null || edgeId.isBlank()) {
            throw EdgeEnrollmentException.badRequest("edgeId required");
        }
        String normalisedEdgeId = edgeId.trim();
        String x25519 = normalisePublicKey(x25519PubB64, "edgeX25519PublicKey");
        String ed25519 = normalisePublicKey(ed25519PubB64, "edgeEd25519PublicKey");

        String candidateHash = sha256Hex(enrollmentCode);
        EdgeNode node = edgeNodeRepository.findByEnrollmentCodeHash(candidateHash)
                .filter(n -> constantTimeEquals(candidateHash, n.getEnrollmentCodeHash()))
                .orElseThrow(() -> EdgeEnrollmentException.forbidden(
                        "invalid or already-used enrollment code"));

        Instant now = Instant.now();
        if (node.getEnrollmentCodeExpiresAt() == null || now.isAfter(node.getEnrollmentCodeExpiresAt())) {
            throw EdgeEnrollmentException.forbidden("enrollment code expired");
        }
        if (node.getStatus() != EdgeNodeStatus.APPROVED) {
            throw EdgeEnrollmentException.forbidden(
                    "edge node is not APPROVED for activation (status " + node.getStatus() + ")");
        }
        requirePspActive(node.getPspId(), "activate");
        requireAssertedPspIdMatches(assertedPspId, node);

        // Trust-on-first-activation. The first activation pins the keys; anything presenting a
        // different key pair for an already-known edge id is refused, never re-pinned.
        Optional<EdgeNode> existingForEdgeId = edgeNodeRepository.findByEdgeId(normalisedEdgeId);
        if (existingForEdgeId.isPresent() && !existingForEdgeId.get().getId().equals(node.getId())) {
            EdgeNode other = existingForEdgeId.get();
            if (!keysMatch(other, x25519, ed25519)) {
                log.warn("Rejected activation for edge id {}: key-pinning conflict with node {}",
                        normalisedEdgeId, other.getId());
                throw EdgeEnrollmentException.conflict(
                        "edge id " + normalisedEdgeId + " is pinned to different public keys");
            }
            throw EdgeEnrollmentException.conflict(
                    "edge id " + normalisedEdgeId + " is already registered to another node");
        }
        if (!keysMatch(node, x25519, ed25519)) {
            log.warn("Rejected activation for node {}: key-pinning conflict", node.getId());
            throw EdgeEnrollmentException.conflict(
                    "edge node public keys are already pinned to different values");
        }

        node.setEdgeId(normalisedEdgeId);
        node.setEdgeX25519PublicKey(x25519);
        node.setEdgeEd25519PublicKey(ed25519);
        node.setStatus(EdgeNodeStatus.ACTIVE);
        node.setActivatedAt(now);
        node.setLastSeenAt(now);
        node.setHostname(trimToNull(hostname));
        node.setAgentVersion(trimToNull(agentVersion));
        // Single-use: destroying the digest means the same code can never activate anything again.
        destroyEnrollmentCode(node);

        log.info("Edge node {} activated as edgeId={} (psp {}), keys pinned",
                node.getId(), normalisedEdgeId, node.getPspId());
        return edgeNodeRepository.save(node);
    }

    // ── suspend / revoke ─────────────────────────────────────────────────────────────────────────

    /** ACTIVE → SUSPENDED. Distribution stops on the next poll. */
    @Transactional
    public EdgeNode suspend(Long id, String adminUser) {
        EdgeNode node = loadForTenant(id);
        if (node.getStatus().isTerminal()) {
            throw EdgeEnrollmentException.conflict(
                    "edge node is " + node.getStatus() + " — this state is terminal");
        }
        if (node.getStatus() != EdgeNodeStatus.ACTIVE) {
            throw EdgeEnrollmentException.conflict(
                    "only an ACTIVE edge node can be suspended (node is " + node.getStatus() + ")");
        }
        node.setStatus(EdgeNodeStatus.SUSPENDED);
        node.setSuspendedAt(Instant.now());
        node.setApprovedBy(adminUser);
        log.warn("Edge node {} suspended by {} — bundle distribution stopped", id, adminUser);
        return edgeNodeRepository.save(node);
    }

    /**
     * Revoke a node. Irreversible: the row stays for audit, the keys stay pinned so the same edge
     * identity can never be silently re-used, and distribution stops immediately — the very next
     * {@link #isAuthorized(String)} call returns false.
     */
    @Transactional
    public EdgeNode revoke(Long id, String adminUser) {
        EdgeNode node = loadForTenant(id);
        if (node.getStatus() == EdgeNodeStatus.REVOKED) {
            return node; // idempotent; revocation is already permanent
        }
        if (node.getStatus() == EdgeNodeStatus.REJECTED) {
            throw EdgeEnrollmentException.conflict("edge node is REJECTED — this state is terminal");
        }
        node.setStatus(EdgeNodeStatus.REVOKED);
        node.setRevokedAt(Instant.now());
        node.setApprovedBy(adminUser);
        destroyEnrollmentCode(node);
        log.warn("Edge node {} REVOKED by {} — bundle distribution stopped permanently", id, adminUser);
        return edgeNodeRepository.save(node);
    }

    // ── distribution-side authorisation ──────────────────────────────────────────────────────────

    /**
     * The gate used by the distribution and metrics endpoints. True only when the node exists, is
     * ACTIVE, has its keys pinned, and its owning PSP is itself ACTIVE. Everything else — unknown,
     * pending, approved-but-not-activated, suspended, revoked, or owned by a non-active PSP — is
     * false, and the caller must answer 403 with no bundle bytes.
     */
    @Transactional(readOnly = true)
    public boolean isAuthorized(String edgeId) {
        return findAuthorized(edgeId).isPresent();
    }

    /** {@link #isAuthorized(String)}, returning the node when authorised. */
    @Transactional(readOnly = true)
    public Optional<EdgeNode> findAuthorized(String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            return Optional.empty();
        }
        return edgeNodeRepository.findByEdgeId(edgeId.trim())
                .filter(n -> n.getStatus().canReceiveBundles())
                .filter(n -> n.getEdgeX25519PublicKey() != null && n.getEdgeEd25519PublicKey() != null)
                .filter(n -> isPspActive(n.getPspId()));
    }

    /** Record a successful poll: liveness plus the bundle version the node now runs (attestation). */
    @Transactional
    public void recordBundleDelivery(Long nodeId, Long bundleVersion, String agentVersion) {
        edgeNodeRepository.findById(nodeId).ifPresent(node -> {
            node.setLastSeenAt(Instant.now());
            if (bundleVersion != null) {
                node.setLastBundleVersion(bundleVersion);
            }
            if (trimToNull(agentVersion) != null) {
                node.setAgentVersion(agentVersion.trim());
            }
            edgeNodeRepository.save(node);
        });
    }

    /** Record liveness only (metrics upload, 304 poll). */
    @Transactional
    public void recordSeen(Long nodeId, String agentVersion) {
        recordBundleDelivery(nodeId, null, agentVersion);
    }

    // ── tenant-scoped reads for the admin API ────────────────────────────────────────────────────

    /** Nodes visible to the caller: a PSP user sees only its own; a platform admin sees all. */
    @Transactional(readOnly = true)
    public List<EdgeNode> listVisibleNodes(Long requestedPspId) {
        Long pspId = pspIsolationService.sanitizePspId(requestedPspId);
        if (pspId == null) {
            // Platform administrator with no PSP filter — full fleet.
            return edgeNodeRepository.findAllByOrderByCreatedAtDesc();
        }
        return edgeNodeRepository.findByPspIdOrderByCreatedAtDesc(pspId);
    }

    /** A single node, refused unless the caller's PSP owns it. */
    @Transactional(readOnly = true)
    public EdgeNode getNode(Long id) {
        return loadForTenant(id);
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────────

    private EdgeNode loadForTenant(Long id) {
        if (id == null) {
            throw EdgeEnrollmentException.badRequest("edge node id is required");
        }
        EdgeNode node = edgeNodeRepository.findById(id)
                .orElseThrow(() -> EdgeEnrollmentException.notFound("edge node " + id + " not found"));
        // Throws SecurityException for a cross-tenant access attempt; surfaced as 403.
        pspIsolationService.validatePspAccess(node.getPspId());
        return node;
    }

    private void requirePspExists(Long pspId) {
        pspRepository.findById(pspId)
                .orElseThrow(() -> EdgeEnrollmentException.notFound("PSP " + pspId + " not found"));
    }

    /**
     * Refuse an activation whose edge-side {@code pspId} config disagrees with the node's real
     * owner. Called before any mutation, so a refusal costs the operator nothing but a retry.
     *
     * <p>The comparison is strict against the numeric PSP id — not the PSP code — so there is
     * exactly one correct value, and the error names it explicitly rather than leaving the operator
     * to guess which of several accepted forms is expected.
     */
    private void requireAssertedPspIdMatches(String assertedPspId, EdgeNode node) {
        if (assertedPspId == null || assertedPspId.isBlank()) {
            return; // nothing asserted is not a disagreement
        }
        String expected = String.valueOf(node.getPspId());
        if (!expected.equals(assertedPspId.trim())) {
            log.warn("Refusing activation of edge node {}: edge asserts pspId '{}' but the "
                    + "enrollment code belongs to PSP {}", node.getId(), assertedPspId, expected);
            throw EdgeEnrollmentException.forbidden(
                    "PSP mismatch: this enrollment code belongs to PSP " + expected
                            + ", but the edge is configured with pspId '" + assertedPspId.trim()
                            + "'. Set the edge host's PSP id to " + expected
                            + " and retry — the enrollment code has not been used.");
        }
    }

    private void requirePspActive(Long pspId, String action) {
        Psp psp = pspRepository.findById(pspId)
                .orElseThrow(() -> EdgeEnrollmentException.notFound("PSP " + pspId + " not found"));
        if (!psp.isActive()) {
            throw EdgeEnrollmentException.forbidden(
                    "cannot " + action + " an edge node: owning PSP is not ACTIVE");
        }
    }

    private boolean isPspActive(Long pspId) {
        return pspId != null && pspRepository.findById(pspId).map(Psp::isActive).orElse(false);
    }

    /** True when the node has no pinned keys yet, or the presented keys are byte-identical. */
    private static boolean keysMatch(EdgeNode node, String x25519, String ed25519) {
        if (node.getEdgeX25519PublicKey() == null && node.getEdgeEd25519PublicKey() == null) {
            return true; // first activation — nothing pinned yet
        }
        return constantTimeEquals(node.getEdgeX25519PublicKey(), x25519)
                && constantTimeEquals(node.getEdgeEd25519PublicKey(), ed25519);
    }

    private static void destroyEnrollmentCode(EdgeNode node) {
        node.setEnrollmentCodeHash(null);
        node.setEnrollmentCodeExpiresAt(null);
    }

    private String generateEnrollmentCode() {
        byte[] bytes = new byte[ENROLLMENT_CODE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Normalise an inbound public key to the canonical stored form: base64 of the <b>raw 32 bytes</b>
     * (RFC-7748 for X25519, RFC-8032 for Ed25519), which is what HSE-1 consumes directly.
     *
     * <p>Accepts what the edge host sends. {@code EdgeKeys.encodePublic} emits base64 of the X.509
     * SubjectPublicKeyInfo DER (RFC 8410, 44 bytes: a fixed 12-byte prefix then the raw key), and
     * raw 32-byte encodings are accepted too so a key can be provisioned either way. Both are
     * canonicalised here, so a node that re-enrolls with the same key in the other encoding still
     * matches the pinned value byte-for-byte.
     */
    private static String normalisePublicKey(String b64, String field) {
        if (b64 == null || b64.isBlank()) {
            throw EdgeEnrollmentException.badRequest(field + " is required");
        }
        String trimmed = b64.trim();
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            try {
                decoded = Base64.getUrlDecoder().decode(trimmed);
            } catch (IllegalArgumentException e2) {
                throw EdgeEnrollmentException.badRequest(field + " is not valid base64");
            }
        }
        byte[] raw;
        if (decoded.length == PUBLIC_KEY_BYTES) {
            raw = decoded;
        } else if (decoded.length == SPKI_PREFIX_BYTES + PUBLIC_KEY_BYTES) {
            raw = Arrays.copyOfRange(decoded, SPKI_PREFIX_BYTES, decoded.length);
        } else {
            throw EdgeEnrollmentException.badRequest(field + " must decode to " + PUBLIC_KEY_BYTES
                    + " bytes (raw) or " + (SPKI_PREFIX_BYTES + PUBLIC_KEY_BYTES)
                    + " bytes (X.509 SPKI); got " + decoded.length);
        }
        return Base64.getEncoder().encodeToString(raw);
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
