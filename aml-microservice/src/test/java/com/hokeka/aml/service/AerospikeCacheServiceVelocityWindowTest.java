package com.hokeka.aml.service;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Record;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.WritePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W49-13: velocity counters must live in per-window buckets (bounded TTL) instead of a single
 * record whose TTL is refreshed on every write.
 */
class AerospikeCacheServiceVelocityWindowTest {

    private static final long WINDOW_MS = 3_600_000L;
    // 10:15 into bucket 500_000 (a quarter of the window elapsed).
    private static final long NOW_MS = 500_000L * WINDOW_MS + WINDOW_MS / 4;

    private AerospikeClient client;
    private AerospikeCacheService service;

    @BeforeEach
    void setUp() {
        client = mock(AerospikeClient.class);
        when(client.isConnected()).thenReturn(true);
        service = new AerospikeCacheService();
        ReflectionTestUtils.setField(service, "aerospikeClient", client);
        ReflectionTestUtils.setField(service, "namespace", "aml_cache");
        ReflectionTestUtils.setField(service, "velocityWindowSeconds", 3600L);
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.ofEpochMilli(NOW_MS), ZoneOffset.UTC));
    }

    @Test
    void writeGoesToTimeBucketWithBoundedTtl() {
        service.recordVelocity("cust-1", Map.of("tsMs", NOW_MS, "amountCents", 1250L));

        ArgumentCaptor<WritePolicy> policy = ArgumentCaptor.forClass(WritePolicy.class);
        ArgumentCaptor<Key> key = ArgumentCaptor.forClass(Key.class);
        verify(client).operate(policy.capture(), key.capture(), any(Operation[].class));

        assertEquals("cust-1|500000", key.getValue().userKey.toString());
        assertEquals("velocity", key.getValue().setName);
        // Two windows, never the old 7-day sliding TTL.
        assertEquals(7200, policy.getValue().expiration);
    }

    @Test
    void eventsInDifferentWindowsLandInDifferentBuckets() {
        service.recordVelocity("cust-1", Map.of("tsMs", NOW_MS - WINDOW_MS));

        ArgumentCaptor<Key> key = ArgumentCaptor.forClass(Key.class);
        verify(client).operate(any(WritePolicy.class), key.capture(), any(Operation[].class));
        assertEquals("cust-1|499999", key.getValue().userKey.toString());
    }

    @Test
    void readReturnsCurrentWindowAndWeightedSlidingEstimate() {
        when(client.get(isNull(Policy.class), eq(new Key("aml_cache", "velocity", "cust-1|500000"))))
                .thenReturn(record(4L, 4000L, NOW_MS - 1000));
        when(client.get(isNull(Policy.class), eq(new Key("aml_cache", "velocity", "cust-1|499999"))))
                .thenReturn(record(8L, 8000L, NOW_MS - WINDOW_MS));

        Map<String, Object> v = service.getVelocity("cust-1");

        assertEquals(4L, v.get("txn_count"));
        assertEquals(4000L, v.get("total_amount"));
        // 25% of the current window elapsed -> 75% of the previous bucket still in the trailing window.
        assertEquals(10L, v.get("sliding_txn_count"));
        assertEquals(10000L, v.get("sliding_total_amount"));
        assertEquals(WINDOW_MS, v.get("window_ms"));
        assertEquals(500_000L * WINDOW_MS, v.get("window_start_ms"));
        assertEquals(NOW_MS - 1000, v.get("last_ts_ms"));
    }

    @Test
    void quietCustomerOutsideBothWindowsReturnsNull() {
        assertNull(service.getVelocity("cust-1"));
    }

    @Test
    void onlyPreviousBucketStillCountsInSlidingEstimate() {
        when(client.get(isNull(Policy.class), eq(new Key("aml_cache", "velocity", "cust-1|499999"))))
                .thenReturn(record(4L, 400L, NOW_MS - WINDOW_MS));

        Map<String, Object> v = service.getVelocity("cust-1");

        assertEquals(0L, v.get("txn_count"));
        assertEquals(3L, v.get("sliding_txn_count"));
        assertEquals(300L, v.get("sliding_total_amount"));
    }

    private static Record record(long count, long amount, long lastTs) {
        Map<String, Object> bins = new HashMap<>();
        bins.put("txn_count", count);
        bins.put("total_amount", amount);
        bins.put("last_ts_ms", lastTs);
        return new Record(bins, 1, 7200);
    }
}
