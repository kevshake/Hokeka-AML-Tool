package com.posgateway.aml.entity.reporting;

/**
 * Report Trigger Types
 */
public enum TriggerType {
    MANUAL("Manual", "Triggered manually by user"),
    SCHEDULED("Scheduled", "Triggered by schedule"),
    API("API", "Triggered via API call");

    private final String displayName;
    private final String description;

    TriggerType(String displayName, String description) {
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
