package com.posgateway.aml.service.ai.decision;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Convenience wrapper for engine-specific JEV calls with consistent fallback handling.
 */
@Component
public class AiEngineAdvisor {

    private final AiDecisionGateway gateway;

    public AiEngineAdvisor(AiDecisionGateway gateway) {
        this.gateway = gateway;
    }

    public AiDecisionOutcome advise(AiEngineType engine,
                                     Long pspId,
                                     String baselineDecision,
                                     Map<String, Object> features) {
        return advise(engine, pspId, baselineDecision, features, null, null, null, null, null, null);
    }

    public AiDecisionOutcome advise(AiEngineType engine,
                                     Long pspId,
                                     String baselineDecision,
                                     Map<String, Object> features,
                                     Long transactionId,
                                     Long alertId,
                                     Long caseId,
                                     Long merchantId,
                                     String screeningHitId,
                                     String edgeId) {
        AiDecisionContext ctx = AiDecisionContext.builder(engine)
                .pspId(pspId)
                .baselineDecision(baselineDecision)
                .features(features != null ? features : Map.of())
                .transactionId(transactionId)
                .alertId(alertId)
                .caseId(caseId)
                .merchantId(merchantId)
                .screeningHitId(screeningHitId)
                .edgeId(edgeId)
                .advisoryOnly(true)
                .build();
        return gateway.decide(ctx);
    }

    public void adviseAsync(AiEngineType engine,
                            Long pspId,
                            String baselineDecision,
                            Map<String, Object> features,
                            Long transactionId,
                            Long alertId,
                            Long caseId,
                            Long merchantId) {
        adviseAsync(engine, pspId, baselineDecision, features, transactionId, alertId, caseId, merchantId, null);
    }

    public void adviseAsync(AiEngineType engine,
                            Long pspId,
                            String baselineDecision,
                            Map<String, Object> features,
                            Long transactionId,
                            Long alertId,
                            Long caseId,
                            Long merchantId,
                            String screeningHitId) {
        AiDecisionContext ctx = AiDecisionContext.builder(engine)
                .pspId(pspId)
                .baselineDecision(baselineDecision)
                .features(features != null ? features : new HashMap<>())
                .transactionId(transactionId)
                .alertId(alertId)
                .caseId(caseId)
                .merchantId(merchantId)
                .screeningHitId(screeningHitId)
                .advisoryOnly(true)
                .build();
        gateway.decideAsync(ctx);
    }

    public AiDecisionOutcome adviseInline(AiEngineType engine,
                                           Long pspId,
                                           String baselineDecision,
                                           Map<String, Object> features,
                                           Duration timeout,
                                           String edgeId) {
        AiDecisionContext ctx = AiDecisionContext.builder(engine)
                .pspId(pspId)
                .baselineDecision(baselineDecision)
                .features(features != null ? features : Map.of())
                .edgeId(edgeId)
                .advisoryOnly(true)
                .build();
        return gateway.decide(ctx, timeout);
    }

    public boolean isBorderlineTransactionScore(double score, double holdThreshold, double blockThreshold) {
        double reviewBandLow = holdThreshold * 0.75;
        return score >= reviewBandLow && score < blockThreshold;
    }
}
