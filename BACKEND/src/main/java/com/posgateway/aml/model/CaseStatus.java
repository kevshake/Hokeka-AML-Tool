package com.posgateway.aml.model;

/**
 * Case Status Lifecycle
 * Defines the states a compliance case can be in
 */
public enum CaseStatus {
    NEW, // Automatically created, not yet assigned
    ASSIGNED, // Assigned to an investigator/analyst
    IN_PROGRESS, // Investigation actively being worked on
    PENDING_INFO, // Waiting for external information/response
    PENDING_REVIEW, // Investigation complete, waiting for approval
    ESCALATED, // Escalated to higher authority (e.g. MLRO)
    CLOSED_CLEARED, // Closed as false positive/cleared
    CLOSED_SAR_FILED, // Closed with SAR filing
    CLOSED_BLOCKED, // Closed with entity blocking
    CLOSED_REJECTED, // Closed as rejected (transaction/customer rejected)
    REOPENED // Previously closed case reopened
}
