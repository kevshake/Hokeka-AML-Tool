package com.posgateway.aml.model;

/**
 * Activity Type Enum
 * Defines the types of activities that can occur in a case
 */
public enum ActivityType {
    CASE_CREATED,
    CASE_ASSIGNED,
    CASE_STATUS_CHANGED,
    CASE_REASSIGNED,
    NOTE_ADDED,
    EVIDENCE_ATTACHED,
    CASE_ESCALATED,
    CASE_DE_ESCALATED,
    SAR_CREATED,
    SAR_SUBMITTED,
    SAR_APPROVED,
    SAR_REJECTED,
    SAR_FILED,
    CASE_CLOSED,
    CASE_REOPENED,
    USER_MENTIONED
}

