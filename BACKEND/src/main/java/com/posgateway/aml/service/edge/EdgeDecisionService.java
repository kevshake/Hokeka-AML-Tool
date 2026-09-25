package com.posgateway.aml.service.edge;

import com.posgateway.aml.dto.edge.EdgeDecisionRequest;
import com.posgateway.aml.dto.edge.EdgeDecisionResponse;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.jev.JevAiDisclosureSanitizer;
import com.posgateway.aml.service.jev.JevDecisionContext;
import com.posgateway.aml.service.jev.JevDecisionGateway;
import com.posgateway.aml.service.jev.JevDecisionOutcome;
import com.posgateway.aml.service.jev.JevEngineType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Handles Edge → Control Plane JEV decision requests. Edge never calls OpenRouter directly.
 */
@Service
public class EdgeDecisionService {

    private static final Logger log = LoggerFactory.getLogger(EdgeDecisionService.class);

    public static final String DECISION_CONTEXT = "hokeka.edge.decision";

    private final JevDecisionGateway gateway;
    private final PspRepository pspRepository;

    public EdgeDecisionService(JevDecisionGateway gateway, PspRepository pspRepository) {
        this.gateway = gateway;
        this.pspRepository = pspRepository;
    }

    public EdgeDecisionResponse handle(EdgeNode node, EdgeDecisionRequest request) {
        JevEngineType engine = parseEngine(request.engine());
        String baseline = request.baselineDecision() != null ? request.baselineDecision() : "REVIEW";
        Long pspId = node.getPspId();

        Psp psp = pspRepository.findById(pspId).orElse(null);
        boolean inlineMode = psp != null && Boolean.TRUE.equals(psp.getAiInlineMode());
        int inlineBudgetMs = psp != null ? psp.getAiInlineBudgetMs() : 500;

        JevDecisionContext ctx = JevDecisionContext.builder(engine)
                .pspId(pspId)
                .baselineDecision(baseline)
                .features(request.features() != null ? request.features() : Map.of())
                .edgeId(node.getEdgeId())
                .advisoryOnly(true)
                .build();

        if (request.async() || !inlineMode) {
            gateway.decideAsync(ctx);
            return toResponse(baseline, JevDecisionOutcome.escalateHuman(baseline,
                    inlineMode ? "async request" : "inline mode off"), true);
        }

        try {
            JevDecisionOutcome outcome = gateway.decide(ctx, Duration.ofMillis(inlineBudgetMs));
            return toResponse(baseline, outcome, true);
        } catch (Exception e) {
            log.warn("Inline Hokeka AI decision failed for edge {}: {}", node.getEdgeId(), e.getMessage());
            return toResponse(baseline, JevDecisionOutcome.escalateHuman(baseline, "inline timeout/error"), true);
        }
    }

    private static JevEngineType parseEngine(String engine) {
        if (engine == null || engine.isBlank()) {
            return JevEngineType.TRANSACTION_RISK;
        }
        try {
            return JevEngineType.valueOf(engine.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return JevEngineType.TRANSACTION_RISK;
        }
    }

    private static EdgeDecisionResponse toResponse(String baseline, JevDecisionOutcome outcome, boolean advisory) {
        return new EdgeDecisionResponse(
                baseline,
                outcome.getFinalDecision() != null ? outcome.getFinalDecision() : baseline,
                outcome.getRecommendation() != null ? outcome.getRecommendation().name() : null,
                outcome.getRiskScore(),
                outcome.getConfidence(),
                outcome.getReasons(),
                outcome.getCitedSignals(),
                outcome.getAuditId(),
                outcome.isAiApplied(),
                outcome.isFallback(),
                JevAiDisclosureSanitizer.sanitizeFallbackReason(outcome.getFallbackReason()),
                advisory
        );
    }
}
