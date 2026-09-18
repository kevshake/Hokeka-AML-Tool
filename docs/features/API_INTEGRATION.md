# API Integration Guide

## Overview

This guide explains how PSPs integrate with the Hokeka AML Platform. All functionality is accessible via REST APIs documented with Swagger/OpenAPI.

## Quick Start

```bash
# 1. Authenticate (get JWT token)
TOKEN=$(curl -s -X POST https://api.hokeka.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"psp_admin@mypsp.com","password":"s3cr3t"}' | jq -r '.token')

# 2. Screen a transaction
curl -s -X POST https://api.hokeka.com/api/v1/transactions/ingest \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "amountCents": 1250000,
    "currency": "KES",
    "merchantId": "123",
    "pan": "4111111111111111",
    "ipAddress": "41.204.187.12",
    "countryCode": "KE",
    "direction": "OUTBOUND",
    "channelType": "ECOMMERCE",
    "customerAccountReference": "PSP-TOKEN-CUST-78901",
    "customerEmail": "customer@example.com"
  }'

# 3. Check billing usage
curl -s https://api.hokeka.com/api/v1/billing/usage/7/current \
  -H "Authorization: Bearer $TOKEN"
```

Amounts are integer minor units. The merchant ID is the platform merchant
primary key represented as a string. Raw PAN is hashed immediately and is never
persisted. Customer account references must be PSP-issued opaque tokens; the
customer email is encrypted at rest.

## Authentication

### JWT Token Flow

```
POST /api/v1/auth/login → { token, expiresIn, tokenType, userId, pspId, role }
```

- Token expiry: 24 hours (configurable)
- Auto-refresh: `POST /api/v1/auth/refresh` with current token
- Include in all requests: `Authorization: Bearer eyJ...`

### Role-Based Scoping

- **PSP_ADMIN / PSP_USER**: Queries automatically scoped to own PSP's data
- **ADMIN / SUPER_ADMIN**: Can access all PSPs, optional `pspId` filter
- Attempting cross-PSP access returns 403 FORBIDDEN

## Base URLs

| Environment | URL |
|---|---|
| Local Dev | `http://localhost:2637/api/v1` |
| Sandbox | `https://testapi.hokeka.com/api/v1` |
| Production | `https://api.hokeka.com/api/v1` |

## Core API Endpoints

### Transaction Monitoring

| Method | Endpoint | Description |
|---|---|---|
| POST | `/transactions/ingest` | Ingest and score a transaction |
| POST | `/transactions/batch-score` | Batch score multiple transactions |
| GET | `/monitoring/dashboard/stats` | Dashboard statistics |
| GET | `/monitoring/risk-distribution` | Risk distribution data |
| GET | `/monitoring/risk-indicators` | Risk indicator metrics |

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

### Python

```python
import requests
class HokekaAMLClient:
    def __init__(self, base_url, username, password):
        self.base_url = base_url
        self.token = None
        self._login(username, password)

    def _login(self, username, password):
        resp = requests.post(f"{self.base_url}/auth/login", json={
            "username": username, "password": password
        })
        resp.raise_for_status()
        data = resp.json()
        self.token = data["token"]
        self.expires_at = time.time() + data["expiresIn"] - 300

    def _ensure_token(self):
        if time.time() >= self.expires_at:
            resp = requests.post(f"{self.base_url}/auth/refresh",
                headers={"Authorization": f"Bearer {self.token}"})
            resp.raise_for_status()
            self.token = resp.json()["token"]

    def screen_transaction(self, amount, currency, merchant_id, **kwargs):
        resp = requests.post(
            f"{self.base_url}/transactions/ingest",
            headers={"Authorization": f"Bearer {self.token}"},
            json={"amount": amount, "currency": currency, "merchantId": merchant_id, **kwargs}
        )
        resp.raise_for_status()
        return resp.json()
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
  }
  async ensureToken() {
    if (!this.token) {
      const resp = await axios.post(`${this.client.defaults.baseURL}/auth/login`, {
        username: this.username, password: this.password
      });
      this.token = resp.data.token;
      this.client.defaults.headers.common['Authorization'] = `Bearer ${this.token}`;
    }
  }
  async screenTransaction(txn) {
    await this.ensureToken();
    const { data } = await this.client.post('/transactions/ingest', txn);
    return data;
  }
}
```

## Webhooks

Subscribe to real-time events:

```http
POST /api/v1/webhooks/subscribe
{
  "url": "https://mypsp.com/webhooks/hokeka",
  "events": ["alert.created", "case.updated", "transaction.flagged"],
  "secret": "my-webhook-secret"
}
```

### Events

- `transaction.flagged` — HOLD/BLOCK decision
- `alert.created` — New alert
- `alert.resolved` — Alert resolved
- `case.created` — Case opened
- `case.updated` — Status change
- `invoice.generated` — Invoice created
- `invoice.overdue` — Invoice overdue
- `invoice.paid` — Payment received

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
| 401 | Missing/invalid token |
| 403 | Insufficient permissions |
| 404 | Resource not found |
| 409 | Duplicate resource |
| 422 | Business rule violation |
| 429 | Rate limit exceeded |
| 500 | Server error |
