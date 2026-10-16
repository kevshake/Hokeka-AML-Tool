package com.posgateway.aml.service.edge.records;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.contracts.ContractFixtures;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Batch-level rules of POST /edge/assessments v2 (contract 4.1, 4.2): per-record codes, identity
 * and site checks, idempotency mapping and the acknowledgement shape (validated against
 * decision-record-ack-v2.schema.json).
 */
class DecisionRecordIngestServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private DecisionRecordStore store;
    private EdgeSiteResolver sites;
    private DecisionRecordIngestService service;
    private EdgeNode node;

    @BeforeEach
    void setUp() {
        store = mock(DecisionRecordStore.class);
        when(store.store(any(), any(), anyString())).thenReturn(DecisionRecordStore.Outcome.ACCEPTED);
        sites = mock(EdgeSiteResolver.class);
        service = new DecisionRecordIngestService(store, sites);
        node = new EdgeNode();
        node.setId(7L);
        node.setPspId(12L);
        node.setEdgeId("acme-eu-1");
        node.setStatus(EdgeNodeStatus.ACTIVE);
        when(sites.siteOf(node)).thenReturn("acme-eu");
    }

    @Test
    void siteResolverFallsBackToTheEdgeIdWithoutARegisteredSite() {
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        assertEquals("acme-eu-1", new EdgeSiteResolver(jdbc).siteOf(node), "no site_id column yet (before V263)");
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(7L))).thenReturn(java.util.Collections.singletonList(null));
        assertEquals("acme-eu-1", new EdgeSiteResolver(jdbc).siteOf(node), "single-node site");
        when(jdbc.queryForList(anyString(), eq(String.class), eq(7L))).thenReturn(List.of("acme-eu"));
        assertEquals("acme-eu", new EdgeSiteResolver(jdbc).siteOf(node), "registered site");
    }

    @Test
    void contractBatchFixtureIsAcceptedRecordByRecord() {
        JsonNode batch = ContractFixtures.fixture("decision-record-batch-v2--two-records.json");
        DecisionRecordIngestService.Ack ack = service.ingest(node, batch);
        assertEquals(2, ack.accepted());
        assertEquals(0, ack.duplicates());
        assertTrue(ack.rejected().isEmpty());
        verify(store, times(2)).store(eq(node), any(), anyString());
        assertAckConforms(ack);
    }

    @Test
    void everyInvalidRecordFixtureIsRejectedAndNeverStored() {
        for (String name : ContractFixtures.fixtureNames("decision-record-v2--")) {
            if (!name.endsWith(".invalid.json")) {
                continue;
            }
            DecisionRecordIngestService.Ack ack = service.ingest(node, batchOf(ContractFixtures.fixture(name)));
            assertEquals(0, ack.accepted(), name);
            assertEquals(1, ack.rejected().size(), name);
            assertTrue(Set.of("SCHEMA_INVALID", "FORBIDDEN_FIELD").contains(ack.rejected().get(0).code()), name);
            assertAckConforms(ack);
        }
        verify(store, never()).store(any(), any(), anyString());
    }

    @Test
    void recordFromAnotherNodeIsNodeMismatch() {
        ObjectNode record = (ObjectNode) ContractFixtures.fixture("decision-record-v2--alert.json");
        record.put("edgeNodeId", "acme-eu-2");
        DecisionRecordIngestService.Ack ack = service.ingest(node, batchOf(record));
        assertEquals("NODE_MISMATCH", ack.rejected().get(0).code());

        ObjectNode batch = batchOf(ContractFixtures.fixture("decision-record-v2--alert.json"));
        batch.put("edgeId", "someone-else");
        assertEquals("NODE_MISMATCH", service.ingest(node, batch).rejected().get(0).code());
        verify(store, never()).store(any(), any(), anyString());
    }

    @Test
    void recordForAnotherSiteIsUnknownSite() {
        ObjectNode record = (ObjectNode) ContractFixtures.fixture("decision-record-v2--alert.json");
        record.put("siteId", "acme-us");
        DecisionRecordIngestService.Ack ack = service.ingest(node, batchOf(record));
        assertEquals("UNKNOWN_SITE", ack.rejected().get(0).code());
    }

    @Test
    void singleNodeSiteDefaultsToTheEdgeId() {
        when(sites.siteOf(node)).thenReturn("acme-eu-1");
        ObjectNode record = (ObjectNode) ContractFixtures.fixture("decision-record-v2--alert.json");
        record.put("siteId", "acme-eu-1");
        ObjectNode batch = batchOf(record);
        batch.put("siteId", "acme-eu-1");
        assertEquals(1, service.ingest(node, batch).accepted());
    }

    @Test
    void duplicatesAndConflictsAreReportedPerRecord() {
        when(store.store(any(), any(), anyString()))
                .thenReturn(DecisionRecordStore.Outcome.DUPLICATE)
                .thenReturn(DecisionRecordStore.Outcome.CONFLICT);
        DecisionRecordIngestService.Ack ack =
                service.ingest(node, ContractFixtures.fixture("decision-record-batch-v2--two-records.json"));
        assertEquals(0, ack.accepted());
        assertEquals(1, ack.duplicates());
        assertEquals("CONFLICT", ack.rejected().get(0).code());
        assertEquals("c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f", ack.rejected().get(0).edgeAssessmentId());
        assertAckConforms(ack);
    }

    @Test
    void aLostInsertRaceIsReclassified() {
        when(store.store(any(), any(), anyString())).thenThrow(new DataIntegrityViolationException("dup"));
        when(store.classifyExisting(any(), any(), anyString())).thenReturn(DecisionRecordStore.Outcome.DUPLICATE);
        DecisionRecordIngestService.Ack ack =
                service.ingest(node, batchOf(ContractFixtures.fixture("decision-record-v2--alert.json")));
        assertEquals(1, ack.duplicates());
    }

    @Test
    void identicalRecordsHaveTheSameDigestRegardlessOfMemberOrder() {
        JsonNode record = ContractFixtures.fixture("decision-record-v2--alert.json");
        ObjectNode reordered = mapper.createObjectNode();
        List<String> names = new java.util.ArrayList<>();
        record.fieldNames().forEachRemaining(names::add);
        java.util.Collections.reverse(names);
        names.forEach(n -> reordered.set(n, record.get(n)));
        assertEquals(com.posgateway.aml.service.residency.CanonicalJson.sha256Hex(record),
                com.posgateway.aml.service.residency.CanonicalJson.sha256Hex(reordered));
    }

    @Test
    void wrapperProblemsAndUnacknowledgeableRecordsAreBatchErrors() {
        ObjectNode notV2 = (ObjectNode) ContractFixtures.fixture("decision-record-batch-v2--two-records.json");
        notV2.put("v", 1);
        assertThrows(DecisionRecordIngestService.InvalidBatchException.class, () -> service.ingest(node, notV2));

        ObjectNode noId = (ObjectNode) ContractFixtures.fixture("decision-record-v2--alert.json");
        noId.remove("edgeAssessmentId");
        assertThrows(DecisionRecordIngestService.InvalidBatchException.class, () -> service.ingest(node, batchOf(noId)));
        verify(store, never()).store(any(), any(), anyString());
    }

    /** A one-record batch for the test node. */
    private ObjectNode batchOf(JsonNode record) {
        ObjectNode batch = mapper.createObjectNode();
        batch.put("v", 2);
        batch.put("edgeId", "acme-eu-1");
        batch.put("siteId", "acme-eu");
        ArrayNode records = batch.putArray("records");
        records.add(record);
        return batch;
    }

    /** The acknowledgement conforms to decision-record-ack-v2.schema.json. */
    private void assertAckConforms(DecisionRecordIngestService.Ack ack) {
        Set<String> errors = ContractFixtures.validate("decision-record-ack-v2.schema.json", mapper.valueToTree(ack));
        assertTrue(errors.isEmpty(), "ack conforms to the contract: " + errors);
    }
}
