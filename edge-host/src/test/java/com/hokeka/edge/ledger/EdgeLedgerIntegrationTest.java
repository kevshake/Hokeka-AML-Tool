package com.hokeka.edge.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeController;
import com.hokeka.edge.EdgeEngine;
import com.hokeka.edge.EdgeEvaluation;
import com.hokeka.edge.EdgeRuleInterpreter;
import com.hokeka.edge.activation.AuthorizationGate;
import com.hokeka.edge.channel.EdgeMetricsAggregator;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.store.NoOpFeatureStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Edge Ledger against a real PostgreSQL 16 (see {@link LedgerTestDatabase}): schema and
 * partitions, the one-transaction evaluation + outbox write, hygiene at rest, the Luhn 422, the
 * txn_ref index, and the {@code ledger.fail-closed} HOLD with in-memory retry.
 */
class EdgeLedgerIntegrationTest {

    static final String BUNDLE = """
        { "version": 37, "psp_id": 42, "rules": [
          { "id": 100, "name": "VPN hold",
            "condition": { "type": "cmp", "field": "ip_vpn_or_proxy", "op": "EQ", "value": true },
            "action": "HOLD", "score": 25, "priority": 5 } ] }""";

    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractSchemas schemas =
            ContractSchemas.fromDirectory(Path.of(System.getProperty("contracts.dir", "../docs/contracts")));

    private String schema;
    private EdgeLedger ledger;

    @BeforeEach
    void open() {
        schema = LedgerTestDatabase.newSchema();
        ledger = LedgerTestDatabase.openLedger(LedgerTestDatabase.properties(schema), "acme-eu-1");
    }

    @AfterEach
    void close() {
        if (ledger != null) {
            ledger.close();
        }
        if (schema != null) {
            LedgerTestDatabase.dropSchema(schema);
        }
    }

    /** A controller over a real Java-interpreter engine with the test bundle, and the given recorder. */
    static EdgeController controller(EvaluationRecorder recorder) {
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized());
        engine.loadPlainBundle(BUNDLE.getBytes(StandardCharsets.UTF_8));
        EdgeController controller = new EdgeController(engine, new EdgeMetricsAggregator("42", "acme-eu-1", 64, 1_000L),
                new NoOpFeatureStore(), recorder, "acme-eu-1", "acme-eu");
        ReflectionTestUtils.setField(controller, "failClosedOnStoreUnavailable", false);
        return controller;
    }

    static Map<String, Object> txn(String txnId, boolean vpn) {
        Map<String, Object> f = new LinkedHashMap<>();
        if (txnId != null) {
            f.put("txn_id", txnId);
        }
        f.put("pan_hash", "9f2c1d7a");
        f.put("pan", "4111111111111111");
        f.put("cvv", "123");
        f.put("track2", ";4111111111111111=2912");
        f.put("amount_cents", 125000);
        f.put("currency", "KES");
        f.put("merchant_id", "mrc_00421");
        f.put("ip_vpn_or_proxy", vpn);
        return f;
    }

    @Test
    void migrationCreatesEveryContractTableAndARollingPartitionWindow() throws Exception {
        try (Connection c = LedgerTestDatabase.connect(schema); Statement st = c.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("""
                    SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE n.nspname = current_schema() AND c.relkind IN ('r', 'p') AND NOT c.relispartition
                     ORDER BY 1""")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            for (String t : List.of("edge_evaluations", "edge_outbox", "edge_legal_holds", "edge_held_evaluations",
                    "edge_config", "edge_purge_log", "edge_access_log", "edge_txn_refs")) {
                assertTrue(tables.contains(t), "missing table " + t + " in " + tables);
            }
            try (ResultSet rs = st.executeQuery("""
                    SELECT count(*) FROM pg_inherits i JOIN pg_class p ON p.oid = i.inhparent
                     WHERE p.relname = 'edge_evaluations'""")) {
                rs.next();
                assertEquals(4, rs.getInt(1), "current month + 3 ahead");
            }
            try (ResultSet rs = st.executeQuery("SELECT relkind FROM pg_class WHERE relname = 'edge_evaluations'"
                    + " AND relnamespace = current_schema()::regnamespace")) {
                rs.next();
                assertEquals("p", rs.getString(1), "edge_evaluations is partitioned");
            }
        }
        assertEquals(0, ledger.ensurePartitions(Instant.now()), "partition creation is idempotent");
        assertEquals(1, ledger.schemaVersion());
        // A second initialisation (another node of the site, or a restart) is a no-op.
        try (EdgeLedger second = LedgerTestDatabase.openLedger(LedgerTestDatabase.properties(schema), "acme-eu-2")) {
            assertTrue(second.ready());
        }
    }

    @Test
    void evaluateWritesTheEvaluationAndItsDecisionRecordInOneTransaction() throws Exception {
        EvaluationRecorder recorder = new EvaluationRecorder(ledger, schemas, mapper);
        EdgeController controller = controller(recorder);

        ResponseEntity<?> response = controller.evaluate(txn("AUTH-2026-10-16-000981", true));
        EdgeEvaluation evaluation = (EdgeEvaluation) response.getBody();
        assertNotNull(evaluation);
        assertEquals(EdgeRuleInterpreter.Action.HOLD, evaluation.action());

        try (Connection c = LedgerTestDatabase.connect(schema);
             PreparedStatement ps = c.prepareStatement("""
                     SELECT e.txn_ref, e.site_id, e.node_id, e.pan_hash, e.amount_cents, e.currency, e.merchant_id,
                            e.features::text, e.action, e.score, e.triggered_rule_ids, e.evaluator, e.origin,
                            e.fail_closed_code, o.payload::text, o.shipped_at, o.node_id
                       FROM edge_evaluations e JOIN edge_outbox o USING (edge_assessment_id)
                      WHERE e.edge_assessment_id = ?""")) {
            ps.setObject(1, UUID.fromString(evaluation.edgeAssessmentId()));
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "evaluation and outbox row exist");
                assertEquals("AUTH-2026-10-16-000981", rs.getString(1));
                assertEquals("acme-eu", rs.getString(2));
                assertEquals("acme-eu-1", rs.getString(3));
                assertEquals("9f2c1d7a", rs.getString(4));
                assertEquals(125000L, rs.getLong(5));
                assertEquals("KES", rs.getString(6));
                assertEquals("mrc_00421", rs.getString(7));
                JsonNode features = mapper.readTree(rs.getString(8));
                for (String secret : List.of("pan", "pan_hash", "cvv", "track2")) {
                    assertFalse(features.has(secret), secret + " must not be stored in features");
                }
                assertFalse(rs.getString(8).contains("4111111111111111"));
                assertEquals("HOLD", rs.getString(9));
                assertEquals(25, rs.getInt(10));
                assertEquals("{100}", rs.getString(11));
                assertEquals("java", rs.getString(12));
                assertEquals("LIVE", rs.getString(13));
                assertEquals(null, rs.getString(14));
                JsonNode record = mapper.readTree(rs.getString(15));
                assertEquals(List.of(), schemas.validate(ContractSchemas.DECISION_RECORD_V2, record));
                assertEquals(evaluation.edgeAssessmentId(), record.path("edgeAssessmentId").asText());
                assertEquals("AUTH-2026-10-16-000981", record.path("txnRef").asText());
                assertEquals(null, rs.getTimestamp(16));
                assertEquals("acme-eu-1", rs.getString(17));
            }
        }
        try (Connection c = LedgerTestDatabase.connect(schema);
             PreparedStatement ps = c.prepareStatement("SELECT edge_assessment_id FROM edge_txn_refs WHERE txn_ref = ?")) {
            ps.setString(1, "AUTH-2026-10-16-000981");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(evaluation.edgeAssessmentId(), rs.getObject(1, UUID.class).toString());
            }
        }
        assertEquals(1, recorder.recorded());
        assertEquals(0, recorder.writeFailures());
    }

    @Test
    void panLikeTxnRefIsRefusedWith422AndNothingIsStored() throws Exception {
        EdgeController controller = controller(new EvaluationRecorder(ledger, schemas, mapper));

        ResponseEntity<?> pan = controller.evaluate(txn("4111-1111-1111-1111", false));
        assertEquals(422, pan.getStatusCode().value());
        assertEquals(Map.of("error", "TXN_REF_LOOKS_LIKE_PAN"), pan.getBody());

        ResponseEntity<?> invalid = controller.evaluate(txn("has a space", false));
        assertEquals(422, invalid.getStatusCode().value());
        assertEquals(Map.of("error", "TXN_REF_INVALID"), invalid.getBody());

        assertEquals(0, count("edge_evaluations"));
        assertEquals(0, count("edge_outbox"));
    }

    @Test
    void reEvaluatedTxnRefPointsTheSiteIndexAtTheNewestEvaluation() throws Exception {
        EdgeController controller = controller(new EvaluationRecorder(ledger, schemas, mapper));
        EdgeEvaluation first = (EdgeEvaluation) controller.evaluate(txn("RETRY-1", false)).getBody();
        EdgeEvaluation second = (EdgeEvaluation) controller.evaluate(txn("RETRY-1", false)).getBody();

        assertEquals(2, count("edge_evaluations"));
        assertEquals(1, count("edge_txn_refs"));
        try (Connection c = LedgerTestDatabase.connect(schema); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT edge_assessment_id FROM edge_txn_refs")) {
            rs.next();
            assertEquals(second.edgeAssessmentId(), rs.getObject(1, UUID.class).toString());
            assertFalse(first.edgeAssessmentId().equals(second.edgeAssessmentId()));
        }
    }

    @Test
    void theStatementIsAtomicAnOutboxConflictLeavesNoEvaluationBehind() throws Exception {
        LedgerEvaluation ev = LedgerEvaluations.fromEvaluation(UUID.randomUUID(), Instant.now(), "acme-eu",
                "acme-eu-1", txn("ATOMIC-1", false), "h", new EdgeRuleInterpreter.Decision(
                        EdgeRuleInterpreter.Action.ALLOW, 0, List.of(), List.of()), 37,
                "sha256:" + "ab".repeat(32), "java", null, 100);
        ledger.write(ev, LedgerEvaluations.decisionRecord(ev, mapper));

        // Same id, a different instant: a new edge_evaluations key, but the outbox id is taken.
        LedgerEvaluation clash = new LedgerEvaluation(ev.edgeAssessmentId(), "ATOMIC-2", ev.siteId(), ev.nodeId(),
                ev.evaluatedAt().plusSeconds(5), ev.panHash(), ev.amountCents(), ev.currency(), ev.merchantId(),
                ev.mcc(), ev.countryCode(), ev.channel(), ev.features(), ev.decision(), ev.score(),
                ev.triggeredRuleIds(), ev.ruleVersionIds(), ev.reasons(), ev.bundleVersion(), ev.bundleHash(),
                ev.evaluator(), ev.latencyUs(), ev.failClosedCode(), false, List.of(), List.of());
        EdgeLedger.LedgerWriteException e = assertThrows(EdgeLedger.LedgerWriteException.class,
                () -> ledger.write(clash, LedgerEvaluations.decisionRecord(clash, mapper)));
        assertTrue(e.duplicate());
        assertEquals(1, count("edge_evaluations"), "the evaluation insert rolled back with the outbox insert");
        assertEquals(1, count("edge_txn_refs"), "ATOMIC-2 was never indexed");
    }

    @Test
    void aMissingMonthIsCreatedOnDemandByTheWritePath() throws Exception {
        Instant farFuture = Instant.now().plus(800, ChronoUnit.DAYS);
        LedgerEvaluation ev = LedgerEvaluations.fromEvaluation(UUID.randomUUID(), farFuture, "acme-eu",
                "acme-eu-1", txn(null, false), null, new EdgeRuleInterpreter.Decision(
                        EdgeRuleInterpreter.Action.ALLOW, 0, List.of(), List.of()), 37,
                "sha256:" + "ab".repeat(32), "java", null, 100);
        ledger.write(ev, LedgerEvaluations.decisionRecord(ev, mapper));
        assertEquals(1, count("edge_evaluations"));
    }

    @Test
    void failClosedHoldWhenTheTransactionFailsThenTheHoldIsRecordedOnceTheLedgerRecovers() throws Exception {
        EvaluationRecorder recorder = new EvaluationRecorder(ledger, schemas, mapper);
        EdgeController controller = controller(recorder);
        breakOutbox(true);
        try {
            EdgeEvaluation answer = (EdgeEvaluation) controller.evaluate(txn("DOWN-1", false)).getBody();
            assertEquals(EdgeRuleInterpreter.Action.HOLD, answer.action(), "ledger failure must not ALLOW");
            assertTrue(answer.reasons().get(0).contains("ledger"));
            assertEquals(0, count("edge_evaluations"), "nothing half-written");
            assertEquals(1, recorder.retryBacklog());
            assertEquals(1, recorder.writeFailures());
            assertEquals(0, recorder.drainRetries(), "still broken: nothing drained");
        } finally {
            breakOutbox(false);
        }
        assertEquals(1, recorder.drainRetries());
        assertEquals(0, recorder.retryBacklog());
        try (Connection c = LedgerTestDatabase.connect(schema); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT e.action, e.fail_closed_code, e.evaluator, e.txn_ref, o.payload::text
                       FROM edge_evaluations e JOIN edge_outbox o USING (edge_assessment_id)""")) {
            assertTrue(rs.next());
            assertEquals("HOLD", rs.getString(1));
            assertEquals("LEDGER_UNAVAILABLE", rs.getString(2));
            assertEquals("none", rs.getString(3));
            assertEquals("DOWN-1", rs.getString(4));
            JsonNode record = mapper.readTree(rs.getString(5));
            assertEquals(List.of(), schemas.validate(ContractSchemas.DECISION_RECORD_V2, record));
            assertEquals("LEDGER_UNAVAILABLE", record.path("failClosedCode").asText());
        }
    }

    /** Makes the outbox insert fail (relation missing) or restores it. */
    private void breakOutbox(boolean broken) throws Exception {
        try (Connection c = LedgerTestDatabase.connect(schema); Statement st = c.createStatement()) {
            st.execute(broken ? "ALTER TABLE edge_outbox RENAME TO edge_outbox_offline"
                    : "ALTER TABLE edge_outbox_offline RENAME TO edge_outbox");
        }
    }

    private long count(String table) throws Exception {
        try (Connection c = LedgerTestDatabase.connect(schema); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
