package com.posgateway.aml.model;

/**
 * Alert Disposition Codes
 * Standardized codes for documenting alert investigation outcomes
 */
public enum AlertDisposition {

    // False Positives
    FALSE_POSITIVE("False Positive - Not suspicious after review"),
    DUPLICATE("Duplicate alert for same activity"),
    TECHNICAL_ERROR("System error or data quality issue"),

    // True Positives
    TRUE_POSITIVE_SAR_FILED("True Positive - SAR filed with regulators"),
    TRUE_POSITIVE_BLOCKED("True Positive - Transaction/merchant blocked"),
    TRUE_POSITIVE_REPORTED("True Positive - Reported to law enforcement"),

    // Requires Further Action
    ESCALATED("Escalated to senior investigator or MLRO"),
    PENDING_INFORMATION("Awaiting additional information"),
    ONGOING_MONITORING("Under ongoing monitoring"),

    // Cleared
    CLEARED_LOW_RISK("Cleared - assessed as low risk"),
    CLEARED_CUSTOMER_EXPLANATION("Cleared - customer provided valid explanation"),
    CLEARED_KNOWN_PATTERN("Cleared - known legitimate business pattern"),

    // Administrative
    MERGED_WITH_CASE("Merged with compliance case"),
    SUPERSEDED("Superseded by newer alert"),
    EXPIRED("Alert expired per retention policy");

    private final String description;

    AlertDisposition(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if disposition indicates a true positive
     */
    public boolean isTruePositive() {
        return this == TRUE_POSITIVE_SAR_FILED ||
                this == TRUE_POSITIVE_BLOCKED ||
                this == TRUE_POSITIVE_REPORTED;
    }

    /**
     * Check if disposition indicates a false positive
     */
    public boolean isFalsePositive() {
        return this == FALSE_POSITIVE ||
                this == DUPLICATE ||
                this == TECHNICAL_ERROR;
    }

    /**
     * Check if alert requires further action
     */
    public boolean requiresAction() {
        return this == ESCALATED ||
                this == PENDING_INFORMATION ||
                this == ONGOING_MONITORING;
    }
}
