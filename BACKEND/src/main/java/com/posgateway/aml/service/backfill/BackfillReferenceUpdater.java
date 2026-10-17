package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keeps cloud workflow references resolvable once the Edge holds a back-filled row (contract §11
 * step 2, invariant I4), inside the batch-acknowledgement transaction.
 *
 * <ul>
 *   <li><b>Decision record.</b> A row with a cloud assessment gets that assessment completed as a
 *       decision record: {@code edge_assessment_id} = its existing Edge id, else its own id (the id R8
 *       already wrote into workflow rows); {@code site_id}, {@code evaluated_at} (seconds) and
 *       {@code merchant_id} filled where empty. A row without one gets a new decision record whose
 *       id and Edge id are the minted UUIDv5, holding class-D fields only (no amount, currency,
 *       country, card, customer or free text).</li>
 *   <li><b>Workflow rows R8 left without an Edge id</b> (alerts, alert links, case links, SAR links,
 *       screening activities, findings, and the Envers history of the link tables) get the minted id,
 *       the txnRef when they had none, and the site.</li>
 *   <li><b>Workflow rows that already carry the Edge id</b> get the site where they had none, so the
 *       R6 proxy routes {@code txn.get} to the site that now holds the row.</li>
 * </ul>
 *
 * Every statement is PSP-scoped. Legacy workflow rows created before {@code psp_id} existed may have
 * a NULL PSP; they are matched only by the legacy transaction id, which is globally unique and
 * belongs to the run's PSP (the same rule V264 used).
 */
@Component
public class BackfillReferenceUpdater {

    private final JdbcTemplate jdbc;

    /** Uses the application's primary database. */
    public BackfillReferenceUpdater(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One held row: what was sent and where it now lives. */
    public record HeldRow(LegacyRow source, ObjectNode sent) {}

    /** Applies the decision-record and reference updates for the rows the Edge holds at {@code siteId}. */
    public void apply(long pspId, String siteId, List<HeldRow> held) {
        if (held.isEmpty()) {
            return;
        }
        List<Object[]> complete = new ArrayList<>();
        List<Object[]> insert = new ArrayList<>();
        List<Object[]> byTxn = new ArrayList<>();
        List<Object[]> byTxnUnique = new ArrayList<>();
        List<Object[]> siteFill = new ArrayList<>();
        for (HeldRow h : held) {
            LegacyRow row = h.source();
            UUID edgeId = row.edgeAssessmentId();
            String txnRef = text(h.sent(), "txnRef");
            String merchant = text(h.sent(), "merchantId");
            OffsetDateTime evaluatedSeconds = OffsetDateTime.ofInstant(row.evaluatedAt().truncatedTo(ChronoUnit.SECONDS),
                    ZoneOffset.UTC);
            if (!row.minted()) {
                complete.add(new Object[]{siteId, evaluatedSeconds, merchant, row.assessmentId(), pspId});
            } else {
                Long txnId = row.source() == LegacySource.TXN ? row.sourceId() : null;
                insert.add(new Object[]{edgeId, pspId, row.legacyTransactionId(), txnId, edgeId,
                        h.sent().get("decision").asText(), siteId, txnRef, evaluatedSeconds, merchant,
                        h.sent().get("score").asInt(), h.sent().get("ctrRequired").asBoolean(),
                        h.sent().get("triggeredRuleIds").toString(), h.sent().get("ruleVersionIds").toString()});
                if (txnId != null) {
                    byTxn.add(new Object[]{edgeId, txnRef, siteId, txnId, pspId});
                    byTxnUnique.add(new Object[]{edgeId, txnRef, siteId, txnId, pspId, edgeId});
                }
            }
            siteFill.add(new Object[]{siteId, edgeId, pspId});
        }
        if (!complete.isEmpty()) {
            jdbc.batchUpdate("""
                    UPDATE assessments SET edge_assessment_id = COALESCE(edge_assessment_id, id),
                           site_id = COALESCE(site_id, ?), evaluated_at = COALESCE(evaluated_at, ?),
                           merchant_id = COALESCE(merchant_id, ?)
                     WHERE id = ? AND psp_id = ?
                    """, complete);
        }
        if (!insert.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO assessments (id, psp_id, trigger_type, trigger_ref, txn_id, edge_assessment_id, decision,
                        created_at, site_id, txn_ref, evaluated_at, merchant_id, score, ctr_required, sanctions_hit,
                        evaluator, triggered_rule_ids, rule_version_ids)
                    VALUES (?, ?, 'TXN', ?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, FALSE, 'cloud-legacy', ?::jsonb, ?::jsonb)
                    ON CONFLICT DO NOTHING
                    """, insert);
        }
        if (!byTxn.isEmpty()) {
            mintedReferences(byTxn, byTxnUnique);
        }
        fillSites(siteFill);
    }

    /** R8 rows without an Edge id, by legacy transaction id: they take the minted id. */
    private void mintedReferences(List<Object[]> byTxn, List<Object[]> byTxnUnique) {
        jdbc.batchUpdate("""
                UPDATE alerts SET edge_assessment_id = ?, txn_ref = COALESCE(txn_ref, ?), site_id = COALESCE(site_id, ?)
                 WHERE txn_id = ? AND edge_assessment_id IS NULL AND (psp_id IS NULL OR psp_id = ?)
                """, byTxn);
        jdbc.batchUpdate("""
                UPDATE alert_transactions x SET edge_assessment_id = ?, txn_ref = COALESCE(x.txn_ref, ?),
                       site_id = COALESCE(x.site_id, ?)
                 WHERE x.txn_id = ? AND x.edge_assessment_id IS NULL
                   AND EXISTS (SELECT 1 FROM alerts al WHERE al.alert_id = x.alert_id AND (al.psp_id IS NULL OR al.psp_id = ?))
                   AND NOT EXISTS (SELECT 1 FROM alert_transactions y WHERE y.alert_id = x.alert_id AND y.edge_assessment_id = ?)
                """, byTxnUnique);
        jdbc.batchUpdate("""
                UPDATE case_transactions ct SET edge_assessment_id = ?, txn_ref = COALESCE(ct.txn_ref, ?),
                       site_id = COALESCE(ct.site_id, ?)
                 WHERE ct.transaction_id = ? AND ct.edge_assessment_id IS NULL
                   AND EXISTS (SELECT 1 FROM compliance_cases c WHERE c.case_id = ct.case_id
                                AND (c.psp_id IS NULL OR c.psp_id = ?))
                """, byTxn);
        jdbc.batchUpdate("""
                UPDATE sar_transactions st SET edge_assessment_id = ?, txn_ref = COALESCE(st.txn_ref, ?),
                       site_id = COALESCE(st.site_id, ?)
                 WHERE st.txn_id = ? AND st.edge_assessment_id IS NULL
                   AND EXISTS (SELECT 1 FROM suspicious_activity_reports s WHERE s.id = st.sar_id
                                AND (s.psp_id IS NULL OR s.psp_id = ?))
                   AND NOT EXISTS (SELECT 1 FROM sar_transactions y WHERE y.sar_id = st.sar_id AND y.edge_assessment_id = ?)
                """, byTxnUnique);
        jdbc.batchUpdate("""
                UPDATE screening_activities SET edge_assessment_id = ?, txn_ref = COALESCE(txn_ref, ?),
                       site_id = COALESCE(site_id, ?)
                 WHERE txn_id = ? AND edge_assessment_id IS NULL AND (psp_id IS NULL OR psp_id = ?)
                """, byTxn);
        jdbc.batchUpdate("""
                UPDATE findings f SET edge_assessment_id = ?, txn_ref = COALESCE(f.txn_ref, ?), site_id = COALESCE(f.site_id, ?)
                 WHERE f.txn_id = ? AND f.edge_assessment_id IS NULL
                   AND EXISTS (SELECT 1 FROM assessments a WHERE a.id = f.assessment_id AND a.psp_id = ?)
                """, byTxn);
        List<Object[]> audit = new ArrayList<>();
        for (Object[] a : byTxn) {
            audit.add(new Object[]{a[0], a[1], a[2], a[3], a[0]});
        }
        // Envers history of the link tables mirrors the live rows' references (as V264 did).
        jdbc.batchUpdate("""
                UPDATE case_transactions_aud h SET edge_assessment_id = ?, txn_ref = COALESCE(h.txn_ref, ?),
                       site_id = COALESCE(h.site_id, ?)
                 WHERE h.edge_assessment_id IS NULL
                   AND h.id IN (SELECT ct.id FROM case_transactions ct WHERE ct.transaction_id = ? AND ct.edge_assessment_id = ?)
                """, audit);
        jdbc.batchUpdate("""
                UPDATE sar_transactions_aud h SET edge_assessment_id = ?, txn_ref = COALESCE(h.txn_ref, ?),
                       site_id = COALESCE(h.site_id, ?)
                 WHERE h.edge_assessment_id IS NULL
                   AND h.id IN (SELECT st.id FROM sar_transactions st WHERE st.txn_id = ? AND st.edge_assessment_id = ?)
                """, audit);
    }

    /** Workflow rows that carry the Edge id but no site get the site that now holds the row. */
    private void fillSites(List<Object[]> siteFill) {
        jdbc.batchUpdate("UPDATE alerts SET site_id = ? WHERE edge_assessment_id = ? AND site_id IS NULL "
                + "AND (psp_id IS NULL OR psp_id = ?)", siteFill);
        jdbc.batchUpdate("""
                UPDATE alert_transactions x SET site_id = ? WHERE x.edge_assessment_id = ? AND x.site_id IS NULL
                   AND EXISTS (SELECT 1 FROM alerts al WHERE al.alert_id = x.alert_id AND (al.psp_id IS NULL OR al.psp_id = ?))
                """, siteFill);
        jdbc.batchUpdate("""
                UPDATE case_transactions ct SET site_id = ? WHERE ct.edge_assessment_id = ? AND ct.site_id IS NULL
                   AND EXISTS (SELECT 1 FROM compliance_cases c WHERE c.case_id = ct.case_id AND (c.psp_id IS NULL OR c.psp_id = ?))
                """, siteFill);
        jdbc.batchUpdate("""
                UPDATE sar_transactions st SET site_id = ? WHERE st.edge_assessment_id = ? AND st.site_id IS NULL
                   AND EXISTS (SELECT 1 FROM suspicious_activity_reports s WHERE s.id = st.sar_id
                                AND (s.psp_id IS NULL OR s.psp_id = ?))
                """, siteFill);
        jdbc.batchUpdate("UPDATE screening_activities SET site_id = ? WHERE edge_assessment_id = ? AND site_id IS NULL "
                + "AND (psp_id IS NULL OR psp_id = ?)", siteFill);
        jdbc.batchUpdate("""
                UPDATE findings f SET site_id = ? WHERE f.edge_assessment_id = ? AND f.site_id IS NULL
                   AND EXISTS (SELECT 1 FROM assessments a WHERE a.id = f.assessment_id AND a.psp_id = ?)
                """, siteFill);
    }

    /** A text member of the sent row, or null. */
    private static String text(JsonNode n, String name) {
        JsonNode v = n.get(name);
        return v == null || v.isNull() ? null : v.asText();
    }
}
