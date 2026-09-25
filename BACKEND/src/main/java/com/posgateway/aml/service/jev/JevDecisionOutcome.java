package com.posgateway.aml.service.jev;

import java.util.List;
import java.util.Map;

/**
 * Result of a JEV decision call. When {@link #isFallback()} is true, callers must use
 * the deterministic baseline — AI must never hard-fail a decision path.
 */
public final class JevDecisionOutcome {

    private final boolean fallback;
    private final String fallbackReason;
    private final JevRecommendation recommendation;
    private final Double riskScore;
    private final Double confidence;
    private final List<String> reasons;
    private final List<String> citedSignals;
    private final String finalDecision;
    private final boolean aiApplied;
    private final Long auditId;
    private final Map<String, Object> rawParsed;

    private JevDecisionOutcome(Builder builder) {
        this.fallback = builder.fallback;
        this.fallbackReason = builder.fallbackReason;
        this.recommendation = builder.recommendation;
        this.riskScore = builder.riskScore;
        this.confidence = builder.confidence;
        this.reasons = builder.reasons == null ? List.of() : List.copyOf(builder.reasons);
        this.citedSignals = builder.citedSignals == null ? List.of() : List.copyOf(builder.citedSignals);
        this.finalDecision = builder.finalDecision;
        this.aiApplied = builder.aiApplied;
        this.auditId = builder.auditId;
        this.rawParsed = builder.rawParsed == null ? Map.of() : Map.copyOf(builder.rawParsed);
    }

    public static JevDecisionOutcome fallback(String baselineDecision, String reason) {
        return builder()
                .fallback(true)
                .fallbackReason(reason)
                .finalDecision(baselineDecision)
                .aiApplied(false)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isFallback() {
        return fallback;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public JevRecommendation getRecommendation() {
        return recommendation;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public Double getConfidence() {
        return confidence;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public List<String> getCitedSignals() {
        return citedSignals;
    }

    public String getFinalDecision() {
        return finalDecision;
    }

    public boolean isAiApplied() {
        return aiApplied;
    }

    public Long getAuditId() {
        return auditId;
    }

    public Map<String, Object> getRawParsed() {
        return rawParsed;
    }

    public static final class Builder {
        private boolean fallback;
        private String fallbackReason;
        private JevRecommendation recommendation;
        private Double riskScore;
        private Double confidence;
        private List<String> reasons;
        private List<String> citedSignals;
        private String finalDecision;
        private boolean aiApplied;
        private Long auditId;
        private Map<String, Object> rawParsed;

        public Builder fallback(boolean fallback) {
            this.fallback = fallback;
            return this;
        }

        public Builder fallbackReason(String fallbackReason) {
            this.fallbackReason = fallbackReason;
            return this;
        }

        public Builder recommendation(JevRecommendation recommendation) {
            this.recommendation = recommendation;
            return this;
        }

        public Builder riskScore(Double riskScore) {
            this.riskScore = riskScore;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder reasons(List<String> reasons) {
            this.reasons = reasons;
            return this;
        }

        public Builder citedSignals(List<String> citedSignals) {
            this.citedSignals = citedSignals;
            return this;
        }

        public Builder finalDecision(String finalDecision) {
            this.finalDecision = finalDecision;
            return this;
        }

        public Builder aiApplied(boolean aiApplied) {
            this.aiApplied = aiApplied;
            return this;
        }

        public Builder auditId(Long auditId) {
            this.auditId = auditId;
            return this;
        }

        public Builder rawParsed(Map<String, Object> rawParsed) {
            this.rawParsed = rawParsed;
            return this;
        }

        public JevDecisionOutcome build() {
            return new JevDecisionOutcome(this);
        }
    }
}
