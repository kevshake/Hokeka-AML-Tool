package com.posgateway.aml.service.assessment;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.service.FraudDetectionOrchestrator.FraudDetectionResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Wraps fraud pipelines with assessment begin/complete without changing decision logic.
 */
@Component
public class FraudPipelineAssessmentHook {

    private final TransactionAssessmentService transactionAssessmentService;

    public FraudPipelineAssessmentHook(TransactionAssessmentService transactionAssessmentService) {
        this.transactionAssessmentService = transactionAssessmentService;
    }

    public AssessmentRecordingScope.Scope begin(TransactionEntity transaction) {
        return transactionAssessmentService.beginTransactionAssessment(transaction);
    }

    public void finalizeResult(TransactionEntity transaction, FraudDetectionResult result,
                               AssessmentRecordingScope.Scope scope, long pipelineLatencyMs) {
        if (scope == null || result == null) {
            AssessmentRecordingScope.clear();
            return;
        }
        result.setAssessmentId(scope.assessmentId());
        transactionAssessmentService.completeAssessment(
                scope.assessmentId(),
                result.getAction(),
                pipelineLatencyMs,
                Map.of("pipelineLatencyMs", pipelineLatencyMs));
    }

    public void finalizeAsync(TransactionEntity transaction, String action, UUID assessmentId, long pipelineLatencyMs) {
        if (assessmentId == null) {
            return;
        }
        transactionAssessmentService.completeAssessmentAsync(
                assessmentId, action, pipelineLatencyMs, Map.of("pipelineLatencyMs", pipelineLatencyMs));
    }

    public void finalize(UUID assessmentId, String decision, long pipelineLatencyMs) {
        if (assessmentId == null) {
            AssessmentRecordingScope.clear();
            return;
        }
        transactionAssessmentService.completeAssessment(
                assessmentId, decision, pipelineLatencyMs, Map.of("pipelineLatencyMs", pipelineLatencyMs));
    }
}
