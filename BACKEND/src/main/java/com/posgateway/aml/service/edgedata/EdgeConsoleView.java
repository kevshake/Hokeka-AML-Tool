package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.TunnelOp;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.model.TransactionReference;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds the Console shapes of the tunnel-backed endpoints (contract §7.14), per response, never
 * stored:
 * <ul>
 *   <li>items keep the field names the Console already reads for a transaction ({@code id},
 *       {@code txnTs}, {@code amountCents}, {@code merchantId}, {@code currency}, {@code decision},
 *       {@code score}) next to the contract names, plus {@code source};</li>
 *   <li>lists are wrapped as {@code {items, nextCursor, incomplete, unavailableSites, source}}
 *       (and {@code missing} for batchGet).</li>
 * </ul>
 * Legacy rows are projected onto the same item shape and never carry the card hash, ISO message,
 * EMV tags, IP, device, e-mail or account reference that the legacy table holds.
 */
public final class EdgeConsoleView {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final DateTimeFormatter LEGACY_TS = DateTimeFormatter.ISO_INSTANT;

    /** Static helpers only. */
    private EdgeConsoleView() {
    }

    /** A Console item from an Edge summary or detail item: contract fields, Console aliases, source. */
    public static ObjectNode edgeItem(JsonNode item) {
        ObjectNode copy = item.deepCopy();
        if (copy.hasNonNull("edgeAssessmentId") && !copy.has("id")) {
            copy.put("id", copy.get("edgeAssessmentId").asText());
        }
        if (copy.hasNonNull("evaluatedAt")) {
            copy.put("txnTs", copy.get("evaluatedAt").asText());
        }
        if (copy.hasNonNull("amountMinor")) {
            copy.set("amountCents", copy.get("amountMinor"));
        }
        copy.put("source", TransactionSource.EDGE.name());
        return copy;
    }

    /** A Console {@code txn.related} item: the link plus its transaction as a Console item. */
    public static ObjectNode relatedItem(JsonNode item) {
        ObjectNode copy = item.deepCopy();
        copy.set("transaction", edgeItem(item.path("transaction")));
        copy.put("source", TransactionSource.EDGE.name());
        return copy;
    }

    /** The §7.14 list envelope of an Edge answer, items mapped for {@code op}. */
    public static ObjectNode envelope(EdgePull pull, TunnelOp op) {
        ObjectNode n = F.objectNode();
        ArrayNode items = n.putArray("items");
        for (ObjectNode item : pull.items()) {
            items.add(op == TunnelOp.TXN_RELATED ? relatedItem(item) : edgeItem(item));
        }
        if (pull.nextCursor() != null) {
            n.put("nextCursor", pull.nextCursor());
        } else {
            n.putNull("nextCursor");
        }
        n.put("incomplete", pull.incomplete());
        if (pull.incompleteReason() != null) {
            n.put("incompleteReason", pull.incompleteReason());
        }
        ArrayNode u = n.putArray("unavailableSites");
        pull.unavailableSites().forEach(s -> u.add(s.toJson()));
        if (op == TunnelOp.TXN_BATCH_GET) {
            ArrayNode m = n.putArray("missing");
            pull.missing().forEach(m::add);
        }
        n.put("source", TransactionSource.EDGE.name());
        return n;
    }

    /** The same envelope around legacy items (never incomplete: the rows are local). */
    public static ObjectNode legacyEnvelope(List<ObjectNode> items, String nextCursor, List<String> missing,
                                            boolean withMissing) {
        ObjectNode n = F.objectNode();
        ArrayNode arr = n.putArray("items");
        items.forEach(arr::add);
        if (nextCursor != null) {
            n.put("nextCursor", nextCursor);
        } else {
            n.putNull("nextCursor");
        }
        n.put("incomplete", false);
        n.putArray("unavailableSites");
        if (withMissing) {
            ArrayNode m = n.putArray("missing");
            missing.forEach(m::add);
        }
        n.put("source", TransactionSource.CLOUD_LEGACY.name());
        return n;
    }

    /**
     * A legacy cloud-ingest row as a Console item flagged {@code CLOUD_LEGACY}. Only the fields the
     * transaction list and detail show are projected; card hash, raw message, EMV, IP, device,
     * e-mail and account references are left out.
     */
    public static ObjectNode legacyItem(TransactionEntity t, TransactionReference reference) {
        ObjectNode n = F.objectNode();
        n.put("id", t.getTxnId());
        n.put("txnId", t.getTxnId());
        if (reference != null && reference.edgeAssessmentId() != null) {
            n.put("edgeAssessmentId", reference.edgeAssessmentId().toString());
        }
        String txnRef = reference != null && reference.txnRef() != null ? reference.txnRef()
                : TransactionReference.safeTxnRef(t.getClientReference());
        putText(n, "txnRef", txnRef);
        putText(n, "merchantId", t.getMerchantId());
        if (t.getPspId() != null) {
            n.put("pspId", t.getPspId());
        }
        putText(n, "terminalId", t.getTerminalId());
        if (t.getAmountCents() != null) {
            n.put("amountCents", t.getAmountCents());
            n.put("amountMinor", t.getAmountCents());
        }
        putText(n, "currency", t.getCurrency());
        if (t.getTxnTs() != null) {
            String ts = LEGACY_TS.format(t.getTxnTs().toInstant(ZoneOffset.UTC));
            n.put("txnTs", ts);
            n.put("evaluatedAt", ts);
        }
        putText(n, "decision", t.getDecision());
        putText(n, "riskLevel", t.getRiskLevel());
        if (t.getKrs() != null) {
            n.put("krs", t.getKrs());
        }
        if (t.getTrs() != null) {
            n.put("trs", t.getTrs());
        }
        if (t.getCra() != null) {
            n.put("cra", t.getCra());
        }
        putText(n, "countryCode", t.getMerchantCountry());
        putText(n, "channel", t.getChannelType());
        n.put("ctrRequired", t.isCtrRequired());
        n.put("source", TransactionSource.CLOUD_LEGACY.name());
        return n;
    }

    /**
     * The decision-record fields the cloud holds for a transaction (class D, contract §2.2), shown
     * next to the Edge detail. Never transaction detail.
     */
    public static ObjectNode decisionRecord(Assessment a) {
        ObjectNode n = F.objectNode();
        n.put("assessmentId", a.getId().toString());
        if (a.getEdgeAssessmentId() != null) {
            n.put("edgeAssessmentId", a.getEdgeAssessmentId().toString());
        }
        putText(n, "txnRef", a.getTxnRef());
        putText(n, "siteId", a.getSiteId());
        putText(n, "edgeNodeId", a.getEdgeNodeId());
        if (a.getEvaluatedAt() != null) {
            n.put("evaluatedAt", a.getEvaluatedAt().toString());
        }
        putText(n, "decision", a.getDecision());
        if (a.getScore() != null) {
            n.put("score", a.getScore());
        }
        putText(n, "merchantId", a.getMerchantId());
        if (a.getBundleVersion() != null) {
            n.put("bundleVersion", a.getBundleVersion());
        }
        putText(n, "evaluator", a.getEvaluator());
        putText(n, "failClosedCode", a.getFailClosedCode());
        if (a.getCtrRequired() != null) {
            n.put("ctrRequired", a.getCtrRequired());
        }
        if (a.getSanctionsHit() != null) {
            n.put("sanctionsHit", a.getSanctionsHit());
        }
        putLongs(n, "triggeredRuleIds", a.getTriggeredRuleIds());
        putLongs(n, "ruleVersionIds", a.getRuleVersionIds());
        putStrings(n, "watchlistEntryIds", a.getWatchlistEntryIds());
        putStrings(n, "typologyCodes", a.getTypologyCodes());
        if (a.getCreatedAt() != null) {
            n.put("recordedAt", a.getCreatedAt().toString());
        }
        return n;
    }

    /** Puts a non-null text field. */
    private static void putText(ObjectNode n, String field, String value) {
        if (value != null) {
            n.put(field, value);
        }
    }

    /** Puts a non-null list of ids. */
    private static void putLongs(ObjectNode n, String field, List<Long> values) {
        if (values != null) {
            ArrayNode a = n.putArray(field);
            values.forEach(a::add);
        }
    }

    /** Puts a non-null list of strings. */
    private static void putStrings(ObjectNode n, String field, List<String> values) {
        if (values != null) {
            ArrayNode a = n.putArray(field);
            values.forEach(a::add);
        }
    }
}
