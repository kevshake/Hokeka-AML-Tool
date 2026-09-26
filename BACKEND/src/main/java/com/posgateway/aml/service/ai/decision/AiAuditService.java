package com.posgateway.aml.service.ai.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.ai.AiDecisionAudit;
import com.posgateway.aml.repository.ai.AiDecisionAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class AiAuditService {

    private final AiDecisionAuditRepository repository;
    private final ObjectMapper objectMapper;

    public AiAuditService(AiDecisionAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiDecisionAudit persistDecisions(AiDecisionContext context,
                                             AiDecisionPoint decisionPoint,
                                             String questionConfigVersion,
                                             Map<String, Object> state,
                                             String stateHash,
                                             AiBranchEvaluator.EvaluationResult evaluation,
                                             AiDecisionOutcome outcome,
                                             LayaSystemOneClient.DecisionsResponse apiResponse,
                                             boolean shadowMode) {
        AiDecisionAudit audit = new AiDecisionAudit();
        audit.setPspId(context.getPspId());
        audit.setEngineCode(context.getEngine().name());
        audit.setPromptVersion(questionConfigVersion);
        audit.setDecisionPoint(decisionPoint != null ? decisionPoint.name() : null);
        audit.setQuestionConfigVersion(questionConfigVersion);
        audit.setRequestFeatures(state != null ? state : Map.of());
        audit.setStateHash(stateHash);
        audit.setShadowMode(shadowMode);

        if (apiResponse != null) {
            audit.setModelId(apiResponse.modelSnapshot());
            audit.setModelSnapshot(apiResponse.modelSnapshot());
            audit.setProviderRequestId(apiResponse.id());
            audit.setLatencyMs(apiResponse.latencyMs());
            audit.setInputTokens(apiResponse.inputTokens());
            audit.setOutputTokens(apiResponse.outputTokens());
            audit.setEstimatedCostUsd(BigDecimal.valueOf(apiResponse.costUsd()));
            audit.setUsageCostUsd(BigDecimal.valueOf(apiResponse.costUsd()));
            audit.setAnswersJson(objectMapper.convertValue(apiResponse.answers(), Map.class));
        } else if (outcome.getModelSnapshot() != null) {
            audit.setModelId(outcome.getModelSnapshot());
            audit.setModelSnapshot(outcome.getModelSnapshot());
            audit.setProviderRequestId(outcome.getRequestId());
        }

        if (evaluation != null) {
            audit.setThresholdsJson(Map.of(
                    "version", evaluation.thresholds().version(),
                    "provisional", evaluation.thresholds().provisional(),
                    "values", evaluation.thresholds().values(),
                    "diagnostics", evaluation.diagnostics()));
            audit.setBranchTaken(evaluation.branch().name());
        } else if (outcome.getBranch() != null) {
            audit.setBranchTaken(outcome.getBranch().name());
        }

        audit.setParsedResponse(outcome.getRawParsed());
        if (outcome.getRecommendation() != null) {
            audit.setRecommendation(outcome.getRecommendation().name());
        }
        audit.setRiskScore(outcome.getRiskScore());
        audit.setConfidence(outcome.getConfidence());
        audit.setReasons(outcome.getReasons());
        audit.setCitedSignals(outcome.getCitedSignals());
        audit.setFallbackReason(outcome.getFallbackReason());
        audit.setAiApplied(outcome.isAiApplied());
        audit.setWouldApply(outcome.isWouldApply());
        audit.setBaselineDecision(context.getBaselineDecision());
        audit.setFinalDecision(outcome.getFinalDecision());
        audit.setTransactionId(context.getTransactionId());
        audit.setAlertId(context.getAlertId());
        audit.setCaseId(context.getCaseId());
        audit.setMerchantId(context.getMerchantId());
        audit.setScreeningHitId(context.getScreeningHitId());
        audit.setEdgeId(context.getEdgeId());
        return repository.save(audit);
    }

    /** Legacy generation-path persistence retained for backward compatibility in tests. */
    @Transactional
    public AiDecisionAudit persist(AiDecisionContext context,
                                    String promptVersion,
                                    String modelId,
                                    Map<String, Object> maskedFeatures,
                                    String rawResponse,
                                    Map<String, Object> parsed,
                                    AiDecisionOutcome outcome,
                                    LayaAskClient.AskResponse apiResponse) {
        AiDecisionAudit audit = new AiDecisionAudit();
        audit.setPspId(context.getPspId());
        audit.setEngineCode(context.getEngine().name());
        audit.setPromptVersion(promptVersion);
        audit.setQuestionConfigVersion(promptVersion);
        audit.setModelId(modelId);
        audit.setRequestFeatures(maskedFeatures);
        audit.setRawResponse(rawResponse);
        audit.setParsedResponse(parsed);
        if (outcome.getRecommendation() != null) {
            audit.setRecommendation(outcome.getRecommendation().name());
        }
        audit.setRiskScore(outcome.getRiskScore());
        audit.setConfidence(outcome.getConfidence());
        audit.setReasons(outcome.getReasons());
        audit.setCitedSignals(outcome.getCitedSignals());
        audit.setFallbackReason(outcome.getFallbackReason());
        audit.setAiApplied(outcome.isAiApplied());
        audit.setBaselineDecision(context.getBaselineDecision());
        audit.setFinalDecision(outcome.getFinalDecision());
        audit.setTransactionId(context.getTransactionId());
        audit.setAlertId(context.getAlertId());
        audit.setCaseId(context.getCaseId());
        audit.setMerchantId(context.getMerchantId());
        audit.setScreeningHitId(context.getScreeningHitId());
        audit.setEdgeId(context.getEdgeId());
        audit.setShadowMode(outcome.isShadowMode());
        audit.setWouldApply(outcome.isWouldApply());
        if (outcome.getBranch() != null) {
            audit.setBranchTaken(outcome.getBranch().name());
        }
        if (apiResponse != null) {
            audit.setLatencyMs(apiResponse.latencyMs());
            audit.setInputTokens(apiResponse.inputTokens());
            audit.setOutputTokens(apiResponse.outputTokens());
            audit.setEstimatedCostUsd(BigDecimal.ZERO);
            audit.setUsageCostUsd(BigDecimal.ZERO);
        }
        return repository.save(audit);
    }

    public List<AiDecisionAudit> forTransaction(Long transactionId) {
        return repository.findByTransactionIdOrderByCreatedAtDesc(transactionId);
    }

    public List<AiDecisionAudit> forAlert(Long alertId) {
        return repository.findByAlertIdOrderByCreatedAtDesc(alertId);
    }

    public List<AiDecisionAudit> forCase(Long caseId) {
        return repository.findByCaseIdOrderByCreatedAtDesc(caseId);
    }

    public List<AiDecisionAudit> forMerchant(Long merchantId) {
        return repository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    public List<AiDecisionAudit> forMerchant(Long merchantId, String engineCode) {
        if (engineCode == null || engineCode.isBlank()) {
            return forMerchant(merchantId);
        }
        return repository.findByMerchantIdAndEngineCodeOrderByCreatedAtDesc(
                merchantId, engineCode.trim().toUpperCase());
    }

    public List<AiDecisionAudit> forScreeningHit(String screeningHitId) {
        if (screeningHitId == null || screeningHitId.isBlank()) {
            return List.of();
        }
        return repository.findByScreeningHitIdOrderByCreatedAtDesc(screeningHitId.trim());
    }

    public java.util.Optional<AiDecisionAudit> byId(Long auditId) {
        if (auditId == null) {
            return java.util.Optional.empty();
        }
        return repository.findById(auditId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toDto(AiDecisionAudit audit) {
        return objectMapper.convertValue(audit, Map.class);
    }
}
