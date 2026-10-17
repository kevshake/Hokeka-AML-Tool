package com.posgateway.aml.service.filing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.service.edgedata.EdgeDataScope;
import com.posgateway.aml.service.edgedata.EdgeTransactionProxyService;
import com.posgateway.aml.service.edgedata.LegacyTransactionReadService;
import com.posgateway.aml.service.edgedata.TransactionSource;
import com.posgateway.aml.service.edgedata.TransactionSourceResolver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pulls the transaction data of a filing at filing time (R9): {@code filing.extract} over the Edge
 * Tunnel for a PSP with an Edge (contract §7.6), or the legacy cloud rows for a PSP without one
 * (R6 legacy-row rule, until R12). The result exists only for the filing request.
 *
 * <p><b>Never an incomplete document.</b> Every reference must resolve to a transaction with an
 * amount and a currency, every site must answer, and a CTR needs its aggregate; otherwise the
 * filing is refused with a retryable {@link FilingException} and nothing is filed.
 */
@Service
public class FilingDataService {

    private final TransactionSourceResolver sources;
    private final EdgeTransactionProxyService edge;
    private final LegacyTransactionReadService legacy;

    /** Wires the data pulls. */
    public FilingDataService(TransactionSourceResolver sources, EdgeTransactionProxyService edge,
                             LegacyTransactionReadService legacy) {
        this.sources = sources;
        this.edge = edge;
        this.legacy = legacy;
    }

    /**
     * The transactions of a SAR/STR filing.
     *
     * @param scope     the filer's scope (PSP user, purpose SAR_FILING, filing reference)
     * @param kind      {@code SAR} or {@code STR}
     * @param filingRef the SAR reference
     */
    public FilingExtract extractSar(EdgeDataScope scope, String kind, String filingRef,
                                    List<TransactionReference> refs) {
        List<TransactionReference> list = refs == null ? List.of() : refs.stream().filter(r -> r != null && !r.isEmpty()).toList();
        if (list.isEmpty()) {
            return new FilingExtract("NONE", List.of(), null, List.of());
        }
        return sources.sourceFor(scope.pspId()) == TransactionSource.EDGE
                ? fromEdge(scope, kind, filingRef, list, null)
                : fromLegacy(scope, kind, filingRef, list, false);
    }

    /**
     * The anchor transaction and CTR aggregate of a CTR filing.
     *
     * @param anchor the decision record (or legacy transaction) the CTR deadline was raised from
     */
    public FilingExtract extractCtr(EdgeDataScope scope, String filingRef, TransactionReference anchor) {
        if (anchor == null || anchor.isEmpty()) {
            throw new FilingException(FilingException.Code.FILING_STATE_CONFLICT,
                    "the CTR deadline names no transaction", filingRef);
        }
        return sources.sourceFor(scope.pspId()) == TransactionSource.EDGE
                ? fromEdge(scope, "CTR", filingRef, List.of(anchor), anchor)
                : fromLegacy(scope, "CTR", filingRef, List.of(anchor), true);
    }

    // ── Edge ─────────────────────────────────────────────────────────────────────────────────────

    /** filing.extract over the tunnel; refuses anything short of a complete answer. */
    private FilingExtract fromEdge(EdgeDataScope scope, String kind, String filingRef, List<TransactionReference> refs,
                                   TransactionReference ctrAnchor) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        int unpullable = 0;
        for (TransactionReference r : refs) {
            if (r.edgeAssessmentId() != null) {
                ids.add(r.edgeAssessmentId());
            } else {
                unpullable++;
            }
        }
        if (unpullable > 0) {
            throw new FilingException(FilingException.Code.FILING_INCOMPLETE, 409, false,
                    unpullable + " transaction reference(s) carry no decision record id and cannot be pulled from"
                            + " the Edge; nothing was filed", filingRef, null, List.of(), unpullable);
        }
        EdgeTransactionProxyService.FilingPull pull;
        try {
            pull = edge.filingExtract(scope, kind, filingRef, ids);
        } catch (EdgeDataException e) {
            throw FilingException.edgeFailure(e.error(), filingRef);
        }
        // Every id must come back from a site that answered completely; an unavailable site only
        // matters when it leaves an id missing.
        if (pull.incomplete() || !pull.missing().isEmpty()) {
            throw new FilingException(FilingException.Code.FILING_INCOMPLETE, 503, true,
                    "the Edge did not return every transaction of this filing; nothing was filed, retry when every"
                            + " site is online", filingRef, null, pull.unavailableSites(), pull.missing().size());
        }
        List<FilingExtract.Txn> txns = new ArrayList<>();
        for (ObjectNode item : pull.transactions()) {
            txns.add(edgeTxn(item.path("transaction"), filingRef));
        }
        FilingExtract.CtrAggregate agg = null;
        if (ctrAnchor != null) {
            String anchorId = ctrAnchor.edgeAssessmentId().toString();
            ObjectNode a = pull.aggregates().stream()
                    .filter(x -> anchorId.equals(x.path("anchorEdgeAssessmentId").asText())).findFirst()
                    .orElseThrow(() -> new FilingException(FilingException.Code.FILING_INCOMPLETE, 409, false,
                            "the Edge returned no cash aggregate for this CTR (the anchor is not a cash transaction"
                                    + " of its business day); nothing was filed", filingRef, null, List.of(), 0));
            agg = edgeAggregate(a, filingRef);
        }
        return new FilingExtract(TransactionSource.EDGE.name(), List.copyOf(txns), agg, pull.auditIds());
    }

    /** One {@code transactionDetail} as a filed transaction (amount and currency required). */
    static FilingExtract.Txn edgeTxn(JsonNode t, String filingRef) {
        if (!t.hasNonNull("amountMinor") || !t.hasNonNull("currency")) {
            throw new FilingException(FilingException.Code.FILING_INCOMPLETE, 409, false,
                    "a transaction of this filing has no amount or currency on the Edge; nothing was filed",
                    filingRef, null, List.of(), 1);
        }
        List<Long> rules = new ArrayList<>();
        t.path("triggeredRuleIds").forEach(n -> rules.add(n.asLong()));
        List<String> typologies = new ArrayList<>();
        t.path("typologyCodes").forEach(n -> typologies.add(n.asText()));
        List<FilingExtract.SanctionsHit> hits = new ArrayList<>();
        for (JsonNode h : t.path("sanctionsHits")) {
            hits.add(new FilingExtract.SanctionsHit(h.path("entryId").asText(), h.path("score").asDouble(),
                    h.path("matchedOn").asText(null), h.path("matchedValue").asText(null),
                    h.path("listVersion").asText(null)));
        }
        Map<String, String> features = new LinkedHashMap<>();
        JsonNode f = t.path("features");
        for (Iterator<Map.Entry<String, JsonNode>> it = f.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getValue().isValueNode() && !e.getValue().isNull()) {
                features.put(e.getKey(), e.getValue().asText());
            }
        }
        return new FilingExtract.Txn(t.path("edgeAssessmentId").asText(), null, t.path("txnRef").asText(null),
                t.path("siteId").asText(null), instant(t.path("evaluatedAt").asText(null)),
                t.path("decision").asText(null), t.hasNonNull("score") ? t.get("score").asInt() : null,
                t.path("merchantId").asText(null), t.path("mcc").asText(null), t.get("amountMinor").asLong(),
                t.get("currency").asText(), t.path("countryCode").asText(null), t.path("channel").asText(null),
                List.copyOf(rules), List.copyOf(typologies), List.copyOf(hits), features,
                t.path("legalHold").asBoolean(false));
    }

    /** A CTR_AGGREGATE item, amounts converted from minor to major units. */
    static FilingExtract.CtrAggregate edgeAggregate(JsonNode a, String filingRef) {
        String ccy = a.path("reportingCurrency").asText();
        List<String> ids = new ArrayList<>();
        a.path("edgeAssessmentIds").forEach(n -> ids.add(n.asText()));
        LocalDate day;
        try {
            day = LocalDate.parse(a.path("businessDate").asText());
        } catch (DateTimeParseException e) {
            throw new FilingException(FilingException.Code.FILING_INCOMPLETE, 409, false,
                    "the CTR aggregate has no valid business date; nothing was filed", filingRef, null, List.of(), 0);
        }
        return new FilingExtract.CtrAggregate("CARD_BUSINESS_DAY", a.path("anchorEdgeAssessmentId").asText(), day,
                a.path("timezone").asText(null), ccy, major(a.path("cashTotalMinor").asLong(), ccy),
                major(a.path("thresholdMinor").asLong(), ccy), a.path("txnCount").asInt(), List.copyOf(ids),
                instant(a.path("fxAsOf").asText(null)), null,
                a.hasNonNull("configVersion") ? a.get("configVersion").asLong() : null);
    }

    // ── legacy cloud rows ────────────────────────────────────────────────────────────────────────

    /** The legacy rows of a PSP without an Edge; refuses missing rows and rows without amounts. */
    private FilingExtract fromLegacy(EdgeDataScope scope, String kind, String filingRef,
                                     List<TransactionReference> refs, boolean ctr) {
        UUID auditId = UUID.randomUUID();
        LegacyTransactionReadService.LegacyFiling found;
        try {
            found = legacy.filingExtract(scope, kind, filingRef, refs, auditId);
        } catch (EdgeDataException e) {
            throw FilingException.edgeFailure(e.error(), filingRef);
        }
        if (!found.missing().isEmpty()) {
            throw new FilingException(FilingException.Code.FILING_INCOMPLETE, 409, false,
                    found.missing().size() + " transaction(s) of this filing no longer exist; nothing was filed",
                    filingRef, null, List.of(), found.missing().size());
        }
        List<FilingExtract.Txn> txns = new ArrayList<>();
        for (LegacyTransactionReadService.LegacyFilingRow row : found.rows()) {
            txns.add(legacyTxn(row, filingRef));
        }
        FilingExtract.CtrAggregate agg = null;
        if (ctr) {
            agg = legacyAggregate(found.rows().get(0), filingRef);
        }
        return new FilingExtract(TransactionSource.CLOUD_LEGACY.name(), List.copyOf(txns), agg, List.of(auditId));
    }

    /** One legacy row as a filed transaction. */
    static FilingExtract.Txn legacyTxn(LegacyTransactionReadService.LegacyFilingRow row, String filingRef) {
        TransactionEntity t = row.transaction();
        if (t.getAmountCents() == null || t.getCurrency() == null || t.getCurrency().isBlank()) {
            throw new FilingException(FilingException.Code.FILING_INCOMPLETE, 409, false,
                    "a transaction of this filing has no amount or currency; nothing was filed", filingRef, null,
                    List.of(), 1);
        }
        TransactionReference ref = row.reference();
        String txnRef = ref.txnRef() != null ? ref.txnRef() : TransactionReference.safeTxnRef(t.getClientReference());
        Instant at = t.getTxnTs() == null ? null : t.getTxnTs().toInstant(ZoneOffset.UTC);
        return new FilingExtract.Txn(ref.edgeAssessmentId() == null ? null : ref.edgeAssessmentId().toString(),
                t.getTxnId(), txnRef, null, at, t.getDecision(), null, t.getMerchantId(), null, t.getAmountCents(),
                t.getCurrency(), t.getMerchantCountry(), t.getChannelType(), List.of(), List.of(), List.of(),
                Map.of(), false);
    }

    /** The CTR evaluation recorded on a legacy row (single-transaction basis, USD equivalent). */
    static FilingExtract.CtrAggregate legacyAggregate(LegacyTransactionReadService.LegacyFilingRow row,
                                                      String filingRef) {
        TransactionEntity t = row.transaction();
        if (t.getCtrUsdEquivalent() == null || t.getCtrThresholdUsd() == null) {
            throw new FilingException(FilingException.Code.FILING_INCOMPLETE, 409, false,
                    "the transaction has no recorded CTR evaluation; nothing was filed", filingRef, null, List.of(), 0);
        }
        String id = row.reference().edgeAssessmentId() != null ? row.reference().edgeAssessmentId().toString()
                : "legacy-" + t.getTxnId();
        LocalDate day = t.getTxnTs() == null ? null : t.getTxnTs().toLocalDate();
        Instant fxAsOf = t.getCtrRateEffectiveAt() == null ? null : t.getCtrRateEffectiveAt().toInstant(ZoneOffset.UTC);
        return new FilingExtract.CtrAggregate("SINGLE_TRANSACTION", id, day, "UTC", "USD", t.getCtrUsdEquivalent(),
                t.getCtrThresholdUsd(), 1, List.of(id), fxAsOf, t.getCtrRateSource(), null);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** Minor units to major units with the currency's ISO 4217 exponent (2 when unknown). */
    static BigDecimal major(long minor, String currency) {
        return BigDecimal.valueOf(minor, fractionDigits(currency));
    }

    /** ISO 4217 minor-unit exponent of a currency (2 when unknown). */
    static int fractionDigits(String currency) {
        try {
            int d = Currency.getInstance(currency).getDefaultFractionDigits();
            return d < 0 ? 2 : d;
        } catch (IllegalArgumentException | NullPointerException e) {
            return 2;
        }
    }

    /** Parses an RFC 3339 instant, or null. */
    private static Instant instant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
