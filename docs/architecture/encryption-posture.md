# Encryption posture — every channel to and from a PSP on-prem node

Evidence-based inventory of every hop, what protects it, and how that claim is tested. Claims here
are backed by tests that run in CI, not by design intent. Where a hop is **not** encrypted, it says
so plainly.

_Last verified: 2026-07-20._

## 1. Hop inventory

| # | Hop | Direction | Transport | Payload | Verified by |
|---|-----|-----------|-----------|---------|-------------|
| 1 | PSP API node → edge `/edge/evaluate` | inbound, on-prem | **TLS 1.3** | — | `EdgeTransactionApiTlsIntegrationTest` |
| 2 | Edge → control plane `GET /api/v1/edge/bundle` | outbound | **mTLS, TLS 1.3** | **HSE-1 sealed** | `hse-crypto` suite + `SealedEnvelopeCodecTest` |
| 3 | Edge → control plane `POST /api/v1/edge/metrics` | outbound | **mTLS, TLS 1.3** | **HSE-1 sealed** | same |
| 4 | Edge → control plane `POST /api/v1/edge/enroll` | outbound, once | **TLS 1.3** | activation context | `ActivationService` tests |
| 5 | Edge → local Aerospike | on-prem, loopback/private | **plaintext** ⚠️ | — | see §4 |
| 6 | Browser → control plane | inbound | **TLS 1.2/1.3 + HSTS** | — | nginx config |
| 7 | nginx → Spring upstream | control-plane internal | **plaintext** ⚠️ | — | see §4 |

## 2. Why two layers on hops 2–3

TLS terminates wherever the PSP's infrastructure terminates it — a load balancer, a proxy, a service
mesh sidecar. At that point rule logic would be readable by infrastructure the PSP controls. HSE-1
keeps the payload sealed from the control plane's signing key all the way to the edge's X25519 key,
so **no intermediary can read the rules**. Conversely HSE-1 alone would leak sizes, timing and
endpoints, which TLS hides. Neither layer is optional; the code refuses to run with either disabled.

## 3. What is actually proven, and by which test

**Crypto layer** — `edge-engine/crates/hse-crypto`, 8/8, runs by default:

| Property | Test | Result |
|---|---|---|
| Rust opens byte-for-byte what Java sealed | `opens_java_sealed_vector` | ✅ |
| **No plaintext on the wire** — every 8-byte window of the payload asserted absent from the envelope | `envelope_leaks_no_plaintext` | ✅ |
| One flipped ciphertext bit is caught before decryption | `rejects_tampered_ciphertext` | ✅ `BadSignature` |
| Forged signature refused | `rejects_tampered_signature` | ✅ `BadSignature` |
| Another edge's key cannot open it | `rejects_wrong_edge_key` | ✅ `Decrypt` |
| Impersonating the control plane refused (key pinning) | `rejects_wrong_control_plane_key` | ✅ `BadSignature` |
| A rule bundle cannot be opened as metrics (context binding) | `rejects_wrong_context` | ✅ `Decrypt` |
| Malformed/truncated framing never yields partial plaintext | `rejects_malformed_framing` | ✅ |

> Regression found while establishing this: the Java↔Rust interop test was `#[ignore]`d, so the
> default suite skipped the one test that catches wire-format drift between the two implementations.
> It now runs by default.

**Transport + envelope layer** — `edge-host`, 40/40:

- Real Tomcat on TLS 1.3 serves `/edge/status`; a plaintext request to the same port is refused.
- The app aborts at startup with an actionable message when TLS material is missing — it never
  silently downgrades to HTTP.
- `SealedEnvelopeCodecTest` proves no-plaintext, tamper, untrusted signer, wrong context, replay,
  stale timestamp and wrong-edge-id rejection.

**Fail-closed behaviour** — an edge that has never loaded a verified bundle returns `HOLD`, never
`ALLOW` (`fail_closed_is_hold`); a malformed publish leaves the previously-active bundle in place
(`malformed_ir_leaves_previous_bundle_active`), so a bad rule push cannot disarm a running edge.

## 4. Honest gaps

**Hop 5 — edge → Aerospike is plaintext.** Aerospike **Community Edition supports neither
authentication nor TLS**. It is protected by isolation only: in the container deployment it sits on a
private bridge network with **no published port**; in the native deployment it is bound to `127.0.0.1`
with port 3000 firewalled. For an on-prem single-tenant box that is a reasonable boundary, but it is
network isolation, not cryptography. **Aerospike Enterprise** adds TLS and DB-level credentials; the
config carries the hook and the note. Recommended for PSPs handling regulated data.

**Hop 7 — nginx → Spring is plaintext**, by design (`proxy_pass http://fraud_detector_backend/`).
Acceptable only while that upstream is loopback or a private network segment on the same host. If the
control plane is ever split across hosts, this hop must become TLS.

**Plaintext to the TLS port returns HTTP 400, not a connection reset.** Tomcat answers rather than
drops. The request never reaches application code (asserted), but the port does respond.

**Postgres TLS is operator-controlled.** `spring.datasource.url=${DATABASE_URL}` does not enforce
`sslmode=require`. Deployments should set it; nothing in code compels it.

**Not verified end-to-end against a live control plane.** The crypto, transport and envelope layers
are each tested, and both sides implement the same pinned contract, but a full edge↔control-plane
round trip over real mTLS has not been executed on this machine.

## 5. Key management

- The edge generates its own X25519 + Ed25519 keypairs on first boot; private keys never leave the
  node and are stored mode 600.
- The control plane pins the edge's public keys at first activation (trust-on-first-activation). A
  later activation presenting different keys for a known edge id is rejected and raised as a security
  alert, not a generic failure.
- The edge pins the control plane's Ed25519 and X25519 public keys, so a compromised DNS or CA cannot
  substitute a rule source.
- Revocation stops bundle distribution on the next poll and is irreversible — re-enrollment requires
  fresh keys.
