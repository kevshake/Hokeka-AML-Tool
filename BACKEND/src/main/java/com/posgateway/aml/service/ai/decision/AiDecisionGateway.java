package com.posgateway.aml.service.ai.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.ai.AiDecisionProperties;
import com.posgateway.aml.entity.ai.AiDecisionAudit;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single gateway for typed Hokeka AI decisions via Laya {@code /v1/systemone}.
 * Fail-closed: any error routes to {@link AiBranch#ESCALATE_HUMAN}; baseline stands.
 * Shadow mode (default) logs only — no production decision changes.
 */
@Service
public class AiDecisionGateway {

    private static final Logger log = LoggerFactory.getLogger(AiDecisionGateway.class);

    private final AiDecisionProperties properties;
    private final LayaSystemOneClient decisionsClient;
    private final AiQuestionConfigService questionConfigService;
    private final AiStateBuilderService stateBuilderService;
    private final AiBranchEvaluator branchEvaluator;
    private final AiTightenOnlyAuthority tightenOnlyAuthority;
    private final AiEngineConfigService engineConfigService;
    private final AiBudgetService budgetService;
    private final AiAuditService auditService;
    private final ObjectMapper objectMapper;

    public AiDecisionGateway(AiDecisionProperties properties,
                              LayaSystemOneClient decisionsClient,
                              AiQuestionConfigService questionConfigService,
                              AiStateBuilderService stateBuilderService,
                              AiBranchEvaluator branchEvaluator,
                              AiTightenOnlyAuthority tightenOnlyAuthority,
                              AiEngineConfigService engineConfigService,
                              AiBudgetService budgetService,
                              AiAuditService auditService,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.decisionsClient = decisionsClient;
        this.questionConfigService = questionConfigService;
        this.stateBuilderService = stateBuilderService;
        this.branchEvaluator = branchEvaluator;
        this.tightenOnlyAuthority = tightenOnlyAuthority;
        this.engineConfigService = engineConfigService;
        this.budgetService = budgetService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public String configuredModel() {
        if (!properties.isConfigured()) {
            return null;
        }
        String pinned = properties.pinnedModel();
        return pinned != null && !pinned.isBlank() ? pinned : "auto";
    }

    public AiDecisionOutcome decide(AiDecisionContext context) {
        return decide(context, properties.getDecisionsTimeout());
    }

    @CircuitBreaker(name = "layaSystemOne", fallbackMethod = "decideFallback")
    public AiDecisionOutcome decide(AiDecisionContext context, Duration timeout) {
        String baseline = context.getBaselineDecision() != null ? context.getBaselineDecision() : "REVIEW";
        AiDecisionPoint decisionPoint = AiDecisionPoint.fromEngine(context.getEngine());

        if (decisionPoint == null) {
            return auditAndReturn(context, baseline,
                    AiDecisionOutcome.escalateHuman(baseline,
                            "Engine " + context.getEngine() + " is not an AI decision point (use chat LLM path)"),
                    null, null, null, null, null);
        }

        if (!properties.isConfigured()) {
            return auditAndReturn(context, baseline,
                    AiDecisionOutcome.escalateHuman(baseline, "Hokeka AI not configured (missing LAYA_API_KEY)"),
                    decisionPoint, null, null, null, null);
        }
        if (!engineConfigService.isEngineEnabled(context.getEngine())) {
            return auditAndReturn(context, baseline,
                    AiDecisionOutcome.escalateHuman(baseline, "Engine disabled"),
                    decisionPoint, null, null, null, null);
        }
        if (context.getPspId() != null && budgetService.isBudgetExceeded(context.getPspId())) {
            return auditAndReturn(context, baseline,
                    AiDecisionOutcome.escalateHuman(baseline, "Daily budget exceeded"),
                    decisionPoint, null, null, null, null);
        }

        Map<String, Object> state = stateBuilderService.buildState(decisionPoint, context);
        Map<String, Object> questions = questionConfigService.questionsFor(decisionPoint);
        Map<String, String> expectedTypes = questionConfigService.questionTypesFor(decisionPoint);
        String sessionId = buildSessionId(context);
        Map<String, Object> trace = Map.of(
                "trace_name", decisionPoint.getTraceName(),
                "generation_name", decisionPoint.name().toLowerCase());

        LayaSystemOneClient.DecisionsResponse apiResponse;
        try {
            apiResponse = decisionsClient.decide(
                    new LayaSystemOneClient.DecisionsRequest(state, questions, sessionId, trace),
                    expectedTypes,
                    timeout != null ? timeout : properties.getDecisionsTimeout());
        } catch (Exception e) {
            log.warn("Laya systemone failed for {}: {}", decisionPoint, e.getClass().getSimpleName());
            return auditAndReturn(context, baseline,
                    AiDecisionOutcome.escalateHuman(baseline, "Decisions API error: " + e.getClass().getSimpleName()),
                    decisionPoint, state, null, null, null);
        }

        Double primaryConfidence = extractPrimaryConfidence(apiResponse.answers(), decisionPoint);
        if (primaryConfidence != null && primaryConfidence < properties.getMinConfidenceToApply()) {
            return auditAndReturn(context, baseline,
                    AiDecisionOutcome.escalateHuman(baseline,
                            "Low confidence below apply threshold"),
                    decisionPoint, state, null, apiResponse, null);
        }

        AiBranchEvaluator.EvaluationResult evaluation =
                branchEvaluator.evaluate(decisionPoint, context.getPspId(), apiResponse.answers(), context);
        AiBranch branch = evaluation.branch();
        if (branch != AiBranch.ESCALATE_HUMAN) {
            branch = enforceTightenOnlyBranch(branch, baseline, context);
        }

        AiTightenOnlyAuthority.AuthorityResult authority = tightenOnlyAuthority.apply(
                baseline, branch, context, properties.isShadowMode(), properties.isPromoted());

        boolean confidenceOkForAuto = primaryConfidence == null
                || primaryConfidence >= properties.getMinConfidenceToAutoAct();
        boolean aiApplied = authority.wouldApply() && properties.isActive()
                && confidenceOkForAuto
                && !authority.finalDecision().equals(baseline);
        String finalDecision = properties.isShadowMode() || !properties.isPromoted()
                ? baseline
                : authority.finalDecision();

        if (context.getPspId() != null) {
            long tokens = (long) apiResponse.inputTokens() + apiResponse.outputTokens();
            budgetService.recordSpend(context.getPspId(), tokens, BigDecimal.valueOf(apiResponse.costUsd()));
        }

        Map<String, Object> parsed = buildParsedSummary(apiResponse, evaluation);
        AiDecisionOutcome outcome = AiDecisionOutcome.builder()
                .fallback(false)
                .decisionPoint(decisionPoint)
                .branch(branch)
                .shadowMode(properties.isShadowMode())
                .wouldApply(authority.wouldApply())
                .requestId(apiResponse.id())
                .modelSnapshot(apiResponse.modelSnapshot())
                .questionConfigVersion(questionConfigService.getVersion())
                .answers(apiResponse.answers())
                .recommendation(recommendationForBranch(branch))
                .riskScore(extractPrimaryScore(apiResponse.answers(), decisionPoint))
                .confidence(extractPrimaryConfidence(apiResponse.answers(), decisionPoint))
                .finalDecision(finalDecision)
                .aiApplied(aiApplied)
                .rawParsed(parsed)
                .build();

        return auditAndReturn(context, baseline, outcome, decisionPoint, state, evaluation,
                apiResponse, parsed);
    }

    @SuppressWarnings("unused")
    private AiDecisionOutcome decideFallback(AiDecisionContext context, Duration timeout, Throwable t) {
        String baseline = context.getBaselineDecision() != null ? context.getBaselineDecision() : "REVIEW";
        AiDecisionPoint dp = AiDecisionPoint.fromEngine(context.getEngine());
        return auditAndReturn(context, baseline,
                AiDecisionOutcome.escalateHuman(baseline, "Circuit breaker open"),
                dp, null, null, null, null);
    }

    @Async
    public void decideAsync(AiDecisionContext context) {
        decide(context);
    }

    private AiBranch enforceTightenOnlyBranch(AiBranch branch, String baseline, AiDecisionContext context) {
        if (branch == AiBranch.LOW_RISK_CANDIDATE && AiTightenOnlyAuthority.isHardBaseline(baseline)) {
            return AiBranch.DEFAULT;
        }
        if (branch == AiBranch.PROPOSE_CLOSURE && AiTightenOnlyAuthority.isHardBaseline(baseline)) {
            return AiBranch.DEFAULT;
        }
        if (branch == AiBranch.LIKELY_FALSE_POSITIVE) {
            return AiBranch.DEFAULT;
        }
        return branch;
    }

    private static AiRecommendation recommendationForBranch(AiBranch branch) {
        return switch (branch) {
            case ESCALATE_UP, ESCALATE_HUMAN, RAISE_RISK_TIER -> AiRecommendation.ESCALATE;
            case LOW_RISK_CANDIDATE, PROPOSE_CLOSURE -> AiRecommendation.REVIEW;
            case LIKELY_FALSE_POSITIVE -> AiRecommendation.REVIEW;
            case DEFAULT -> AiRecommendation.REVIEW;
        };
    }

    private static Double extractPrimaryScore(Map<String, JsonNode> answers, AiDecisionPoint dp) {
        String key = switch (dp) {
            case DP1_TM_ALERT_TRIAGE -> "activity_risk";
            case DP3_CASE_TRIAGE -> "case_priority";
            case DP4_CUSTOMER_RISK -> "risk_tier";
            default -> null;
        };
        if (key == null || !answers.containsKey(key)) {
            JsonNode laundering = answers.get("laundering_suspicion");
            if (laundering != null) {
                return laundering.path("noul").asDouble() * 100.0;
            }
            JsonNode sameEntity = answers.get("same_entity");
            if (sameEntity != null) {
                return sameEntity.path("noul").asDouble() * 100.0;
            }
            return null;
        }
        return answers.get(key).path("score").asDouble();
    }

    private static Double extractPrimaryConfidence(Map<String, JsonNode> answers, AiDecisionPoint dp) {
        for (JsonNode answer : answers.values()) {
            if (answer.has("confidence")) {
                return answer.path("confidence").asDouble();
            }
        }
        return null;
    }

    private Map<String, Object> buildParsedSummary(LayaSystemOneClient.DecisionsResponse response,
                                                   AiBranchEvaluator.EvaluationResult evaluation) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("requestId", response.id());
        parsed.put("branch", evaluation.branch().name());
        parsed.put("thresholds", evaluation.thresholds().values());
        parsed.put("diagnostics", evaluation.diagnostics());
        parsed.put("answers", objectMapper.convertValue(response.answers(), Map.class));
        return parsed;
    }

    private AiDecisionOutcome auditAndReturn(AiDecisionContext context,
                                              String baseline,
                                              AiDecisionOutcome outcome,
                                              AiDecisionPoint decisionPoint,
                                              Map<String, Object> state,
                                              AiBranchEvaluator.EvaluationResult evaluation,
                                              LayaSystemOneClient.DecisionsResponse apiResponse,
                                              Map<String, Object> parsed) {
        String stateHash = state != null ? stateBuilderService.hashState(state) : null;
        AiDecisionAudit persisted = auditService.persistDecisions(
                context,
                decisionPoint,
                questionConfigService.getVersion(),
                state,
                stateHash,
                evaluation,
                outcome,
                apiResponse,
                properties.isShadowMode());
        return AiDecisionOutcome.builder()
                .fallback(outcome.isFallback())
                .fallbackReason(outcome.getFallbackReason())
                .recommendation(outcome.getRecommendation())
                .riskScore(outcome.getRiskScore())
                .confidence(outcome.getConfidence())
                .reasons(outcome.getReasons())
                .citedSignals(outcome.getCitedSignals())
                .finalDecision(outcome.getFinalDecision() != null ? outcome.getFinalDecision() : baseline)
                .aiApplied(outcome.isAiApplied())
                .auditId(persisted.getId())
                .rawParsed(outcome.getRawParsed() != null && !outcome.getRawParsed().isEmpty()
                        ? outcome.getRawParsed() : parsed)
                .decisionPoint(decisionPoint)
                .branch(outcome.getBranch())
                .shadowMode(outcome.isShadowMode())
                .wouldApply(outcome.isWouldApply())
                .requestId(outcome.getRequestId())
                .modelSnapshot(outcome.getModelSnapshot())
                .questionConfigVersion(outcome.getQuestionConfigVersion())
                .answers(outcome.getAnswers())
                .build();
    }

    private static String buildSessionId(AiDecisionContext context) {
        if (context.getAlertId() != null) {
            return "hokeka-alert-" + context.getAlertId();
        }
        if (context.getCaseId() != null) {
            return "hokeka-case-" + context.getCaseId();
        }
        if (context.getTransactionId() != null) {
            return "hokeka-txn-" + context.getTransactionId();
        }
        if (context.getScreeningHitId() != null) {
            return "hokeka-screen-" + context.getScreeningHitId();
        }
        if (context.getEdgeId() != null) {
            return "hokeka-edge-" + context.getEdgeId();
        }
        return "hokeka-ai-" + context.getEngine().name().toLowerCase();
    }
}
