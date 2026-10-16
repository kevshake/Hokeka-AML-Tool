package com.posgateway.aml.service.edge;

import com.posgateway.aml.dto.edge.EdgeAssessmentFindingRequest;
import com.posgateway.aml.dto.edge.EdgeAssessmentIngestRequest;
import com.posgateway.aml.dto.edge.EdgeAssessmentIngestResponse;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.AssessmentTriggerType;
import com.posgateway.aml.entity.assessment.Finding;
import com.posgateway.aml.entity.assessment.FindingPhase;
import com.posgateway.aml.entity.assessment.FindingSourceType;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import com.posgateway.aml.service.FraudDetectionOrchestrator.FraudDetectionResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Records an edge assessment envelope and, on ingest, adopts that decision instead of
 * running the synchronous decision engine again.
 *
 * <p>Edge assessment ids are UUIDs: they are stored in {@code assessments.edge_assessment_id}
 * (UUID column from V238, unique since V251).
 */
@Service
public class EdgeAssessmentService {

    private static final Set<String> DROPPED_SNAPSHOT_KEYS = Set.of(
            "pan", "isomsg", "iso_msg", "track2", "cvv", "cardnumber", "card_number",
            "primaryaccountnumber");

    private final AssessmentRepository assessments;
    private final FindingRepository findings;
    private final TransactionRepository transactions;

    public EdgeAssessmentService(AssessmentRepository assessments,
                                 FindingRepository findings,
                                 TransactionRepository transactions) {
        this.assessments = assessments;
        this.findings = findings;
        this.transactions = transactions;
    }

    /**
     * Stores the edge envelope (decision, findings, bundle/version provenance) for the enrolled
     * edge's PSP. Idempotent on {@code edgeAssessmentId}: a repeat post returns the existing
     * assessment flagged as duplicate. Rejects ids that belong to another PSP.
     */
    @Transactional
    public EdgeAssessmentIngestResponse record(EdgeNode node, EdgeAssessmentIngestRequest request) {
        if (request == null || request.edgeAssessmentId() == null || request.edgeAssessmentId().isBlank()) {
            throw new IllegalArgumentException("edgeAssessmentId is required");
        }
        if (node.getPspId() == null) {
            throw new IllegalArgumentException("enrolled edge has no psp");
        }
        // Data residency contract §4.5: a v1 envelope must carry an empty featureSnapshot; the feature
        // bag is transaction data (class T) and must never be stored in the cloud.
        if (request.featureSnapshot() != null && !request.featureSnapshot().isEmpty()) {
            throw new IllegalArgumentException("featureSnapshot must be empty: transaction features stay on the Edge");
        }
        String edgeAssessmentId = request.edgeAssessmentId().trim();
        UUID edgeId = parseEdgeAssessmentId(edgeAssessmentId);
        Assessment assessment = assessments.findByEdgeAssessmentId(edgeId).orElse(null);
        // A decision record v2 for the same id already holds the authoritative decision.
        boolean duplicate = assessment != null
                && (hasEdgeFindings(assessment) || Integer.valueOf(2).equals(assessment.getRecordVersion()));
        if (duplicate) {
            if (assessment.getPspId() != null && !assessment.getPspId().equals(node.getPspId())) {
                throw new EdgeAssessmentConflictException("edge assessment belongs to another psp");
            }
            return new EdgeAssessmentIngestResponse(edgeAssessmentId, assessment.getId().toString(), true);
        }
        if (assessment == null) {
            assessment = newAssessment(edgeId, node.getPspId());
            try {
                assessment = assessments.saveAndFlush(assessment);
            } catch (DataIntegrityViolationException race) {
                assessment = assessments.findByEdgeAssessmentId(edgeId)
                        .orElseThrow(() -> race);
                if (hasEdgeFindings(assessment)) {
                    return new EdgeAssessmentIngestResponse(edgeAssessmentId, assessment.getId().toString(), true);
                }
            }
        }
        if (assessment.getPspId() != null && !assessment.getPspId().equals(node.getPspId())) {
            throw new EdgeAssessmentConflictException("edge assessment belongs to another psp");
        }
        assessment.setPspId(node.getPspId());
        assessment.setDecision(request.decision());
        assessment.setLatencyMs(request.latencyMs());
        assessment.setContextHash(request.bundleManifestHash());
        assessment.setVersions(versionsMap(node, request));
        assessments.save(assessment);
        writeFindings(assessment, request);
        applyDecisionToLinkedTransaction(assessment);
        return new EdgeAssessmentIngestResponse(edgeAssessmentId, assessment.getId().toString(), false);
    }

    /**
     * Links an ingested transaction to its edge assessment and returns the edge decision as the
     * synchronous result.
     *
     * @return empty when the request did not carry an edge assessment id, so the caller keeps
     * today's decision-engine path. A present id never falls through to that path.
     */
    @Transactional
    public Optional<FraudDetectionResult> adopt(TransactionEntity transaction, String edgeAssessmentId) {
        if (edgeAssessmentId == null || edgeAssessmentId.isBlank()) {
            return Optional.empty();
        }
        if (transaction == null || transaction.getTxnId() == null) {
            throw new IllegalArgumentException("ingested transaction has no id");
        }
        UUID id = parseEdgeAssessmentId(edgeAssessmentId.trim());
        Assessment assessment = assessments.findByEdgeAssessmentId(id).orElse(null);
        if (assessment == null) {
            assessment = newAssessment(id, transaction.getPspId());
            assessment.setTxnId(transaction.getTxnId());
            assessment.setTriggerRef(String.valueOf(transaction.getTxnId()));
            try {
                assessment = assessments.saveAndFlush(assessment);
            } catch (DataIntegrityViolationException race) {
                assessment = assessments.findByEdgeAssessmentId(id).orElseThrow(() -> race);
                attachTransaction(assessment, transaction);
                assessments.save(assessment);
            }
        } else {
            attachTransaction(assessment, transaction);
            assessments.save(assessment);
        }
        applyDecisionToLinkedTransaction(assessment);
        return Optional.of(toResult(transaction, assessment));
    }

    /** Parses the edge-issued id; a non-UUID value is a client error (400), not a server fault. */
    private static UUID parseEdgeAssessmentId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("edgeAssessmentId must be a UUID");
        }
    }

    /** Binds the transaction to the assessment, refusing cross-PSP or already-linked assessments. */
    private void attachTransaction(Assessment assessment, TransactionEntity transaction) {
        if (assessment.getPspId() != null && transaction.getPspId() != null
                && !assessment.getPspId().equals(transaction.getPspId())) {
            throw new EdgeAssessmentConflictException("edge assessment belongs to another psp");
        }
        if (assessment.getTxnId() != null && !assessment.getTxnId().equals(transaction.getTxnId())) {
            throw new EdgeAssessmentConflictException("edge assessment is already linked to another transaction");
        }
        if (assessment.getPspId() == null) {
            assessment.setPspId(transaction.getPspId());
        }
        assessment.setTxnId(transaction.getTxnId());
        assessment.setTriggerRef(String.valueOf(transaction.getTxnId()));
    }

    /** Copies the edge decision onto the linked transaction when it has no decision yet. */
    private void applyDecisionToLinkedTransaction(Assessment assessment) {
        if (assessment.getTxnId() == null || assessment.getDecision() == null || assessment.getDecision().isBlank()) {
            return;
        }
        transactions.findById(assessment.getTxnId()).ifPresent(txn -> {
            if (txn.getDecision() == null || txn.getDecision().isBlank()) {
                txn.setDecision(assessment.getDecision());
                transactions.save(txn);
            }
        });
    }

    /**
     * Builds a TXN-triggered assessment shell for an edge id. The trigger ref starts as the edge id
     * and is replaced by the txn id once the dual-post ingest links it.
     */
    private Assessment newAssessment(UUID edgeAssessmentId, Long pspId) {
        Assessment assessment = new Assessment();
        assessment.setId(UUID.randomUUID());
        assessment.setPspId(pspId);
        assessment.setTriggerType(AssessmentTriggerType.TXN);
        assessment.setTriggerRef(edgeAssessmentId.toString());
        assessment.setEdgeAssessmentId(edgeAssessmentId);
        assessment.setCreatedAt(Instant.now());
        return assessment;
    }

    /** True once the edge envelope's findings (phase EDGE_SYNC) have been written. */
    private boolean hasEdgeFindings(Assessment assessment) {
        return findings.existsByAssessmentIdAndPhase(assessment.getId(), FindingPhase.EDGE_SYNC);
    }

    /** Writes one non-shadow EDGE_SYNC finding per edge rule hit, or a single SYSTEM finding when none hit. */
    private void writeFindings(Assessment assessment, EdgeAssessmentIngestRequest request) {
        Instant now = Instant.now();
        List<EdgeAssessmentFindingRequest> incoming = request.findings() == null ? List.of() : request.findings();
        if (incoming.isEmpty()) {
            Finding finding = baseFinding(assessment, now);
            finding.setSourceType(FindingSourceType.SYSTEM);
            finding.setSourceId("edge-decision");
            finding.setTriggered(request.decision() != null && !"ALLOW".equalsIgnoreCase(request.decision()));
            finding.setScore(request.score() == null ? null : request.score().doubleValue());
            finding.setProposedAction(request.decision());
            finding.setExplanation("Edge decision with no rule hit");
            findings.save(finding);
            return;
        }
        for (EdgeAssessmentFindingRequest hit : incoming) {
            Finding finding = baseFinding(assessment, now);
            finding.setSourceType(hit.ruleId() == null ? FindingSourceType.SYSTEM : FindingSourceType.RULE);
            finding.setSourceId(hit.ruleId() == null ? "edge-decision" : Long.toString(hit.ruleId()));
            finding.setSourceVersion(hit.ruleVersionId() == null ? null : Long.toString(hit.ruleVersionId()));
            finding.setTriggered(hit.triggered() == null || hit.triggered());
            finding.setScore(hit.score());
            finding.setProposedAction(hit.action() != null ? hit.action() : request.decision());
            finding.setExplanation(hit.explanation());
            findings.save(finding);
        }
    }

    /** Common fields of an edge finding: assessment/txn link, EDGE_SYNC phase, not shadow. */
    private Finding baseFinding(Assessment assessment, Instant now) {
        Finding finding = new Finding();
        finding.setId(UUID.randomUUID());
        finding.setAssessmentId(assessment.getId());
        finding.setTxnId(assessment.getTxnId());
        finding.setPhase(FindingPhase.EDGE_SYNC);
        finding.setShadow(false);
        finding.setCreatedAt(now);
        return finding;
    }

    /**
     * Builds the {@code assessments.versions} JSON: edge id, bundle manifest hash, rule version ids hit
     * and the feature snapshot with card data keys (PAN, track2, CVV, raw ISO message) removed.
     */
    private Map<String, Object> versionsMap(EdgeNode node, EdgeAssessmentIngestRequest request) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("edgeId", node.getEdgeId());
        if (request.bundleManifestHash() != null) {
            root.put("bundleManifestHash", request.bundleManifestHash());
        }
        List<Long> versions = new ArrayList<>();
        if (request.ruleVersionIds() != null) {
            for (Long id : request.ruleVersionIds()) {
                if (id != null) {
                    versions.add(id);
                }
            }
        }
        root.put("ruleVersionIds", versions);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (request.featureSnapshot() != null) {
            request.featureSnapshot().forEach((key, value) -> {
                if (key == null || DROPPED_SNAPSHOT_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                    return;
                }
                snapshot.put(key, value);
            });
        }
        root.put("featureSnapshot", snapshot);
        return root;
    }

    /** Converts the stored edge assessment and its findings into the ingest response result. */
    private FraudDetectionResult toResult(TransactionEntity transaction, Assessment assessment) {
        FraudDetectionResult result = new FraudDetectionResult();
        result.setTxnId(transaction.getTxnId());
        result.setAction(assessment.getDecision());
        result.setLatencyMs(assessment.getLatencyMs());
        List<Finding> rows = findings.findByAssessmentIdOrderByCreatedAtAsc(assessment.getId());
        double score = 0;
        boolean anyScore = false;
        List<String> reasons = new ArrayList<>();
        for (Finding finding : rows) {
            if (finding.getScore() != null) {
                score += finding.getScore();
                anyScore = true;
            }
            if (finding.getExplanation() != null && !finding.getExplanation().isBlank()) {
                reasons.add(finding.getExplanation());
            }
        }
        result.setScore(anyScore ? score : null);
        if (assessment.getDecision() == null) {
            reasons.add("edge assessment not received yet");
        }
        result.setReasons(reasons);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("decidedAtEdge", true);
        details.put("edgeAssessmentId", assessment.getEdgeAssessmentId() == null
                ? null : assessment.getEdgeAssessmentId().toString());
        details.put("edgeAssessmentPending", assessment.getDecision() == null);
        if (assessment.getContextHash() != null) {
            details.put("bundleManifestHash", assessment.getContextHash());
        }
        result.setRiskDetails(details);
        return result;
    }
}
