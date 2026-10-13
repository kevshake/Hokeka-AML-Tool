package com.posgateway.aml.dto.export;

/**
 * Identifies which Console list or record context a server-side export applies to.
 */
public enum ConsoleListViewType {
    ALERTS,
    CASES,
    TRANSACTIONS,
    MERCHANTS,
    AUDIT_LOGS,
    SARS,
    CHARGEBACKS,
    SCREENING_ACTIVITY,
    USERS,
    RULES,
    RECORD_DETAIL,
    CUSTOMERS,
    EDGE_NODES,
    PSPS,
    KYC_DOCUMENTS,
    ASSET_ACCOUNTS,
    MESSAGES,
    RUNTIME_ERRORS,
    MONITORING_TRANSACTIONS,
    REGULATORY_SUBMISSIONS
}
