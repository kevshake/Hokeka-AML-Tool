package com.posgateway.aml.rules;

/**
 * Rule evaluation result DTO.
 * Returned by the DroolsRulesService after evaluating all rules.
 */
public class RuleEvaluationResult {

    private final Long txnId;
    private final String decision;
    private final java.util.List<String> reasons;
    private final java.util.List<String> triggeredRules;
    private final boolean sarRequired;
    private final boolean ctrRequired;
    private final int rulesExecuted;
    private final long evaluationTimeMs;

    public RuleEvaluationResult(Long txnId, String decision, java.util.List<String> reasons,
            java.util.List<String> triggeredRules, boolean sarRequired,
            boolean ctrRequired, int rulesExecuted, long evaluationTimeMs) {
        this.txnId = txnId;
        this.decision = decision;
        this.reasons = reasons;
        this.triggeredRules = triggeredRules;
        this.sarRequired = sarRequired;
        this.ctrRequired = ctrRequired;
        this.rulesExecuted = rulesExecuted;
        this.evaluationTimeMs = evaluationTimeMs;
    }

    public Long getTxnId() {
        return txnId;
    }

    public String getDecision() {
        return decision;
    }

    public java.util.List<String> getReasons() {
        return reasons;
    }

    public java.util.List<String> getTriggeredRules() {
        return triggeredRules;
    }

    public boolean isSarRequired() {
        return sarRequired;
    }

    public boolean isCtrRequired() {
        return ctrRequired;
    }

    public int getRulesExecuted() {
        return rulesExecuted;
    }

    public long getEvaluationTimeMs() {
        return evaluationTimeMs;
    }

    @Override
    public String toString() {
        return String.format("RuleResult[txn=%d, decision=%s, rules=%d, time=%dms]",
                txnId, decision, triggeredRules.size(), evaluationTimeMs);
    }
}
