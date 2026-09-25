# Process 06 — PSP API Dual-Post Integration

**Audience:** PSP integration / payment platform engineers.

**This is not a server install.** It is the required **integration process** for wiring PSP **API nodes** after [01 — Client Edge Node](01-client-edge-node.md) is live.

**Normative contract:** [`docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`](../EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md).

---

## Why dual-post exists

| Path | Endpoint | Cloud txn persisted? | Alerts / cases / SAR? | Webhooks? |
|------|----------|------------------------|----------------------|-----------|
| **Edge (pre-auth)** | `POST /edge/evaluate` | No | No | No |
| **Edge JEV advisory** | `POST /api/v1/edge/decision` (mTLS, optional) | Audit row only | Async when inline off | No |
| **Cloud (post-auth)** | `POST /api/v1/transactions/ingest` | Yes | Yes | Yes (durable outbox) |

Edge alone gives **fast local decisions** with data staying on-prem. Cloud ingest runs the **full compliance pipeline** operators see in Console.

**JEV AI:** Edge Nodes never call OpenRouter. Borderline edge decisions may request a JEV advisory through the Control Plane (`POST /edge/decision`). With `aiInlineMode` off (default), pre-auth stays rules-only on the edge; JEV runs asynchronously on the Control Plane. With `aiInlineMode` on, the edge waits up to `aiInlineBudgetMs` then falls back to rules.

> Installing Edge without dual-post means Console will show **no alerts or cases** for those transactions.

---

## Prerequisites checklist

- [ ] Edge Node installed, approved, `evaluator: native`, `featureStore` healthy ([01](01-client-edge-node.md))
- [ ] PSP tenant active in Control Plane; API users or service accounts exist
- [ ] JWT credentials or integration auth configured ([`docs/PSP_API_GUIDE.md`](../PSP_API_GUIDE.md) §2)
- [ ] Network: API nodes → Edge **8443** (internal); API nodes → `https://api.hokeka.com` **443**
- [ ] Stable idempotency strategy chosen (`Idempotency-Key` / `clientReference`)

---

## Integration sequence

```
┌─────────────┐     pre-auth      ┌──────────────┐
│  PSP API    │ ────────────────► │  Edge Node   │
│   node      │  /edge/evaluate │  (on-prem)   │
└─────────────┘                 └──────────────┘
       │
       │  apply acquirer decision (do not auto-approve HOLD/BLOCK)
       │
       ▼ post-auth
┌─────────────┐
│  Cloud API  │  POST /api/v1/transactions/ingest
│ (Hokeka)    │  → fraud pipeline → alerts/cases/webhooks
└─────────────┘
```

### Step 1 — Pre-auth edge evaluation

```http
POST https://<edge-host>:8443/edge/evaluate
Content-Type: application/json

{
  "txn_id": "auth-ref-12345",
  "pan_hash": "<sha256-of-pan>",
  "amount_cents": 150000,
  "currency": "KES",
  "country_code": "KE",
  "mcc": "5411",
  "merchant_id": "m-42"
}
```

**Response (example):**

```json
{
  "action": "ALLOW",
  "score": 12,
  "triggeredRuleIds": [],
  "reasons": []
}
```

- Field names are **`snake_case`** — mismatched names are silently ignored by rules.
- When feature store is unavailable and `featurestore.fail-closed=true`, edge returns **HOLD**.
- Unapproved edge nodes return **HOLD** for all traffic.

Full request/response: [`docs/edge-transaction-evaluation.md`](../edge-transaction-evaluation.md).

**TLS:** Use your CA-trusted edge cert; production should use `--tls-client-auth need` so only your API nodes call evaluate.

### Step 2 — Apply payment decision

Map edge `action` to your authorisation flow:

| Edge action | Typical handling |
|-------------|------------------|
| `ALLOW` | Proceed with authorisation |
| `ALERT` | Policy-dependent — often allow with flag or step-up |
| `HOLD` | Do not auto-approve — manual review or decline |
| `BLOCK` | Decline |

Edge decision is **pre-auth**; acquirer outcome may differ — pass actual outcome to ingest.

### Step 3 — Post-auth cloud ingest (required for compliance artifacts)

```http
POST https://api.hokeka.com/api/v1/transactions/ingest
Authorization: Bearer <jwt-token>
Idempotency-Key: auth-ref-12345
Content-Type: application/json

{
  "merchantId": "m-42",
  "amountCents": 150000,
  "currency": "KES",
  "pan": "<tokenized-or-per-policy>",
  "clientReference": "auth-ref-12345",
  "acquirerResponse": "APPROVED",
  "countryCode": "KE",
  "mcc": "5411"
}
```

Use the **same stable reference** as edge `txn_id` for idempotency across retries.

Cloud path:

- Persists transaction
- Runs fraud detection (rules, ML, sanctions, limits)
- Creates **alerts** on HOLD/BLOCK/ALERT paths
- May auto-create **cases** when configured
- Enqueues **webhooks** via transactional outbox

API details: [`docs/PSP_API_GUIDE.md`](../PSP_API_GUIDE.md) §5, §7, §10.

### Step 4 — Webhook enrollment (if applicable)

If the PSP consumes async events:

1. Register webhook URL in Console or via API (`PSP_API_GUIDE` §10).
2. Verify signature / delivery retry semantics.
3. Expect events such as `RISK_ALERT`, `CASE_UPDATE`, `MERCHANT_STATUS_CHANGE` **only from cloud ingest**, not from edge.

---

## Idempotency and retries

| Concern | Guidance |
|---------|----------|
| Edge retry | Safe to retry evaluate with same `txn_id` if idempotent on your side |
| Cloud retry | Always send `Idempotency-Key` or stable `clientReference` |
| Partial failure | Edge ALLOW + failed ingest = **no cloud compliance record** — monitor ingest errors |
| Ordering | Always evaluate before ingest; ingest after final acquirer outcome when possible |

---

## Verification checklist

- [ ] Test txn: edge returns expected `action`
- [ ] Same txn appears in Console transaction views after ingest
- [ ] HOLD/BLOCK test txn creates alert in Console
- [ ] Webhook test endpoint receives event (if configured)
- [ ] Idempotency: duplicate ingest does not duplicate txn
- [ ] Latency budget: edge p99 within authorisation SLA

---

## Enrollment from Console (operator side)

Hokeka operators / PSP admins:

1. **Console → Edge Nodes** — create node, copy install command for infra team ([01](01-client-edge-node.md)).
2. Approve node after enrollment; distribute pinned keys + mTLS client cert to edge ops.
3. **Console → API / Users** — ensure integration service account exists.
4. Confirm PSP `pspId` matches edge `--pspid`.

Console install: [02 — Console](02-console-dashboard.md) — not on PSP API servers.

---

## Common mistakes

| Mistake | Consequence |
|---------|-------------|
| Edge only, no ingest | No cloud alerts, cases, SAR, webhooks |
| camelCase field names on edge | Rules never see features |
| Treating HOLD as ALLOW | Regulatory / fraud exposure |
| Ingest before acquirer finality | Wrong `acquirerResponse`, case quality issues |
| Same idempotency key across different txns | Dropped or merged cloud records |

---

## Related

- [Installation process map](README.md)
- [01 — Client Edge Node](01-client-edge-node.md)
- [`docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`](../EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md)
- [`docs/edge-transaction-evaluation.md`](../edge-transaction-evaluation.md)
- [`docs/PSP_API_GUIDE.md`](../PSP_API_GUIDE.md)
- [`docs/features/API_INTEGRATION.md`](../features/API_INTEGRATION.md)
