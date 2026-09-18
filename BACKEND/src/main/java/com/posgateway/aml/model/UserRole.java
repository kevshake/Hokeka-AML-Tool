package com.posgateway.aml.model;

/**
 * User Roles for AML System
 * Defines the roles available in the system
 */
public enum UserRole {
    SUPER_ADMIN, // Full system access - manages PSPs and global settings
    // Platform operator: cross-PSP visibility without full super-admin powers.
    // Seeded as roles.name = 'PLATFORM_ADMIN' (e.g. platform.admin demo account).
    PLATFORM_ADMIN,
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
    SENIOR_ANALYST, // New: Escalate/Approve capabilities
    // W20-8 fix: "PSP_USER" is a real, registerable role name -- AuthenticationController.
    // register()'s default-role fallback chain tries VIEWER, then PSP_USER, then USER. Before
    // this constant existed, any account actually registered under that fallback (VIEWER
    // unseeded) hit UserRole.valueOf(user.getRole().getName()) at ~20 call sites (case
    // management, PSP CBK-filing controllers, case permissions/escalation) and got an uncaught
    // IllegalArgumentException. Tenant-scoped, least-privilege by default: every permission
    // check in CasePermissionService/CaseEscalationService/WorkflowAutomationService is a
    // positive allow-list (`role == X || role == Y`), so PSP_USER simply falls through to
    // "not permitted" wherever it isn't explicitly granted -- no other branch needed adjusting.
    PSP_USER,
    // Same fix, same reasoning: "USER" is the third and final fallback in that same chain and
    // was equally missing from this enum.
    USER,
    // Machine/service-account role (Grafana, reporting-config). Seeded by V218; referenced by
    // raw role-name checks in PspIsolationService and GrafanaUserContextController.
    APP_CONTROLLER
}
