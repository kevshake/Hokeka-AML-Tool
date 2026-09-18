# Edge + Cloud Dual-Post Integrator Contract

**Audience:** PSP engineers integrating pre-auth edge evaluation with cloud compliance artifacts (alerts, cases, SAR, webhooks).

This is the authoritative contract for how the **on-premises edge** and the **cloud control plane** work together. It supersedes any implied assumption that a single edge call creates cloud-side compliance records.

---

## Summary

| Path | Endpoint | Creates cloud txn? | Creates alerts/cases/SAR? | Sends webhooks? |
|------|----------|-------------------|---------------------------|-----------------|
| **Edge (pre-auth)** | `POST /edge/evaluate` | No | No | No |
| **Cloud (post-auth typical)** | `POST /api/v1/transactions/ingest` | Yes | Yes (via fraud pipeline) | Yes (via durable outbox) |

**Integrators that need cloud compliance artifacts must dual-post:** call `/edge/evaluate` for the pre-auth decision, then call `/api/v1/transactions/ingest` with the same transaction context (typically after authorisation) so the control plane can persist the transaction, run the full fraud/AML pipeline, raise alerts/cases, and deliver webhooks.

---

## 1. Edge `/edge/evaluate` — local-only pre-auth

- Served on the PSP's edge node over TLS; transaction payloads **do not leave the premises**.
- Evaluates caller-supplied features plus locally-derived velocity history (Aerospike).
- Returns `{ action, score, triggeredRuleIds, reasons }` synchronously.
- **Does not** call the cloud control plane, **does not** write to cloud Postgres, **does not** create alerts, cases, SAR filings, or webhook deliveries.
- Only aggregate metrics windows are shipped upward (see `docs/architecture/edge-channel-contract.md`).

When `featurestore.fail-closed=true` (default), an unavailable local feature store returns `HOLD` instead of evaluating without velocity history. See `docs/edge-transaction-evaluation.md` §5.

---

## 2. Cloud `/api/v1/transactions/ingest` — compliance artifacts

- Authenticated cloud API (`Bearer` token, PSP-scoped roles).
- Persists the transaction, enqueues Kafka outbox events, runs fraud detection (rules, ML, sanctions, limits).
- **Alerts** are created by `DecisionEngine` on HOLD/BLOCK/ALERT paths.
- **Cases** may be auto-created from rule outcomes when configured.
- **Webhooks** (`RISK_ALERT`, `CASE_UPDATE`, `MERCHANT_STATUS_CHANGE`) are enqueued in the **transactional outbox** (`event_outbox` channel `WEBHOOK`) atomically with the triggering write.
- Supports **idempotent ingest** via `Idempotency-Key` header or `clientReference` body field (unique per PSP).

---

## 3. Recommended integration sequence

```
1. Pre-auth (edge)
   POST https://edge-node.internal/edge/evaluate
   { pan_hash, txn_id, amount_cents, merchant_id, ... }
   → ALLOW | HOLD | BLOCK

2. Apply acquirer decision based on edge action (do not auto-approve HOLD/BLOCK)

3. Post-auth (cloud) — required for compliance coverage
   POST https://api.hokeka.com/api/v1/transactions/ingest
   Idempotency-Key: <stable client reference for this txn>
   Authorization: Bearer ...
   { merchantId, amountCents, currency, pan, clientReference, acquirerResponse, ... }
   → fraud detection result; alerts/cases/webhooks as applicable
```

Use the **same stable client reference** (`txn_id` from edge, or your authorisation reference) as `Idempotency-Key` / `clientReference` on ingest so retries do not duplicate cloud transactions.

---

## 4. What each path owns

| Concern | Edge | Cloud |
|---------|------|-------|
| Pre-auth latency | ✓ primary | optional |
| Velocity / local history | ✓ Aerospike | Postgres statistics |
| Rule bundle enforcement | ✓ pulled bundle | full rule engine + DB features |
| Sanctions / cross-PSP | — | ✓ |
| Alert / case / SAR | — | ✓ |
| PSP webhooks | — | ✓ durable outbox |
| Operator UI / reporting | aggregates only | ✓ |

---

## 5. Related documentation

- [`docs/edge-transaction-evaluation.md`](edge-transaction-evaluation.md) — edge request/response contract
- [`docs/PSP_API_GUIDE.md`](PSP_API_GUIDE.md) — cloud ingest, webhooks, auth
- [`docs/features/API_INTEGRATION.md`](features/API_INTEGRATION.md) — SDK-style integration notes
- [`docs/SYSTEM-COMMUNICATION-AND-TX-PATH-ANALYSIS.md`](SYSTEM-COMMUNICATION-AND-TX-PATH-ANALYSIS.md) — architecture analysis (Pass A–D)
