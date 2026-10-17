package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.edge.tunnel.SupportAccessPolicy;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.residency.PspEdgeSettings;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.repository.residency.PspEdgeSettingsRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.edgedata.EdgeTunnelAccess;
import com.posgateway.aml.service.security.PspIsolationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

/**
 * R15 back-fill against real PostgreSQL 16 with the full Flyway history (V1..V270): selection,
 * mapping, BEGIN / BATCH / COMMIT, verification, decision records and R8 reference minting, the R12
 * purge gate, resume after interruption, offline / busy / timeout / frame-size handling, re-send on
 * VERIFY_MISMATCH, pause / resume / abort, multi-site routing, access rules and tenant isolation.
 *
 * <p>The Edge is {@link EdgeLedgerSimulator} (the edge-host importer semantics); the real tunnel path
 * is covered by {@code LedgerBackfillTunnelIntegrationTest} and the real importer by the edge-host
 * {@code CloudBackfillFixturesIntegrationTest}.
 *
 * <p>Database: a fresh database on {@code -Dschema.it.jdbc-url} (with {@code schema.it.user} /
 * {@code schema.it.password}), else a Testcontainers {@code postgres:16-alpine}; skipped without
 * either.
 */
class LedgerBackfillPostgresIntegrationTest {

    private static PostgreSQLContainer<?> container;
    private static String adminUrl;
    private static String user;
    private static String password;
    private static String databaseName;
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager txManager;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Long, PspEdgeSettings> settingsByPsp = new ConcurrentHashMap<>();
    private final Map<Long, List<EdgeNode>> nodesByPsp = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> supportAccess = new ConcurrentHashMap<>();
    private final AtomicReference<User> currentUser = new AtomicReference<>();
    private AuditLogService audit;
    private BackfillProperties props;
    private BackfillRunStore store;
    private LegacyRowReader reader;
    private BackfillCoverageService coverage;
    private LedgerBackfillService service;
    private EdgeLedgerSimulator sim;
    private BackfillEngine engine;

    /** Creates and migrates a fresh database. */
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
        databaseName = "r15_backfill_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = DriverManager.getConnection(adminUrl, user, password); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + databaseName);
        }
        String url = withDatabase(adminUrl, databaseName);
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration")
                .placeholderReplacement(false).load().migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
        jdbc = new JdbcTemplate(ds);
        txManager = new DataSourceTransactionManager(ds);
    }

    /** Drops the database and stops the container if one was started. */
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

    /** Fresh components and mocks per test (each test uses its own PSPs). */
    @BeforeEach
    void wire() {
        audit = Mockito.mock(AuditLogService.class);
        props = new BackfillProperties();
        props.setBatchRows(2);
        props.setInitialBackoffMs(10);
        props.setMaxBackoffMs(20);
        store = new BackfillRunStore(jdbc, mapper);
        reader = new LegacyRowReader(jdbc, mapper, props);
        coverage = new BackfillCoverageService(jdbc, props);
        PspEdgeSettingsRepository settings = Mockito.mock(PspEdgeSettingsRepository.class);
        Mockito.when(settings.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(settingsByPsp.get((Long) i.getArgument(0))));
        PspRepository psps = Mockito.mock(PspRepository.class);
        Mockito.when(psps.findById(anyLong())).thenAnswer(i -> {
            Psp p = new Psp();
            p.setPspId(i.getArgument(0));
            p.setRetentionYears(5);
            return Optional.of(p);
        });
        Mockito.when(psps.existsById(anyLong())).thenReturn(true);
        EdgeNodeRepository nodes = Mockito.mock(EdgeNodeRepository.class);
        Mockito.when(nodes.findByPspIdAndStatus(anyLong(), eq(EdgeNodeStatus.ACTIVE)))
                .thenAnswer(i -> nodesByPsp.getOrDefault((Long) i.getArgument(0), List.of()));
        PspIsolationService isolation = Mockito.mock(PspIsolationService.class);
        Mockito.when(isolation.getCurrentUser()).thenAnswer(i -> currentUser.get());
        Mockito.when(isolation.isPlatformAdministrator(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            return u != null && u.getPsp() == null;
        });
        EdgeTunnelAccess tunnel = Mockito.mock(EdgeTunnelAccess.class);
        SupportAccessPolicy policy = psp -> supportAccess.getOrDefault(psp, false);
        Mockito.when(tunnel.supportAccess()).thenReturn(policy);
        LedgerBackfillAccess access = new LedgerBackfillAccess(isolation, tunnel);
        service = new LedgerBackfillService(store, reader, coverage, access, psps, settings, nodes, audit, props, txManager);
        this.settingsRepo = settings;
    }

    private PspEdgeSettingsRepository settingsRepo;

    // ── scenarios ────────────────────────────────────────────────────────────────────────────────

    @Test
    void backfillsVerifiesMintsReferencesAndOpensThePurgeGate() throws Exception {
        long psp = psp("R15A", true, true, "site-a");
        long other = psp("R15B", true, true, "site-b");
        long t1 = txn(psp, "ORD-1", "m-1", 5_000, 3, null);
        long t2 = txn(psp, "ORD-2", "m-1", 7_000, 2, "{\"device_id\":\"dv-1\",\"pan\":\"4111111111111111\","
                + "\"cvv\":\"999\",\"note\":\"4111 1111 1111 1111\"}");
        long t3 = txn(psp, "ORD-3", "m-2", 9_000, 1, null);
        long old = txnAt(psp, "ORD-OLD", "m-1", 1_000, LocalDateTime.now().minusYears(6));
        long foreign = txn(other, "ORD-X", "m-9", 123, 1, null);
        jdbc.update("UPDATE transactions SET iso_msg = '0200...4111111111111111...', "
                + "emv_tags = '{\"5A\":\"4111111111111111\",\"57\":\"4111111111111111D2512\"}'::jsonb, "
                + "customer_email = 'secret@example.com' WHERE txn_id = ?", t2);
        jdbc.update("UPDATE transactions SET triggered_rules = '[\"R15 velocity\",\"Unknown legacy rule\"]' WHERE txn_id = ?", t1);
        long ruleId = jdbc.queryForObject("INSERT INTO rule_definitions (name, psp_id) VALUES ('R15 velocity', ?) "
                + "RETURNING id", Long.class, psp);

        // t2 has a legacy cloud assessment (R8 gave its workflow rows that id); t1 has none.
        UUID assessment = UUID.randomUUID();
        jdbc.update("INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, txn_id, decision, created_at) "
                + "VALUES (?, ?, 'TXN', ?, ?, 'HOLD', NOW())", assessment, psp, String.valueOf(t2), t2);
        long alert1 = jdbc.queryForObject("INSERT INTO alerts (txn_id, psp_id, status, action, created_at) "
                + "VALUES (?, ?, 'open', 'HOLD', NOW()) RETURNING alert_id", Long.class, t1, psp);
        jdbc.update("INSERT INTO alert_transactions (alert_id, txn_id, primary_link) VALUES (?, ?, true)", alert1, t1);
        long alert2 = jdbc.queryForObject("INSERT INTO alerts (txn_id, psp_id, edge_assessment_id, status, action, "
                + "created_at) VALUES (?, ?, ?, 'open', 'HOLD', NOW()) RETURNING alert_id", Long.class, t2, psp, assessment);
        long caseId = jdbc.queryForObject("INSERT INTO compliance_cases (psp_id, status, priority, case_reference, "
                + "created_at, updated_at) VALUES (?, 'NEW', 'MEDIUM', ?, NOW(), NOW()) RETURNING case_id", Long.class, psp,
                "CASE-" + psp);
        long caseTxn = jdbc.queryForObject("INSERT INTO case_transactions (case_id, transaction_id, relationship_type, "
                + "added_at) VALUES (?, ?, 'PRIMARY', NOW()) RETURNING id", Long.class, caseId, t1);
        int rev = jdbc.queryForObject("INSERT INTO revinfo (revtstmp) VALUES (0) RETURNING rev", Integer.class);
        jdbc.update("INSERT INTO case_transactions_aud (id, rev, revtype, case_id, transaction_id) VALUES (?, ?, 0, ?, ?)",
                caseTxn, rev, caseId, t1);
        long sarId = jdbc.queryForObject("INSERT INTO suspicious_activity_reports (sar_reference, status, case_id, psp_id, "
                + "created_at, jurisdiction, narrative, sar_type, suspicion_arose_at, suspicion_timestamp_source, "
                + "suspicious_activity_type) VALUES (?, 'DRAFT', ?, ?, NOW(), 'KE', 'n', 'INITIAL', NOW(), 'MANUAL', "
                + "'FRAUD') RETURNING id", Long.class, "SAR-" + psp, caseId, psp);
        jdbc.update("INSERT INTO sar_transactions (sar_id, txn_id) VALUES (?, ?)", sarId, t1);
        jdbc.update("INSERT INTO screening_activities (psp_id, txn_id, subject_type) VALUES (?, ?, 'TRANSACTION')", psp, t1);
        UUID finding = UUID.randomUUID();
        jdbc.update("INSERT INTO findings (id, assessment_id, txn_id, edge_assessment_id, source_type, triggered) "
                + "VALUES (?, ?, ?, ?, 'RULE', true)", finding, assessment, t2, assessment);
        long customer = jdbc.queryForObject("INSERT INTO multi_asset_customers (psp_id, external_customer_id, "
                + "customer_type, display_name) VALUES (?, ?, 'INDIVIDUAL', 'Wanjiru K') RETURNING id", Long.class, psp,
                "cust-" + psp);
        long mat = jdbc.queryForObject("INSERT INTO multi_asset_transactions (psp_id, external_transaction_id, customer_id, "
                + "asset_class, product_domain, transaction_type, amount, currency, executed_at, decision, risk_score, "
                + "beneficiary_name) VALUES (?, 'MM-1', ?, 'E_MONEY', 'E_MONEY_MOBILE_MONEY', 'TRANSFER', 150.25, 'KES', "
                + "NOW() - INTERVAL '1 day', 'REVIEW', 40, 'Juma B') RETURNING id", Long.class, psp, customer);
        jdbc.update("INSERT INTO mobile_money_transaction_contexts (psp_id, transaction_id, wallet_reference, event_type, "
                + "executed_at) VALUES (?, ?, 'wallet-77', 'P2P_TRANSFER', NOW() - INTERVAL '1 day')", psp, mat);
        // A multi-asset row whose PSP reference equals card row t1's: the Edge allows one row per txnRef.
        long reused = jdbc.queryForObject("INSERT INTO multi_asset_transactions (psp_id, external_transaction_id, "
                + "customer_id, asset_class, product_domain, transaction_type, amount, currency, executed_at, decision) "
                + "VALUES (?, 'ORD-1', ?, 'BANKING', 'BANKING', 'TRANSFER', 10, 'KES', NOW() - INTERVAL '1 hour', 'ALLOW') "
                + "RETURNING id", Long.class, psp, customer);

        assertThat(coverage.purgeReadiness(psp).reasons()).contains("NO_VERIFIED_RUN");
        assertThatThrownBy(() -> coverage.requirePurgeAllowed(psp)).isInstanceOf(IllegalStateException.class);

        sim = new EdgeLedgerSimulator(psp, "site-a");
        engine = engine(sim);
        asPlatformAdmin();
        List<BackfillRun> runs = service.start(psp, new LedgerBackfillService.StartRequest(null, "Ticket R15-1 migrate legacy"));
        assertThat(runs).hasSize(1);
        BackfillRun run = runs.get(0);
        assertThat(run.siteId()).isEqualTo("site-a");
        assertThat(run.expectedRows()).as("t1, t2, t3, mat, reused; not the 6-year-old row").isEqualTo(5);

        // Offline, busy and a lost acknowledgement along the way.
        sim.fault(EdgeLedgerSimulator.Fault.OFFLINE);
        assertThat(engine.step(load(run))).isFalse();
        assertThat(load(run).lastErrorCode()).isEqualTo("EDGE_OFFLINE");
        assertThat(load(run).state()).isEqualTo(BackfillState.PENDING);
        drive(run, 1);
        assertThat(load(run).state()).isEqualTo(BackfillState.RUNNING);
        sim.fault(EdgeLedgerSimulator.Fault.BUSY);
        assertThat(engine.step(load(run))).isFalse();
        assertThat(load(run).nextAttemptAt()).isAfter(java.time.Instant.now().plusMillis(1_000));
        sim.fault(EdgeLedgerSimulator.Fault.TIMEOUT_AFTER_APPLY);
        assertThat(engine.step(load(run))).isFalse();
        assertThat(load(run).nextBatchSeq()).as("the lost ack is not recorded").isZero();
        drive(run, 50);

        BackfillRun done = load(run);
        assertThat(done.state()).isEqualTo(BackfillState.VERIFIED);
        assertThat(done.edgeIdDigest()).isEqualTo(done.expectedIdDigest());
        assertThat(done.sampleMismatches()).isZero();
        assertThat(done.rowsDuplicate()).as("the re-sent batch after the lost ack").isEqualTo(2);
        assertThat(sim.held(run.backfillId().toString()))
                .containsExactlyInAnyOrder("txn-" + t1, "txn-" + t2, "txn-" + t3, "mat-" + mat);

        // The reused PSP reference collides with t1 on the Edge: a back-filled row, so an operator decides.
        BackfillRunStore.RowRecord rejected = store.rejected(run.backfillId(), 0, 10).get(0);
        assertThat(rejected.legacyTransactionId()).isEqualTo("mat-" + reused);
        assertThat(rejected.rejectCode()).isEqualTo("TXN_REF_CONFLICT");
        assertThat(rejected.resolution()).isNull();

        // Nothing class S left the cloud; iso_msg, EMV tags and e-mail were never read.
        String wire = String.join("\n", sim.received);
        assertThat(wire).doesNotContain("4111111111111111").doesNotContain("4111 1111").doesNotContain("\"cvv\"")
                .doesNotContain("secret@example.com").doesNotContain("D2512").doesNotContain("\"txn-" + foreign + "\"")
                .doesNotContain("\"txn-" + old + "\"");
        assertThat(wire).contains("\"triggeredRuleIds\":[" + ruleId + "]").contains("Legacy rule Unknown legacy rule");

        // Decision records: minted for t1 (class D only), completed for t2.
        UUID minted = LegacyBackfillIds.mint(psp, "txn-" + t1);
        Map<String, Object> rec = jdbc.queryForMap("SELECT * FROM assessments WHERE edge_assessment_id = ?", minted);
        assertThat(rec.get("id")).isEqualTo(minted);
        assertThat(rec.get("site_id")).isEqualTo("site-a");
        assertThat(rec.get("evaluator")).isEqualTo("cloud-legacy");
        assertThat(rec.get("txn_ref")).isEqualTo("ORD-1");
        assertThat(rec.get("psp_id")).isEqualTo(psp);
        Map<String, Object> rec2 = jdbc.queryForMap("SELECT edge_assessment_id, site_id FROM assessments WHERE id = ?",
                assessment);
        assertThat(rec2.get("edge_assessment_id")).isEqualTo(assessment);
        assertThat(rec2.get("site_id")).isEqualTo("site-a");

        // R8 references that were NULL now point at the minted id; existing ones gained the site.
        assertThat(jdbc.queryForObject("SELECT edge_assessment_id FROM alerts WHERE alert_id = ?", UUID.class, alert1))
                .isEqualTo(minted);
        assertThat(jdbc.queryForObject("SELECT site_id FROM alerts WHERE alert_id = ?", String.class, alert2))
                .isEqualTo("site-a");
        assertThat(jdbc.queryForObject("SELECT edge_assessment_id FROM alert_transactions WHERE alert_id = ?", UUID.class,
                alert1)).isEqualTo(minted);
        assertThat(jdbc.queryForObject("SELECT edge_assessment_id FROM case_transactions WHERE id = ?", UUID.class, caseTxn))
                .isEqualTo(minted);
        assertThat(jdbc.queryForObject("SELECT edge_assessment_id FROM case_transactions_aud WHERE id = ?", UUID.class,
                caseTxn)).isEqualTo(minted);
        assertThat(jdbc.queryForObject("SELECT edge_assessment_id FROM sar_transactions WHERE sar_id = ?", UUID.class,
                sarId)).isEqualTo(minted);
        assertThat(jdbc.queryForObject("SELECT edge_assessment_id FROM screening_activities WHERE txn_id = ?", UUID.class,
                t1)).isEqualTo(minted);
        assertThat(jdbc.queryForObject("SELECT site_id FROM findings WHERE id = ?", String.class, finding))
                .isEqualTo("site-a");

        // The purge gate: refused while the rejected row waits for a decision, open after it.
        BackfillCoverageService.PurgeReadiness before = coverage.purgeReadiness(psp);
        assertThat(before.allowed()).isFalse();
        assertThat(before.uncoveredRows()).isEqualTo(1);
        assertThat(before.reasons()).containsExactly("UNCOVERED_ROWS", "REJECTIONS_PENDING_DECISION");
        asPspUser(psp, "MLRO");
        assertThat(service.exclude(psp, run.backfillId(), List.of("mat-" + reused), "Duplicate PSP reference, data kept on t1"))
                .isEqualTo(1);
        assertThat(coverage.requirePurgeAllowed(psp).allowed()).isTrue();

        // Report: counts per run, readable by the PSP itself.
        LedgerBackfillService.PspReport report = service.report(psp, null);
        LedgerBackfillService.RunReport r = report.runs().get(0);
        assertThat(r.rowsSent()).isEqualTo(5);
        assertThat(r.held()).isEqualTo(4);
        assertThat(r.verified()).isEqualTo(4);
        assertThat(r.accepted()).as("batch 0 was first acknowledged by its re-send").isEqualTo(2);
        assertThat(r.duplicates()).isEqualTo(2);
        assertThat(r.rejectedByCode()).isEqualTo(Map.of("TXN_REF_CONFLICT", 1L));
        assertThat(r.resolvedBy()).isEqualTo(Map.of("EXCLUDED", 1L));
        assertThat(r.pendingDecision()).isZero();
        assertThat(report.purge().allowed()).isTrue();

        // Tenant isolation: the other PSP was never read, written or covered.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_backfill_rows WHERE psp_id = ?", Long.class, other))
                .isZero();
        assertThat(coverage.purgeReadiness(other).allowed()).isFalse();
        asPspUser(other, "PSP_ADMIN");
        assertThatThrownBy(() -> service.report(psp, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.abort(psp, run.backfillId(), "not my PSP at all")).isInstanceOf(SecurityException.class);

        // A row ingested after the window closes the gate again until a top-up run covers it.
        long late = txn(psp, "ORD-LATE", "m-1", 100, 0, null);
        assertThat(coverage.purgeReadiness(psp).allowed()).isFalse();
        asPlatformAdmin();
        BackfillRun topUp = service.start(psp, new LedgerBackfillService.StartRequest(null, "Ticket R15-1 top-up run")).get(0);
        assertThat(topUp.expectedRows()).isEqualTo(1);
        drive(topUp, 20);
        assertThat(load(topUp).state()).isEqualTo(BackfillState.VERIFIED);
        assertThat(sim.held(topUp.backfillId().toString())).containsExactly("txn-" + late);
        assertThat(coverage.requirePurgeAllowed(psp).allowed()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE psp_id = ?", Long.class, psp))
                .as("R15 never deletes cloud data").isEqualTo(5);
        Mockito.verify(audit, Mockito.atLeastOnce()).logAction(any(), eq("EDGE_BACKFILL_STARTED"), eq("EDGE_BACKFILL_RUN"),
                any(), any(), any(), any(), eq("Ticket R15-1 migrate legacy"));
        Mockito.verify(audit, Mockito.atLeastOnce()).logAction(any(), eq("EDGE_BACKFILL_VERIFIED"), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void resumesAfterRestartPauseAndVerifyMismatch() {
        long psp = psp("R15C", true, true, "site-c");
        for (int i = 0; i < 5; i++) {
            txn(psp, "C-" + i, "m-1", 100 + i, 5 - i, null);
        }
        sim = new EdgeLedgerSimulator(psp, "site-c");
        engine = engine(sim);
        asPlatformAdmin();
        BackfillRun run = service.start(psp, new LedgerBackfillService.StartRequest("site-c", "Ticket R15-2 resume test")).get(0);
        drive(run, 2);
        assertThat(load(run).nextBatchSeq()).isEqualTo(1);

        asPspUser(psp, "PSP_ADMIN");
        service.pause(psp, run.backfillId(), "Pausing over the weekend freeze");
        assertThat(engine.step(load(run))).isFalse();
        assertThat(load(run).nextBatchSeq()).as("a paused run does not move").isEqualTo(1);
        assertThatThrownBy(() -> service.resume(psp, run.backfillId(), "PSP cannot resume itself"))
                .isInstanceOf(SecurityException.class);

        // A Control Plane restart: new engine, same database state.
        asPlatformAdmin();
        service.resume(psp, run.backfillId(), "Ticket R15-2 resume after freeze");
        engine = engine(sim);
        sim.fault(EdgeLedgerSimulator.Fault.FRAME_TOO_LARGE);
        assertThat(engine.step(load(run))).isTrue();
        assertThat(load(run).batchByteBudget()).isEqualTo(props.getBatchBytes() / 2);
        drive(run, 4);
        assertThat(load(run).phase()).isEqualTo(BackfillPhase.COMMIT);
        sim.fault(EdgeLedgerSimulator.Fault.FORGET_ONE_HELD_ROW_AT_COMMIT);
        drive(run, 30);
        BackfillRun done = load(run);
        assertThat(done.state()).isEqualTo(BackfillState.VERIFIED);
        assertThat(done.resendRound()).as("one full re-send after VERIFY_MISMATCH").isEqualTo(1);
        assertThat(sim.held(run.backfillId().toString())).hasSize(5);
        assertThat(coverage.requirePurgeAllowed(psp).allowed()).isTrue();
    }

    @Test
    void abortsFailsOnWithdrawnConsentAndEnforcesAccess() {
        long psp = psp("R15D", true, true, "site-d");
        txn(psp, "D-1", "m-1", 100, 1, null);
        txn(psp, "D-2", "m-1", 100, 1, null);
        txn(psp, "D-3", "m-1", 100, 1, null);
        sim = new EdgeLedgerSimulator(psp, "site-d");
        engine = engine(sim);

        asPspUser(psp, "PSP_ADMIN");
        assertThatThrownBy(() -> service.start(psp, new LedgerBackfillService.StartRequest(null, "PSP starting itself")))
                .isInstanceOf(SecurityException.class);
        asPlatformAdmin();
        assertThatThrownBy(() -> service.start(psp, new LedgerBackfillService.StartRequest(null, "short")))
                .isInstanceOf(IllegalArgumentException.class);
        supportAccess.put(psp, false);
        assertThatThrownBy(() -> service.start(psp, new LedgerBackfillService.StartRequest(null, "Ticket R15-3 no consent")))
                .isInstanceOf(SecurityException.class);
        supportAccess.put(psp, true);
        settingsByPsp.get(psp).setTunnelBackfillAllowed(false);
        assertThatThrownBy(() -> service.start(psp, new LedgerBackfillService.StartRequest(null, "Ticket R15-3 not allowed")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ledger.backfill");
        settingsByPsp.get(psp).setTunnelBackfillAllowed(true);

        BackfillRun run = service.start(psp, new LedgerBackfillService.StartRequest(null, "Ticket R15-3 abort test")).get(0);
        assertThatThrownBy(() -> service.start(psp, new LedgerBackfillService.StartRequest(null, "Ticket R15-3 second")))
                .isInstanceOf(ResponseStatusException.class);
        drive(run, 2);

        // The Edge withdraws consent: non-retryable, FAILED; resumable once fixed.
        sim.backfillAllowed = false;
        assertThat(engine.step(load(run))).isFalse();
        assertThat(load(run).state()).isEqualTo(BackfillState.FAILED);
        assertThat(load(run).lastErrorCode()).isEqualTo("BACKFILL_NOT_ALLOWED");
        sim.backfillAllowed = true;

        asPspUser(psp, "COMPLIANCE_OFFICER");
        service.abort(psp, run.backfillId(), "PSP decided to stop the migration");
        drive(run, 3);
        assertThat(load(run).state()).isEqualTo(BackfillState.ABORTED);
        assertThat(sim.state(run.backfillId().toString())).isEqualTo("ABORTED");
        assertThat(coverage.purgeReadiness(psp).allowed()).isFalse();
        assertThatThrownBy(() -> service.resume(psp, run.backfillId(), "x".repeat(20)))
                .isInstanceOf(SecurityException.class);
        asPlatformAdmin();
        assertThatThrownBy(() -> service.resume(psp, run.backfillId(), "Ticket R15-3 resume aborted"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void routesMerchantsToTheirSiteAndAmbiguousOnesToTheDefault() {
        long psp = psp("R15E", true, true, "site-1", "site-2");
        long onTwo = txn(psp, "E-1", "m-two", 100, 2, null);
        long ambiguous = txn(psp, "E-2", "m-both", 100, 2, null);
        long unknown = txn(psp, "E-3", "m-none", 100, 2, null);
        decisionRecord(psp, "m-two", "site-2");
        decisionRecord(psp, "m-both", "site-1");
        decisionRecord(psp, "m-both", "site-2");
        asPlatformAdmin();
        assertThatThrownBy(() -> service.start(psp, new LedgerBackfillService.StartRequest(null, "Ticket R15-4 no default")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultSiteId");
        List<BackfillRun> runs = service.start(psp, new LedgerBackfillService.StartRequest("site-1", "Ticket R15-4 routing"));
        assertThat(runs).extracting(BackfillRun::siteId).containsExactly("site-1", "site-2");
        BackfillRun one = runs.get(0);
        BackfillRun two = runs.get(1);
        assertThat(one.expectedRows()).isEqualTo(2);
        assertThat(two.expectedRows()).isEqualTo(1);

        EdgeLedgerSimulator s1 = new EdgeLedgerSimulator(psp, "site-1");
        EdgeLedgerSimulator s2 = new EdgeLedgerSimulator(psp, "site-2");
        engine = engine(s1);
        drive(one, 20);
        engine = engine(s2);
        drive(two, 20);
        assertThat(load(one).state()).isEqualTo(BackfillState.VERIFIED);
        assertThat(load(two).state()).isEqualTo(BackfillState.VERIFIED);
        assertThat(s1.held(one.backfillId().toString())).containsExactlyInAnyOrder("txn-" + ambiguous, "txn-" + unknown);
        assertThat(s2.held(two.backfillId().toString())).containsExactly("txn-" + onTwo);
        assertThat(jdbc.queryForObject("SELECT site_id FROM assessments WHERE edge_assessment_id = ?", String.class,
                LegacyBackfillIds.mint(psp, "txn-" + onTwo))).isEqualTo("site-2");
        assertThat(coverage.requirePurgeAllowed(psp).allowed()).isTrue();
    }

    @Test
    void resolvesConflictsWithLiveEdgeEvaluations() {
        long psp = psp("R15F", true, true, "site-f");
        long dual = txn(psp, "F-DUAL", "m-1", 100, 1, null);
        UUID edgeId = UUID.randomUUID();
        jdbc.update("INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, txn_id, edge_assessment_id, created_at) "
                + "VALUES (?, ?, 'TXN', ?, ?, ?, NOW())", UUID.randomUUID(), psp, String.valueOf(dual), dual, edgeId);
        long refOnly = txn(psp, "F-REF", "m-1", 100, 1, null);
        sim = new EdgeLedgerSimulator(psp, "site-f");
        sim.preloadLive(edgeId.toString(), "F-DUAL");
        sim.preloadLive(UUID.randomUUID().toString(), "F-REF");
        engine = engine(sim);
        asPlatformAdmin();
        BackfillRun run = service.start(psp, new LedgerBackfillService.StartRequest(null, "Ticket R15-5 dual post")).get(0);
        drive(run, 20);
        assertThat(load(run).state()).isEqualTo(BackfillState.VERIFIED);
        List<BackfillRunStore.RowRecord> rejected = store.rejected(run.backfillId(), 0, 10);
        assertThat(rejected).extracting(BackfillRunStore.RowRecord::rejectCode)
                .containsExactlyInAnyOrder("ID_CONFLICT", "TXN_REF_CONFLICT");
        assertThat(rejected).extracting(BackfillRunStore.RowRecord::resolution).containsOnly("PRESENT_ON_EDGE");
        assertThat(coverage.requirePurgeAllowed(psp).allowed()).isTrue();
    }

    @Test
    void workerClaimsRunsUnderALeaseAndDrivesThemToVerified() {
        long psp = psp("R15G", true, true, "site-g");
        txn(psp, "G-1", "m-1", 100, 2, null);
        txn(psp, "G-2", "m-1", 100, 1, null);
        txn(psp, "G-3", "m-1", 100, 0, null);
        sim = new EdgeLedgerSimulator(psp, "site-g");
        engine = engine(sim);
        asPlatformAdmin();
        BackfillRun run = service.start(psp, new LedgerBackfillService.StartRequest(null, "Ticket R15-6 worker run")).get(0);

        // Two instances never hold the same run: the second claim waits for the lease.
        Optional<UUID> first = store.claimDue("cp-1", 60_000);
        assertThat(first).isPresent();
        jdbc.update("UPDATE ledger_backfill_runs SET lease_until = NOW() + INTERVAL '1 minute' WHERE state IN "
                + "('PENDING','RUNNING','ABORTING') AND backfill_id <> ? AND lease_until IS NULL", first.get());
        assertThat(store.claimDue("cp-2", 60_000)).isEmpty();
        assertThat(store.renewLease(first.get(), "cp-2", 60_000)).isFalse();
        store.releaseLease(first.get(), "cp-1");

        props.setSliceMs(30_000);
        LedgerBackfillWorker worker = new LedgerBackfillWorker(store, engine, props);
        for (int i = 0; i < 20 && load(run).state() != BackfillState.VERIFIED; i++) {
            jdbc.update("UPDATE ledger_backfill_runs SET next_attempt_at = NULL, lease_until = NULL, lease_owner = NULL "
                    + "WHERE backfill_id = ?", run.backfillId());
            worker.tick();
        }
        assertThat(load(run).state()).isEqualTo(BackfillState.VERIFIED);
        assertThat(jdbc.queryForObject("SELECT lease_owner FROM ledger_backfill_runs WHERE backfill_id = ?", String.class,
                run.backfillId())).as("lease released").isNull();
        assertThat(sim.held(run.backfillId().toString())).hasSize(3);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** An engine over the simulator with the test's components. */
    private BackfillEngine engine(EdgeLedgerSimulator edge) {
        BackfillVerifier verifier = new BackfillVerifier(store, reader, props);
        return new BackfillEngine(store, reader, new BackfillReferenceUpdater(jdbc), verifier, edge, settingsRepo, audit,
                props, jdbc, txManager, mapper);
    }

    /** Steps a run until it is no longer driven or {@code max} steps were taken (back-off ignored). */
    private void drive(BackfillRun run, int max) {
        for (int i = 0; i < max; i++) {
            BackfillRun current = load(run);
            if (!BackfillState.DRIVEN.contains(current.state())) {
                return;
            }
            engine.step(current);
        }
    }

    /** The run's current row. */
    private BackfillRun load(BackfillRun run) {
        return store.find(run.backfillId()).orElseThrow();
    }

    /** A PSP with settings, support access, active sites. */
    private long psp(String code, boolean support, boolean backfillAllowed, String... sites) {
        long id = jdbc.queryForObject("INSERT INTO psps (psp_code, legal_name, country, contact_email) VALUES (?, ?, 'KEN', "
                + "'it@example.com') RETURNING psp_id", Long.class, code + "-" + UUID.randomUUID().toString().substring(0, 6),
                "R15 " + code);
        PspEdgeSettings s = PspEdgeSettings.defaultsFor(id);
        List<String> ops = new ArrayList<>(PspEdgeSettings.DEFAULT_TUNNEL_OPS);
        ops.add("ledger.backfill");
        s.setTunnelOpsAllowed(ops);
        s.setTunnelBackfillAllowed(backfillAllowed);
        settingsByPsp.put(id, s);
        supportAccess.put(id, support);
        List<EdgeNode> list = new ArrayList<>();
        for (String site : sites) {
            EdgeNode n = new EdgeNode();
            n.setPspId(id);
            n.setEdgeId(site + "-node");
            n.setSiteId(site);
            n.setStatus(EdgeNodeStatus.ACTIVE);
            list.add(n);
        }
        nodesByPsp.put(id, list);
        return id;
    }

    /** A legacy card transaction with a features row, {@code daysAgo} days old. */
    private long txn(long psp, String ref, String merchant, long amount, int daysAgo, String featureJson) {
        long id = txnAt(psp, ref, merchant, amount, LocalDateTime.now().minusDays(daysAgo).withNano(123_456_000));
        jdbc.update("INSERT INTO transaction_features (txn_id, psp_id, feature_json, score, action_taken, scored_at) "
                + "VALUES (?, ?, ?::jsonb, 0.42, 'ALERT', NOW())", id, psp,
                featureJson == null ? "{\"device_id\":\"dv-" + id + "\"}" : featureJson);
        return id;
    }

    /** A legacy card transaction at {@code ts}. */
    private long txnAt(long psp, String ref, String merchant, long amount, LocalDateTime ts) {
        return jdbc.queryForObject("INSERT INTO transactions (psp_id, merchant_id, pan_hash, amount_cents, currency, "
                + "client_reference, txn_ts, created_at, decision, merchant_country, channel_type) VALUES (?, ?, ?, ?, 'KES', "
                + "?, ?, NOW(), 'ALERT', 'KEN', 'POS') RETURNING txn_id", Long.class, psp, merchant, "h".repeat(64), amount,
                ref, ts);
    }

    /** An Edge decision record of {@code merchant} at {@code site}. */
    private void decisionRecord(long psp, String merchant, String site) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, edge_assessment_id, record_version, "
                + "site_id, merchant_id, decision, created_at) VALUES (?, ?, 'TXN', ?, ?, 2, ?, ?, 'ALLOW', NOW())",
                id, psp, id.toString(), id, site, merchant);
    }

    /** Signs in as a platform operator. */
    private void asPlatformAdmin() {
        Role role = new Role();
        role.setName("PLATFORM_ADMIN");
        currentUser.set(User.builder().id(1L).username("ops").email("o@x").passwordHash("").role(role).build());
    }

    /** Signs in as a user of {@code psp} with {@code roleName}. */
    private void asPspUser(long psp, String roleName) {
        Role role = new Role();
        role.setName(roleName);
        Psp p = new Psp();
        p.setPspId(psp);
        currentUser.set(User.builder().id(100 + psp).username("u-" + psp).email("x").passwordHash("").role(role).psp(p)
                .build());
    }

    /** The JDBC URL with its database replaced. */
    private static String withDatabase(String jdbcUrl, String database) {
        int query = jdbcUrl.indexOf('?');
        String base = query < 0 ? jdbcUrl : jdbcUrl.substring(0, query);
        String params = query < 0 ? "" : jdbcUrl.substring(query);
        return base.substring(0, base.lastIndexOf('/') + 1) + database + params;
    }
}
