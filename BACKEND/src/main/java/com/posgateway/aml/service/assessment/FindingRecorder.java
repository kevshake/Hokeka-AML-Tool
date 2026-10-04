package com.posgateway.aml.service.assessment;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.Finding;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import com.posgateway.aml.repository.assessment.FindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Writes the shadow assessment for a decision the existing engine already made.
 * It does not change that decision.
 */
@Service
public class FindingRecorder {
    private static final Logger log = LoggerFactory.getLogger(FindingRecorder.class);

    private final AssessmentRepository assessments;
    private final FindingRepository findings;

    public FindingRecorder(AssessmentRepository assessments, FindingRepository findings) {
        this.assessments = assessments;
        this.findings = findings;
    }

    public record FindingDraft(
            String sourceType,
            String sourceId,
            String nature,
            String explanation,
            String proposedAction,
            boolean triggered,
            Double score
    ) {
        public static FindingDraft of(String sourceType, String sourceId, String explanation,
                                      String proposedAction, boolean triggered) {
            return new FindingDraft(sourceType, sourceId, null, explanation, proposedAction, triggered, null);
        }
    }

    @Transactional
    public UUID recordDecision(TransactionEntity transaction, String decision, Double score, Long latencyMs) {
        return recordDecision(transaction, decision, score, latencyMs, List.of());
    }

    @Transactional
    public UUID recordDecision(TransactionEntity transaction, String decision, Double score, Long latencyMs,
                               List<FindingDraft> drafts) {
        if (transaction == null || transaction.getTxnId() == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        Assessment assessment = new Assessment();
        assessment.setId(UUID.randomUUID());
        assessment.setPspId(transaction.getPspId());
        assessment.setTxnId(transaction.getTxnId());
        assessment.setTrigger("TXN");
        assessment.setTriggerRef(String.valueOf(transaction.getTxnId()));
        assessment.setDecision(decision);
        assessment.setLatencyMs(latencyMs);
        assessment.setCreatedAt(now);
        assessments.save(assessment);

        List<FindingDraft> toWrite = new ArrayList<>(drafts != null ? drafts : List.of());
        if (toWrite.isEmpty()) {
            toWrite.add(FindingDraft.of(
                    "SYSTEM",
                    "decision-engine",
                    "Shadow copy of the decision engine result.",
                    decision,
                    decision != null && !"ALLOW".equalsIgnoreCase(decision)));
        }

        for (FindingDraft draft : toWrite) {
            Finding finding = new Finding();
            finding.setId(UUID.randomUUID());
            finding.setAssessmentId(assessment.getId());
            finding.setTxnId(transaction.getTxnId());
            finding.setSourceType(draft.sourceType() != null ? draft.sourceType() : "SYSTEM");
            finding.setSourceId(draft.sourceId());
            finding.setPhase("CP_SYNC");
            finding.setShadow(true);
            finding.setNature(draft.nature());
            finding.setTriggered(draft.triggered());
            finding.setScore(draft.score() != null ? draft.score() : score);
            finding.setProposedAction(draft.proposedAction() != null ? draft.proposedAction() : decision);
            finding.setExplanation(draft.explanation());
            finding.setCreatedAt(now);
            findings.save(finding);
        }
        log.debug("Recorded shadow assessment {} with {} findings for txn {}",
                assessment.getId(), toWrite.size(), transaction.getTxnId());
        return assessment.getId();
    }
}
