# Hokeka AML Platform — PSP API Integration Guide

> **Version:** 2.0 — June 2026  
> **For:** Payment Service Providers, Banks, and Fintech Institutions  
> **Base URL:** `https://api.hokeka.com/api/v1` (Production)  
> **Swagger UI:** `https://api.hokeka.com/api/v1/swagger-ui.html`
>
> **W36-4 reconciliation (2026-08-27):** every documented `METHOD /path` in this guide has been
> checked against an actual `@RequestMapping`/`@GetMapping`/etc. in the current codebase. All
> match except the one fixed here (`/reports/download/{id}`, was documented as `{executionId}` —
> cosmetic path-variable naming only, not a functional break). W36-1 and W36-3 (session-prior)
> already corrected the two genuinely wrong paths this guide had.

---

## Table of Contents

1. [SaaS Model & PSP Onboarding](#1-saas-model--psp-onboarding)
2. [Authentication & Role-Based Access](#2-authentication--role-based-access)
3. [API Endpoint Reference](#3-api-endpoint-reference)
4. [Billing & Usage Tracking](#4-billing--usage-tracking)
5. [Transaction Monitoring](#5-transaction-monitoring)
6. [Sanctions Screening & AML Checks](#6-sanctions-screening--aml-checks)
7. [Alerts & Case Management](#7-alerts--case-management)
8. [Reports & Regulatory Compliance](#8-reports--regulatory-compliance)
9. [CBK (Central Bank of Kenya) Reporting](#9-cbk-central-bank-of-kenya-reporting)
10. [Webhooks & Event Subscriptions](#10-webhooks--event-subscriptions)
11. [Error Handling & Rate Limits](#11-error-handling--rate-limits)
12. [Integration Examples](#12-integration-examples)
13. [SDK & Client Libraries](#13-sdk--client-libraries)
14. [Support & Troubleshooting](#14-support--troubleshooting)

---

## 1. SaaS Model & PSP Onboarding

### Multi-Tenant Architecture

Hokeka AML is a **single-instance, multi-tenant SaaS platform**. Each PSP
(Payment Service Provider) or Bank is a tenant with **fully isolated data**:

```
┌─────────────────────────────────────────────────────────┐
│                   Hokeka AML Platform                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │  PSP A   │  │  PSP B   │  │  PSP C   │  │  Bank D  │ │
│  │ (Tenant) │  │ (Tenant) │  │ (Tenant) │  │ (Tenant) │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
│  ┌─────────────────────────────────────────────────────┐ │
│  │        Shared Infrastructure (Scoring, Rules,      │ │
│  │         Analytics, Reporting, Cross-PSP Intel)     │ │
│  └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

**Key principles:**

| Principle | Description |
|---|---|
| **Data Isolation** | Each PSP sees only its own merchants, transactions, alerts, cases, and reports |
| **Shared Intelligence** | Fraud patterns benefit all PSPs (anonymized cross-PSP fraud flags) |
| **Per-PSP Configuration** | Rules, risk thresholds, screening lists, and billing plans are per-PSP |
| **Role-Based Access** | PSP_ADMIN manages their PSP; SUPER_ADMIN manages the platform |

### PSP User Roles

| Role | Privileges |
|---|---|
| **PSP_ADMIN** | View own PSP's usage, billing, invoices, reports; manage own users; configure PSP profile |
| **PSP_USER** | View own PSP's alerts, cases, transactions; screen names; generate reports |
| **ANALYST** | Investigate alerts, review cases, add notes and evidence |
| **COMPLIANCE_OFFICER** | Make case decisions (APPROVE/REJECT/FILE_SAR); manage escalations |
| **INVESTIGATOR** | Full investigation workflow (timeline, network graph, case replay) |
| **ADMIN** | Platform-wide: manage all PSPs, users, billing, settings, CBK configuration |
| **SUPER_ADMIN** | Full system access including platform configuration, billing administration |

### Onboarding Flow

```
1. Registration ──→ POST /psps/register (public) or via platform admin
       │
2. Activation ────→ SUPER_ADMIN activates the PSP account
       │
3. User Setup ────→ PSP_ADMIN users created (can be bulk-imported)
       │
4. API Credentials → JWT tokens issued on login
       │
5. Configuration ──→ Risk thresholds, rules, screening lists configured
       │
6. Integration ────→ API keys used in production transaction flow
       │
7. Go Live ────────→ Platform admin promotes CBK environment to live
```

---

## 2. Authentication & Role-Based Access

The platform has two authentication modes. Use the one that matches the caller:

| Caller | Mechanism | Endpoints |
|---|---|---|
| **Your systems** (payment switch, back office jobs) | **PSP API key** — `X-Api-Key: hka_…` or `Authorization: Bearer hka_…` | Transaction ingest and assessment read-back (below) |
| **People** using the Console | **Session cookie** (`JSESSIONID`) after `POST /api/v1/auth/login` | Everything the user's role allows |

There is **no JWT**: a `Bearer` header is only accepted when it carries a PSP API key (`hka_…`).

### 2.1 Machine integration — PSP API keys

Hokeka platform administrators issue keys for your PSP (`POST /api/v1/admin/psp-api-keys?pspId=…`;
ask your Hokeka contact). The full key (`hka_` + 43 characters) is shown **once**; only its SHA-256
hash is stored. Keys are rotated with `POST /api/v1/admin/psp-api-keys/{id}/rotate` (the old key
stops working immediately) and revoked with `DELETE /api/v1/admin/psp-api-keys/{id}`. Keys may
carry an expiry (`hokeka.api-keys.default-ttl-days`).

```http
POST /api/v1/transactions/ingest
X-Api-Key: hka_8Hq2...            # or: Authorization: Bearer hka_8Hq2...
Idempotency-Key: auth-2026-000123
Content-Type: application/json
```

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/transactions/ingest` | Ingest one transaction (§5) |
| `POST /api/v1/transactions/ingest/batch` | Ingest a batch |
| `GET /api/v1/transactions/{id}/assessment` | Read the assessment and findings for one of your transactions |

Rules:

- The key is bound to **your PSP**. Every merchant in an ingest request must belong to that PSP;
  any other merchant id is answered `404 Merchant not found` (exactly like a non-existent one).
- An invalid, revoked or expired key, or a key of a PSP that is not `ACTIVE`, gets
  `401 {"error":"UNAUTHORIZED"}`. A key sent to any other endpoint is ignored (`401`/`403`).
- Rate limits: per key `hokeka.api-keys.rate-limit-per-minute` (default 1200/min) and per client IP
  for failed key attempts (default 20/min). Both answer `429` (`Retry-After: 60` for the per-key
  limit).
- Requests are stateless: no session cookie is created.

### 2.2 Console users — session cookie

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "username": "psp_admin@mypayment.com", "password": "your-secure-password" }
```

**Response** — the server sets the `JSESSIONID` cookie (`Secure`, `HttpOnly`, `SameSite=Strict`);
send it on every later request.

```json
{
  "success": true,
  "user": { "id": 42, "username": "psp_admin@mypayment.com", "role": "PSP_ADMIN", "pspId": 7 },
  "redirectUrl": "/dashboard"
}
```

- Idle timeout: 30 minutes (`SESSION_TIMEOUT`). `GET /api/v1/auth/session/check` reports the time
  remaining; `POST /api/v1/auth/session/refresh` extends the session.
- One active session per user: a new login ends the previous one.
- `POST /api/v1/auth/logout` ends the session.

### PSP Scope Isolation

The system determines your PSP scope from the authenticated principal (the API key's PSP, or the
logged-in user's PSP):

- **PSP_ADMIN / PSP_USER users** — all queries are automatically scoped
  to their PSP's data. They cannot see other PSPs' transactions, alerts,
  or cases.
- **Platform ADMIN / SUPER_ADMIN users** — can see all PSPs' data and
  can optionally filter by `pspId` query parameter.

An attempt to access another PSP's data returns `403 FORBIDDEN`.

---

## 3. API Endpoint Reference

### Base URLs

| Environment | Base URL |
|---|---|
| **Local Development** | `http://localhost:2637/api/v1` |
| **Sandbox / Staging** | `https://testapi.hokeka.com/api/v1` |
| **Production** | `https://api.hokeka.com/api/v1` |

### All API Groups

| Group | Base Path | Description | PSP Access |
|---|---|---|---|
| **Auth** | `/auth` | Login (session cookie), session refresh, password reset | ✅ All roles |
| **Transactions** | `/transactions` | Ingest & monitor transactions | ✅ PSP roles |
| **Alerts** | `/alerts` | Fraud & AML alerts with disposition | ✅ PSP roles |
| **Cases** | `/cases` | Case management & investigation | ✅ PSP roles |
| **Screening** | `/screening` | Sanctions, PEP & watchlist screening | ✅ PSP roles |
| **Risk** | `/risk` | Country risk, risk scores, heatmaps | ✅ PSP roles |
| **Monitoring** | `/monitoring` | Live transaction feed, analytics | ✅ PSP roles |
| **Reports** | `/reports` | Generate & schedule reports | ✅ PSP roles |
| **Billing** | `/billing` | Invoices, usage, payments | ✅ PSP_ADMIN |
| **Payments** | `/billing/payments` | M-Pesa & bank payments | ✅ PSP_ADMIN |
| **Subscriptions** | `/subscriptions` | Plan & tier management | ✅ PSP_ADMIN |
| **PSPs** | `/psps` | PSP profile & configuration | ✅ PSP_ADMIN |
| **Compliance** | `/compliance` | SAR, KYC, CBK reporting | ✅ Compliance roles (SAR register: MLRO / COMPLIANCE_OFFICER only) |
| **Blacklist** | `/blacklist` | Payment blacklist: PAN, terminal, IP, device, payment details, wallet | ✅ PSP_ADMIN, COMPLIANCE_OFFICER (own PSP; platform-wide list read-only) |
| **Admin** | `/admin/psp-billing` | Billing management portal | ❌ Admin only |
| **Users** | `/users` | User & role management | ✅ PSP_ADMIN |

### Rate Limiting

| Tier | Requests/Minute | Burst |
|---|---|---|
| **PSP Standard** | 1000 RPM | 50 burst |
| **PSP Premium** | 5000 RPM | 200 burst |
| **Enterprise** | Custom | Custom |

Rate limit headers are returned on every response:
```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 987
X-RateLimit-Reset: 1623456789
```

When exceeded:
```json
{
  "error": "rate_limit_exceeded",
  "message": "API rate limit exceeded. Retry after 45 seconds.",
  "retryAfter": 45
}
```

### Payment blacklist

Entries are scoped to your PSP; Hokeka also maintains a platform-wide list that applies to every
PSP (you can read it, only Hokeka can change it). Removing an entry deactivates it (kept for audit);
adding the same value again re-activates it. Every change is audit-logged.

```http
POST /api/v1/blacklist
Cookie: JSESSIONID=...
Content-Type: application/json

{ "entryType": "pan", "value": "4111111111111111", "reason": "confirmed card fraud",
  "expiresAt": "2026-12-31T23:59:59" }
```

- `entryType`: `pan`, `terminal`, `ip`, `device`, `payment_details`, `wallet`.
- A PAN is accepted raw (12–19 digits) or as its 64-hex lookup hash; it is stored **only** as the
  hash the ingest pipeline uses, never in clear.
- `GET /api/v1/blacklist?type=&active=&page=&size=`, `GET /api/v1/blacklist/{id}`,
  `PATCH /api/v1/blacklist/{id}` (`reason`, `expiresAt`, `clearExpiry`, `active`),
  `DELETE /api/v1/blacklist/{id}?reason=`. A duplicate active entry → `409`.

---

## 4. Billing & Usage Tracking

### How You Are Billed

Every API call you make is tracked and billed according to your
subscription plan. Three billing models are available:

| Model | Description | Best For |
|---|---|---|
| **PER_REQUEST** | `baseRate × requestCount` | Low-volume / variable usage |
| **SUBSCRIPTION** | `monthlyFee + max(0, count - includedRequests) × overageRate` | Predictable volume |
| **TIERED** | Cumulative tier pricing (e.g., 0-10K @ $0.005, 10K-100K @ $0.004, unlimited @ $0.003) | High volume |

### Tracked Service Types

| Service Type | What Gets Tracked |
|---|---|
| `TRANSACTION_PROCESSING` | Each transaction scored by the fraud engine |
| `SANCTIONS_SCREENING` | Each name screened against sanctions lists |
| `AML_CHECK` | Each AML risk assessment performed |
| `RISK_ASSESSMENT` | Each merchant/customer risk assessment |
| `REPORT_GENERATION` | Each report generated or exported |
| `CASE_MANAGEMENT` | Case operations (create, update, decision) |
| `MERCHANT_ONBOARDING` | Merchant onboarding checks |

### Viewing Your Usage

**Real-time current month usage:**
```http
GET /api/v1/billing/usage/{pspId}/current
Cookie: JSESSIONID=...
```

**Response:**
```json
{
  "pspId": 7,
  "period": "2026-06 (live)",
  "totalRequests": 15423,
  "billableRequests": 15423,
  "totalCost": 771.15,
  "breakdown": [
    { "serviceType": "TRANSACTION_PROCESSING", "requestCount": 12000, "cost": 600.00 },
    { "serviceType": "SANCTIONS_SCREENING", "requestCount": 3000, "cost": 150.00 },
    { "serviceType": "AML_CHECK", "requestCount": 423, "cost": 21.15 }
  ]
}
```

**Monthly usage summary:**
```http
GET /api/v1/billing/usage/{pspId}?month=2026-05
```

### Invoice Lifecycle

```
Usage Recorded ──→ Monthly Invoice Generated ──→ SENT ──→ PAID
                    (1st of month @ 2AM)                (via M-Pesa/Bank)
                                                          │
                                                       OVERDUE (past due date)
                                                          │
                                                       ESCALATED (>30 days)
```

### Subscription Management

```http
# Get active subscription
GET /api/v1/subscriptions/psp/{pspId}

# List all subscriptions
GET /api/v1/subscriptions

# Get usage history for billing
GET /api/v1/subscriptions/{id}/usage-history
```

### Available Pricing Tiers

| Tier | Monthly Fee | Per-Check Price | Included Checks | Monthly Minimum |
|---|---|---|---|---|
| **Starter** | $0 | $0.050 | 1,000 | $0 |
| **Growth** | $199 | $0.030 | 10,000 | $199 |
| **Scale** | $499 | $0.020 | 50,000 | $499 |
| **Enterprise** | $999 | $0.010 | 200,000 | $999 |

Volume discounts apply automatically for high-volume tiers.

---

## 5. Transaction Monitoring

> **Edge + cloud dual-post (required for compliance):** If you deploy an **Edge Node** for pre-auth
> evaluation, you must **also** call `POST /api/v1/transactions/ingest` after authorisation so the
> control plane persists the transaction and raises alerts/cases/webhooks. Edge `/edge/evaluate` alone
> does not create cloud compliance artifacts. Authoritative contract:
> [`docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`](EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md). Edge install:
> [`docs/edge-client-install-guide.md`](edge-client-install-guide.md).

### Core Transaction Flow

```
PSP System                          Hokeka AML Platform
    │                                       │
    │  POST /transactions/ingest            │
    │──────────────────────────────────────▶│
    │                                       │
    │                          ┌────────────┤
    │                          │  Feature   │
    │                          │ Extraction │
    │                          │ (80+ feat) │
    │                          ├────────────┤
    │                          │  Scoring   │
    │                          │  L1: Aero  │
    │                          │  L2: Redis │
    │                          │  L3: XGB   │
    │                          │  L4: Rules │
    │                          ├────────────┤
    │                          │  Decision  │
    │                          │  ALLOW     │
    │                          │  HOLD      │
    │                          │  BLOCK     │
    │                          │  ALERT     │
    │                          └────────────┤
    │                                       │
    │  { decision, score, riskFactors }     │
    │◀──────────────────────────────────────│
```

#### Ingest a Transaction

```http
POST /api/v1/transactions/ingest
X-Api-Key: hka_...
Content-Type: application/json

{
  "amountCents": 1250000,
  "currency": "KES",
  "merchantId": "123",
  "terminalId": "TERM-456",
  "pan": "4111111111111111",
  "ipAddress": "41.204.187.12",
  "countryCode": "KE",
  "direction": "OUTBOUND",
  "channelType": "ECOMMERCE",
  "cashTransaction": false,
  "customerAccountReference": "PSP-TOKEN-CUST-78901",
  "customerEmail": "customer@example.com",
  "acquirerResponse": "00"
}
```

`merchantId` is the numeric platform merchant ID represented as a string; it must belong to your
PSP. If you pre-authorised the transaction on an Edge Node, also send the `edgeAssessmentId` from
the edge response (see `docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`).

Ingest errors are JSON: a non-numeric `merchantId` or a non-UUID `edgeAssessmentId` → `400`; a
merchant that does not exist or belongs to another PSP → `404`; an `edgeAssessmentId` already linked
to another transaction → `409`. Nothing is persisted for a rejected request.
`amountCents` is an integer in the currency's minor unit. Send raw PAN only over
TLS; the backend hashes it immediately and never persists the raw value.
`customerAccountReference` must be a PSP-issued token or opaque reference, not
a raw bank account number. It and `customerEmail` are required source evidence
for CBK failed/rejected-transaction reporting; email is encrypted at rest.

**Response:**

```json
{
  "txnId": 1048576,
  "decision": "ALLOW",
  "riskScore": 23.5,
  "riskLevel": "LOW",
  "riskFactors": [],
  "triggeredRules": []
}
```

#### Decision Values

| Decision | Meaning | Action for PSP |
|---|---|---|
| **ALLOW** | No suspicious signals detected | Process normally |
| **HOLD** | Moderate risk detected | Additional verification recommended |
| **BLOCK** | High risk or hard rule triggered | Decline the transaction |
| **ALERT** | Risk detected but below BLOCK threshold | Flag for manual review |

#### Batch Scoring

**Correction (W36-3):** this section previously documented `POST /api/v1/transactions/batch-score`
— that exact path doesn't exist. The real endpoint offering this capability (submit an array of
transactions, get a fraud result back per transaction) is:

```http
POST /api/v1/transactions/ingest/batch
X-Api-Key: hka_...
Content-Type: application/json

[
  { "amountCents": 1250000, "currency": "KES", "merchantId": "101", ... },
  { "amountCents": 50000, "currency": "KES", "merchantId": "102", ... }
]
```

Note the body is a bare JSON array of transaction objects, not `{"transactions": [...]}`. The
response is a JSON array of fraud detection results, one per submitted transaction, in the same
order.

Separately, there is an **admin-only, no-request-body** trigger for the standing "score
yesterday's transactions" background job — this is an ops/maintenance action, not something a PSP
integration calls as part of normal transaction flow:

```http
POST /api/v1/batch/score/yesterday
Cookie: JSESSIONID=...    # Console session; SUPER_ADMIN or ADMIN role only
```

---

## 6. Sanctions Screening & AML Checks

### Name Screening

Screen individuals or entities against OFAC, UN, EU, and custom
watchlists in real-time:

```http
POST /api/v1/sanctions/screen
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "name": "John Doe",
  "dateOfBirth": "1980-01-15",
  "country": "KE"
}
```

**Response:**

```json
{
  "matchFound": false,
  "screenId": "scr_abc123",
  "similarity": 0.0,
  "matches": [],
  "screeningResult": "CLEAR"
}
```

### AML Check

```http
POST /api/v1/aml/check
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "amount": 15000.00,
  "currency": "KES",
  "country": "KE",
  "merchantId": "MRC-00123",
  "senderAccount": "ACC-789",
  "receiverAccount": "ACC-456"
}
```

### Risk Assessment

```http
GET /api/v1/risk/customer/{merchantId}/rating
Cookie: JSESSIONID=...
```

---

## 7. Alerts & Case Management

### Alert Lifecycle

```
Transaction
    │
    ▼
Alert Created (OPEN)
    │
    ▼
Investigator Reviews ──┬── FALSE_POSITIVE → closed
    │                   │
    │                   ├── CLEARED → resolved
    │                   │
    │                   ├── TRUE_POSITIVE → case created
    │                   │
    │                   └── ESCALATED → case created + escalated
    │
    ▼
Case Created ──┬── APPROVE → CLOSED_CLEARED
               │
               ├── REJECT → CLOSED_BLOCKED
               │
               └── FILE_SAR → CLOSED_SAR_FILED
```

### List Alerts

```http
GET /api/v1/alerts?page=0&size=25&status=open
Cookie: JSESSIONID=...
```

### Resolve an Alert

```http
PUT /api/v1/alerts/{id}/resolve
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "disposition": "TRUE_POSITIVE_BLOCKED",
  "notes": "Card testing pattern detected. Merchant blocked."
}
```

### Case Management

```http
# Get case timeline
GET /api/v1/cases/{id}/timeline

# Get case network graph
GET /api/v1/cases/{id}/graph?depth=2

# Make a decision
POST /api/v1/cases/{id}/decisions
{
  "decisionType": "FILE_SAR",
  "justification": "Transactions match structuring pattern (7 deposits of $9,500)"
}

# Escalate a case
POST /api/v1/cases/{id}/escalate
{
  "reason": "Amount exceeds $100K threshold, requires MLRO review"
}
```

### Escalation Matrix

| Priority | SLA | Escalation Path |
|---|---|---|
| **CRITICAL** | 4 hours | → MLRO |
| **HIGH** | 24 hours | → COMPLIANCE_OFFICER |
| **MEDIUM** | 3 days | → ANALYST |
| **LOW** | 7 days | → ANALYST |

---

## 8. Reports & Regulatory Compliance

### Generate a Report

```http
POST /api/v1/reports/generate
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "reportType": "TRANSACTION_SUMMARY",
  "parameters": {
    "dateFrom": "2026-05-01",
    "dateTo": "2026-05-31"
  }
}
```

### Download Report

```http
GET /api/v1/reports/download/{id}?format=pdf
```

Supported formats: `pdf`, `csv`, `xml`

### Schedule Recurring Reports

```http
POST /api/v1/reports/schedule
Content-Type: application/json

{
  "reportId": "TRANSACTION_SUMMARY",
  "schedule": "0 0 8 1 * *",
  "parameters": { "pspId": 7 },
  "recipients": ["compliance@mypsp.com"]
}
```

---

## 9. CBK (Central Bank of Kenya) Reporting

For PSPs licensed by the Central Bank of Kenya, the platform provides
automated GDI (Gateway Data Interface) submissions covering all 17
required endpoints.

### CBK Endpoint Categories

| Cadence | Count | Endpoints |
|---|---|---|
| **Daily** | 8 | Cyber incidents, Fraud incidents, System stability, System activity (24h TPS/TPH), Trust account, Billing template, Merchant transactions, Failed transactions |
| **Monthly** | 5 | Customer complaints, Products info, Card brands, Transaction details, Tariffs |
| **Annual** | 4 | Senior management, Directors, Trustees, Shareholders |

### CBK Configuration

PSP_ADMINs can view their CBK configuration:
```http
GET /api/v1/psps/{id}/cbk-config
```

SUPER_ADMINs configure CBK credentials and promote to live:
```http
PUT /api/v1/psps/{id}/cbk-config
```

### Submission History

```http
GET /api/v1/compliance/cbk/submissions?pspId=7&status=SUBMITTED
```

Manual retrigger for a specific endpoint:
```http
POST /api/v1/compliance/cbk/submissions/{endpointType}/run
{ "pspId": 7 }
```

---

## 10. Webhooks & Event Subscriptions

**Corrected (W36-2):** this section previously documented a richer feature than exists — a single
`POST /webhooks/subscribe` call accepting multiple `events` and a client-supplied `secret`, with
eight event types spanning transactions/alerts/cases/invoices. The real, working endpoint (it
returned 404 before this fix; there was no controller at all) is simpler:

```http
POST /api/v1/webhooks/subscribe
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "callbackUrl": "https://mypsp.com/webhooks/hokeka",
  "eventType": "RISK_ALERT"
}
```

Each call creates **one subscription for one event type** — call it again with a different
`eventType` to subscribe to more than one. The signing secret is **server-generated**, not
client-supplied; it's returned in the response body's `secretKey` field (store it — it isn't shown
again). `callbackUrl` must be HTTPS. Subscriptions are automatically scoped to your own PSP; you
cannot subscribe on another tenant's behalf.

```http
GET /api/v1/webhooks/subscriptions        # list your own PSP's subscriptions
DELETE /api/v1/webhooks/subscriptions/{id}  # unsubscribe
```

### Available Events

All three event types are delivered through the **durable webhook outbox** (same transactional
pattern as Kafka internal events). Delivery is retried with exponential backoff; permanently failed
rows are marked `FAILED` in `event_outbox` for operator visibility. Subscriptions auto-disable after
five consecutive delivery failures.

| Event | Triggered When |
|---|---|
| `RISK_ALERT` | New fraud/AML alert from transaction monitoring (`DecisionEngine`) |
| `CASE_UPDATE` | Compliance case created or updated; case decision recorded |
| `MERCHANT_STATUS_CHANGE` | PSP status transition (manual admin, dunning suspension, payment reactivation) |

> **Edge vs cloud:** `POST /edge/evaluate` does **not** emit webhooks. Subscribe and receive events
> only from cloud paths (primarily `/api/v1/transactions/ingest`). See
> [`docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`](EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md).

### Webhook Payload (RISK_ALERT)

```json
{
  "alertId": 445566,
  "txnId": 1048576,
  "action": "HOLD",
  "score": 0.75,
  "status": "open",
  "severity": "HIGH",
  "sarRequired": true,
  "ctrRequired": false,
  "triggeredRules": ["R-2", "R-7", "R-30"],
  "pspId": 7,
  "merchantId": "4",
  "timestamp": "2026-06-29T14:32:15"
}
```

### Webhook Payload (CASE_UPDATE)

```json
{
  "caseId": 1234,
  "caseReference": "CASE-20260918-ABC",
  "previousStatus": "IN_PROGRESS",
  "status": "CLOSED_CLEARED",
  "decision": "APPROVE",
  "resolution": "CLEARED",
  "merchantId": 4,
  "pspId": 7,
  "decidedBy": "analyst@psp.com",
  "timestamp": "2026-09-18T14:32:15"
}
```

### Webhook Payload (MERCHANT_STATUS_CHANGE)

```json
{
  "pspId": 7,
  "pspCode": "ACME",
  "previousStatus": "ACTIVE",
  "status": "SUSPENDED",
  "reason": "MANUAL_STATUS_UPDATE",
  "timestamp": "2026-09-18T14:32:15"
}
```

### Transaction ingest idempotency

Duplicate POSTs to `/api/v1/transactions/ingest` with the same key do not create duplicate
transactions or alerts. Supply either:

- Header: `Idempotency-Key: <stable reference>` (preferred), or
- Body field: `"clientReference": "<stable reference>"`

The key is scoped per PSP (`psp_id` + `client_reference` unique index). Retries return the
existing transaction without re-running statistics or outbox enqueue.

---

## 11. Error Handling & Rate Limits

### HTTP Status Codes

| Code | Meaning | Action |
|---|---|---|
| **200 OK** | Success | Process response |
| **201 Created** | Resource created | Extract ID from response |
| **202 Accepted** | Async request accepted | Poll status endpoint |
| **400 Bad Request** | Invalid payload | Check request format |
| **401 Unauthorized** | Missing/expired session, or invalid/revoked/expired API key | Log in again / check the key |
| **403 Forbidden** | Insufficient permissions | Check PSP scope |
| **404 Not Found** | Resource not found | Verify resource ID |
| **409 Conflict** | Duplicate resource | Check idempotency key |
| **422 Unprocessable** | Business rule violation | Check validation errors |
| **429 Too Many Requests** | Rate limit exceeded | Backoff and retry |
| **500 Internal Error** | Server error | Retry with backoff |

### Error Response Format

```json
{
  "error": "validation_failed",
  "message": "Amount must be greater than 0",
  "status": 422,
  "timestamp": "2026-06-29T14:30:00Z",
  "path": "/api/v1/transactions/ingest",
  "details": {
    "field": "amount",
    "rejectedValue": -100
  }
}
```

---

## 12. Integration Examples

### cURL

```bash
export HOKEKA_API_KEY=hka_...   # issued for your PSP; keep it in your secrets manager

# Ingest a transaction (machine integration)
curl -s -X POST https://api.hokeka.com/api/v1/transactions/ingest   -H "X-Api-Key: $HOKEKA_API_KEY"   -H "Idempotency-Key: auth-2026-000123"   -H "Content-Type: application/json"   -d '{
    "merchantId": "123",
    "amountCents": 1250000,
    "currency": "KES",
    "pan": "4111111111111111",
    "ipAddress": "41.204.187.12",
    "countryCode": "KE"
  }'

# Read the assessment of a transaction you ingested
curl -s https://api.hokeka.com/api/v1/transactions/1048576/assessment   -H "X-Api-Key: $HOKEKA_API_KEY"
```

### Python

```python
import os
import requests


class HokekaAMLClient:
    """Machine client: authenticates every call with the PSP API key (no login, no session)."""

    def __init__(self, base_url, api_key):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.session.headers.update({"X-Api-Key": api_key})

    def ingest(self, merchant_id, amount_cents, currency, client_reference, **fields):
        payload = {"merchantId": str(merchant_id), "amountCents": amount_cents,
                   "currency": currency, **fields}
        resp = self.session.post(f"{self.base_url}/transactions/ingest", json=payload,
                                 headers={"Idempotency-Key": client_reference}, timeout=10)
        if resp.status_code == 429:
            raise RuntimeError("rate limited; retry after " + resp.headers.get("Retry-After", "60") + "s")
        resp.raise_for_status()
        return resp.json()


client = HokekaAMLClient("https://api.hokeka.com/api/v1", os.environ["HOKEKA_API_KEY"])
result = client.ingest(123, 1250000, "KES", "auth-2026-000123", ipAddress="41.204.187.12")
print(result["action"], result.get("score"))
```

### JavaScript / Node.js

```javascript
const axios = require('axios');

const hokeka = axios.create({
  baseURL: 'https://api.hokeka.com/api/v1',
  headers: { 'X-Api-Key': process.env.HOKEKA_API_KEY },
  timeout: 10000,
});

async function ingest(txn, clientReference) {
  const { data } = await hokeka.post('/transactions/ingest', txn, {
    headers: { 'Idempotency-Key': clientReference },
  });
  return data;
}

const result = await ingest(
  { merchantId: '123', amountCents: 1250000, currency: 'KES' },
  'auth-2026-000123'
);
console.log(`Decision: ${result.action}`);
```

### Java / Spring Boot

```java
@Service
public class HokekaAmlClient {
    private final RestClient rest;

    public HokekaAmlClient(@Value("${hokeka.api.base-url}") String baseUrl,
                           @Value("${hokeka.api.key}") String apiKey) {
        this.rest = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .build();
    }

    public Map<?, ?> ingest(String merchantId, long amountCents, String currency, String clientReference) {
        return rest.post().uri("/transactions/ingest")
                .header("Idempotency-Key", clientReference)
                .body(Map.of("merchantId", merchantId, "amountCents", amountCents, "currency", currency))
                .retrieve()
                .body(Map.class);
    }
}
```

---

## 13. SDK & Client Libraries

Official client libraries are available for:

| Language | Package | Status |
|---|---|---|
| **Python** | `pip install hokeka-aml` | ✅ Available |
| **Node.js** | `npm install @hokeka/aml-client` | ✅ Available |
| **Java** | Maven: `com.hokeka:aml-client` | ✅ Available |
| **PHP** | `composer require hokeka/aml-client` | ✅ Available |
| **Go** | `go get github.com/hokeka/aml-go` | ✅ Available |

---

## 14. Support & Troubleshooting

### Integration Checklist

- [ ] PSP account created and activated by platform admin
- [ ] PSP_ADMIN user credentials issued
- [ ] JWT authentication working
- [ ] Transaction ingestion tested in sandbox
- [ ] Decision handling implemented (ALLOW/HOLD/BLOCK/ALERT)
- [ ] Webhook endpoints configured (optional but recommended)
- [ ] Billing usage monitoring set up
- [ ] Production go-live approved

### Common Issues

| Symptom | Likely Cause | Solution |
|---|---|---|
| `401 Unauthorized` | Expired token | Re-authenticate or refresh token |
| `403 Forbidden` | Wrong role for endpoint | Check user has required role |
| `429 Rate Limited` | Exceeded RPM | Check rate limit headers; upgrade plan |
| High false positive rate | Rules not tuned | Contact support for rule calibration |
| Missing data in reports | PSP not configured for CBK | Contact platform admin |

### Getting Help

| Channel | Contact |
|---|---|
| **Support Email** | support@hokeka.com |
| **Integration Help** | integrations@hokeka.com |
| **Billing Queries** | billing@hokeka.com |
| **Swagger UI** | `https://api.hokeka.com/api/v1/swagger-ui.html` |
| **Status Page** | `https://status.hokeka.com` |

---

**Last Updated:** June 29, 2026  
**© Hokeka AML Platform** — *Modern AML & Fraud Detection for PSPs and Banks*
