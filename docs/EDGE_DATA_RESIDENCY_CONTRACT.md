# Edge data residency contract (v1)

_Status: normative. Version 1, 2026-10-16. Work package R0 of
[`architecture/EDGE_DATA_RESIDENCY_PLAN.md`](architecture/EDGE_DATA_RESIDENCY_PLAN.md), whose owner
decisions are binding on this document._

_Supersedes [`EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`](EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md) (the
"cloud ingest + adopt" model). Extends [`architecture/edge-channel-contract.md`](architecture/edge-channel-contract.md):
HSE-1, the replay envelope, mTLS identity and the node lifecycle defined there are unchanged and
apply here._

**Machine-readable contract:** JSON Schemas (draft 2020-12) under [`contracts/`](contracts/), fixtures
under [`contracts/tunnel/examples/`](contracts/tunnel/examples/), checked by
`py docs/contracts/validate.py`. Where this text and a schema disagree, this text wins and the schema
is a defect.

The key words MUST, MUST NOT, REQUIRED, SHALL, SHALL NOT, SHOULD, SHOULD NOT, RECOMMENDED, MAY and
OPTIONAL are to be read as described in BCP 14 (RFC 2119, RFC 8174) when, and only when, they
appear in all capitals.

---

## Contents

1. [Roles and invariants](#1-roles-and-invariants)
2. [Data classification](#2-data-classification)
3. [Keys, sealing and replay protection](#3-keys-sealing-and-replay-protection)
4. [Decision record v2](#4-decision-record-v2)
5. [Metrics report v2](#5-metrics-report-v2)
6. [Edge config document](#6-edge-config-document)
7. [Edge Tunnel protocol](#7-edge-tunnel-protocol)
8. [Retention and legal holds](#8-retention-and-legal-holds)
9. [Support access](#9-support-access)
10. [Cross-PSP intelligence](#10-cross-psp-intelligence)
11. [Legacy data backfill](#11-legacy-data-backfill)
12. [Versioning and compatibility](#12-versioning-and-compatibility)
13. [Conformance](#13-conformance)
- [Appendix A: decisions this spec takes that the plan left open](#appendix-a-decisions-this-spec-takes-that-the-plan-left-open)

---

## 1. Roles and invariants

| Term | Meaning |
|---|---|
| **Edge Node** | One `edge-host` process (+ `edge-engine`) identified by `edgeId`, the CN of its mTLS client certificate. |
| **Edge Site** | 1..N Edge Nodes of one PSP that share one Edge Ledger and one Aerospike cluster. Identified by `siteId`, assigned by the Control Plane. A single-node site's `siteId` defaults to its `edgeId`. |
| **Edge Ledger** | The site's PostgreSQL store of evaluations (`edge_evaluations`), outbox, holds, config history, purge log and access log. The system of record for transaction data. |
| **Edge Tunnel** | The outbound WebSocket (TLS 1.3, mTLS) each Edge Node opens to the Control Plane, over which the cloud sends read requests (§7). |
| **Decision Record** | The ID-and-outcome record of one evaluation, the only per-transaction data that leaves an Edge Site (§4). |
| **Edge Config** | The sealed per-PSP settings document the Edge pulls (§6). |
| **Control Plane** | The cloud `BACKEND`. **Console**: the cloud SPA. |

Invariants. Every later section refines one of these; an implementation that breaks one is
non-conformant whatever else it does.

- **I1 (sole decider).** Only the Edge evaluates transactions. The Control Plane MUST NOT
  re-evaluate, re-score or override an Edge decision.
- **I2 (no cloud transaction store).** The Control Plane MUST NOT persist, log, cache or queue any
  class-T field (§2) outside a filed regulatory document (§2.3). This covers Postgres, Redis,
  Kafka, Neo4j, Aerospike, files, application logs, traces, metrics labels, error messages and
  backups.
- **I3 (outbound only).** The Edge opens every connection. A PSP MUST NOT need to open an inbound
  port for Hokeka. Egress is TCP 443 only.
- **I4 (workflow by reference).** Alerts, cases, SAR/STR/CTR workflow records in the cloud
  reference transactions only by `edgeAssessmentId` and `txnRef`.
- **I5 (audited pulls).** Every tunnel request is attributable to an actor and a purpose and is
  written to an audit log on both sides under one `auditId` (§7.15).
- **I6 (PSP sovereignty).** The PSP can restrict or disable the tunnel locally (§7.6.1), and
  platform staff can see its data only when the PSP has enabled support access (§9).
- **I7 (retention floor).** Nothing is purged before 5 years, on either side (§8).
- **I8 (fail closed).** On any doubt (bad seal, replay, unknown op, unknown field in outbound data,
  ledger write failure with `ledger.fail-closed=true`) the component refuses rather than degrades
  to a less private or less safe behaviour.

## 2. Data classification

### 2.1 Classes

| Class | Where it may exist | Leaves the Edge Site? |
|---|---|---|
| **S** (secret) | Nowhere. Never stored, even on the Edge. | Never. |
| **E** (Edge-only) | Edge Ledger, Aerospike, Edge memory. | Never, not even over the tunnel. |
| **D** (decision record) | Edge Ledger; cloud `assessments` (decision records). | Yes, in decision record v2 (§4). |
| **A** (aggregate) | Edge memory; cloud metrics tables. | Only as hourly per-merchant / per-currency aggregates (§5). |
| **T** (tunnel-transient) | Edge Ledger. In the cloud: only in memory for the life of one request, and in the Console's memory. | Only inside a sealed tunnel response to an authorised, audited request (§7). |
| **F** (filed) | Class-T data the cloud MAY store inside a **filed** SAR/STR/CTR document (§2.3). | As T, then stored in the filed document. |
| **C** (cloud master data) | Cloud. | n/a: originates in the cloud (merchants, rules, users, cases). |

### 2.2 Field by field

`/edge/evaluate` request fields are those of [`edge-transaction-evaluation.md`](edge-transaction-evaluation.md) §2.

| Field | Class | Decision record | Metrics | Tunnel response | Notes |
|---|---|---|---|---|---|
| Raw PAN, CVV/CVC, track 1/2, PIN block, expiry | S | MUST NOT | MUST NOT | MUST NOT | The Edge MUST strip these keys (§2.4) before storing `features`; a value detected by the Luhn guard is never stored. |
| `pan_hash` | E | MUST NOT | MUST NOT | MUST NOT | Used inside the Edge for velocity and `txn.related` linkage only. |
| Velocity counters (`pan_txn_count_*`, `pan_amount_sum_24h`) | E (key) / T (value) | MUST NOT | MUST NOT | value MAY appear in `features` | Aerospike keys contain `pan_hash`; the derived values are ordinary features. |
| `txn_id` → `txnRef` | D | `txnRef` | MUST NOT | yes | Max 128 printable ASCII, no spaces; PAN-like values are refused (§2.5). |
| `edgeAssessmentId` | D | yes | MUST NOT | yes | UUID minted per evaluation, including fail-closed `HOLD`. |
| `edgeNodeId`, `siteId` | D | yes | yes | yes | |
| Evaluation time | D (second precision) / T (millisecond precision) | `evaluatedAt` truncated to the second | window only | `evaluatedAt` with ms | Second precision in the cloud limits timing correlation. |
| `decision`, `score` | D | yes | counts | yes | |
| Triggered rule ids, rule version ids, bundle version and hash | D | yes | rule hit counts | yes | |
| `reasons` (free text) | T | MUST NOT | MUST NOT | yes (detail) | The cloud renders reason text from rule ids. |
| Evaluator, fail-closed code, latency | D | `evaluator`, `failClosedCode`, `latencyMs` | percentiles | `latencyUs` | |
| `merchant_id` | D / C | `merchantId` | per-merchant aggregates | yes | Cloud-onboarded master data. |
| `amount_cents` | T / A | MUST NOT | sums per merchant / currency | `amountMinor` | |
| `currency` | T / A | MUST NOT | breakdown key | yes | |
| `country_code` | T | MUST NOT | MUST NOT | `countryCode` | |
| `mcc`, `channel` | T | MUST NOT | MUST NOT | yes | |
| Customer / account / device / IP identifiers in features | T | MUST NOT | MUST NOT | in `features` (detail only) | Linkage via `txn.related` never returns the key value. |
| Names (cardholder, counterparty, beneficiary) | T | MUST NOT | MUST NOT | in `features`; `sanctionsHits[].matchedValue` | |
| Other caller features | T | MUST NOT | MUST NOT | in `features` (detail only) | |
| `ctrRequired`, `sanctionsHit` | D | yes | counts | yes | |
| Watchlist entry ids | D | `watchlistEntryIds` | MUST NOT | in `sanctionsHits` | Public-list ids (`ofac-sdn:36216`), never a name. |
| Typology codes | D | `typologyCodes` | MUST NOT | yes | |
| CTR day aggregate (cash total per card per business day) | T / F | MUST NOT | MUST NOT | `filing.extract` only | |
| Legal-hold flag | T | MUST NOT | MUST NOT | `legalHold` | Hold *ids* and references are C (they originate in the cloud). |

### 2.3 Filed documents

When a SAR, STR or CTR is **filed** (`POST /api/v1/sar/{id}/file`, `POST /api/v1/ctr/{id}/file`)
the Control Plane MAY store class-T data pulled by `filing.extract` inside the filed document
(`regulatory_filed_documents`). That is the regulatory record and the only exception to I2.
Drafts MUST NOT persist pulled data: the draft's transaction tables are rendered at view time.
Analyst narratives are workflow data; the Control Plane SHOULD warn when a narrative matches the
Luhn guard (§2.5) and MUST redact such matches before storing.

### 2.4 Feature bag hygiene (Edge)

Before writing `edge_evaluations.features` the Edge MUST:

1. drop keys `pan`, `pan_hash`, `card_number`, `pan_masked_full`, `cvv`, `cvv2`, `cvc`, `cvc2`,
   `track1`, `track2`, `track_data`, `pin`, `pin_block`, `expiry`, `exp_date`, `card_expiry`
   (`pan_hash` is kept in its own column, never in `features`);
2. drop any key that is not `snake_case` (`^[a-z][a-z0-9_]{0,63}$`), any non-scalar value, and any
   string value over 512 characters;
3. drop any string value the Luhn guard flags.

The tunnel item schemas enforce the same rules (`common.schema.json#/$defs/features`), so a cloud
that validates responses also catches an Edge that skipped them.

### 2.5 Luhn guard

A string is **PAN-like** when, after removing spaces and hyphens, it is 13 to 19 decimal digits and
passes the Luhn check. The schemas mark such fields with the extension keyword
`x-hokeka-luhnReject`. The Edge MUST apply the guard to `txn_id` on `/edge/evaluate` and MUST answer
`422` with `{"error":"TXN_REF_LOOKS_LIKE_PAN"}` without evaluating or storing anything when it
fires. Both sides MUST apply it to every field marked `x-hokeka-luhnReject` before sending and on
receipt.

## 3. Keys, sealing and replay protection

### 3.1 Keys (existing; no new key material)

| Key | Holder | Source | Used for |
|---|---|---|---|
| Control Plane Ed25519 signing key | Control Plane | `EDGE_BUNDLE_SIGNING_KEY` (`edge.bundle.signing-key`) | Signs every cloud→Edge HSE-1 envelope: bundle, Edge config, tunnel `hello.ack`, `request`, `cancel`, `credit`, `notify`, cloud-originated `error`. |
| Control Plane X25519 key | Control Plane | `EDGE_METRICS_INGEST_KEY` (`edge.metrics.ingest-key`) | Recipient key of every Edge→cloud envelope: metrics, decision records, tunnel `hello`, `response`, `chunk`, Edge-originated `error`. Its role widens from "metrics ingest key" to "Control Plane ingest key"; the variable name is kept. |
| Edge Ed25519 / X25519 keys | Edge Node | identity file (`EDGE_IDENTITY_FILE`), generated at first boot; public halves pinned at activation | Edge signs Edge→cloud envelopes; cloud seals cloud→Edge envelopes to the Edge X25519 key. |
| mTLS client certificate | Edge Node | `EDGE_MTLS_KEYSTORE` | Transport identity; CN = `edgeId`. |

The Edge pins the Control Plane public keys from `hokeka.controlplane.signing-public-key`
(`CONTROLPLANE_ED25519_PUBLIC_KEY`) and `hokeka.controlplane.encryption-public-key`
(`CONTROLPLANE_X25519_PUBLIC_KEY`).

### 3.2 HSE-1 contexts

HSE-1 (X25519 ECDH → HKDF-SHA256 → ChaCha20-Poly1305, Ed25519 signature over the whole envelope;
wire format in `HokekaSecureEnvelope`) binds a context string into the HKDF info and the AEAD AAD.
This contract adds:

| Purpose | Context bytes | Direction |
|---|---|---|
| Decision record batch | `hokeka.decision.records` | Edge → cloud |
| Edge config document | `hokeka.edge.config` | cloud → Edge |
| Sanctions/PEP list manifest (§6.4) | `hokeka.sanctions.list` | cloud → Edge |
| Sanctions/PEP list chunk (§6.4) | `hokeka.sanctions.list.chunk` | cloud → Edge |
| Tunnel frame of type `<t>` | `hokeka.tunnel.<t>` (e.g. `hokeka.tunnel.request`, `hokeka.tunnel.hello.ack`) | per frame type |
| Metrics report v2 | `hokeka.metrics.report` (unchanged; v1 and v2 are told apart by the payload's `v`) | Edge → cloud |

Existing contexts (`hokeka.rules.bundle`, `hokeka.metrics.report`, `hokeka.edge.activation`) are
unchanged.

### 3.3 Signatures

The HSE-1 Ed25519 signature **is** the request signature the plan calls for: a `request` frame is
sealed with the Control Plane signing key, and the Edge MUST verify it against the pinned key (as
`SealedEnvelopeCodec.open` does) before parsing the body. There is no second, detached signature.
A frame that fails `HokekaSecureEnvelope.open` (bad magic, version, length, signature or AEAD tag)
MUST be dropped and answered with `error SEAL_INVALID` when it carried a request id, and the
receiver SHOULD close the session after 3 seal failures in 60 s (close code 4401).

### 3.4 Replay envelope

Every sealed payload, including every tunnel frame, is the unchanged channel replay envelope
`{nonce, issuedAt, edgeId, payload}` (edge-channel-contract §4). The receiver MUST reject when the
nonce was already seen (bounded LRU, at least 10 000 entries: `ReplayGuard` / `EdgeReplayGuard`) or
`|now − issuedAt| > 300 s`. `edgeId` is the sending node for Edge→cloud payloads and the addressed
node for cloud→Edge payloads; the receiver MUST compare it with the mTLS identity (cloud) or its
own id (Edge).

## 4. Decision record v2

Schema: [`contracts/decision-record-v2.schema.json`](contracts/decision-record-v2.schema.json); batch:
[`decision-record-batch-v2.schema.json`](contracts/decision-record-batch-v2.schema.json); acknowledgement:
[`decision-record-ack-v2.schema.json`](contracts/decision-record-ack-v2.schema.json).

### 4.1 Fields

| Field | Req. | Type / rule |
|---|---|---|
| `v` | yes | `2` |
| `edgeAssessmentId` | yes | lowercase UUID; primary key in both stores |
| `txnRef` | no | §2.2; absent when the PSP sent no `txn_id` |
| `edgeNodeId`, `siteId` | yes | node that evaluated; its site |
| `evaluatedAt` | yes | RFC 3339 UTC, **truncated** (not rounded) to the second |
| `decision` | yes | `ALLOW` / `ALERT` / `HOLD` / `BLOCK` |
| `score` | yes | integer ≥ 0 (sum of triggered rule scores) |
| `triggeredRuleIds`, `ruleVersionIds` | yes | unique integer arrays, ≤ 512 |
| `bundleVersion` | yes | `-1` before the first verified bundle |
| `bundleHash` | iff `bundleVersion ≥ 0` | `sha256:<hex>` manifest hash (`EdgeRuleCompiler.manifestHash`) |
| `evaluator` | yes | `native` / `java`; `none` iff `failClosedCode` is present |
| `failClosedCode` | no | `NODE_NOT_AUTHORIZED`, `NO_BUNDLE`, `FEATURE_STORE_UNAVAILABLE`, `LEDGER_UNAVAILABLE`, `EVALUATOR_FAULT`; present ⇒ `decision = HOLD`, no triggered rules |
| `latencyMs` | yes | integer ms, 0..600 000 |
| `merchantId` | no | cloud master-data id |
| `ctrRequired`, `sanctionsHit` | yes | booleans |
| `watchlistEntryIds` | iff `sanctionsHit` | ≥ 1 public-list ids when `sanctionsHit = true`, none otherwise |
| `typologyCodes` | no | `^[A-Z][A-Z0-9_]{1,63}$`, ≤ 64 |

The schema is **closed** (`additionalProperties: false`). The Edge MUST validate each record
against it before writing it to `edge_outbox`; a record that fails is a defect, is not shipped,
and increments `ledger.writeFailures`. The Control Plane MUST reject (per record, code
`FORBIDDEN_FIELD` or `SCHEMA_INVALID`) any record with an unknown member and MUST NOT store any part
of it.

### 4.2 Transport

- `POST /api/v1/edge/assessments`, mTLS, `Content-Type: application/octet-stream`. Body: the batch
  (`{v:2, edgeId, siteId, outboxSeqFrom?, outboxSeqTo?, records[1..500]}`) wrapped in the replay
  envelope and HSE-1 sealed (context `hokeka.decision.records`). Header `X-Hokeka-Edge-Id` as today
  (a cross-checked hint only, `EdgeIdentityResolver`).
- The Control Plane MUST authorise the node exactly as `/edge/bundle` does (verified certificate
  identity, node `ACTIVE`), MUST require `batch.edgeId` = certificate CN = every
  `record.edgeNodeId` (else per-record `NODE_MISMATCH`) and `siteId` = the node's registered site
  (else `UNKNOWN_SITE`).
- Responses: `200` with the acknowledgement; `403` (identity / lifecycle, no body detail); `400`
  (envelope cannot be opened or is not a v2 batch); `413` (over `edge.metrics.max-payload-bytes`,
  which this endpoint shares; default 256 KiB, so the shipper MUST size batches to fit).
- Idempotent on `edgeAssessmentId`: a byte-identical re-send counts as `duplicates`. The same id
  with any differing field is `CONFLICT`; the cloud keeps the first record.

### 4.3 Outbox delivery (Edge)

The record is written to `edge_outbox` in the same local transaction as the `edge_evaluations`
row. If that transaction fails and `ledger.fail-closed=true` (default), the evaluation answers
`HOLD` with `failClosedCode = LEDGER_UNAVAILABLE`, and that record is retried in memory until the
ledger is writable. The shipper drains the outbox in `seq` order, at least once, and marks rows
`shipped_at` only on `200`. Rejected records (`rejected[]`) are marked shipped with their code and
counted; they are not retried. `401`/`403` marks the node unauthorised (as `EdgeAssessmentShipper`
does today); other failures retry with jittered backoff (1 s → 60 s).

### 4.4 Cloud processing

The Control Plane stores the record in `assessments` (decision-record columns added in R7) and
raises alerts and cases from it (R7): `ALERT`/`HOLD`/`BLOCK`, `sanctionsHit` and `ctrRequired`
drive the same workflow the cloud pipeline drove before, referencing the transaction by id only
(I4). It MUST NOT call back into the tunnel to enrich a record at ingest time. Decision records
follow PSP retention (§8.4).

### 4.5 v1 compatibility

The v1 JSON envelope (`EdgeAssessmentIngestRequest`) stays accepted until R12 (§12.3). A v1 body
MUST still carry an empty `featureSnapshot`; the Control Plane MUST reject a v1 body whose
`featureSnapshot` is non-empty.

## 5. Metrics report v2

Schema: [`contracts/metrics-report-v2.schema.json`](contracts/metrics-report-v2.schema.json).

- One report covers one closed UTC clock hour (`windowStart`/`windowEnd` hour-aligned) for one
  node. Sent sealed to `POST /api/v1/edge/metrics` (context `hokeka.metrics.report`). The Control
  Plane tells v2 from v1 by `v`.
- Contents: totals (evaluated, per decision, fail-closed, CTR-flagged, sanctions hits), fail-closed
  counts by code, rule hit counts, latency percentiles (µs), engine health, `byMerchant[]`
  (`merchantId` or `null` for "no merchant", currency, count, amount sum, per-decision counts),
  `byCurrency[]`, ledger outbox backlog and write failures.
- Breakdowns MUST be per merchant and per currency only, never per card, customer, account,
  device, IP or country. The schema is closed.
- Idempotent on `(edgeId, windowStart)`: a re-sent report **replaces** the stored one, so the Edge
  MAY re-send a window after a restart. The Edge SHOULD send each window within 5 minutes of its
  end and MUST keep unsent windows in the ledger (not memory) for at least 7 days.
- The cloud's `decision_stats_hourly` rollup is built from decision records; metrics v2 supplies
  amount sums, which decision records deliberately lack.

## 6. Edge config document

Schema: [`contracts/edge-config.schema.json`](contracts/edge-config.schema.json).

### 6.1 Content

| Section | Content | Default |
|---|---|---|
| `configVersion` | strictly increasing per PSP | — |
| `retention` | `years` in force (5..25), `effectiveSince`, optional `pendingChange` (approved reduction not yet in force) | 5 |
| `supportAccess` | `enabled`, `changedAt` | `false` |
| `crossPspIntelligence` | `optIn`, `changedAt`, `indicatorSetVersion` | `false` |
| `ctr` | `enabled`, `aggregation = CARD_BUSINESS_DAY`, `businessDayTimezone` (IANA), `reportingCurrency`, `thresholds[]` (minor units), `cashChannels[]`, optional `structuring {floorRatio (decimal string), minTransactions ≥ 2}`, `fx {asOf, base, rates{CCY: decimal string}}` | per PSP jurisdiction; structuring 0.80 / 2 |
| `limits[]` | `{limitId, scope CARD/MERCHANT/PSP, metric COUNT/AMOUNT, window PT1H/P1D/P7D/P30D, currency (AMOUNT), max, action, merchantId?, mcc?}` | none |
| `merchantBlocklist` | `version`, `entries[{merchantId, reasonCode, since}]` | empty |
| `sanctions` | `listVersion`, `listHash`, `publishedAt` of the list snapshot to screen with (§6.4) | `none` |
| `tunnel` | `enabled`, `opsAllowed[]`, `backfillAllowed`, `maxInflightPerSession` (≤ 16) | enabled, all read ops, backfill off, 16 |

FX rates are decimal strings (never JSON numbers) and mean "units of `base` per one unit of the
keyed currency". Amounts are integers in minor units.

### 6.2 Endpoint

- `GET /api/v1/edge/config`, mTLS, same authorisation as `/edge/bundle`. `200` body: the document
  in the replay envelope, sealed to the node's X25519 key, signed with the Control Plane key,
  context `hokeka.edge.config`, `application/octet-stream`. Headers: `ETag: "<configVersion>"`,
  `X-Edge-Config-Version: <configVersion>`, `Cache-Control: no-store`.
- `If-None-Match: "<configVersion>"` (also bare or `W/"…"`, as for the bundle) → `304`.
- The Edge polls it alongside the bundle (every `EDGE_BUNDLE_POLL_INTERVAL`, default 15 s) and
  SHOULD poll within 5 s of a `notify config.updated`.
- Any change to the inputs (retention approval coming into force, support-access toggle, CTR/FX
  update, blocklist change, sanctions list publish, tunnel settings) MUST increment
  `configVersion` and SHOULD trigger `notify config.updated` to every connected node of the PSP.

### 6.3 Edge processing

The Edge MUST verify-then-swap, exactly as for the bundle: open (signature, AEAD, replay,
`edgeId`), validate against the schema, check `pspId` equals its own, and refuse a document with a
lower `configVersion` than the one it holds (rollback protection). A refused document leaves the
previous one in force and does not advance the ETag. Every accepted document is appended to
`edge_config` (history) before use. A document with `retention.years < 5` cannot pass the schema;
an Edge MUST additionally clamp the effective retention to at least 5 years in code.

**CTR, FX and limits on evaluate (R10a).** For a cash transaction (request `cash_transaction`, or
`channel` ∈ `ctr.cashChannels`) the Edge converts the amount to `ctr.reportingCurrency` with
`ctr.fx`, adds it to the card's business-day total (`ctr.businessDayTimezone`) and sets
`ctrRequired` on the decision record of the transaction that takes the total to
`thresholds[reportingCurrency]` or above (typology code `CTR_REPORTABLE`). The Edge MUST NOT
under-report: when it cannot convert the amount (no or zero rate, no currency), has no threshold for
the reporting currency, or cannot read the card-day total, it answers `HOLD` with
`ctrRequired = true` and typology code `CTR_FX_UNAVAILABLE`, `CTR_CONFIG_INVALID` or
`CTR_AGGREGATE_UNAVAILABLE`. The optional `ctr.structuring` section (`floorRatio`,
`minTransactions`; defaults 0.80 and 2) drives cash structuring: `HOLD`, `CASH_STRUCTURING_24H`.
Each applicable `limits[]` entry is enforced with its `action` and typology code
`LIMIT_<limitId>`; a limit that cannot be evaluated (no FX to its currency, unreadable counters)
answers `HOLD` with `LIMIT_FX_UNAVAILABLE` / `LIMITS_UNAVAILABLE`. Details:
[`edge-transaction-evaluation.md`](edge-transaction-evaluation.md) §6.1.

Until the first document is accepted the Edge uses: no purge at all (§8.2), support access off,
cross-PSP off, CTR off, no limits, empty blocklist, tunnel per local settings. Rule evaluation is
unaffected.

Local settings can only narrow: `EDGE_TUNNEL_ENABLED=false` and `EDGE_TUNNEL_OPS` restrict the
tunnel further (§7.6.1); `EDGE_RETENTION_MIN_YEARS` (default 5) raises the floor, so effective
retention = max(config `years`, local floor).

### 6.4 Sanctions/PEP list document (R10b)

Schemas: [`contracts/sanctions-list-manifest.schema.json`](contracts/sanctions-list-manifest.schema.json),
[`contracts/sanctions-list-chunk.schema.json`](contracts/sanctions-list-chunk.schema.json),
[`contracts/sanctions-list-entry.schema.json`](contracts/sanctions-list-entry.schema.json).

The public sanctions/PEP list is distributed to the Edge so counterparties are screened locally; no
name ever goes to the cloud or to aml-microservice.

- **Source.** The Control Plane builds a snapshot from the same entities its OpenSanctions download
  pushes to aml-microservice (`SanctionsListDownloadService`), so both matchers screen the same names.
  Snapshots live in `edge_sanctions_snapshots` / `edge_sanctions_snapshot_chunks` (V267); the newest
  two READY ones are kept. A publish re-issues the Edge config of every PSP with Edge Nodes (new
  `configVersion`, `notify config.updated`).
- **Format.** Entries (`{id, name, aliases, type, pepLevel?}`, `id` = public watchlist entry id
  `<listId>:<entryId>`) as JSON lines, at most 5 000 per chunk, gzip-compressed. Chosen over one big
  document because the OpenSanctions "default" collection (≈1.2 M entities with PEPs) is ≈120–180 MB
  of JSON lines (≈30–45 MB gzip, ≈250 chunks of ≈150 KB); a single sealed envelope that size would be
  held in memory twice on both sides. The sanctions-only collection is ≈60 k entities (≈12 chunks).
  `edge.sanctions.snapshot.include-pep=false` (cloud) leaves PEP/RCA entries out, since they never
  change an Edge decision (§6.5).
- **Hashes.** `chunks[i].sha256` is the SHA-256 of chunk i's uncompressed bytes. `listHash` is the
  SHA-256 of the UTF-8 text of one line `<index>:<sha256>:<entryCount>\n` per chunk in index order.
  The Edge config's `sanctions.listHash` is that value (`listVersion "none"` and the hash of the empty
  string before any publish).
- **Endpoints.** mTLS, same authorisation as `/edge/config` (verified certificate, node `ACTIVE`,
  bare `403` otherwise), `Cache-Control: no-store`:
  - `GET /api/v1/edge/sanctions/list`: the newest snapshot's manifest, replay envelope, HSE-1 sealed to
    the node, context `hokeka.sanctions.list`. `ETag: "<listHash>"`, `X-Sanctions-List-Version`;
    `If-None-Match` (quoted, bare or weak) → `304`; `404` before the first publish.
  - `GET /api/v1/edge/sanctions/list/{listHash}/chunks/{index}`: one chunk (`data` = base64 of the
    gzip), context `hokeka.sanctions.list.chunk`, `ETag: "<chunk sha256>"`. Addressed by hash, so a
    node mid-download keeps fetching the snapshot it started even after a newer publish; `404` once
    that snapshot is pruned or for an unknown index.
- **Edge processing.** The Edge fetches when the config in force names a `listHash` it does not hold
  (on every config poll and right after a newly accepted config). It MUST open each document like the
  config (signature, AEAD, replay, `edgeId`), validate it against its schema, require manifest
  `listHash` = config `sanctions.listHash` and re-derive it from the chunk lines, and require every
  chunk's `listHash`, `index`, uncompressed `sha256` and `entryCount` to match its manifest line.
  Verified chunks are stored on the site ledger (`edge_sanctions_lists`, `edge_sanctions_list_chunks`,
  ledger V3) as they arrive; the list is used only after the whole stored list was re-verified, and
  the previous list stays in force until then. After a restart the node restores the stored list
  (re-verified the same way) without the Control Plane.

### 6.5 Edge sanctions screening and merchant blocklist (R10b)

- **Matcher.** The aml-microservice matcher, ported line for line: NFD normalisation, marks
  stripped, lower case, non `[a-z0-9 ]` to space; score `max(Jaccard of tokens, 1 − levenshtein/maxLen)`;
  best of name and aliases; kept at or above the similarity threshold (default 0.8, same as
  aml-microservice); rounded to 4 decimals; top 5, equal scores by entry id.
  [`contracts/parity/sanctions-matcher-vectors.json`](contracts/parity/sanctions-matcher-vectors.json)
  is generated from aml-microservice and asserted by both implementations. The Edge indexes the list
  in memory with a lossless candidate filter, so screening never changes a result and stays well
  under a millisecond per name for lists of 100 k entries.
- **Fields.** `counterparty_name` and `cardholder_name` (`matchedOn COUNTERPARTY_NAME`),
  `beneficiary_name` (`BENEFICIARY_NAME`), when present as non-blank strings. No type filter.
- **Actions.** A match on a sanctions-list entry (no `pepLevel`) with score ≥ 0.95 (aml-microservice
  `FLAGGED`, the cloud's confirmed-match threshold) is a confirmed hit: the decision becomes `BLOCK`.
  A sanctions-list match below 0.95 is a potential hit: the decision becomes at least `HOLD`. Both set
  `sanctionsHit = true` and list the entry ids in `watchlistEntryIds`; the full match
  (`sanctionsHitDetail`, with `matchedValue`) stays in `edge_evaluations.sanctions_hits`. A PEP/RCA
  match is never a sanctions hit and never changes the decision; it only adds a local reason.
- **Merchant blocklist.** A `merchant_id` on the config's `merchantBlocklist` makes the decision
  `BLOCK` (no `sanctionsHit`: the cloud already knows). The cloud adds a `SANCTIONED` entry when
  merchant screening (onboarding, update, periodic rescreen) finds a confirmed sanctions-list hit and
  lifts it when a rescreen is clear of sanctions-list matches; PEP-only matches count as clear.
  Entries with other reasons are only lifted by a person.
- **No list.** When the config names a published list that the node has not loaded yet, an evaluation
  carrying a name to screen is held (`EDGE_SANCTIONS_HOLD_WHEN_LIST_MISSING`, default true). Before
  the first config, or while the config says `none`, screening is off.
- **Fail-closed.** A fail-closed evaluation keeps its `HOLD` (§4.1); its hits are still recorded.
- **Combining with the R10a overlay and the rules.** Screening runs before limit usage is
  recorded. The final decision is the strongest of the rules, the CTR/structuring/limit overlay
  (§6.3) and screening (`BLOCK` > `HOLD` > `ALERT` > `ALLOW`). Limit usage is recorded with that
  final decision, so a transaction blocked by screening or the merchant blocklist uses no limit. The
  CTR card-day total still counts it, so the CTR is never under-reported. One decision record
  carries `sanctionsHit` / `watchlistEntryIds` and `ctrRequired` / `typologyCodes` together.

## 7. Edge Tunnel protocol

### 7.1 Transport

- Endpoint: `wss://edge.hokeka.com/api/v1/edge/tunnel` on a dedicated nginx vhost with
  `ssl_verify_client on`, TLS 1.3 only, `Upgrade`/`Connection` passthrough, forwarding
  `X-SSL-Client-Verify` and `X-SSL-Client-S-DN` exactly as for the HTTPS edge endpoints.
- Cloud: `spring-boot-starter-websocket`. Edge: JDK `java.net.http.WebSocket` with the existing
  `SecureChannel` `SSLContext` (no new dependency).
- WebSocket subprotocol: the Edge MUST offer `Sec-WebSocket-Protocol: hokeka-tunnel.v1`; the cloud
  MUST select it or refuse the upgrade.
- Upgrade request headers: `X-Hokeka-Edge-Id` (hint, cross-checked) and `X-Edge-Agent-Version`.
- The cloud MUST resolve the identity with `EdgeIdentityResolver` and require the node `ACTIVE`
  **before** completing the upgrade; otherwise it answers `403` with no body detail.
- Text frames only; one JSON frame per WebSocket message; at most 1 MiB (1 048 576 bytes) encoded.
  Binary frames, fragmented messages over 1 MiB and compression extensions are not used; a
  receiver MUST close with 4400 on a binary frame and 4413 on an oversize frame.
- Fallback transport (proxies that strip `Upgrade`): HTTP/2 long-poll at
  `POST /api/v1/edge/tunnel/poll` carrying the same frames as a JSON array. Optional; specified
  here only so it reuses the frame format. Not implemented in R4/R5.

### 7.2 Session lifecycle

```
Edge                                                   Control Plane
 │  TLS 1.3 + client cert, GET /api/v1/edge/tunnel  ───► EdgeIdentityResolver, node ACTIVE?
 │  ◄─── 101 Switching Protocols (hokeka-tunnel.v1)      (else 403, no upgrade)
 │  hello  (sealed, ctx hokeka.tunnel.hello)        ───► open, replay check, edgeId == cert CN,
 │                                                       siteId/pspId == registry, version select
 │  ◄─── hello.ack (sealed to edge, sessionId)           register in Redis tunnel:psp:{pspId}
 │  ◄─── notify hold.* (each unacknowledged hold, §7.10)
 │  ping every 20 s ───►   ◄─── pong                     either side: no frame for 60 s → dead
 │  ◄─── request / credit / cancel / notify
 │  response / chunk / error ───►
```

- The Edge MUST send `hello` within 10 s of the upgrade, else the cloud closes with 4408.
- `hello.protocolVersions` lists supported tunnel versions; the cloud selects the highest common
  one and echoes it in `hello.ack.protocolVersion`, or closes with 4426.
- `hello.edgeId` MUST equal the certificate CN; `hello.siteId` and `hello.pspId` MUST equal the
  Control Plane's records for that node, else 4403.
- `hello.ack.opsEffective` = `hello.opsEnabled` ∩ config `tunnel.opsAllowed`. The cloud MUST NOT
  send any other op on this session.
- One session per node: a second successful handshake for the same `edgeId` replaces the first,
  which the cloud closes with 4409.
- Reconnect: the Edge reconnects after any close or network failure with full-jitter exponential
  backoff from 1 s to 60 s, and only while it is authorised (`ActivationService.authorized()`).
  After a 4403 it MUST NOT reconnect until its next successful bundle poll.
- Heartbeat: the Edge sends `ping` every `heartbeatIntervalMs` (20 000); the receiver answers
  `pong` with `corrId` = the ping id. Either side treats the session as dead after `idleTimeoutMs`
  (60 000) without receiving any frame and closes it (4408).

**Close codes:**

| Code | Meaning | Edge reconnects? |
|---|---|---|
| 1000 | normal | yes |
| 4000 | Control Plane instance going away (deploy) | yes, immediately with jitter ≤ 5 s |
| 4400 | malformed frame / binary frame / bad JSON | yes, with backoff |
| 4401 | repeated seal or replay failures | yes, with backoff |
| 4403 | node not ACTIVE, identity/site/PSP mismatch, tunnel disabled in config | not until next bundle poll succeeds |
| 4408 | hello timeout or idle timeout | yes |
| 4409 | replaced by a newer session of the same node | no (the newer session is live) |
| 4413 | frame over 1 MiB | yes, with backoff |
| 4426 | no common protocol version | no (needs an upgrade) |
| 4500 | internal error | yes, with backoff |

### 7.3 Frame envelope

Schema: [`contracts/tunnel/frame.schema.json`](contracts/tunnel/frame.schema.json).

```json
{ "v": 1, "type": "request", "id": "<uuid>", "corrId": "<uuid>", "ts": 1792142047000,
  "nonce": "<22-char base64url>", "sealed": "<standard base64 of HSE-1 bytes>" }
```

| `type` | Direction | Sealed | `corrId` | Body schema |
|---|---|---|---|---|
| `hello` | E→C | yes | none | `tunnel/hello.schema.json` |
| `hello.ack` | C→E | yes | hello id | `tunnel/hello-ack.schema.json` |
| `request` | C→E | yes | none | `tunnel/request.schema.json` |
| `response` | E→C | yes | request id (or notify id for `hold.ack`) | `tunnel/response.schema.json` |
| `chunk` | E→C | yes | request id | `tunnel/response.schema.json` |
| `cancel` | C→E | yes | request id | `tunnel/cancel.schema.json` |
| `credit` | C→E | yes | request id | `tunnel/credit.schema.json` |
| `notify` | C→E | yes | none | `tunnel/notify.schema.json` |
| `error` | both | yes | request id, or none for a session-level error | `tunnel/error.schema.json` |
| `ping` | both (Edge initiates) | **no** | none | — |
| `pong` | both | **no** | ping id | — |

`ts` is epoch milliseconds at send. `ping`/`pong` carry only `v`, `type`, `id`, `ts` (and `corrId`
for `pong`); they carry no data and are unsealed. An unknown `type` MUST be answered with
`error UNSUPPORTED_FRAME` and otherwise ignored.

### 7.4 Sealed plaintext and frame binding

Schema: [`contracts/tunnel/sealed-plaintext.schema.json`](contracts/tunnel/sealed-plaintext.schema.json).

`sealed` = base64(HSE-1 seal(context `hokeka.tunnel.<type>`, plaintext)), where plaintext is:

```json
{ "nonce": "<same as outer nonce>", "issuedAt": "<RFC 3339, same instant as outer ts>",
  "edgeId": "acme-eu-1",
  "payload": { "frame": { "type": "request", "id": "<uuid>", "corrId": "<uuid>", "sessionId": "<uuid>" },
               "body": { … } } }
```

The receiver MUST, in order: (1) check the outer frame against its schema and the 1 MiB limit;
(2) open the HSE-1 envelope with the context derived from the outer `type` and the pinned peer
key; (3) run the replay check (§3.4); (4) require `payload.frame.type`, `id` and `corrId` to equal
the outer header, `nonce` to equal the outer `nonce`, `issuedAt` to equal the outer `ts` to the
millisecond, and `sessionId` to equal the live session's id (absent only in `hello`); any mismatch
is `BINDING_MISMATCH`; (5) validate `body` against the body schema. Only then may the body be
acted on. The outer header is therefore a routing hint; authority comes from the sealed plaintext.

### 7.5 Requests

Schema: [`contracts/tunnel/request.schema.json`](contracts/tunnel/request.schema.json).

```json
{ "op": "txn.search", "params": { … }, "deadlineMs": 8000, "page": { "limit": 50, "cursor": "…" },
  "actor": { "kind": "PSP_USER", "userId": "u-4512", "role": "COMPLIANCE_OFFICER", "pspId": 12,
             "purpose": "CASE_INVESTIGATION", "caseId": "CASE-2026-0412" },
  "auditId": "<uuid>" }
```

- `params` MUST validate against the op's params schema; unknown params are `INVALID_PARAMS` with
  `param` = the JSON pointer (never the value).
- `actor.kind`: `PSP_USER` (a user of this PSP), `PLATFORM_USER` (Hokeka staff; requires support
  access, §9), `SYSTEM` (`userId = system:<job>`, only for `LEDGER_BACKFILL`,
  `LEDGER_OPERATIONS`, `SAR_FILING`, `CTR_FILING` purposes).
- `actor.purpose` is one of `ALERT_REVIEW`, `CASE_INVESTIGATION`, `SAR_FILING`, `CTR_FILING`,
  `TRANSACTION_MONITORING`, `CUSTOMER_REVIEW`, `NETWORK_ANALYSIS`, `DATA_EXPORT`, `SUPPORT_ACCESS`,
  `LEDGER_BACKFILL`, `LEDGER_OPERATIONS`.
- The Edge MUST refuse with `TENANT_MISMATCH` when `actor.pspId` ≠ its own PSP, and with
  `SUPPORT_ACCESS_DISABLED` when `actor.kind = PLATFORM_USER` and its current config has
  `supportAccess.enabled = false`.
- **Deadline.** `deadlineMs` is a budget measured from the frame's `issuedAt`. Per-op maxima are in
  §7.6. The Edge MUST set the ledger statement timeout to the remaining budget minus 250 ms, and
  answer `DEADLINE_EXCEEDED` when it runs out. The cloud waits `deadlineMs + 1000 ms`, then answers
  its caller `504 EDGE_TIMEOUT` and sends `cancel {reason: DEADLINE}`.
- **Concurrency.** At most `maxInflight` (≤ 16) requests per session. The cloud MUST NOT exceed it;
  the Edge MUST answer an excess request with `EDGE_BUSY`.

#### Paging and cursors

Ordering for every list op is **(`evaluatedAt` desc, `edgeAssessmentId` desc)**, where
`edgeAssessmentId` compares as its lowercase string. `page.limit` ≤ 200. The cursor is
base64url (no padding) of [`tunnel/cursor.schema.json`](contracts/tunnel/cursor.schema.json):
`{v:1, k:[evaluatedAt, edgeAssessmentId] of the last item returned, q: SHA-256 hex of the RFC 8785
canonical JSON of params}`. It is deliberately a documented keyset rather than an opaque token, so
the Control Plane can mint a site cursor from the last item it consumed when merging sites
(§7.13). The Edge MUST answer `INVALID_CURSOR` when `q` does not match the request's params or the
cursor does not decode. Keyset paging is stable under concurrent inserts (new rows sort before the
cursor and are not returned on later pages) and under purges (purged rows simply disappear).

### 7.6 Operations

Allowlist, enforced on the Edge. Any other `op` is `OP_NOT_ALLOWED`.

| Op | Params schema | Item schema | Limits | Deadline max (default) | Paged | Writes |
|---|---|---|---|---|---|---|
| `txn.search` | `ops/txn.search.params` | `ops/txn.search.item` (summary) | `to − from` ≤ 93 days; `page.limit` ≤ 200 | 8 s (8 s) | yes | no |
| `txn.get` | `ops/txn.get.params` | `ops/txn.get.item` (detail) | exactly one of `edgeAssessmentId` / `txnRef` | 8 s (5 s) | no | no |
| `txn.batchGet` | `ops/txn.batchGet.params` | `ops/txn.batchGet.item` (detail) | 1..200 distinct ids | 8 s (8 s) | no | no |
| `txn.related` | `ops/txn.related.params` | `ops/txn.related.item` | `windowDays` 1..180 (30), `depth` 1..2 (1), `page.limit` ≤ 200 | 8 s (8 s) | yes | no |
| `filing.extract` | `ops/filing.extract.params` | `ops/filing.extract.item` | 1..200 ids | 30 s (15 s) | no | no |
| `export.stream` | `ops/export.stream.params` | `ops/export.stream.item` (summary/projection) | span ≤ 366 days; `maxRows` ≤ 1 000 000 (100 000); chunks ≤ 500 items | 300 s (300 s) | credit | no |
| `ledger.backfill` | `ops/ledger.backfill.params` | `ops/ledger.backfill.item` | ≤ 500 rows per BATCH | 60 s per phase (60 s) | no | **insert-only** |
| `ledger.health` | `ops/ledger.health.params` | `ops/ledger.health.item` | none | 8 s (3 s) | no | no |

Op semantics:

- **`txn.search`** filters on `from`/`to` (required, inclusive/exclusive: `from ≤ evaluatedAt < to`),
  `merchantId`, `decisions[]`, `ruleId` (triggered), `txnRef` (exact), `minScore`, `ctrRequired`,
  `sanctionsHit`. Items are summaries (no `features`, no `reasons`). The Edge MAY return
  `incomplete: true, incompleteReason: SCAN_LIMIT` with a `nextCursor` when it hit an internal scan
  budget before filling the page.
- **`txn.get`** returns one detail item, or `NOT_FOUND`. By `txnRef` it uses the site's unique
  index.
- **`txn.batchGet`** returns detail items in request order and lists absent ids in `missing[]`.
- **`txn.related`** returns evaluations linked to the anchor by the requested link types, where
  the Edge resolves the key internally: `CARD` = `pan_hash`, `MERCHANT` = `merchant_id`, `DEVICE` =
  `features.device_id`, `ACCOUNT` = `features.account_id` or `features.customer_id`, `IP` =
  `features.ip_address`. Items carry `{link, hop, viaEdgeAssessmentId, transaction: summary}` and
  MUST NOT carry the key value. Depth-2 expansion stops at the page limit with
  `incomplete: true, incompleteReason: DEPTH_LIMIT`. Linkage is site-local.
- **`filing.extract`** returns `{kind: TRANSACTION, transaction: detail}` per id and, when
  `kind = CTR`, one `{kind: CTR_AGGREGATE, …}` per anchor: the card's cash total for the anchor's
  business day (config `ctr.businessDayTimezone`), converted to `reportingCurrency` with the config
  FX in force, the threshold, the count and the ids that make it up, plus the `configVersion` used.
- **`export.stream`** streams summaries (projected to `columns` if given; `edgeAssessmentId`,
  `evaluatedAt`, `siteId`, `decision` always present) as `chunk` frames under credit (§7.8). The
  last chunk carries `last: true` and `totalRows`.
- **`ledger.backfill`** imports legacy rows (§11). Insert-only, idempotent on `edgeAssessmentId`;
  never updates or deletes; rows get `origin = BACKFILL`, `evaluator = cloud-legacy`. Allowed only
  when the config has `tunnel.backfillAllowed = true`, else `BACKFILL_NOT_ALLOWED`; actor MUST be
  `SYSTEM` / `LEDGER_BACKFILL`.
- **`ledger.health`** returns one item: ledger status, schema version, row counts and range,
  monthly partitions, outbox backlog, retention and last purge, holds (with `activeHoldDigest`),
  storage, write failures. Counts and timestamps only.

#### 7.6.1 PSP controls

`EDGE_TUNNEL_ENABLED` (default `true`) disables the tunnel entirely when `false`: the node never
connects and the Console shows its site as offline. `EDGE_TUNNEL_OPS` (comma-separated op names;
unset = all) narrows the ops the node serves; `hello.opsEnabled` advertises the result. The
effective set is implemented ∩ `EDGE_TUNNEL_OPS` ∩ config `tunnel.opsAllowed`. The Edge MUST
enforce it per request even if the cloud should never send a disallowed op.

### 7.7 Responses

Schema: [`contracts/tunnel/response.schema.json`](contracts/tunnel/response.schema.json).

```json
{ "op": "txn.search", "status": "OK", "seq": 0, "last": true, "items": [ … ],
  "nextCursor": "…", "incomplete": false }
```

- A request is answered by **either** one or more `response` frames (`seq` 0, 1, …) ending with
  `last: true`, **or** a single terminal `error` frame (possibly after some responses). The Edge
  MUST split a page across several `response` frames when it would not fit 1 MiB; `nextCursor`,
  `missing` and `totalRows` appear only on the `last` frame.
- `export.stream` uses `chunk` frames instead of `response` frames, same body.
- `items` MUST validate against the op's item schema. The cloud SHOULD validate every item and
  MUST discard (and log the defect, without the payload) any item that fails; a failing item is
  never forwarded to the Console. This is the cloud-side backstop for the "never returns
  `pan_hash`" guarantee.
- Item ordering follows §7.5.

### 7.8 Credit and backpressure

For `export.stream` the Edge starts with `initialCredit` (1..16, default 4) chunks. Each `credit
{grant: n}` (1..64) adds `n`. The Edge MUST NOT send a chunk while outstanding credit is 0 and
MUST send the last chunk only within credit. The cloud grants credit as it forwards chunks to the
Console response stream (it never buffers more than its outstanding grant). If no credit arrives
for 60 s the Edge ends the stream with `error STREAM_STALLED`. Credit for an unknown or finished
request is ignored; a grant that would exceed 1 024 outstanding chunks is `CREDIT_VIOLATION`.

### 7.9 Cancel

`cancel {reason}` (`CLIENT_GONE`, `DEADLINE`, `SUPERSEDED`, `SUPPORT_ACCESS_REVOKED`, `SHUTDOWN`)
names the request in `corrId`. The Edge MUST stop work within 1 s, cancel the running statement,
send no further `response`/`chunk` for it, and SHOULD send `error CANCELLED`. Cancel is idempotent.
The cloud MUST send `cancel` when the Console client disconnects, on its own deadline, and to every
in-flight `PLATFORM_USER` request of a PSP whose support access is switched off.

### 7.10 Notify

Schemas: [`contracts/tunnel/notify.schema.json`](contracts/tunnel/notify.schema.json) and `tunnel/notify/*`.

| `type` | Body | Edge action | Ack |
|---|---|---|---|
| `config.updated` | `configVersion` | fetch `/edge/config` within 5 s | none |
| `bundle.updated` | `bundleVersion` | poll `/edge/bundle` within 5 s | none |
| `hold.place` | `holdId`, `placedAt`, `placedBy`, `reasonCode` (CASE/SAR/STR/CTR/REGULATOR_REQUEST/LITIGATION), `reference`, `scope` (`edgeAssessmentIds[≤1000]` **or** `merchantId + from + to`) | insert into `edge_legal_holds`, mark matching rows | **required** |
| `hold.release` | `holdId`, `releasedAt`, `releasedBy`, `reasonCode` | close the hold | **required** |

Hold notifies are acknowledged with a `response` frame, `corrId` = the notify frame id,
`op: "hold.ack"`, one item `{holdId, state: APPLIED|RELEASED|UNKNOWN_HOLD, matchedRows,
activeHoldDigest}` ([`notify/hold.ack.item.schema.json`](contracts/tunnel/notify/hold.ack.item.schema.json)).
Holds are idempotent on `holdId`. The cloud is the source of truth for holds: it MUST re-send every
unacknowledged hold notify after each `hello.ack`, and SHOULD compare `activeHoldDigest`
(SHA-256 hex of the sorted, newline-joined active hold ids) from acks and `ledger.health` with its
own set and re-send on divergence. A hold MUST be delivered to every site of the PSP; a merchant-
range hold also covers rows inserted later inside the range.

### 7.11 Errors

Schema: [`contracts/tunnel/error.schema.json`](contracts/tunnel/error.schema.json). Body
`{code, httpStatus, message, retryable, retryAfterMs?, lastSeenAt?, siteId?, param?}`. `message` is
operator text and MUST NOT contain any transaction value. The same body is the Control Plane's
JSON error to the Console on tunnel-backed endpoints.

| Code | HTTP | Retry | Raised by | Meaning |
|---|---|---|---|---|
| `INVALID_PARAMS` | 400 | no | Edge / cloud | params fail the op schema or a §7.6 limit (`param` = pointer) |
| `INVALID_CURSOR` | 400 | no | Edge / cloud | cursor undecodable, or `q` mismatch |
| `PAGE_LIMIT_EXCEEDED` | 400 | no | Edge / cloud | `page.limit` > 200, ids > 200, export span > 366 d |
| `UNSUPPORTED_FRAME` | 400 | no | both | unknown frame `type` |
| `CREDIT_VIOLATION` | 400 | no | Edge | credit overflow |
| `SEAL_INVALID` | 401 | no | both | HSE-1 open failed |
| `REPLAY_DETECTED` | 401 | no | both | nonce seen or skew > 300 s |
| `BINDING_MISMATCH` | 401 | no | both | sealed frame binding ≠ outer header / session |
| `OP_NOT_ALLOWED` | 403 | no | Edge / cloud | op not in the effective set |
| `TENANT_MISMATCH` | 403 | no | Edge | `actor.pspId` ≠ Edge PSP |
| `SUPPORT_ACCESS_DISABLED` | 403 | no | Edge / cloud | platform actor without PSP consent |
| `BACKFILL_NOT_ALLOWED` | 403 | no | Edge | `tunnel.backfillAllowed = false` |
| `NOT_FOUND` | 404 | no | Edge | `txn.get` target absent |
| `STREAM_STALLED` | 408 | yes | Edge | no credit for 60 s |
| `BACKFILL_REJECTED` | 409 | maybe | Edge | backfill phase out of order or unknown `backfillId` |
| `FRAME_TOO_LARGE` | 413 | no | both | frame > 1 MiB |
| `UNSUPPORTED_VERSION` | 426 | no | both | protocol / schema version not supported |
| `EDGE_BUSY` | 429 | yes (`retryAfterMs`) | Edge / cloud | in-flight limit reached on every node of a site |
| `CANCELLED` | 499 | no | Edge | request was cancelled |
| `INTERNAL` | 500 | maybe | both | unexpected fault |
| `LEDGER_UNAVAILABLE` | 503 | yes | Edge | ledger down |
| `EDGE_OFFLINE` | 503 | yes | cloud | no live session for any needed site (`lastSeenAt` REQUIRED) |
| `DEADLINE_EXCEEDED` | 504 | yes | Edge | Edge ran out of budget |
| `EDGE_TIMEOUT` | 504 | yes | cloud | no answer within `deadlineMs + 1 s` |

### 7.12 Registry and routing (Control Plane)

- **Registry.** Redis hash `tunnel:psp:{pspId}`, field = `sessionId`, value =
  `{edgeId, siteId, cpInstance, inflight, connectedAt, lastPongAt}` (JSON), written on
  `hello.ack`, updated on pong and on every request start/finish, removed on close. Entries whose
  `lastPongAt` is older than 60 s MUST be ignored and SHOULD be reaped. `edge:lastseen:{edgeId}`
  keeps the last-seen instant for `EDGE_OFFLINE.lastSeenAt`.
- **Cross-instance routing.** A request is handled by the instance serving the Console call. If
  the chosen session lives on another instance, the request is published on Redis channel
  `tunnel:cp:{cpInstance}` and the frames come back on `tunnel:reply:{originInstance}`. Frames
  relayed through Redis MUST stay **sealed**: the session-holding instance forwards the Edge's
  sealed `response`/`chunk` bytes unopened, and only the origin instance opens them (any instance
  holds the Control Plane X25519 key). Redis therefore never sees plaintext transaction data.
  Pub/sub is not persisted; the cloud MUST NOT use Redis streams, lists or keys for payloads.
- **Node selection.** Within a site, pick the session with the lowest `inflight` (ties: most
  recent `lastPongAt`). A site whose every session is at `maxInflight` gives `EDGE_BUSY` for that
  site. `txn.get`/`txn.batchGet`/`filing.extract` by `edgeAssessmentId` go only to the site in
  the cloud decision record; everything else fans out to all sites of the PSP.
- **Retry.** If a session drops before the `last` frame, a read op MAY be retried once on another
  session of the same site within the remaining budget. `export.stream` and `ledger.backfill` are
  not retried automatically.

### 7.13 Multi-site merge

For a PSP with several sites the cloud fans the request out to every site and merges:

- **Lists** (`txn.search`, `txn.related`, `export.stream`): k-way merge by
  (`evaluatedAt` desc, `edgeAssessmentId` desc), take `limit`. The returned `nextCursor` is a
  **composite cursor** ([`tunnel/composite-cursor.schema.json`](contracts/tunnel/composite-cursor.schema.json)):
  for each site, the site cursor of the **last item consumed from that site** (minted by the cloud
  from that item's key), `START` if none consumed yet, or `DONE` if the site is exhausted; the
  JSON is HMAC-SHA256 signed by the cloud (`<b64url json>.<b64url mac>`), and a cursor with a bad
  MAC is `INVALID_CURSOR`. `export.stream` merges chunk-wise with per-site credit.
- **Gets** (`txn.get` by `txnRef`, `txn.batchGet`): union; an id is `missing` only if missing on
  every site that answered.
- **Partial results.** When some but not all sites answer (offline, timeout, busy), the cloud
  returns `200` with `incomplete: true` and `unavailableSites: [{siteId, code, lastSeenAt?}]`, and
  the composite cursor keeps those sites at their previous position so a later page can include
  them. When no site answers, the cloud returns the error of the "best" failure: `EDGE_BUSY` over
  `EDGE_TIMEOUT` over `EDGE_OFFLINE`.

### 7.14 Console-facing contract

Tunnel-backed cloud endpoints (`GET /api/v1/transactions`, `/{id}`, `/{id}/alerts`,
`/{id}/assessment`, `/{id}/compliance-links`, `POST /api/v1/transactions:batchGet`,
`GET /api/v1/transactions/{edgeAssessmentId}/related`, `GET /api/v1/transactions/export`) keep
their DTO shape, add `source: "EDGE"`, and wrap lists as
`{items, nextCursor, incomplete, unavailableSites}`. They MUST send `Cache-Control: no-store` and
MUST NOT be served from any cache. Errors use §7.11 bodies with the listed HTTP status. The export
is streamed to the client (`text/csv` or `application/x-ndjson`) and never written to disk.

Implementation notes (R6):

- **PSP scope.** A PSP user always reads its own PSP; a `pspId` parameter naming another PSP is
  `403 TENANT_MISMATCH` before anything is pulled. Platform staff MUST name `pspId` (else
  `400 INVALID_PARAMS`, `param: /pspId`) and give `supportReason` (parameter or
  `X-Support-Reason` header); the §9 gate applies and is audited either way.
- **Envelope.** Lists also carry `source`; batchGet carries `missing`. Items keep the Console's
  transaction names next to the contract names (`id` = `edgeAssessmentId`, `txnTs` =
  `evaluatedAt`, `amountCents` = `amountMinor`). `GET /{id}` adds the cloud `decisionRecord`
  (class D). List paging is cursor only (`page > 0` without a cursor is `INVALID_PARAMS /page`);
  the default window is the last 30 days.
- **Routing.** `/{id}` and batchGet go to the site in the PSP's decision record (one pull per site
  group); ids with no cloud record fan out. `related` goes to the anchor's site.
- **Legacy rows.** The source is chosen per PSP, never per row: a PSP with an Edge Node that ever
  activated is served from its Edge only (legacy rows become readable there after the §11
  back-fill; a legacy numeric id is translated to its reference id). A PSP with no activated node
  is served its legacy cloud rows with `source: "CLOUD_LEGACY"`, the same envelope, gate and
  access-log row. `related` is `403 OP_NOT_ALLOWED` for such a PSP.
- **Export completeness.** Sites already failed when the stream opens are named in
  `X-Edge-Incomplete` / `X-Edge-Unavailable-Sites`; NDJSON ends with one `{"@summary": {rows,
  incomplete, unavailableSites, edgeTotalRows}}` line; an incomplete CSV ends with a
  `# INCOMPLETE EXPORT: …` line; trailer fields repeat it for clients that accept trailers.
- **Workflow views.** Alert detail (`GET /alerts/{id}`), SAR detail
  (`GET /compliance/sar/{id}/detail`), case timeline and case network fill `transactionDetails` by
  a batchGet when viewed (`transactionDetails=false` skips it; the case network adds `txn.related`
  links with `related=CARD,DEVICE,…`) and report `transactionDetailsStatus`
  (`OK`/`PARTIAL`/`NONE`/`NOT_REQUESTED` or the §7.11 code) instead of failing the view.
- **Logs.** Response bodies are written through a wrapper whose `toString()` is fixed, so web-layer
  DEBUG/TRACE logging never prints pulled data.
- `GET /api/v1/edge-data/status` reports per site the tunnel state (registry), nodes and the
  `ledger.health` item (or the §7.11 code of a failed health pull); `GET /api/v1/edge-data/access-log`
  is paged (`page`, `size` ≤ 200, filters `from`, `to`, `op`, `actorKind`, `supportAccess`,
  `outcome`) and exportable at `/access-log/export` (CSV / NDJSON).

### 7.15 Audit

Every request MUST produce one row on each side, sharing `auditId` (minted by the cloud before
sending).

**Cloud `edge_data_access_log`** (written before sending, completed on the terminal frame):
`audit_id`, `psp_id`, `actor_kind`, `actor_user_id`, `actor_role`, `purpose`, `case_id`,
`alert_id`, `filing_ref`, `support_access` (bool), `support_reason`, `op`, `params_summary`
(only: time range, `merchantId`, `decisions`, `ruleId`, `minScore`, flags, id **count**, link
types, depth; never `txnRef` values or backfill rows), `params_digest` (SHA-256 of the JCS params),
`sites_contacted`, `sites_answered`, `item_count`, `bytes`, `outcome` (`OK`/`PARTIAL`/error code),
`requested_at`, `completed_at`. It MUST NOT store items. It is retained for at least the PSP's
retention period, is PSP-visible through `GET /api/v1/edge-data/access-log` (filtered to the
caller's PSP), and is never deletable by PSP or platform users.

**Edge `edge_access_log`**: `audit_id`, `received_at`, `completed_at`, `session_id`, `node_id`,
`op`, actor fields as received, `params_digest`, `outcome`, `item_count`, and the list of returned
`edge_assessment_id`s (local data, so the PSP can see exactly which records were read). The Edge
MUST write the row even when it refuses the request (with the error code). Retained with the
ledger's retention and purged by the same job.

### 7.16 No payload persistence (Control Plane)

The Control Plane MUST NOT log request params beyond §7.15, or any response body, at any log level;
MUST NOT put payloads in Kafka, Redis keys/streams, caches (Caffeine, Spring Cache, HTTP), Envers
audit tables, or exception messages; and MUST hold a response only for the life of the request
that pulled it. A test MUST assert this (the plan's N1 canary scans Postgres, Redis and logs for a
planted PAN hash, amount and `txnRef`, allowing hits only in filed documents).

## 8. Retention and legal holds

### 8.1 Setting

`psps.retention_years`: integer, default 5, `CHECK (retention_years BETWEEN 5 AND 25)`. Changed
through `PUT /api/v1/psps/{id}/data-retention` with maker-checker; every change is audited. An
**increase** takes effect on approval. A **reduction** takes effect no sooner than 30 days after
approval: until then the config document carries the old value in `retention.years` and the new
one in `retention.pendingChange`. Increasing retention cannot restore data already purged.

### 8.2 Edge purge

- Daily (default 02:00 site-local time) the Edge computes `cutoff = now − effective retention years`
  (§6.3) in UTC and drops every monthly `edge_evaluations` partition whose **upper bound** is ≤
  `cutoff`. Data is therefore kept for at least the retention period and at most one month longer.
- Before dropping a partition the Edge MUST copy every row covered by an active hold into
  `edge_held_evaluations` in the same transaction as the drop; held rows remain readable by every
  op (`legalHold: true`).
- `edge_access_log`, `edge_outbox` (shipped rows), `edge_purge_log` and `edge_config` history
  older than the cutoff are purged by the same job; `edge_purge_log` itself keeps at least the
  last 400 runs.
- Each run writes `edge_purge_log` (`run_at`, `cutoff`, `retention_years`, `config_version`,
  dropped partitions, held rows copied, duration, outcome), surfaced in `ledger.health`.
- The Edge MUST NOT purge evaluations until it has accepted at least one config document: a
  fresh or reinstalled node cannot know whether the PSP chose more than 5 years, and keeping data
  too long is recoverable while purging too early is not.
- A failed purge MUST be retried next day and MUST NOT partially drop a partition.
- One node per site purges. A run holds the PostgreSQL session advisory lock
  `hokeka.edge.purge:<ledger schema>` and re-checks `edge_purge_log` while holding it. The key names
  the schema because advisory locks are database-wide: ledgers of other sites in other schemas of
  the same database purge independently.

### 8.3 Holds

Holds (§7.10) override retention on both sides. Releasing a hold makes its rows purgeable at the
next run if they are past the cutoff and no other active hold covers them;
`edge_held_evaluations` rows are deleted then.

**Implementation notes (R9, Control Plane).** When holds are placed and released, which §7.10
leaves to the cloud:

| Workflow record | Placed | reasonCode | Release basis | Released with |
|---|---|---|---|---|
| SAR | when drafted (and by the reconciler for any unfiled SAR whose references are not yet covered) | `SAR` | filing time | `FILING_RETENTION_ELAPSED` once basis + `retention_years` has passed; `PLACED_IN_ERROR` when the SAR is deleted unfiled |
| Case | when the case is `ESCALATED` (reconciler) | `CASE` | resolution time when the case closes (cleared again if reopened) | `CASE_CLOSED` once basis + `retention_years` has passed; `PLACED_IN_ERROR` when the case is deleted |
| CTR deadline | at filing, over the aggregate's ids | `CTR` | filing time | `FILING_RETENTION_ELAPSED` |

A hold covers at most 1 000 ids (more ids become several holds), is recorded per site in
`legal_hold_site_acks`, and is unconfirmed until that site's `hold.ack` matches the last notify
(APPLIED for a place, RELEASED or UNKNOWN_HOLD for a release). Every unconfirmed hold is re-sent
after each `hello.ack` of the site, and every five minutes to connected sites that have not
confirmed it; a divergent `activeHoldDigest` re-sends the site's unconfirmed holds.

### 8.4 Cloud decision records and filed documents

- Daily, the Control Plane deletes decision records with `evaluated_at < now − retention_years`,
  **except** records referenced by an open alert, open case, unfiled SAR/STR/CTR, or active hold.
  Closed workflow records keep their own retention (at least `retention_years` from closure) and
  keep the decision records they reference until then.
- Filed SAR/STR/CTR documents are kept for at least `retention_years` from the filing date.
  (R9: `regulatory_filed_documents.retain_until`; a database trigger refuses UPDATE always and
  DELETE before `retain_until`. The document is AES-256-GCM encrypted with the platform field key
  and sealed by the SHA-256 of its plaintext, re-checked on every read.)
- `edge_data_access_log` is kept for at least `retention_years`.

## 9. Support access

- `psps.support_access_enabled`, default `false`, toggled by a PSP admin (Settings → Hokeka support
  access), audited, and carried to the Edge in config `supportAccess.enabled`.
- A `PLATFORM_USER` pull of a PSP's data REQUIRES, at the Control Plane: support access enabled
  for that PSP; `actor.purpose = SUPPORT_ACCESS`; a free-text `supportReason` of 10..500 characters
  supplied by the user for **each** pull (or each Console session of at most 60 minutes for one
  ticket); and the platform role. Otherwise `403 SUPPORT_ACCESS_DISABLED` without contacting the
  Edge.
- The Edge re-checks: it refuses a `PLATFORM_USER` request when its current config has support
  access off. (Double gating means a cloud bug cannot bypass PSP consent, at the cost of up to one
  config poll of lag when access is switched **on**.)
- Switching support access off MUST cancel in-flight platform requests (§7.9) and bump
  `configVersion` with `notify config.updated`.
- Support pulls are flagged `support_access = true` with the reason in both audit logs and are
  shown prominently in the PSP's access-log viewer.
- `SYSTEM` actors are not support access: they are limited to backfill, ledger operations and
  filing jobs triggered by a PSP user's filing action.

## 10. Cross-PSP intelligence

Opt-in per PSP (`psps.cross_psp_opt_in`, config `crossPspIntelligence.optIn`). Indicators are drawn
only from **filed** SARs of opted-in PSPs and are limited to merchant ids and platform-keyed HMAC
device tokens (HMAC-SHA256 under a platform secret that never leaves the cloud, over the device id
extracted at filing time). They are distributed as a sealed indicator set (version
`indicatorSetVersion`; transport specified with R10b) and matched on the Edge. Indicators MUST NOT
carry amounts, names, card data or the filing PSP's identity. Nothing in this section uses the
tunnel; no PSP can pull another PSP's data.

## 11. Legacy data backfill

One-off, per PSP, before the cloud transaction tables are dropped (R15, then R12).

1. **Preconditions.** Target site chosen by a platform admin and recorded per PSP (default: the
   PSP's only site; a multi-site PSP MUST choose one, because legacy rows have no site). Target
   ledger `ledger.health` = `OK`; tunnel online; the PSP's config has `tunnel.backfillAllowed =
   true` and `ledger.backfill` in `opsAllowed`; the PSP's local `EDGE_TUNNEL_OPS` allows it.
2. **Select.** Legacy rows with `evaluated_at ≥ now − retention_years` (older rows are purged, not
   copied), ordered by (`evaluated_at`, legacy id). Each row's `edgeAssessmentId` is the existing
   cloud assessment id if it has one, else UUIDv5 with namespace
   `6f1c2a5e-0b7d-5d43-9a8e-1f3c4b2a6d90` over `"<pspId>:<legacyTransactionId>"` (deterministic, so
   re-runs are idempotent). The cloud MUST also make sure a decision record exists for every row,
   so cloud workflow references stay resolvable.
3. **BEGIN** `{backfillId, sourceFrom, sourceTo, expectedRows, retentionCutoff}` → `state OPEN`.
4. **BATCH** `{backfillId, batchSeq, rows[≤500]}` in increasing `batchSeq`. The Edge inserts with
   `ON CONFLICT DO NOTHING`, sets `origin = BACKFILL`, `evaluator = cloud-legacy`, applies §2.4
   hygiene, and acknowledges `{accepted, duplicates, rejected[{legacyTransactionId, code}]}`.
   Rejections: `SCHEMA_INVALID`, `PAST_RETENTION`, `ID_CONFLICT` (same id, different content),
   `TXN_REF_CONFLICT` (txnRef already used by a different row). A batch MAY be re-sent; out-of-order
   or unknown `backfillId` is `BACKFILL_REJECTED`.
5. **COMMIT** `{backfillId, expectedRows, expectedIdDigest}` where the digest is SHA-256 hex over
   the sorted, newline-joined `legacyTransactionId`s sent. The Edge computes the same over the
   rows it holds for this `backfillId` and answers `VERIFIED` or `VERIFY_MISMATCH` with its
   `rowsTotal` and `idDigest`. On mismatch the cloud re-sends batches (idempotent) and commits
   again, or sends **ABORT**.
6. **Record.** The cloud stores the run (`ledger_backfill_runs`: psp, site, backfill id, counts,
   digests, state, timestamps; no row content).
7. **Purge.** The cloud purge migration (R12) MUST NOT run until every PSP with legacy data has a
   `VERIFIED` run. Rejected rows are listed (by legacy id only) for operator decision before purge.

`ledger.backfill` rows travel cloud → Edge and MAY carry `panHash`, amounts and features; the Edge
MUST NOT echo them in acknowledgements.

Implementation notes (R15):

- **Sources.** Card / cloud-ingest rows (`transactions` + `transaction_features` + the R8 cloud
  assessment) and multi-asset rows (`multi_asset_transactions` + `mobile_money_transaction_contexts`
  + `travel_rule_transfers` + `multi_asset_risk_signals`; the dependent stores travel as features
  of their multi-asset row). `legacyTransactionId` is `txn-<transactions.txn_id>` or
  `mat-<multi_asset_transactions.id>`; the minted id is UUIDv5 over `"<pspId>:<legacyTransactionId>"`
  (fixture `request--ledger-backfill-batch.json` is the vector). The carried id follows R8 / V264:
  the assessment's `edge_assessment_id`, else its own id. Rows are sent `TXN` first, then `MAT`,
  each by (evaluation time, id); legacy `TIMESTAMP` columns are read in
  `edge.backfill.legacy-time-zone` (default: the JVM zone that wrote them).
- **Mapping.** `iso_msg`, `emv_tags` and `customer_email` are never read; features go through the
  §2.4 rules in the cloud too (so the Edge strips nothing more, asserted by the edge-host
  `CloudBackfillFixturesIntegrationTest` on the shared `ledger.backfill.params--cloud-*.json`
  fixtures). A `pan_hash` that is not hash-shaped (raw PAN, `UNKNOWN`) is dropped. Values a
  contract field cannot hold travel as features: `amount_decimal` / `amount_currency` (asset codes,
  more decimals than the currency), `amount_minor_signed`, `legacy_merchant_ref`, `legacy_country`,
  `legacy_channel`. Decisions map REVIEW/FLAG → ALERT, SUSPEND → HOLD; scores are the
  assessment's, else the legacy model probability × 100, else the multi-asset risk score. Legacy
  rule names resolve to rule definition ids (PSP's own, then global); unresolved names and
  multi-asset risk signals become reasons.
- **Site routing (rows "whose site is ambiguous").** A platform admin records a default target site
  per PSP (`ledger_backfill_targets`; a single-site PSP defaults to its site, a multi-site PSP
  must name one). Per row: the site of its cloud decision record when that is a current site of
  the PSP; else the merchant's site when every Edge decision record of that merchant names the
  same one site (frozen at the PSP's first back-fill in `ledger_backfill_merchant_sites`); else
  (no records, records at two or more sites, multi-asset rows without a merchant) the default
  site. One run (backfillId) per receiving site.
- **Batches** are ≤ 500 rows and ≤ `edge.backfill.batch-bytes` (600 000) of row JSON, halved on
  `FRAME_TOO_LARGE`. The cursor, batch sequence and every acknowledgement are persisted before the
  next request (`ledger_backfill_runs` / `_batches` / `_rows`, V270: ids, counts, codes only), so a
  restart resumes at the next batch; a lost acknowledgement re-sends the same batch (all
  duplicates). Retryable errors back off (`retryAfterMs` honoured) and STALL after
  `max-consecutive-failures`; others FAIL the run with the code. ABORT is sent when the Edge began.
- **COMMIT digest.** `expectedRows` / `expectedIdDigest` cover the rows the Edge acknowledged
  holding (accepted or duplicate), not rejected rows: the Edge computes its digest over the rows it
  holds, so this is the only reading under which a run with any rejection can verify. Rejected rows
  are then listed separately and gate the purge (below). VERIFY_MISMATCH re-sends every batch up to
  `max-resend-rounds` (2), then FAILs.
- **Verification.** After COMMIT `VERIFIED` (equal count and digest), the cloud re-derives a
  deterministic sample (up to 200 held rows) from the legacy tables and compares it field by field
  with `txn.batchGet` (ids, txnRef, time, decision, score, merchant, amount, currency, mcc,
  country, channel, CTR flag, rule / version ids, reasons, canonical features, `origin = BACKFILL`,
  `evaluator = cloud-legacy`, site). Only then is the run `VERIFIED`. Rejections are triaged:
  `PAST_RETENTION` needs no copy; `ID_CONFLICT` / `TXN_REF_CONFLICT` against the Edge's own `LIVE`
  evaluation (a dual-posted transaction) are `PRESENT_ON_EDGE`; the rest wait for an operator
  decision (`EXCLUDED`, with a reason).
- **References.** Per acknowledged batch, in the same transaction: the cloud assessment of a held
  row becomes its decision record (`edge_assessment_id`, `site_id`, `evaluated_at`, `merchant_id`
  filled); a row without one gets a minted class-D decision record (`evaluator = cloud-legacy`);
  R8 workflow rows left without an Edge id (alerts, alert / case / SAR links and their Envers
  history, screening activities, findings) get the minted id, and rows with the id get the site.
- **Purge gate.** `BackfillCoverageService.requirePurgeAllowed(pspId)` (R12): refused unless the
  PSP has a `VERIFIED` run and every legacy row inside its retention is held or resolved in a
  `VERIFIED` run; rows ingested after a run's window need a top-up run. `purgeReadinessAll()` also
  counts legacy rows without a PSP, which no run can cover.
- **Control.** `/api/v1/psps/{pspId}/edge-backfill` (report; `/runs` start; `/runs/{id}/pause`,
  `/resume`, `/abort`; `/runs/{id}/rejections`, `/rejections/exclude`). Platform-initiated: start
  and resume are platform operators only, with the PSP's support access enabled and a 10..500
  character reason; pause, abort, row decisions and the report are also open to the PSP's own
  administrators, MLRO and compliance officers. Tunnel requests are always `SYSTEM` /
  `system:backfill` (never a platform user). Actions are audited (`EDGE_BACKFILL_*`) and every tunnel
  request writes its `edge_data_access_log` row. Nothing deletes cloud data.

## 12. Versioning and compatibility

### 12.1 Version fields

| Artifact | Version field | This spec |
|---|---|---|
| Tunnel frames | frame `v`; negotiated `protocolVersion` | 1 |
| Decision record | `v` | 2 (v1 = current JSON envelope) |
| Metrics report | `v` | 2 (v1 = `EdgeMetricsReport` record) |
| Edge config | `v` | 1 |
| Site / composite cursor | `v` | 1 |
| JSON Schemas | `$id` under `https://contracts.hokeka.com/edge/` | — |

### 12.2 Rules

- **Additive within a version:** a new optional field, a new op, a new notify type, a new error
  code, a new enum value. Anything else (removing or renaming a field, changing a type, tightening
  a limit, changing semantics) needs a new version number.
- **Data leaving the Edge is closed.** Decision records, metrics, and tunnel response items are
  validated against closed schemas; a receiver MUST reject unknown fields there (privacy guard),
  so adding a field to them is a coordinated change: cloud first, then Edge.
- **Requests to the Edge are closed.** Unknown params are `INVALID_PARAMS`; an unknown op is
  `OP_NOT_ALLOWED`. New ops reach a node only after it advertises them in `hello.opsEnabled`.
- **Control documents are open at the top level.** The Edge MUST ignore unknown top-level members
  of the config document and of `hello.ack`, and unknown notify types (acknowledge nothing, log
  once). Sections that exist are closed.
- **Unknown enum values in control data** (e.g. a new `reasonCode`) MUST be treated as the most
  conservative known value: for holds, as a hold.
- **Rollout order:** cloud accepts new version → Edges upgrade and send it → cloud stops accepting
  the old version. The cloud MUST accept the previous decision-record and metrics versions for at
  least 90 days after a new one is introduced.

### 12.3 Transition from dual-post

- Until R12: `/api/v1/transactions/ingest` and v1 assessment envelopes keep working; new Edges send
  v2 records.
- R10c removed the `HokekaAiDecisionClient` feature-bag POST to `/edge/decision` **before** R12,
  because it was a class-T leak. Neither the Edge client nor the Control Plane endpoint exists.
- R12 deletes the ingest path and v1 acceptance, gated on simulator parity sign-off (owner
  decision 7).

## 13. Conformance

- Schemas and fixtures: `docs/contracts/**`, validated by `py docs/contracts/validate.py`
  (stdlib only). The script asserts every valid fixture passes, every `*.invalid.json` fixture
  fails for the reason recorded in `tunnel/examples/expected-failures.json`, and every schema is
  exercised.
- BACKEND and edge-host tests MUST load the shared fixtures (plan "Contract fixtures") rather than
  copy them: frame codec round-trip, sealing per context, binding mismatch, replay, op allowlist
  and params validation, cursor stability, retention floor, purge with holds.
- `x-hokeka-luhnReject` is a Hokeka extension; generic JSON Schema validators ignore it, so each
  runtime MUST implement the guard (§2.5) explicitly.
- The N1 residency canary (plan, Simulator) is a release gate for I2.

---

## Appendix A: decisions this spec takes that the plan left open

Each item is a choice made here for the owner to confirm or overturn.

1. **No separate request signature.** The HSE-1 Ed25519 envelope signature is the request
   signature (§3.3).
2. **Every frame except ping/pong is sealed**, including `cancel`, `credit` and `error`, with a
   per-frame-type HSE-1 context `hokeka.tunnel.<type>`; the outer header is bound inside the
   sealed plaintext (§7.4).
3. **Existing keys reused:** the "metrics ingest" X25519 key becomes the Control Plane ingest key
   for every Edge→cloud payload; the bundle signing key signs every cloud→Edge payload (§3.1).
4. **Decision records are sealed** (context `hokeka.decision.records`), not plain JSON over mTLS,
   and are batched up to 500 per POST (§4.2).
5. **PAN-like `txn_id` gets `422` at `/edge/evaluate`** (no evaluation, nothing stored) rather
   than evaluating with the reference dropped (§2.5).
6. **Fail-closed records** carry `evaluator: "none"`, and `bundleHash` is omitted when
   `bundleVersion = -1` (§4.1).
7. **Metrics v2 are hourly and replace-on-resend**; per-merchant rows have no minimum-count
   (k-anonymity) suppression, so a merchant with one transaction in an hour reveals that amount to
   the cloud. Owner to confirm this is acceptable (§5).
8. **`pspId` is an integer** in all v2 payloads (v1 metrics used a string).
9. **Cursors are a documented keyset**, not opaque, so the cloud can merge sites; composite
   cursors are HMAC-signed by the cloud (§7.5, §7.13).
10. **Search window caps:** `txn.search` ≤ 93 days, `export.stream` ≤ 366 days, `maxRows` default
    100 000 (max 1 000 000), `filing.extract` deadline up to 30 s (the plan named only 8 s
    interactive and 300 s export).
11. **Tunnel items never carry a card identifier** (no masked PAN, no card token); card linkage is
    only through `txn.related` with `CARD`. Customer/device ids the PSP put in features are
    returned in detail views.
12. **`txn.related` link keys** are fixed feature names: `device_id`, `account_id`/`customer_id`,
    `ip_address` (§7.6).
13. **Hold notifies require an acknowledgement** and are re-sent after every reconnect; the cloud
    is the hold source of truth and reconciles with `activeHoldDigest` (§7.10).
14. **Support access is double-gated** (cloud and Edge), the reason is per pull or per ≤ 60-minute
    session for one ticket, and switching access off cancels in-flight pulls (§9).
15. **`ledger.backfill` is the one write op** (insert-only, `SYSTEM` actor, requires
    `backfillAllowed`); legacy rows go to one PSP-chosen site; deterministic UUIDv5 ids for rows
    without a cloud assessment id; COMMIT verification by count + sorted-id digest (§11).
16. **Purge granularity** is the monthly partition, so data lives up to one month past retention
    (§8.2); cloud decision records linked to open workflow are exempt until the workflow closes
    (§8.4).
17. **Local overrides can only narrow:** `EDGE_TUNNEL_ENABLED`, `EDGE_TUNNEL_OPS`,
    `EDGE_RETENTION_MIN_YEARS` (§6.3, §7.6.1).
18. **One session per node**, newest wins (close 4409); least-inflight node selection; read ops
    retried once on session loss (§7.2, §7.12).
19. **Cross-instance relay keeps frames sealed**, so Redis pub/sub never carries plaintext (§7.12).
20. **No purge before the first config document**; until then support access is off and CTR is
    off (no hard-coded thresholds) (§6.3, §8.2).
21. **WebSocket subprotocol** `hokeka-tunnel.v1` and the close-code table (§7.2) are new names.
22. **Travel Rule (VASP) exchange** remains an open owner decision (plan conflict 8); this
    contract does not cover it.
23. **Legacy rows are served per PSP, never mixed (R6):** Edge PSPs read only their Edge; PSPs with
    no activated Edge Node read their legacy cloud rows flagged `CLOUD_LEGACY` until R12 (§7.14).
24. **Back-fill COMMIT digest covers held rows (R15):** `expectedIdDigest` is computed over the
    rows the Edge acknowledged holding, so rejected rows do not make every COMMIT mismatch; they
    are triaged or decided by an operator and gate the R12 purge instead (§11).
25. **Back-fill site routing (R15):** decision-record site, else the merchant's single Edge site
    (frozen at the first back-fill), else the PSP's default target site (§11).
