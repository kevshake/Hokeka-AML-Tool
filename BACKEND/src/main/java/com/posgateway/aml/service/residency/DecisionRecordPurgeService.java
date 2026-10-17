package com.posgateway.aml.service.residency;

import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.AuditLogService;
import com.posgateway.aml.service.edge.records.DecisionRecordWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Daily purge of cloud decision records past the PSP's retention (contract 8.4).
 *
 * <p>A decision record ({@code assessments} row with {@code record_version = 2}) is deleted when
 * {@code evaluated_at < now − retention_years}, <b>except</b> while workflow still references it.
 * Workflow references a record by its {@code edge_assessment_id} (V264, R8): an alert carrying it
 * (or raised from it: {@code source_type = 'EDGE_DECISION'}, {@code source_reference}), an
 * {@code alert_transactions} link, a {@code case_transactions} link or a {@code sar_transactions}
 * reference (a legacy case or SAR row with no psp_id counts too: keeping a record longer is the safe
 * side). The record is kept while
 * <ul>
 *   <li>the alert is open, or closed less than {@code retention_years} ago (or its closure time is
 *       unknown);</li>
 *   <li>a CTR filing deadline sourced from the alert is not completed;</li>
 *   <li>a case holding the alert (via {@code alerts.compliance_case_id} or
 *       {@code case_alerts.source_alert_id}) is not closed, or closed less than
 *       {@code retention_years} ago (or its resolution time is unknown);</li>
 *   <li>a SAR of such a case is unfiled (DRAFT / PENDING_REVIEW / APPROVED), or filed less than
 *       {@code retention_years} ago;</li>
 *   <li>a case linking the record directly (case_transactions) is open or recently closed, or has
 *       such a SAR;</li>
 *   <li>a SAR referencing the record directly (sar_transactions) is unfiled or filed less than
 *       {@code retention_years} ago;</li>
 *   <li>an ACTIVE legal hold of the PSP covers the record ({@code legal_hold_items}, V269, R9):
 *       holds override retention on both sides (contract 8.3).</li>
 * </ul>
 * Findings go with the record ({@code ON DELETE CASCADE}). The rollup {@code decision_stats_hourly}
 * holds aggregates only and is not touched. Each PSP run that deletes anything is audited.
 */
@Service
public class DecisionRecordPurgeService {

    private static final Logger log = LoggerFactory.getLogger(DecisionRecordPurgeService.class);

    /** Rows deleted per statement, so a large backlog never holds one long lock. */
    static final int BATCH_SIZE = 1000;

    static final String PURGE_SQL = """
            DELETE FROM assessments
             WHERE id IN (
                SELECT d.id FROM assessments d
                 WHERE d.psp_id = ?
                   AND d.record_version = 2
                   AND d.evaluated_at < ?
                   AND NOT EXISTS (
                       SELECT 1 FROM alerts al
                        WHERE al.psp_id = d.psp_id
                          AND (al.edge_assessment_id = d.edge_assessment_id
                               OR (al.source_type = '%s'
                                   AND al.source_reference = d.edge_assessment_id::text)
                               OR EXISTS (SELECT 1 FROM alert_transactions x
                                           WHERE x.alert_id = al.alert_id
                                             AND x.edge_assessment_id = d.edge_assessment_id))
                          AND (
                               LOWER(al.status) NOT IN ('closed', 'false_positive', 'resolved', 'dismissed')
                            OR COALESCE(al.closed_at, al.disposed_at) IS NULL
                            OR COALESCE(al.closed_at, al.disposed_at) >= ?
                            OR EXISTS (SELECT 1 FROM compliance_deadlines cd
                                        WHERE cd.source_type = 'ALERT' AND cd.source_id = al.alert_id
                                          AND cd.completed = FALSE)
                            OR EXISTS (SELECT 1 FROM compliance_cases c
                                        WHERE (c.case_id = al.compliance_case_id
                                               OR c.case_id IN (SELECT ca.case_id FROM case_alerts ca
                                                                 WHERE ca.source_alert_id = al.alert_id))
                                          AND (c.status NOT LIKE 'CLOSED%%'
                                               OR c.resolved_at IS NULL
                                               OR c.resolved_at >= ?
                                               OR EXISTS (SELECT 1 FROM suspicious_activity_reports s
                                                           WHERE s.case_id = c.case_id
                                                             AND (s.status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED')
                                                                  OR s.filed_at IS NULL
                                                                  OR s.filed_at >= ?))))
                          )
                   )
                   AND NOT EXISTS (
                       SELECT 1 FROM case_transactions ct
                         JOIN compliance_cases c ON c.case_id = ct.case_id
                        WHERE ct.edge_assessment_id = d.edge_assessment_id
                          AND (c.psp_id = d.psp_id OR c.psp_id IS NULL)
                          AND (c.status NOT LIKE 'CLOSED%%'
                               OR c.resolved_at IS NULL
                               OR c.resolved_at >= ?
                               OR EXISTS (SELECT 1 FROM suspicious_activity_reports s
                                           WHERE s.case_id = c.case_id
                                             AND (s.status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED')
                                                  OR s.filed_at IS NULL
                                                  OR s.filed_at >= ?)))
                   )
                   AND NOT EXISTS (
                       SELECT 1 FROM sar_transactions st
                         JOIN suspicious_activity_reports s ON s.id = st.sar_id
                        WHERE st.edge_assessment_id = d.edge_assessment_id
                          AND (s.psp_id = d.psp_id OR s.psp_id IS NULL)
                          AND (s.status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED')
                               OR s.filed_at IS NULL
                               OR s.filed_at >= ?)
                   )
                   AND NOT EXISTS (
                       SELECT 1 FROM legal_hold_items hi
                         JOIN legal_holds h ON h.hold_id = hi.hold_id
                        WHERE hi.edge_assessment_id = d.edge_assessment_id
                          AND h.psp_id = d.psp_id
                          AND h.state = 'ACTIVE'
                   )
                 LIMIT ?)
            """.formatted(DecisionRecordWorkflowService.SOURCE_TYPE);

    private final PspRepository psps;
    private final PspDataSettingsService dataSettings;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AuditLogService audit;
    private final Clock clock;

    @Value("${residency.decision-records.purge-enabled:true}")
    private boolean enabled = true;

    @Autowired
    public DecisionRecordPurgeService(PspRepository psps,
                                      PspDataSettingsService dataSettings,
                                      JdbcTemplate jdbc,
                                      PlatformTransactionManager transactionManager,
                                      AuditLogService audit) {
        this(psps, dataSettings, jdbc, new TransactionTemplate(transactionManager), audit, Clock.systemUTC());
    }

    /** Test constructor with an explicit clock and transaction template. */
    public DecisionRecordPurgeService(PspRepository psps,
                                      PspDataSettingsService dataSettings,
                                      JdbcTemplate jdbc,
                                      TransactionTemplate transactions,
                                      AuditLogService audit,
                                      Clock clock) {
        this.psps = psps;
        this.dataSettings = dataSettings;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
    }

    /** Daily run over every PSP (default 03:30 server time). */
    @Scheduled(cron = "${residency.decision-records.purge-cron:0 30 3 * * *}")
    public void purgeAll() {
        if (!enabled) {
            log.debug("Decision record purge disabled (residency.decision-records.purge-enabled=false)");
            return;
        }
        long total = 0;
        for (Psp psp : psps.findAll()) {
            if (psp.getPspId() == null || psp.getPspId() == 0L) {
                continue;
            }
            try {
                total += purgePsp(psp.getPspId());
            } catch (RuntimeException e) {
                // One PSP's failure must not stop the others; the next daily run retries it.
                log.error("Decision record purge failed for PSP {}: {}", psp.getPspId(), e.toString());
            }
        }
        log.info("Decision record purge finished: {} record(s) deleted", total);
    }

    /**
     * Purges one PSP's expired, unreferenced decision records in batches and audits the run.
     *
     * @return number of records deleted
     */
    public long purgePsp(Long pspId) {
        Instant now = clock.instant();
        int years = Math.max(PspDataSettingsService.MIN_RETENTION_YEARS, dataSettings.retentionState(pspId).years());
        Instant cutoff = OffsetDateTime.ofInstant(now, ZoneOffset.UTC).minusYears(years).toInstant();
        OffsetDateTime cutoffTz = OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC);
        // Workflow tables store local TIMESTAMP (no zone): compare in the server's zone.
        LocalDateTime cutoffLocal = LocalDateTime.ofInstant(cutoff, ZoneId.systemDefault());
        long deleted = 0;
        while (true) {
            Integer batch = transactions.execute(status -> jdbc.update(PURGE_SQL,
                    pspId, cutoffTz, cutoffLocal, cutoffLocal, cutoffLocal,
                    cutoffLocal, cutoffLocal, cutoffLocal, BATCH_SIZE));
            int count = batch == null ? 0 : batch;
            deleted += count;
            if (count < BATCH_SIZE) {
                break;
            }
        }
        if (deleted > 0) {
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("pspId", pspId);
            after.put("retentionYears", years);
            after.put("cutoff", cutoff.toString());
            after.put("deleted", deleted);
            audit.logAction(null, "DECISION_RECORD_PURGE", "PSP", String.valueOf(pspId), null, after, null,
                    "Retention purge: decision records evaluated before " + cutoff);
            log.info("Purged {} decision record(s) of PSP {} evaluated before {} ({} years)",
                    deleted, pspId, cutoff, years);
        }
        return deleted;
    }
}
