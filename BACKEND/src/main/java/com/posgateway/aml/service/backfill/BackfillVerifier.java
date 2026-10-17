package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.Jcs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The cloud side of contract §11 verification, run after the Edge answered COMMIT {@code VERIFIED}
 * (count and sorted-id digest equal on both sides):
 *
 * <ol>
 *   <li><b>Content sample.</b> A deterministic sample of held rows (smallest md5 of the id) is
 *       re-derived from the legacy tables and compared field by field with the Edge's
 *       {@code txn.batchGet} detail: ids, txnRef, evaluation time, decision, score, merchant, amount,
 *       currency, mcc, country, channel, CTR flag, rule and version ids, reasons, the feature bag
 *       (RFC 8785 canonical form), {@code origin = BACKFILL}, {@code evaluator = cloud-legacy} and the
 *       site. A missing row or any difference is a mismatch.</li>
 *   <li><b>Rejection triage.</b> {@code PAST_RETENTION} needs no copy (the Edge would purge it);
 *       {@code ID_CONFLICT} rows whose edgeAssessmentId, and {@code TXN_REF_CONFLICT} rows whose
 *       txnRef, the Edge holds as its <em>own live</em> evaluation (a dual-posted transaction) are
 *       {@code PRESENT_ON_EDGE}. {@code SCHEMA_INVALID}, conflicts with another back-filled row and
 *       anything not confirmed stay for an operator decision.</li>
 * </ol>
 *
 * Nothing compared is logged: mismatches are logged as counts and field names only.
 */
@Component
public class BackfillVerifier {

    private static final Logger log = LoggerFactory.getLogger(BackfillVerifier.class);
    private static final int BATCH_GET_MAX = 200;
    private static final String SYSTEM_RESOLVER = "system:" + TunnelBackfillTransport.JOB;

    /** Fields compared as text (absent on both sides is equal). */
    private static final List<String> TEXT_FIELDS = List.of("edgeAssessmentId", "txnRef", "decision", "merchantId",
            "currency", "mcc", "countryCode", "channel");

    /** Fields compared as JSON values. */
    private static final List<String> VALUE_FIELDS = List.of("score", "amountMinor", "ctrRequired", "triggeredRuleIds",
            "ruleVersionIds", "reasons");

    private final BackfillRunStore store;
    private final LegacyRowReader reader;
    private final BackfillProperties props;

    /** Wires the verifier. */
    public BackfillVerifier(BackfillRunStore store, LegacyRowReader reader, BackfillProperties props) {
        this.store = store;
        this.reader = reader;
        this.props = props;
    }

    /** Outcome of a verification pass. */
    public record Result(int sampleSize, int mismatches, int resolvedRejections) {

        /** True when every sampled row matched. */
        public boolean matched() {
            return mismatches == 0;
        }
    }

    /** Runs the sample check and the rejection triage for a run whose COMMIT verified. */
    public Result verify(BackfillRun run, BackfillTransport transport, LegacyRowMapper.RuleNameIndex rules) {
        List<BackfillRunStore.RowRecord> sample = store.heldSample(run.backfillId(), props.getSampleSize());
        Map<UUID, ObjectNode> expected = expected(run, sample, rules);
        int mismatches = 0;
        Map<String, Integer> fieldCounts = new LinkedHashMap<>();
        List<UUID> ids = new ArrayList<>();
        for (BackfillRunStore.RowRecord r : sample) {
            if (!expected.containsKey(r.edgeAssessmentId())) {
                mismatches++;
                fieldCounts.merge("sourceRowGone", 1, Integer::sum);
                continue;
            }
            ids.add(r.edgeAssessmentId());
        }
        for (int i = 0; i < ids.size(); i += BATCH_GET_MAX) {
            List<UUID> chunk = ids.subList(i, Math.min(ids.size(), i + BATCH_GET_MAX));
            BackfillTransport.BatchGet got = transport.batchGet(run.pspId(), run.siteId(), chunk);
            Map<String, ObjectNode> byId = new HashMap<>();
            got.items().forEach(item -> byId.put(item.path("edgeAssessmentId").asText(), item));
            for (UUID id : chunk) {
                ObjectNode actual = byId.get(id.toString());
                List<String> diff = actual == null ? List.of("missing") : differences(expected.get(id), actual, run.siteId());
                if (!diff.isEmpty()) {
                    mismatches++;
                    diff.forEach(f -> fieldCounts.merge(f, 1, Integer::sum));
                }
            }
        }
        if (mismatches > 0) {
            log.warn("Back-fill {} sample check: {} of {} row(s) differ on the Edge (fields: {})", run.backfillId(),
                    mismatches, sample.size(), fieldCounts.keySet());
        }
        int resolved = triage(run, transport, rules);
        return new Result(sample.size(), mismatches, resolved);
    }

    /**
     * Differences between what the cloud sent and what the Edge returns for one row: field names only.
     */
    static List<String> differences(ObjectNode sent, ObjectNode edge, String siteId) {
        List<String> out = new ArrayList<>();
        for (String f : TEXT_FIELDS) {
            if (!Objects.equals(text(sent, f), text(edge, f))) {
                out.add(f);
            }
        }
        for (String f : VALUE_FIELDS) {
            JsonNode a = sent.get(f);
            JsonNode b = edge.get(f);
            boolean bothAbsent = (a == null || a.isNull()) && (b == null || b.isNull());
            if (!bothAbsent && (a == null || b == null || !Jcs.canonicalize(a).equals(Jcs.canonicalize(b)))) {
                out.add(f);
            }
        }
        if (!sameInstant(text(sent, "evaluatedAt"), text(edge, "evaluatedAt"))) {
            out.add("evaluatedAt");
        }
        JsonNode sentFeatures = sent.path("features");
        JsonNode edgeFeatures = edge.path("features");
        if (!Jcs.canonicalize(sentFeatures).equals(Jcs.canonicalize(edgeFeatures))) {
            out.add("features");
        }
        if (!"BACKFILL".equals(text(edge, "origin"))) {
            out.add("origin");
        }
        if (!"cloud-legacy".equals(text(edge, "evaluator"))) {
            out.add("evaluator");
        }
        if (!siteId.equals(text(edge, "siteId"))) {
            out.add("siteId");
        }
        return out;
    }

    /** Re-derives the rows that were sent for the sample, keyed by edgeAssessmentId. */
    private Map<UUID, ObjectNode> expected(BackfillRun run, List<BackfillRunStore.RowRecord> sample,
                                           LegacyRowMapper.RuleNameIndex rules) {
        Map<UUID, ObjectNode> out = new HashMap<>();
        for (LegacySource source : LegacySource.values()) {
            List<Long> ids = sample.stream().filter(r -> r.source() == source).map(BackfillRunStore.RowRecord::sourceId)
                    .toList();
            for (LegacyRow row : reader.byIds(run.backfillId(), source, ids)) {
                out.put(row.edgeAssessmentId(), LegacyRowMapper.toBackfillRow(row, rules));
            }
        }
        return out;
    }

    /** Resolves rejections that need no copy; returns how many were resolved. */
    private int triage(BackfillRun run, BackfillTransport transport, LegacyRowMapper.RuleNameIndex rules) {
        int resolved = 0;
        int limit = props.getTxnRefChecks();
        List<BackfillRunStore.RowRecord> past = store.unresolved(run.backfillId(), Set.of("PAST_RETENTION"), Integer.MAX_VALUE);
        resolved += store.resolve(run.backfillId(), past.stream().map(BackfillRunStore.RowRecord::legacyTransactionId)
                .toList(), "PAST_RETENTION", "The Edge's retention cutoff is later than the row: purged, not copied",
                SYSTEM_RESOLVER);

        List<BackfillRunStore.RowRecord> idConflicts = store.unresolved(run.backfillId(), Set.of("ID_CONFLICT"), limit);
        for (int i = 0; i < idConflicts.size(); i += BATCH_GET_MAX) {
            List<BackfillRunStore.RowRecord> chunk = idConflicts.subList(i, Math.min(idConflicts.size(), i + BATCH_GET_MAX));
            BackfillTransport.BatchGet got = transport.batchGet(run.pspId(), run.siteId(),
                    chunk.stream().map(BackfillRunStore.RowRecord::edgeAssessmentId).distinct().toList());
            Set<String> live = new java.util.HashSet<>();
            got.items().stream().filter(BackfillVerifier::liveEvaluation)
                    .forEach(item -> live.add(item.path("edgeAssessmentId").asText()));
            List<String> present = chunk.stream().filter(r -> live.contains(r.edgeAssessmentId().toString()))
                    .map(BackfillRunStore.RowRecord::legacyTransactionId).toList();
            resolved += store.resolve(run.backfillId(), present, "PRESENT_ON_EDGE",
                    "The Edge already holds this edgeAssessmentId as its own live evaluation (authoritative)",
                    SYSTEM_RESOLVER);
        }

        List<BackfillRunStore.RowRecord> refConflicts = store.unresolved(run.backfillId(), Set.of("TXN_REF_CONFLICT"), limit);
        for (LegacySource source : LegacySource.values()) {
            List<BackfillRunStore.RowRecord> ofSource = refConflicts.stream().filter(r -> r.source() == source).toList();
            if (ofSource.isEmpty()) {
                continue;
            }
            Map<Long, String> legacyIdBySource = new HashMap<>();
            ofSource.forEach(r -> legacyIdBySource.put(r.sourceId(), r.legacyTransactionId()));
            List<String> present = new ArrayList<>();
            for (LegacyRow row : reader.byIds(run.backfillId(), source, legacyIdBySource.keySet())) {
                String txnRef = LegacyRowMapper.txnRef(row);
                if (txnRef != null && transport.getByTxnRef(run.pspId(), run.siteId(), txnRef)
                        .filter(BackfillVerifier::liveEvaluation).isPresent()) {
                    present.add(legacyIdBySource.get(row.sourceId()));
                }
            }
            resolved += store.resolve(run.backfillId(), present, "PRESENT_ON_EDGE",
                    "The Edge already holds its own live evaluation under this PSP txnRef", SYSTEM_RESOLVER);
        }
        return resolved;
    }

    /**
     * True for an evaluation the Edge made itself ({@code origin = LIVE}, e.g. a dual-posted
     * transaction). A conflicting {@code BACKFILL} row is another legacy row (a reused PSP reference,
     * or an older copy) and is left for an operator decision.
     */
    private static boolean liveEvaluation(JsonNode item) {
        return "LIVE".equals(item.path("origin").asText());
    }

    /** A text member, or null. */
    private static String text(JsonNode n, String name) {
        JsonNode v = n.get(name);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Both null, or both parse to the same instant. */
    private static boolean sameInstant(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        try {
            return Instant.parse(a).equals(Instant.parse(b));
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
