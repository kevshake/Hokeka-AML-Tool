package com.posgateway.aml.model.underwriting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The explainable result of running merchant underwriting: the decision, the numeric
 * risk score with its per-domain component breakdown (never a black box), every
 * normalized signal collected, any hard-stop reasons, and the controls to apply on a
 * conditional approval.
 */
public final class UnderwritingOutcome {

    private UnderwritingDecision decision;
    private int score;
    private final Map<String, Integer> scoreComponents = new LinkedHashMap<>();
    private final List<VerificationSignal> signals = new ArrayList<>();
    private final List<String> hardStops = new ArrayList<>();
    private final List<String> requiredControls = new ArrayList<>();
    private boolean manualReviewForced;

    public UnderwritingDecision getDecision() { return decision; }
    public void setDecision(UnderwritingDecision decision) { this.decision = decision; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public Map<String, Integer> getScoreComponents() { return scoreComponents; }
    public List<VerificationSignal> getSignals() { return signals; }
    public List<String> getHardStops() { return hardStops; }
    public List<String> getRequiredControls() { return requiredControls; }

    public boolean isManualReviewForced() { return manualReviewForced; }
    public void setManualReviewForced(boolean manualReviewForced) { this.manualReviewForced = manualReviewForced; }

    public void addComponent(String domain, int weightedPoints) {
        scoreComponents.merge(domain, weightedPoints, Integer::sum);
    }
}
