package com.hokeka.edge.typology;

import com.hokeka.edge.EdgeRuleInterpreter.Action;
import com.hokeka.edge.residency.EdgeConfigDocument;
import com.hokeka.edge.store.AerospikeFeatureStore;
import com.hokeka.edge.store.FakeAerospike;
import com.hokeka.edge.store.TxnContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CTR, FX, structuring and limits as the overlay applies them on evaluate. */
class ComplianceOverlayTest {

    private final FakeAerospike as = new FakeAerospike();
    private final AerospikeFeatureStore store = new AerospikeFeatureStore(as.client, "hokeka", "site-a");
    private final Instant at = Instant.parse("2026-10-17T07:00:00Z");

    /** A request bag. */
    private static Map<String, Object> req(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Assesses one request the way the controller does (derive → assess → record → commit). */
    private ComplianceOverlay.Assessment run(ComplianceOverlay overlay, Map<String, Object> request, Action ruleAction) {
        TxnContext t = TxnContext.from(request, at);
        Map<String, Object> derived = store.derive(t).features();
        ComplianceOverlay.Assessment a = overlay.assess(t, request, derived);
        store.record(t, a.structuringBand());
        overlay.commit(a, ruleAction.max(a.action()));
        return a;
    }

    /** The overlay over a fixed config document. */
    private ComplianceOverlay overlay(EdgeConfigDocument doc) {
        return new ComplianceOverlay(store, () -> Optional.of(doc));
    }

    /** The cash transaction that takes the card-day total over the threshold sets ctrRequired, once. */
    @Test
    void ctrIsFlaggedOnTheCrossingTransactionInTheReportingCurrency() {
        ComplianceOverlay o = overlay(TypologyFixtures.config(d -> { }));
        Map<String, Object> kes = req("pan_hash", "card-1", "amount_cents", 100_000_000L, "currency", "KES",
                "channel", "ATM");
        ComplianceOverlay.Assessment first = run(o, kes, Action.ALLOW);   // 7 700 USD
        assertFalse(first.ctrRequired());
        ComplianceOverlay.Assessment second = run(o, kes, Action.ALLOW);  // 15 400 USD
        assertTrue(second.ctrRequired());
        assertEquals(List.of("CTR_REPORTABLE"), second.typologyCodes());
        assertEquals(Action.ALLOW, second.action(), "CTR alone does not stop the payment");
        assertFalse(run(o, kes, Action.ALLOW).ctrRequired(), "the card-day was already reported");
        // Not cash: never aggregated.
        assertFalse(run(o, req("pan_hash", "card-1", "amount_cents", 900_000_000L, "currency", "KES",
                "channel", "POS"), Action.ALLOW).ctrRequired());
    }

    /** A single cash transaction at the threshold is reportable even without a card key. */
    @Test
    void ctrWithoutCardIsPerTransaction() {
        ComplianceOverlay o = overlay(TypologyFixtures.config(d -> { }));
        assertTrue(run(o, req("amount_cents", 1_500_000L, "currency", "USD", "cash_transaction", true),
                Action.ALLOW).ctrRequired());
        assertFalse(run(o, req("amount_cents", 1_499_999L, "currency", "USD", "cash_transaction", true),
                Action.ALLOW).ctrRequired());
    }

    /** A missing FX rate or currency never under-reports: HOLD + ctrRequired + a visible code. */
    @Test
    void missingFxFailsVisibly() {
        ComplianceOverlay o = overlay(TypologyFixtures.config(d -> { }));
        for (Map<String, Object> r : List.of(
                req("pan_hash", "card-2", "amount_cents", 100L, "currency", "GBP", "channel", "ATM"),
                req("pan_hash", "card-2", "amount_cents", 100L, "channel", "ATM"))) {
            ComplianceOverlay.Assessment a = run(o, r, Action.ALLOW);
            assertEquals(Action.HOLD, a.action());
            assertTrue(a.ctrRequired());
            assertEquals(List.of("CTR_FX_UNAVAILABLE"), a.typologyCodes());
        }
    }

    /** No threshold for the reporting currency, or no readable counter: fail visibly too. */
    @Test
    void invalidConfigAndUnreadableCounterFailVisibly() {
        ComplianceOverlay bad = overlay(TypologyFixtures.config(d -> ((com.fasterxml.jackson.databind.node.ObjectNode)
                d.get("ctr")).put("reportingCurrency", "EUR")));
        ComplianceOverlay.Assessment a = run(bad, req("pan_hash", "card-3", "amount_cents", 100L, "currency", "USD",
                "channel", "ATM"), Action.ALLOW);
        assertEquals(List.of("CTR_CONFIG_INVALID"), a.typologyCodes());
        assertTrue(a.ctrRequired());

        ComplianceOverlay o = overlay(TypologyFixtures.config(d -> { }));
        as.setDown(true);
        ComplianceOverlay.Assessment down = o.assess(TxnContext.from(req("pan_hash", "card-3", "amount_cents", 100L,
                "currency", "USD", "channel", "ATM"), at), Map.of(), Map.of());
        assertEquals(Action.HOLD, down.action());
        assertEquals(List.of("CTR_AGGREGATE_UNAVAILABLE"), down.typologyCodes());
        assertTrue(down.ctrRequired());
    }

    /** Two cash amounts just under the threshold within 24 h: structuring HOLD on the second. */
    @Test
    void cashStructuringHoldsTheRepeat() {
        ComplianceOverlay o = overlay(TypologyFixtures.config(d -> { }));
        Map<String, Object> band = req("pan_hash", "card-4", "amount_cents", 1_300_000L, "currency", "USD",
                "channel", "CASH_IN");
        ComplianceOverlay.Assessment first = run(o, band, Action.ALLOW);
        assertTrue(first.structuringBand());
        assertEquals(1L, first.features().get("structuring_repeat_count_24h"));
        assertEquals(Action.ALLOW, first.action());
        ComplianceOverlay.Assessment second = run(o, band, Action.ALLOW);
        assertEquals(Action.HOLD, second.action());
        assertTrue(second.typologyCodes().contains("CASH_STRUCTURING_24H"));
        assertEquals(true, second.features().get("is_structuring_suspected"));
        // Below the floor (0.80 x 15 000 = 12 000) is not in the band.
        assertFalse(run(o, req("pan_hash", "card-5", "amount_cents", 1_199_999L, "currency", "USD",
                "channel", "ATM"), Action.ALLOW).structuringBand());
    }

    /** COUNT limit: the transaction beyond max takes the limit's action; BLOCKed ones consume nothing. */
    @Test
    void countLimitsAreEnforcedAndBlockedTransactionsDoNotConsume() {
        ComplianceOverlay o = overlay(TypologyFixtures.config(d -> {
            TypologyFixtures.limit(d, 9, "CARD", "COUNT", "PT1H", null, 2, "HOLD");
            TypologyFixtures.limit(d, 10, "MERCHANT", "COUNT", "P1D", null, 3, "BLOCK");
        }));
        Map<String, Object> r = req("pan_hash", "card-6", "merchant_id", "m-1", "amount_cents", 100L, "currency", "USD");
        assertEquals(Action.ALLOW, run(o, r, Action.ALLOW).action());
        assertEquals(Action.ALLOW, run(o, r, Action.ALLOW).action());
        ComplianceOverlay.Assessment third = run(o, r, Action.ALLOW);
        assertEquals(Action.HOLD, third.action());
        assertEquals(List.of("LIMIT_9"), third.typologyCodes());
        ComplianceOverlay.Assessment fourth = run(o, req("pan_hash", "card-7", "merchant_id", "m-1",
                "amount_cents", 100L, "currency", "USD"), Action.ALLOW);
        assertEquals(Action.BLOCK, fourth.action(), "merchant m-1 already had 3 today");
        // The BLOCKed one did not count: still 3 used.
        ComplianceOverlay.Usage merchantUsage = fourth.limitUsage().stream()
                .filter(u -> u.key().key().startsWith("L10:")).findFirst().orElseThrow();
        long[][] used = store.readCounters(List.of(merchantUsage.key()), List.of("n")).orElseThrow();
        assertEquals(3L, used[0][0]);
    }

    /** AMOUNT limits convert into the limit's currency; a missing rate fails visibly. */
    @Test
    void amountLimitsConvertAndFailVisiblyWithoutFx() {
        ComplianceOverlay o = overlay(TypologyFixtures.config(d ->
                TypologyFixtures.limit(d, 11, "CARD", "AMOUNT", "P1D", "KES", 100_000_000L, "ALERT")));
        assertEquals(Action.ALLOW, run(o, req("pan_hash", "card-8", "amount_cents", 700_000L, "currency", "USD"),
                Action.ALLOW).action()); // 909 090.91 KES
        ComplianceOverlay.Assessment over = run(o, req("pan_hash", "card-8", "amount_cents", 100_000L,
                "currency", "USD"), Action.ALLOW);
        assertEquals(Action.ALERT, over.action());
        assertEquals(List.of("LIMIT_11"), over.typologyCodes());
        ComplianceOverlay.Assessment gbp = run(o, req("pan_hash", "card-8", "amount_cents", 1L, "currency", "GBP"),
                Action.ALLOW);
        assertEquals(Action.HOLD, gbp.action());
        assertEquals(List.of("LIMIT_FX_UNAVAILABLE"), gbp.typologyCodes());
    }

    /** Many breached limits: the action holds, the record keeps the schema's 64-code maximum. */
    @Test
    void typologyCodesAreCappedAtTheSchemaMaximum() {
        ComplianceOverlay o = overlay(TypologyFixtures.config(d -> {
            for (int i = 1; i <= 70; i++) {
                TypologyFixtures.limit(d, i, "CARD", "COUNT", "P1D", null, 0, "HOLD");
            }
        }));
        ComplianceOverlay.Assessment a = run(o, req("pan_hash", "card-x", "amount_cents", 1L, "currency", "USD"),
                Action.ALLOW);
        assertEquals(Action.HOLD, a.action());
        assertEquals(ComplianceOverlay.MAX_CODES, a.typologyCodes().size());
        assertEquals(70, a.reasons().size());
    }

    /** Before the first config document: no CTR, no limits (contract §6.3). */
    @Test
    void noConfigMeansNoCtrAndNoLimits() {
        ComplianceOverlay o = new ComplianceOverlay(store, Optional::empty);
        ComplianceOverlay.Assessment a = run(o, req("pan_hash", "card-9", "amount_cents", 10_000_000_00L,
                "currency", "USD", "cash_transaction", true), Action.ALLOW);
        assertFalse(a.ctrRequired());
        assertTrue(a.typologyCodes().isEmpty());
        assertEquals(true, a.features().get("cash_transaction"));
    }

    /** The R-170 anonymity and R-24 reference features. */
    @Test
    void requestFeatures() {
        ComplianceOverlay o = new ComplianceOverlay(store, Optional::empty);
        assertEquals(true, o.assess(TxnContext.from(req(), at), req(), Map.of()).features()
                .get("anonymous_payment_screening_hit"));
        ComplianceOverlay.Assessment known = o.assess(TxnContext.from(req(), at),
                req("customer_id", "c-1", "reference", "Casino Night"), Map.of());
        assertEquals(false, known.features().get("anonymous_payment_screening_hit"));
        assertEquals("casino night", known.features().get("reference_lc"));
        assertEquals(true, o.assess(TxnContext.from(req(), at), req("customer_id", "c-1", "card_type", "prepaid"),
                Map.of()).features().get("anonymous_payment_screening_hit"));
    }
}
