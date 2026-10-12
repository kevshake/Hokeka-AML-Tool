package com.posgateway.aml.service.assessment;

import com.posgateway.aml.entity.assessment.FindingPhase;
import com.posgateway.aml.entity.assessment.FindingSourceType;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.service.limits.TransactionLimitEnforcementService;
import com.posgateway.aml.service.sanctions.RealTimeTransactionScreeningService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapters from existing engine outputs to {@link FindingDraft} records (shadow only).
 */
@Component
public class AssessmentEngineRecorder {

    private static final String AI_PSP_LABEL = "Hokeka AI recommendation";

    private final FindingRecorder findingRecorder;

    public AssessmentEngineRecorder(FindingRecorder findingRecorder) {
        this.findingRecorder = findingRecorder;
    }

    public void recordLimits(TransactionLimitEnforcementService.LimitBreach breach) {
        boolean triggered = breach != null;
        findingRecorder.record(FindingDraft.create()
                .sourceType(FindingSourceType.LIMIT)
                .sourceId("transaction_limits")
                .sourceVersion("limits-v1")
                .nature("AML")
                .severity(triggered ? "HIGH" : "INFO")
                .triggered(triggered)
                .proposedAction(triggered ? "BLOCK" : "ALLOW")
                .score(triggered ? 1.0 : 0.0)
                .evidence(triggered ? Map.of("reasons", breach.reasons()) : Map.of())
                .explanation(triggered ? String.join("; ", breach.reasons()) : "Within configured limits"));
    }

    public void recordBlacklistChecks(List<String> hitReasons) {
        boolean triggered = hitReasons != null && !hitReasons.isEmpty();
        findingRecorder.record(FindingDraft.create()
                .sourceType(FindingSourceType.LIST)
                .sourceId("payment_blacklist")
                .sourceVersion("blacklist-v1")
                .nature("FRAUD")
                .severity(triggered ? "CRITICAL" : "INFO")
                .triggered(triggered)
                .proposedAction(triggered ? "BLOCK" : "ALLOW")
                .score(triggered ? 1.0 : 0.0)
                .evidence(triggered ? Map.of("hits", hitReasons) : Map.of())
                .explanation(triggered ? String.join("; ", hitReasons) : "No blacklist match"));
    }

    public void recordScreening(RealTimeTransactionScreeningService.TransactionScreeningResult result) {
        if (result == null) {
            findingRecorder.record(FindingDraft.create()
                    .sourceType(FindingSourceType.SCREENING)
                    .sourceId("realtime_sanctions")
                    .nature("SCREENING")
                    .triggered(false)
                    .explanation("Screening not executed"));
            return;
        }
        boolean triggered = result.hasMatches();
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("blocking", result.shouldBlock());
        evidence.put("unavailable", result.isScreeningUnavailable());
        if (result.getMatches() != null) {
            evidence.put("matches", result.getMatches().stream()
                    .map(m -> Map.of(
                            "screenedName", m.getScreenedName(),
                            "entityType", m.getEntityType(),
                            "status", m.getScreeningResult() != null ? m.getScreeningResult().getStatus() : null))
                    .toList());
        }
        findingRecorder.record(FindingDraft.create()
                .sourceType(FindingSourceType.SCREENING)
                .sourceId("realtime_sanctions")
                .sourceVersion("sanctions-v1")
                .nature("SCREENING")
                .severity(triggered ? "CRITICAL" : "INFO")
                .triggered(triggered)
                .proposedAction(result.shouldBlock() ? "BLOCK"
                        : result.isScreeningUnavailable() ? "HOLD" : "ALLOW")
                .score(triggered ? 1.0 : 0.0)
                .evidence(evidence)
                .explanation(triggered ? "Sanctions screening match" : "No sanctions match"));
    }

    public void recordCrossPsp(boolean triggered, List<String> reasons, String proposedAction, double score) {
        findingRecorder.record(FindingDraft.create()
                .sourceType(FindingSourceType.NETWORK)
                .sourceId("cross_psp_fraud")
                .sourceVersion("cross-psp-v1")
                .nature("FRAUD")
                .severity(triggered ? "HIGH" : "INFO")
                .triggered(triggered)
                .proposedAction(proposedAction)
                .score(score)
                .evidence(triggered ? Map.of("reasons", reasons) : Map.of())
                .explanation(triggered ? String.join("; ", reasons) : "No cross-PSP fraud match"));
    }

    public void recordRule(RuleDefinition rule, boolean triggered, String proposedAction,
                           long executionTimeMicros, Map<String, Object> evidence) {
        String version = rule.getCurrentVersionId() != null
                ? String.valueOf(rule.getCurrentVersionId()) : "unversioned";
        findingRecorder.record(FindingDraft.create()
                .sourceType(FindingSourceType.RULE)
                .sourceId(rule.getId() != null ? String.valueOf(rule.getId()) : rule.getName())
                .sourceVersion(version)
                .nature("AML")
                .severity(triggered ? "WARN" : "INFO")
                .triggered(triggered)
                .proposedAction(proposedAction)
                .evidence(evidence != null ? evidence : Map.of("executionTimeMicros", executionTimeMicros))
                .explanation(rule.getDescription() != null ? rule.getDescription() : rule.getName()));
    }

    public void recordRegulatoryCompliance(boolean strRequired, boolean ctrRequired,
                                           String decision, Map<String, Object> evidence) {
        boolean triggered = strRequired || ctrRequired || (decision != null && !"ALLOW".equals(decision));
        findingRecorder.record(FindingDraft.create()
                .sourceType(FindingSourceType.RULE)
                .sourceId("regulatory_compliance")
                .sourceVersion("regulatory-v1")
                .nature("AML")
                .severity(triggered ? "HIGH" : "INFO")
                .triggered(triggered)
                .proposedAction(decision)
                .evidence(evidence != null ? evidence : Map.of(
                        "strRequired", strRequired,
                        "ctrRequired", ctrRequired))
                .explanation("Regulatory CTR/STR compliance evaluation"));
    }

    public void recordMlScore(Long txnId, double score, String modelVersion, Map<String, Object> evidence) {
        findingRecorder.record(FindingDraft.create()
                .sourceType(FindingSourceType.ML)
                .sourceId("xgboost_scoring")
                .sourceVersion(modelVersion != null ? modelVersion : "ml-unavailable")
                .phase(FindingPhase.CP_SYNC)
                .nature("FRAUD")
                .triggered(score > 0)
                .score(score)
                .evidence(evidence != null ? evidence : Map.of("txnId", txnId))
                .explanation("ML fraud score (shadow ledger)"));
    }

    public void recordMlThresholdDecision(String proposedAction, double score, String explanation) {
        findingRecorder.record(FindingDraft.create()
                .sourceType(FindingSourceType.ML)
                .sourceId("ml_thresholds")
                .sourceVersion("thresholds-v1")
                .nature("FRAUD")
                .triggered(!"ALLOW".equals(proposedAction))
                .score(score)
                .proposedAction(proposedAction)
                .explanation(explanation));
    }

    public void recordAiShadow(Long txnId, String engineCode, String recommendation,
                               Double confidence, Double riskScore, boolean shadowMode) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("engine", engineCode);
        evidence.put("shadowMode", shadowMode);
        if (confidence != null) {
            evidence.put("confidence", confidence);
        }
        if (riskScore != null) {
            evidence.put("riskScore", riskScore);
        }
        findingRecorder.record(FindingDraft.create()
                .sourceType(FindingSourceType.ML)
                .sourceId(engineCode)
                .sourceVersion("ai-shadow")
                .phase(FindingPhase.ASYNC)
                .nature("FRAUD")
                .triggered(recommendation != null && !"ALLOW".equalsIgnoreCase(recommendation))
                .score(riskScore)
                .proposedAction(recommendation)
                .evidence(evidence)
                .explanation(AI_PSP_LABEL));
    }

    public static String pspSafeAiLabel() {
        return AI_PSP_LABEL;
    }
}
