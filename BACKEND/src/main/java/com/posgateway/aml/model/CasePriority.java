package com.posgateway.aml.model;

/**
 * Case Priority Levels
 * Defines the urgency of a compliance case
 */
public enum CasePriority {
    LOW,        // Routine checks, low risk score
    MEDIUM,     // Standard alerts, medium risk score
    HIGH,       // High risk score, PEP matches, Sanctions hits
    CRITICAL    // Immediate attention required, potential massive fraud/terrorist financing
}

