package com.posgateway.aml.service.assessment;

import com.posgateway.aml.entity.assessment.Finding;
import com.posgateway.aml.repository.assessment.FindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Non-blocking Finding persistence (batched async JDBC). Failures are logged only.
 */
@Service
public class FindingRecorder {

    private static final Logger logger = LoggerFactory.getLogger(FindingRecorder.class);

    private final FindingRepository findingRepository;
    private final ConcurrentLinkedQueue<FindingDraft> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger();

    @Value("${hokeka.assessment.recording.enabled:true}")
    private boolean recordingEnabled;

    @Value("${hokeka.assessment.finding.batch-size:32}")
    private int batchSize;

    public FindingRecorder(FindingRepository findingRepository) {
        this.findingRepository = findingRepository;
    }

    /**
     * Queue a finding for async persistence. Uses {@link AssessmentRecordingScope} when assessmentId is null.
     */
    public void record(FindingDraft draft) {
        if (!recordingEnabled || draft == null || draft.getSourceType() == null) {
            return;
        }
        FindingDraft resolved = resolveScope(draft);
        if (resolved.getAssessmentId() == null) {
            logger.debug("Skipping finding (no assessment scope): sourceType={}", draft.getSourceType());
            return;
        }
        pending.add(resolved);
        if (pendingCount.incrementAndGet() >= batchSize) {
            flushAsync();
        }
    }

    public void recordNow(FindingDraft draft) {
        if (!recordingEnabled || draft == null) {
            return;
        }
        FindingDraft resolved = resolveScope(draft);
        if (resolved.getAssessmentId() == null) {
            return;
        }
        pending.add(resolved);
        flushAsync();
    }

    @Async("amlTaskExecutor")
    public void flushAsync() {
        flushInternal();
    }

    @Transactional
    public void flushInternal() {
        if (!recordingEnabled) {
            pending.clear();
            pendingCount.set(0);
            return;
        }
        List<Finding> batch = new ArrayList<>(Math.max(batchSize, 8));
        FindingDraft draft;
        while ((draft = pending.poll()) != null) {
            pendingCount.decrementAndGet();
            batch.add(toEntity(draft));
            if (batch.size() >= batchSize) {
                persistBatch(batch);
                batch = new ArrayList<>(batchSize);
            }
        }
        if (!batch.isEmpty()) {
            persistBatch(batch);
        }
    }

    private void persistBatch(List<Finding> batch) {
        try {
            findingRepository.saveAll(batch);
        } catch (Exception e) {
            logger.warn("Failed to persist {} findings (assessment ledger): {}", batch.size(), e.getMessage());
        }
    }

    private static FindingDraft resolveScope(FindingDraft draft) {
        if (draft.getAssessmentId() != null) {
            return draft;
        }
        AssessmentRecordingScope.Scope scope = AssessmentRecordingScope.current();
        if (scope == null) {
            return draft;
        }
        FindingDraft copy = FindingDraft.create()
                .assessmentId(scope.assessmentId())
                .txnId(draft.getTxnId() != null ? draft.getTxnId() : scope.txnId())
                .partyId(draft.getPartyId() != null ? draft.getPartyId() : scope.merchantId())
                .sourceType(draft.getSourceType())
                .sourceId(draft.getSourceId())
                .sourceVersion(draft.getSourceVersion())
                .phase(draft.getPhase())
                .shadow(draft.isShadow())
                .nature(draft.getNature())
                .severity(draft.getSeverity())
                .triggered(draft.isTriggered())
                .score(draft.getScore())
                .proposedAction(draft.getProposedAction())
                .proposedActions(draft.getProposedActions())
                .evidence(draft.getEvidence())
                .featureReferences(draft.getFeatureReferences())
                .explanation(draft.getExplanation());
        return copy;
    }

    private static Finding toEntity(FindingDraft draft) {
        Finding finding = new Finding();
        finding.setAssessmentId(draft.getAssessmentId());
        finding.setTxnId(draft.getTxnId());
        finding.setPartyId(draft.getPartyId());
        finding.setSourceType(draft.getSourceType());
        finding.setSourceId(draft.getSourceId());
        finding.setSourceVersion(draft.getSourceVersion());
        finding.setPhase(draft.getPhase());
        finding.setShadow(draft.isShadow());
        finding.setNature(draft.getNature());
        finding.setSeverity(draft.getSeverity());
        finding.setTriggered(draft.isTriggered());
        finding.setScore(draft.getScore());
        finding.setProposedAction(draft.getProposedAction());
        finding.setProposedActions(draft.getProposedActions());
        finding.setEvidence(draft.getEvidence());
        finding.setFeatureReferences(draft.getFeatureReferences());
        finding.setExplanation(draft.getExplanation());
        return finding;
    }

    /** Test hook: wait until queue is drained (best-effort). */
    void drainForTests() {
        flushInternal();
    }

    public UUID currentAssessmentId() {
        AssessmentRecordingScope.Scope scope = AssessmentRecordingScope.current();
        return scope != null ? scope.assessmentId() : null;
    }
}
