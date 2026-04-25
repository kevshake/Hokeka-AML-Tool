export interface User {
    id: number;
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    role: Role;
    psp: Psp | null;
    enabled: boolean;
    createdAt: string;
}

export interface Role {
    id: number;
    name: string;
    description: string;
    psp: Psp | null;
    permissions: Permission[];
}

export interface Psp {
    id: number;
    name: string;
    code: string;
}

export enum Permission {
    // Case Management
    VIEW_CASES = "VIEW_CASES",
    CREATE_CASES = "CREATE_CASES",
    ASSIGN_CASES = "ASSIGN_CASES",
    CLOSE_CASES = "CLOSE_CASES",
    ESCALATE_CASES = "ESCALATE_CASES",
    REOPEN_CASES = "REOPEN_CASES",
    ADD_CASE_NOTES = "ADD_CASE_NOTES",
    ADD_CASE_EVIDENCE = "ADD_CASE_EVIDENCE",

    // SAR Operations
    VIEW_SAR = "VIEW_SAR",
    CREATE_SAR = "CREATE_SAR",
    APPROVE_SAR = "APPROVE_SAR",
    FILE_SAR = "FILE_SAR",
    REJECT_SAR = "REJECT_SAR",
    AMEND_SAR = "AMEND_SAR",

    // Data Access
    VIEW_PII = "VIEW_PII",
    EXPORT_DATA = "EXPORT_DATA",
    MODIFY_RISK_SCORES = "MODIFY_RISK_SCORES",
    VIEW_TRANSACTION_DETAILS = "VIEW_TRANSACTION_DETAILS",

    // Screening
    VIEW_SCREENING_RESULTS = "VIEW_SCREENING_RESULTS",
    MANAGE_WATCHLISTS = "MANAGE_WATCHLISTS",
    WHITELIST_ENTITY = "WHITELIST_ENTITY",
    OVERRIDE_SCREENING_MATCH = "OVERRIDE_SCREENING_MATCH",

    // System Administration
    MANAGE_USERS = "MANAGE_USERS",
    MANAGE_ROLES = "MANAGE_ROLES",
    MANAGE_RULES = "MANAGE_RULES",
    VIEW_AUDIT_LOGS = "VIEW_AUDIT_LOGS",
    CONFIGURE_SYSTEM = "CONFIGURE_SYSTEM",

    // PSP Administration
    MANAGE_PSP = "MANAGE_PSP",
    MANAGE_PSP_THEME = "MANAGE_PSP_THEME",
    PSP_SETTINGS_VIEW = "PSP_SETTINGS_VIEW",
    PSP_SETTINGS_EDIT = "PSP_SETTINGS_EDIT",
    PSP_UI_EDIT = "PSP_UI_EDIT",

    // Merchant Data Access
    MERCHANT_VIEW = "MERCHANT_VIEW",
    MERCHANT_EDIT = "MERCHANT_EDIT",

    // Reporting
    REPORT_VIEW = "REPORT_VIEW",
}

export const PERMISSION_LABELS: Record<Permission, string> = {
    [Permission.VIEW_CASES]: "View Cases",
    [Permission.CREATE_CASES]: "Create Cases",
    [Permission.ASSIGN_CASES]: "Assign Cases",
    [Permission.CLOSE_CASES]: "Close Cases",
    [Permission.ESCALATE_CASES]: "Escalate Cases",
    [Permission.REOPEN_CASES]: "Reopen Cases",
    [Permission.ADD_CASE_NOTES]: "Add Case Notes",
    [Permission.ADD_CASE_EVIDENCE]: "Add Case Evidence",
    [Permission.VIEW_SAR]: "View SAR",
    [Permission.CREATE_SAR]: "Create SAR",
    [Permission.APPROVE_SAR]: "Approve SAR",
    [Permission.FILE_SAR]: "File SAR",
    [Permission.REJECT_SAR]: "Reject SAR",
    [Permission.AMEND_SAR]: "Amend SAR",
    [Permission.VIEW_PII]: "View PII",
    [Permission.EXPORT_DATA]: "Export Data",
    [Permission.MODIFY_RISK_SCORES]: "Modify Risk Scores",
    [Permission.VIEW_TRANSACTION_DETAILS]: "View Transaction Details",
    [Permission.VIEW_SCREENING_RESULTS]: "View Screening Results",
    [Permission.MANAGE_WATCHLISTS]: "Manage Watchlists",
    [Permission.WHITELIST_ENTITY]: "Whitelist Entity",
    [Permission.OVERRIDE_SCREENING_MATCH]: "Override Screening Match",
    [Permission.MANAGE_USERS]: "Manage Users",
    [Permission.MANAGE_ROLES]: "Manage Roles",
    [Permission.MANAGE_RULES]: "Manage Rules",
    [Permission.VIEW_AUDIT_LOGS]: "View Audit Logs",
    [Permission.CONFIGURE_SYSTEM]: "Configure System",
    [Permission.MANAGE_PSP]: "Manage PSP",
    [Permission.MANAGE_PSP_THEME]: "Manage PSP Theme",
    [Permission.PSP_SETTINGS_VIEW]: "PSP Settings View",
    [Permission.PSP_SETTINGS_EDIT]: "PSP Settings Edit",
    [Permission.PSP_UI_EDIT]: "PSP UI Edit",
    [Permission.MERCHANT_VIEW]: "Merchant View",
    [Permission.MERCHANT_EDIT]: "Merchant Edit",
    [Permission.REPORT_VIEW]: "Report View",
};

export const PERMISSION_CATEGORIES = {
    "Case Management": [
        Permission.VIEW_CASES,
        Permission.CREATE_CASES,
        Permission.ASSIGN_CASES,
        Permission.CLOSE_CASES,
        Permission.ESCALATE_CASES,
        Permission.REOPEN_CASES,
        Permission.ADD_CASE_NOTES,
        Permission.ADD_CASE_EVIDENCE,
    ],
    "SAR Operations": [
        Permission.VIEW_SAR,
        Permission.CREATE_SAR,
        Permission.APPROVE_SAR,
        Permission.FILE_SAR,
        Permission.REJECT_SAR,
        Permission.AMEND_SAR,
    ],
    "Data Access": [
        Permission.VIEW_PII,
        Permission.EXPORT_DATA,
        Permission.MODIFY_RISK_SCORES,
        Permission.VIEW_TRANSACTION_DETAILS,
    ],
    "Screening": [
        Permission.VIEW_SCREENING_RESULTS,
        Permission.MANAGE_WATCHLISTS,
        Permission.WHITELIST_ENTITY,
        Permission.OVERRIDE_SCREENING_MATCH,
    ],
    "System Administration": [
        Permission.MANAGE_USERS,
        Permission.MANAGE_ROLES,
        Permission.MANAGE_RULES,
        Permission.VIEW_AUDIT_LOGS,
        Permission.CONFIGURE_SYSTEM,
    ],
    "PSP Administration": [
        Permission.MANAGE_PSP,
        Permission.MANAGE_PSP_THEME,
        Permission.PSP_SETTINGS_VIEW,
        Permission.PSP_SETTINGS_EDIT,
        Permission.PSP_UI_EDIT,
    ],
    "Merchant Data": [Permission.MERCHANT_VIEW, Permission.MERCHANT_EDIT],
    "Reporting": [Permission.REPORT_VIEW],
};
