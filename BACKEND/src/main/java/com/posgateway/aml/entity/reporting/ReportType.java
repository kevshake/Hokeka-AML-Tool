package com.posgateway.aml.entity.reporting;

/**
 * Report Types
 */
public enum ReportType {
    STATIC("Static Report", "Pre-generated, fixed data snapshot"),
    DYNAMIC("Dynamic Report", "Real-time generated based on current data"),
    REGULATORY("Regulatory Report", "Official regulatory filing report");

    private final String displayName;
    private final String description;

    ReportType(String displayName, String description) {
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
