package com.posgateway.aml.service.edge.records;

import com.fasterxml.jackson.databind.JsonNode;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.AssessmentTriggerType;
import com.posgateway.aml.entity.assessment.Finding;
import com.posgateway.aml.entity.assessment.FindingPhase;
import com.posgateway.aml.entity.assessment.FindingSourceType;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores one validated decision record v2 and everything derived from it in a single transaction:
 * the {@code assessments} row (decision record columns, V262), one EDGE_SYNC finding per triggered
 * rule and per watchlist entry, the {@code decision_stats_hourly} bucket and the cloud workflow
 * (alerts, cases, CTR deadline). If any part fails nothing is kept and the Edge re-sends the record.
 */
@Service
public class DecisionRecordStore {

    /** Result of storing one record. */
    public enum Outcome { ACCEPTED, DUPLICATE, CONFLICT }

    private final AssessmentRepository assessments;
    private final FindingRepository findings;
    private final DecisionStatsHourlyStore stats;
    private final DecisionRecordWorkflowService workflow;

    public DecisionRecordStore(AssessmentRepository assessments,
                               FindingRepository findings,
                               DecisionStatsHourlyStore stats,
                               DecisionRecordWorkflowService workflow) {
        this.assessments = assessments;
        this.findings = findings;
        this.stats = stats;
        this.workflow = workflow;
    }

    /**
     * Stores {@code record} for {@code node}'s PSP. Idempotent on {@code edgeAssessmentId}: an equal
     * record (same canonical digest) is a DUPLICATE; any difference, or the id belonging to another
     * PSP, is a CONFLICT and the first record is kept. An existing non-v2 row of the same PSP (a v1
     * envelope or the dual-post adopt shell for the same id) is completed with the v2 columns.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome store(EdgeNode node, JsonNode record, String digest) {
        UUID id = UUID.fromString(record.get("edgeAssessmentId").asText());
        Assessment existing = assessments.findByEdgeAssessmentId(id).orElse(null);
        if (existing != null) {
            Outcome classified = classify(existing, node, digest, record.get("decision").asText());
            if (classified != null) {
                return classified;
            }
        }
        Instant now = Instant.now();
        Assessment row = existing != null ? existing : newRow(id, node.getPspId(), now);
        // A v1 envelope for the same id already wrote its EDGE_SYNC findings; do not double them.
        boolean hasFindings = existing != null
                && findings.existsByAssessmentIdAndPhase(existing.getId(), FindingPhase.EDGE_SYNC);
        apply(row, node, record, digest, now);
        row = assessments.saveAndFlush(row);
        if (!hasFindings) {
            writeFindings(row, now);
        }
        stats.add(row.getPspId(), row.getEvaluatedAt(), row.getSiteId(), row.getMerchantId(), row.getDecision(),
                row.getFailClosedCode() != null, Boolean.TRUE.equals(row.getCtrRequired()),
                Boolean.TRUE.equals(row.getSanctionsHit()), row.getScore(), row.getLatencyMs());
        workflow.raise(row);
        return Outcome.ACCEPTED;
    }

    /**
     * Classifies a record whose id already exists (also used after losing an insert race, in a
     * fresh transaction).
     *
     * @return DUPLICATE / CONFLICT, or null when the existing row is a non-v2 row of the same PSP
     *         that this record should complete
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Outcome classifyExisting(EdgeNode node, JsonNode record, String digest) {
        UUID id = UUID.fromString(record.get("edgeAssessmentId").asText());
        Assessment existing = assessments.findByEdgeAssessmentId(id).orElse(null);
        if (existing == null) {
            return null;
        }
        Outcome classified = classify(existing, node, digest, record.get("decision").asText());
        return classified != null ? classified : Outcome.CONFLICT;
    }

    /**
     * DUPLICATE / CONFLICT for an existing row, or null when the row is completable: a non-v2 row of
     * the same PSP that is either undecided (the dual-post adopt shell) or decided identically by a
     * v1 envelope.
     */
    private static Outcome classify(Assessment existing, EdgeNode node, String digest, String decision) {
        if (existing.getPspId() != null && !existing.getPspId().equals(node.getPspId())) {
            return Outcome.CONFLICT;
        }
        if (Integer.valueOf(2).equals(existing.getRecordVersion())) {
            return digest.equals(existing.getRecordDigest()) ? Outcome.DUPLICATE : Outcome.CONFLICT;
        }
        if (existing.getDecision() != null && !existing.getDecision().isBlank()
                && !existing.getDecision().equals(decision)) {
            return Outcome.CONFLICT;
        }
        return null;
    }

    /** A TXN-triggered assessment shell for a new decision record. */
    private static Assessment newRow(UUID edgeAssessmentId, Long pspId, Instant now) {
        Assessment row = new Assessment();
        row.setId(UUID.randomUUID());
        row.setPspId(pspId);
        row.setTriggerType(AssessmentTriggerType.TXN);
        row.setTriggerRef(edgeAssessmentId.toString());
        row.setEdgeAssessmentId(edgeAssessmentId);
        row.setCreatedAt(now);
        return row;
    }

    /** Copies the record's class-D fields onto the row. Nothing else from the record is kept. */
    private static void apply(Assessment row, EdgeNode node, JsonNode r, String digest, Instant now) {
        row.setPspId(node.getPspId());
        row.setRecordVersion(2);
        row.setEdgeNodeId(r.get("edgeNodeId").asText());
        row.setSiteId(r.get("siteId").asText());
        row.setTxnRef(r.has("txnRef") ? r.get("txnRef").asText() : null);
        row.setEvaluatedAt(Instant.parse(r.get("evaluatedAt").asText()));
        row.setDecision(r.get("decision").asText());
        row.setScore(r.get("score").decimalValue().intValueExact());
        row.setTriggeredRuleIds(longs(r.get("triggeredRuleIds")));
        row.setRuleVersionIds(longs(r.get("ruleVersionIds")));
        row.setBundleVersion(r.get("bundleVersion").decimalValue().longValueExact());
        row.setContextHash(r.has("bundleHash") ? r.get("bundleHash").asText() : null);
        row.setEvaluator(r.get("evaluator").asText());
        row.setFailClosedCode(r.has("failClosedCode") ? r.get("failClosedCode").asText() : null);
        row.setLatencyMs(r.get("latencyMs").decimalValue().longValueExact());
        row.setMerchantId(r.has("merchantId") ? r.get("merchantId").asText() : null);
        row.setCtrRequired(r.get("ctrRequired").asBoolean());
        row.setSanctionsHit(r.get("sanctionsHit").asBoolean());
        row.setWatchlistEntryIds(strings(r.get("watchlistEntryIds")));
        row.setTypologyCodes(strings(r.get("typologyCodes")));
        row.setRecordDigest(digest);
        row.setReceivedAt(now);
        Map<String, Object> versions = new LinkedHashMap<>();
        versions.put("recordVersion", 2);
        versions.put("edgeId", node.getEdgeId());
        versions.put("bundleVersion", row.getBundleVersion());
        if (row.getContextHash() != null) {
            versions.put("bundleManifestHash", row.getContextHash());
        }
        versions.put("ruleVersionIds", row.getRuleVersionIds());
        row.setVersions(versions);
    }

    /** One EDGE_SYNC finding per triggered rule and per watchlist entry (non-shadow). */
    private void writeFindings(Assessment row, Instant now) {
        List<Long> ruleIds = row.getTriggeredRuleIds();
        List<Long> versionIds = row.getRuleVersionIds();
        for (int i = 0; i < ruleIds.size(); i++) {
            Finding finding = baseFinding(row, now);
            finding.setSourceType(FindingSourceType.RULE);
            finding.setSourceId(Long.toString(ruleIds.get(i)));
            // ruleVersionIds is positionally aligned with triggeredRuleIds when both have the same length.
            if (versionIds.size() == ruleIds.size()) {
                finding.setSourceVersion(Long.toString(versionIds.get(i)));
            }
            finding.setProposedAction(row.getDecision());
            findings.save(finding);
        }
        for (String entry : row.getWatchlistEntryIds()) {
            Finding finding = baseFinding(row, now);
            finding.setSourceType(FindingSourceType.SCREENING);
            finding.setSourceId(entry);
            finding.setProposedAction(row.getDecision());
            findings.save(finding);
        }
    }

    /** Common fields of a decision-record finding. */
    private static Finding baseFinding(Assessment row, Instant now) {
        Finding finding = new Finding();
        finding.setId(UUID.randomUUID());
        finding.setAssessmentId(row.getId());
        finding.setTxnId(null);
        // The transaction by ID reference (V264): edgeAssessmentId, txnRef and site of the record.
        finding.applyTransactionReference(
                com.posgateway.aml.service.residency.TransactionReferenceResolver.forDecisionRecord(row));
        finding.setPhase(FindingPhase.EDGE_SYNC);
        finding.setShadow(false);
        finding.setTriggered(true);
        finding.setCreatedAt(now);
        return finding;
    }

    /** JSON integer array → list of longs (empty when absent). */
    private static List<Long> longs(JsonNode array) {
        List<Long> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(n -> out.add(n.decimalValue().longValueExact()));
        }
        return out;
    }

    /** JSON string array → list (empty when absent). */
    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(n -> out.add(n.asText()));
        }
        return out;
    }
}
