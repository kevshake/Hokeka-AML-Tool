# Edge ⇄ Control-plane channel contract (v1)

Normative contract shared by the control plane (`BACKEND`, `com.posgateway.aml.edge`) and the PSP
on-prem host (`edge-host`, `com.hokeka.edge`). Both sides implement exactly this; the proof tests in
`EdgeChannelEncryptionTest` assert it.

> **Scope.** This document defines the channel: mTLS identity, HSE-1, the replay envelope (§4) and
> the node lifecycle (§7). [`../EDGE_DATA_RESIDENCY_CONTRACT.md`](../EDGE_DATA_RESIDENCY_CONTRACT.md)
> reuses all of them unchanged and defines what travels over the channel: decision record v2, the
> Edge Config document, the Edge Tunnel and metrics report v2. Where the two overlap, the residency
> contract wins.
>
> The Edge Node is the only component that evaluates transactions. The cloud receives decision
> records and aggregates, never transaction data, and reads transaction details live from the Edge
> over the tunnel. The Edge opens every connection; the PSP needs egress on TCP 443 only and no
> inbound port.

## 1. Layering — two independent layers, both mandatory

```
┌─ TLS 1.3 (transport) ───────────────────────────────────────────────┐
│  mTLS: edge presents a client cert; control plane pins the edge     │
│  ┌─ HSE-1 envelope (payload) ───────────────────────────────────┐   │
│  │  X25519 ECDH → HKDF-SHA256 → ChaCha20-Poly1305 + Ed25519 sig │   │
│  │  rule IR / config / decision records / metrics / tunnel frame │   │
│  └───────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

TLS alone is **not** sufficient: it terminates at load balancers and proxies the PSP controls, so
rule logic would be readable at that hop. HSE-1 keeps the payload opaque end-to-end, from the
control plane's signing key to the edge's X25519 key — never decryptable by anything in between.
HSE-1 alone is **not** sufficient either: it leaks traffic metadata (sizes, timing, endpoints) that
TLS hides. Neither layer may be disabled.

## 2. Fail-closed rules

| Condition | Behaviour |
|-----------|-----------|
| TLS disabled or plaintext HTTP request | connection refused, no fallback |
| Edge not `ACTIVE` in the control plane | `403`, no bundle served |
| HSE-1 signature invalid / bit-flipped | envelope rejected, previous bundle retained |
| Envelope replayed (nonce seen, or ts skew > 300 s) | rejected |
| No bundle ever successfully loaded | evaluate returns `HOLD` (never ALLOW) |
| Edge Ledger write fails (`ledger.fail-closed=true`, default) | evaluate returns `HOLD / LEDGER_UNAVAILABLE` |
| Decision record fails the closed v2 schema | Edge: not shipped, counted; cloud: rejected per record, nothing stored |
| Tunnel upgrade from a node that is not `ACTIVE` | `403`, no `101` |

## 3. HSE-1 context strings

`context` is bound into the HKDF `info` and the AEAD AAD, so an envelope sealed for one purpose can
never be opened as another.

| Purpose | Context bytes |
|---------|---------------|
| Rule bundle distribution | `hokeka.rules.bundle` |
| Aggregate metrics upload (v1 and v2) | `hokeka.metrics.report` |
| Activation credential issue | `hokeka.edge.activation` |
| Decision record batch v2 (Edge → cloud) | `hokeka.decision.records` |
| Edge Config document (cloud → Edge) | `hokeka.edge.config` |
| Edge Tunnel frame of type `<t>` | `hokeka.tunnel.<t>` |
| Sanctions/PEP list snapshot (reserved, R10b) | `hokeka.sanctions.list` |

The last four are defined in the residency contract §3.2. Keys are unchanged: the Control Plane
Ed25519 key signs every cloud → Edge envelope, and the Control Plane X25519 key
(`EDGE_METRICS_INGEST_KEY`) receives every Edge → cloud envelope.

## 4. Replay envelope (inner JSON, before sealing)

Every sealed payload is wrapped so replays are detectable:

```json
{
  "nonce":    "<22-char base64url, 16 random bytes>",
  "issuedAt": "2026-07-20T10:15:30Z",
  "edgeId":   "acme-eu-1",
  "payload":  { ... }
}
```

Receiver rejects when: `nonce` already seen (bounded LRU, ≥ 10 000 entries), or
`|now - issuedAt| > 300 s`.

## 5. HTTP endpoints (control plane)

| Method | Path | Auth | Body | Returns |
|--------|------|------|------|---------|
| `POST` | `/api/v1/edge/enroll` | enrollment code | edge X25519 + Ed25519 public keys | `202` pending |
| `GET`  | `/api/v1/edge/bundle` | mTLS + edge id | — | HSE-1 sealed rule IR (`application/octet-stream`) |
| `POST` | `/api/v1/edge/metrics` | mTLS + edge id | HSE-1 sealed aggregate metrics | `204` |
| `GET`  | `/api/v1/edge/config` | mTLS + edge id | — | HSE-1 sealed Edge Config document (`application/octet-stream`), `ETag: "<configVersion>"`, `Cache-Control: no-store` |
| `POST` | `/api/v1/edge/assessments` | mTLS + edge id | `application/octet-stream`: HSE-1 sealed decision record batch v2 (1..500 records) | `200` acknowledgement |
| `GET` (WebSocket upgrade) | `/api/v1/edge/tunnel` | mTLS + edge id, node `ACTIVE` before `101` | subprotocol `hokeka-tunnel.v1`, sealed JSON text frames | session |
| `POST` | `/api/v1/edge/assessments` | mTLS + edge id | `application/json`: v1 assessment envelope | legacy; **removed in R12** |

`GET /api/v1/edge/bundle` and `GET /api/v1/edge/config` support `If-None-Match` (quoted, bare or
`W/"…"`) → `304` when the edge is current. Both v1 and v2 share the `/edge/assessments` path until
R12; Spring routes by `Content-Type`.

All machine-to-machine endpoints use the same authorisation: verified certificate identity
(`EdgeIdentityResolver`), node `ACTIVE`, bare `403` otherwise. nginx terminates mTLS on
`edge.hokeka.com` with `ssl_verify_client on` (`BACKEND/nginx/edge-tunnel.conf`) and forwards the
verification headers only to the Control Plane.

**Status of each side:**

| Endpoint | Control Plane | Edge Node |
|---|---|---|
| `/edge/bundle`, `/edge/metrics` (v1) | built | built (metrics v2 not yet sent) |
| `/edge/config` | built (R3) | consumer planned (R2) |
| `/edge/assessments` v2 | built (R7): stores the record, raises alerts and cases by reference | built (R1): durable outbox in the Edge Ledger, `OutboxShipper` |
| `/edge/tunnel` | built (R4): handshake auth, Redis registry, cross-instance relay, timeouts, `edge_data_access_log` audit | tunnel client planned (R5) |

## 6. Metrics payload — aggregate only

The metrics body carries **counts and outcomes only**. It must never contain a PAN, PAN hash,
customer identifier, IP or any per-transaction field. v1 (`EdgeMetricsReport`, sent today) carries
no amount and no merchant id. v2 (residency contract §5, not yet sent by the Edge) adds hourly
amount sums broken down per merchant and per currency only, never per card, customer, device, IP or
country.

## 6a. Decision records — ids and outcome only

The decision record v2 is the only per-transaction data that leaves an Edge Site. It carries ids,
decision, score, rule ids, bundle, evaluator, latency, merchant id and compliance flags. It never
carries the PAN hash, amount, currency, country, customer or device ids, names or free-text
reasons. The schema is closed: the Control Plane rejects any record with an unknown field and stores
no part of it. See residency contract §4.

## 6b. Edge Tunnel — cloud reads, Edge answers

The Edge opens a WebSocket over TLS 1.3 mTLS to `wss://edge.hokeka.com/api/v1/edge/tunnel` and keeps
it open. The cloud sends sealed, signed, read-only requests (`txn.search`, `txn.get`,
`txn.batchGet`, `txn.related`, `filing.extract`, `export.stream`, `ledger.health`, plus the one
insert-only `ledger.backfill`). The Edge enforces the op allowlist and answers with sealed
responses. Every request is audited on both sides under one `auditId`. The cloud never stores a
response. The PSP can disable the tunnel (`EDGE_TUNNEL_ENABLED=false`) or narrow its ops
(`EDGE_TUNNEL_OPS`). See residency contract §7.

## 7. Edge node lifecycle

```
PENDING ──approve──> APPROVED ──activate(first boot)──> ACTIVE
   │                     │                                 │
   └──────reject─────────┴──────────revoke/suspend─────────┘
                                    ↓
                              REVOKED  (bundle distribution stops immediately)
```

- `PENDING` — PSP requested a node; enrollment code issued, single-use, 24 h TTL.
- `APPROVED` — a platform admin authorized it; the node may complete activation.
- `ACTIVE` — the node presented its enrollment code and public keys; keys are **pinned**. It now
  receives bundles and its Edge Config, and its decision records and tunnel sessions are accepted.
- `SUSPENDED` / `REVOKED` — distribution stops on the next poll, and decision records and tunnel
  upgrades get `403`; revocation is irreversible and requires re-enrollment with fresh keys.

Key pinning is trust-on-first-activation: the first successful activation records the edge's public
keys permanently. A later activation presenting different keys for the same edge id is rejected.
