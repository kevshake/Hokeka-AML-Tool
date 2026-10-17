package com.hokeka.edge.store;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Key;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AerospikeFeatureStoreTest {

    /** A card transaction at {@code at}. */
    private static TxnContext txn(String pan, Instant at, long cents) {
        return new TxnContext(at, pan, cents, "USD", "KE", "m-1", "POS", TxnContext.OUTBOUND, false, null, null, null);
    }

    /**
     * Recording advances the card's hourly and daily buckets and its profile; no transaction record
     * is written to Aerospike (evaluations live on the Edge Ledger).
     */
    @Test
    void recordAdvancesHourDayAndProfileOnly() {
        FakeAerospike as = new FakeAerospike();
        AerospikeFeatureStore store = new AerospikeFeatureStore(as.client, "hokeka", "site-a");
        Instant at = Instant.parse("2026-10-17T09:15:00Z");

        store.record(txn("abc123", at, 1250L), false);
        store.recordVelocity("", 99L);
        store.recordVelocity(null, 99L);

        assertEquals(List.of("abc123:2026101709:site-a"), as.keys("velocity"));
        assertEquals(List.of("abc123:20261017:site-a"), as.keys("velocity_day"));
        assertEquals(List.of("abc123:site-a"), as.keys("card_profile"));
        Map<String, Object> hour = as.bins("velocity", "abc123:2026101709:site-a");
        assertEquals(1L, hour.get("count"));
        assertEquals(1250L, hour.get("amount_cents"));
        assertEquals(1250L, hour.get("out_cents"));
        assertEquals(1250.0 * 1250.0, as.bins("velocity_day", "abc123:20261017:site-a").get("sq"));
        assertTrue(as.keys("txn").isEmpty());
    }

    /** One batch get reads 48 hourly + 31 daily buckets and the profile (80 keys), all site-scoped. */
    @Test
    void historyIsOneBoundedBatchOfSiteScopedKeys() {
        FakeAerospike as = new FakeAerospike();
        AerospikeFeatureStore store = new AerospikeFeatureStore(as.client, "hokeka", "site-a");
        Instant at = Instant.parse("2026-10-17T09:15:00Z");

        store.derive(txn("abc123", at, 100L));

        assertEquals(1, as.batchGets.get());
        Key[] keys = as.lastBatch.get();
        assertEquals(CardHistory.HOURS + CardHistory.DAYS + 1, keys.length);
        assertEquals("abc123:2026101709:site-a", keys[0].userKey.toString());
        assertEquals("abc123:2026101510:site-a", keys[CardHistory.HOURS - 1].userKey.toString()); // 47 h back
        assertEquals("abc123:20261017:site-a", keys[CardHistory.HOURS].userKey.toString());
        for (Key k : keys) {
            assertTrue(k.userKey.toString().endsWith(":site-a"), k.userKey.toString());
        }
    }

    /** A dead cluster makes history unavailable (and only request features are derived). */
    @Test
    void storeOutageIsReportedNotTreatedAsNoHistory() {
        FakeAerospike as = new FakeAerospike();
        AerospikeFeatureStore store = new AerospikeFeatureStore(as.client, "hokeka", "site-a");
        as.setDown(true);

        FeatureDerivation d = store.derive(txn("abc123", Instant.now(), 100L));

        assertTrue(d.storeUnavailable());
        assertFalse(d.features().containsKey("pan_txn_count_24h"));
        assertFalse(d.features().containsKey("is_first_transaction"));
        assertEquals(1.0, d.features().get("amount"));
        store.record(txn("abc123", Instant.now(), 100L), false); // never throws
        assertTrue(store.readCounters(List.of(new EdgeFeatureStore.CounterKey("ctr_day", "x")), List.of("n")).isEmpty());
    }

    /** The contract key format: {@code <pan>:<yyyyMMddHH UTC>:<site>}. */
    @Test
    void velocityUserKeyIsPanHourSite() {
        assertEquals("abc123:2026101709:nbo-dc1",
                AerospikeFeatureStore.velocityUserKey("abc123", Instant.parse("2026-10-17T09:59:59Z"), "nbo-dc1"));
    }

    /**
     * Two sites sharing nothing but a card: activity on site A is invisible to site B (no XDR), while
     * a second node of site A (same site id, same cluster) sees it. Counts include the current one.
     */
    @Test
    void historyIsScopedToTheSite() {
        FakeAerospike as = new FakeAerospike();
        AerospikeFeatureStore siteANode1 = new AerospikeFeatureStore(as.client, "hokeka", "site-a");
        AerospikeFeatureStore siteANode2 = new AerospikeFeatureStore(as.client, "hokeka", "site-a");
        AerospikeFeatureStore siteB = new AerospikeFeatureStore(as.client, "hokeka", "site-b");
        Instant at = Instant.now();

        siteANode1.record(txn("card-1", at.minusSeconds(30), 1000L), false);
        siteANode1.record(txn("card-1", at.minusSeconds(20), 2500L), false);
        siteANode2.record(txn("card-1", at.minusSeconds(10), 500L), false);
        siteB.record(txn("card-1", at.minusSeconds(5), 9900L), false);

        Map<String, Object> a = siteANode2.derive(txn("card-1", at, 0L)).features();
        assertEquals(4L, a.get("pan_txn_count_1m"));
        assertEquals(4L, a.get("pan_txn_count_24h"));
        assertEquals(40.0, a.get("pan_amount_sum_24h"));
        assertEquals(false, a.get("is_first_transaction"));

        Map<String, Object> b = siteB.derive(txn("card-1", at, 0L)).features();
        assertEquals(2L, b.get("pan_txn_count_24h"));
        assertEquals(99.0, b.get("pan_amount_sum_24h"));

        Map<String, Object> c = new AerospikeFeatureStore(as.client, "hokeka", "site-c")
                .derive(txn("card-1", at, 0L)).features();
        assertEquals(1L, c.get("pan_txn_count_24h"), "a site with no activity sees only the current one");
        assertEquals(true, c.get("is_first_transaction"));
    }

    /** Counters add atomically and read back per key. */
    @Test
    void countersAddAndRead() {
        FakeAerospike as = new FakeAerospike();
        AerospikeFeatureStore store = new AerospikeFeatureStore(as.client, "hokeka", "site-a");
        EdgeFeatureStore.CounterKey k = new EdgeFeatureStore.CounterKey("limit_usage", "L1:card:h1");
        assertEquals(5L, store.addCounters(k, 60, new java.util.LinkedHashMap<>(Map.of("n", 5L))).orElseThrow()[0]);
        assertEquals(7L, store.addCounters(k, 60, new java.util.LinkedHashMap<>(Map.of("n", 2L))).orElseThrow()[0]);
        long[][] read = store.readCounters(List.of(k, new EdgeFeatureStore.CounterKey("limit_usage", "none")),
                List.of("n")).orElseThrow();
        assertEquals(7L, read[0][0]);
        assertEquals(0L, read[1][0]);
        assertEquals(List.of("L1:card:h1:site-a"), as.keys("limit_usage"));
    }

    /** The profile keeps at most RECENT_MAX entries, newest first. */
    @Test
    void profileListIsBounded() {
        FakeAerospike as = new FakeAerospike();
        AerospikeFeatureStore store = new AerospikeFeatureStore(as.client, "hokeka", "site-a");
        Instant at = Instant.now().minusSeconds(3600);
        for (int i = 0; i < CardHistory.RECENT_MAX + 20; i++) {
            store.record(txn("card-9", at.plusSeconds(i), i), false);
        }
        CardHistory h = store.readHistory(txn("card-9", Instant.now(), 0L)).orElseThrow();
        assertEquals(CardHistory.RECENT_MAX, h.recent().size());
        assertEquals((long) CardHistory.RECENT_MAX + 19, h.recent().get(0).cents());
        assertEquals(at.toEpochMilli(), h.firstSeenMs());
    }

    /** A store without a site id cannot scope its keys and is refused. */
    @Test
    void blankSiteIdIsRefused() {
        AerospikeClient client = mock(AerospikeClient.class);
        assertThrows(IllegalArgumentException.class, () -> new AerospikeFeatureStore(client, "hokeka", " "));
        assertThrows(IllegalArgumentException.class, () -> new AerospikeFeatureStore(client, "hokeka", null));
        assertEquals("site-a", new AerospikeFeatureStore(client, "hokeka", " site-a ").siteId());
    }
}
