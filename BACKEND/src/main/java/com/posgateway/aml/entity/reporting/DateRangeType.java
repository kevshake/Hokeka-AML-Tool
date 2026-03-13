package com.posgateway.aml.entity.reporting;

/**
 * Date Range Types for Scheduled Reports
 */
public enum DateRangeType {
    PREVIOUS_DAY("Previous Day", "Yesterday" data),
    PREVIOUS_WEEK("Previous Week", "Last week" data),
    PREVIOUS_MONTH("Previous Month", "Last month" data),
    PREVIOUS_QUARTER("Previous Quarter", "Last quarter" data),
    PREVIOUS_YEAR("Previous Year", "Last year" data),
    CUSTOM("Custom", "Custom date range");

    private final String displayName;
    private final String description;

    DateRangeType(String displayName, String description) {
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
