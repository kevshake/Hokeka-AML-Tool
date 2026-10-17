package com.hokeka.edge.sanctions;

import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.EdgeRuleInterpreter;
import com.hokeka.edge.residency.EdgeConfigDocument;
import com.hokeka.edge.residency.EdgeConfigStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Local sanctions screening and merchant blocklist enforcement on {@code /edge/evaluate}
 * (contract section 6.5). Nothing here leaves the Edge: the counterparty names are screened against
 * the in-memory list, and only public watchlist entry ids reach the decision record.
 *
 * <ul>
 *   <li>Screened fields: {@code counterparty_name} and {@code cardholder_name} (matchedOn
 *       {@code COUNTERPARTY_NAME}), {@code beneficiary_name} ({@code BENEFICIARY_NAME}).</li>
 *   <li>A sanctions-list match (not PEP/RCA) at or above {@value #CONFIRMED_THRESHOLD} is a confirmed
 *       hit and the decision becomes {@code BLOCK}; a match between the similarity threshold and that
 *       is a potential hit and the decision becomes at least {@code HOLD}. Both set
 *       {@code sanctionsHit} with the entry ids.</li>
 *   <li>A PEP/RCA match never is a sanctions hit and never changes the decision; it only adds a local
 *       reason (enhanced due diligence).</li>
 *   <li>A {@code merchant_id} on the config's merchant blocklist makes the decision {@code BLOCK}.</li>
 *   <li>When the config names a published list but none is loaded yet, an evaluation carrying a name
 *       to screen is held ({@code hokeka.sanctions.hold-when-list-missing}, default true).</li>
 *   <li>A fail-closed evaluation keeps its {@code HOLD} (the schema forbids anything else); hits are
 *       still recorded.</li>
 * </ul>
 * Screening only reads the swapped in-memory list and config; it never blocks on I/O.
 */
public class SanctionsScreener {

    /** aml-microservice FLAGGED threshold and the cloud's confirmed-match threshold. */
    public static final double CONFIRMED_THRESHOLD = 0.95;
    /** Longest screened value (longer values are screened on their first 512 characters). */
    static final int MAX_SCREENED_LENGTH = 512;
    /** Longest {@code matchedValue} kept (schema maxLength). */
    static final int MAX_MATCHED_VALUE = 256;

    /** Feature key → {@code matchedOn}. */
    static final Map<String, String> FIELDS = Map.of(
            "counterparty_name", "COUNTERPARTY_NAME",
            "cardholder_name", "COUNTERPARTY_NAME",
            "beneficiary_name", "BENEFICIARY_NAME");
    private static final List<String> FIELD_ORDER = List.of("counterparty_name", "cardholder_name", "beneficiary_name");

    /**
     * What screening found.
     *
     * @param hits       {@code sanctionsHitDetail} objects (sanctions-list matches only)
     * @param escalation the least severe action the decision must reach, or null
     * @param reasons    local reasons to add (class T, returned to the PSP, kept on the Edge)
     */
    public record Result(List<Map<String, Object>> hits, EdgeRuleInterpreter.Action escalation, List<String> reasons) {
        /** Nothing found. */
        public static final Result NONE = new Result(List.of(), null, List.of());

        /** Copies the lists. */
        public Result {
            hits = List.copyOf(hits);
            reasons = List.copyOf(reasons);
        }
    }

    private final SanctionsListStore lists;
    private final EdgeConfigStore config;
    private final boolean holdWhenListMissing;

    /**
     * @param lists               the list in force
     * @param config              the Edge config in force (blocklist, which list is published)
     * @param holdWhenListMissing hold evaluations with names while a published list is not loaded
     */
    public SanctionsScreener(SanctionsListStore lists, EdgeConfigStore config, boolean holdWhenListMissing) {
        this.lists = lists;
        this.config = config;
        this.holdWhenListMissing = holdWhenListMissing;
    }

    /** Screens one evaluation's feature bag. */
    public Result screen(Map<String, Object> features) {
        if (features == null) {
            return Result.NONE;
        }
        Optional<EdgeConfigDocument> doc = config.current();
        List<String> reasons = new ArrayList<>();
        EdgeRuleInterpreter.Action escalation = null;

        Object merchant = features.get("merchant_id");
        if (merchant != null && doc.isPresent() && doc.get().merchantBlocked(merchant.toString())) {
            escalation = EdgeRuleInterpreter.Action.BLOCK;
            reasons.add("merchant is on the PSP's Edge merchant blocklist");
        }

        Map<String, String> names = new LinkedHashMap<>();
        for (String key : FIELD_ORDER) {
            if (features.get(key) instanceof CharSequence cs && !cs.toString().isBlank()) {
                String value = cs.toString();
                names.put(key, value.length() > MAX_SCREENED_LENGTH ? value.substring(0, MAX_SCREENED_LENGTH) : value);
            }
        }
        if (names.isEmpty()) {
            return escalation == null ? Result.NONE : new Result(List.of(), escalation, reasons);
        }
        Optional<SanctionsListStore.Loaded> list = lists.current();
        if (list.isEmpty()) {
            if (holdWhenListMissing && doc.isPresent() && doc.get().sanctionsListPublished()) {
                escalation = max(escalation, EdgeRuleInterpreter.Action.HOLD);
                reasons.add("sanctions list not yet loaded on this node: held until it is (fail-closed)");
            }
            return new Result(List.of(), escalation, reasons);
        }
        Map<String, Map<String, Object>> hits = new LinkedHashMap<>();
        for (Map.Entry<String, String> name : names.entrySet()) {
            String matchedOn = FIELDS.get(name.getKey());
            for (SanctionsIndex.Match match : list.get().index().screen(name.getValue())) {
                String where = matchedOn.toLowerCase(Locale.ROOT).replace('_', ' ');
                if (match.entry().pep()) {
                    reasons.add(String.format(Locale.ROOT, "%s screening: %s matches %s list entry %s (score %.4f): "
                            + "enhanced due diligence, not a sanctions hit", "PEP", where,
                            match.entry().pepLevel(), match.entry().id(), match.score()));
                    continue;
                }
                boolean confirmed = match.score() >= CONFIRMED_THRESHOLD;
                escalation = max(escalation, confirmed ? EdgeRuleInterpreter.Action.BLOCK : EdgeRuleInterpreter.Action.HOLD);
                reasons.add(String.format(Locale.ROOT, "sanctions screening: %s matches watchlist entry %s (score %.4f): %s",
                        where, match.entry().id(), match.score(), confirmed ? "confirmed hit, blocked" : "potential hit, held for review"));
                String key = match.entry().id() + "|" + matchedOn;
                Map<String, Object> existing = hits.get(key);
                if (existing == null || (double) existing.get("score") < match.score()) {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("entryId", match.entry().id());
                    hit.put("score", match.score());
                    hit.put("matchedOn", matchedOn);
                    String value = name.getValue();
                    hit.put("matchedValue", value.length() > MAX_MATCHED_VALUE ? value.substring(0, MAX_MATCHED_VALUE) : value);
                    hit.put("listVersion", truncate(list.get().listVersion(), 64));
                    hits.put(key, hit);
                }
            }
        }
        List<Map<String, Object>> ordered = new ArrayList<>(hits.values());
        ordered.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
        return new Result(ordered, escalation, reasons);
    }

    /**
     * The outcome after screening: the decision escalated to at least {@code result.escalation()} with
     * the reasons appended. A fail-closed outcome is returned unchanged (it is already a HOLD and must
     * stay one).
     */
    public static EdgeEngine.Outcome apply(EdgeEngine.Outcome outcome, Result result) {
        if (result == null || (result.escalation() == null && result.reasons().isEmpty())
                || outcome.failClosedCode() != null) {
            return outcome;
        }
        EdgeRuleInterpreter.Decision d = outcome.decision();
        EdgeRuleInterpreter.Action action = max(d.action(), result.escalation());
        List<String> reasons = new ArrayList<>(d.reasons() == null ? List.of() : d.reasons());
        reasons.addAll(result.reasons());
        EdgeRuleInterpreter.Decision escalated = new EdgeRuleInterpreter.Decision(action, d.score(),
                d.triggeredRuleIds(), reasons, d.triggeredRuleVersionIds());
        return new EdgeEngine.Outcome(escalated, outcome.evaluator(), outcome.failClosedCode());
    }

    /** The more severe of two actions (declaration order is severity order); null-safe. */
    static EdgeRuleInterpreter.Action max(EdgeRuleInterpreter.Action a, EdgeRuleInterpreter.Action b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return b.ordinal() > a.ordinal() ? b : a;
    }

    /** At most {@code max} characters. */
    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
