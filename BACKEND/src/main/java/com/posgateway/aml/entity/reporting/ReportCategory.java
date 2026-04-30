package com.posgateway.aml.entity.reporting;

/**
 * Report Categories for AML Reporting System
 */
public enum ReportCategory {
    AML_FRAUD("AML & Fraud", "Reports related to suspicious activity and fraud detection"),
    CURRENCY_THRESHOLD("Currency & Threshold", "Currency transaction and threshold monitoring"),
    TRANSACTION_MONITORING("Transaction Monitoring", "Transaction volume and pattern analysis"),
    CHANNEL_MONITORING("Channel Monitoring", "Channel-specific transaction monitoring"),
    SANCTIONS("Sanctions", "Sanctions screening and compliance"),
    FRAUD_INCIDENTS("Fraud Incidents", "Fraud incident tracking and analysis"),
    ALERT_CASE_MANAGEMENT("Alert & Case Management", "Alert and case workflow reports"),
    RULE_ENGINE("Rule Engine", "Rule effectiveness and metrics"),
    RISK_SCORING_MODELS("Risk Scoring & Models", "Risk model performance and scoring"),
    REGULATORY_SUBMISSION("Regulatory Submission", "Regulatory filing and submission tracking"),
    COMPLIANCE_MANAGEMENT("Compliance Management", "Compliance and audit reports"),
    DATA_QUALITY("Data Quality", "Data quality and integrity reports"),
    CHARGEBACK_DISPUTE("Chargeback & Dispute", "Chargeback and dispute analysis");

    private final String displayName;
    private final String description;

    ReportCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
