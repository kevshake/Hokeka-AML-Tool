package com.posgateway.aml.service.ai.decision;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Result of a Jev Decisions API call. On failure the branch is {@link AiBranch#ESCALATE_HUMAN}
 * and the deterministic baseline stands.
 */
public final class AiDecisionOutcome {

    private final boolean fallback;
    private final String fallbackReason;
    private final AiRecommendation recommendation;
    private final Double riskScore;
    private final Double confidence;
    private final List<String> reasons;
    private final List<String> citedSignals;
    private final String finalDecision;
    private final boolean aiApplied;
    private final Long auditId;
    private final Map<String, Object> rawParsed;
    private final AiDecisionPoint decisionPoint;
    private final AiBranch branch;
    private final boolean shadowMode;
    private final boolean wouldApply;
    private final String requestId;
    private final String modelSnapshot;
    private final String questionConfigVersion;
    private final Map<String, JsonNode> answers;

    private AiDecisionOutcome(Builder builder) {
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
        this.decisionPoint = builder.decisionPoint;
        this.branch = builder.branch;
        this.shadowMode = builder.shadowMode;
        this.wouldApply = builder.wouldApply;
        this.requestId = builder.requestId;
        this.modelSnapshot = builder.modelSnapshot;
        this.questionConfigVersion = builder.questionConfigVersion;
        this.answers = builder.answers == null ? Map.of() : Map.copyOf(builder.answers);
    }

    public static AiDecisionOutcome escalateHuman(String baselineDecision, String reason) {
        return builder()
                .fallback(true)
                .fallbackReason(reason)
                .branch(AiBranch.ESCALATE_HUMAN)
                .finalDecision(baselineDecision != null ? baselineDecision : "REVIEW")
                .recommendation(AiRecommendation.ESCALATE)
                .aiApplied(false)
                .build();
    }

    /** @deprecated use {@link #escalateHuman(String, String)} */
    @Deprecated
    public static AiDecisionOutcome fallback(String baselineDecision, String reason) {
        return escalateHuman(baselineDecision, reason);
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

    public AiRecommendation getRecommendation() {
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

    public AiDecisionPoint getDecisionPoint() {
        return decisionPoint;
    }

    public AiBranch getBranch() {
        return branch;
    }

    public boolean isShadowMode() {
        return shadowMode;
    }

    public boolean isWouldApply() {
        return wouldApply;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getModelSnapshot() {
        return modelSnapshot;
    }

    public String getQuestionConfigVersion() {
        return questionConfigVersion;
    }

    public Map<String, JsonNode> getAnswers() {
        return answers;
    }

    public static final class Builder {
        private boolean fallback;
        private String fallbackReason;
        private AiRecommendation recommendation;
        private Double riskScore;
        private Double confidence;
        private List<String> reasons;
        private List<String> citedSignals;
        private String finalDecision;
        private boolean aiApplied;
        private Long auditId;
        private Map<String, Object> rawParsed;
        private AiDecisionPoint decisionPoint;
        private AiBranch branch;
        private boolean shadowMode = true;
        private boolean wouldApply;
        private String requestId;
        private String modelSnapshot;
        private String questionConfigVersion;
        private Map<String, JsonNode> answers;

        public Builder fallback(boolean fallback) {
            this.fallback = fallback;
            return this;
        }

        public Builder fallbackReason(String fallbackReason) {
            this.fallbackReason = fallbackReason;
            return this;
        }

        public Builder recommendation(AiRecommendation recommendation) {
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

        public Builder decisionPoint(AiDecisionPoint decisionPoint) {
            this.decisionPoint = decisionPoint;
            return this;
        }

        public Builder branch(AiBranch branch) {
            this.branch = branch;
            return this;
        }

        public Builder shadowMode(boolean shadowMode) {
            this.shadowMode = shadowMode;
            return this;
        }

        public Builder wouldApply(boolean wouldApply) {
            this.wouldApply = wouldApply;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder modelSnapshot(String modelSnapshot) {
            this.modelSnapshot = modelSnapshot;
            return this;
        }

        public Builder questionConfigVersion(String questionConfigVersion) {
            this.questionConfigVersion = questionConfigVersion;
            return this;
        }

        public Builder answers(Map<String, JsonNode> answers) {
            this.answers = answers;
            return this;
        }

        public AiDecisionOutcome build() {
            return new AiDecisionOutcome(this);
        }
    }
}
