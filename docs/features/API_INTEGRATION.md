# API Integration Guide

## Overview

This guide explains how PSPs integrate with the Hokeka AML Platform. There are two parts:

1. **Your Edge Node** (on your premises) evaluates every transaction. Your payment system calls
   `POST /edge/evaluate` on it. Transaction data stays on your premises.
2. **The Hokeka cloud** (`https://api.hokeka.com/api/v1`) holds alerts, cases, SAR/STR workflow,
   screening, reports, billing and settings. It receives a decision record per evaluation from your
   Edge (IDs, action, score, rule IDs; no PAN, amount or features). It never stores transaction data.

The normative contract is [`EDGE_DATA_RESIDENCY_CONTRACT.md`](../EDGE_DATA_RESIDENCY_CONTRACT.md).
The `/edge/evaluate` behaviour is in [`edge-transaction-evaluation.md`](../edge-transaction-evaluation.md).
Cloud APIs are documented with Swagger/OpenAPI.

## Quick Start

```bash
# 1. Evaluate a transaction on your Edge Node (your network, port 8443)
curl -s -X POST https://edge.internal.mypsp.com:8443/edge/evaluate \
  --cacert edge-ca.pem \
  -H "Content-Type: application/json" \
  -d '{
    "txn_id": "auth-2026-000123",
    "pan_hash": "sha256:4a7d...",
    "amount_cents": 1250000,
    "currency": "KES",
    "country_code": "KE",
    "merchant_id": "123",
    "mcc": "5411",
    "channel": "ECOMMERCE"
  }'
# -> { "action": "ALLOW", "score": 0, "triggeredRuleIds": [], "edgeAssessmentId": "3f0c...", ... }

# 2. Log in to the cloud as a Console user (session cookie)
curl -s -c cookies.txt -X POST https://api.hokeka.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"psp_admin@mypsp.com","password":"s3cr3t"}'

# 3. List the alerts your Edge decisions raised
curl -s -b cookies.txt "https://api.hokeka.com/api/v1/alerts?page=0&size=25&status=open"
```

Rules for the evaluate body:

- Send `pan_hash`, never the PAN.
- `txn_id` is your own reference (1..128 printable ASCII, no spaces). A PAN-like value (13 to 19
  digits that pass the Luhn check) gets `422 {"error":"TXN_REF_LOOKS_LIKE_PAN"}`. Nothing is
  evaluated or stored.
- Amounts are integer minor units.

You do not call the cloud per transaction. Your Edge ships decision records to the cloud in the
background, and the cloud raises alerts and cases from them.

## Authentication

| Caller | Mechanism |
|---|---|
| Your API nodes to your Edge Node | TLS on port 8443. Optional mutual TLS (`--tls-client-auth need` at install). |
| Your Edge Node to the Hokeka cloud | mTLS client certificate issued at activation. Outbound port 443 only. |
| Your back-office jobs to the cloud | PSP API key: `X-Api-Key: hka_...` or `Authorization: Bearer hka_...` |
| People to the cloud | Session cookie (`JSESSIONID`) after `POST /api/v1/auth/login` |

There is no JWT. A `Bearer` header is accepted only when it carries a PSP API key. See
[`PSP_API_GUIDE.md`](../PSP_API_GUIDE.md) section 2.

### Role-Based Scoping

- **PSP_ADMIN / PSP_USER**: Queries automatically scoped to own PSP's data
- **ADMIN / SUPER_ADMIN**: Can access all PSPs, optional `pspId` filter
- Attempting cross-PSP access returns 403 FORBIDDEN
- Hokeka staff can read your Edge transaction data only when you turn on **Settings > Hokeka
  support access**. It is off by default. Each pull needs a reason and is audited. The cloud check
  is built; the pulls themselves are planned (R6).

## Base URLs

| Environment | URL |
|---|---|
| Local Dev | `http://localhost:2637/api/v1` |
| Sandbox | `https://testapi.hokeka.com/api/v1` |
| Production | `https://api.hokeka.com/api/v1` |

## Core API Endpoints

### Transaction Monitoring

| Where | Method | Endpoint | Description |
|---|---|---|---|
| Edge Node | POST | `/edge/evaluate` | Evaluate a transaction and record it on the Edge Ledger |
| Edge Node | GET | `/edge/status` | Node status |
| Cloud | GET | `/monitoring/dashboard/stats` | Dashboard statistics |
| Cloud | GET | `/monitoring/risk-distribution` | Risk distribution data |
| Cloud | GET | `/monitoring/risk-indicators` | Risk indicator metrics |
| Cloud | POST | `/transactions/ingest`, `/transactions/ingest/batch` | **Deprecated.** Legacy cloud ingest; stores the transaction in the cloud. Kept for existing integrations until R12 deletes it. Do not use it for new integrations. |

Transaction detail in the Console will come live from your Edge over the Edge Tunnel (planned: R5
Edge client, R6 cloud data proxy, R11 Console).

### Alerts

| Method | Endpoint | Description |
|---|---|---|
| GET | `/alerts` | List alerts (paginated) |
| GET | `/alerts/{id}` | Get alert detail |
| PUT | `/alerts/{id}/resolve` | Resolve with disposition |
| POST | `/alerts/bulk` | Bulk operations |

### Cases

| Method | Endpoint | Description |
|---|---|---|
| GET | `/cases` | List cases (paginated) |
| GET | `/cases/{id}/timeline` | Case timeline |
| GET | `/cases/{id}/graph` | Case network graph |
| POST | `/cases/{id}/decisions` | Make case decision |
| POST | `/cases/{id}/escalate` | Escalate case |

### Screening

| Method | Endpoint | Description |
|---|---|---|
| POST | `/sanctions/screen` | Screen a name against watchlists |
| GET | `/sanctions/watchlist` | List custom watchlists |
| POST | `/sanctions/watchlist` | Add to watchlist |
| DELETE | `/sanctions/watchlist/{id}` | Remove from watchlist |

### Reports

| Method | Endpoint | Description |
|---|---|---|
| POST | `/reports/generate` | Generate a report (async) |
| GET | `/reports/download/{id}` | Download generated report |
| POST | `/reports/schedule` | Schedule recurring report |
| POST | `/reports/chart` | Get chart data |

### Regulatory Reporting

| Method | Endpoint | Description |
|---|---|---|
| GET | `/reporting/regulatory/ctr` | Currency Transaction Report |
| GET | `/reporting/regulatory/lctr` | Large Cash Transaction Report |
| GET | `/reporting/regulatory/iftr` | International Funds Transfer Report |
| GET | `/reporting/regulatory/{type}/export` | Export as CSV/PDF |

### PSP Management

| Method | Endpoint | Description |
|---|---|---|
| GET | `/psps` | List all PSPs |
| POST | `/psps` | Register a PSP |
| GET | `/psps/{id}` | Get PSP details |
| PUT | `/psps/{id}` | Update PSP profile |
| PUT | `/psps/{id}/status` | Update PSP status |
| GET | `/psps/{id}/cbk-config` | View CBK configuration |
| PUT | `/psps/{id}/cbk-config` | Update CBK configuration |

### Billing

| Method | Endpoint | Description |
|---|---|---|
| GET | `/billing/usage/{pspId}/current` | Current month usage |
| GET | `/billing/invoices/{id}` | Invoice details |
| GET | `/billing/invoices/{id}/pdf` | Invoice PDF download |
| POST | `/billing/payments/mpesa` | Initiate M-Pesa payment |

### CBK Compliance

| Method | Endpoint | Description |
|---|---|---|
| GET | `/compliance/cbk/submissions` | Submission history |
| POST | `/compliance/cbk/submissions/{endpoint}/run` | Manual retrigger |

## SDK Examples

These call your Edge Node. Use your own host name and CA bundle.

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
        return resp.json()          # action, score, triggeredRuleIds, reasons, edgeAssessmentId, ...
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
  return data; // treat HOLD as "do not approve on your own"
}
```

## Webhooks

Subscribe to real-time events (one subscription per event type; secret is server-generated):

```http
POST /api/v1/webhooks/subscribe
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "callbackUrl": "https://mypsp.com/webhooks/hokeka",
  "eventType": "RISK_ALERT"
}
```

Repeat for `CASE_UPDATE` and `MERCHANT_STATUS_CHANGE` as needed. Deliveries are HMAC-signed
(`X-Hokeka-Signature`, `X-Hokeka-Timestamp`) and durable (transactional outbox with retries).

### Events (implemented)

| Event | When |
|---|---|
| `RISK_ALERT` | Alert raised from an Edge decision record (or from the deprecated cloud ingest) |
| `CASE_UPDATE` | Case created, updated, or decision recorded |
| `MERCHANT_STATUS_CHANGE` | PSP status transition |

Webhooks raised from Edge decision records carry IDs and the decision only: `alertId`,
`edgeAssessmentId`, `txnRef`, `decision`, `pspId`, `merchantId`, `caseId`, `timestamp`. They never
carry the amount, card or customer data. See [`PSP_API_GUIDE.md`](../PSP_API_GUIDE.md) section 10
for payloads and [`EDGE_DATA_RESIDENCY_CONTRACT.md`](../EDGE_DATA_RESIDENCY_CONTRACT.md) for the
residency rules.

## Idempotency

- Edge: each evaluation gets its own `edgeAssessmentId`. The Edge Ledger keeps `txn_id` unique per
  Edge Site. The cloud stores each decision record once (idempotent on `edgeAssessmentId`).
- Deprecated cloud ingest: pass `Idempotency-Key: <reference>` or `"clientReference"`. Duplicate
  keys return the existing transaction without duplicating alerts or webhooks.

## Rate Limits

| Tier | RPM | Burst |
|---|---|---|
| Standard | 1,000 | 50 |
| Premium | 5,000 | 200 |
| Enterprise | Custom | Custom |

Headers returned on every response:
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 987
X-RateLimit-Reset: 1623456789
```

## Error Handling

| Code | Meaning |
|---|---|
| 400 | Invalid payload |
| 401 | Missing/expired session, or invalid/revoked/expired API key |
| 403 | Insufficient permissions |
| 404 | Resource not found |
| 409 | Duplicate resource |
| 422 | Business rule violation; on the Edge, `TXN_REF_LOOKS_LIKE_PAN` or `TXN_REF_INVALID` |
| 429 | Rate limit exceeded |
| 500 | Server error |
