package com.posgateway.aml.service.assessment;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.assessment.Assessment;
import com.posgateway.aml.entity.assessment.AssessmentTriggerType;
import com.posgateway.aml.entity.assessment.FindingPhase;
import com.posgateway.aml.entity.assessment.FindingSourceType;
import com.posgateway.aml.repository.assessment.AssessmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and finalizes transaction assessments for ingest (shadow; does not affect decisions).
 */
@Service
public class TransactionAssessmentService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionAssessmentService.class);

    private final AssessmentRepository assessmentRepository;
    private final FindingRecorder findingRecorder;

    @Value("${hokeka.assessment.recording.enabled:true}")
    private boolean recordingEnabled;

    public TransactionAssessmentService(AssessmentRepository assessmentRepository,
                                        FindingRecorder findingRecorder) {
        this.assessmentRepository = assessmentRepository;
        this.findingRecorder = findingRecorder;
    }

    /**
     * Begin a TXN assessment and bind {@link AssessmentRecordingScope} for downstream engine hooks.
     */
    @Transactional
    public AssessmentRecordingScope.Scope beginTransactionAssessment(TransactionEntity transaction) {
        if (!recordingEnabled || transaction == null || transaction.getTxnId() == null) {
            return null;
        }
        Assessment assessment = new Assessment();
        assessment.setPspId(transaction.getPspId());
        assessment.setTriggerType(AssessmentTriggerType.TXN);
        assessment.setTriggerRef(String.valueOf(transaction.getTxnId()));
        assessment.setTxnId(transaction.getTxnId());
        assessment.setVersions(buildInitialVersions());
        assessment = assessmentRepository.save(assessment);

        Long merchantId = parseMerchantId(transaction.getMerchantId());
        AssessmentRecordingScope.Scope scope = new AssessmentRecordingScope.Scope(
                assessment.getId(), transaction.getPspId(), transaction.getTxnId(), merchantId);
        AssessmentRecordingScope.set(scope);
        recordRiskFactors(transaction, scope);
        return scope;
    }

    @Transactional
    public void completeAssessment(UUID assessmentId, String decision, long latencyMs,
                                   Map<String, Object> extraVersions) {
        if (!recordingEnabled || assessmentId == null) {
            AssessmentRecordingScope.clear();
            return;
        }
        try {
            assessmentRepository.findById(assessmentId).ifPresent(assessment -> {
                assessment.setDecision(decision);
                assessment.setLatencyMs(latencyMs);
                if (extraVersions != null && !extraVersions.isEmpty()) {
                    Map<String, Object> merged = assessment.getVersions() != null
                            ? new LinkedHashMap<>(assessment.getVersions()) : new LinkedHashMap<>();
                    merged.putAll(extraVersions);
                    assessment.setVersions(merged);
                }
                assessmentRepository.save(assessment);
            });
        } catch (Exception e) {
            logger.warn("Failed to finalize assessment {}: {}", assessmentId, e.getMessage());
        } finally {
            findingRecorder.flushAsync();
            AssessmentRecordingScope.clear();
        }
    }

    @Async("amlTaskExecutor")
    @Transactional
    public void completeAssessmentAsync(UUID assessmentId, String decision, long latencyMs,
                                        Map<String, Object> extraVersions) {
        completeAssessment(assessmentId, decision, latencyMs, extraVersions);
    }

    public void recordRiskFactors(TransactionEntity transaction, AssessmentRecordingScope.Scope scope) {
        if (scope == null) {
            return;
        }
        if (transaction.getKrs() != null) {
            findingRecorder.record(FindingDraft.create()
                    .sourceType(FindingSourceType.RISK_FACTOR)
                    .sourceId("KRS")
                    .sourceVersion("CRA-v1")
                    .phase(FindingPhase.CP_SYNC)
                    .nature("RISK")
                    .triggered(true)
                    .score(transaction.getKrs())
                    .explanation("Know Your Customer risk score at ingest"));
        }
        if (transaction.getTrs() != null) {
            findingRecorder.record(FindingDraft.create()
                    .sourceType(FindingSourceType.RISK_FACTOR)
                    .sourceId("TRS")
                    .sourceVersion("CRA-v1")
                    .phase(FindingPhase.CP_SYNC)
                    .nature("RISK")
                    .triggered(true)
                    .score(transaction.getTrs())
                    .explanation("Transaction risk score at ingest"));
        }
        if (transaction.getCra() != null) {
            findingRecorder.record(FindingDraft.create()
                    .sourceType(FindingSourceType.RISK_FACTOR)
                    .sourceId("CRA")
                    .sourceVersion("CRA-v1")
                    .phase(FindingPhase.CP_SYNC)
                    .nature("RISK")
                    .triggered(true)
                    .score(transaction.getCra())
                    .explanation("Combined risk assessment at ingest"));
        }
    }

    private static Map<String, Object> buildInitialVersions() {
        Map<String, Object> versions = new LinkedHashMap<>();
        versions.put("riskModel", "CRA-v1");
        versions.put("assessmentLedger", "WP-01-shadow");
        return versions;
    }

    private static Long parseMerchantId(String merchantId) {
        if (merchantId == null || merchantId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(merchantId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
