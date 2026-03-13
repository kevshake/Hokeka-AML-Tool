package com.posgateway.aml.model;

/**
 * User Roles for AML System
 * Defines the roles available in the system
 */
public enum UserRole {
    SUPER_ADMIN, // Full system access - manages PSPs and global settings
    ADMIN,
    MLRO, // Money Laundering Reporting Officer
    COMPLIANCE_OFFICER,
    INVESTIGATOR, // Case investigator
    ANALYST,
    SCREENING_ANALYST, // Sanctions screening specialist
    CASE_MANAGER, // Case workflow manager
    AUDITOR,
    VIEWER, // Read-only access
    PSP_ADMIN, // Manages PSP onboarding/configuration
    PSP_ANALYST, // New: Analyze cases for specific PSP
    BANK_OFFICER, // New: Bank-wide oversight
    BANK_AUDITOR, // New: Bank-wide read-only
    SENIOR_ANALYST // New: Escalate/Approve capabilities
}
