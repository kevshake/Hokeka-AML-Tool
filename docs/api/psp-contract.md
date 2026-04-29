# PSP-Facing External Contract Specification

**Owner:** Solutions Architect (HOK-135)
**Parent:** HOK-130
**Consumer:** Backend Developer (9fdbb219), PSP integration partners
**Status:** Draft v1 — pending Backend Developer sign-off

This document is the authoritative external contract for what Payment Service Providers (PSPs) call into the Hokeka AML backend. It is **not** a frontend-driven API; it is the inbound surface that PSP merchants and gateways integrate against.

Source of truth:
- `BACKEND/src/main/java/com/posgateway/aml/controller/TransactionController.java`
- `BACKEND/src/main/java/com/posgateway/aml/dto/TransactionRequestDTO.java`
- `BACKEND/src/main/java/com/posgateway/aml/dto/FraudDetectionResponseDTO.java`
- `BACKEND/src/main/java/com/posgateway/aml/service/psp/WebhookService.java`
- `BACKEND/src/main/java/com/posgateway/aml/controller/psp/PspController.java`

All paths below are prefixed with the servlet context-path `/api/v1` (see `application.properties: server.servlet.context-path`).

---

## 1. Endpoints

### 1.1 Transaction ingestion (single)

`POST /api/v1/transactions/ingest`

PSP submits a single POS / card-present / card-not-present transaction for fraud + AML evaluation. Response carries the fraud-detection decision the PSP must enforce at its gateway.

- **Auth:** PSP API credentials (see §2). Inbound traffic from PSPs MUST present credentials; current session-cookie auth on the same controller is for internal admin use and is NOT the PSP path — see §2 for the production contract.
- **Idempotency:** Required header `Idempotency-Key` (see §3). MUST be retained by PSP for retries.
- **Content-Type:** `application/json`
- **Response codes:**
  - `200 OK` — synchronous decision returned in body
  - `202 Accepted` — accepted into buffer, decision will be delivered via webhook `transaction.scored`
  - `400 Bad Request` — schema validation failed (see §4)
  - `401 Unauthorized` — missing/invalid credentials
  - `403 Forbidden` — credentials valid but PSP not authorized for this merchant
  - `409 Conflict` — `Idempotency-Key` reused with a different request body
  - `429 Too Many Requests` — per-PSP rate limit exceeded (see `RequestRateLimiter`)
  - `503 Service Unavailable` — concurrent-request ceiling reached and buffer full

### 1.2 Transaction ingestion (batch)

`POST /api/v1/transactions/ingest/batch`

Body: JSON array of TransactionRequest objects, max 500 per call.

- Same auth and idempotency rules as §1.1, with one `Idempotency-Key` per **batch** plus a per-element `clientTxnRef` (see §4) so partial retries are deduplicated at the element level.
- Response is an ordered array of `FraudDetectionResponse` matching the request order; failed elements carry `action: "ERROR"` with reasons.

### 1.3 Health probe

`GET /api/v1/transactions/health`

Anonymous, returns `200 OK` with body `Transaction Service is running`. PSPs MAY use this for connectivity checks; it is not part of the SLA-bearing surface.

---

## 2. Authentication & signature

The current code path (`TransactionController.ingestTransaction`) does not yet enforce HMAC verification — `WebhookService.java:46` carries the explicit TODO `"In real app: Add HMAC signature header using sub.getSecretKey()"`. The contract below is what Backend Developer MUST implement to close the gap before production traffic.

### 2.1 Inbound (PSP → Hokeka)

Each request MUST carry these headers:

| Header | Required | Description |
|---|---|---|
| `X-PSP-Id` | yes | Numeric PSP id assigned at onboarding (matches `psp.id`). |
| `X-PSP-Key-Id` | yes | Identifier of the active signing key for this PSP (so we can rotate without downtime). |
| `X-Hokeka-Timestamp` | yes | Unix epoch seconds. Reject if drift > 300 s. |
| `X-Hokeka-Signature` | yes | `sha256=<hex>` of HMAC-SHA-256 over the canonical string (see §2.3) using the PSP's shared secret. |
| `Idempotency-Key` | yes | See §3. |
| `X-Request-Id` | optional | Free-form correlation id; echoed in response and logs. |

Authentication failure modes:
- Missing/malformed signature → `401`, body `{"error":"signature_invalid"}`
- Stale timestamp → `401`, body `{"error":"timestamp_skew"}`
- Unknown `X-PSP-Key-Id` → `401`, body `{"error":"unknown_key"}`
- Valid signature but PSP not authorized for the `merchantId` in body → `403`, body `{"error":"merchant_not_authorized"}`

### 2.2 Outbound webhooks (Hokeka → PSP)

When a buffered/async transaction completes, Hokeka POSTs a webhook to the PSP's registered callback (see `WebhookSubscription`). Webhook headers:

| Header | Description |
|---|---|
| `X-Hokeka-Event` | Event type, e.g. `transaction.scored`, `alert.created` |
| `X-Hokeka-Delivery-Id` | UUID, unique per delivery attempt; PSP should de-dup on this |
| `X-Hokeka-Timestamp` | Unix epoch seconds |
| `X-Hokeka-Signature` | `sha256=<hex>` HMAC-SHA-256 over `timestamp + "." + body` using the per-subscription secret returned at registration |

Retry policy: exponential backoff (1m, 5m, 25m, 2h, 12h), max 5 attempts; subscription is auto-disabled after `failureCount > 5` (already enforced in `WebhookService.java:63-65`).

### 2.3 Canonical signing string

```
<HTTP-METHOD>\n
<request-path-with-query>\n
<X-Hokeka-Timestamp>\n
<sha256-hex of raw request body>
```

HMAC-SHA-256 the canonical string with the PSP's secret. Verification MUST be constant-time.

---

## 3. Idempotency contract

`Idempotency-Key` is a PSP-generated string, max 128 chars, that uniquely identifies the request **as the PSP sees it** (typically the PSP's own transaction id).

Hokeka MUST:
- Persist `(pspId, idempotencyKey)` on first successful enqueue with a 24-hour TTL.
- On re-receipt within TTL with **same** request body → return the original response unchanged (status, body, txnId).
- On re-receipt within TTL with a **different** body → return `409 Conflict` with body `{"error":"idempotency_conflict","originalTxnId":<id>}`.
- After TTL expiry → treat as a new request.

PSPs MUST regenerate the key only when the underlying transaction is genuinely different. Retrying a network failure MUST reuse the same key.

---

## 4. Schemas

### 4.1 `TransactionRequest` (request body)

Mirrors `TransactionRequestDTO`:

| Field | Type | Required | Notes |
|---|---|---|---|
| `merchantId` | string | yes | Must be a merchant the authenticated PSP is authorized for. |
| `terminalId` | string | no | POS terminal identifier. |
| `amountCents` | integer (long) | yes | Positive minor units. |
| `currency` | string | yes | ISO 4217 (e.g. `KES`, `USD`). |
| `pan` | string | no | PAN. PSPs SHOULD send via the VGS proxy; never send unmasked PAN. |
| `isoMsg` | string | no | Raw ISO 8583 message (base64 or hex). |
| `emvTags` | object<string,any> | no | EMV tag → value map. |
| `acquirerResponse` | string | no | Acquirer response code. |
| `direction` | enum | no | `INBOUND` or `OUTBOUND`. |
| `clientTxnRef` | string | yes (batch only) | PSP-side per-element id used for batch dedup. |

### 4.2 `FraudDetectionResponse` (response body)

| Field | Type | Notes |
|---|---|---|
| `txnId` | string | Hokeka-assigned id; PSP MUST persist for reconciliation. |
| `action` | enum | `ALLOW` \| `HOLD` \| `ALERT` \| `BLOCK` \| `ERROR` |
| `riskScore` | number | 0–100. |
| `reasons` | array<string> | Human-readable rule trigger labels. |
| `decisionAt` | ISO-8601 datetime | Server time. |

PSP enforcement contract: PSP MUST NOT release `BLOCK` transactions to its acquirer; `HOLD` transactions MUST be queued pending Hokeka analyst clearance; `ALERT` transactions are released but flagged for monitoring.

### 4.3 Webhook event payloads

`transaction.scored`:
```json
{
  "event": "transaction.scored",
  "deliveryId": "<uuid>",
  "occurredAt": "<iso-8601>",
  "data": { /* FraudDetectionResponse */ }
}
```

`alert.created`:
```json
{
  "event": "alert.created",
  "deliveryId": "<uuid>",
  "occurredAt": "<iso-8601>",
  "data": {
    "alertId": "<long>",
    "txnId": "<string>",
    "severity": "LOW|MEDIUM|HIGH|CRITICAL",
    "ruleId": "<string>"
  }
}
```

---

## 5. Rate limiting & capacity

- Per-PSP token bucket via `RequestRateLimiter` (`429` when exhausted).
- Soft global concurrency cap: `app.transaction.maxContextRequests` (default 1000). Excess requests are buffered (`202`) when `app.transaction.ultraThroughput=true`, else `503`.
- PSPs SHOULD respect `Retry-After` header on `429`/`503`.

---

## 6. Open questions (for board / Backend Developer)

These items are not derivable from the current codebase. Each MUST be resolved before the contract is final:

1. **Signing key distribution.** How are PSP secrets provisioned and rotated? PSP admin console, ops handover, or KMS? Backend Developer to confirm.
2. **`X-PSP-Id` vs `merchantId` mapping.** PSPs onboard merchants — do we expect the PSP to send `X-PSP-Id` AND a merchantId, with server-side check that the merchant belongs to the PSP? (Assumed yes, see §2.1.)
3. **Idempotency TTL.** 24h is industry default; CTO to confirm acceptable for our DR/replay window.
4. **Batch size cap.** Currently no enforced cap in `BatchTransactionIngestionService`. Proposed 500/request — Backend Developer to set the value.
5. **PII transit.** Should `pan` ever cross our edge unmasked, or is VGS proxy mandatory for all PSPs? Currently controlled by `vgs.proxy.enabled` per webhook — PSP-inbound policy is undefined.
6. **HMAC clock skew tolerance.** Proposed 300 s; needs CTO sign-off given analyst SLAs.

Each unresolved item to be filed as a board question on HOK-135 if not closed by Backend Developer review.

---

## 7. Sign-off

- [ ] Backend Developer (9fdbb219) — contract is implementable
- [ ] CTO (fff2ad2e) — sequencing accepted
