# Encryption posture — every channel to and from a PSP on-prem node

Evidence-based inventory of every hop, what protects it, and how that claim is tested. Claims here
are backed by tests that run in CI, not by design intent. Where a hop is **not** encrypted, it says
so plainly.

_Last verified: 2026-07-20 for hops 1–7. Hops 8–14 added 2026-10-17 (R14a) from the code; the
tests named there exist but were not re-run for this update._

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
| 8 | Edge → control plane `POST /api/v1/edge/assessments` (decision record v2) | outbound | **mTLS, TLS 1.3** | **HSE-1 sealed**, context `hokeka.decision.records`, replay envelope | `OutboxShipperIntegrationTest` (edge-host), `EdgeResidencyControllerTest` (BACKEND) |
| 9 | Control plane → Edge `GET /api/v1/edge/config` | outbound (Edge polls) | **mTLS, TLS 1.3** | **HSE-1 sealed** to the node's X25519 key, context `hokeka.edge.config` | `EdgeConfigServiceTest`, `EdgeResidencyControllerTest`. The Edge does not fetch it yet (planned, R2). |
| 10 | Edge ↔ control plane Edge Tunnel `wss://…/api/v1/edge/tunnel` | outbound (Edge opens) | **mTLS, TLS 1.3**, nginx `ssl_verify_client on` (`BACKEND/nginx/edge-tunnel.conf`) | every frame except `ping`/`pong` **HSE-1 sealed**, context `hokeka.tunnel.<type>`, header bound inside the seal | `TunnelFrameCodecTest`, `EdgeTunnelIntegrationTest` (fake Edge). Server only: the Edge tunnel client is planned (R5). |
| 11 | Control plane instance ↔ instance via Redis pub/sub (tunnel relay) | control-plane internal | Redis transport as deployed | frames relayed **still sealed** (`RelayMessage`); Redis never sees plaintext | `EdgeTunnelIntegrationTest` (two instances) |
| 12 | Edge → control plane `POST /api/v1/edge/decision` (Hokeka AI recommendation) | outbound | **mTLS, TLS 1.3** | **plain JSON, not sealed**, carries the full feature bag ⚠️ | see §4 |
| 13 | Edge → local Edge Ledger (PostgreSQL 16) | on-prem, private network / loopback | **plaintext** ⚠️, SCRAM-SHA-256 auth | — | see §4 |
| 14 | Edge → shared site ledger (`--ledger-url`) | on-prem, PSP network | whatever the JDBC URL sets | — | operator-controlled, see §4 |

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

**Hop 12 — the Hokeka AI recommendation call sends transaction data in plain JSON.**
`HokekaAiDecisionClient.enrichBorderline` posts the full enriched feature bag (including
`pan_hash`, amount and merchant) to `/api/v1/edge/decision` for borderline decisions. TLS protects
it on the wire, but it is not HSE-1 sealed, and it puts class-T data in the cloud, which the
residency contract forbids. Work package R10c removes this call. Until then it is the one known
residency leak on the Edge side.

**Hops 13–14 — Edge → ledger is not TLS.** The local ledger container sits on the same private
bridge network as Aerospike with **no published port**; the native install binds PostgreSQL to
`127.0.0.1`. Auth is SCRAM-SHA-256 with a random password from the installer. A shared site
ledger (`--ledger-url`) crosses the PSP network: the operator must add `sslmode=require` (or
stronger) to the JDBC URL. Nothing in code compels it.

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
- The residency work adds no new key material (contract §3.1). The control plane Ed25519 bundle
  signing key (`EDGE_BUNDLE_SIGNING_KEY`) signs every cloud → Edge envelope: bundle, Edge config and
  every cloud-sent tunnel frame. The control plane X25519 key (`EDGE_METRICS_INGEST_KEY`) is the
  recipient of every Edge → cloud envelope: metrics, decision records and Edge-sent tunnel frames.
  Every control plane instance must hold the same two keys, because any instance may open a
  relayed frame.

## 6. Data residency and HSE-1 contexts

_Added 2026-10-17. Normative source: [`EDGE_DATA_RESIDENCY_CONTRACT.md`](../EDGE_DATA_RESIDENCY_CONTRACT.md) §2–§3._

Encryption here protects data in transit. Residency limits what reaches the cloud at all. Only
decision records (class D: ids, decision, score, rule ids, bundle version, flags) and hourly
aggregates (class A) are meant to leave the Edge Site. Transaction details (class T) reach the
cloud only inside a sealed tunnel response to an audited request, and the cloud must not persist
them. Card secrets (class S) and `pan_hash` (class E) never leave the site.

HSE-1 binds a context string into key derivation and the AEAD tag, so an envelope sealed for one
purpose cannot be opened as another.

| Context | Direction | Payload | Status |
|---|---|---|---|
| `hokeka.rules.bundle` | cloud → Edge | rule bundle | in use |
| `hokeka.metrics.report` | Edge → cloud | metrics report | in use |
| `hokeka.edge.activation` | Edge → cloud | activation | in use |
| `hokeka.decision.records` | Edge → cloud | decision record batch v2 | in use (`OutboxShipper`, `DecisionRecordIngestService`) |
| `hokeka.edge.config` | cloud → Edge | Edge config document | sealed by the cloud (`EdgeConfigService`); Edge consumer planned (R2) |
| `hokeka.tunnel.<type>` | per frame type | tunnel frames | cloud side in use (`TunnelFrameCodec`); Edge side planned (R5) |
| `hokeka.sanctions.list` | cloud → Edge | sanctions list snapshot | reserved (R10b) |

Every sealed payload is wrapped in the replay envelope `{nonce, issuedAt, edgeId, payload}`. The
receiver rejects a repeated nonce or a clock skew over 300 s, and checks `edgeId` against the mTLS
identity.

**Support access is gated twice by design.** The cloud gate exists today: `EdgeDataGateway`
refuses a platform user's pull without the PSP's support-access flag, a reason and a platform
role. The Edge gate (refuse a platform request when the Edge config has support access off) is
planned with the Edge tunnel client (R5).

**Cloud data that still breaks the residency model:** the cloud `transactions` table and its
dependants, written by `/api/v1/transactions/ingest`, until R15 back-fills each Edge and R12
purges them; and hop 12 above until R10c.
