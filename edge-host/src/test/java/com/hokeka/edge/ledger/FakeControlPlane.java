package com.hokeka.edge.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.channel.ReplayGuard;
import com.hokeka.edge.channel.SealedEnvelopeCodec;
import com.hokeka.edge.contract.ContractSchemas;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A test stand-in for the Control Plane's {@code POST /api/v1/edge/assessments} (the real receiver is
 * work package R7). It does what the contract (§4.2) requires of the receiver: opens the HSE-1
 * envelope with the Control Plane X25519 key and the pinned Edge Ed25519 key under context
 * {@code hokeka.decision.records}, runs the replay check, validates the batch against
 * {@code decision-record-batch-v2}, enforces {@code batch.edgeId = record.edgeNodeId}, is idempotent
 * on {@code edgeAssessmentId} (byte-identical re-send = duplicate, differing content = CONFLICT) and
 * answers with an acknowledgement that it validates against {@code decision-record-ack-v2} before
 * sending. Scripted HTTP failures let tests exercise the shipper's retry paths.
 */
final class FakeControlPlane implements AutoCloseable {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;
    private final KeyPair controlPlaneX25519;
    private final PublicKey edgeEd25519;
    private final String edgeId;
    private final ContractSchemas schemas;
    private final SealedEnvelopeCodec codec = new SealedEnvelopeCodec(mapper, new ReplayGuard());

    /** Status codes to answer before processing normally (consumed one per request). */
    final ConcurrentLinkedDeque<Integer> scriptedStatuses = new ConcurrentLinkedDeque<>();
    /** When set, a batch with more records than this is answered 413. */
    volatile int maxRecordsPerBatch = Integer.MAX_VALUE;
    /** Ids to reject, with the code to use. */
    final Map<String, String> rejectIds = new ConcurrentHashMap<>();
    /** Every record ever received, in order (including re-sends). */
    final List<JsonNode> received = new CopyOnWriteArrayList<>();
    /** Accepted store: id → canonical JSON of the first version. */
    final Map<String, String> store = new ConcurrentHashMap<>();
    /** Sealed body sizes and record counts per request. */
    final List<int[]> requests = new CopyOnWriteArrayList<>();
    /** Validation problems seen on received payloads (must stay empty). */
    final List<String> problems = new CopyOnWriteArrayList<>();
    /** Every request that reached this fake, on any path, in arrival order. */
    final List<Outbound> outbound = new CopyOnWriteArrayList<>();
    /** Opened (decrypted) plaintext of every sealed decision-record batch and metrics report. */
    final List<String> openedPayloads = new CopyOnWriteArrayList<>();

    /**
     * One request as the Control Plane saw it.
     *
     * @param method HTTP method
     * @param path   request path
     * @param body   raw request body (sealed bytes for the sealed endpoints)
     */
    record Outbound(String method, String path, byte[] body) {
    }

    FakeControlPlane(KeyPair controlPlaneX25519, PublicKey edgeEd25519, String edgeId, ContractSchemas schemas)
            throws Exception {
        this.controlPlaneX25519 = controlPlaneX25519;
        this.edgeEd25519 = edgeEd25519;
        this.edgeId = edgeId;
        this.schemas = schemas;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Every other path: recorded, then answered like the Control Plane would for a node with
        // nothing new (bundle / config 304, metrics opened and 204). Anything else is 404, and is
        // still recorded so a test can prove it was never called.
        server.createContext("/", ex -> {
            byte[] body = ex.getRequestBody().readAllBytes();
            String path = ex.getRequestURI().getPath();
            outbound.add(new Outbound(ex.getRequestMethod(), path, body));
            int status = switch (path) {
                case "/api/v1/edge/bundle", "/api/v1/edge/config" -> 304;
                case "/api/v1/edge/metrics" -> openMetrics(body);
                default -> 404;
            };
            ex.sendResponseHeaders(status, -1);
            ex.close();
        });
        server.createContext("/api/v1/edge/assessments", ex -> {
            byte[] body = ex.getRequestBody().readAllBytes();
            outbound.add(new Outbound(ex.getRequestMethod(), ex.getRequestURI().getPath(), body));
            int status;
            byte[] out = new byte[0];
            try {
                Integer scripted = scriptedStatuses.pollFirst();
                Response r = scripted != null ? new Response(scripted, null) : handle(ex.getRequestHeaders()
                        .getFirst("Content-Type"), ex.getRequestHeaders().getFirst("X-Hokeka-Edge-Id"), body);
                status = r.status();
                if (r.body() != null) {
                    out = r.body().getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().add("Content-Type", "application/json");
                }
            } catch (Exception e) {
                problems.add("handler: " + e);
                status = 400;
            }
            ex.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                ex.getResponseBody().write(out);
            }
            ex.close();
        });
        server.start();
    }

    private record Response(int status, String body) {
    }

    /** Opens, validates and acknowledges one sealed batch. */
    private Response handle(String contentType, String edgeHeader, byte[] sealed) throws Exception {
        if (!"application/octet-stream".equals(contentType)) {
            problems.add("content type " + contentType);
            return new Response(400, null);
        }
        if (!edgeId.equals(edgeHeader)) {
            problems.add("edge header " + edgeHeader);
        }
        JsonNode batch = codec.open(sealed, controlPlaneX25519.getPrivate(), edgeEd25519,
                SealedEnvelopeCodec.CTX_DECISION_RECORDS, edgeId, Instant.now());
        openedPayloads.add(batch.toString());
        List<String> violations = schemas.validate(ContractSchemas.DECISION_RECORD_BATCH_V2, batch);
        if (!violations.isEmpty()) {
            problems.add("batch schema: " + violations);
            return new Response(400, null);
        }
        int records = batch.path("records").size();
        requests.add(new int[]{sealed.length, records});
        if (records > maxRecordsPerBatch) {
            return new Response(413, null);
        }
        int accepted = 0;
        int duplicates = 0;
        ArrayNode rejected = mapper.createArrayNode();
        for (JsonNode record : batch.path("records")) {
            received.add(record);
            String id = record.path("edgeAssessmentId").asText();
            String canonical = canonical(record);
            if (!batch.path("edgeId").asText().equals(record.path("edgeNodeId").asText())) {
                rejected.addObject().put("edgeAssessmentId", id).put("code", "NODE_MISMATCH");
            } else if (rejectIds.containsKey(id)) {
                rejected.addObject().put("edgeAssessmentId", id).put("code", rejectIds.get(id));
            } else {
                String previous = store.putIfAbsent(id, canonical);
                if (previous == null) {
                    accepted++;
                } else if (previous.equals(canonical)) {
                    duplicates++;
                } else {
                    rejected.addObject().put("edgeAssessmentId", id).put("code", "CONFLICT");
                }
            }
        }
        ObjectNode ack = mapper.createObjectNode();
        ack.put("accepted", accepted);
        ack.put("duplicates", duplicates);
        ack.set("rejected", rejected);
        List<String> ackViolations = schemas.validate(ContractSchemas.DECISION_RECORD_ACK_V2, ack);
        if (!ackViolations.isEmpty()) {
            problems.add("ack schema: " + ackViolations);
        }
        return new Response(200, mapper.writeValueAsString(ack));
    }

    /**
     * Opens a sealed metrics report (context {@code hokeka.metrics.report}) and keeps its plaintext;
     * 204 when it opens, 400 (and a recorded problem) when it does not.
     */
    private int openMetrics(byte[] sealed) {
        try {
            openedPayloads.add(codec.open(sealed, controlPlaneX25519.getPrivate(), edgeEd25519,
                    SealedEnvelopeCodec.CTX_METRICS_REPORT, edgeId, Instant.now()).toString());
            return 204;
        } catch (Exception e) {
            problems.add("metrics: " + e);
            return 400;
        }
    }

    /** Paths of every request received, in order. */
    List<String> outboundPaths() {
        return outbound.stream().map(Outbound::path).toList();
    }

    /** Key-order independent JSON, so "byte-identical" means "same members and values". */
    private String canonical(JsonNode node) throws Exception {
        Object tree = mapper.treeToValue(node, Object.class);
        return mapper.copy().configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,
                true).writeValueAsString(tree);
    }

    /** Distinct record ids received. */
    Set<String> receivedIds() {
        Set<String> ids = Collections.newSetFromMap(new ConcurrentHashMap<>());
        for (JsonNode r : received) {
            ids.add(r.path("edgeAssessmentId").asText());
        }
        return ids;
    }

    /** Received records with the given id (re-sends included). */
    List<JsonNode> receivedFor(String id) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode r : received) {
            if (id.equals(r.path("edgeAssessmentId").asText())) {
                out.add(r);
            }
        }
        return out;
    }

    /** Base URL of the server. */
    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Stops the server. */
    @Override
    public void close() {
        server.stop(0);
    }
}
