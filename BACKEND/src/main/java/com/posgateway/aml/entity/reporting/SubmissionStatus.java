package com.posgateway.aml.entity.reporting;

/**
 * Regulatory Submission Status
 */
public enum SubmissionStatus {
    DRAFT("Draft", "Initial draft state"),
    PENDING_REVIEW("Pending Review", "Awaiting review"),
    APPROVED("Approved", "Approved for filing"),
    FILED("Filed", "Submitted to regulator"),
    REJECTED("Rejected", "Rejected by regulator"),
    AMENDED("Amended", "Amended submission");

    private final String displayName;
    private final String description;

    SubmissionStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean canEdit() {
        return this == DRAFT || this == PENDING_REVIEW || this == REJECTED;
    }

    public boolean canFile() {
        return this == APPROVED;
    }
}
