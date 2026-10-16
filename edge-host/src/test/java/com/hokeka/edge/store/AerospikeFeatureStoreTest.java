package com.hokeka.edge.store;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Record;
import com.aerospike.client.policy.BatchPolicy;
import com.aerospike.client.policy.WritePolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AerospikeFeatureStoreTest {

    /**
     * Velocity is kept (one atomic operate on the card's hourly bucket in the velocity set) and no
     * transaction record is written to Aerospike any more — evaluations live on the Edge Ledger.
     */
    @Test
    void recordVelocityAdvancesTheHourlyBucketAndStoresNoTransaction() {
        AerospikeClient client = mock(AerospikeClient.class);
        AerospikeFeatureStore store = new AerospikeFeatureStore(client, "hokeka", "site-a");

        store.recordVelocity("abc123", 1250L);
        store.recordVelocity("", 99L);
        store.recordVelocity(null, 99L);

        ArgumentCaptor<Key> key = ArgumentCaptor.forClass(Key.class);
        ArgumentCaptor<com.aerospike.client.policy.WritePolicy> policy =
                ArgumentCaptor.forClass(com.aerospike.client.policy.WritePolicy.class);
        verify(client, org.mockito.Mockito.times(1)).operate(policy.capture(), key.capture(),
                any(com.aerospike.client.Operation[].class));
        assertEquals("velocity", key.getValue().setName);
        assertTrue(key.getValue().userKey.toString().matches("abc123:\\d{10}:site-a"),
                "velocity key is <pan>:<yyyyMMddHH>:<site>: " + key.getValue().userKey);
        assertEquals(26 * 3600, policy.getValue().expiration);
        verify(client, org.mockito.Mockito.never()).put(any(com.aerospike.client.policy.WritePolicy.class),
                any(Key.class), any(com.aerospike.client.Bin[].class));
    }

    @Test
    void deriveFeaturesUsesSingleBatchGetForTwentyFourHourWindow() {
        AerospikeClient client = mock(AerospikeClient.class);
        when(client.isConnected()).thenReturn(true);
        Record[] records = new Record[24];
        records[0] = record(3, 500L);
        when(client.get(any(BatchPolicy.class), any(Key[].class))).thenReturn(records);

        AerospikeFeatureStore store = new AerospikeFeatureStore(client, "hokeka", "site-a");
        FeatureDerivation derived = store.deriveFeaturesDetailed("abc123");

        ArgumentCaptor<Key[]> keysCaptor = ArgumentCaptor.forClass(Key[].class);
        verify(client).get(any(BatchPolicy.class), keysCaptor.capture());
        assertEquals(24, keysCaptor.getValue().length);
        assertEquals(3L, derived.features().get("pan_txn_count_1h"));
        assertEquals(3L, derived.features().get("pan_txn_count_24h"));
        assertEquals(5.0, derived.features().get("pan_amount_sum_24h"));
        assertFalse(derived.storeUnavailable());
    }

    @Test
    void deriveFeaturesMarksStoreUnavailableOnBatchFailure() {
        AerospikeClient client = mock(AerospikeClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.get(any(BatchPolicy.class), any(Key[].class)))
                .thenThrow(new RuntimeException("cluster down"));

        AerospikeFeatureStore store = new AerospikeFeatureStore(client, "hokeka", "site-a");
        FeatureDerivation derived = store.deriveFeaturesDetailed("abc123");

        assertTrue(derived.storeUnavailable());
        assertTrue(derived.features().isEmpty());
    }

    /** The 24 keys of one read are the local site's hourly buckets, newest first. */
    @Test
    void deriveFeaturesReadsOnlyTheLocalSitesBuckets() {
        AerospikeClient client = mock(AerospikeClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.get(any(BatchPolicy.class), any(Key[].class))).thenReturn(new Record[24]);

        new AerospikeFeatureStore(client, "hokeka", "site-a").deriveFeaturesDetailed("abc123");

        ArgumentCaptor<Key[]> keys = ArgumentCaptor.forClass(Key[].class);
        verify(client).get(any(BatchPolicy.class), keys.capture());
        Instant now = Instant.now();
        for (int hoursBack = 0; hoursBack < 24; hoursBack++) {
            String userKey = keys.getValue()[hoursBack].userKey.toString();
            assertTrue(userKey.matches("abc123:\\d{10}:site-a"), userKey);
            assertEquals("velocity", keys.getValue()[hoursBack].setName);
        }
        // Newest bucket is the current hour (allow for the read straddling an hour boundary).
        String newest = keys.getValue()[0].userKey.toString();
        assertTrue(newest.equals(AerospikeFeatureStore.velocityUserKey("abc123", now, "site-a"))
                        || newest.equals(AerospikeFeatureStore.velocityUserKey("abc123", now.minusSeconds(3600), "site-a")),
                newest);
    }

    /** The contract key format: {@code <pan>:<yyyyMMddHH UTC>:<site>}. */
    @Test
    void velocityUserKeyIsPanHourSite() {
        assertEquals("abc123:2026101709:nbo-dc1",
                AerospikeFeatureStore.velocityUserKey("abc123", Instant.parse("2026-10-17T09:59:59Z"), "nbo-dc1"));
    }

    /**
     * Two sites sharing nothing but a card: activity on site A is invisible to site B (no XDR), while
     * a second node of site A (same site id, same cluster) sees it.
     */
    @Test
    void countersAreScopedToTheSite() {
        InMemoryAerospike memory = new InMemoryAerospike();
        AerospikeFeatureStore siteANode1 = new AerospikeFeatureStore(memory.client, "hokeka", "site-a");
        AerospikeFeatureStore siteANode2 = new AerospikeFeatureStore(memory.client, "hokeka", "site-a");
        AerospikeFeatureStore siteB = new AerospikeFeatureStore(memory.client, "hokeka", "site-b");

        siteANode1.recordVelocity("card-1", 1000L);
        siteANode1.recordVelocity("card-1", 2500L);
        siteANode2.recordVelocity("card-1", 500L);
        siteB.recordVelocity("card-1", 9900L);

        FeatureDerivation a = siteANode2.deriveFeaturesDetailed("card-1");
        assertEquals(3L, a.features().get("pan_txn_count_1h"));
        assertEquals(3L, a.features().get("pan_txn_count_24h"));
        assertEquals(40.0, a.features().get("pan_amount_sum_24h"));

        FeatureDerivation b = siteB.deriveFeaturesDetailed("card-1");
        assertEquals(1L, b.features().get("pan_txn_count_24h"));
        assertEquals(99.0, b.features().get("pan_amount_sum_24h"));

        FeatureDerivation c = new AerospikeFeatureStore(memory.client, "hokeka", "site-c").deriveFeaturesDetailed("card-1");
        assertEquals(0L, c.features().get("pan_txn_count_24h"), "a site with no activity sees none");
        assertTrue(memory.keys().stream().allMatch(k -> k.endsWith(":site-a") || k.endsWith(":site-b")),
                "every bucket is site-scoped: " + memory.keys());
    }

    /** A store without a site id cannot scope its keys and is refused. */
    @Test
    void blankSiteIdIsRefused() {
        AerospikeClient client = mock(AerospikeClient.class);
        assertThrows(IllegalArgumentException.class, () -> new AerospikeFeatureStore(client, "hokeka", " "));
        assertThrows(IllegalArgumentException.class, () -> new AerospikeFeatureStore(client, "hokeka", null));
        assertEquals("site-a", new AerospikeFeatureStore(client, "hokeka", " site-a ").siteId());
    }

    /**
     * Deliberate test double (intentional-stub: stands in for an Aerospike cluster): a mocked client
     * whose {@code operate} applies {@code Operation.add} to an in-memory map and whose batch
     * {@code get} reads it back, so several stores can share one "cluster".
     */
    private static final class InMemoryAerospike {
        final Map<String, Map<String, Object>> rows = new ConcurrentHashMap<>();
        final AerospikeClient client = mock(AerospikeClient.class);

        /** Wires the mock's operate / batch get / isConnected onto {@link #rows}. */
        InMemoryAerospike() {
            when(client.isConnected()).thenReturn(true);
            when(client.operate(any(WritePolicy.class), any(Key.class), any(Operation[].class))).thenAnswer(inv -> {
                Key key = inv.getArgument(1);
                Map<String, Object> bins = rows.computeIfAbsent(key.setName + "/" + key.userKey,
                        k -> new ConcurrentHashMap<>());
                for (int i = 2; i < inv.getArguments().length; i++) {
                    Object arg = inv.getArguments()[i];
                    Operation[] ops = arg instanceof Operation[] arr ? arr : new Operation[]{(Operation) arg};
                    for (Operation op : ops) {
                        bins.merge(op.binName, op.value.toLong(), (x, y) -> (Long) x + (Long) y);
                    }
                }
                return null;
            });
            when(client.get(any(BatchPolicy.class), any(Key[].class))).thenAnswer(inv -> {
                Key[] keys = inv.getArgument(1);
                Record[] out = new Record[keys.length];
                for (int i = 0; i < keys.length; i++) {
                    Map<String, Object> bins = rows.get(keys[i].setName + "/" + keys[i].userKey);
                    out[i] = bins == null ? null : new Record(new HashMap<>(bins), 1, 0);
                }
                return out;
            });
        }

        /** User keys of every stored bucket. */
        List<String> keys() {
            return rows.keySet().stream().map(k -> k.substring(k.indexOf('/') + 1)).toList();
        }
    }

    /** A mocked record with the given counter bins. */
    private Record record(long count, long amountCents) {
        Record record = mock(Record.class);
        when(record.getLong(AerospikeFeatureStore.BIN_COUNT)).thenReturn(count);
        when(record.getLong(AerospikeFeatureStore.BIN_AMOUNT)).thenReturn(amountCents);
        return record;
    }
}
