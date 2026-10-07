package com.posgateway.aml.service.ai.decision;

/**
 * Typed Jev decision points (DP1–DP5). Chat/generation engines do not map here.
 */
public enum AiDecisionPoint {
    DP1_TM_ALERT_TRIAGE("DP1_tm_alert_triage", "hokeka-tm-alert-triage"),
    DP2_SCREENING_MATCH("DP2_screening_match_disambiguation", "hokeka-screening-match"),
    DP3_CASE_TRIAGE("DP3_case_triage_and_str", "hokeka-case-triage"),
    DP4_CUSTOMER_RISK("DP4_customer_risk_and_edd", "hokeka-customer-risk"),
    DP5_SAR_NARRATIVE("DP5_sar_narrative_verification", "hokeka-sar-narrative");

    private final String configKey;
    private final String traceName;

    AiDecisionPoint(String configKey, String traceName) {
        this.configKey = configKey;
        this.traceName = traceName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getTraceName() {
        return traceName;
    }

    public static AiDecisionPoint fromEngine(AiEngineType engine) {
        if (engine == null) {
            return null;
        }
        return switch (engine) {
            case TRANSACTION_RISK, ALERT_TRIAGE, FRAUD_SCORING -> DP1_TM_ALERT_TRIAGE;
            case SANCTIONS_DISAMBIGUATION -> DP2_SCREENING_MATCH;
            case CASE_TRIAGE -> DP3_CASE_TRIAGE;
            case KYC_EDD -> DP4_CUSTOMER_RISK;
            case SAR_NARRATIVE_VERIFICATION -> DP5_SAR_NARRATIVE;
            case G2_CONTENT, ADVERSE_MEDIA, RULE_SUGGESTION -> null;
        };
    }
}
