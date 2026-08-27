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

    // User Skills (W19-4 fix: UserSkillController referenced these via hasAuthority(...) in
    // every @PreAuthorize on the class, but neither was ever registered here -- the authority
    // check could never match, so the endpoints were reachable ONLY via the hardcoded role list
    // (SUPER_ADMIN/ADMIN/COMPLIANCE_OFFICER) alongside it, defeating the point of having a
    // fine-grained permission as an alternative grant path for a role without full admin rights)
    MANAGE_SKILLS,
    CERTIFY_SKILLS,

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
