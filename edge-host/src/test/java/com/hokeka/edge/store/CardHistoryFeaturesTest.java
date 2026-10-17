package com.hokeka.edge.store;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The derivations against the store, with the arithmetic the cloud producer uses. */
class CardHistoryFeaturesTest {

    private final FakeAerospike as = new FakeAerospike();
    private final AerospikeFeatureStore store = new AerospikeFeatureStore(as.client, "hokeka", "site-a");

    /** A transaction. */
    private static TxnContext txn(Instant at, long cents, String country, String merchant, String direction) {
        return new TxnContext(at, "card", cents, "USD", country, merchant, "POS",
                TxnContext.normaliseDirection(direction), direction != null && TxnContext.inLike(direction),
                null, null, null);
    }

    /** Z-score of a spike against a steady history (population variance, as the cloud). */
    @Test
    void zscoreAgainstTheCardsOwnHistory() {
        Instant now = Instant.parse("2026-10-17T12:00:00Z");
        long[] history = {1000, 1200, 800, 1000, 1000};
        for (int i = 0; i < history.length; i++) {
            store.record(txn(now.minusSeconds(86400L * (i + 1)), history[i], "KE", "m", null), false);
        }
        Map<String, Object> f = store.derive(txn(now, 5000, "KE", "m", null)).features();
        // mean 1000, population sd sqrt((0+40000+40000+0+0)/5)=126.49 → (5000-1000)/126.49 = 31.62
        assertEquals(31.6227766, (double) f.get("zscore_amount_vs_pan_history"), 1e-6);
        assertEquals(5.0, (double) f.get("avg_amount_spike_ratio"), 1e-9);
        // No history → 0, as the cloud.
        assertEquals(0.0, store.derive(new TxnContext(now, "fresh", 5000L, "USD", null, null, null,
                TxnContext.OUTBOUND, false, null, null, null)).features().get("zscore_amount_vs_pan_history"));
    }

    /** Rolling windows include the current transaction; old evaluations fall out of them. */
    @Test
    void rollingWindowsAndFirstSeen() {
        Instant now = Instant.parse("2026-10-17T12:00:30Z");
        Map<String, Object> first = store.derive(txn(now.minusSeconds(7200), 100, "KE", "m", null)).features();
        assertEquals(true, first.get("is_first_transaction"));
        assertEquals(1L, first.get("pan_txn_count_1m"));
        store.record(txn(now.minusSeconds(7200), 100, "KE", "m", null), false);
        store.record(txn(now.minusSeconds(200), 100, "KE", "m", null), false);
        store.record(txn(now.minusSeconds(20), 100, "KE", "m", null), false);
        Map<String, Object> f = store.derive(txn(now, 100, "KE", "m", null)).features();
        assertEquals(false, f.get("is_first_transaction"));
        assertEquals(2L, f.get("pan_txn_count_1m"));
        assertEquals(3L, f.get("pan_txn_count_5m"));
        assertEquals(3L, f.get("pan_txn_count_1h"));
        assertEquals(4L, f.get("pan_txn_count_24h"));
        assertEquals(0L, f.get("days_since_last_activity"));
        assertEquals(false, f.get("is_new_country"));
        assertEquals(true, store.derive(txn(now, 100, "IR", "m", null)).features().get("is_new_country"));
    }

    /** Rapid movement: inbound then outbound within a day drives the send/receive ratios. */
    @Test
    void rapidMovementRatios() {
        Instant now = Instant.parse("2026-10-17T12:00:00Z");
        store.record(txn(now.minusSeconds(600), 100_000, "KE", "m", "INBOUND"), false);
        Map<String, Object> f = store.derive(txn(now, 1_000_000, "KE", "m2", "OUTBOUND")).features();
        assertEquals(10.0, (double) f.get("spend_receive_spike_ratio"), 1e-9);
        assertEquals(1.0, (double) f.get("send_receive_ratio"), 1e-9);
        assertEquals("OUTBOUND", f.get("txn_direction"));
        assertEquals(2L, f.get("unique_counterparties"));
    }

    /** Circular trading, round values and same-party volume from the remembered evaluations. */
    @Test
    void recentEvaluationFeatures() {
        Instant now = Instant.parse("2026-10-17T12:00:00Z");
        for (int i = 0; i < 3; i++) {
            store.record(txn(now.minusSeconds(3600L * (i + 1)), 50_000, "KE", "m-a", null), false);
        }
        store.record(txn(now.minusSeconds(60), 12_345, "KE", "m-b", "REFUND"), false);
        Map<String, Object> f = store.derive(txn(now, 10_000, "KE", "m-a", null)).features();
        assertEquals(1L, f.get("circular_trading_count"));
        assertEquals(75.0, (double) f.get("round_value_share_pct"), 1e-9);
        assertEquals(3L, f.get("round_value_txn_count"));
        assertEquals(25.0, (double) f.get("refund_share_pct"), 1e-9);
        assertEquals(1600.0, (double) f.get("same_parties_volume"), 1e-9);
        assertEquals(true, f.get("amount_ending_pattern"));
    }

    /** Device and IP features from their own profiles. */
    @Test
    void deviceAndIpProfiles() {
        Instant now = Instant.parse("2026-10-17T12:00:00Z");
        String[] countries = {"KE", "UG", "TZ"};
        for (int i = 0; i < 3; i++) {
            store.record(new TxnContext(now.minusSeconds(100L * (i + 1)), "c" + i, 100L, "USD", countries[i],
                    "m" + i, "WEB", TxnContext.OUTBOUND, false, "dev-1", "10.0.0.1", null), false);
        }
        Map<String, Object> f = store.derive(new TxnContext(now, "c9", 100L, "USD", "RW", "m9", "WEB",
                TxnContext.OUTBOUND, false, "dev-1", "10.0.0.1", null)).features();
        assertEquals(4, f.get("distinct_users_per_payment_id"));
        assertEquals(4, f.get("distinct_users_per_ip"));
        assertEquals(4L, f.get("payment_id_use_count"));
        assertEquals(4, f.get("distinct_countries"));
        assertEquals(true, f.get("ip_outside_expected_location"));
        assertEquals("dev-1", f.get("device_key"));
        assertFalse(f.containsKey("reference_lc"));
        assertTrue(CardHistoryFeatures.zscore(null, 3, 30, 300) == 0.0);
    }
}
