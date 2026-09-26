package com.posgateway.aml.service.jev;

/**
 * Decision engines that consult the single JEV gateway.
 */
public enum JevEngineType {
    TRANSACTION_RISK,
    ALERT_TRIAGE,
    CASE_TRIAGE,
    SANCTIONS_DISAMBIGUATION,
    KYC_EDD,
    G2_CONTENT,
    ADVERSE_MEDIA,
    RULE_SUGGESTION,
    FRAUD_SCORING,
    /** DP5: verifies a chat-model SAR/STR draft against case facts; Jev never generates text. */
    SAR_NARRATIVE_VERIFICATION
}
