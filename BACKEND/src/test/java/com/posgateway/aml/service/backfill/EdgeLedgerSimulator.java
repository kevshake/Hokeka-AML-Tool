package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.Jcs;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An in-memory Edge Ledger with the {@code ledger.backfill} semantics of the edge-host
 * {@code BackfillImporter} (contract §11): idempotent BEGIN, ordered BATCH with ACCEPTED /
 * DUPLICATE / {@code PAST_RETENTION} / {@code ID_CONFLICT} / {@code TXN_REF_CONFLICT}, COMMIT over the
 * held legacy ids, ABORT; plus {@code txn.batchGet}, {@code txn.get} by txnRef and
 * {@code ledger.health}. Faults can be queued per call to exercise offline, busy, timeout (with the
 * request applied but the answer lost) and forgotten rows. The real importer is exercised against a
 * real ledger by the edge-host {@code CloudBackfillFixturesIntegrationTest}.
 */
public class EdgeLedgerSimulator implements BackfillTransport {

    /** A fault for the next call. */
    public enum Fault {
        OFFLINE, BUSY, TIMEOUT_AFTER_APPLY, FRAME_TOO_LARGE, FORGET_ONE_HELD_ROW_AT_COMMIT
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    final long pspId;
    final String siteId;
    volatile boolean backfillAllowed = true;
    volatile String healthStatus = "OK";
    volatile Instant localCutoff;
    final Deque<Fault> faults = new ArrayDeque<>();
    /** Every params object received (for the no-card-secret assertions). */
    final List<String> received = new CopyOnWriteArrayList<>();
    /** Stored evaluations by edgeAssessmentId: the row as sent plus origin. */
    final Map<String, ObjectNode> rows = new ConcurrentHashMap<>();
    final Map<String, String> txnRefs = new ConcurrentHashMap<>();
    final Map<String, Run> runs = new ConcurrentHashMap<>();

    /** One Edge-side run. */
    static final class Run {
        String state = "OPEN";
        long nextBatchSeq;
        String begin;
        final Map<String, String> held = new LinkedHashMap<>();
    }

    /** A simulated site ledger of {@code pspId}. */
    public EdgeLedgerSimulator(long pspId, String siteId) {
        this.pspId = pspId;
        this.siteId = siteId;
    }

    /** Pre-loads an Edge-native evaluation (origin LIVE), e.g. a dual-posted transaction. */
    public void preloadLive(String edgeAssessmentId, String txnRef) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("edgeAssessmentId", edgeAssessmentId);
        if (txnRef != null) {
            n.put("txnRef", txnRef);
            txnRefs.put(txnRef, edgeAssessmentId);
        }
        n.put("evaluatedAt", "2026-01-01T00:00:00.000Z");
        n.put("decision", "ALLOW");
        n.put("score", 0);
        n.putArray("triggeredRuleIds");
        n.putArray("ruleVersionIds");
        n.putArray("reasons");
        n.put("ctrRequired", false);
        n.putObject("features");
        n.put("origin", "LIVE");
        rows.put(edgeAssessmentId, n);
    }

    /** Queues faults for the next calls. */
    public synchronized void fault(Fault... next) {
        faults.addAll(List.of(next));
    }

    /** Next fault, or null. */
    private synchronized Fault nextFault() {
        return faults.pollFirst();
    }

    @Override
    public synchronized ObjectNode backfill(long psp, String site, ObjectNode params) {
        check(psp, site);
        received.add(params.toString());
        Fault f = nextFault();
        if (f == Fault.OFFLINE) {
            throw new EdgeDataException(TunnelError.offline(site, Instant.parse("2026-10-17T00:00:00Z")));
        }
        if (f == Fault.BUSY) {
            throw new EdgeDataException(TunnelError.busy(site, 16, 1_500));
        }
        if (f == Fault.FRAME_TOO_LARGE) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.FRAME_TOO_LARGE, "frame too large"));
        }
        if (!backfillAllowed) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.BACKFILL_NOT_ALLOWED, "not allowed"));
        }
        ObjectNode ack = switch (params.get("phase").asText()) {
            case "BEGIN" -> begin(params);
            case "BATCH" -> batch(params);
            case "COMMIT" -> commit(params, f == Fault.FORGET_ONE_HELD_ROW_AT_COMMIT);
            case "ABORT" -> abort(params);
            default -> throw new EdgeDataException(TunnelError.invalidParams("/phase"));
        };
        if (f == Fault.TIMEOUT_AFTER_APPLY) {
            throw new EdgeDataException(TunnelError.timeout(site));
        }
        return ack;
    }

    /** BEGIN: opens or re-confirms the run; different parameters are BACKFILL_REJECTED. */
    private ObjectNode begin(ObjectNode p) {
        String id = p.get("backfillId").asText();
        ObjectNode key = p.deepCopy();
        Run run = runs.computeIfAbsent(id, k -> {
            Run r = new Run();
            r.begin = Jcs.canonicalize(key);
            return r;
        });
        if (!run.begin.equals(Jcs.canonicalize(key))) {
            throw rejected("backfillId already used with different parameters");
        }
        return item(id, "BEGIN", run.state);
    }

    /** BATCH: per row ACCEPTED / DUPLICATE / rejection, as BackfillImporter decides. */
    private ObjectNode batch(ObjectNode p) {
        String id = p.get("backfillId").asText();
        Run run = runOrReject(id);
        if (!"OPEN".equals(run.state)) {
            throw rejected("backfill run is " + run.state);
        }
        long seq = p.get("batchSeq").asLong();
        if (seq > run.nextBatchSeq) {
            throw rejected("batchSeq out of order");
        }
        Instant cutoff = Instant.parse(retentionCutoff(run));
        if (localCutoff != null && localCutoff.isAfter(cutoff)) {
            cutoff = localCutoff;
        }
        int accepted = 0;
        int duplicates = 0;
        ArrayNode rejectedRows = MAPPER.createArrayNode();
        for (JsonNode row : p.get("rows")) {
            String legacy = row.get("legacyTransactionId").asText();
            String eid = row.get("edgeAssessmentId").asText();
            String code;
            ObjectNode stored = rows.get(eid);
            String txnRef = row.path("txnRef").asText(null);
            if (Instant.parse(row.get("evaluatedAt").asText()).isBefore(cutoff)) {
                code = "PAST_RETENTION";
            } else if (stored != null) {
                code = "BACKFILL".equals(stored.path("origin").asText()) && sameContent(stored, row) ? "DUPLICATE"
                        : "ID_CONFLICT";
            } else if (txnRef != null && txnRefs.containsKey(txnRef) && !txnRefs.get(txnRef).equals(eid)) {
                code = "TXN_REF_CONFLICT";
            } else {
                ObjectNode copy = row.deepCopy();
                copy.put("origin", "BACKFILL");
                rows.put(eid, copy);
                if (txnRef != null) {
                    txnRefs.put(txnRef, eid);
                }
                code = "ACCEPTED";
            }
            switch (code) {
                case "ACCEPTED" -> {
                    accepted++;
                    run.held.put(legacy, eid);
                }
                case "DUPLICATE" -> {
                    duplicates++;
                    run.held.put(legacy, eid);
                }
                default -> rejectedRows.addObject().put("legacyTransactionId", legacy).put("code", code);
            }
        }
        run.nextBatchSeq = Math.max(run.nextBatchSeq, seq + 1);
        ObjectNode ack = item(id, "BATCH", "OPEN");
        ack.put("batchSeq", seq);
        ack.put("accepted", accepted);
        ack.put("duplicates", duplicates);
        ack.set("rejected", rejectedRows);
        return ack;
    }

    /** COMMIT: count and digest over the held legacy ids. */
    private ObjectNode commit(ObjectNode p, boolean forgetOne) {
        String id = p.get("backfillId").asText();
        Run run = runOrReject(id);
        if ("ABORTED".equals(run.state)) {
            throw rejected("backfill run was aborted");
        }
        if (forgetOne && !run.held.isEmpty()) {
            run.held.remove(run.held.keySet().iterator().next());
        }
        TreeSet<String> ids = new TreeSet<>(run.held.keySet());
        String digest = Jcs.sha256Hex(String.join("\n", ids).getBytes(StandardCharsets.UTF_8));
        boolean ok = ids.size() == p.get("expectedRows").asLong() && digest.equals(p.get("expectedIdDigest").asText());
        if (ok) {
            run.state = "VERIFIED";
        }
        ObjectNode ack = item(id, "COMMIT", ok ? "VERIFIED" : "VERIFY_MISMATCH");
        ack.put("rowsTotal", ids.size());
        ack.put("idDigest", digest);
        return ack;
    }

    /** ABORT: closes an open run; a verified run cannot be aborted. */
    private ObjectNode abort(ObjectNode p) {
        String id = p.get("backfillId").asText();
        Run run = runOrReject(id);
        if ("VERIFIED".equals(run.state)) {
            throw rejected("backfill run is already verified");
        }
        run.state = "ABORTED";
        return item(id, "ABORT", "ABORTED");
    }

    @Override
    public ObjectNode health(long psp, String site) {
        check(psp, site);
        Fault f = peekOffline();
        if (f == Fault.OFFLINE) {
            throw new EdgeDataException(TunnelError.offline(site, Instant.parse("2026-10-17T00:00:00Z")));
        }
        ObjectNode n = MAPPER.createObjectNode();
        n.put("siteId", site);
        n.put("status", healthStatus);
        return n;
    }

    /** Health fails only when an OFFLINE fault is queued first (consumed). */
    private synchronized Fault peekOffline() {
        if (faults.peekFirst() == Fault.OFFLINE) {
            return faults.pollFirst();
        }
        return null;
    }

    @Override
    public BatchGet batchGet(long psp, String site, List<UUID> ids) {
        check(psp, site);
        List<ObjectNode> items = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (UUID id : ids) {
            ObjectNode r = rows.get(id.toString());
            if (r == null) {
                missing.add(id.toString());
            } else {
                items.add(detail(r));
            }
        }
        return new BatchGet(items, missing);
    }

    @Override
    public Optional<ObjectNode> getByTxnRef(long psp, String site, String txnRef) {
        check(psp, site);
        String id = txnRefs.get(txnRef);
        return id == null ? Optional.empty() : Optional.of(detail(rows.get(id)));
    }

    /** The detail item of a stored row (no panHash, legacyTransactionId; Edge-added fields set). */
    private ObjectNode detail(ObjectNode stored) {
        ObjectNode d = stored.deepCopy();
        d.remove("panHash");
        d.remove("legacyTransactionId");
        d.remove("sanctionsHits");
        d.put("siteId", siteId);
        d.put("nodeId", siteId + "-1");
        d.put("bundleVersion", -1);
        d.put("evaluator", "BACKFILL".equals(stored.path("origin").asText()) ? "cloud-legacy" : "java");
        d.put("sanctionsHit", false);
        d.put("legalHold", false);
        return d;
    }

    /** Held legacy ids of a run. */
    public List<String> held(String backfillId) {
        Run r = runs.get(backfillId);
        return r == null ? List.of() : List.copyOf(r.held.keySet());
    }

    /** The Edge-side state of a run, or null. */
    public String state(String backfillId) {
        Run r = runs.get(backfillId);
        return r == null ? null : r.state;
    }

    /** Same content as BackfillImporter.sameAsStored compares (everything but origin). */
    private static boolean sameContent(ObjectNode stored, JsonNode row) {
        ObjectNode a = stored.deepCopy();
        a.remove("origin");
        return Jcs.canonicalize(a).equals(Jcs.canonicalize(row));
    }

    /** The run's BEGIN retentionCutoff. */
    private static String retentionCutoff(Run run) {
        try {
            return MAPPER.readTree(run.begin).get("retentionCutoff").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Tenant and site checks the real tunnel makes. */
    private void check(long psp, String site) {
        if (psp != pspId) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.TENANT_MISMATCH, "tenant mismatch"));
        }
        if (!siteId.equals(site)) {
            throw new EdgeDataException(TunnelError.offline(site, Instant.EPOCH));
        }
    }

    /** The run or BACKFILL_REJECTED. */
    private Run runOrReject(String id) {
        Run run = runs.get(id);
        if (run == null) {
            throw rejected("unknown backfillId");
        }
        return run;
    }

    /** A BACKFILL_REJECTED error. */
    private static EdgeDataException rejected(String message) {
        return new EdgeDataException(TunnelError.of(TunnelErrorCode.BACKFILL_REJECTED, message));
    }

    /** {@code {backfillId, phase, state}}. */
    private static ObjectNode item(String id, String phase, String state) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("backfillId", id);
        n.put("phase", phase);
        n.put("state", state);
        return n;
    }
}
