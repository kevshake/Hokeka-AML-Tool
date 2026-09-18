# CBK GDI API Inventory

Source: `D:\WORK\CBK_EDW\CBK\` (Spring Boot 17, port 9785, Oracle backend).
This is the contract document for porting CBK GDI submissions into the AML platform.

---

## Auth (OAuth2 client_credentials)

- **Token endpoint:** `https://gdicbk.centralbank.go.ke/oauth2/v1/token`
- **Form params:** `grant_type=client_credentials`, `scope`, `client_id`, `client_secret`
- **Live host:** `gdicbk.centralbank.go.ke`
- **Pre-prod host:** `gdi.centralbank.go.ke` with `/preprod` URL prefix
- **Live `client_id`:** `e68c5dbfde8c412c89a2eecb7d86507d` _(legacy app's hard-coded value — do NOT reuse, fetch from PSP config / env)_
- **Live `scope`:** `https://014E63FF296F4C5CAC66A00FFCEA22D4.ke1.s7071808.oraclecloudatcustomer.com:443urn:opc:resource:consumer::all`
- **Pre-prod `scope`:** `https://8CBB5ED2490D46BD924E028C30F9B697.ke1.s7071808.oraclecloudatcustomer.com:443urn:opc:resource:consumer::all`
- **Token usage:** `Authorization: Bearer {token}`
- **Expiry:** read `expires_in`, subtract 10s buffer, refresh on next call when expired.

---

## Common envelope

Every report posts a JSON body with this wrapper:

```json
{
  "INSTITUTION_CODE": "<PSP institution code>",
  "REQUEST_ID": "",
  "REPORTING_DATE": "yyyy-MM-dd",
  "IS_ATTACHED": "N",
  "<WRAPPER_KEY>": [ { "<record fields>" } ]
}
```

- `REPORTING_DATE` is either today or yesterday depending on endpoint.
- Sub-record PSP-id key naming is inconsistent (8 variants in legacy code: `PSP ID`, `PSP_ID`, `PSP_CODE`, `Psp ID`, both nested-inside-record and as separate sub-objects). Each endpoint section below records the variant CBK actually expects.
- Outer transport: `multipart/mixed; boundary="----=_Part_1_<RANDOM_ID>"` with `Content-Transfer-Encoding: 8bit` before the JSON. We will reproduce this faithfully.

---

## Endpoints

URL prefix below is the path appended to `https://{baseHost}{post_prefix}` (post_prefix = `""` live, `"/preprod"` pre-prod).

### Reference — PSP company configuration (one-shot, not periodic)

These four are sourced from PSP master data; they fire annually but materially never change unless directors/shareholders change.

| # | Enum | URL path | Wrapper key | When |
|---|------|----------|-------------|------|
| 1 | `txn_psp_senior_mngt_schedule` | `/api/v1/flows/rest/CBK_API_SENIO_MNGT_SCHED_PAREN/1.0/CBK_API_SENIOR_MNGT_SCHEDULE_PARENT` | `SENIOR_MNGT_SCHEDULE` | Annual, Jan 5 |
| 2 | `txn_psp_sched_of_dir` | `/api/v1/flows/rest/CBK_API_SCHED_OF_DIR_PAREN/1.0/CBK_API_SCHED_OF_DIR_PARENT` | `SCHED_OF_DIR` | Annual, Jan 5 |
| 3 | `txn_psp_sched_of_trustees` | `/api/v1/flows/rest/CBK_API_SCHED_OF_TRUST_PAREN/1.0/CBK_API_SCHED_OF_TRUSTEES_PARENT` | `SCHED_OF_TRUSTEES` | Annual, Jan 5 |
| 4 | `txn_psp_sched_of_share_hldrs` | `/api/v1/flows/rest/CBK_API_SCHE_OF_SHAR_HLDR_PARE/1.0/CBK_API_SCHED_OF_SHARE_HLDRS_PARENT` | `SCHED_OF_SHARE_HLDRS` | Annual, Jan 4 |

**Per-record fields:**

- **Senior management** (#1): `PSP_ID, OFFICER_NAMES, GENDER, DESIGNATION, DOB, NATIONALITY, ID_NO, TAX_ID, QUALIFICATION, DATE_OF_EMP, EMP_TYPE, RETIREMENT_DT, EXTERNAL_AFFLIATES, OTHER_DISCLOSURE`
- **Directors** (#2): `PSP_ID, DIRECTOR_NAMES, DIRECTOR_GENDER, TYPE_OF_DIRECTOR, DOB, NATIONALITY, RESIDENT_COUNTRY, ID_NO_PASSPORT, PIN, CONTACT_NUMBER, QUALIFICATIONS, OTHER_DIRECTORSHIPS, DATE_OF_APPOINTMENT, DATE_OF_RETIREMENT, RETIREMENT_REASON, DISCLOSURES`
- **Trustees** (#3): `PSP_ID, TRUST_COMP_NAME, DIRECTORS_TRUST_COMP, TRUSTEE_NAMES, TRUSTEE_GENDER, DOB, NATIONALITY, RESIDENT_COUNTRY, ID_NO_PASSPORT, PIN, CONTACT_NUMBER, QUALIFICATIONS, OTHERS_TRUSTEESHIPS, DISCLOSURES, SHAREHOLDERS, SHAREHOLDING_PERCENTAGE`
- **Shareholders** (#4): `PSP_ID, SHAREHOLDER_NAME, SHAREHOLDER_GENDER, SHAREHOLDER_TYPE, DOB_OR_REG_DATE, NATIONALITY, RESIDENT_COUNTRY, COUNTRY_OF_INC, ID_NO_PASSPORT, PIN, CONTACT_NUMBER, QUALIFICATIONS, PREVIOUS_EMPLOYMENT, ONBOARDING_DATE, NO_OF_SHARES_HELD, SHARE_VALUE, PERCENTAGE_OF_SHARE`

### Operational — daily

| # | Enum | URL path | Wrapper key |
|---|------|----------|-------------|
| 6 | `txn_psp_cybersecurity_incident_record` | `/api/v1/flows/rest/API_PSPCYBERSECURITYSYNC/1.0/PSPs_Cybersec_Incident_Record` | `PSP_CYBERSECURITY_INCIDENT_RECORD` |
| 7 | `txn_psp_incidents_data` | `/api/v1/flows/rest/API_INCIDENTSINFOSYN/1.0/PSPs_Fraud_Theft_Robbery_Incidents` | `INCIDENTS_DATA` |
| 8 | `txn_psp_sy_stabil_srvce_int` | `/api/v1/flows/rest/API_SCHDLESYSTEMSTBSVCINTERPSYNC/1.0/PSPs_Sched_SysStability_and_Srvc_Intrpt` | `SCH_SY_STABIL_SRVCE_INT` |
| 9 | `txn_psp_system_activity` | `/api/v1/flows/rest/API_SYSTEMACTIVITYSYNC/1.0/PSPs_System_Activity` | `SYSTEM_ACTIVITY_INFO` |
| 11 | `txn_psp_trust_account` | `/api/v1/flows/rest/API_TRUSTACCOUNTSYNC/1.0/PSPs_Trust_acct` | `TRUSTACCOUNT_DATA` |
| 13 | `txn_gw_billing_template` | `/api/v1/flows/rest/API_PAYMENTGATEWAYBITEMPSY/1.0/PSPs_PayGtwy_Billing_Temp` | `PAY_GTWAY_BILL_TEMP` |
| 16 | `txn_gw_merchant_trx_info` | `/api/v1/flows/rest/API_PSPMERCHANTTRANS/1.0/PSPs_PayGtwy_Merch_Txns` | `MERCHANT_STLMNT_ACCT_DATA` |
| 17 | `txn_gw_failed_rejected_trx_info` | `/api/v1/flows/rest/API_FAILEDREJECTEDTRXSYNC/1.0/PSPs_PayGtwy_Failed_Rjected_Txns` | `FAILED_REJECTED_TRX_INFO` |

**Per-record fields:**

- **#6 Cybersecurity incident:** `PSP_ID, INCIDENT_NUMBER, REPORTING_DATE, LOCATION_OF_ATTACKER, INCIDENT_MODE, DATE_AND_TIME_OF_INCIDENT_HAPPENED, LOSS_TYPE, DETAILS_OF_THE_INCIDENT, ACTION_TAKEN_TO_MANAGE_THE_INCIDENT, DATE_AND_TIME_OF_THE_INCIDENT_RESOLUTION, ACTION_TAKEN_TO_MITIGATE_FUTURE_INCIDENTS, AMOUNT_INVOLVED, AMOUNT_LOST`
- **#7 Fraud/theft/robbery:** `PSP_ID, REPORTING_DATE, SUB_COUNTY_CODE, SUB_FRAUD_CODE, FRAUD_CATEGORY_FLAG, VICTIM_CATEGORY, VICTIM_INFORMATION, DATE_OF_OCCURRENCE, NUMBER_OF_INCIDENCES, AMOUNT_INVOLVED, AMOUNT_LOST, AMOUNT_RECOVERED, ACTION_TAKEN, RECOVERY_DETAILS`
- **#8 System stability/service interruption:** `PSP_ID, REPORTING_DATE, SUB_COUNTY_CODE, SYSTEM_OWNER_FLAG, THIRD_PARTY_OWNED_CATEGORY, THIRD_PARTY_NAME, PRODUCT_TYPE, SYSTEM_UNAVAILABILITY_TYPE_COD, THIRD_PARTY_SYSTEM_AFFECTED, SERVICE_INTERRUPTION_CAUSE_COD, SEVERITY_INTERRUPTION_CODE, RECOVERY_TIME_CODE, REMEDIAL_STATUS_CODE, SYSTEM_UPTIME_PERCENTAGE`
- **#9 System activity:** `PSP_ID, REPORTING_DATE, HOUR_OF_THE_DAY, NUMBER_OF_TXNS_PER_SEC, NUMBER_OF_TRANSACTIONS_PER_HOUR` _(24 records/day, one per hour)_
- **#11 Trust account:** `PSP_ID, REPORTING_DATE, BANK_ID, BANK_ACCOUNT_NUMBER, TRUST_ACC_DR_TYPE_CODE, ORG_RECEIVING_DONATION, SECTOR_CODE, TRUST_ACC_INT_UTILIZED_DETAILS, TRUST_ACC_OPENING_BALANCE, PRINCIPAL_AMOUNT, TRUST_ACC_INTEREST_EARNED, CLOSING_BALANCE, TRUST_ACC_INTEREST_UTILIZED, TRUST_FIELDS`
- **#13 Billing template:** `ROW_ID, REPORTING_DATE, BILL_CLASSIFICATION_CODE, NUMBER_OF_TRANSACTION, VALUE_OF_TRANSACTIONS`
- **#16 Merchant transactions:** `BANK ID, REPORTING DATE, MERCHANT ACCOUNT NUMBER, CHANNEL OF SETTLEMENT, MERCHANT ID, EMAIL ADDRESS, MERCHANT COUNTRY, ECONOMIC SECTORS, NUMBER OF TRANSACTIONS, VALUE OF TRANSACTIONS` _(field names contain spaces — keep verbatim)_
- **#17 Failed/rejected transactions:** `BANK ID, REPORTING DATE, CUSTOMER ACCOUNT NUMBER, CHANNEL OF SETTLEMENT, MERCHANT ID, Email, REJECTION FAILURE REASON, NUMBER OF TRANSACTIONS, VALUE OF TRANSACTIONS`

### Operational — monthly

| # | Enum | URL path | Wrapper key | When |
|---|------|----------|-------------|------|
| 5 | `txn_psp_cutomer_complaints` | `/api/v1/flows/rest/API_PSPSCHEDULECUSTC/1.0/PSPs_Sched_CustComplnts_and_Remedials` | `PSP_CUTOMER_COMPLAINTS` | Day 3 each month |
| 10 | `txn_psp_products_info` | `/api/v1/flows/rest/API_MOBILEPSPPRODUCTSSYNC/1.0/PSPs_Products` | `PSP_PRODUCTS_INFO` | Day 1 each month |
| 12 | `txn_gw_card_brands` | `/api/v1/flows/rest/CBK_API_PAYEM_GATEW_CARD_BRAND/1.0/PAYMENTGATEWAYCARDBRANDS` | `PYMT_GW_CARD_BRANDS` | Day 2 each month |
| 14 | `txn_gw_transactions_details` | `/api/v1/flows/rest/API_PAYGATWAYTRXDETAILSSYNC/1.0/PSPs_PayGtwy_Txn_Details` | `PAYMENT_GATEWAY_TRANSACTIONS_DETAILS` | Monthly |
| 15 | `txn_gw_transactions_tariffs` | `/api/v1/flows/rest/API_PAYMENTGATEWAYSYNC/1.0/PSPs_PayGtwy_Txn_Tarrifs` | `PAYMENT_GATEWAY_TARIFFS` | Monthly |

**Per-record fields:**

- **#5 Customer complaints:** `PSP_ID, COMPLAINT_ID, COMPLAINT_CODE, COMPLAINANT_GENDER, COMPLAINT_FREQUENCY, COMPLAINANT_NAME, COMPLAINANT_AGE, COMPLAINANT_CONTACT_NUMBER, COMPLAINANT_SUB_COUNTY_LOCATION, COMPLAINANT_EDUCATION_LEVEL, OTHERS_COMPLAINANT_DETAILS, AGENT_ID, DATE_OF_OCCURRENCE, DATE_REPORTED_TO_THE_INSTITUTION, DATE_RESOLVED, REMEDIAL_STATUS, AMOUNT_LOST, AMOUNT_RECOVERED`
- **#10 Products info:** `PSP_ID, REPORTING_DATE, PRODUCT_OWNERSHIP_FLAG, PRODUCT_OWNERSHIP_CATEGORY, PRODUCT_PARTNER_NAME, PRODUCT_TRANSACTION_CODE, GENDER, STATUS_CODE, BAND_CODE, NO_OF_CUSTOMERS, NO_OF_TRANSACTIONS, VALUE_OF_TRANSACTIONS, PRODUCT_NAME`
- **#12 Card brands:** `ROW_ID, REPORTING_DATE, BANK_ID, TRANSACTION_CATEGORY, CARD_BRAND_TYPE, NUMBER_OF_TXNS, VALUE_OF_TXNS` _(card brand codes: CDB01=Visa, CDB02=Mastercard)_
- **#14 Transactions details:** `Reporting date, Row ID, Card brand type, Card type, Card class type, Mobile money partner ID, Mobile banking partner ID, Transaction category type, Channel type, Total number of transactions done, Total value of transactions done` _(spaces in field names)_
- **#15 Transactions tariffs:** `ROW ID, REPORTING DATE, CHANNEL USED, CHANNEL PARTNER NAME, CHARGE DESCRIPTION, PERCENTAGE TRANSACTION COST, ABSOLUTE TRANSACTION COST`

---

## Data sources mapping (where each report's data comes from in OUR system)

| CBK report | Local source in AML platform |
|------------|------------------------------|
| Senior management / Directors / Trustees / Shareholders | New PSP child entities (we are adding) |
| Customer complaints | Existing — extend from `case_management` (case type=COMPLAINT) or new `customer_complaints` table |
| Cybersecurity incidents | New `cyber_incident` table (admin enters via FE) |
| Fraud/theft/robbery incidents | Existing `Alert` + `ComplianceCase` (filter on category=FRAUD/THEFT/ROBBERY) |
| System stability / service interruption | New `system_interruption` table (admin enters via FE; could be auto-derived from Resilience4j circuit-breaker open events) |
| System activity (TPS/TPH per hour) | Aggregate from `transaction` table grouped by hour |
| Products info | New `psp_product` table (PSP config) + transaction aggregation |
| Trust account | New `trust_account` table + balance/interest movements |
| Billing template | Aggregate from `transaction` by bill_classification_code |
| Card brands | Aggregate from `transaction` grouped by card_brand_type |
| Transactions details | Aggregate from `transaction` grouped by brand/type/class/channel |
| Transactions tariffs | New `tariff_template` table (PSP config) |
| Merchant transactions (success) | Aggregate from `transaction` filtered status=SUCCESS, grouped by merchant_id |
| Failed/rejected transactions | Aggregate from `transaction` filtered status=FAILED/REJECTED |

---

## Scheduling matrix

| Endpoint | Daily | Monthly | Annual | Specific day | Notes |
|----------|:-----:|:-------:|:------:|:------------:|-------|
| #1 Senior mngt | | | X | Jan 5 | One-shot snapshot of management roster |
| #2 Directors | | | X | Jan 5 | |
| #3 Trustees | | | X | Jan 5 | |
| #4 Shareholders | | | X | Jan 4 | |
| #5 Complaints | | X | | Day 3 | Previous month's complaints |
| #6 Cyber incident | X | | | | One row per incident yesterday |
| #7 Fraud/theft | X | | | | Previous day's incidents |
| #8 System stability | X | | | | Previous day's outages |
| #9 System activity | X | | | | 24 records/day (TPS+TPH per hour) |
| #10 Products | | X | | Day 1 | Previous month's per-product metrics |
| #11 Trust account | X | | | | Daily balances |
| #12 Card brands | | X | | Day 2 | Previous month's volume by brand |
| #13 Billing template | X | | | | Previous day |
| #14 Txn details | | X | | | Previous month |
| #15 Txn tariffs | | X | | | Previous month |
| #16 Merchant txns | X | | | | Previous day successes |
| #17 Failed txns | X | | | | Previous day failures |

---

## Porting notes (gotchas)

1. **Hardcoded credentials in legacy app.** Move to: per-PSP config table (each PSP has its own institution code, optionally its own client_id/secret if CBK issues per-PSP credentials), env-var fallback, never check secrets into git.
2. **Two date conventions.** Some reports use today's date, others use yesterday. Replicate exactly per endpoint — it determines which day's data is being reported.
3. **PSP ID key naming inconsistency.** The legacy app keeps 8 variants (`PSP ID`, `PSP_ID`, `PSP_CODE`, `Psp ID` × 2 nesting forms). Don't normalize — match per-endpoint.
4. **Field names with spaces.** Several endpoints (#14, #15, #16, #17) use field names with literal spaces (`"BANK ID"`, `"Reporting date"`). Keep verbatim.
5. **Multipart wrapper.** Outer transport is `multipart/mixed; boundary=...` even though body is pure JSON. Required by CBK gateway.
6. **`expires_in` minus 10s buffer.** Always proactively refresh.
7. **`executed` flag.** Legacy app marks records `executed="1"` after successful POST and reads response `RequestNo` field. We will mirror this in `cbk_submission` table.
8. **Failure handling.** Legacy app returns null on error, no retries. Our impl must use Resilience4j retry + circuit breaker + persist failures for retry next cycle.
9. **Field-name typos.** Legacy DB has `custome_account_number` (typo for "customer") and mixed-case `Reporting_date`. CBK API expects the correct names — don't propagate the typos.
10. **24 hour-rows per day.** #9 (system activity) sends 24 records per daily POST, not one. Plan aggregation accordingly.

---

## Out of scope (legacy-only artefacts we will NOT port)

- `Cbk_Records_Params` / `Cbk_Txn_Generator` — these synthesize fake data for testing. Our system has real data.
- `oracledb_merchant` direct Oracle queries — we use Postgres + JPA.
- `RestAssured` — we use Spring `WebClient`.
- `LogEngine` / `LogThreader` / `Logs4jLogger` — we use SLF4J.
