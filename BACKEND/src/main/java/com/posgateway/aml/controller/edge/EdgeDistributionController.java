package com.posgateway.aml.controller.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.dto.edge.EdgeDecisionRequest;
import com.posgateway.aml.dto.edge.EdgeDecisionResponse;
import com.posgateway.aml.dto.edge.EdgeEnrollRequest;
import com.posgateway.aml.dto.edge.EdgeEnrollResponse;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.service.edge.EdgeBundleDistributionService;
import com.posgateway.aml.service.edge.EdgeDecisionService;
import com.posgateway.aml.service.edge.EdgeEnrollmentException;
import com.posgateway.aml.service.edge.EdgeEnrollmentService;
import com.posgateway.aml.service.edge.EdgeIdentityResolver;
import com.posgateway.aml.service.edge.EdgeMetricsIngestService;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Machine-to-machine edge channel — the three endpoints of
 * {@code docs/architecture/edge-channel-contract.md} §5.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>POST</td><td>/edge/enroll</td><td>enrollment code</td><td>202</td></tr>
 *   <tr><td>GET</td><td>/edge/bundle</td><td>mTLS + edge id</td><td>HSE-1 sealed rule IR</td></tr>
 *   <tr><td>POST</td><td>/edge/metrics</td><td>mTLS + edge id</td><td>204</td></tr>
 * </table>
 *
 * <p>These are <b>not</b> session/JWT user endpoints: there is no logged-in principal, so they are
 * permitted in {@code SecurityConfig} and authenticated here instead — by the mTLS client identity
 * ({@link EdgeIdentityResolver}, which never trusts a bare header) plus the node's ACTIVE
 * authorisation state.
 *
 * <p><b>Fail-closed</b> (contract §2): an unknown, unenrolled, pending, suspended or revoked edge
 * gets {@code 403} and <b>no bundle bytes</b> — the bundle is not even compiled before the
 * authorisation check passes.
 */
@RestController
@RequestMapping("/edge")
public class EdgeDistributionController {

    private static final Logger log = LoggerFactory.getLogger(EdgeDistributionController.class);

    private final EdgeIdentityResolver identityResolver;
    private final EdgeEnrollmentService enrollmentService;
    private final EdgeBundleDistributionService distributionService;
    private final EdgeMetricsIngestService metricsIngestService;
    private final EdgeDecisionService edgeDecisionService;
    private final EdgeReplayGuard replayGuard;
    private final EdgeControlPlaneKeys controlPlaneKeys;
    private final EdgeProperties properties;
    private final HokekaSecureEnvelope envelope;
    private final ObjectMapper mapper = new ObjectMapper();

    public EdgeDistributionController(EdgeIdentityResolver identityResolver,
                                      EdgeEnrollmentService enrollmentService,
                                      EdgeBundleDistributionService distributionService,
                                      EdgeMetricsIngestService metricsIngestService,
                                      EdgeDecisionService edgeDecisionService,
                                      EdgeReplayGuard replayGuard,
                                      EdgeControlPlaneKeys controlPlaneKeys,
                                      EdgeProperties properties,
                                      HokekaSecureEnvelope envelope) {
        this.identityResolver = identityResolver;
        this.enrollmentService = enrollmentService;
        this.distributionService = distributionService;
        this.metricsIngestService = metricsIngestService;
        this.edgeDecisionService = edgeDecisionService;
        this.replayGuard = replayGuard;
        this.controlPlaneKeys = controlPlaneKeys;
        this.properties = properties;
        this.envelope = envelope;
    }

    // ── POST /edge/enroll ────────────────────────────────────────────────────────────────────────

    /**
     * First-boot activation. The enrollment code is the credential; the presented public keys are
     * pinned for the life of the node. Contract §5 specifies {@code 202} on success, and the edge
     * host reads {@code status} from the body to decide whether it may open its authorization gate.
     *
     * <p>{@code pspId} in the request grants no privilege — the owning PSP comes from the
     * enrollment code — but it must still <b>agree</b> with it. A node whose local config names the
     * wrong tenant would mislabel its metrics, logs and support diagnostics, so the mismatch is
     * refused with {@code 403} rather than logged. The check happens before any state changes, so
     * the operator can fix the config and retry with the same code.
     */
    @PostMapping(path = "/enroll", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> enroll(@RequestBody EdgeEnrollRequest request) {
        try {
            EdgeNode node = enrollmentService.activate(
                    request.enrollmentCode(),
                    request.edgeId(),
                    request.pspId(),
                    request.x25519PublicKey(),
                    request.ed25519PublicKey(),
                    request.hostname(),
                    request.agentVersion());

            return ResponseEntity.accepted().body(new EdgeEnrollResponse(
                    node.getEdgeId(),
                    node.getStatus().name(),
                    controlPlaneKeys.signingPublicKeyBase64(),
                    controlPlaneKeys.ingestPublicKeyBase64(),
                    EdgeBundleDistributionService.BUNDLE_CONTEXT,
                    EdgeMetricsIngestService.METRICS_CONTEXT,
                    EdgeReplayGuard.MAX_SKEW.toSeconds()));
        } catch (EdgeEnrollmentException e) {
            log.warn("Edge activation refused ({}): {}", e.getStatus(), e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
        }
    }

    // ── GET /edge/bundle ─────────────────────────────────────────────────────────────────────────

    /**
     * Serve the HSE-1 sealed rule bundle for the calling edge.
     *
     * <p>{@code If-None-Match: <version>} short-circuits to {@code 304} when the edge already runs
     * the current version — the common case for a 15 s poll, and it skips the sealing work entirely.
     */
    @GetMapping(path = "/bundle", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> bundle(HttpServletRequest request,
                                         @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                         String ifNoneMatch) {
        Optional<EdgeNode> authorized = authorize(request);
        if (authorized.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        EdgeNode node = authorized.get();
        String agentVersion = identityResolver.agentVersion(request);

        long currentVersion = distributionService.currentVersion(node.getPspId());
        if (matchesEtag(ifNoneMatch, currentVersion)) {
            enrollmentService.recordSeen(node.getId(), agentVersion);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(quoted(currentVersion))
                    .build();
        }

        EdgeBundleDistributionService.SealedBundle sealed;
        try {
            sealed = distributionService.sealFor(node);
        } catch (EdgeBundleDistributionService.EmptyBundleException e) {
            // A zero-rule bundle would tell the edge to allow everything. Answer 503 (not 200, not
            // 304) so the poller retries without advancing its ETag and the node keeps its previous
            // verified bundle — or stays fail-closed if it never had one.
            enrollmentService.recordSeen(node.getId(), agentVersion);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Edge-Bundle-Error", "empty-rule-set")
                    .build();
        }
        enrollmentService.recordBundleDelivery(node.getId(), sealed.version(), agentVersion);

        return ResponseEntity.ok()
                .eTag(quoted(sealed.version()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Edge-Bundle-Version", Long.toString(sealed.version()))
                // Surface how many rules were dropped as uncompilable; previously computed and
                // discarded, leaving a silently-degraded rule set invisible to operators.
                .header("X-Edge-Rules-Skipped", Integer.toString(sealed.skippedRules().size()))
                .header("X-Edge-Rules-Count", Integer.toString(sealed.ruleCount()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(sealed.sealed());
    }

    // ── POST /edge/metrics ───────────────────────────────────────────────────────────────────────

    /**
     * Accept an HSE-1 sealed aggregate metrics upload.
     *
     * <p>Opened with context {@value EdgeMetricsIngestService#METRICS_CONTEXT} (so a bundle
     * envelope can never be replayed here), signature-verified against the edge's <b>pinned</b>
     * Ed25519 key, then replay-checked per contract §4 (nonce LRU + 300 s skew) and finally
     * allow-list validated so nothing per-transaction can be stored.
     */
    @PostMapping(path = "/metrics", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> metrics(HttpServletRequest request, @RequestBody byte[] sealed) {
        Optional<EdgeNode> authorized = authorize(request);
        if (authorized.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        EdgeNode node = authorized.get();

        if (sealed == null || sealed.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "empty metrics envelope"));
        }
        if (sealed.length > properties.getMetrics().getMaxPayloadBytes()) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", "metrics envelope exceeds the configured maximum"));
        }

        try {
            PublicKey edgeSigningKey = EdgeControlPlaneKeys.ed25519PublicFromRaw(
                    Base64.getDecoder().decode(node.getEdgeEd25519PublicKey()));

            byte[] plaintext = envelope.open(
                    sealed,
                    controlPlaneKeys.ingestPrivateKey(),
                    edgeSigningKey,
                    EdgeMetricsIngestService.METRICS_CONTEXT.getBytes(StandardCharsets.UTF_8));

            JsonNode replayEnvelope = mapper.readTree(plaintext);
            EdgeReplayGuard.OpenedEnvelope opened =
                    replayGuard.validate(replayEnvelope, node.getEdgeId(), Instant.now());

            metricsIngestService.ingest(node, opened.payload());
            enrollmentService.recordSeen(node.getId(), identityResolver.agentVersion(request));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            log.warn("Rejected metrics upload from edge {}: {}", node.getEdgeId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("Malformed metrics upload from edge {}: {}", node.getEdgeId(), e.toString());
            return ResponseEntity.badRequest().body(Map.of("error", "malformed metrics envelope"));
        }
    }

    // ── POST /edge/decision ──────────────────────────────────────────────────────────────────────

    /**
     * Edge → Control Plane JEV decision path. Authenticated via mTLS + ACTIVE node state.
     * Laya is called only here on the Control Plane — never on the Edge Node.
     */
    @PostMapping(path = "/decision", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EdgeDecisionResponse> decision(HttpServletRequest request,
                                                         @RequestBody EdgeDecisionRequest body) {
        Optional<EdgeNode> authorized = authorize(request);
        if (authorized.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        EdgeNode node = authorized.get();
        EdgeDecisionResponse response = edgeDecisionService.handle(node, body);
        enrollmentService.recordSeen(node.getId(), identityResolver.agentVersion(request));
        return ResponseEntity.ok(response);
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────────

    /**
     * Resolve the calling edge from its verified mTLS identity and check it is authorised. Returns
     * empty for every failure mode, which the callers turn into a bare {@code 403} — no detail is
     * echoed to an unauthenticated caller.
     */
    private Optional<EdgeNode> authorize(HttpServletRequest request) {
        Optional<EdgeIdentityResolver.EdgeIdentity> identity;
        try {
            identity = identityResolver.resolve(request);
        } catch (EdgeIdentityResolver.EdgeIdentityConflictException e) {
            log.warn("Edge identity conflict from {}: {}", request.getRemoteAddr(), e.getMessage());
            return Optional.empty();
        }
        if (identity.isEmpty()) {
            log.debug("No verified edge client-certificate identity on {} {}",
                    request.getMethod(), request.getRequestURI());
            return Optional.empty();
        }
        Optional<EdgeNode> node = enrollmentService.findAuthorized(identity.get().edgeId());
        if (node.isEmpty()) {
            log.warn("Refusing edge {} (identified via {}): not an ACTIVE authorised node",
                    identity.get().edgeId(), identity.get().source());
        }
        return node;
    }

    /** Accepts {@code "37"}, {@code 37} and {@code W/"37"} for {@code If-None-Match}. */
    private static boolean matchesEtag(String ifNoneMatch, long version) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String normalised = candidate.trim();
            if (normalised.startsWith("W/")) {
                normalised = normalised.substring(2).trim();
            }
            if (normalised.length() >= 2 && normalised.startsWith("\"") && normalised.endsWith("\"")) {
                normalised = normalised.substring(1, normalised.length() - 1);
            }
            if (normalised.equals(Long.toString(version)) || "*".equals(normalised)) {
                return true;
            }
        }
        return false;
    }

    private static String quoted(long version) {
        return "\"" + version + "\"";
    }
}
