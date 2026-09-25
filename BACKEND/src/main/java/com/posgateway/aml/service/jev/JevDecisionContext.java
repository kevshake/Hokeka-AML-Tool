package com.posgateway.aml.service.jev;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Input to a JEV decision call. Features are masked before leaving the control plane.
 */
public final class JevDecisionContext {

    private final JevEngineType engine;
    private final Long pspId;
    private final String baselineDecision;
    private final Map<String, Object> features;
    private final Long transactionId;
    private final Long alertId;
    private final Long caseId;
    private final Long merchantId;
    private final String screeningHitId;
    private final String edgeId;
    private final boolean advisoryOnly;

    private JevDecisionContext(Builder builder) {
        this.engine = builder.engine;
        this.pspId = builder.pspId;
        this.baselineDecision = builder.baselineDecision;
        this.features = Map.copyOf(builder.features);
        this.transactionId = builder.transactionId;
        this.alertId = builder.alertId;
        this.caseId = builder.caseId;
        this.merchantId = builder.merchantId;
        this.screeningHitId = builder.screeningHitId;
        this.edgeId = builder.edgeId;
        this.advisoryOnly = builder.advisoryOnly;
    }

    public JevEngineType getEngine() {
        return engine;
    }

    public Long getPspId() {
        return pspId;
    }

    public String getBaselineDecision() {
        return baselineDecision;
    }

    public Map<String, Object> getFeatures() {
        return features;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Long getAlertId() {
        return alertId;
    }

    public Long getCaseId() {
        return caseId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public String getScreeningHitId() {
        return screeningHitId;
    }

    public String getEdgeId() {
        return edgeId;
    }

    public boolean isAdvisoryOnly() {
        return advisoryOnly;
    }

    public static Builder builder(JevEngineType engine) {
        return new Builder(engine);
    }

    public static final class Builder {
        private final JevEngineType engine;
        private Long pspId;
        private String baselineDecision;
        private final Map<String, Object> features = new LinkedHashMap<>();
        private Long transactionId;
        private Long alertId;
        private Long caseId;
        private Long merchantId;
        private String screeningHitId;
        private String edgeId;
        private boolean advisoryOnly = true;

        private Builder(JevEngineType engine) {
            this.engine = engine;
        }

        public Builder pspId(Long pspId) {
            this.pspId = pspId;
            return this;
        }

        public Builder baselineDecision(String baselineDecision) {
            this.baselineDecision = baselineDecision;
            return this;
        }

        public Builder feature(String key, Object value) {
            if (key != null && value != null) {
                features.put(key, value);
            }
            return this;
        }

        public Builder features(Map<String, Object> map) {
            if (map != null) {
                features.putAll(map);
            }
            return this;
        }

        public Builder transactionId(Long transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder alertId(Long alertId) {
            this.alertId = alertId;
            return this;
        }

        public Builder caseId(Long caseId) {
            this.caseId = caseId;
            return this;
        }

        public Builder merchantId(Long merchantId) {
            this.merchantId = merchantId;
            return this;
        }

        public Builder screeningHitId(String screeningHitId) {
            this.screeningHitId = screeningHitId;
            return this;
        }

        public Builder edgeId(String edgeId) {
            this.edgeId = edgeId;
            return this;
        }

        public Builder advisoryOnly(boolean advisoryOnly) {
            this.advisoryOnly = advisoryOnly;
            return this;
        }

        public JevDecisionContext build() {
            return new JevDecisionContext(this);
        }
    }
}
