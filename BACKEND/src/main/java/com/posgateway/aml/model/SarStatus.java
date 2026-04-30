package com.posgateway.aml.model;

/**
 * SAR Status Lifecycle
 * Defines the states a Suspicious Activity Report can be in
 */
public enum SarStatus {
    DRAFT,              // Initial draft by investigator
    PENDING_REVIEW,     // Waiting for Compliance Officer review
    APPROVED,           // Approved by MLRO/Compliance Officer, ready for filing
    FILED,              // Filed with regulatory body (e.g., FinCEN)
    REJECTED,           // Rejected by reviewer (returned to draft or closed)
    AMENDED             // Previously filed SAR that has been amended
}

