package com.posgateway.aml.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V264 (plan R8) against real PostgreSQL 16: legacy workflow rows are migrated to V263, then V264
 * runs and must back-fill the ID references, drop the foreign keys to {@code transactions}, retire
 * {@code alerts.pan_hash} and rewrite transaction snapshots in {@code case_alerts.raw_data}.
 *
 * <p>Database: a fresh database is created on the server named by {@code -Dschema.it.jdbc-url}
 * ({@code schema.it.user} / {@code schema.it.password}) and dropped afterwards, because the test must
 * migrate in two steps from empty; otherwise a Testcontainers {@code postgres:16-alpine}; skipped
 * when neither is available.
 */
class ComplianceRefsByIdMigrationIntegrationTest {

    private static PostgreSQLContainer<?> container;
    private static String adminUrl;
    private static String user;
    private static String password;
    private static String databaseName;
    private static String url;
    private static JdbcTemplate jdbc;

    /** Creates an empty database (explicit server or Testcontainers) and migrates it to V263. */
    @BeforeAll
    @SuppressWarnings("resource")
    static void database() throws Exception {
        adminUrl = System.getProperty("schema.it.jdbc-url");
        user = System.getProperty("schema.it.user", "postgres");
        password = System.getProperty("schema.it.password", "");
        if (adminUrl == null || adminUrl.isBlank()) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    org.testcontainers.DockerClientFactory.instance().isDockerAvailable(),
                    "Docker not available to Testcontainers; pass -Dschema.it.jdbc-url to run against a database");
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            adminUrl = container.getJdbcUrl();
            user = container.getUsername();
            password = container.getPassword();
        }
        databaseName = "r8_refs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = DriverManager.getConnection(adminUrl, user, password); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + databaseName);
        }
        url = withDatabase(adminUrl, databaseName);
        flyway("263").migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    }

    /** Drops the database this class created and stops the container if it started one. */
    @AfterAll
    static void stop() throws Exception {
        if (databaseName != null && adminUrl != null) {
            try (Connection c = DriverManager.getConnection(adminUrl, user, password); Statement s = c.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)");
            }
        }
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void backfillsReferencesDropsForeignKeysAndRemovesTransactionData() {
        long psp = jdbc.queryForObject("INSERT INTO psps (psp_code, legal_name, country, contact_email) "
                + "VALUES ('R8IT', 'Refs IT', 'KEN', 'it@example.com') RETURNING psp_id", Long.class);
        String panHash = "a".repeat(64);
        jdbc.update("INSERT INTO transactions (txn_id, psp_id, merchant_id, pan_hash, amount_cents, currency, "
                + "client_reference, created_at) VALUES (1001, ?, '4', ?, 5000, 'KES', 'ORD-1', NOW())", psp, panHash);
        jdbc.update("INSERT INTO transactions (txn_id, psp_id, merchant_id, pan_hash, amount_cents, currency, "
                + "client_reference, created_at) VALUES (1002, ?, '4', ?, 7000, 'KES', '4111-1111-1111-1111', NOW())",
                psp, panHash);
        // 1001 has an older cloud assessment and a newer Edge-adopted one: the Edge one wins.
        UUID cloudAssessment = UUID.randomUUID();
        jdbc.update("INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, txn_id, created_at) "
                + "VALUES (?, ?, 'TXN', '1001', 1001, NOW() - INTERVAL '2 hours')", cloudAssessment, psp);
        UUID edgeAdopted = UUID.randomUUID();
        UUID adoptedRow = UUID.randomUUID();
        jdbc.update("INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, txn_id, edge_assessment_id, site_id, "
                + "created_at) VALUES (?, ?, 'TXN', '1001', 1001, ?, 'site-a', NOW() - INTERVAL '1 hour')",
                adoptedRow, psp, edgeAdopted);
        // An R7 decision record and the alert raised from it.
        UUID decision = UUID.randomUUID();
        UUID decisionRow = UUID.randomUUID();
        jdbc.update("INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, edge_assessment_id, record_version, "
                + "txn_ref, site_id, decision, created_at) VALUES (?, ?, 'TXN', ?, ?, 2, 'AUTH-9', 'site-b', 'BLOCK', NOW())",
                decisionRow, psp, decision.toString(), decision);

        long legacyAlert = jdbc.queryForObject("INSERT INTO alerts (txn_id, pan_hash, status, action, created_at) "
                + "VALUES (1001, ?, 'open', 'HOLD', NOW()) RETURNING alert_id", Long.class, panHash);
        long cardRefAlert = jdbc.queryForObject("INSERT INTO alerts (txn_id, psp_id, status, action, created_at) "
                + "VALUES (1002, ?, 'open', 'HOLD', NOW()) RETURNING alert_id", Long.class, psp);
        long edgeAlert = jdbc.queryForObject("INSERT INTO alerts (psp_id, source_type, source_reference, status, action, "
                + "created_at) VALUES (?, 'EDGE_DECISION', ?, 'open', 'BLOCK', NOW()) RETURNING alert_id",
                Long.class, psp, decision.toString());
        jdbc.update("INSERT INTO alert_transactions (alert_id, txn_id, primary_link) VALUES (?, 1001, true)", legacyAlert);

        long caseId = jdbc.queryForObject("INSERT INTO compliance_cases (psp_id, status, priority, case_reference, "
                + "created_at, updated_at) VALUES (?, 'NEW', 'MEDIUM', 'CASE-R8-IT', NOW(), NOW()) RETURNING case_id",
                Long.class, psp);
        long caseTxn = jdbc.queryForObject("INSERT INTO case_transactions (case_id, transaction_id, relationship_type, "
                + "added_at) VALUES (?, 1001, 'PRIMARY', NOW()) RETURNING id", Long.class, caseId);
        int rev = jdbc.queryForObject("INSERT INTO revinfo (revtstmp) VALUES (0) RETURNING rev", Integer.class);
        jdbc.update("INSERT INTO case_transactions_aud (id, rev, revtype, case_id, transaction_id) VALUES (?, ?, 0, ?, 1001)",
                caseTxn, rev, caseId);
        long sarId = jdbc.queryForObject("INSERT INTO suspicious_activity_reports (sar_reference, status, case_id, psp_id, "
                + "created_at, jurisdiction, narrative, sar_type, suspicion_arose_at, suspicion_timestamp_source, "
                + "suspicious_activity_type) VALUES ('SAR-R8-IT', 'DRAFT', ?, ?, NOW(), 'KE', 'n', 'INITIAL', NOW(), "
                + "'MANUAL', 'FRAUD') RETURNING id", Long.class, caseId, psp);
        jdbc.update("INSERT INTO sar_transactions (sar_id, txn_id) VALUES (?, 1001)", sarId);
        jdbc.update("INSERT INTO sar_transactions_aud (rev, sar_id, txn_id, revtype) VALUES (?, ?, 1001, 0)", rev, sarId);
        jdbc.update("INSERT INTO sar_transactions_aud (rev, sar_id, txn_id, revtype) VALUES (?, ?, 1002, 2)", rev, sarId);
        jdbc.update("INSERT INTO screening_activities (psp_id, txn_id, subject_type, subject_name) "
                + "VALUES (?, 1001, 'TRANSACTION', 'John Counterparty')", psp);
        UUID legacyFinding = UUID.randomUUID();
        jdbc.update("INSERT INTO findings (id, assessment_id, txn_id, source_type, triggered) VALUES (?, ?, 1001, 'RULE', true)",
                legacyFinding, cloudAssessment);
        UUID edgeFinding = UUID.randomUUID();
        jdbc.update("INSERT INTO findings (id, assessment_id, source_type, phase, shadow, triggered) "
                + "VALUES (?, ?, 'RULE', 'EDGE_SYNC', false, true)", edgeFinding, decisionRow);
        long snapshotAlert = jdbc.queryForObject("INSERT INTO case_alerts (case_id, alert_type, raw_data, triggered_at) "
                + "VALUES (?, 'RULE_VIOLATION', ?, NOW()) RETURNING id", Long.class, caseId,
                "{\"txnId\":1001,\"panHash\":\"" + panHash + "\",\"amountCents\":5000,\"currency\":\"KES\",\"ipAddress\":\"10.0.0.1\"}");
        String screeningRaw = "{\"source\": \"PERIODIC_SCREENING\", \"merchantId\": 4}";
        long screeningCaseAlert = jdbc.queryForObject("INSERT INTO case_alerts (case_id, alert_type, raw_data, triggered_at) "
                + "VALUES (?, 'SANCTIONS_HIT', ?, NOW()) RETURNING id", Long.class, caseId, screeningRaw);

        flyway("264").migrate();

        // Foreign keys from workflow tables to transactions are gone; the legacy ids stay.
        Integer fks = jdbc.queryForObject("SELECT COUNT(*) FROM pg_constraint WHERE contype = 'f' "
                + "AND confrelid = 'transactions'::regclass AND conrelid::regclass::text IN ('alerts', 'alert_transactions', "
                + "'case_transactions', 'sar_transactions', 'screening_activities', 'findings')", Integer.class);
        assertEquals(0, fks);

        Map<String, Object> a = jdbc.queryForMap("SELECT psp_id, txn_id, edge_assessment_id, txn_ref, site_id, pan_hash "
                + "FROM alerts WHERE alert_id = ?", legacyAlert);
        assertEquals(psp, ((Number) a.get("psp_id")).longValue(), "psp_id back-filled from the transaction");
        assertEquals(1001L, ((Number) a.get("txn_id")).longValue());
        assertEquals(edgeAdopted, a.get("edge_assessment_id"), "Edge-adopted assessment preferred");
        assertEquals("ORD-1", a.get("txn_ref"), "client reference used as txnRef");
        assertEquals("site-a", a.get("site_id"));
        assertNull(a.get("pan_hash"), "alerts.pan_hash nulled");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE alerts SET pan_hash = ? WHERE alert_id = ?", panHash, legacyAlert),
                "alerts.pan_hash is locked to NULL");

        Map<String, Object> card = jdbc.queryForMap("SELECT edge_assessment_id, txn_ref FROM alerts WHERE alert_id = ?",
                cardRefAlert);
        assertNull(card.get("edge_assessment_id"), "no assessment: R15 mints the id");
        assertNull(card.get("txn_ref"), "a card-shaped client reference is never a txnRef");

        Map<String, Object> e = jdbc.queryForMap("SELECT edge_assessment_id, txn_ref, site_id FROM alerts WHERE alert_id = ?",
                edgeAlert);
        assertEquals(decision, e.get("edge_assessment_id"));
        assertEquals("AUTH-9", e.get("txn_ref"));
        assertEquals("site-b", e.get("site_id"));
        Integer edgeLinks = jdbc.queryForObject("SELECT COUNT(*) FROM alert_transactions WHERE alert_id = ? "
                + "AND edge_assessment_id = ? AND txn_id IS NULL AND primary_link", Integer.class, edgeAlert, decision);
        assertEquals(1, edgeLinks, "decision-record alerts get their primary link row");

        assertEquals(edgeAdopted, jdbc.queryForObject("SELECT edge_assessment_id FROM alert_transactions "
                + "WHERE alert_id = ? AND txn_id = 1001", UUID.class, legacyAlert));
        assertEquals(edgeAdopted, jdbc.queryForObject("SELECT edge_assessment_id FROM case_transactions WHERE id = ?",
                UUID.class, caseTxn));
        assertEquals(edgeAdopted, jdbc.queryForObject("SELECT edge_assessment_id FROM case_transactions_aud "
                + "WHERE id = ? AND rev = ?", UUID.class, caseTxn, rev));
        Map<String, Object> st = jdbc.queryForMap("SELECT id, edge_assessment_id, txn_ref FROM sar_transactions "
                + "WHERE sar_id = ?", sarId);
        assertNotNull(st.get("id"), "sar_transactions has a surrogate id");
        assertEquals(edgeAdopted, st.get("edge_assessment_id"));
        assertEquals("ORD-1", st.get("txn_ref"));
        assertEquals(edgeAdopted, jdbc.queryForObject("SELECT edge_assessment_id FROM sar_transactions_aud "
                + "WHERE id = ? AND rev = ?", UUID.class, st.get("id"), rev));
        Long orphanAudId = jdbc.queryForObject("SELECT id FROM sar_transactions_aud WHERE txn_id = 1002", Long.class);
        assertNotNull(orphanAudId, "audit history of a removed link keeps an id");
        assertFalse(orphanAudId.equals(((Number) st.get("id")).longValue()));

        Map<String, Object> sa = jdbc.queryForMap("SELECT edge_assessment_id, subject_name FROM screening_activities "
                + "WHERE txn_id = 1001");
        assertEquals(edgeAdopted, sa.get("edge_assessment_id"));
        assertNull(sa.get("subject_name"), "the screened counterparty name is transaction data");

        assertEquals(edgeAdopted, jdbc.queryForObject("SELECT edge_assessment_id FROM findings WHERE id = ?",
                UUID.class, legacyFinding), "legacy finding takes its transaction's reference");
        Map<String, Object> ef = jdbc.queryForMap("SELECT edge_assessment_id, txn_ref, site_id FROM findings WHERE id = ?",
                edgeFinding);
        assertEquals(decision, ef.get("edge_assessment_id"));
        assertEquals("AUTH-9", ef.get("txn_ref"));

        String rewritten = jdbc.queryForObject("SELECT raw_data FROM case_alerts WHERE id = ?", String.class, snapshotAlert);
        assertFalse(rewritten.contains(panHash), rewritten);
        assertFalse(rewritten.contains("amountCents"), rewritten);
        assertFalse(rewritten.contains("10.0.0.1"), rewritten);
        assertTrue(rewritten.contains(edgeAdopted.toString()), rewritten);
        assertTrue(rewritten.contains("LEGACY_TRANSACTION"), rewritten);
        assertEquals(screeningRaw, jdbc.queryForObject("SELECT raw_data FROM case_alerts WHERE id = ?", String.class,
                screeningCaseAlert), "non-transaction raw data is left alone");

        // A link row must identify its transaction.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO case_transactions (case_id, relationship_type, added_at) "
                        + "VALUES (?, 'PRIMARY', NOW())", caseId));
    }

    /** Flyway over this class's database up to {@code target}. */
    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration")
                .placeholderReplacement(false).target(target).load();
    }

    /** The JDBC URL with its database name replaced (query string kept). */
    private static String withDatabase(String jdbcUrl, String database) {
        int query = jdbcUrl.indexOf('?');
        String base = query < 0 ? jdbcUrl : jdbcUrl.substring(0, query);
        String params = query < 0 ? "" : jdbcUrl.substring(query);
        return base.substring(0, base.lastIndexOf('/') + 1) + database + params;
    }
}
