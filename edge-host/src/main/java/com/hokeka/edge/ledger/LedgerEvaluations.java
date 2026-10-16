package com.hokeka.edge.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.EdgeRuleInterpreter;
import com.hokeka.edge.contract.FeatureHygiene;
import com.hokeka.edge.contract.LuhnGuard;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Maps an evaluation onto the Edge Ledger row ({@link LedgerEvaluation}) and onto the decision
 * record v2 (EDGE_DATA_RESIDENCY_CONTRACT.md §4.1) that is written to {@code edge_outbox}.
 *
 * <p>The decision record is built field by field from an allowlist, so no transaction field
 * (amount, currency, country, pan hash, features, reasons) can reach it; the closed schema check in
 * {@link EvaluationRecorder} is the second line of defence.
 */
public final class LedgerEvaluations {

    /** Formats from {@code docs/contracts/common.schema.json}. */
    static final Pattern TXN_REF = Pattern.compile("^[\\x21-\\x7E]{1,128}$");
    static final Pattern MERCHANT_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");
    static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");
    static final Pattern MCC = Pattern.compile("^[0-9]{4}$");
    static final Pattern CHANNEL = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");
    static final Pattern BUNDLE_HASH = Pattern.compile("^sha256:[0-9a-f]{64}$");

    /** Contract {@code latencyMs} upper bound. */
    static final long MAX_LATENCY_MS = 600_000L;

    private LedgerEvaluations() {
    }

    /** Outcome of validating the PSP's {@code txn_id} before anything is evaluated (§2.2, §2.5). */
    public enum TxnRefCheck {
        /** No {@code txn_id} was sent. */
        ABSENT,
        /** Usable as {@code txnRef}. */
        VALID,
        /** PAN-like: 422 {@code TXN_REF_LOOKS_LIKE_PAN}. */
        LOOKS_LIKE_PAN,
        /** Not 1..128 printable ASCII without spaces: 422 {@code TXN_REF_INVALID}. */
        INVALID
    }

    /**
     * The PSP's {@code txn_id} as a string: strings as-is, integral numbers in decimal, anything
     * else (objects, arrays, booleans, fractional numbers) as an invalid marker.
     *
     * @return the reference text, {@code null} when absent, or {@code ""} when unusable
     */
    public static String txnRefText(Object txnId) {
        if (txnId == null) {
            return null;
        }
        if (txnId instanceof CharSequence cs) {
            return cs.toString();
        }
        if (txnId instanceof Integer || txnId instanceof Long || txnId instanceof Short
                || txnId instanceof java.math.BigInteger) {
            return txnId.toString();
        }
        return "";
    }

    /**
     * Classifies a {@code txn_id} value per §2.2 / §2.5.
     *
     * @param txnId the raw request value
     */
    public static TxnRefCheck checkTxnRef(Object txnId) {
        String text = txnRefText(txnId);
        if (text == null) {
            return TxnRefCheck.ABSENT;
        }
        if (LuhnGuard.panLike(text)) {
            return TxnRefCheck.LOOKS_LIKE_PAN;
        }
        return TXN_REF.matcher(text).matches() ? TxnRefCheck.VALID : TxnRefCheck.INVALID;
    }

    /**
     * Maps the engine's action onto the contract's four decisions. {@code FLAG} is an alerting
     * outcome and becomes {@code ALERT}; {@code SUSPEND} stops the payment pending review and
     * becomes {@code HOLD} (the most conservative contract value that is not a final refusal).
     */
    public static String contractDecision(EdgeRuleInterpreter.Action action) {
        if (action == null) {
            return "HOLD";
        }
        return switch (action) {
            case ALLOW -> "ALLOW";
            case ALERT, FLAG -> "ALERT";
            case HOLD, SUSPEND -> "HOLD";
            case BLOCK -> "BLOCK";
        };
    }

    /**
     * Builds the ledger row for one evaluation.
     *
     * @param edgeAssessmentId id minted for this evaluation
     * @param evaluatedAt      evaluation instant
     * @param siteId           Edge Site id
     * @param nodeId           this node's edge id
     * @param enriched         the feature bag that was evaluated (caller + derived features); it is
     *                         sanitised here and never stored as given
     * @param panHash          card hash from the request, or null
     * @param decision         the engine decision
     * @param bundleVersion    active bundle version
     * @param bundleHash       active bundle hash (blank when none)
     * @param evaluator        {@code native} / {@code java} / {@code none}
     * @param failClosedCode   fail-closed gate code, or null
     * @param latencyUs        evaluation latency
     */
    public static LedgerEvaluation fromEvaluation(UUID edgeAssessmentId, Instant evaluatedAt, String siteId,
                                                  String nodeId, Map<String, Object> enriched, String panHash,
                                                  EdgeRuleInterpreter.Decision decision, long bundleVersion,
                                                  String bundleHash, String evaluator, String failClosedCode,
                                                  long latencyUs) {
        Map<String, Object> features = enriched == null ? Map.of() : enriched;
        String txnRef = checkTxnRef(features.get("txn_id")) == TxnRefCheck.VALID
                ? txnRefText(features.get("txn_id")) : null;
        boolean failClosed = failClosedCode != null;
        return new LedgerEvaluation(
                edgeAssessmentId,
                txnRef,
                siteId,
                nodeId,
                evaluatedAt,
                blankToNull(panHash),
                amountCents(features.get("amount_cents")),
                matching(features.get("currency"), CURRENCY, true),
                merchantId(features.get("merchant_id")),
                mcc(features.get("mcc")),
                matching(features.get("country_code"), COUNTRY, true),
                matching(features.get("channel"), CHANNEL, true),
                FeatureHygiene.sanitize(features),
                failClosed ? "HOLD" : contractDecision(decision == null ? null : decision.action()),
                failClosed || decision == null ? 0 : Math.max(0, decision.score()),
                failClosed || decision == null ? List.of() : unique(decision.triggeredRuleIds()),
                failClosed || decision == null ? List.of() : unique(decision.triggeredRuleVersionIds()),
                decision == null || decision.reasons() == null ? List.of() : decision.reasons(),
                Math.max(-1L, bundleVersion),
                bundleVersion >= 0 && bundleHash != null && !bundleHash.isBlank() ? bundleHash : null,
                failClosed ? "none" : evaluator,
                Math.max(0L, latencyUs),
                failClosedCode,
                false,
                List.of(),
                List.of());
    }

    /**
     * The decision record v2 for a ledger row (§4.1): only the allowlisted fields, evaluatedAt
     * truncated to the second, latency in whole milliseconds.
     *
     * @param ev     the ledger row
     * @param mapper mapper used to create the JSON nodes
     */
    public static ObjectNode decisionRecord(LedgerEvaluation ev, ObjectMapper mapper) {
        ObjectNode r = mapper.createObjectNode();
        r.put("v", 2);
        r.put("edgeAssessmentId", ev.edgeAssessmentId().toString());
        if (ev.txnRef() != null) {
            r.put("txnRef", ev.txnRef());
        }
        r.put("edgeNodeId", ev.nodeId());
        r.put("siteId", ev.siteId());
        r.put("evaluatedAt", ev.evaluatedAt().truncatedTo(ChronoUnit.SECONDS).toString());
        r.put("decision", ev.decision());
        r.put("score", ev.score());
        ArrayNode rules = r.putArray("triggeredRuleIds");
        ev.triggeredRuleIds().forEach(rules::add);
        ArrayNode versions = r.putArray("ruleVersionIds");
        ev.ruleVersionIds().forEach(versions::add);
        r.put("bundleVersion", ev.bundleVersion());
        if (ev.bundleVersion() >= 0 && ev.bundleHash() != null) {
            r.put("bundleHash", ev.bundleHash());
        }
        r.put("evaluator", ev.evaluator());
        if (ev.failClosedCode() != null) {
            r.put("failClosedCode", ev.failClosedCode());
        }
        r.put("latencyMs", Math.min(MAX_LATENCY_MS, Math.round(ev.latencyUs() / 1000.0)));
        if (ev.merchantId() != null) {
            r.put("merchantId", ev.merchantId());
        }
        r.put("ctrRequired", ev.ctrRequired());
        r.put("sanctionsHit", ev.sanctionsHit());
        if (ev.sanctionsHit()) {
            ArrayNode ids = r.putArray("watchlistEntryIds");
            new LinkedHashSet<>(watchlistEntryIds(ev)).forEach(ids::add);
        }
        if (!ev.typologyCodes().isEmpty()) {
            ArrayNode codes = r.putArray("typologyCodes");
            new LinkedHashSet<>(ev.typologyCodes()).forEach(codes::add);
        }
        return r;
    }

    /** Public-list entry ids of the row's sanctions matches (never a name). */
    static List<String> watchlistEntryIds(LedgerEvaluation ev) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> hit : ev.sanctionsHits()) {
            Object id = hit.get("entryId");
            if (id != null) {
                ids.add(id.toString());
            }
        }
        return ids;
    }

    /** Distinct values in first-seen order (the schema requires unique rule id lists). */
    static List<Long> unique(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>(new LinkedHashSet<>(values));
        out.removeIf(java.util.Objects::isNull);
        return out;
    }

    /** {@code amount_cents} as a non-negative integer, or null when absent or not integral. */
    static Long amountCents(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            long v = ((Number) value).longValue();
            return v >= 0 ? v : null;
        }
        if (value instanceof java.math.BigInteger big) {
            return big.signum() >= 0 && big.bitLength() < 63 ? big.longValue() : null;
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return d >= 0 && d == Math.rint(d) && d < 9.0e18 ? (long) d : null;
        }
        if (value instanceof CharSequence cs) {
            try {
                long v = Long.parseLong(cs.toString().trim());
                return v >= 0 ? v : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** {@code merchant_id} when it matches the contract format and is not PAN-like. */
    static String merchantId(Object value) {
        String s = value == null ? null : value.toString();
        if (s == null || LuhnGuard.panLike(s) || !MERCHANT_ID.matcher(s).matches()) {
            return null;
        }
        return s;
    }

    /** {@code mcc} as four digits; an integral number is zero-padded (e.g. 742 → "0742"). */
    static String mcc(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            long v = ((Number) value).longValue();
            return v >= 0 && v <= 9999 ? String.format("%04d", v) : null;
        }
        return matching(value, MCC, false);
    }

    /** The value's text when it matches {@code pattern} (optionally after upper-casing), else null. */
    static String matching(Object value, Pattern pattern, boolean upperCase) {
        if (!(value instanceof CharSequence cs)) {
            return null;
        }
        String s = cs.toString().trim();
        if (upperCase) {
            s = s.toUpperCase(java.util.Locale.ROOT);
        }
        return pattern.matcher(s).matches() ? s : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
