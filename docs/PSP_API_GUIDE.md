# Hokeka AML Platform — PSP API Integration Guide

> **Version:** 2.1 — October 2026 (Edge data residency)  
> **For:** Payment Service Providers, Banks, and Fintech Institutions  
> **Base URL:** `https://api.hokeka.com/api/v1` (Production)  
> **Swagger UI:** `https://api.hokeka.com/api/v1/swagger-ui.html`
>
> **W36-4 reconciliation (2026-08-27):** every documented `METHOD /path` in this guide has been
> checked against an actual `@RequestMapping`/`@GetMapping`/etc. in the current codebase. All
> match except the one fixed here (`/reports/download/{id}`, was documented as `{executionId}` —
> cosmetic path-variable naming only, not a functional break). W36-1 and W36-3 (session-prior)
> already corrected the two genuinely wrong paths this guide had.
>
> **Data residency (2026-10-17):** your Edge Node is the only component that evaluates
> transactions. The cloud never stores transaction data; it keeps decision records and the
> alert, case and SAR workflow. Normative contract:
> [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md). Items marked "planned (Rnn)"
> are not built yet; Rnn is the work package in
> [`architecture/EDGE_DATA_RESIDENCY_PLAN.md`](architecture/EDGE_DATA_RESIDENCY_PLAN.md).

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
| **Data Residency** | Transactions are evaluated and stored on your Edge Node. The cloud holds decision records (IDs, action, score, rule IDs) and workflow records only |
| **Shared Intelligence** | Opt-in only. Indicators come from filed SARs of opted-in PSPs: merchant IDs and keyed device tokens. No amounts, names, card data or the filing PSP's identity |
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
6. Integration ────→ Edge Node installed; your API nodes call /edge/evaluate
       │
7. Go Live ────────→ Platform admin promotes CBK environment to live
```

---

## 2. Authentication & Role-Based Access

The platform has two authentication modes. Use the one that matches the caller:

| Caller | Mechanism | Endpoints |
|---|---|---|
| **Your payment switch** → your Edge Node | TLS on the Edge's port 8443 (optional mutual TLS) | `POST /edge/evaluate` (§5) |
| **Your back office jobs** → cloud | **PSP API key** — `X-Api-Key: hka_…` or `Authorization: Bearer hka_…` | Assessment read-back; deprecated transaction ingest (below) |
| **People** using the Console | **Session cookie** (`JSESSIONID`) after `POST /api/v1/auth/login` | Everything the user's role allows |

There is **no JWT**: a `Bearer` header is only accepted when it carries a PSP API key (`hka_…`).

### 2.1 Machine integration — PSP API keys

Hokeka platform administrators issue keys for your PSP (`POST /api/v1/admin/psp-api-keys?pspId=…`;
ask your Hokeka contact). The full key (`hka_` + 43 characters) is shown **once**; only its SHA-256
hash is stored. Keys are rotated with `POST /api/v1/admin/psp-api-keys/{id}/rotate` (the old key
stops working immediately) and revoked with `DELETE /api/v1/admin/psp-api-keys/{id}`. Keys may
carry an expiry (`hokeka.api-keys.default-ttl-days`).

```http
GET /api/v1/transactions/1048576/assessment
X-Api-Key: hka_8Hq2...            # or: Authorization: Bearer hka_8Hq2...
```

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/transactions/{id}/assessment` | Read the assessment and findings for one of your cloud-ingested transactions |
| `POST /api/v1/transactions/ingest` | **Deprecated.** Legacy cloud ingest of one transaction (§5.6) |
| `POST /api/v1/transactions/ingest/batch` | **Deprecated.** Legacy cloud ingest of a batch |

The ingest endpoints store transaction data in the cloud, which the residency model forbids. They
keep working for existing integrations until work package R12 deletes them. Do not build new
integrations on them.

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
| **Transactions** | `/transactions` | Monitor transactions (ingest is deprecated, §5.6) | ✅ PSP roles |
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

Every billable cloud API call you make is tracked and billed according to your
subscription plan. Evaluations on your Edge Node are not metered today; see
[`features/BILLING_PRICING.md`](features/BILLING_PRICING.md) for the current service types and the
planned metering from decision records. Three billing models are available:

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

Your Edge Node evaluates every transaction on your premises. Your API nodes call it once per
transaction. You do not call the cloud per transaction.

Behaviour of `/edge/evaluate` in full: [`edge-transaction-evaluation.md`](edge-transaction-evaluation.md).
Residency rules: [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md). Edge install:
[`edge-client-install-guide.md`](edge-client-install-guide.md).

### 5.1 Core transaction flow

```
Your API node               Your Edge Node (on premises)                 Hokeka cloud
    │                              │                                          │
    │ POST /edge/evaluate          │                                          │
    │─────────────────────────────▶│ velocity (Aerospike) + rule bundle       │
    │                              │ record evaluation + decision record      │
    │                              │ in the Edge Ledger (PostgreSQL)          │
    │ { action, score, ... }       │                                          │
    │◀─────────────────────────────│                                          │
    │                              │ outbox: decision records v2              │
    │                              │ (mTLS, sealed, outbound 443) ───────────▶│ alerts, cases,
    │                              │                                          │ CTR deadlines,
    │                              │                                          │ webhooks
```

### 5.2 Evaluate a transaction

```http
POST https://edge.internal.mypsp.com:8443/edge/evaluate
Content-Type: application/json

{
  "txn_id": "auth-2026-000123",
  "pan_hash": "sha256:4a7d...",
  "amount_cents": 1250000,
  "currency": "KES",
  "country_code": "KE",
  "merchant_id": "123",
  "mcc": "5411",
  "channel": "ECOMMERCE"
}
```

- Send `pan_hash`, never the PAN.
- `txn_id` is your reference, 1..128 printable ASCII without spaces. A PAN-like value (13 to 19
  digits that pass the Luhn check) gets `422 {"error":"TXN_REF_LOOKS_LIKE_PAN"}`; an invalid one
  gets `422 {"error":"TXN_REF_INVALID"}`. Nothing is evaluated or stored in either case.
- `amount_cents` is an integer in the currency's minor unit.
- Other fields are passed to the rules as features.

**Response:**

```json
{
  "action": "ALERT",
  "score": 45,
  "triggeredRuleIds": [1204, 1311],
  "reasons": ["Card velocity above 10 transactions per hour"],
  "triggeredRuleVersionIds": [88012, 88140],
  "edgeAssessmentId": "3f0c9a2e-7d41-4b8e-9a51-0c2f6b1d7e44",
  "ruleBundleVersion": 37,
  "ruleBundleHash": "sha256:9b1f..."
}
```

#### Decision Values

| Decision | Meaning | Action for PSP |
|---|---|---|
| **ALLOW** | No suspicious signals detected | Process normally |
| **ALERT** | Risk detected but below HOLD | Process, and expect an alert for review |
| **HOLD** | Suspicious, or the node cannot decide safely (fail-closed) | Do not approve on your own; route to review or step-up |
| **BLOCK** | Hard rule triggered | Decline the transaction |

### 5.3 What the cloud receives

Your Edge writes each evaluation and its **decision record** to the Edge Ledger in one local
transaction. A background shipper sends the records, sealed and over mTLS, to
`POST /api/v1/edge/assessments`. A restart or a cloud outage loses nothing: the records wait in the
ledger outbox.

A decision record holds `edgeAssessmentId`, `txnRef` (your `txn_id`), node and site ids, the
evaluation time to the second, `decision`, `score`, rule ids and versions, bundle version and hash,
`merchantId`, `ctrRequired`, `sanctionsHit` with public watchlist entry ids, and typology codes.

It never holds the PAN or its hash, the amount, currency or country, customer, account, device or
IP identifiers, names, or free-text reasons. The cloud rejects any record with an unknown field.

From each record the cloud (current):

- raises an alert for `ALERT`, `HOLD`, `BLOCK`, a sanctions hit or a CTR flag;
- opens or joins a case for a sanctions hit, and for a rule decision when auto-create-cases is on;
- schedules a CTR filing deadline when `ctrRequired` is set;
- sends a `RISK_ALERT` webhook with IDs and the decision only (§10).

### 5.4 Viewing transactions

- **Today.** Alerts and cases show the `edgeAssessmentId` and `txnRef`. The transaction detail stays
  on your Edge. The Console transaction list reads only the legacy cloud store (§5.6).
- **Planned (R5, R6, R11).** The Console pulls transaction detail live from your Edge over the Edge
  Tunnel. Your Edge opens that WebSocket outbound on port 443 with mTLS. You open no inbound port.
  The cloud end of the tunnel exists today; the Edge client (R5), the cloud data proxy (R6) and the
  Console pages (R11) are not built yet. When they ship, every pull names the user and purpose and
  is logged both in the cloud and on your Edge, and you can restrict or turn off the tunnel locally.

### 5.5 Retention, support access and cross-PSP intelligence

- **Retention.** Set it in the Console under **Settings → Data Retention**. Minimum and default 5
  years (FATF R.11), maximum 25. A second approver must approve each change (maker-checker). An
  increase takes effect on approval; a reduction takes effect 30 days after approval. The setting
  reaches your Edge in the sealed config document (`GET /api/v1/edge/config`). The cloud purges its
  decision records past retention, except those linked to open alerts, cases, unfiled SARs or
  holds. The Edge purge is planned (R2); until then your Edge keeps every evaluation.
- **Hokeka support access.** Off by default. A PSP admin turns it on under **Settings → Hokeka
  support access**. While it is off, Hokeka staff cannot pull your Edge data: the cloud refuses the
  request. The Edge check is planned (R5). Each support pull needs a written reason and is shown
  in your access log. The cloud gate is built; support pulls themselves come with the data proxy
  (planned, R6).
- **Cross-PSP intelligence.** Opt-in under the same settings. Indicators come only from filed SARs
  of opted-in PSPs and are limited to merchant IDs and keyed device tokens. No PSP can read another
  PSP's data.
- **Filed reports.** Filed SAR, STR and CTR documents are kept in the cloud as the regulatory
  record. Drafts never store pulled transaction data.

### 5.6 Legacy cloud ingest (deprecated)

`POST /api/v1/transactions/ingest` and `POST /api/v1/transactions/ingest/batch` still work for
existing integrations. They store the transaction in the cloud and run the old cloud pipeline.
This contradicts the residency model.

- Do not build new integrations on them.
- Planned (R15): legacy cloud transactions are copied to your Edge Ledger and the copy is verified.
- Planned (R12): the cloud deletes the ingest endpoints and its transaction tables, after R15 and
  after every cloud-only detection runs on the Edge.

Reference for existing callers:

```http
POST /api/v1/transactions/ingest
X-Api-Key: hka_...
Idempotency-Key: auth-2026-000123
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

`merchantId` is the numeric platform merchant ID as a string; it must belong to your PSP. Errors:
a non-numeric `merchantId` or a non-UUID `edgeAssessmentId` → `400`; an unknown merchant or one of
another PSP → `404`; an `edgeAssessmentId` already linked to another transaction → `409`. Nothing
is persisted for a rejected request. The backend hashes a raw PAN at once and never persists it.
`customerAccountReference` must be a PSP-issued token, not a raw account number; it and
`customerEmail` are source evidence for CBK failed-transaction reporting; email is encrypted at
rest.

The batch endpoint takes a bare JSON array of the same objects and returns one result per item, in
order. Duplicate requests with the same `Idempotency-Key` (or body `clientReference`, unique per
PSP) return the existing transaction without new alerts or webhooks.

An admin-only maintenance trigger re-scores yesterday's legacy cloud transactions. It is not part
of a PSP integration:

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
| `RISK_ALERT` | New alert raised from an Edge decision record (or from the deprecated cloud ingest) |
| `CASE_UPDATE` | Compliance case created or updated; case decision recorded |
| `MERCHANT_STATUS_CHANGE` | PSP status transition (manual admin, dunning suspension, payment reactivation) |

> **Edge and webhooks:** the cloud sends webhooks, never the Edge. When your Edge's decision
> record reaches the cloud and raises an alert, the cloud sends `RISK_ALERT` with IDs and the
> decision only. It never carries the amount, card or customer data. Join it to your own records by
> `txnRef` (your `txn_id`) or `edgeAssessmentId`.

### Webhook Payload (RISK_ALERT from an Edge decision record)

```json
{
  "alertId": 445566,
  "edgeAssessmentId": "3f0c9a2e-7d41-4b8e-9a51-0c2f6b1d7e44",
  "txnRef": "auth-2026-000123",
  "decision": "HOLD",
  "pspId": 7,
  "merchantId": "4",
  "caseId": 1234,
  "timestamp": "2026-10-17T14:32:15"
}
```

`txnRef`, `merchantId` and `caseId` are omitted when absent.

### Webhook Payload (RISK_ALERT from the deprecated cloud ingest)

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

### Idempotency

- Each Edge evaluation gets its own `edgeAssessmentId`. The cloud stores each decision record once:
  a re-sent identical record counts as a duplicate, and one alert is raised per record.
- The deprecated cloud ingest uses `Idempotency-Key` or `clientReference` (§5.6).

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
| **422 Unprocessable** | Business rule violation; on the Edge, `TXN_REF_LOOKS_LIKE_PAN` / `TXN_REF_INVALID` | Check validation errors; never send a PAN as `txn_id` |
| **429 Too Many Requests** | Rate limit exceeded | Backoff and retry |
| **500 Internal Error** | Server error | Retry with backoff |

### Error Response Format

```json
{
  "error": "validation_failed",
  "message": "Amount must be greater than 0",
  "status": 422,
  "timestamp": "2026-06-29T14:30:00Z",
  "path": "/api/v1/cases",
  "details": {
    "field": "amount",
    "rejectedValue": -100
  }
}
```

---

## 12. Integration Examples

These examples call `/edge/evaluate` on your own Edge Node. Use your Edge host name and the CA that
issued its TLS certificate.

### cURL

```bash
# Evaluate a transaction on your Edge Node
curl -s -X POST https://edge.internal.mypsp.com:8443/edge/evaluate \
  --cacert edge-ca.pem \
  -H "Content-Type: application/json" \
  -d '{
    "txn_id": "auth-2026-000123",
    "pan_hash": "sha256:4a7d...",
    "amount_cents": 1250000,
    "currency": "KES",
    "merchant_id": "123",
    "country_code": "KE"
  }'
```

### Python

```python
import requests


class HokekaEdgeClient:
    """Calls /edge/evaluate on the PSP's own Edge Node."""

    def __init__(self, base_url, ca_bundle, client_cert=None):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.session.verify = ca_bundle
        if client_cert:                 # (cert.pem, key.pem) when the Edge requires mutual TLS
            self.session.cert = client_cert

    def evaluate(self, txn_id, pan_hash, amount_cents, currency, merchant_id, **features):
        body = {"txn_id": txn_id, "pan_hash": pan_hash, "amount_cents": amount_cents,
                "currency": currency, "merchant_id": merchant_id, **features}
        resp = self.session.post(f"{self.base_url}/edge/evaluate", json=body, timeout=2)
        resp.raise_for_status()
        return resp.json()


edge = HokekaEdgeClient("https://edge.internal.mypsp.com:8443", "edge-ca.pem")
result = edge.evaluate("auth-2026-000123", "sha256:4a7d...", 1250000, "KES", "123", country_code="KE")
print(result["action"], result["score"], result["edgeAssessmentId"])
```

### JavaScript / Node.js

```javascript
const https = require('https');
const fs = require('fs');
const axios = require('axios');

const edge = axios.create({
  baseURL: 'https://edge.internal.mypsp.com:8443',
  httpsAgent: new https.Agent({ ca: fs.readFileSync('edge-ca.pem') }),
  timeout: 2000,
});

async function evaluate(txn) {
  const { data } = await edge.post('/edge/evaluate', txn);
  return data;
}

const result = await evaluate({
  txn_id: 'auth-2026-000123', pan_hash: 'sha256:4a7d...',
  amount_cents: 1250000, currency: 'KES', merchant_id: '123',
});
console.log(`Decision: ${result.action}`); // treat HOLD as "do not approve on your own"
```

### Java / Spring Boot

```java
@Service
public class HokekaEdgeClient {
    private final RestClient rest;

    public HokekaEdgeClient(@Value("${hokeka.edge.base-url}") String baseUrl, RestClient.Builder builder) {
        // Configure the builder's request factory with an SSLContext that trusts your Edge's CA
        // (and holds your client certificate if the Edge requires mutual TLS).
        this.rest = builder.baseUrl(baseUrl).build();
    }

    public Map<?, ?> evaluate(String txnId, String panHash, long amountCents, String currency, String merchantId) {
        return rest.post().uri("/edge/evaluate")
                .body(Map.of("txn_id", txnId, "pan_hash", panHash, "amount_cents", amountCents,
                             "currency", currency, "merchant_id", merchantId))
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
- [ ] Console login (session cookie) working
- [ ] Edge Node installed and activated (outbound 443 only; no inbound ports)
- [ ] `/edge/evaluate` tested from your API nodes; no PAN sent as `txn_id`
- [ ] Alerts from Edge decision records visible in the Console
- [ ] Data retention reviewed under Settings → Data Retention (default 5 years)
- [ ] Decision handling implemented (ALLOW/HOLD/BLOCK/ALERT)
- [ ] Webhook endpoints configured (optional but recommended)
- [ ] Billing usage monitoring set up
- [ ] Production go-live approved

### Common Issues

| Symptom | Likely Cause | Solution |
|---|---|---|
| `401 Unauthorized` | Expired session or invalid API key | Log in again, or check the key |
| `422 TXN_REF_LOOKS_LIKE_PAN` from the Edge | `txn_id` looks like a card number | Send your own transaction reference |
| `HOLD` on every evaluation | Edge not activated, no rule bundle, or ledger down (fail-closed) | Check the Edge status and logs |
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

**Last Updated:** October 17, 2026  
**© Hokeka AML Platform** — *Modern AML & Fraud Detection for PSPs and Banks*
