package com.posgateway.aml.entity.reporting;

/**
 * Report Execution Status
 */
public enum ExecutionStatus {
    PENDING("Pending", "Execution queued, waiting to start"),
    RUNNING("Running", "Currently executing"),
    COMPLETED("Completed", "Successfully completed"),
    FAILED("Failed", "Execution failed"),
    CANCELLED("Cancelled", "Cancelled by user");

    private final String displayName;
    private final String description;

    ExecutionStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
