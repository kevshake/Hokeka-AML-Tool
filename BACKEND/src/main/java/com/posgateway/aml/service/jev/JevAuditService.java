package com.posgateway.aml.service.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.jev.JevDecisionAudit;
import com.posgateway.aml.repository.jev.JevDecisionAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class JevAuditService {

    private final JevDecisionAuditRepository repository;
    private final ObjectMapper objectMapper;

    public JevAuditService(JevDecisionAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JevDecisionAudit persistDecisions(JevDecisionContext context,
                                             JevDecisionPoint decisionPoint,
                                             String questionConfigVersion,
                                             Map<String, Object> state,
                                             String stateHash,
                                             JevBranchEvaluator.EvaluationResult evaluation,
                                             JevDecisionOutcome outcome,
                                             JevDecisionsClient.DecisionsResponse apiResponse,
                                             boolean shadowMode) {
        JevDecisionAudit audit = new JevDecisionAudit();
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
            audit.setOpenrouterRequestId(apiResponse.id());
            audit.setLatencyMs(apiResponse.latencyMs());
            audit.setInputTokens(apiResponse.inputTokens());
            audit.setOutputTokens(apiResponse.outputTokens());
            audit.setEstimatedCostUsd(BigDecimal.valueOf(apiResponse.costUsd()));
            audit.setUsageCostUsd(BigDecimal.valueOf(apiResponse.costUsd()));
            audit.setAnswersJson(objectMapper.convertValue(apiResponse.answers(), Map.class));
        } else if (outcome.getModelSnapshot() != null) {
            audit.setModelId(outcome.getModelSnapshot());
            audit.setModelSnapshot(outcome.getModelSnapshot());
            audit.setOpenrouterRequestId(outcome.getRequestId());
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

    /** Legacy chat-path persistence retained for backward compatibility in tests. */
    @Transactional
    public JevDecisionAudit persist(JevDecisionContext context,
                                    String promptVersion,
                                    String modelId,
                                    Map<String, Object> maskedFeatures,
                                    String rawResponse,
                                    Map<String, Object> parsed,
                                    JevDecisionOutcome outcome,
                                    OpenRouterClient.OpenRouterResponse apiResponse) {
        JevDecisionAudit audit = new JevDecisionAudit();
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
            if (apiResponse.estimatedCostUsd() != null) {
                audit.setEstimatedCostUsd(BigDecimal.valueOf(apiResponse.estimatedCostUsd()));
                audit.setUsageCostUsd(BigDecimal.valueOf(apiResponse.estimatedCostUsd()));
            }
        }
        return repository.save(audit);
    }

    public List<JevDecisionAudit> forTransaction(Long transactionId) {
        return repository.findByTransactionIdOrderByCreatedAtDesc(transactionId);
    }

    public List<JevDecisionAudit> forAlert(Long alertId) {
        return repository.findByAlertIdOrderByCreatedAtDesc(alertId);
    }

    public List<JevDecisionAudit> forCase(Long caseId) {
        return repository.findByCaseIdOrderByCreatedAtDesc(caseId);
    }

    public List<JevDecisionAudit> forMerchant(Long merchantId) {
        return repository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    public List<JevDecisionAudit> forMerchant(Long merchantId, String engineCode) {
        if (engineCode == null || engineCode.isBlank()) {
            return forMerchant(merchantId);
        }
        return repository.findByMerchantIdAndEngineCodeOrderByCreatedAtDesc(
                merchantId, engineCode.trim().toUpperCase());
    }

    public List<JevDecisionAudit> forScreeningHit(String screeningHitId) {
        if (screeningHitId == null || screeningHitId.isBlank()) {
            return List.of();
        }
        return repository.findByScreeningHitIdOrderByCreatedAtDesc(screeningHitId.trim());
    }

    public java.util.Optional<JevDecisionAudit> byId(Long auditId) {
        if (auditId == null) {
            return java.util.Optional.empty();
        }
        return repository.findById(auditId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toDto(JevDecisionAudit audit) {
        return objectMapper.convertValue(audit, Map.class);
    }
}
