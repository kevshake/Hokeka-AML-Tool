package com.posgateway.aml.model;

/**
 * Granular permissions for AML System
 * Defines specific actions users can perform
 */
public enum Permission {
    // Case Management
    VIEW_CASES,
    CREATE_CASES,
    ASSIGN_CASES,
    CLOSE_CASES,
    ESCALATE_CASES,
    REOPEN_CASES,
    ADD_CASE_NOTES,
    ADD_CASE_EVIDENCE,

    // SAR Operations
    VIEW_SAR,
    CREATE_SAR,
    APPROVE_SAR,
    FILE_SAR,
    REJECT_SAR,
    AMEND_SAR,

    // Data Access
    VIEW_PII,
    EXPORT_DATA,
    MODIFY_RISK_SCORES,
    VIEW_TRANSACTION_DETAILS,

    // Screening
    VIEW_SCREENING_RESULTS,
    MANAGE_WATCHLISTS,
    WHITELIST_ENTITY,
    OVERRIDE_SCREENING_MATCH,

    // System Administration
    MANAGE_USERS,
    MANAGE_ROLES,
    MANAGE_RULES,
    VIEW_AUDIT_LOGS,
    CONFIGURE_SYSTEM,

    // PSP Administration
    MANAGE_PSP,
    MANAGE_PSP_THEME,
    PSP_SETTINGS_VIEW,
    PSP_SETTINGS_EDIT,
    PSP_UI_EDIT,

    // Merchant Data Access
    MERCHANT_VIEW,
    MERCHANT_EDIT,

    // Reporting
    REPORT_VIEW
}
