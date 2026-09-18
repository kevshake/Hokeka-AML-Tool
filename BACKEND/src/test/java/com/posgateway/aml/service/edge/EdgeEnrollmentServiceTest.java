package com.posgateway.aml.service.edge;

import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Proves the enrollment lifecycle of {@code docs/architecture/edge-channel-contract.md} §7:
 * code validation, expiry, single use, trust-on-first-activation key pinning, irreversible
 * revocation stopping distribution, and PSP tenant isolation.
 *
 * <p>Backed by an in-memory stand-in for {@link EdgeNodeRepository} so state transitions are
 * observed exactly as they would be against the database, rather than stubbed away.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EdgeEnrollmentServiceTest {

    private static final long PSP_ID = 42L;

    /** What a correctly configured edge host asserts in its enrollment body. */
    private static final String PSP_ID_ASSERTED = "42";

    @Mock private EdgeNodeRepository edgeNodeRepository;
    @Mock private PspRepository pspRepository;
    @Mock private PspIsolationService pspIsolationService;

    private EdgeEnrollmentService service;

    /** In-memory table backing the repository mock. */
    private final Map<Long, EdgeNode> table = new HashMap<>();
    private final AtomicLong ids = new AtomicLong();

    private Psp psp;
    private String x25519PubB64;
    private String ed25519PubB64;

    @BeforeEach
    void setUp() throws Exception {
        service = new EdgeEnrollmentService(edgeNodeRepository, pspRepository, pspIsolationService);

        psp = new Psp();
        psp.setPspId(PSP_ID);
        psp.setStatus("ACTIVE");
        when(pspRepository.findById(PSP_ID)).thenAnswer(i -> Optional.of(psp));

        when(edgeNodeRepository.save(any(EdgeNode.class))).thenAnswer(i -> {
            EdgeNode n = i.getArgument(0);
            if (n.getId() == null) {
                n.setId(ids.incrementAndGet());
            }
            table.put(n.getId(), n);
            return n;
        });
        when(edgeNodeRepository.findById(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(table.get(i.<Long>getArgument(0))));
        when(edgeNodeRepository.findByEnrollmentCodeHash(any())).thenAnswer(i -> {
            String hash = i.getArgument(0);
            return table.values().stream()
                    .filter(n -> hash.equals(n.getEnrollmentCodeHash()))
                    .findFirst();
        });
        when(edgeNodeRepository.findByEdgeId(any())).thenAnswer(i -> {
            String edgeId = i.getArgument(0);
            return table.values().stream()
                    .filter(n -> edgeId.equals(n.getEdgeId()))
                    .findFirst();
        });

        KeyPair x = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyPair ed = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        x25519PubB64 = Base64.getEncoder().encodeToString(
                com.posgateway.aml.edge.crypto.HokekaSecureEnvelope
                        .rawFromX25519Public((XECPublicKey) x.getPublic()));
        ed25519PubB64 = Base64.getEncoder().encodeToString(
                com.posgateway.aml.config.edge.EdgeControlPlaneKeys
                        .rawEd25519Public((EdECPublicKey) ed.getPublic()));
    }

    // ── request ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void requestNodeIssuesAHighEntropySingleUseCodeAndStoresOnlyItsHash() {
        var result = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");

        assertEquals(EdgeNodeStatus.PENDING, result.node().getStatus());
        assertEquals(PSP_ID, result.node().getPspId());
        assertNotNull(result.rawEnrollmentCode());
        // 32 random bytes, base64url, unpadded.
        assertEquals(43, result.rawEnrollmentCode().length());

        // The raw code is NOT stored anywhere — only its SHA-256 digest.
        assertFalse(result.rawEnrollmentCode().equals(result.node().getEnrollmentCodeHash()));
        assertEquals(sha256Hex(result.rawEnrollmentCode()), result.node().getEnrollmentCodeHash());

        long ttlSeconds = Duration.between(Instant.now(), result.expiresAt()).getSeconds();
        assertTrue(ttlSeconds > Duration.ofHours(23).getSeconds()
                && ttlSeconds <= Duration.ofHours(24).getSeconds(), "24h TTL, got " + ttlSeconds + "s");
    }

    @Test
    void twoRequestsNeverProduceTheSameCode() {
        String a = service.requestNode(PSP_ID, "n1", "u").rawEnrollmentCode();
        String b = service.requestNode(PSP_ID, "n2", "u").rawEnrollmentCode();
        assertFalse(a.equals(b));
    }

    // ── activation ───────────────────────────────────────────────────────────────────────────────

    @Test
    void activationPinsTheKeysAndMakesTheNodeActive() {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        service.approve(requested.node().getId(), "platform-admin");

        EdgeNode active = service.activate(requested.rawEnrollmentCode(), "acme-eu-1",
                PSP_ID_ASSERTED, x25519PubB64, ed25519PubB64, "edge01.acme.internal", "1.4.0");

        assertEquals(EdgeNodeStatus.ACTIVE, active.getStatus());
        assertEquals("acme-eu-1", active.getEdgeId());
        assertEquals(x25519PubB64, active.getEdgeX25519PublicKey());
        assertEquals(ed25519PubB64, active.getEdgeEd25519PublicKey());
        assertNotNull(active.getActivatedAt());
        // Single-use: the digest is destroyed on consumption.
        assertNull(active.getEnrollmentCodeHash());
        assertNull(active.getEnrollmentCodeExpiresAt());
        assertTrue(service.isAuthorized("acme-eu-1"));
    }

    @Test
    void anUnknownCodeIsRefused() {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        service.approve(requested.node().getId(), "platform-admin");

        EdgeEnrollmentException e = assertThrows(EdgeEnrollmentException.class,
                () -> service.activate("not-the-code", "acme-eu-1", PSP_ID_ASSERTED, x25519PubB64,
                        ed25519PubB64, null, null));
        assertEquals(403, e.getStatus());
    }

    @Test
    void anExpiredCodeIsRefused() {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        service.approve(requested.node().getId(), "platform-admin");
        requested.node().setEnrollmentCodeExpiresAt(Instant.now().minusSeconds(1));

        EdgeEnrollmentException e = assertThrows(EdgeEnrollmentException.class,
                () -> service.activate(requested.rawEnrollmentCode(), "acme-eu-1", PSP_ID_ASSERTED,
                        x25519PubB64, ed25519PubB64, null, null));
        assertEquals(403, e.getStatus());
        assertTrue(e.getMessage().contains("expired"));
    }

    @Test
    void anUnapprovedNodeCannotActivate() {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        // deliberately NOT approved

        EdgeEnrollmentException e = assertThrows(EdgeEnrollmentException.class,
                () -> service.activate(requested.rawEnrollmentCode(), "acme-eu-1", PSP_ID_ASSERTED,
                        x25519PubB64, ed25519PubB64, null, null));
        assertEquals(403, e.getStatus());
        assertTrue(e.getMessage().contains("not APPROVED"));
    }

    @Test
    void anEnrollmentCodeCanOnlyBeUsedOnce() {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        service.approve(requested.node().getId(), "platform-admin");
        service.activate(requested.rawEnrollmentCode(), "acme-eu-1", PSP_ID_ASSERTED,
                x25519PubB64, ed25519PubB64, null, null);

        EdgeEnrollmentException e = assertThrows(EdgeEnrollmentException.class,
                () -> service.activate(requested.rawEnrollmentCode(), "acme-eu-2", PSP_ID_ASSERTED,
                        x25519PubB64, ed25519PubB64, null, null));
        assertEquals(403, e.getStatus());
        assertTrue(e.getMessage().contains("already-used"));
    }

    @Test
    void aLaterActivationForTheSameEdgeIdWithDifferentKeysIsRejected() throws Exception {
        var first = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        service.approve(first.node().getId(), "platform-admin");
        service.activate(first.rawEnrollmentCode(), "acme-eu-1", PSP_ID_ASSERTED,
                x25519PubB64, ed25519PubB64, null, null);

        // A second, legitimately issued and approved node tries to claim the same edge id with a
        // fresh key pair — trust-on-first-activation must refuse it.
        var second = service.requestNode(PSP_ID, "Impostor", "psp-admin");
        service.approve(second.node().getId(), "platform-admin");

        KeyPair otherX = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyPair otherEd = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String otherX25519 = Base64.getEncoder().encodeToString(
                com.posgateway.aml.edge.crypto.HokekaSecureEnvelope
                        .rawFromX25519Public((XECPublicKey) otherX.getPublic()));
        String otherEd25519 = Base64.getEncoder().encodeToString(
                com.posgateway.aml.config.edge.EdgeControlPlaneKeys
                        .rawEd25519Public((EdECPublicKey) otherEd.getPublic()));

        EdgeEnrollmentException e = assertThrows(EdgeEnrollmentException.class,
                () -> service.activate(second.rawEnrollmentCode(), "acme-eu-1", PSP_ID_ASSERTED,
                        otherX25519, otherEd25519, null, null));
        assertEquals(409, e.getStatus());
        assertTrue(e.getMessage().contains("pinned"));

        // The original node is untouched and still authorised.
        assertEquals(x25519PubB64, table.get(first.node().getId()).getEdgeX25519PublicKey());
        assertTrue(service.isAuthorized("acme-eu-1"));
    }

    @Test
    void aMalformedPublicKeyIsRejectedBeforeAnythingIsPinned() {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        service.approve(requested.node().getId(), "platform-admin");

        EdgeEnrollmentException e = assertThrows(EdgeEnrollmentException.class,
                () -> service.activate(requested.rawEnrollmentCode(), "acme-eu-1", PSP_ID_ASSERTED,
                        Base64.getEncoder().encodeToString(new byte[16]), ed25519PubB64, null, null));
        assertEquals(400, e.getStatus());
        assertNull(table.get(requested.node().getId()).getEdgeX25519PublicKey());
    }

    // ── tenant self-assertion ────────────────────────────────────────────────────────────────────

    @Test
    void anEdgeAssertingTheWrongPspIdIsRefusedAndNothingIsMutated() {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        service.approve(requested.node().getId(), "platform-admin");
        String code = requested.rawEnrollmentCode();

        EdgeEnrollmentException e = assertThrows(EdgeEnrollmentException.class,
                () -> service.activate(code, "acme-eu-1", "99", x25519PubB64, ed25519PubB64,
                        null, null));

        assertEquals(403, e.getStatus());
        // The operator must be able to see both what was supplied and what is expected.
        assertTrue(e.getMessage().contains("99"), "message must name the supplied pspId: " + e.getMessage());
        assertTrue(e.getMessage().contains("42"), "message must name the expected pspId: " + e.getMessage());

        // Refused before any mutation: still APPROVED, no keys pinned, code still live.
        EdgeNode stored = table.get(requested.node().getId());
        assertEquals(EdgeNodeStatus.APPROVED, stored.getStatus());
        assertNull(stored.getEdgeId());
        assertNull(stored.getEdgeX25519PublicKey());
        assertNull(stored.getEdgeEd25519PublicKey());
        assertNotNull(stored.getEnrollmentCodeHash());

        // ...so the operator fixes the edge config and retries with the SAME code.
        EdgeNode active = service.activate(code, "acme-eu-1", PSP_ID_ASSERTED, x25519PubB64,
                ed25519PubB64, null, null);
        assertEquals(EdgeNodeStatus.ACTIVE, active.getStatus());
    }

    @Test
    void anEdgeThatAssertsNoPspIdIsStillAllowedThrough() {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        service.approve(requested.node().getId(), "platform-admin");

        // Absent/blank is not a disagreement — only a contradicting value is.
        assertEquals(EdgeNodeStatus.ACTIVE, service.activate(requested.rawEnrollmentCode(),
                "acme-eu-1", null, x25519PubB64, ed25519PubB64, null, null).getStatus());
    }

    @Test
    void theAssertedPspIdIsComparedAfterTrimmingWhitespace() {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        service.approve(requested.node().getId(), "platform-admin");

        assertEquals(EdgeNodeStatus.ACTIVE, service.activate(requested.rawEnrollmentCode(),
                "acme-eu-1", "  42 ", x25519PubB64, ed25519PubB64, null, null).getStatus());
    }

    // ── PSP-before-edge ordering ─────────────────────────────────────────────────────────────────

    @Test
    void anEdgeNodeCannotBeApprovedOrActivatedWhileItsPspIsNotActive() {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        psp.setStatus("PENDING");

        EdgeEnrollmentException approveFailure = assertThrows(EdgeEnrollmentException.class,
                () -> service.approve(requested.node().getId(), "platform-admin"));
        assertEquals(403, approveFailure.getStatus());

        // Even if the node were somehow APPROVED, activation re-checks the PSP.
        requested.node().setStatus(EdgeNodeStatus.APPROVED);
        EdgeEnrollmentException activateFailure = assertThrows(EdgeEnrollmentException.class,
                () -> service.activate(requested.rawEnrollmentCode(), "acme-eu-1", PSP_ID_ASSERTED,
                        x25519PubB64, ed25519PubB64, null, null));
        assertEquals(403, activateFailure.getStatus());
        assertTrue(activateFailure.getMessage().contains("PSP is not ACTIVE"));
    }

    @Test
    void anActiveNodeStopsBeingAuthorisedIfItsPspIsSuspended() {
        EdgeNode node = activatedNode("acme-eu-1");
        assertTrue(service.isAuthorized(node.getEdgeId()));

        psp.setStatus("SUSPENDED");
        assertFalse(service.isAuthorized(node.getEdgeId()));
    }

    // ── suspend / revoke ─────────────────────────────────────────────────────────────────────────

    @Test
    void suspensionStopsDistribution() {
        EdgeNode node = activatedNode("acme-eu-1");
        service.suspend(node.getId(), "platform-admin");

        assertEquals(EdgeNodeStatus.SUSPENDED, table.get(node.getId()).getStatus());
        assertFalse(service.isAuthorized("acme-eu-1"));
        assertTrue(service.findAuthorized("acme-eu-1").isEmpty());
    }

    @Test
    void revocationStopsDistributionImmediatelyAndIsIrreversible() {
        EdgeNode node = activatedNode("acme-eu-1");
        service.revoke(node.getId(), "platform-admin");

        assertEquals(EdgeNodeStatus.REVOKED, table.get(node.getId()).getStatus());
        assertNotNull(table.get(node.getId()).getRevokedAt());
        assertFalse(service.isAuthorized("acme-eu-1"), "a revoked edge must never receive a bundle");

        // No transition leads out of REVOKED.
        assertThrows(EdgeEnrollmentException.class, () -> service.approve(node.getId(), "admin"));
        assertThrows(EdgeEnrollmentException.class, () -> service.suspend(node.getId(), "admin"));
        // ...and re-revoking is a harmless no-op rather than an error.
        assertEquals(EdgeNodeStatus.REVOKED, service.revoke(node.getId(), "admin").getStatus());
        assertFalse(service.isAuthorized("acme-eu-1"));
    }

    @Test
    void rejectionIsTerminalAndDestroysTheCode() {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        EdgeNode rejected = service.reject(requested.node().getId(), "platform-admin");

        assertEquals(EdgeNodeStatus.REJECTED, rejected.getStatus());
        assertNull(rejected.getEnrollmentCodeHash());
        assertThrows(EdgeEnrollmentException.class,
                () -> service.activate(requested.rawEnrollmentCode(), "acme-eu-1", PSP_ID_ASSERTED,
                        x25519PubB64, ed25519PubB64, null, null));
        assertThrows(EdgeEnrollmentException.class,
                () -> service.approve(requested.node().getId(), "platform-admin"));
    }

    // ── authorisation edge cases ─────────────────────────────────────────────────────────────────

    @Test
    void anUnknownOrUnenrolledEdgeIsNeverAuthorised() {
        assertFalse(service.isAuthorized("nobody"));
        assertFalse(service.isAuthorized(null));
        assertFalse(service.isAuthorized("  "));

        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        service.approve(requested.node().getId(), "platform-admin");
        // APPROVED but never activated: no pinned keys, so still not authorised.
        assertFalse(service.isAuthorized("acme-eu-1"));
    }

    // ── PSP tenant isolation ─────────────────────────────────────────────────────────────────────

    @Test
    void aPspUserCannotTouchAnotherPspsNode() {
        EdgeNode node = activatedNode("acme-eu-1");

        doThrow(new SecurityException("Cannot access data from another PSP"))
                .when(pspIsolationService).validatePspAccess(PSP_ID);

        assertThrows(SecurityException.class, () -> service.getNode(node.getId()));
        assertThrows(SecurityException.class, () -> service.approve(node.getId(), "other-psp"));
        assertThrows(SecurityException.class, () -> service.suspend(node.getId(), "other-psp"));
        assertThrows(SecurityException.class, () -> service.revoke(node.getId(), "other-psp"));

        // The node is untouched by the refused attempts.
        assertEquals(EdgeNodeStatus.ACTIVE, table.get(node.getId()).getStatus());
    }

    @Test
    void aPspUserCannotRequestANodeForAnotherPsp() {
        doThrow(new SecurityException("Cannot access data from another PSP"))
                .when(pspIsolationService).validatePspAccess(99L);

        assertThrows(SecurityException.class, () -> service.requestNode(99L, "Someone else", "u"));
    }

    @Test
    void listingIsScopedToTheCallersPsp() {
        activatedNode("acme-eu-1");
        when(pspIsolationService.sanitizePspId(any())).thenReturn(PSP_ID);
        when(edgeNodeRepository.findByPspIdOrderByCreatedAtDesc(PSP_ID))
                .thenReturn(table.values().stream().toList());

        assertEquals(1, service.listVisibleNodes(null).size());
        // A platform administrator (sanitizePspId -> null) gets the whole fleet instead.
        when(pspIsolationService.sanitizePspId(any())).thenReturn(null);
        when(edgeNodeRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(table.values().stream().toList());
        assertEquals(1, service.listVisibleNodes(null).size());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private EdgeNode activatedNode(String edgeId) {
        var requested = service.requestNode(PSP_ID, "Acme EU 1", "psp-admin");
        service.approve(requested.node().getId(), "platform-admin");
        return service.activate(requested.rawEnrollmentCode(), edgeId, PSP_ID_ASSERTED, x25519PubB64,
                ed25519PubB64, "host", "1.0.0");
    }

    private static String sha256Hex(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
