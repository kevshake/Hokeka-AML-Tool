# Visa / Verifi RDR Integration

## Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/api/v1/integrations/verifi/rdr` | HMAC or API key | Primary RDR webhook |
| POST | `/api/v1/chargeback/verifi/rdr` | HMAC or API key | Alias path |
| GET | `/api/v1/integrations/verifi/rdr/health` | Public | Health / config probe |
| GET | `/api/v1/chargeback/disputes` | Session | List ingested disputes |
| GET | `/api/v1/chargeback/disputes/{id}` | Session | Dispute detail |

## Configuration

```properties
verifi.rdr.enabled=true
verifi.rdr.webhook-secret=<shared HMAC secret>
verifi.rdr.api-key=<optional X-Api-Key fallback>
verifi.rdr.callback-url=https://your-host/api/v1/integrations/verifi/rdr
verifi.rdr.signature-required=true
verifi.rdr.auto-create-cases=true
```

Environment variables: `VERIFI_RDR_ENABLED`, `VERIFI_RDR_WEBHOOK_SECRET`, `VERIFI_RDR_API_KEY`, `VERIFI_RDR_CALLBACK_URL`.

## Notification handling

The webhook service normalizes partner payloads into `chargeback_disputes` and:

1. Deduplicates via `X-Butter-Webhook-Deduplication-ID` / `X-Verifi-Deduplication-ID`
2. Creates an `alerts` row for analyst visibility
3. Optionally opens/updates a `compliance_cases` row (`CHARGEBACK` alert type)
4. Increments Aerospike merchant chargeback counters (when merchant resolves)

Supported notification types (derived from headers + body):

- `DISPUTE_ALERT` — pre-dispute / case opened
- `RDR_PREVENTION` — auto-refund / liability accepted before chargeback
- `RDR_RESOLUTION` — terminal accepted outcome
- `RDR_DECLINED` — rule did not match; dispute proceeds

Payload shapes supported:

- Butter/Verifi `verifi.rdr` object (case, network, card, psp_transaction)
- PayNext-style `data.visa_rdr` sub-object (`status`, `case_id`, `reason`)

## Signature verification

HMAC-SHA256 over `jsonBody + "+" + createdAt` header (Butter/Verifi partner pattern).
Headers checked: `X-Butter-Webhook-Signature`, `X-Verifi-Webhook-Signature`, `X-Webhook-Signature`.

## Gaps vs full Verifi certification

| Gap | Notes |
|-----|-------|
| **Decision API** | Not implemented. Merchants needing real-time accept/decline responses must integrate Verifi Decision API separately (sub-2s SLA). |
| **Notifications API outbound responses** | We ingest webhooks only; we do not POST decision responses back to Verifi. |
| **BIN/CAID enrollment** | Enrollment and rule configuration happen in Verifi portal / acquirer — not in this app. |
| **Merchant MID mapping** | Network `merchant_id` → internal `merchants` mapping relies on `merchant_order_id` parsing; dedicated MID lookup table not yet seeded. |
| **SFTP daily extract** | Batch reconciliation file ingestion not implemented. |
| **CDRN** | Cardholder Dispute Resolution Network is a separate Verifi product — not wired here. |
| **Certification test cases** | No automated certification test suite; manual webhook replay required. |
| **3DS / fraud score passthrough** | RDR case enrichment with issuer 3DS data not implemented. |

## Frontend

Chargeback disputes are exposed via `GET /chargeback/disputes` for the Reports Center chargeback category. A dedicated chargeback operations page is not yet present.
