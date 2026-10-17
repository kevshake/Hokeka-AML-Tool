package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelTime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * The Console's transaction list / export filter, as the contract's {@code searchFilter}
 * ({@code from ≤ evaluatedAt < to}, merchant, decisions, rule, txnRef, minimum score, CTR and
 * sanctions flags). Validation of the values is left to the gateway's schema check, which answers
 * {@code 400 INVALID_PARAMS} with the JSON pointer only.
 *
 * @param from         inclusive lower bound
 * @param to           exclusive upper bound
 * @param merchantId   cloud merchant id
 * @param decisions    ALLOW / ALERT / HOLD / BLOCK
 * @param ruleId       triggered rule id
 * @param txnRef       the PSP's transaction id (exact)
 * @param minScore     minimum score
 * @param ctrRequired  CTR flag
 * @param sanctionsHit sanctions flag
 */
public record TransactionFilter(Instant from, Instant to, String merchantId, List<String> decisions, Long ruleId,
                                String txnRef, Integer minScore, Boolean ctrRequired, Boolean sanctionsHit) {

    /** Default window when the Console names none: the last 30 days. */
    public static final Duration DEFAULT_WINDOW = Duration.ofDays(30);

    /**
     * Parses the request parameters; a missing {@code to} is now, a missing {@code from} is
     * {@code to − 30 days}.
     *
     * @throws EdgeDataException {@code 400 INVALID_PARAMS} naming {@code /from} or {@code /to}
     */
    public static TransactionFilter parse(String from, String to, String merchantId, List<String> decisions,
                                          Long ruleId, String txnRef, Integer minScore, Boolean ctrRequired,
                                          Boolean sanctionsHit, Clock clock) {
        Instant t = to == null || to.isBlank() ? clock.instant() : instant(to, "/to");
        Instant f = from == null || from.isBlank() ? t.minus(DEFAULT_WINDOW) : instant(from, "/from");
        List<String> ds = decisions == null ? null : decisions.stream()
                .filter(d -> d != null && !d.isBlank()).map(d -> d.strip().toUpperCase(Locale.ROOT)).distinct().toList();
        return new TransactionFilter(f, t, blankToNull(merchantId), ds == null || ds.isEmpty() ? null : ds, ruleId,
                blankToNull(txnRef), minScore, ctrRequired, sanctionsHit);
    }

    /** The contract {@code searchFilter} object (instants at millisecond precision). */
    public ObjectNode toJson() {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("from", TunnelTime.formatMillis(from));
        n.put("to", TunnelTime.formatMillis(to));
        if (merchantId != null) {
            n.put("merchantId", merchantId);
        }
        if (decisions != null) {
            ArrayNode a = n.putArray("decisions");
            decisions.forEach(a::add);
        }
        if (ruleId != null) {
            n.put("ruleId", ruleId);
        }
        if (txnRef != null) {
            n.put("txnRef", txnRef);
        }
        if (minScore != null) {
            n.put("minScore", minScore);
        }
        if (ctrRequired != null) {
            n.put("ctrRequired", ctrRequired);
        }
        if (sanctionsHit != null) {
            n.put("sanctionsHit", sanctionsHit);
        }
        return n;
    }

    /** Parses an RFC 3339 instant, else {@code INVALID_PARAMS} with the pointer (never the value). */
    private static Instant instant(String value, String pointer) {
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new EdgeDataException(TunnelError.invalidParams(pointer));
        }
    }

    /** Blank strings are absent. */
    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }
}
