# Edge ⇄ Control-plane channel contract (v1)

Normative contract shared by the control plane (`BACKEND`, `com.posgateway.aml.edge`) and the PSP
on-prem host (`edge-host`, `com.hokeka.edge`). Both sides implement exactly this; the proof tests in
`EdgeChannelEncryptionTest` assert it.

## 1. Layering — two independent layers, both mandatory

```
┌─ TLS 1.3 (transport) ───────────────────────────────────────────────┐
│  mTLS: edge presents a client cert; control plane pins the edge     │
│  ┌─ HSE-1 envelope (payload) ───────────────────────────────────┐   │
│  │  X25519 ECDH → HKDF-SHA256 → ChaCha20-Poly1305 + Ed25519 sig │   │
│  │  rule IR / metrics JSON                                       │   │
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

## 3. HSE-1 context strings

`context` is bound into the HKDF `info` and the AEAD AAD, so an envelope sealed for one purpose can
never be opened as another.

| Purpose | Context bytes |
|---------|---------------|
| Rule bundle distribution | `hokeka.rules.bundle` |
| Aggregate metrics upload | `hokeka.metrics.report` |
| Activation credential issue | `hokeka.edge.activation` |

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

`GET /api/v1/edge/bundle` supports `If-None-Match: <version>` → `304` when the edge is current.

## 6. Metrics payload — aggregate only

The metrics body carries **counts and outcomes only**. It must never contain a PAN, PAN hash,
customer identifier, IP, amount, merchant id, or any per-transaction field. Enforced by
`EdgeMetricsReport` (aggregate record) and asserted in the proof tests.

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
  receives bundles.
- `SUSPENDED` / `REVOKED` — distribution stops on the next poll; revocation is irreversible and
  requires re-enrollment with fresh keys.

Key pinning is trust-on-first-activation: the first successful activation records the edge's public
keys permanently. A later activation presenting different keys for the same edge id is rejected.
