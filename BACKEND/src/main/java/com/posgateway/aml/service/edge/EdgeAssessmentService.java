package com.posgateway.aml.service.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Persists edge assessment envelopes and adopts edge decisions on cloud ingest so the synchronous
 * decision engine is not run again for dual-post transactions that already decided at the edge.
 */
@Service
public class EdgeAssessmentService {

    private static final Set<String> DROPPED_SNAPSHOT_KEYS = Set.of(
            "pan", "isomsg", "iso_msg", "track2", "cvv", "cardnumber", "card_number",
            "primaryaccountnumber");

    private final AssessmentRepository assessments;
    private final FindingRepository findings;
    private final TransactionRepository transactions;
    private final ObjectMapper mapper = new ObjectMapper();

    public EdgeAssessmentService(AssessmentRepository assessments,
                                 FindingRepository findings,
                                 TransactionRepository transactions) {
        this.assessments = assessments;
        this.findings = findings;
        this.transactions = transactions;
    }

    /**
     * Records an edge-posted assessment envelope (idempotent on {@code edgeAssessmentId}).
     */
    @Transactional
    public EdgeAssessmentIngestResponse record(EdgeNode node, EdgeAssessmentIngestRequest request) {
        if (request == null || request.edgeAssessmentId() == null || request.edgeAssessmentId().isBlank()) {
            throw new IllegalArgumentException("edgeAssessmentId is required");
        }
        if (node.getPspId() == null) {
            throw new IllegalArgumentException("enrolled edge has no psp");
        }
        String edgeAssessmentId = request.edgeAssessmentId().trim();
        Assessment assessment = assessments.findByEdgeAssessmentId(edgeAssessmentId).orElse(null);
        boolean duplicate = assessment != null && hasEdgeFindings(assessment);
        if (duplicate) {
            if (assessment.getPspId() != null && !assessment.getPspId().equals(node.getPspId())) {
                throw new EdgeAssessmentConflictException("edge assessment belongs to another psp");
            }
            return new EdgeAssessmentIngestResponse(edgeAssessmentId, assessment.getId().toString(), true);
        }
        if (assessment == null) {
            assessment = newAssessment(edgeAssessmentId, node.getPspId());
            try {
                assessment = assessments.saveAndFlush(assessment);
            } catch (DataIntegrityViolationException race) {
                assessment = assessments.findByEdgeAssessmentId(edgeAssessmentId)
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
     * Adopts a previously recorded edge assessment for an ingested transaction, skipping cloud re-evaluation.
     *
     * @return empty when no edge id was supplied; otherwise a fraud result built from stored findings
     */
    @Transactional
    public Optional<FraudDetectionResult> adopt(TransactionEntity transaction, String edgeAssessmentId) {
        if (edgeAssessmentId == null || edgeAssessmentId.isBlank()) {
            return Optional.empty();
        }
        if (transaction == null || transaction.getTxnId() == null) {
            throw new IllegalArgumentException("ingested transaction has no id");
        }
        String id = edgeAssessmentId.trim();
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

    /** Ensures the assessment row is linked to the ingested transaction without cross-tenant reuse. */
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

    /** Copies the edge decision onto the transaction row when cloud ingest has not set one yet. */
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

    /** Creates a placeholder assessment keyed by the opaque edge assessment id. */
    private Assessment newAssessment(String edgeAssessmentId, Long pspId) {
        Assessment assessment = new Assessment();
        assessment.setId(UUID.randomUUID());
        assessment.setPspId(pspId);
        assessment.setTriggerType(AssessmentTriggerType.TXN);
        assessment.setTriggerRef("edge:" + edgeAssessmentId);
        assessment.setEdgeAssessmentId(edgeAssessmentId);
        assessment.setCreatedAt(Instant.now());
        return assessment;
    }

    /** True when at least one finding was written from an edge sync phase. */
    private boolean hasEdgeFindings(Assessment assessment) {
        return findings.findByAssessmentIdOrderByCreatedAtAsc(assessment.getId()).stream()
                .anyMatch(f -> f.getPhase() == FindingPhase.EDGE_SYNC);
    }

    /** Persists rule hits (or a synthetic system finding) for an edge assessment envelope. */
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

    /** Builds a non-shadow edge-sync finding row tied to the assessment. */
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

    /** Serializes edge provenance (bundle hash, rule versions, redacted feature snapshot) for the assessment row. */
    private Map<String, Object> versionsMap(EdgeNode node, EdgeAssessmentIngestRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("edgeId", node.getEdgeId());
        if (request.bundleManifestHash() != null) {
            root.put("bundleManifestHash", request.bundleManifestHash());
        }
        ArrayNode versions = root.putArray("ruleVersionIds");
        if (request.ruleVersionIds() != null) {
            for (Long id : request.ruleVersionIds()) {
                if (id != null) {
                    versions.add(id);
                }
            }
        }
        ObjectNode snapshot = root.putObject("featureSnapshot");
        if (request.featureSnapshot() != null) {
            request.featureSnapshot().forEach((key, value) -> {
                if (key == null || DROPPED_SNAPSHOT_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                    return;
                }
                snapshot.putPOJO(key, value);
            });
        }
        try {
            return mapper.convertValue(root, mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception e) {
            throw new IllegalStateException("failed to store the edge assessment envelope", e);
        }
    }

    /** Maps stored assessment + findings into the fraud orchestrator result shape for API responses. */
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
        details.put("edgeAssessmentId", assessment.getEdgeAssessmentId());
        details.put("edgeAssessmentPending", assessment.getDecision() == null);
        if (assessment.getContextHash() != null) {
            details.put("bundleManifestHash", assessment.getContextHash());
        }
        result.setRiskDetails(details);
        return result;
    }
}
