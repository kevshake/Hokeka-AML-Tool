package com.hokeka.edge.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeController;
import com.hokeka.edge.EdgeEvaluation;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.activation.EdgeIdentity;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.channel.OutboxShipper;
import com.hokeka.edge.channel.ReplayGuard;
import com.hokeka.edge.channel.SealedEnvelopeCodec;
import com.hokeka.edge.channel.SecureChannel;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.crypto.EdgeKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The durable outbox shipper against a real PostgreSQL ledger and a contract-conformant fake
 * Control Plane ({@link FakeControlPlane}): sealing per context, batch schema, ≤500 per batch and
 * payload-cap sizing, ack handling (accepted / duplicates / rejected), retries with backoff, the
 * 401/403 lifecycle, and crash/replay — records written before a crash, or sent but not
 * acknowledged, are delivered after a restart.
 */
class OutboxShipperIntegrationTest {

    private static final String EDGE_ID = "acme-eu-1";

    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractSchemas schemas =
            ContractSchemas.fromDirectory(Path.of(System.getProperty("contracts.dir", "../docs/contracts")));
    private final KeyPair controlPlaneX25519 = EdgeKeys.generateX25519();
    private final KeyPair edgeEd25519 = EdgeKeys.generateEd25519();
    private final MutableClock clock = new MutableClock(Instant.now());

    private String schema;
    private LedgerProperties ledgerProperties;
    private EdgeLedger ledger;
    private FakeControlPlane cloud;
    private ActivationService activation;

    /** A clock tests can move forward to step through the backoff. */
    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void open() throws Exception {
        schema = LedgerTestDatabase.newSchema();
        ledgerProperties = LedgerTestDatabase.properties(schema);
        ledger = LedgerTestDatabase.openLedger(ledgerProperties, EDGE_ID);
        cloud = new FakeControlPlane(controlPlaneX25519, edgeEd25519.getPublic(), EDGE_ID, schemas);
        activation = mock(ActivationService.class);
        when(activation.authorized()).thenReturn(true);
        when(activation.loadOrCreateIdentity()).thenReturn(
                new EdgeIdentity(EDGE_ID, "42", "", "", "", "", "", EdgeIdentity.STATUS_ACTIVE));
        when(activation.ed25519Private()).thenReturn(edgeEd25519.getPrivate());
    }

    @AfterEach
    void close() {
        if (cloud != null) {
            cloud.close();
        }
        if (ledger != null) {
            ledger.close();
        }
        if (schema != null) {
            LedgerTestDatabase.dropSchema(schema);
        }
    }

    private OutboxShipper shipper(EdgeLedger over) {
        ControlPlaneProperties props = new ControlPlaneProperties();
        props.setEnabled(true);
        props.setEdgeId(EDGE_ID);
        props.setEncryptionPublicKey(EdgeKeys.encodePublic(controlPlaneX25519.getPublic()));
        SecureChannel channel = mock(SecureChannel.class);
        when(channel.mutualTlsClient()).thenAnswer(inv -> RestClient.builder().baseUrl(cloud.baseUrl()).build());
        return new OutboxShipper(props, ledgerProperties, channel, new SealedEnvelopeCodec(mapper, new ReplayGuard()),
                activation, over, schemas, mapper, clock);
    }

    /** Evaluates {@code n} transactions through the real controller and returns their ids. */
    private List<String> evaluate(EdgeLedger over, int n, String prefix) {
        EdgeController controller = EdgeLedgerIntegrationTest.controller(new EvaluationRecorder(over, schemas, mapper));
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            EdgeEvaluation e = (EdgeEvaluation) controller.evaluate(
                    EdgeLedgerIntegrationTest.txn(prefix + "-" + i, i % 3 == 0)).getBody();
            ids.add(e.edgeAssessmentId());
        }
        return ids;
    }

    @Test
    void shipsSealedSchemaValidBatchesInSeqOrderAndMarksThemShipped() throws Exception {
        List<String> ids = evaluate(ledger, 7, "OK");
        OutboxShipper shipper = shipper(ledger);

        assertEquals(OutboxShipper.RunOutcome.DRAINED, shipper.ship());

        assertEquals(List.of(), cloud.problems);
        assertEquals(1, cloud.requests.size(), "7 records fit one batch");
        List<String> order = cloud.received.stream().map(r -> r.path("edgeAssessmentId").asText()).toList();
        assertEquals(ids, order, "records are delivered in outbox seq order");
        for (JsonNode record : cloud.received) {
            assertEquals(List.of(), schemas.validate(ContractSchemas.DECISION_RECORD_V2, record));
            String json = record.toString();
            assertTrue(!json.contains("9f2c1d7a") && !json.contains("125000") && !json.contains("KES"),
                    "no transaction data leaves the edge");
        }
        assertEquals(0, ledger.outboxBacklog());
        assertEquals(7, shipper.shipped());
        assertEquals(OutboxShipper.RunOutcome.DRAINED, shipper.ship(), "nothing left to send");
        assertEquals(1, cloud.requests.size());
    }

    @Test
    void recordsWrittenBeforeACrashAreDeliveredAfterRestart() throws Exception {
        List<String> ids = evaluate(ledger, 12, "CRASH");
        // "Crash": the node dies with 12 records in the outbox and nothing shipped.
        ledger.close();

        ledger = LedgerTestDatabase.openLedger(ledgerProperties, EDGE_ID);
        assertEquals(12, ledger.outboxBacklog(), "the outbox survived the restart");
        assertEquals(OutboxShipper.RunOutcome.DRAINED, shipper(ledger).ship());

        assertEquals(new HashSet<>(ids), cloud.receivedIds());
        assertEquals(0, ledger.outboxBacklog());
        assertEquals(List.of(), cloud.problems);
    }

    @Test
    void aSendWhoseAckWasLostIsReplayedAfterRestartAndCountedAsDuplicates() throws Exception {
        List<String> ids = evaluate(ledger, 5, "REPLAY");
        // The cloud stores the batch but the acknowledgement never arrives (500 after processing is
        // indistinguishable for the edge from a lost response).
        OutboxShipper first = shipper(ledger);
        cloud.scriptedStatuses.add(503);
        assertEquals(OutboxShipper.RunOutcome.BACKING_OFF, first.ship());
        assertEquals(5, ledger.outboxBacklog(), "nothing is marked shipped without a 200");
        assertEquals(Set.of("HTTP_503"), lastErrors());

        // Deliver once successfully so the cloud has the records, then simulate the lost-ack case:
        // mark nothing, crash, restart, re-send.
        clock.advance(Duration.ofSeconds(2));
        ledgerMarkUnshippedAfter(() -> assertEquals(OutboxShipper.RunOutcome.DRAINED, first.ship()));
        assertEquals(5, ledger.outboxBacklog(), "simulated lost ack: still pending");
        ledger.close();

        ledger = LedgerTestDatabase.openLedger(ledgerProperties, EDGE_ID);
        OutboxShipper restarted = shipper(ledger);
        assertEquals(OutboxShipper.RunOutcome.DRAINED, restarted.ship());

        assertEquals(0, ledger.outboxBacklog());
        assertEquals(5, restarted.duplicates(), "the cloud recognised byte-identical re-sends");
        for (String id : ids) {
            List<JsonNode> copies = cloud.receivedFor(id);
            assertEquals(2, copies.size());
            assertEquals(copies.get(0), copies.get(1), "a replayed record is identical to the first send");
        }
        assertEquals(List.of(), cloud.problems);
    }

    @Test
    void rejectedRecordsAreMarkedWithTheirCodeAndNotRetried() throws Exception {
        List<String> ids = evaluate(ledger, 3, "REJ");
        cloud.rejectIds.put(ids.get(1), "FORBIDDEN_FIELD");
        OutboxShipper shipper = shipper(ledger);

        assertEquals(OutboxShipper.RunOutcome.DRAINED, shipper.ship());
        assertEquals(1, shipper.rejected());
        assertEquals(0, ledger.outboxBacklog());
        try (Connection c = LedgerTestDatabase.connect(schema); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT edge_assessment_id::text, reject_code FROM edge_outbox "
                     + "WHERE reject_code IS NOT NULL")) {
            assertTrue(rs.next());
            assertEquals(ids.get(1), rs.getString(1));
            assertEquals("FORBIDDEN_FIELD", rs.getString(2));
        }
        shipper.ship();
        assertEquals(3, cloud.received.size(), "a rejected record is not re-sent");
    }

    @Test
    void failuresBackOffWithJitterAndRecoverAfterTheWait() throws Exception {
        evaluate(ledger, 2, "BO");
        OutboxShipper shipper = shipper(ledger);
        cloud.scriptedStatuses.add(500);
        cloud.scriptedStatuses.add(500);

        assertEquals(OutboxShipper.RunOutcome.BACKING_OFF, shipper.ship());
        Duration firstWait = Duration.between(clock.instant(), shipper.nextAttemptAt());
        assertTrue(!firstWait.isNegative() && firstWait.compareTo(Duration.ofSeconds(1)) <= 0,
                "first backoff is within 1 s: " + firstWait);
        assertEquals(OutboxShipper.RunOutcome.SKIPPED, shipper.ship(), "no attempt before the backoff elapses");

        clock.advance(Duration.ofSeconds(1));
        assertEquals(OutboxShipper.RunOutcome.BACKING_OFF, shipper.ship());
        Duration secondWait = Duration.between(clock.instant(), shipper.nextAttemptAt());
        assertTrue(secondWait.compareTo(Duration.ofSeconds(1)) >= 0 && secondWait.compareTo(Duration.ofSeconds(2)) <= 0,
                "second backoff is in [1 s, 2 s]: " + secondWait);

        clock.advance(Duration.ofSeconds(2));
        assertEquals(OutboxShipper.RunOutcome.DRAINED, shipper.ship());
        assertEquals(0, ledger.outboxBacklog());
        assertEquals(2, shipper.failedAttempts());
        assertEquals(Instant.EPOCH, shipper.nextAttemptAt(), "backoff resets after success");
    }

    @Test
    void backoffIsCappedAtSixtySeconds() {
        evaluate(ledger, 1, "CAP");
        OutboxShipper shipper = shipper(ledger);
        for (int i = 0; i < 12; i++) {
            cloud.scriptedStatuses.add(502);
            shipper.ship();
            Duration wait = Duration.between(clock.instant(), shipper.nextAttemptAt());
            assertTrue(wait.compareTo(Duration.ofSeconds(60)) <= 0, "attempt " + i + " waited " + wait);
            clock.advance(wait.plusMillis(1));
        }
    }

    @Test
    void unauthorisedNodeStopsShippingAndKeepsTheOutbox() throws Exception {
        evaluate(ledger, 2, "AUTH");
        cloud.scriptedStatuses.add(403);
        assertEquals(OutboxShipper.RunOutcome.UNAUTHORIZED, shipper(ledger).ship());
        verify(activation).markUnauthorized(anyString());
        assertEquals(2, ledger.outboxBacklog());
    }

    @Test
    void tooLargeBatchesAreHalvedAndPayloadCapIsRespected() throws Exception {
        evaluate(ledger, 20, "SIZE");
        cloud.maxRecordsPerBatch = 6;
        ledgerProperties.setOutboxMaxPayloadBytes(4_000);
        OutboxShipper shipper = shipper(ledger);

        assertEquals(OutboxShipper.RunOutcome.DRAINED, shipper.ship());
        assertEquals(0, ledger.outboxBacklog());
        assertEquals(20, cloud.store.size());
        for (int[] req : cloud.requests) {
            if (req[1] <= 6) {
                assertTrue(req[0] <= 4_000 || req[1] == 1, "sealed batch of " + req[1] + " is " + req[0] + " bytes");
            }
        }
        assertEquals(List.of(), cloud.problems);
    }

    @Test
    void batchesNeverExceedFiveHundredRecords() throws Exception {
        evaluate(ledger, 520, "BIG");
        ledgerProperties.setOutboxBatchSize(10_000);
        ledgerProperties.setOutboxMaxPayloadBytes(4 * 1024 * 1024);
        assertEquals(OutboxShipper.RunOutcome.DRAINED, shipper(ledger).ship());
        assertEquals(2, cloud.requests.size());
        assertEquals(500, cloud.requests.get(0)[1]);
        assertEquals(20, cloud.requests.get(1)[1]);
        assertEquals(520, cloud.store.size());
    }

    @Test
    void contractAckFixtureIsAcceptedByTheShipperParser() throws Exception {
        JsonNode fixture = mapper.readTree(Files.readString(Path.of(System.getProperty("contracts.dir",
                "../docs/contracts")).resolve("tunnel/examples/decision-record-ack-v2--partial.json")));
        assertEquals(List.of(), schemas.validate(ContractSchemas.DECISION_RECORD_ACK_V2, fixture));
        // The fixture's rejected id is not in this batch, so the shipper must refuse it as an ack.
        List<EdgeLedger.OutboxRow> rows = List.of(new EdgeLedger.OutboxRow(1, java.util.UUID.randomUUID(), "{}"));
        assertEquals(null, shipper(ledger).parseAck(fixture.toString(), rows));
    }

    /** Runs a successful ship and then clears shipped_at again, i.e. "the ack was lost". */
    private void ledgerMarkUnshippedAfter(Runnable shipping) throws Exception {
        shipping.run();
        try (Connection c = LedgerTestDatabase.connect(schema); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE edge_outbox SET shipped_at = NULL");
        }
    }

    private Set<String> lastErrors() throws Exception {
        Set<String> out = new HashSet<>();
        try (Connection c = LedgerTestDatabase.connect(schema); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT last_error FROM edge_outbox")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
