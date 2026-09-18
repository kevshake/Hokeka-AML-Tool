# Visa / Verifi API 3.0 Integration

Reference: **Verifi API 3.0 v08.07.01** (April 2026). Merchants host endpoints; Verifi calls inbound via HTTPS POST with JWT authentication.

## Merchant endpoints (register base URL in Verifi portal)

Configure base URL as `https://your-host/api/v1/integrations/verifi`. Verifi appends resource paths:

| Verifi resource | Method | Our path | Purpose |
|-----------------|--------|----------|---------|
| `/notifications` | POST | `/api/v1/integrations/verifi/notifications` | All notification case types |
| `/decisions` | POST | `/api/v1/integrations/verifi/decisions` | Real-time RDR accept/decline (2s SLA) |
| `/ping` | POST | `/api/v1/integrations/verifi/ping` | Connectivity / auth validation |

Legacy aliases (non-spec, retained for backward compatibility):

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/v1/integrations/verifi/rdr` | Alias → same handler as `/notifications` |
| POST | `/api/v1/chargeback/verifi/rdr` | Alias → same handler as `/notifications` |
| POST | `/api/v1/integrations/verifi/decision` | Alias → same handler as `/decisions` |
| GET | `/api/v1/integrations/verifi/rdr/health` | Health probe |

Authenticated read API for ingested disputes: `GET /api/v1/chargeback/disputes`.

## Configuration

```properties
verifi.rdr.enabled=true
verifi.rdr.auth-mode=RSA
verifi.rdr.jws-verification-public-key=<Visa signing certificate/public key PEM>
verifi.rdr.jwe-decryption-private-key=<merchant RSA private key PEM>
verifi.rdr.jws-key-id=<optional expected signing kid>
verifi.rdr.jwe-key-id=<optional expected encryption kid>
verifi.rdr.webhook-secret=<32-char shared secret for JWS HS256>
verifi.rdr.partner-id=<Verifi partnerId for Ping response>
verifi.rdr.client-id=<Verifi clientId for Ping response>
verifi.rdr.callback-url=https://your-host/api/v1/integrations/verifi
verifi.rdr.signature-required=true
verifi.rdr.auto-create-cases=true
```

Environment variables use the upper-case names shown by the properties, including `VERIFI_RDR_AUTH_MODE`, `VERIFI_RDR_JWS_VERIFICATION_PUBLIC_KEY`, and `VERIFI_RDR_JWE_DECRYPTION_PRIVATE_KEY`. PEM values may contain real newlines or escaped `\n` sequences.

## Authentication (per spec §Getting Started)

Verifi sends `Authorization: Bearer <JWS>` on every inbound request. In RSA mode the signed JWS payload is a compact JWE; an unsigned, standalone RSA JWE is rejected because encryption alone does not authenticate its sender.

**Shared-secret validation (`auth-mode=HS256`):**

1. Split token into `header.payload.signature`
2. Verify `alg` is `HS256`
3. Compute `HMAC-SHA256(base64url(header) + '.' + base64url(payload), sharedSecret)`
4. Validate `jti` uniqueness within 360 seconds, `iat` within ±300s, `exp` not expired

**Certificate validation (`auth-mode=RSA`):**

1. Require and verify outer `alg=PS256` with the configured Visa/Verifi public key or certificate.
2. Require a nested compact JWE and optional configured signing/encryption `kid` values.
3. Require `alg=RSA-OAEP-256` and `enc=A256GCM` or `A128GCM`.
4. Decrypt the content-encryption key with the merchant RSA private key, authenticate the protected header as AES-GCM AAD, and decrypt the claims.
5. Validate `jti`, `iat`, and `exp` only after signature verification and authenticated decryption.

RSA mode cannot downgrade to API-key or legacy HMAC authentication. `auth-mode=LEGACY` is reserved for non-Verifi partner callbacks that intentionally use those older mechanisms.

**Request headers from Verifi:**

| Header | Purpose |
|--------|---------|
| `Authorization` | Bearer JWS or JWE token |
| `x-verifi-api-version` | `3.0` — return HTTP 501 if unsupported |
| `x-verifi-correlation-id` | Unique request ID (used for deduplication) |
| `x-verifi-retry-count` | Retry attempt counter (notifications) |

Legacy Butter/PSP partner HMAC (`jsonBody + "+" + createdAt`) and `X-Api-Key` remain as dev fallbacks when `signature-required=false` or for non-Verifi partners.

## Notification callback flow

```
Issuer → Visa/Verifi → POST /notifications (Bearer JWS)
                              ↓
                    Validate JWS + API version
                              ↓
                    Map payload → chargeback_disputes
                              ↓
                    Create alert (+ optional compliance case)
                              ↓
                    HTTP 200 empty body
```

### Supported notification case types (`caseType`)

| caseType | caseEvent values | Handled? |
|----------|------------------|----------|
| `RDR_NOTICE` | NEW, DELETE, TIMEOUT | Yes — ingested; `outcome` ACCEPTED/DECLINED mapped |
| `DISPUTE_NOTICE` | NEW, DELETE | Yes |
| `FRAUD_NOTICE` | NEW, UPDATE, DELETE, REACTIVATE | Yes |
| `CE_NOTICE` | NEW, DELETE, FAILED, TIMEOUT | Yes |
| `EXCEPTION_NOTICE` | NEW, UPDATE, DELETE | Stored if received (spec: not yet live) |

### Key payload fields (Verifi API 3.0)

| Spec field | Mapped to |
|------------|-----------|
| `caseId` | `case_id`, dedup fallback |
| `caseType` | `notification_type` |
| `caseEvent` | alert reason / case-open logic |
| `outcome` | `rdr_status` (RDR_NOTICE) |
| `transactionAmount` | `case_amount` / `case_currency` |
| `arn` | `acquirer_reference_number` |
| `cardAcceptorId` | `network_merchant_id` |
| `transactionId` | `network_transaction_id` |
| `purchaseIdentifier` | `merchant_order_id` |
| `reasonCode` | `reason_code` |
| `cardBin`, `cardLast4` | `card_bin`, `card_last4` |

Legacy Butter/PayNext nested shapes (`data.visa_rdr`, `case`, `network`) still supported.

## Decision API

Verifi POSTs to `/decisions` with `decisionId`, `caseType=DISPUTE`, `caseAmount`, etc.

**Response format (spec):**

```json
{
  "outcome": "ACCEPTED",
  "statusCode": "103",
  "reason": "Merchant reason text",
  "refundAmount": { "amount": 10.12, "currency": "USD" }
}
```

Declined: `outcome=DECLINED`, `statusCode` one of `957` (general), `950` (account closed), `951` (already refunded).

Current rule engine: auto-accept low-value (&lt;500) non-fraud; decline fraud (`10.x` reason codes).

## HTTP status codes

| Code | When |
|------|------|
| 200 | Success (notifications: empty body) |
| 401 | JWS/signature validation failed |
| 501 | `x-verifi-api-version` ≠ 3.0 |
| 500 | Processing error |

## Remaining certification gaps

| Gap | Notes |
|-----|-------|
| **JWE (RSA) auth** | Implemented with PS256, RSA-OAEP-256, AES-GCM, key IDs, replay protection, and no legacy downgrade |
| **Order Insight `/orders`** | Separate product — not implemented |
| **BIN/CAID enrollment lookup** | `acquirerBin` + `cardAcceptorId` not used for merchant resolution yet |
| **Merchant MID mapping** | Relies on `purchaseIdentifier` parsing; no dedicated MID table |
| **SFTP daily extract** | Batch reconciliation not implemented |
| **CDRN** | Separate Verifi product |
| **Decision API integration testing** | Verifi test portal supports Order Insight + Notifications only (spec §Integration Testing) |
| **Notification retry handling** | Spec retry (6 attempts / 30 min) marked future release |
| **Firewall allowlisting** | Verifi source IPs: `198.241.206.21`, `198.241.207.21` |
| **Automated certification tests** | Manual replay via Verifi One test portal required |

## Frontend

Chargeback disputes exposed via `GET /chargeback/disputes` for Reports Center.
