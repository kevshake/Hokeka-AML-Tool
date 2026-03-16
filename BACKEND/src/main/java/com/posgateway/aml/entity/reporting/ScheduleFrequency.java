package com.posgateway.aml.entity.reporting;

/**
 * Report Schedule Frequency
 */
public enum ScheduleFrequency {
    DAILY("Daily", "Runs every day"),
    WEEKLY("Weekly", "Runs once per week"),
    MONTHLY("Monthly", "Runs once per month"),
    QUARTERLY("Quarterly", "Runs once per quarter"),
    YEARLY("Yearly", "Runs once per year");

    private final String displayName;
    private final String description;

    ScheduleFrequency(String displayName, String description) {
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
