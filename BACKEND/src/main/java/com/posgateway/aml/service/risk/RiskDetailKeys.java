package com.posgateway.aml.service.risk;

import java.util.Map;

/**
 * Canonical keys for the ML model score carried in scoring {@code riskDetails} and feature maps.
 *
 * <p>The raw model score is written by {@link com.posgateway.aml.service.ScoringService} under the
 * snake_case key {@link #ML_SCORE} (matching the feature-store / rule-field naming, e.g. the edge
 * rule compiler and the Console rule builder). The camelCase {@link #ML_SCORE_RESPONSE_ALIAS} exists
 * only because the fraud-detection API response exposes {@code riskDetails} as {@code scores} and the
 * Console reads {@code scores.mlScore}; it must always carry the same value as {@link #ML_SCORE}.
 * Readers go through {@link #readMlScore(Map)} so either spelling resolves to the same number.
 */
public final class RiskDetailKeys {

    /** Canonical key for the raw ML model score (0..1). */
    public static final String ML_SCORE = "ml_score";

    /** API-response alias of {@link #ML_SCORE} consumed by the Console as {@code scores.mlScore}. */
    public static final String ML_SCORE_RESPONSE_ALIAS = "mlScore";

    private RiskDetailKeys() {
    }

    /**
     * Reads the ML model score from a riskDetails / features map, preferring the canonical
     * {@code ml_score} key and falling back to the legacy {@code mlScore} alias.
     *
     * @param source map to read from; may be null
     * @return the score as a double, or null when neither key holds a numeric value
     */
    public static Double readMlScore(Map<String, ?> source) {
        if (source == null) {
            return null;
        }
        Object canonical = source.get(ML_SCORE);
        if (canonical instanceof Number number) {
            return number.doubleValue();
        }
        Object alias = source.get(ML_SCORE_RESPONSE_ALIAS);
        if (alias instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }
}
