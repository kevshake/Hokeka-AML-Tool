package com.posgateway.aml.controller.edge;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.service.edge.EdgeNodeAuthorizer;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import com.posgateway.aml.service.edge.records.DecisionRecordIngestService;
import com.posgateway.aml.service.residency.EdgeConfigService;
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
 * Data residency Edge endpoints (docs/EDGE_DATA_RESIDENCY_CONTRACT.md §4.2 and §6.2):
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Body</th><th>Returns</th></tr>
 *   <tr><td>GET</td><td>/edge/config</td><td>—</td><td>HSE-1 sealed Edge config, ETag = configVersion</td></tr>
 *   <tr><td>POST</td><td>/edge/assessments</td><td>application/octet-stream: sealed decision record batch v2</td>
 *       <td>200 acknowledgement</td></tr>
 * </table>
 *
 * <p>Both are machine-to-machine, authorised exactly like {@code /edge/bundle} (verified mTLS
 * identity, node ACTIVE) and answer a bare {@code 403} otherwise. The v1 JSON
 * {@code POST /edge/assessments} stays in {@link EdgeDistributionController}; Spring routes by
 * {@code Content-Type}, so both versions share the path until R12 removes v1.
 */
@RestController
@RequestMapping("/edge")
public class EdgeResidencyController {

    private static final Logger log = LoggerFactory.getLogger(EdgeResidencyController.class);

    private final EdgeNodeAuthorizer authorizer;
    private final EdgeConfigService configService;
    private final DecisionRecordIngestService decisionRecords;
    private final EdgeReplayGuard replayGuard;
    private final EdgeControlPlaneKeys controlPlaneKeys;
    private final EdgeProperties properties;
    private final HokekaSecureEnvelope envelope;
    /** Duplicate JSON keys could smuggle a second value past validation, so they are refused. */
    private final ObjectMapper strictMapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public EdgeResidencyController(EdgeNodeAuthorizer authorizer,
                                   EdgeConfigService configService,
                                   DecisionRecordIngestService decisionRecords,
                                   EdgeReplayGuard replayGuard,
                                   EdgeControlPlaneKeys controlPlaneKeys,
                                   EdgeProperties properties,
                                   HokekaSecureEnvelope envelope) {
        this.authorizer = authorizer;
        this.configService = configService;
        this.decisionRecords = decisionRecords;
        this.replayGuard = replayGuard;
        this.controlPlaneKeys = controlPlaneKeys;
        this.properties = properties;
        this.envelope = envelope;
    }

    // ── GET /edge/config ─────────────────────────────────────────────────────────────────────────

    /**
     * Serves the node's PSP config document, sealed to the node (context
     * {@value EdgeConfigService#CONFIG_CONTEXT}). {@code If-None-Match} with the current version
     * (quoted, bare or weak) answers {@code 304} without sealing.
     */
    @GetMapping(path = "/config", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> config(HttpServletRequest request,
                                         @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                         String ifNoneMatch) {
        Optional<EdgeNode> authorized = authorizer.authorize(request);
        if (authorized.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        EdgeNode node = authorized.get();
        EdgeConfigService.IssuedConfig issued = configService.current(node.getPspId());
        authorizer.recordSeen(node, request);
        if (matchesEtag(ifNoneMatch, issued.version())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(quoted(issued.version()))
                    .header("X-Edge-Config-Version", Long.toString(issued.version()))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }
        EdgeConfigService.SealedConfig sealed = configService.seal(node, issued);
        return ResponseEntity.ok()
                .eTag(quoted(sealed.version()))
                .header("X-Edge-Config-Version", Long.toString(sealed.version()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(sealed.sealed());
    }

    // ── POST /edge/assessments (decision record v2) ──────────────────────────────────────────────

    /**
     * Accepts a sealed decision record batch v2: HSE-1 opened with the Control Plane ingest key and
     * the node's pinned Ed25519 key under context {@value DecisionRecordIngestService#DECISION_RECORDS_CONTEXT},
     * replay-checked (nonce LRU, 300 s skew, edgeId = certificate CN), then ingested record by record.
     * {@code 400} when the envelope cannot be opened or is not a v2 batch, {@code 413} when larger than
     * {@code edge.metrics.max-payload-bytes}.
     */
    @PostMapping(path = "/assessments", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> decisionRecords(HttpServletRequest request, @RequestBody(required = false) byte[] sealed) {
        Optional<EdgeNode> authorized = authorizer.authorize(request);
        if (authorized.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        EdgeNode node = authorized.get();
        if (sealed == null || sealed.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "empty decision record envelope"));
        }
        if (sealed.length > properties.getMetrics().getMaxPayloadBytes()) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", "decision record envelope exceeds the configured maximum"));
        }
        JsonNode batch;
        try {
            PublicKey edgeSigningKey = EdgeControlPlaneKeys.ed25519PublicFromRaw(
                    Base64.getDecoder().decode(node.getEdgeEd25519PublicKey()));
            byte[] plaintext = envelope.open(sealed, controlPlaneKeys.ingestPrivateKey(), edgeSigningKey,
                    DecisionRecordIngestService.DECISION_RECORDS_CONTEXT.getBytes(StandardCharsets.UTF_8));
            JsonNode replayEnvelope = strictMapper.readTree(plaintext);
            batch = replayGuard.validate(replayEnvelope, node.getEdgeId(), Instant.now()).payload();
        } catch (SecurityException e) {
            log.warn("Rejected decision record envelope from edge {}: {}", node.getEdgeId(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "decision record envelope could not be opened"));
        } catch (Exception e) {
            log.warn("Malformed decision record envelope from edge {}: {}", node.getEdgeId(), e.getClass().getSimpleName());
            return ResponseEntity.badRequest().body(Map.of("error", "malformed decision record envelope"));
        }
        try {
            DecisionRecordIngestService.Ack ack = decisionRecords.ingest(node, batch);
            authorizer.recordSeen(node, request);
            return ResponseEntity.ok(ack);
        } catch (DecisionRecordIngestService.InvalidBatchException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "not a decision record batch v2: " + e.getMessage()));
        }
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────────

    /** Accepts {@code "14"}, {@code 14} and {@code W/"14"} (and lists of them) for {@code If-None-Match}. */
    static boolean matchesEtag(String ifNoneMatch, long version) {
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

    /** The quoted ETag value of a version. */
    private static String quoted(long version) {
        return "\"" + version + "\"";
    }
}
