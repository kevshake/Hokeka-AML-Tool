# Hokeka AML Platform — PSP API Integration Guide

> **Version:** 2.0 — June 2026  
> **For:** Payment Service Providers, Banks, and Fintech Institutions  
> **Base URL:** `https://api.hokeka.com/api/v1` (Production)  
> **Swagger UI:** `https://api.hokeka.com/api/v1/swagger-ui.html`

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

### JWT Token Flow

All API requests (except login and public registration) require a
**JWT Bearer token**.

```
┌──────────┐                    ┌──────────┐
│   PSP    │   POST /auth/login │  Hokeka  │
│  System  │───────────────────▶│   AML    │
│          │                    │ Platform │
│          │◀───────────────────│          │
│          │   { token, expiresIn }        │
│          │                    │          │
│          │   GET /alerts      │          │
│          │   Authorization:   │          │
│          │   Bearer eyJ...    │          │
│          │───────────────────▶│          │
└──────────┘                    └──────────┘
```

#### Login

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "psp_admin@mypayment.com",
  "password": "your-secure-password"
}
```

**Response**

```json
{
  "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400,
  "tokenType": "Bearer",
  "userId": 42,
  "pspId": 7,
  "role": "PSP_ADMIN"
}
```

#### Using the Token

```http
GET /api/v1/alerts
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

> **Important:** Tokens expire after 24 hours (configurable). Use the
> refresh endpoint or re-login before expiry. Implement automatic
> token refresh in your integration.

#### Token Refreshing

```http
POST /api/v1/auth/session/refresh
Authorization: Bearer <current-token>
```

### PSP Scope Isolation

The system automatically determines your PSP scope from the JWT token:

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
| **Auth** | `/auth` | Login, token refresh, password reset | ✅ All roles |
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
| **Compliance** | `/compliance` | SAR, KYC, CBK reporting | ✅ Compliance roles |
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
Authorization: Bearer eyJ...
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
Authorization: Bearer eyJ...
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

`merchantId` is the numeric platform merchant ID represented as a string.
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
Authorization: Bearer eyJ...
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
Authorization: Bearer eyJ...    # SUPER_ADMIN or ADMIN role only
```

---

## 6. Sanctions Screening & AML Checks

### Name Screening

Screen individuals or entities against OFAC, UN, EU, and custom
watchlists in real-time:

```http
POST /api/v1/sanctions/screen
Authorization: Bearer eyJ...
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
Authorization: Bearer eyJ...
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
Authorization: Bearer eyJ...
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
Authorization: Bearer eyJ...
```

### Resolve an Alert

```http
PUT /api/v1/alerts/{id}/resolve
Authorization: Bearer eyJ...
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
Authorization: Bearer eyJ...
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
GET /api/v1/reports/download/{executionId}?format=pdf
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

Subscribe to real-time events instead of polling:

```http
POST /api/v1/webhooks/subscribe
Authorization: Bearer eyJ...
Content-Type: application/json

{
  "url": "https://mypsp.com/webhooks/hokeka",
  "events": ["alert.created", "case.updated", "transaction.flagged"],
  "secret": "my-webhook-secret"
}
```

### Available Events

| Event | Triggered When |
|---|---|
| `transaction.flagged` | Transaction receives HOLD or BLOCK decision |
| `alert.created` | New fraud/AML alert generated |
| `alert.resolved` | Alert disposition recorded |
| `case.created` | Compliance case opened |
| `case.updated` | Case status changed, decision made |
| `invoice.generated` | Monthly invoice created |
| `invoice.overdue` | Invoice becomes overdue |
| `invoice.paid` | Payment received |

### Webhook Payload

```json
{
  "event": "alert.created",
  "timestamp": "2026-06-29T14:32:15Z",
  "pspId": 7,
  "data": {
    "alertId": 445566,
    "txnId": 1048576,
    "score": 87.3,
    "riskLevel": "HIGH",
    "action": "HOLD",
    "reason": "Velocity: 15 transactions in 1 hour",
    "triggeredRules": ["R-2", "R-7", "R-30"]
  }
}
```

---

## 11. Error Handling & Rate Limits

### HTTP Status Codes

| Code | Meaning | Action |
|---|---|---|
| **200 OK** | Success | Process response |
| **201 Created** | Resource created | Extract ID from response |
| **202 Accepted** | Async request accepted | Poll status endpoint |
| **400 Bad Request** | Invalid payload | Check request format |
| **401 Unauthorized** | Missing/invalid token | Re-authenticate |
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
# 1. Authenticate
TOKEN=$(curl -s -X POST https://api.hokeka.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"psp_admin@mypsp.com","password":"s3cr3t"}' | jq -r '.token')

# 2. Ingest a transaction
curl -s -X POST https://api.hokeka.com/api/v1/transactions/ingest \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 12500.00,
    "currency": "KES",
    "merchantId": "MRC-00123",
    "ipAddress": "41.204.187.12",
    "mcc": "5411"
  }'

# 3. Check your usage
curl -s https://api.hokeka.com/api/v1/billing/usage/7/current \
  -H "Authorization: Bearer $TOKEN"
```

### Python

```python
import requests
import time

class HokekaAMLClient:
    def __init__(self, base_url, username, password):
        self.base_url = base_url
        self.token = None
        self._login(username, password)

    def _login(self, username, password):
        resp = requests.post(f"{self.base_url}/auth/login", json={
            "username": username,
            "password": password
        })
        resp.raise_for_status()
        data = resp.json()
        self.token = data["token"]
        # Auto-refresh before expiry
        self._expires_at = time.time() + data["expiresIn"] - 300

    def _ensure_token(self):
        if time.time() >= self._expires_at:
            resp = requests.post(f"{self.base_url}/auth/session/refresh",
                headers={"Authorization": f"Bearer {self.token}"})
            resp.raise_for_status()
            self.token = resp.json()["token"]

    def _headers(self):
        self._ensure_token()
        return {"Authorization": f"Bearer {self.token}"}

    def screen_transaction(self, amount, currency, merchant_id, **kwargs):
        payload = {
            "amount": amount,
            "currency": currency,
            "merchantId": merchant_id,
            **kwargs
        }
        resp = requests.post(
            f"{self.base_url}/transactions/ingest",
            headers=self._headers(),
            json=payload
        )
        resp.raise_for_status()
        return resp.json()

    def get_usage(self, psp_id):
        resp = requests.get(
            f"{self.base_url}/billing/usage/{psp_id}/current",
            headers=self._headers()
        )
        resp.raise_for_status()
        return resp.json()


# Usage
client = HokekaAMLClient(
    base_url="https://api.hokeka.com/api/v1",
    username="psp_admin@mypsp.com",
    password="s3cr3t"
)

# Screen a transaction
result = client.screen_transaction(
    amount=12500.00,
    currency="KES",
    merchant_id="MRC-00123",
    ip_address="41.204.187.12"
)
print(f"Decision: {result['decision']}, Score: {result['riskScore']}")

# Check billing
usage = client.get_usage(psp_id=7)
print(f"Monthly cost: ${usage['totalCost']}")
```

### JavaScript / Node.js

```javascript
const axios = require('axios');

class HokekaAMLClient {
  constructor(baseURL, username, password) {
    this.client = axios.create({ baseURL });
    this.username = username;
    this.password = password;
    this.token = null;
    this.expiresAt = 0;
  }

  async ensureToken() {
    if (Date.now() >= this.expiresAt) {
      const resp = await axios.post(`${this.client.defaults.baseURL}/auth/login`, {
        username: this.username,
        password: this.password
      });
      this.token = resp.data.token;
      this.expiresAt = Date.now() + (resp.data.expiresIn - 300) * 1000;
      this.client.defaults.headers.common['Authorization'] = `Bearer ${this.token}`;
    }
  }

  async screenTransaction(txn) {
    await this.ensureToken();
    const { data } = await this.client.post('/transactions/ingest', txn);
    return data;
  }

  async getUsage(pspId) {
    await this.ensureToken();
    const { data } = await this.client.get(`/billing/usage/${pspId}/current`);
    return data;
  }
}

// Usage
const client = new HokekaAMLClient(
  'https://api.hokeka.com/api/v1',
  'psp_admin@mypsp.com',
  's3cr3t'
);

const result = await client.screenTransaction({
  amount: 12500.00,
  currency: 'KES',
  merchantId: 'MRC-00123'
});
console.log(`Decision: ${result.decision}`);
```

### Java / Spring Boot

```java
@Service
public class HokekaAmlClient {
    private final RestTemplate rest;
    private String token;
    private Instant expiresAt;

    public HokekaAmlClient(@Value("${hokeka.api.base-url}") String baseUrl) {
        this.rest = new RestTemplate();
        rest.setUriTemplateHandler(new DefaultUriBuilderFactory(baseUrl));
    }

    @PostConstruct
    public void authenticate() {
        var resp = rest.postForEntity("/auth/login", Map.of(
            "username", "psp_admin@mypsp.com",
            "password", "s3cr3t"
        ), Map.class);
        token = (String) resp.getBody().get("token");
        expiresAt = Instant.now().plusSeconds((int) resp.getBody().get("expiresIn") - 300);
    }

    public Map screenTransaction(BigDecimal amount, String currency, String merchantId) {
        if (Instant.now().isAfter(expiresAt)) authenticate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var entity = new HttpEntity<>(Map.of(
            "amount", amount, "currency", currency, "merchantId", merchantId
        ), headers);
        return rest.exchange("/transactions/ingest", HttpMethod.POST, entity, Map.class).getBody();
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
