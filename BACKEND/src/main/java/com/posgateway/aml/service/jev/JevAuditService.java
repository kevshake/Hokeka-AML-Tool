package com.posgateway.aml.service.jev;

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
        if (apiResponse != null) {
            audit.setLatencyMs(apiResponse.latencyMs());
            audit.setInputTokens(apiResponse.inputTokens());
            audit.setOutputTokens(apiResponse.outputTokens());
            if (apiResponse.estimatedCostUsd() != null) {
                audit.setEstimatedCostUsd(BigDecimal.valueOf(apiResponse.estimatedCostUsd()));
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

    @SuppressWarnings("unchecked")
    public Map<String, Object> toDto(JevDecisionAudit audit) {
        return objectMapper.convertValue(audit, Map.class);
    }
}
