package com.hokeka.edge.activation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.channel.SecureChannel;
import com.hokeka.edge.crypto.EdgeKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * First-boot activation, fail-closed.
 *
 * <p>On the first start the node generates its own X25519 (bundle decryption) and Ed25519 (metrics
 * signing) keypairs, persists them to the credentials file with owner-only permissions, and presents
 * the public halves together with the operator-supplied enrollment code to
 * {@code POST /api/v1/edge/enroll}. Keys are pinned on the control plane at first activation
 * (contract §7), so the credentials file is the node's identity for life — losing it means
 * re-enrollment with fresh keys.
 *
 * <p>Until the control plane confirms the node is {@code ACTIVE}, this gate stays closed:
 * {@code EdgeEngine.evaluate} returns HOLD and {@code /edge/status} reports {@code unauthorized}.
 */
@Service
public class ActivationService implements AuthorizationGate {

    private static final Logger log = LoggerFactory.getLogger(ActivationService.class);
    private static final Duration ENROLL_RETRY_INTERVAL = Duration.ofSeconds(60);
    private static final Set<PosixFilePermission> OWNER_RW = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_RWX = PosixFilePermissions.fromString("rwx------");

    private final ControlPlaneProperties properties;
    private final Optional<SecureChannel> channel;
    private final ObjectMapper mapper;
    private final boolean devProfile;

    private volatile EdgeIdentity identity;
    private volatile boolean active;
    private volatile String reason = "activation has not been attempted yet";
    private volatile Instant lastEnrollAttempt = Instant.EPOCH;
    private volatile PrivateKey x25519Private;
    private volatile PrivateKey ed25519Private;

    public ActivationService(ControlPlaneProperties properties, Optional<SecureChannel> channel,
                             ObjectMapper mapper, Environment environment) {
        this.properties = properties;
        this.channel = channel;
        this.mapper = mapper;
        this.devProfile = environment.matchesProfiles("dev");
        if (devProfile) {
            log.warn("Profile 'dev' is active — the authorization gate is OPEN without control-plane "
                    + "activation. Never run a PSP site on this profile.");
        }
    }

    // ── AuthorizationGate ───────────────────────────────────────────────────────────────────────

    @Override
    public boolean authorized() {
        return active || devProfile;
    }

    @Override
    public String state() {
        return authorized() ? STATE_ACTIVE : STATE_UNAUTHORIZED;
    }

    @Override
    public String reason() {
        return authorized() ? "" : reason;
    }

    // ── lifecycle ───────────────────────────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.isEnabled()) {
            reason = "control-plane channel is disabled (hokeka.controlplane.enabled=false)";
            log.warn("Control-plane channel disabled — this node stays UNAUTHORIZED and will only HOLD.");
            return;
        }
        try {
            loadOrCreateIdentity();
        } catch (RuntimeException e) {
            reason = "credentials unavailable: " + e.getMessage();
            log.error("Edge identity could not be established: {}", e.getMessage());
            return;
        }
        ensureEnrolled();
    }

    /** The node's credentials, generating and persisting them on first boot. */
    public synchronized EdgeIdentity loadOrCreateIdentity() {
        EdgeIdentity current = identity;
        if (current != null) {
            return current;
        }
        Path file = Path.of(properties.getIdentityFile());
        if (Files.isReadable(file)) {
            current = read(file);
            if (!current.edgeId().equals(properties.getEdgeId())) {
                throw new SecurityException("credentials at " + file + " belong to edge '" + current.edgeId()
                        + "' but this node is configured as '" + properties.getEdgeId()
                        + "'. Keys are pinned per edge id — move the file aside and re-enroll.");
            }
            log.info("Loaded pinned edge credentials for {} (status {})", current.edgeId(), current.status());
        } else {
            current = generate();
            write(file, current);
            log.info("Generated new edge credentials for {} at {} — register the public keys via enrollment",
                    current.edgeId(), file);
        }
        identity = current;
        x25519Private = EdgeKeys.decodeX25519Private(current.x25519PrivateKey());
        ed25519Private = EdgeKeys.decodeEd25519Private(current.ed25519PrivateKey());
        if (EdgeIdentity.STATUS_ACTIVE.equals(current.status())) {
            active = true;
        }
        return current;
    }

    /**
     * Present the enrollment code + public keys. Retried while the node is not yet ACTIVE: the
     * control plane answers {@code 202} until a platform admin approves the node (contract §7).
     */
    public void ensureEnrolled() {
        if (active || !properties.isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        if (Duration.between(lastEnrollAttempt, now).compareTo(ENROLL_RETRY_INTERVAL) < 0) {
            return;
        }
        lastEnrollAttempt = now;

        if (channel.isEmpty()) {
            reason = "control-plane channel is not configured";
            return;
        }
        if (properties.getEnrollmentCode() == null || properties.getEnrollmentCode().isBlank()) {
            reason = "no enrollment code configured (EDGE_ENROLLMENT_CODE) — ask your Hokeka operator for one";
            log.warn("Activation blocked: {}", reason);
            return;
        }
        EdgeIdentity id;
        try {
            id = loadOrCreateIdentity();
        } catch (RuntimeException e) {
            reason = "credentials unavailable: " + e.getMessage();
            return;
        }
        try {
            RestClient client = channel.get().bootstrapClient();
            ResponseEntity<String> response = client.post()
                    .uri("/api/v1/edge/enroll")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EnrollmentRequest(id.edgeId(), id.pspId(), properties.getEnrollmentCode(),
                            id.x25519PublicKey(), id.ed25519PublicKey()))
                    .retrieve()
                    .toEntity(String.class);

            String status = statusOf(response.getBody());
            if (EdgeIdentity.STATUS_ACTIVE.equalsIgnoreCase(status)) {
                markActive();
            } else {
                reason = "enrollment accepted, awaiting operator approval in the Hokeka portal";
                log.info("Enrollment submitted for {} — {}", id.edgeId(), reason);
            }
        } catch (Exception e) {
            reason = "enrollment failed: " + e.getMessage();
            log.warn("Enrollment attempt for {} failed: {}", id.edgeId(), e.getMessage());
        }
    }

    /** Called by the bundle poller when the control plane serves this node (proof it is ACTIVE). */
    public void markActive() {
        if (active) {
            return;
        }
        active = true;
        reason = "";
        persistStatus(EdgeIdentity.STATUS_ACTIVE);
        log.info("Edge {} is ACTIVE — evaluation is authorized", properties.getEdgeId());
    }

    /** Called when the control plane refuses to serve this node (403 / revoked / suspended). */
    public void markUnauthorized(String why) {
        boolean wasActive = active;
        active = false;
        reason = why;
        if (wasActive) {
            persistStatus(EdgeIdentity.STATUS_PENDING);
            log.error("Edge {} lost authorization: {} — evaluation now HOLDs", properties.getEdgeId(), why);
        }
    }

    public PrivateKey x25519Private() {
        loadOrCreateIdentity();
        return x25519Private;
    }

    public PrivateKey ed25519Private() {
        loadOrCreateIdentity();
        return ed25519Private;
    }

    // ── persistence ─────────────────────────────────────────────────────────────────────────────

    private EdgeIdentity generate() {
        KeyPair x = EdgeKeys.generateX25519();
        KeyPair ed = EdgeKeys.generateEd25519();
        return new EdgeIdentity(
                properties.getEdgeId(), properties.getPspId(),
                EdgeKeys.encodePrivate(x.getPrivate()), EdgeKeys.encodePublic(x.getPublic()),
                EdgeKeys.encodePrivate(ed.getPrivate()), EdgeKeys.encodePublic(ed.getPublic()),
                Instant.now().toString(), EdgeIdentity.STATUS_PENDING);
    }

    private EdgeIdentity read(Path file) {
        try {
            return mapper.readValue(Files.readAllBytes(file), EdgeIdentity.class);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read edge credentials at " + file + ": " + e.getMessage(), e);
        }
    }

    private void write(Path file, EdgeIdentity id) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                Files.createDirectories(parent);
                restrict(parent, OWNER_RWX);
            }
            Files.write(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(id));
            restrict(file, OWNER_RW);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write edge credentials to " + file + ": " + e.getMessage(), e);
        }
    }

    private void persistStatus(String status) {
        EdgeIdentity current = identity;
        if (current == null) {
            return;
        }
        EdgeIdentity updated = current.withStatus(status);
        identity = updated;
        try {
            write(Path.of(properties.getIdentityFile()), updated);
        } catch (RuntimeException e) {
            log.warn("Could not persist activation status: {}", e.getMessage());
        }
    }

    /** POSIX 600/700 where the filesystem supports it; elsewhere the installer's ACLs apply. */
    private static void restrict(Path path, Set<PosixFilePermission> permissions) {
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(path, permissions);
            }
        } catch (IOException | UnsupportedOperationException e) {
            log.warn("Could not restrict permissions on {}: {}", path, e.getMessage());
        }
    }

    private String statusOf(String body) {
        if (body == null || body.isBlank()) {
            return EdgeIdentity.STATUS_PENDING;
        }
        try {
            JsonNode node = mapper.readTree(body);
            return node.path("status").asText(EdgeIdentity.STATUS_PENDING);
        } catch (IOException e) {
            return EdgeIdentity.STATUS_PENDING;
        }
    }

    /** Enrollment body — public keys only; no private material ever crosses the boundary. */
    public record EnrollmentRequest(String edgeId, String pspId, String enrollmentCode,
                                    String x25519PublicKey, String ed25519PublicKey) {
    }
}
