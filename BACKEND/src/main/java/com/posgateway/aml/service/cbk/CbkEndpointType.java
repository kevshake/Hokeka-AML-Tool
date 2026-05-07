package com.posgateway.aml.service.cbk;

/**
 * Enumeration of all 17 CBK GDI report endpoints, sourced verbatim from
 * {@code docs/integrations/CBK_API_INVENTORY.md}.
 *
 * <p>Metadata carried per constant:
 * <ul>
 *   <li>{@code displayName} – human-readable label for logging / UI</li>
 *   <li>{@code cadence} – {@link Cadence#DAILY}, {@link Cadence#MONTHLY}, or {@link Cadence#ANNUAL}</li>
 *   <li>{@code targetDay} – day-of-month (1-31) on which the endpoint fires, or
 *       {@code -1} for DAILY endpoints (no specific day constraint)</li>
 *   <li>{@code wrapperKey} – the JSON payload wrapper key expected by CBK</li>
 *   <li>{@code urlPath} – the API path appended to {@code https://{host}{postPrefix}}</li>
 * </ul>
 */
public enum CbkEndpointType {

    // =========================================================================
    // Annual — PSP company configuration reference (4 endpoints)
    // =========================================================================

    /** #1 Senior management schedule — Annual, Jan 5 */
    SENIOR_MANAGEMENT(
            "Senior Management Schedule",
            Cadence.ANNUAL, 5,
            "SENIOR_MNGT_SCHEDULE",
            "/api/v1/flows/rest/CBK_API_SENIO_MNGT_SCHED_PAREN/1.0/CBK_API_SENIOR_MNGT_SCHEDULE_PARENT"
    ),

    /** #2 Schedule of directors — Annual, Jan 5 */
    DIRECTORS(
            "Schedule of Directors",
            Cadence.ANNUAL, 5,
            "SCHED_OF_DIR",
            "/api/v1/flows/rest/CBK_API_SCHED_OF_DIR_PAREN/1.0/CBK_API_SCHED_OF_DIR_PARENT"
    ),

    /** #3 Schedule of trustees — Annual, Jan 5 */
    TRUSTEES(
            "Schedule of Trustees",
            Cadence.ANNUAL, 5,
            "SCHED_OF_TRUSTEES",
            "/api/v1/flows/rest/CBK_API_SCHED_OF_TRUST_PAREN/1.0/CBK_API_SCHED_OF_TRUSTEES_PARENT"
    ),

    /** #4 Schedule of shareholders — Annual, Jan 4 */
    SHAREHOLDERS(
            "Schedule of Shareholders",
            Cadence.ANNUAL, 4,
            "SCHED_OF_SHARE_HLDRS",
            "/api/v1/flows/rest/CBK_API_SCHE_OF_SHAR_HLDR_PARE/1.0/CBK_API_SCHED_OF_SHARE_HLDRS_PARENT"
    ),

    // =========================================================================
    // Monthly (5 endpoints)
    // =========================================================================

    /** #5 Customer complaints — Monthly, Day 3 */
    CUSTOMER_COMPLAINTS(
            "Customer Complaints",
            Cadence.MONTHLY, 3,
            "PSP_CUTOMER_COMPLAINTS",
            "/api/v1/flows/rest/API_PSPSCHEDULECUSTC/1.0/PSPs_Sched_CustComplnts_and_Remedials"
    ),

    /** #10 Products info — Monthly, Day 1 */
    PRODUCTS_INFO(
            "Products Info",
            Cadence.MONTHLY, 1,
            "PSP_PRODUCTS_INFO",
            "/api/v1/flows/rest/API_MOBILEPSPPRODUCTSSYNC/1.0/PSPs_Products"
    ),

    /** #12 Card brands — Monthly, Day 2 */
    CARD_BRANDS(
            "Card Brands",
            Cadence.MONTHLY, 2,
            "PYMT_GW_CARD_BRANDS",
            "/api/v1/flows/rest/CBK_API_PAYEM_GATEW_CARD_BRAND/1.0/PAYMENTGATEWAYCARDBRANDS"
    ),

    /** #14 Transaction details — Monthly (no fixed day; fires on both day-1 and day-2 crons) */
    TRANSACTION_DETAILS(
            "Transaction Details",
            Cadence.MONTHLY, 2,
            "PAYMENT_GATEWAY_TRANSACTIONS_DETAILS",
            "/api/v1/flows/rest/API_PAYGATWAYTRXDETAILSSYNC/1.0/PSPs_PayGtwy_Txn_Details"
    ),

    /** #15 Transaction tariffs — Monthly (no fixed day; fires on both day-1 and day-2 crons) */
    TRANSACTION_TARIFFS(
            "Transaction Tariffs",
            Cadence.MONTHLY, 1,
            "PAYMENT_GATEWAY_TARIFFS",
            "/api/v1/flows/rest/API_PAYMENTGATEWAYSYNC/1.0/PSPs_PayGtwy_Txn_Tarrifs"
    ),

    // =========================================================================
    // Daily (8 endpoints)
    // =========================================================================

    /** #6 Cybersecurity incident — Daily */
    CYBER_INCIDENT(
            "Cybersecurity Incident Record",
            Cadence.DAILY, -1,
            "PSP_CYBERSECURITY_INCIDENT_RECORD",
            "/api/v1/flows/rest/API_PSPCYBERSECURITYSYNC/1.0/PSPs_Cybersec_Incident_Record"
    ),

    /** #7 Fraud/theft/robbery incidents — Daily */
    FRAUD_INCIDENTS(
            "Fraud/Theft/Robbery Incidents",
            Cadence.DAILY, -1,
            "INCIDENTS_DATA",
            "/api/v1/flows/rest/API_INCIDENTSINFOSYN/1.0/PSPs_Fraud_Theft_Robbery_Incidents"
    ),

    /** #8 System stability and service interruption — Daily */
    SYSTEM_STABILITY(
            "System Stability and Service Interruption",
            Cadence.DAILY, -1,
            "SCH_SY_STABIL_SRVCE_INT",
            "/api/v1/flows/rest/API_SCHDLESYSTEMSTBSVCINTERPSYNC/1.0/PSPs_Sched_SysStability_and_Srvc_Intrpt"
    ),

    /** #9 System activity (24 TPS/TPH rows per day) — Daily */
    SYSTEM_ACTIVITY(
            "System Activity",
            Cadence.DAILY, -1,
            "SYSTEM_ACTIVITY_INFO",
            "/api/v1/flows/rest/API_SYSTEMACTIVITYSYNC/1.0/PSPs_System_Activity"
    ),

    /** #11 Trust account — Daily */
    TRUST_ACCOUNT(
            "Trust Account",
            Cadence.DAILY, -1,
            "TRUSTACCOUNT_DATA",
            "/api/v1/flows/rest/API_TRUSTACCOUNTSYNC/1.0/PSPs_Trust_acct"
    ),

    /** #13 Billing template — Daily */
    BILLING_TEMPLATE(
            "Billing Template",
            Cadence.DAILY, -1,
            "PAY_GTWAY_BILL_TEMP",
            "/api/v1/flows/rest/API_PAYMENTGATEWAYBITEMPSY/1.0/PSPs_PayGtwy_Billing_Temp"
    ),

    /** #16 Merchant transactions (successes) — Daily */
    MERCHANT_TRANSACTIONS(
            "Merchant Transactions",
            Cadence.DAILY, -1,
            "MERCHANT_STLMNT_ACCT_DATA",
            "/api/v1/flows/rest/API_PSPMERCHANTTRANS/1.0/PSPs_PayGtwy_Merch_Txns"
    ),

    /** #17 Failed/rejected transactions — Daily */
    FAILED_TRANSACTIONS(
            "Failed/Rejected Transactions",
            Cadence.DAILY, -1,
            "FAILED_REJECTED_TRX_INFO",
            "/api/v1/flows/rest/API_FAILEDREJECTEDTRXSYNC/1.0/PSPs_PayGtwy_Failed_Rjected_Txns"
    );

    // =========================================================================

    public enum Cadence {
        DAILY, MONTHLY, ANNUAL
    }

    private final String displayName;
    private final Cadence cadence;
    /**
     * Day-of-month on which this endpoint fires. -1 = daily (no day constraint).
     * For MONTHLY/ANNUAL this is the exact DOM the scheduler checks before firing.
     */
    private final int targetDay;
    private final String wrapperKey;
    private final String urlPath;

    CbkEndpointType(String displayName, Cadence cadence, int targetDay,
                    String wrapperKey, String urlPath) {
        this.displayName = displayName;
        this.cadence = cadence;
        this.targetDay = targetDay;
        this.wrapperKey = wrapperKey;
        this.urlPath = urlPath;
    }

    public String getDisplayName() { return displayName; }
    public Cadence getCadence() { return cadence; }
    public int getTargetDay() { return targetDay; }
    public String getWrapperKey() { return wrapperKey; }
    public String getUrlPath() { return urlPath; }
}
