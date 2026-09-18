package com.posgateway.aml.model.underwriting;

/**
 * Terminal outcome of merchant underwriting, matching the KYB research brief's decision set.
 */
public enum UnderwritingDecision {
    APPROVE,
    APPROVE_WITH_CONTROLS,
    MANUAL_REVIEW,
    ENHANCED_DUE_DILIGENCE,
    REJECT
}
