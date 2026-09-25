package com.posgateway.aml.service.jev;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Convenience wrapper for engine-specific JEV calls with consistent fallback handling.
 */
@Component
public class JevEngineAdvisor {

    private final JevDecisionGateway gateway;

    public JevEngineAdvisor(JevDecisionGateway gateway) {
        this.gateway = gateway;
    }

    public JevDecisionOutcome advise(JevEngineType engine,
                                     Long pspId,
                                     String baselineDecision,
                                     Map<String, Object> features) {
        return advise(engine, pspId, baselineDecision, features, null, null, null, null, null, null);
    }

    public JevDecisionOutcome advise(JevEngineType engine,
                                     Long pspId,
                                     String baselineDecision,
                                     Map<String, Object> features,
                                     Long transactionId,
                                     Long alertId,
                                     Long caseId,
                                     Long merchantId,
                                     String screeningHitId,
                                     String edgeId) {
        JevDecisionContext ctx = JevDecisionContext.builder(engine)
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

    public void adviseAsync(JevEngineType engine,
                            Long pspId,
                            String baselineDecision,
                            Map<String, Object> features,
                            Long transactionId,
                            Long alertId,
                            Long caseId,
                            Long merchantId) {
        JevDecisionContext ctx = JevDecisionContext.builder(engine)
                .pspId(pspId)
                .baselineDecision(baselineDecision)
                .features(features != null ? features : new HashMap<>())
                .transactionId(transactionId)
                .alertId(alertId)
                .caseId(caseId)
                .merchantId(merchantId)
                .advisoryOnly(true)
                .build();
        gateway.decideAsync(ctx);
    }

    public JevDecisionOutcome adviseInline(JevEngineType engine,
                                           Long pspId,
                                           String baselineDecision,
                                           Map<String, Object> features,
                                           Duration timeout,
                                           String edgeId) {
        JevDecisionContext ctx = JevDecisionContext.builder(engine)
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
