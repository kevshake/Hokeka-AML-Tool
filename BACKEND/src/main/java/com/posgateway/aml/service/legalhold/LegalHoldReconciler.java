package com.posgateway.aml.service.legalhold;

import com.posgateway.aml.entity.filing.LegalHold;
import com.posgateway.aml.entity.filing.LegalHoldWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the cloud's legal holds in step with the workflow records that need them (R9). It is the
 * safety net behind the direct hooks (SAR drafting, SAR deletion, filing): whatever path created or
 * changed a record, the next run converges.
 *
 * <ul>
 *   <li><b>SAR</b>: every decision record referenced by an unfiled SAR (DRAFT, PENDING_REVIEW,
 *       APPROVED, REJECTED) is covered by a hold with reasonCode {@code SAR}; a SAR that no longer
 *       exists has its holds released {@code PLACED_IN_ERROR}.</li>
 *   <li><b>CASE</b>: every decision record linked to an {@code ESCALATED} case (escalated to the
 *       MLRO, the step before a SAR) is covered by a hold with reasonCode {@code CASE}. When the
 *       case closes, the hold's release basis is the case's resolution time; a reopened case
 *       clears it; a deleted case releases {@code PLACED_IN_ERROR}.</li>
 *   <li>Holds whose workflow ended {@code retention_years} ago are released
 *       ({@link LegalHoldService#releaseDue}).</li>
 *   <li>Holds a connected site has not confirmed within five minutes are re-sent.</li>
 * </ul>
 */
@Service
public class LegalHoldReconciler {

    private static final Logger log = LoggerFactory.getLogger(LegalHoldReconciler.class);

    /** Actor of reconciler placements. */
    static final String JOB = "system:legal-hold-reconciler";

    /** Unconfirmed deliveries older than this are re-sent to connected sites. */
    static final Duration STALE = Duration.ofMinutes(5);

    static final String UNCOVERED_SAR_REFS = """
            SELECT s.id, s.psp_id, s.sar_reference, st.edge_assessment_id
              FROM suspicious_activity_reports s
              JOIN sar_transactions st ON st.sar_id = s.id
             WHERE s.status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')
               AND s.psp_id IS NOT NULL AND s.psp_id > 0
               AND st.edge_assessment_id IS NOT NULL
               AND NOT EXISTS (
                   SELECT 1 FROM legal_hold_workflows w
                     JOIN legal_holds h ON h.workflow_ref_id = w.id AND h.state = 'ACTIVE'
                     JOIN legal_hold_items i ON i.hold_id = h.hold_id
                    WHERE w.workflow_type = 'SAR' AND w.workflow_id = s.id
                      AND i.edge_assessment_id = st.edge_assessment_id)
             ORDER BY s.id, st.id
            """;

    static final String UNCOVERED_CASE_REFS = """
            SELECT c.case_id, c.psp_id, c.case_reference, ct.edge_assessment_id
              FROM compliance_cases c
              JOIN case_transactions ct ON ct.case_id = c.case_id
             WHERE c.status = 'ESCALATED'
               AND c.psp_id IS NOT NULL AND c.psp_id > 0
               AND ct.edge_assessment_id IS NOT NULL
               AND NOT EXISTS (
                   SELECT 1 FROM legal_hold_workflows w
                     JOIN legal_holds h ON h.workflow_ref_id = w.id AND h.state = 'ACTIVE'
                     JOIN legal_hold_items i ON i.hold_id = h.hold_id
                    WHERE w.workflow_type = 'CASE' AND w.workflow_id = c.case_id
                      AND i.edge_assessment_id = ct.edge_assessment_id)
             ORDER BY c.case_id, ct.id
            """;

    static final String ORPHANED_WORKFLOWS = """
            SELECT DISTINCT w.workflow_type, w.workflow_id
              FROM legal_hold_workflows w
              JOIN legal_holds h ON h.workflow_ref_id = w.id AND h.state = 'ACTIVE'
             WHERE (w.workflow_type = 'SAR'
                    AND NOT EXISTS (SELECT 1 FROM suspicious_activity_reports s WHERE s.id = w.workflow_id))
                OR (w.workflow_type = 'CASE'
                    AND NOT EXISTS (SELECT 1 FROM compliance_cases c WHERE c.case_id = w.workflow_id))
            """;

    static final String CLOSED_CASES_OPEN_BASIS = """
            SELECT DISTINCT c.case_id, c.resolved_at
              FROM legal_hold_workflows w
              JOIN legal_holds h ON h.workflow_ref_id = w.id AND h.state = 'ACTIVE' AND h.release_basis_at IS NULL
              JOIN compliance_cases c ON c.case_id = w.workflow_id
             WHERE w.workflow_type = 'CASE' AND c.status LIKE 'CLOSED%'
            """;

    static final String REOPENED_CASES_WITH_BASIS = """
            SELECT DISTINCT c.case_id
              FROM legal_hold_workflows w
              JOIN legal_holds h ON h.workflow_ref_id = w.id AND h.state = 'ACTIVE' AND h.release_basis_at IS NOT NULL
              JOIN compliance_cases c ON c.case_id = w.workflow_id
             WHERE w.workflow_type = 'CASE' AND c.status NOT LIKE 'CLOSED%'
            """;

    private final JdbcTemplate jdbc;
    private final LegalHoldService holds;
    private final Clock clock;

    @Value("${residency.legal-holds.reconcile-enabled:true}")
    private boolean enabled = true;

    /** Wires the reconciler. */
    @org.springframework.beans.factory.annotation.Autowired
    public LegalHoldReconciler(JdbcTemplate jdbc, LegalHoldService holds) {
        this(jdbc, holds, Clock.systemUTC());
    }

    /** Test constructor with an explicit clock. */
    public LegalHoldReconciler(JdbcTemplate jdbc, LegalHoldService holds, Clock clock) {
        this.jdbc = jdbc;
        this.holds = holds;
        this.clock = clock;
    }

    /** Outcome counts of one run. */
    public record Run(int sarHoldsPlaced, int caseHoldsPlaced, int orphansReleased, int casesEnded,
                      int casesReopened, int retentionReleased, int resent) {}

    /** Scheduled run (default every five minutes). */
    @Scheduled(fixedDelayString = "${residency.legal-holds.reconcile-interval-ms:300000}",
            initialDelayString = "${residency.legal-holds.reconcile-initial-delay-ms:60000}")
    public void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            Run r = reconcile();
            if (r.sarHoldsPlaced() + r.caseHoldsPlaced() + r.orphansReleased() + r.casesEnded() + r.casesReopened()
                    + r.retentionReleased() + r.resent() > 0) {
                log.info("Legal hold reconciliation: {}", r);
            }
        } catch (RuntimeException e) {
            // The next run retries; holds already recorded are not affected.
            log.error("Legal hold reconciliation failed: {}", e.getClass().getSimpleName());
        }
    }

    /** One full reconciliation pass. */
    public Run reconcile() {
        int sar = coverUncovered(UNCOVERED_SAR_REFS, LegalHoldWorkflow.Type.SAR, "SAR");
        int cases = coverUncovered(UNCOVERED_CASE_REFS, LegalHoldWorkflow.Type.CASE, "CASE");
        int orphans = 0;
        for (Map<String, Object> row : jdbc.queryForList(ORPHANED_WORKFLOWS)) {
            orphans += holds.releaseWorkflow(LegalHoldWorkflow.Type.valueOf((String) row.get("workflow_type")),
                    ((Number) row.get("workflow_id")).longValue(), LegalHold.ReleaseReason.PLACED_IN_ERROR, JOB);
        }
        int ended = 0;
        for (Map<String, Object> row : jdbc.queryForList(CLOSED_CASES_OPEN_BASIS)) {
            Object resolved = row.get("resolved_at");
            Instant basis = resolved instanceof Timestamp ts ? ts.toInstant() : clock.instant();
            ended += holds.markWorkflowEnded(LegalHoldWorkflow.Type.CASE, ((Number) row.get("case_id")).longValue(), basis);
        }
        int reopened = 0;
        for (Long caseId : jdbc.queryForList(REOPENED_CASES_WITH_BASIS, Long.class)) {
            reopened += holds.markWorkflowReopened(LegalHoldWorkflow.Type.CASE, caseId);
        }
        int released = holds.releaseDue();
        int resent = holds.resendStale(STALE);
        return new Run(sar, cases, orphans, ended, reopened, released, resent);
    }

    /** Places holds for the uncovered references a query returns, grouped per workflow record. */
    private int coverUncovered(String sql, LegalHoldWorkflow.Type type, String reasonCode) {
        record Key(long id, long pspId, String reference) {}
        Map<Key, List<UUID>> byRecord = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            Key k = new Key(rs.getLong(1), rs.getLong(2), rs.getString(3));
            byRecord.computeIfAbsent(k, x -> new ArrayList<>()).add(rs.getObject(4, UUID.class));
        });
        int placed = 0;
        for (Map.Entry<Key, List<UUID>> e : byRecord.entrySet()) {
            Key k = e.getKey();
            placed += holds.ensureCoverage(k.pspId(), type, k.id(), reasonCode, k.reference(), e.getValue(), JOB)
                    .placedHoldIds().size();
        }
        return placed;
    }
}
