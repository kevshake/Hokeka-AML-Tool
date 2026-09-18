package com.aeroorm;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AeroRepositoryTest {

    private AerospikeClient client;

    @BeforeEach
    void setUp() {
        client = mock(AerospikeClient.class);
        when(client.isConnected()).thenReturn(true);
    }

    @AeroEntity(set = "risk_profile")
    static class SampleProfile {
        @AeroKey
        String customerId;
        @AeroBin("score")
        Long score;
    }

    @Test
    void saveMapWritesBinsWithTtl() {
        AeroRepository<SampleProfile> repo = new AeroRepository<>(client, "aml_cache", SampleProfile.class);
        repo.saveMap("42", Map.of("score", 88L, "tier", "LOW"), 1800);

        ArgumentCaptor<WritePolicy> wp = ArgumentCaptor.forClass(WritePolicy.class);
        ArgumentCaptor<Key> key = ArgumentCaptor.forClass(Key.class);
        ArgumentCaptor<Bin[]> bins = ArgumentCaptor.forClass(Bin[].class);
        verify(client).put(wp.capture(), key.capture(), bins.capture());
        assertEquals(1800, wp.getValue().expiration);
        assertEquals("42", key.getValue().userKey.toString());
        assertEquals(2, bins.getValue().length);
    }

    @Test
    void findMapReturnsStoredBins() {
        Record record = new Record(Map.of("score", 77L), 0, 0);
        when(client.get(isNull(), any(Key.class))).thenReturn(record);

        AeroRepository<SampleProfile> repo = new AeroRepository<>(client, "aml_cache", SampleProfile.class);
        Map<String, Object> found = repo.findMap("99");
        assertNotNull(found);
        assertEquals(77L, found.get("score"));
    }
}
