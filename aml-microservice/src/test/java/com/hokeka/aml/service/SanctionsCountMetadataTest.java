package com.hokeka.aml.service;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.cluster.Node;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sanctions count must come from O(1) set metadata, not a full-set query/scan, so the Control
 * Plane's health and startup checks get an answer within their timeout.
 */
class SanctionsCountMetadataTest {

    private SanctionsService serviceWith(AerospikeClient client) {
        SanctionsService service = new SanctionsService();
        ReflectionTestUtils.setField(service, "aerospikeClient", client);
        ReflectionTestUtils.setField(service, "useSearchIndex", true);
        return service;
    }

    @Test
    void singleNodeCountUsesSetObjectsWithoutScanning() {
        AerospikeClient client = mock(AerospikeClient.class);
        Node node = mock(Node.class);
        when(client.isConnected()).thenReturn(true);
        when(client.getNodes()).thenReturn(new Node[]{node});
        SanctionsService service = serviceWith(client);
        service.infoRequester = (n, cmd) -> cmd.startsWith("sets/")
                ? "objects=6:tombstones=0:data_used_bytes=4096:truncate_lut=0"
                : "ns_cluster_size=1;effective_replication_factor=1;replication-factor=2";

        assertEquals(6L, service.count());
        verify(client, never()).query(any(), any(), any());
        verify(client, never()).scanAll(any(), any(), any(), any());
    }

    @Test
    void multiNodeCountDividesByEffectiveReplicationFactor() {
        AerospikeClient client = mock(AerospikeClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.getNodes()).thenReturn(new Node[]{mock(Node.class), mock(Node.class), mock(Node.class)});
        SanctionsService service = serviceWith(client);
        // 1000 records, RF 2 -> 2000 copies spread over three nodes.
        long[] perNode = {700, 650, 650};
        int[] i = {0};
        service.infoRequester = (n, cmd) -> cmd.startsWith("sets/")
                ? "objects=" + perNode[i[0]++] + ":tombstones=0"
                : "effective_replication_factor=2;replication-factor=2";

        assertEquals(1000L, service.count());
    }

    @Test
    void missingSetCountsAsEmpty() {
        AerospikeClient client = mock(AerospikeClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.getNodes()).thenReturn(new Node[]{mock(Node.class)});
        SanctionsService service = serviceWith(client);
        service.infoRequester = (n, cmd) -> "";

        assertEquals(0L, service.count());
    }

    @Test
    void infoFailureFallsBackToScanAndDisconnectedIsUnavailable() {
        AerospikeClient client = mock(AerospikeClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.getNodes()).thenReturn(new Node[]{mock(Node.class)});
        SanctionsService service = serviceWith(client);
        ReflectionTestUtils.setField(service, "useSearchIndex", false);
        service.infoRequester = (n, cmd) -> { throw new IllegalStateException("info refused"); };

        assertEquals(0L, service.count()); // scan (mocked, no records) answered
        verify(client).scanAll(any(), any(), any(), any());

        assertEquals(-1L, new SanctionsService().count());
    }

    @Test
    void parseInfoLongHandlesSeparatorsAndGarbage() {
        assertEquals(6L, SanctionsService.parseInfoLong("objects=6:tombstones=0", "objects", ":"));
        assertEquals(2L, SanctionsService.parseInfoLong("a=1;replication-factor=2", "replication-factor", ";"));
        assertEquals(-1L, SanctionsService.parseInfoLong("objects=x", "objects", ":"));
        assertEquals(-1L, SanctionsService.parseInfoLong(null, "objects", ":"));
    }
}
