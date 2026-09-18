# Regulatory & CBK Reporting

## Overview

Comprehensive regulatory reporting covering FIU requirements (SAR, CTR, LCTR, IFTR) and Central Bank of Kenya GDI (Gateway Data Interface) submissions.

## Reports Center Runtime

The Reports Center is backed by persisted `reports`, versioned `report_definitions`, and `report_executions`; it does not manufacture preview or history rows in the browser. Report definitions must declare an explicit bound `:pspId` predicate for tenant runs. Definitions that omit tenant scope fail closed instead of having SQL text rewritten at runtime.

The runtime supports:

- Preview with database-provided column aliases, typed date parameters, an exact count query, and tenant isolation.
- Durable asynchronous generation: a `PENDING` execution is committed before work starts, then advances through `RUNNING` to `COMPLETED` or `FAILED`.
- PDF, CSV, XML, and real OpenXML XLSX files. The stored format, extension, byte count, and download format must agree.
- Tenant-checked history, status, retry, delete, and download operations. DTOs never expose the server filesystem path.
- Source-record and calculation provenance through report run traces, allowing report rows to link back to record detail views.

### Report APIs

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/reports/definitions` | Browse active report definitions |
| `POST` | `/api/v1/reports/preview` | Execute a bounded preview |
| `POST` | `/api/v1/reports/generate` | Queue a durable report execution |
| `GET` | `/api/v1/reports/status/{executionId}` | Poll generation status |
| `GET` | `/api/v1/reports/history` | Browse tenant-scoped execution history |
| `GET` | `/api/v1/reports/download/{id}` | Download a completed output in its real format |
| `POST` | `/api/v1/reports/schedule` | Create a recurring report schedule |
| `GET` | `/api/v1/reports/schedule/{scheduleId}/history` | Inspect dispatch and delivery evidence |

### Scheduled Generation And Delivery

`ScheduledReportRunner` atomically claims due schedules with a database lock, advances `next_run_at`, materializes the previous day/week/month/quarter/year in the schedule timezone, and queues one execution per configured output format. Each dispatch writes `report_schedule_history` evidence with the execution, format, recipients, scheduled time, completion or delivery state, and any error.

Email delivery uses the configured Spring `JavaMailSender` and attaches the generated report. A schedule with recipients fails visibly when email is disabled or SMTP is not configured; it is never recorded as delivered without a real send.

```properties
REPORTING_SCHEDULER_ENABLED=true
REPORTING_SCHEDULER_POLL_MS=60000
REPORTING_SCHEDULER_INITIAL_DELAY_MS=30000
EMAIL_NOTIFICATIONS_ENABLED=true
SPRING_MAIL_HOST=smtp.example.com
```

## Regulatory Reports

### Suspicious Activity Report (SAR)

```http
GET /api/v1/reporting/regulatory/sar
```

Generated via `RegulatoryReportingService`:
- goAML-pattern XML via `FrcReportingService`
- SAR type: INITIAL, FOLLOW_UP, or CANCELLATION
- Includes narrative, transaction details, risk indicators

### Currency Transaction Report (CTR)

```http
GET /api/v1/reporting/regulatory/ctr
GET /api/v1/reporting/regulatory/ctr/export?format=pdf|csv
```

The Kenya CTR path is cash-only. A transaction is reportable when its approved
USD equivalent is at least USD 15,000. The stored transaction evidence includes
the original amount and currency, USD equivalent, threshold, FX source, FX
effective time, and evaluation time. Non-cash payments never enter this report.

Regulatory conversion rates are managed at:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/compliance/exchange-rates` | Inspect rates and approval state |
| `PUT` | `/api/v1/compliance/exchange-rates/{currency}` | Approve a sourced, effective rate |
| `POST` | `/api/v1/compliance/exchange-rates/{currency}/revoke` | Revoke regulatory approval |

Legacy billing seed rates are not regulatory evidence. A missing, unapproved,
expired, or stale rate causes a cash transaction to be held for review rather
than treated as below threshold.

### Large Cash Transaction Report (LCTR)

```http
GET /api/v1/reporting/regulatory/lctr
GET /api/v1/reporting/regulatory/lctr/export?format=pdf|csv
```

LCTR is an internal large-cash view and uses the same stored conversion evidence.
It does not infer cash activity from currency code or raw payment amount.

### International Funds Transfer Report (IFTR)

```http
GET /api/v1/reporting/regulatory/iftr
GET /api/v1/reporting/regulatory/iftr/export?format=pdf|csv
```

## CBK (Central Bank of Kenya) GDI Reporting

### 17 Endpoints

| # | Endpoint | Cadence | Data Source |
|---|---|---|---|
| 1 | Senior Management Schedule | Annual (Jan 5) | PspSeniorManagement table |
| 2 | Schedule of Directors | Annual (Jan 5) | PspDirector table |
| 3 | Schedule of Trustees | Annual (Jan 5) | PspTrustee table |
| 4 | Schedule of Shareholders | Annual (Jan 4) | PspShareholder table |
| 5 | Customer Complaints | Monthly (Day 3) | PspCustomerComplaint table |
| 6 | Cybersecurity Incident | Daily | PspCyberIncident table |
| 7 | Fraud/Theft/Robbery Incidents | Daily | PspFraudIncident table (auto-populated) |
| 8 | System Stability & Interruption | Daily | PspSystemInterruption table |
| 9 | System Activity (24h TPS/TPH) | Daily | TransactionRepository aggregation |
| 10 | Products Info | Monthly (Day 1) | PspProduct table |
| 11 | Trust Account | Daily | PspTrustAccount table |
| 12 | Card Brands | Monthly (Day 2) | TransactionRepository aggregation |
| 13 | Billing Template | Daily | TransactionRepository aggregation |
| 14 | Transaction Details | Monthly (Day 2) | TransactionRepository aggregation |
| 15 | Transaction Tariffs | Monthly (Day 1) | PspTariffTemplate table |
| 16 | Merchant Transactions (success) | Daily | TransactionRepository aggregation |
| 17 | Failed/Rejected Transactions | Daily | TransactionRepository aggregation |

### Submission Pipeline

```
CbkScheduler (6 cron jobs)
    │ Daily 2AM, Monthly spread Days 1-3, Annual Jan 4-5
    │ Conditional on cbk.enabled global flag
    ▼
CbkSubmissionOrchestrator
    │ 1. Resolves PSPs with cbkReportingEnabled=true
    │ 2. Dispatches to specific endpoint handler
    │ 3. Queries data for the reporting window
    │ 4. Submits via CbkGdiClient
    ▼
CbkGdiClient
    │ OAuth2 authentication (CbkTokenService)
    │ Multipart HTTP POST to CBK API
    │ Circuit breaker (Resilience4j) + retries
    ▼
CbkSubmission
    │ Status: PENDING → SUBMITTED → SUCCESS/FAILED
    │ PSP-scoped submission audit trail
    │ Auto-retry on failure (3 attempts)
```

### Submission Evidence

CBK calls retain the exact 2xx or error HTTP status and regulator response
excerpt. The request identifier is persisted only when CBK returns it; local
validation and transport failures never receive a fabricated CBK reference.
Each audit row also stores the exact reporting window and serialized source
record count.

Endpoint 16 uses persisted merchant settlement-account, contact, country,
economic-sector, and actual transaction-channel evidence. Endpoint 17 uses
persisted tokenized customer references, encrypted email, channel, merchant,
amount, and real rejection reasons. Incomplete source data fails before
transport.

### Live Lock System

3-guard promotion check:
1. Global `cbk.allow-live` (platform config)
2. Per-PSP `cbkAllowLive` (PSP config)
3. Per-PSP `cbkEnvironment` = "live"

### Fraud Incident Auto-Population

When an alert is resolved TRUE_POSITIVE:
- `AlertFraudIncidentBridge` creates `PspFraudIncident` automatically
- Feeds CBK GDI endpoint #7 (FRAUD_INCIDENTS) with real data
- No manual entry needed for confirmed fraud

## Page Features

**RegulatoryReportsPage**: CTR/LCTR/IFTR tabbed view with:
- Statistics cards (total transactions, total amount, period)
- Transaction detail table (first 20)
- CSV export
- CBK Submissions tab with filterable history

**CbkSubmissionsTab**: Filterable paginated table:
- PSP filter, status filter, endpoint filter
- Status badges (SUCCESS/green, FAILED/red, PENDING/yellow, RETRYING/purple)
- Replay button for failed submissions
- Request ID and record count display

## Report Export

Dynamic Reports Center definitions support PDF, CSV, XML, and real OpenXML XLSX. Legacy regulatory endpoints retain the endpoint-specific support shown below.

Supported formats per report type:

| Report | JSON | PDF (OpenPDF) | CSV (UTF-8 BOM) |
|---|---|---|---|
| Transaction Summary | ✅ | ✅ | ✅ |
| CTR | ✅ | ✅ | ✅ |
| LCTR | ✅ | ✅ | ✅ |
| IFTR | ✅ | ✅ | ✅ |
| SAR | ✅ | ❌ | ❌ |
| Case Report | ✅ | ✅ | ✅ |
| Alert Report | ✅ | ✅ | ✅ |

Dynamic report download: `/api/v1/reports/download/{id}?format=PDF|CSV|XML|XLSX`
