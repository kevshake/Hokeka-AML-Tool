package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.contract.FeatureHygiene;

import java.util.Map;
import java.util.Set;

/**
 * Builds tunnel response items from ledger rows (EDGE_DATA_RESIDENCY_CONTRACT.md §7.6,
 * {@code common.schema.json#/$defs/transactionSummary} and {@code transactionDetail}). Items never
 * carry {@code pan_hash} or a card secret: the rows they are built from do not contain
 * {@code pan_hash}, and the stored feature bag is passed through {@link FeatureHygiene} once more on
 * the way out. Every item is still validated against its item schema before it is sent.
 */
public final class TunnelItems {

    /** Columns an export projection may add (export.stream.params {@code columns}). */
    public static final Set<String> EXPORT_COLUMNS = Set.of("txnRef", "nodeId", "score", "merchantId", "mcc",
            "amountMinor", "currency", "countryCode", "channel", "triggeredRuleIds", "ctrRequired", "sanctionsHit",
            "origin");

    private final ObjectMapper mapper;

    /** @param mapper JSON mapper */
    public TunnelItems(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Summary item (txn.search, txn.related, export.stream without projection). */
    public ObjectNode summary(LedgerReads.Row r) {
        return summary(r, null);
    }

    /**
     * Summary item, optionally projected: {@code edgeAssessmentId}, {@code evaluatedAt},
     * {@code siteId} and {@code decision} are always present; with a non-null {@code columns} only
     * those optional members are added.
     */
    public ObjectNode summary(LedgerReads.Row r, Set<String> columns) {
        ObjectNode n = mapper.createObjectNode();
        n.put("edgeAssessmentId", r.id().toString());
        if (want(columns, "txnRef") && r.txnRef() != null) {
            n.put("txnRef", r.txnRef());
        }
        n.put("siteId", r.siteId());
        if (want(columns, "nodeId")) {
            n.put("nodeId", r.nodeId());
        }
        n.put("evaluatedAt", TunnelTime.millis(r.evaluatedAt()));
        n.put("decision", r.decision());
        if (want(columns, "score")) {
            n.put("score", r.score());
        }
        putOptional(n, columns, "merchantId", r.merchantId());
        putOptional(n, columns, "mcc", r.mcc());
        if (want(columns, "amountMinor") && r.amountMinor() != null) {
            n.put("amountMinor", r.amountMinor());
        }
        putOptional(n, columns, "currency", r.currency());
        putOptional(n, columns, "countryCode", r.countryCode());
        putOptional(n, columns, "channel", r.channel());
        if (want(columns, "triggeredRuleIds")) {
            ArrayNode a = n.putArray("triggeredRuleIds");
            r.triggeredRuleIds().forEach(a::add);
        }
        if (want(columns, "ctrRequired")) {
            n.put("ctrRequired", r.ctrRequired());
        }
        if (want(columns, "sanctionsHit")) {
            n.put("sanctionsHit", r.sanctionsHit());
        }
        if (want(columns, "origin")) {
            n.put("origin", r.origin());
        }
        return n;
    }

    /** Detail item (txn.get, txn.batchGet, filing.extract). */
    public ObjectNode detail(LedgerReads.Row r) {
        ObjectNode n = summary(r, null);
        ArrayNode versions = n.putArray("ruleVersionIds");
        r.ruleVersionIds().forEach(versions::add);
        ArrayNode reasons = n.putArray("reasons");
        r.reasons().forEach(reasons::add);
        n.put("bundleVersion", r.bundleVersion());
        if (r.bundleHash() != null) {
            n.put("bundleHash", r.bundleHash());
        }
        n.put("evaluator", r.evaluator());
        n.put("latencyUs", r.latencyUs());
        if (r.failClosedCode() != null) {
            n.put("failClosedCode", r.failClosedCode());
        }
        n.set("sanctionsHits", json(r.sanctionsHitsJson(), true));
        ArrayNode typology = n.putArray("typologyCodes");
        r.typologyCodes().forEach(typology::add);
        n.set("features", features(r.featuresJson()));
        n.put("legalHold", Boolean.TRUE.equals(r.legalHold()));
        return n;
    }

    /** The stored feature bag, re-sanitised (§2.4) so a defect upstream cannot leak through here. */
    @SuppressWarnings("unchecked")
    private JsonNode features(String json) {
        JsonNode raw = json(json, false);
        if (!raw.isObject()) {
            return mapper.createObjectNode();
        }
        Map<String, Object> bag = mapper.convertValue(raw, Map.class);
        return mapper.valueToTree(FeatureHygiene.sanitize(bag));
    }

    /** Parses a jsonb text column; an empty array/object when absent or unreadable. */
    private JsonNode json(String text, boolean array) {
        if (text == null) {
            return array ? mapper.createArrayNode() : mapper.createObjectNode();
        }
        try {
            JsonNode n = mapper.readTree(text);
            if (array ? n.isArray() : n.isObject()) {
                return n;
            }
        } catch (java.io.IOException ignored) {
            // A malformed stored value is replaced by an empty one; the item still validates.
        }
        return array ? mapper.createArrayNode() : mapper.createObjectNode();
    }

    /** Whether an optional member is in the projection (null projection = all). */
    private static boolean want(Set<String> columns, String name) {
        return columns == null || columns.contains(name);
    }

    /** Adds an optional text member when projected and non-null. */
    private static void putOptional(ObjectNode n, Set<String> columns, String name, String value) {
        if (value != null && want(columns, name)) {
            n.put(name, value);
        }
    }
}
