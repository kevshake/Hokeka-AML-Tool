package com.posgateway.aml.integration;

import com.posgateway.aml.entity.filing.LegalHold;
import com.posgateway.aml.entity.filing.LegalHoldWorkflow;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.legalhold.LegalHoldReconciler;
import com.posgateway.aml.service.legalhold.LegalHoldService;
import com.posgateway.aml.service.residency.DecisionRecordPurgeService;
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

import java.sql.Timestamp;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V269 SQL against a real PostgreSQL 16 (work package R9): legal holds keep decision records out of
 * the cloud purge (contract 8.3/8.4), the filed-document table is immutable and encrypted-only,
 * and the legal-hold reconciler's queries find exactly the uncovered, orphaned, closed and reopened
 * workflow records.
 *
 * <p>Database: {@code -Dschema.it.jdbc-url=… -Dschema.it.user=… -Dschema.it.password=…}, otherwise a
 * Testcontainers {@code postgres:16-alpine}; skipped when neither is available.
 */
class FilingAndLegalHoldPostgresIntegrationTest {

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

    /** Verifies: an active hold keeps an expired decision record; once released the purge takes it. */
    @Test
    void activeLegalHoldKeepsDecisionRecordsOutOfThePurge() {
        long psp = newPsp();
        Instant old = NOW.minus(6 * 366, ChronoUnit.DAYS);
        UUID held = record(psp, old);
        UUID free = record(psp, old);
        UUID otherPspHold = record(psp, old);
        UUID holdId = hold(psp, "ACTIVE", held);
        // A hold of another PSP never protects this PSP's record.
        hold(newPsp(), "ACTIVE", otherPspHold);

        PspDataSettingsService settings = mock(PspDataSettingsService.class);
        when(settings.retentionState(any())).thenReturn(new PspDataSettingsService.RetentionState(5, NOW, null));
        DecisionRecordPurgeService purge = new DecisionRecordPurgeService(mock(PspRepository.class), settings, jdbc,
                transactions, mock(AuditLogService.class), Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(2, purge.purgePsp(psp));
        assertTrue(exists(held), "record under an active hold");
        assertFalse(exists(free));
        assertFalse(exists(otherPspHold), "another PSP's hold does not cover this PSP's record");

        jdbc.update("UPDATE legal_holds SET state = 'RELEASED', released_at = NOW(), released_by = 'u-1', "
                + "release_reason = 'FILING_RETENTION_ELAPSED' WHERE hold_id = ?", holdId);
        assertEquals(1, purge.purgePsp(psp));
        assertFalse(exists(held), "a released hold no longer keeps the record");
    }

    /** Verifies: filed documents are encrypted-only, never updated, and not deleted before retain_until. */
    @Test
    void filedDocumentsAreImmutableAndEncryptedOnly() {
        long psp = newPsp();
        long retained = filedDocument(psp, "SAR", NOW, NOW.plus(5 * 366, ChronoUnit.DAYS), "enc:v1:QUJD");
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE regulatory_filed_documents SET regulator_reference = 'x' WHERE id = ?", retained));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("DELETE FROM regulatory_filed_documents WHERE id = ?", retained));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> filedDocument(psp, "SAR", NOW, NOW.plusSeconds(60), "<report>plaintext</report>"),
                "plaintext content is refused");
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> filedDocument(psp, "SAR", NOW, NOW.plusSeconds(60), "enc:v1:QUJD"),
                "one document per workflow record");
        Instant longAgo = Instant.parse("2001-01-01T00:00:00Z");
        long expired = filedDocument(psp, "CTR", longAgo, longAgo.plus(6 * 366, ChronoUnit.DAYS), "enc:v1:QUJD");
        assertEquals(1, jdbc.update("DELETE FROM regulatory_filed_documents WHERE id = ?", expired),
                "deletable once its retention has passed");
    }

    /** Verifies: the reconciler covers exactly the uncovered refs of unfiled SARs and escalated cases. */
    @Test
    void reconcilerFindsUncoveredOrphanedClosedAndReopenedRecords() {
        long psp = newPsp();
        LegalHoldService holds = mock(LegalHoldService.class);
        when(holds.ensureCoverage(anyLong(), any(), anyLong(), anyString(), any(), anyCollection(), any()))
                .thenReturn(new LegalHoldService.Coverage(List.of(UUID.randomUUID()), 1, 0));

        long draftSar = sar(psp, "DRAFT");
        UUID covered = UUID.randomUUID();
        UUID uncovered = UUID.randomUUID();
        sarRef(draftSar, covered);
        sarRef(draftSar, uncovered);
        holdForWorkflow(psp, "SAR", draftSar, "ACTIVE", covered);
        long filedSar = sar(psp, "FILED");
        sarRef(filedSar, UUID.randomUUID());

        long escalated = newCase(psp, "ESCALATED", null);
        UUID caseTxn = UUID.randomUUID();
        caseLink(escalated, caseTxn);
        long inProgress = newCase(psp, "IN_PROGRESS", null);
        caseLink(inProgress, UUID.randomUUID());

        long deletedSarId = 9_000_000_000L + (System.nanoTime() % 1_000_000);
        holdForWorkflow(psp, "SAR", deletedSarId, "ACTIVE", UUID.randomUUID());
        LocalDateTime resolved = LocalDateTime.of(2026, 9, 1, 12, 0);
        long closed = newCase(psp, "CLOSED_CLEARED", resolved);
        holdForWorkflow(psp, "CASE", closed, "ACTIVE", UUID.randomUUID());
        long reopened = newCase(psp, "REOPENED", null);
        UUID reopenedHold = holdForWorkflow(psp, "CASE", reopened, "ACTIVE", UUID.randomUUID());
        jdbc.update("UPDATE legal_holds SET release_basis_at = NOW() WHERE hold_id = ?", reopenedHold);

        new LegalHoldReconciler(jdbc, holds, Clock.fixed(NOW, ZoneOffset.UTC)).reconcile();

        verify(holds).ensureCoverage(eq(psp), eq(LegalHoldWorkflow.Type.SAR), eq(draftSar), eq("SAR"), anyString(),
                eq(List.of(uncovered)), anyString());
        verify(holds, org.mockito.Mockito.never()).ensureCoverage(anyLong(), eq(LegalHoldWorkflow.Type.SAR),
                eq(filedSar), anyString(), any(), anyCollection(), any());
        verify(holds).ensureCoverage(eq(psp), eq(LegalHoldWorkflow.Type.CASE), eq(escalated), eq("CASE"), anyString(),
                eq(List.of(caseTxn)), anyString());
        verify(holds, org.mockito.Mockito.never()).ensureCoverage(anyLong(), eq(LegalHoldWorkflow.Type.CASE),
                eq(inProgress), anyString(), any(), anyCollection(), any());
        verify(holds).releaseWorkflow(eq(LegalHoldWorkflow.Type.SAR), eq(deletedSarId),
                eq(LegalHold.ReleaseReason.PLACED_IN_ERROR), anyString());
        verify(holds).markWorkflowEnded(LegalHoldWorkflow.Type.CASE, closed,
                resolved.atZone(ZoneId.systemDefault()).toInstant());
        verify(holds).markWorkflowReopened(LegalHoldWorkflow.Type.CASE, reopened);
        verify(holds).releaseDue();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────────

    /** A PSP row. */
    private static long newPsp() {
        String code = "R9IT_" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("INSERT INTO psps (psp_code, legal_name, country, contact_email, retention_years) "
                + "VALUES (?, 'Filing IT', 'KEN', 'it@example.com', 5) RETURNING psp_id", Long.class, code);
    }

    /** A v2 decision record. */
    private static UUID record(long psp, Instant evaluatedAt) {
        UUID edgeId = UUID.randomUUID();
        jdbc.update("INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, edge_assessment_id, record_version, "
                        + "evaluated_at, decision, created_at) VALUES (?, ?, 'TXN', ?, ?, 2, ?, 'ALERT', ?)",
                UUID.randomUUID(), psp, edgeId.toString(), edgeId, OffsetDateTime.ofInstant(evaluatedAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(evaluatedAt, ZoneOffset.UTC));
        return edgeId;
    }

    /** A hold of a fresh SAR workflow over one id. */
    private static UUID hold(long psp, String state, UUID edgeId) {
        return holdForWorkflow(psp, "SAR", System.nanoTime(), state, edgeId);
    }

    /** A hold of the given workflow record over one id. */
    private static UUID holdForWorkflow(long psp, String type, long workflowId, String state, UUID edgeId) {
        List<Long> found = jdbc.queryForList("SELECT id FROM legal_hold_workflows WHERE workflow_type = ? AND workflow_id = ?",
                Long.class, type, workflowId);
        Long wf = found.isEmpty() ? null : found.get(0);
        if (wf == null) {
            wf = jdbc.queryForObject("INSERT INTO legal_hold_workflows (psp_id, workflow_type, workflow_id, reason_code, "
                    + "reference) VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class, psp, type, workflowId,
                    "CASE".equals(type) ? "CASE" : "SAR", type + "-" + workflowId);
        }
        UUID holdId = UUID.randomUUID();
        jdbc.update("INSERT INTO legal_holds (hold_id, workflow_ref_id, psp_id, reason_code, reference, state, placed_at, "
                + "placed_by, item_count) VALUES (?, ?, ?, ?, ?, ?, NOW(), 'u-1', 1)", holdId, wf, psp,
                "CASE".equals(type) ? "CASE" : "SAR", type + "-" + workflowId, state);
        jdbc.update("INSERT INTO legal_hold_items (hold_id, edge_assessment_id) VALUES (?, ?)", holdId, edgeId);
        return holdId;
    }

    /** A filed-document row (content as given). */
    private static long filedDocument(long psp, String kind, Instant filedAt, Instant retainUntil, String content) {
        long workflowId = "SAR".equals(kind) ? psp : System.nanoTime();
        return jdbc.queryForObject("INSERT INTO regulatory_filed_documents (psp_id, document_kind, workflow_type, "
                        + "workflow_id, filing_ref, document_format, content_type, file_name, content_ciphertext, "
                        + "content_sha256, content_length, transaction_count, data_source, filed_by_user_id, filed_at, "
                        + "retain_until) VALUES (?, ?, ?, ?, 'REF-1', 'GOAML_XML', 'application/xml', 'f.xml', ?, ?, 3, 1, "
                        + "'EDGE', 12, ?, ?) RETURNING id", Long.class, psp, kind,
                "SAR".equals(kind) ? "SAR" : "CTR_DEADLINE", workflowId, content, "a".repeat(64),
                Timestamp.from(filedAt), Timestamp.from(retainUntil));
    }

    /** A SAR row of a PSP. */
    private static long sar(long psp, String status) {
        return jdbc.queryForObject("INSERT INTO suspicious_activity_reports (sar_reference, status, psp_id, created_at, "
                        + "jurisdiction, narrative, sar_type, suspicion_arose_at, suspicion_timestamp_source, "
                        + "suspicious_activity_type) VALUES (?, ?, ?, NOW(), 'KE', 'n', 'INITIAL', NOW(), 'MANUAL', "
                        + "'FRAUD') RETURNING id",
                Long.class, "SAR-R9-" + UUID.randomUUID().toString().substring(0, 12), status, psp);
    }

    /** A case row. */
    private static long newCase(long psp, String status, LocalDateTime resolvedAt) {
        return jdbc.queryForObject("INSERT INTO compliance_cases (psp_id, status, priority, resolved_at, case_reference, "
                        + "created_at, updated_at) VALUES (?, ?, 'MEDIUM', ?, ?, NOW(), NOW()) RETURNING case_id",
                Long.class, psp, status, resolvedAt, "CASE-R9-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /** A case_transactions row by decision record id. */
    private static void caseLink(long caseId, UUID edgeId) {
        jdbc.update("INSERT INTO case_transactions (case_id, edge_assessment_id, relationship_type, added_at) "
                + "VALUES (?, ?, 'PRIMARY', NOW())", caseId, edgeId);
    }

    /** A sar_transactions row by decision record id. */
    private static void sarRef(long sarId, UUID edgeId) {
        jdbc.update("INSERT INTO sar_transactions (sar_id, edge_assessment_id) VALUES (?, ?)", sarId, edgeId);
    }

    /** True while the decision record exists. */
    private static boolean exists(UUID edgeId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM assessments WHERE edge_assessment_id = ?", Integer.class, edgeId);
        return n != null && n > 0;
    }
}
