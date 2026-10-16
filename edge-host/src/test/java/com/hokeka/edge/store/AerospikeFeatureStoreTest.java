package com.hokeka.edge.store;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.BatchPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        AerospikeFeatureStore store = new AerospikeFeatureStore(client, "hokeka");

        store.recordVelocity("abc123", 1250L);
        store.recordVelocity("", 99L);
        store.recordVelocity(null, 99L);

        ArgumentCaptor<Key> key = ArgumentCaptor.forClass(Key.class);
        ArgumentCaptor<com.aerospike.client.policy.WritePolicy> policy =
                ArgumentCaptor.forClass(com.aerospike.client.policy.WritePolicy.class);
        verify(client, org.mockito.Mockito.times(1)).operate(policy.capture(), key.capture(),
                any(com.aerospike.client.Operation[].class));
        assertEquals("velocity", key.getValue().setName);
        assertTrue(key.getValue().userKey.toString().startsWith("abc123:"));
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

        AerospikeFeatureStore store = new AerospikeFeatureStore(client, "hokeka");
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

        AerospikeFeatureStore store = new AerospikeFeatureStore(client, "hokeka");
        FeatureDerivation derived = store.deriveFeaturesDetailed("abc123");

        assertTrue(derived.storeUnavailable());
        assertTrue(derived.features().isEmpty());
    }

    private Record record(long count, long amountCents) {
        Record record = mock(Record.class);
        when(record.getLong(AerospikeFeatureStore.BIN_COUNT)).thenReturn(count);
        when(record.getLong(AerospikeFeatureStore.BIN_AMOUNT)).thenReturn(amountCents);
        return record;
    }
}
