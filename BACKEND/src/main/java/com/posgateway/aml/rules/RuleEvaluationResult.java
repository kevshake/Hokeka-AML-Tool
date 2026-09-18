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
    private final java.util.Map<String, Object> regulatoryEvidence;

    /**
     * Summed score_impact of every triggered rule. Set after construction. Previously the summed
     * value was written only to a feature-store key that nothing read, so every rule's score_impact
     * was decorative; it now flows into riskDetails and the post-rule score.
     */
    private double scoreImpact = 0.0;

    public RuleEvaluationResult(Long txnId, String decision, java.util.List<String> reasons,
            java.util.List<String> triggeredRules, boolean sarRequired,
            boolean ctrRequired, int rulesExecuted, long evaluationTimeMs) {
        this(txnId, decision, reasons, triggeredRules, sarRequired, ctrRequired,
                rulesExecuted, evaluationTimeMs, java.util.Map.of());
    }

    public RuleEvaluationResult(Long txnId, String decision, java.util.List<String> reasons,
            java.util.List<String> triggeredRules, boolean sarRequired,
            boolean ctrRequired, int rulesExecuted, long evaluationTimeMs,
            java.util.Map<String, Object> regulatoryEvidence) {
        this.txnId = txnId;
        this.decision = decision;
        this.reasons = reasons;
        this.triggeredRules = triggeredRules;
        this.sarRequired = sarRequired;
        this.ctrRequired = ctrRequired;
        this.rulesExecuted = rulesExecuted;
        this.evaluationTimeMs = evaluationTimeMs;
        this.regulatoryEvidence = regulatoryEvidence != null
                ? java.util.Map.copyOf(regulatoryEvidence)
                : java.util.Map.of();
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

    public java.util.Map<String, Object> getRegulatoryEvidence() {
        return regulatoryEvidence;
    }

    public double getScoreImpact() {
        return scoreImpact;
    }

    public void setScoreImpact(double scoreImpact) {
        this.scoreImpact = scoreImpact;
    }

    @Override
    public String toString() {
        return String.format("RuleResult[txn=%d, decision=%s, rules=%d, time=%dms]",
                txnId, decision, triggeredRules.size(), evaluationTimeMs);
    }
}
