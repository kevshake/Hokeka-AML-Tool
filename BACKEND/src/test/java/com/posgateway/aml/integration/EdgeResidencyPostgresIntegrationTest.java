package com.posgateway.aml.integration;

import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.edge.records.DecisionStatsHourlyStore;
import com.posgateway.aml.service.residency.DecisionRecordPurgeService;
import com.posgateway.aml.service.residency.EdgeConfigVersionStore;
import com.posgateway.aml.service.residency.PspDataSettingsService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V261/V262 SQL against a real PostgreSQL 16: the decision-record purge with its workflow exclusions
 * (contract 8.4), the hourly rollup upsert and Edge config version minting.
 *
 * <p>Database: {@code -Dschema.it.jdbc-url=… -Dschema.it.user=… -Dschema.it.password=…} (an empty or
 * already-migrated database), otherwise a Testcontainers {@code postgres:16-alpine}; skipped when
 * neither is available.
 */
class EdgeResidencyPostgresIntegrationTest {

    private static PostgreSQLContainer<?> container;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    /** Connects (explicit database or Testcontainers) and applies the Flyway history. */
    @BeforeAll
    @SuppressWarnings("resource")
    static void database() {
        String url = System.getProperty("schema.it.jdbc-url");
        String user = System.getProperty("schema.it.user", "postgres");
        String password = System.getProperty("schema.it.password", "");
        if (url == null || url.isBlank()) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    org.testcontainers.DockerClientFactory.instance().isDockerAvailable(),
                    "Docker not available to Testcontainers; pass -Dschema.it.jdbc-url to run against a database");
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            url = container.getJdbcUrl();
            user = container.getUsername();
            password = container.getPassword();
        }
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration")
                .placeholderReplacement(false).load().migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
        jdbc = new JdbcTemplate(ds);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    /** Stops the container if this class started one. */
    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void retentionCheckConstraintEnforcesTheFloorAndCap() {
        long psp = newPsp(5);
        assertThrowsSql(() -> jdbc.update("UPDATE psps SET retention_years = 4 WHERE psp_id = ?", psp));
        assertThrowsSql(() -> jdbc.update("UPDATE psps SET retention_years = 26 WHERE psp_id = ?", psp));
        assertEquals(1, jdbc.update("UPDATE psps SET retention_years = 25 WHERE psp_id = ?", psp));
        Boolean support = jdbc.queryForObject("SELECT support_access_enabled FROM psps WHERE psp_id = ?", Boolean.class, psp);
        assertFalse(support, "support access defaults to off");
    }

    @Test
    void purgeDeletesExpiredRecordsExceptThoseHeldByWorkflow() {
        long psp = newPsp(5);
        long otherPsp = newPsp(5);
        Instant old = NOW.minus(6 * 366, ChronoUnit.DAYS);
        LocalDateTime longAgo = local(NOW.minus(5 * 366 + 100, ChronoUnit.DAYS));
        LocalDateTime recently = local(NOW.minus(365, ChronoUnit.DAYS));

        UUID unreferenced = record(psp, old);
        jdbc.update("INSERT INTO findings (id, assessment_id, source_type, phase, shadow, triggered) "
                + "VALUES (?, (SELECT id FROM assessments WHERE edge_assessment_id = ?), 'RULE', 'EDGE_SYNC', false, true)",
                UUID.randomUUID(), unreferenced);
        UUID openAlert = record(psp, old);
        alert(psp, openAlert, "open", null, null);
        UUID closedLongAgo = record(psp, old);
        alert(psp, closedLongAgo, "closed", longAgo, null);
        UUID closedRecently = record(psp, old);
        alert(psp, closedRecently, "closed", recently, null);
        UUID openCase = record(psp, old);
        alert(psp, openCase, "closed", longAgo, newCase(psp, "IN_PROGRESS", null));
        UUID draftSar = record(psp, old);
        long draftCase = newCase(psp, "CLOSED_SAR_FILED", longAgo);
        alert(psp, draftSar, "closed", longAgo, draftCase);
        sar(draftCase, "DRAFT", null);
        UUID filedLongAgo = record(psp, old);
        long filedCase = newCase(psp, "CLOSED_SAR_FILED", longAgo);
        alert(psp, filedLongAgo, "closed", longAgo, filedCase);
        sar(filedCase, "FILED", longAgo);
        UUID ctrPending = record(psp, old);
        long ctrAlert = alert(psp, ctrPending, "closed", longAgo, null);
        jdbc.update("INSERT INTO compliance_deadlines (deadline_type, deadline_date, source_type, source_id, psp_id, completed, created_at) "
                + "VALUES ('CTR_FILING', ?, 'ALERT', ?, ?, false, NOW())", recently, ctrAlert, psp);
        UUID viaCaseAlert = record(psp, old);
        long caseAlertAlert = alert(psp, viaCaseAlert, "closed", longAgo, null);
        long linkedCase = newCase(psp, "ASSIGNED", null);
        jdbc.update("INSERT INTO case_alerts (case_id, alert_type, triggered_at, source_alert_id) VALUES (?, 'RULE_VIOLATION', NOW(), ?)",
                linkedCase, caseAlertAlert);
        UUID recent = record(psp, NOW.minus(4 * 365, ChronoUnit.DAYS));
        UUID v1Row = UUID.randomUUID();
        jdbc.update("INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, edge_assessment_id, decision, created_at) "
                + "VALUES (?, ?, 'TXN', ?, ?, 'ALLOW', ?)", UUID.randomUUID(), psp, v1Row.toString(), v1Row,
                OffsetDateTime.ofInstant(old, ZoneOffset.UTC));
        UUID otherTenant = record(otherPsp, old);
        // R8 (V264): workflow that references a record only by edge_assessment_id also holds it.
        UUID viaOpenCaseLink = record(psp, old);
        caseLink(newCase(psp, "IN_PROGRESS", null), viaOpenCaseLink);
        UUID viaClosedCaseLink = record(psp, old);
        caseLink(newCase(psp, "CLOSED_CLEARED", longAgo), viaClosedCaseLink);
        UUID viaDraftSarRef = record(psp, old);
        sarRef(sarReturningId(newCase(psp, "CLOSED_CLEARED", longAgo), "DRAFT", null), viaDraftSarRef);
        UUID viaAlertLink = record(psp, old);
        long linkedAlert = jdbc.queryForObject("INSERT INTO alerts (psp_id, source_type, source_reference, status, action, "
                + "created_at) VALUES (?, 'TRANSACTION_MONITORING', '1', 'open', 'ALERT', NOW()) RETURNING alert_id",
                Long.class, psp);
        jdbc.update("INSERT INTO alert_transactions (alert_id, edge_assessment_id, primary_link) VALUES (?, ?, false)",
                linkedAlert, viaAlertLink);
        UUID viaAlertColumn = record(psp, old);
        jdbc.update("INSERT INTO alerts (psp_id, source_type, source_reference, status, action, created_at, "
                + "edge_assessment_id) VALUES (?, 'TRANSACTION_MONITORING', '2', 'open', 'ALERT', NOW(), ?)",
                psp, viaAlertColumn);

        PspDataSettingsService settings = mock(PspDataSettingsService.class);
        when(settings.retentionState(any())).thenReturn(new PspDataSettingsService.RetentionState(5, NOW, null));
        AuditLogService audit = mock(AuditLogService.class);
        DecisionRecordPurgeService purge = new DecisionRecordPurgeService(mock(PspRepository.class), settings, jdbc,
                transactions, audit, Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(4, purge.purgePsp(psp));

        assertFalse(exists(unreferenced), "expired and unreferenced");
        assertFalse(exists(closedLongAgo), "alert closed longer ago than retention");
        assertFalse(exists(filedLongAgo), "case and SAR closed / filed longer ago than retention");
        assertTrue(exists(openAlert), "open alert");
        assertTrue(exists(closedRecently), "alert closed within retention");
        assertTrue(exists(openCase), "open case");
        assertTrue(exists(draftSar), "unfiled SAR");
        assertTrue(exists(ctrPending), "CTR filing not completed");
        assertTrue(exists(viaCaseAlert), "open case linked through case_alerts.source_alert_id");
        assertTrue(exists(recent), "within retention");
        assertTrue(exists(v1Row), "v1 rows are not decision records");
        assertTrue(exists(otherTenant), "another PSP is untouched");
        assertTrue(exists(viaOpenCaseLink), "open case linking the record by edge_assessment_id");
        assertFalse(exists(viaClosedCaseLink), "case link of a case closed longer ago than retention");
        assertTrue(exists(viaDraftSarRef), "draft SAR referencing the record by edge_assessment_id");
        assertTrue(exists(viaAlertLink), "open alert linking the record through alert_transactions");
        assertTrue(exists(viaAlertColumn), "open alert carrying the record's edge_assessment_id");
        Integer findings = jdbc.queryForObject("SELECT COUNT(*) FROM findings f JOIN assessments a ON a.id = f.assessment_id "
                + "WHERE a.edge_assessment_id = ?", Integer.class, unreferenced);
        assertEquals(0, findings, "findings go with the record");
        verify(audit).logAction(isNull(), eq("DECISION_RECORD_PURGE"), eq("PSP"), eq(String.valueOf(psp)), any(), any(),
                any(), anyString());

        when(settings.retentionState(any())).thenReturn(new PspDataSettingsService.RetentionState(10, NOW, null));
        assertEquals(0, purge.purgePsp(otherPsp), "a 10-year PSP keeps a 6-year-old record");
        assertTrue(exists(otherTenant));
    }

    @Test
    void hourlyRollupAccumulatesPerHourSiteAndMerchant() {
        long psp = newPsp(5);
        DecisionStatsHourlyStore stats = new DecisionStatsHourlyStore(jdbc);
        Instant at = Instant.parse("2026-10-16T09:14:07Z");
        transactions.executeWithoutResult(s -> {
            stats.add(psp, at, "acme-eu", "mrc_00421", "ALERT", false, false, false, 45, 3);
            stats.add(psp, at.plusSeconds(600), "acme-eu", "mrc_00421", "BLOCK", false, true, true, 100, 11);
            stats.add(psp, at, "acme-eu", null, "HOLD", true, false, false, 0, 0);
        });
        List<DecisionStatsHourlyStore.HourlyStats> rows = stats.range(psp, Instant.parse("2026-10-16T09:00:00Z"),
                Instant.parse("2026-10-16T10:00:00Z"));
        assertEquals(2, rows.size());
        DecisionStatsHourlyStore.HourlyStats noMerchant = rows.get(0);
        assertEquals("", noMerchant.merchantKey());
        assertEquals(1, noMerchant.failClosed());
        DecisionStatsHourlyStore.HourlyStats merchant = rows.get(1);
        assertEquals(Instant.parse("2026-10-16T09:00:00Z"), merchant.hourStart());
        assertEquals(2, merchant.records());
        assertEquals(1, merchant.alert());
        assertEquals(1, merchant.block());
        assertEquals(1, merchant.ctrRequired());
        assertEquals(1, merchant.sanctionsHit());
        assertEquals(145, merchant.scoreSum());
        assertEquals(11, merchant.latencyMsMax());
    }

    @Test
    void configVersionsAreMintedOnlyWhenTheContentHashChanges() {
        long psp = newPsp(5);
        EdgeConfigVersionStore versions = new EdgeConfigVersionStore(jdbc);
        assertEquals(new EdgeConfigVersionStore.VersionResult(1, true), versions.ensureVersion(psp, "a".repeat(64)));
        assertEquals(new EdgeConfigVersionStore.VersionResult(1, false), versions.ensureVersion(psp, "a".repeat(64)));
        assertEquals(new EdgeConfigVersionStore.VersionResult(2, true), versions.ensureVersion(psp, "b".repeat(64)));
        assertEquals(new EdgeConfigVersionStore.VersionResult(3, true), versions.ensureVersion(psp, "a".repeat(64)),
                "returning to earlier content is still a new, higher version");
        assertEquals(3, versions.latestVersion(psp));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────────

    private static long newPsp(int retentionYears) {
        String code = "R3IT_" + UUID.randomUUID().toString().substring(0, 8);
        Long id = jdbc.queryForObject("INSERT INTO psps (psp_code, legal_name, country, contact_email, retention_years) "
                + "VALUES (?, 'Residency IT', 'KEN', 'it@example.com', ?) RETURNING psp_id", Long.class, code, retentionYears);
        return id;
    }

    private static UUID record(long psp, Instant evaluatedAt) {
        UUID edgeId = UUID.randomUUID();
        jdbc.update("INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, edge_assessment_id, record_version, "
                        + "evaluated_at, decision, created_at) VALUES (?, ?, 'TXN', ?, ?, 2, ?, 'ALERT', ?)",
                UUID.randomUUID(), psp, edgeId.toString(), edgeId, OffsetDateTime.ofInstant(evaluatedAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(evaluatedAt, ZoneOffset.UTC));
        return edgeId;
    }

    private static long alert(long psp, UUID edgeId, String status, LocalDateTime closedAt, Long caseId) {
        return jdbc.queryForObject("INSERT INTO alerts (psp_id, source_type, source_reference, status, closed_at, "
                        + "compliance_case_id, action, created_at) VALUES (?, 'EDGE_DECISION', ?, ?, ?, ?, 'ALERT', NOW()) "
                        + "RETURNING alert_id",
                Long.class, psp, edgeId.toString(), status, closedAt, caseId);
    }

    private static long newCase(long psp, String status, LocalDateTime resolvedAt) {
        return jdbc.queryForObject("INSERT INTO compliance_cases (psp_id, status, priority, resolved_at, case_reference, "
                        + "created_at, updated_at) VALUES (?, ?, 'MEDIUM', ?, ?, NOW(), NOW()) RETURNING case_id",
                Long.class, psp, status, resolvedAt, "CASE-IT-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private static void sar(long caseId, String status, LocalDateTime filedAt) {
        jdbc.update("INSERT INTO suspicious_activity_reports (sar_reference, status, case_id, filed_at, created_at, "
                        + "jurisdiction, narrative, sar_type, suspicion_arose_at, suspicion_timestamp_source, "
                        + "suspicious_activity_type) VALUES (?, ?, ?, ?, NOW(), 'KE', 'n', 'INITIAL', NOW(), 'MANUAL', 'FRAUD')",
                "SAR-IT-" + UUID.randomUUID(), status, caseId, filedAt);
    }

    private static long sarReturningId(long caseId, String status, LocalDateTime filedAt) {
        return jdbc.queryForObject("INSERT INTO suspicious_activity_reports (sar_reference, status, case_id, filed_at, "
                        + "created_at, jurisdiction, narrative, sar_type, suspicion_arose_at, suspicion_timestamp_source, "
                        + "suspicious_activity_type) VALUES (?, ?, ?, ?, NOW(), 'KE', 'n', 'INITIAL', NOW(), 'MANUAL', "
                        + "'FRAUD') RETURNING id",
                Long.class, "SAR-IT-" + UUID.randomUUID(), status, caseId, filedAt);
    }

    /** A case_transactions row referencing a decision record by id only (V264). */
    private static void caseLink(long caseId, UUID edgeId) {
        jdbc.update("INSERT INTO case_transactions (case_id, edge_assessment_id, relationship_type, added_at) "
                + "VALUES (?, ?, 'PRIMARY', NOW())", caseId, edgeId);
    }

    /** A sar_transactions row referencing a decision record by id only (V264). */
    private static void sarRef(long sarId, UUID edgeId) {
        jdbc.update("INSERT INTO sar_transactions (sar_id, edge_assessment_id) VALUES (?, ?)", sarId, edgeId);
    }

    private static boolean exists(UUID edgeId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM assessments WHERE edge_assessment_id = ?", Integer.class, edgeId);
        return n != null && n > 0;
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static void assertThrowsSql(Runnable statement) {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class, statement::run);
    }
}
