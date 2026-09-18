# Billing & Pricing

## Overview

The SaaS billing system tracks every API request per PSP, calculates costs based on the selected pricing tier, generates monthly invoices, and supports M-Pesa payments.

## Pricing Tiers

| Tier | Monthly Fee | Per-Check Price | Included Checks | Monthly Minimum |
|---|---|---|---|---|
| Starter | $0 | $0.050 | 1,000 | $0 |
| Growth | $199 | $0.030 | 10,000 | $199 |
| Scale | $499 | $0.020 | 50,000 | $499 |
| Enterprise | $999 | $0.010 | 200,000 | $999 |

## Billing Models

| Model | Calculation | Best For |
|---|---|---|
| PER_REQUEST | `baseRate × requestCount` | Variable usage |
| SUBSCRIPTION | `monthlyFee + max(0, count - included) × overageRate` | Predictable volume |
| TIERED | Cumulative tiers (0-10K @$0.005, 10K-100K @$0.004, etc.) | High volume |

## Billing Pipeline

```
Every API Request
    │
    ├── UsageTrackingFilter (OncePerRequestFilter)
    │   Intercepts /api/v1/* → resolves PSP + service type
    │   Calls ApiUsageTrackingService.logRequest() async
    ▼
    api_usage_log table
    │ psp_id, service_type, request_timestamp, billable, cost
    ▼
Monthly Billing (1st @ 2AM)
    │
    ├── BillingCycleScheduler iterates active subscriptions
    ├── BillingCalculationEngine reads ApiUsageLog + PricingTier
    ├── Computes: base usage + volume discounts + minimums
    └── Creates BillingCalculation record
    ▼
Invoice Generation
    │
    ├── InvoicePdfService (OpenPDF) creates branded A4 PDF
    ├── Line items: per-service breakdown + totals
    └── Invoice status: SENT
    ▼
Email Delivery
    │ BillingEmailService.sendInvoiceEmail()
    │ HTML email with PDF attachment
    │ To: PSP contact email
    ▼
Payment (M-Pesa Daraja)
    │
    ├── PaymentController initiates STK Push
    ├── M-Pesa callback → auto-mark PAID
    └── Invoice status: PAID
    ▼
Dunning (if unpaid)
    │
    ├── DunningScheduler (daily @ 9AM)
    │   → Reminder email (day 7, 14, 21)
    │   → Escalation email (day 30+)
    │   → PSP + platform admin notified
```

## Service Types Tracked

**Corrected (W36-5):** this table previously listed a vocabulary (`TRANSACTION_PROCESSING`,
`SANCTIONS_SCREENING`, `AML_CHECK`, `CASE_MANAGEMENT`) that `UsageTrackingFilter.URL_SERVICE_MAP`
no longer produces — those were the original V147 seed values, since superseded. Regenerated
directly from the filter's current mapping table:

| Service | Tracking Point (path pattern, POST unless noted) | Seeded rate ($/request) |
|---|---|---|
| SANCTIONS_SCREENING_PERSON | `/sanctions/screen/person*`, and the generic `/sanctions/screen*` fallback | 0.50 |
| SANCTIONS_SCREENING_ORGANIZATION | `/sanctions/screen/organization*` | 0.75 |
| AML_SCREENING | `/aml/check*`, `/aml/detection*`, `/screening/*` | 0.10 |
| TRANSACTION_MONITORING | `/transactions/ingest*` | 0.02 |
| RISK_ASSESSMENT | `/risk-assessment/assess*` | (V147 seed, still active) |
| REPORT_GENERATION | `/reports/generate*` (preview/chart reads are not billed) | (V147 seed, still active) |
| KYC_VERIFICATION | `/merchants/onboard*` | 2.00 |
| COMPLIANCE_CASE_CREATION | `/cases*` (create verb only; reads are not billed) | 1.00 |
| SAR_FILING | `/compliance/sar*` | 3.00 (V216 — previously unseeded, billed $0) |
| CBK_REPORTING | `/compliance/cbk*` (POST or PUT) | 1.50 (V216 — previously unseeded, billed $0) |

`API_CALL_GENERIC` is seeded in `billing_rates` (V149) but `URL_SERVICE_MAP` never actually
produces it — any request path that doesn't match one of the patterns above, or matches one on a
non-billable HTTP method, resolves to `null` and is **not tracked or billed at all**
(`resolveServiceType` returns `null` rather than falling back to a generic type). That row is
currently unused dead seed data, not a working catch-all.

Source of truth going forward: `UsageTrackingFilter.URL_SERVICE_MAP`/`resolveServiceType` (code)
and the `billing_rates` table (seeded across V147, V149, V216) — re-check both before trusting this
table again if either changes.

## Page Features

**BillingPage** (Admin): Revenue Dashboard (current MRR, pending invoices), Subscriptions management, Invoice history, Usage by PSP.

**BillingTab** (PSP): Plan details, current usage against limits, invoice history with PDF downloads.

**SubscriptionController** - Full CRUD:
- GET /subscriptions — list all
- GET /subscriptions/psp/{pspId} — active subscription
- POST /subscriptions — create
- PUT /subscriptions/{id} — update tier
- DELETE /subscriptions/{id} — cancel

## Email Notifications

| Type | Trigger | Content |
|---|---|---|
| Invoice | Monthly generation | HTML + PDF attachment |
| Dunning | 7/14/21 days overdue | Overdue reminder |
| Escalation | 30+ days overdue | Escalation notice (PSP + admin) |
| Usage Alert | Configurable threshold | Usage summary |