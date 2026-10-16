# Edge data residency: implementation plan

_Status: approved direction (owner decisions 2026-10-16). Supersedes the dual-post "cloud ingest + adopt" model in `docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`._

## Owner decisions

1. The Edge Node is the **only** component that evaluates transactions for fraud/AML. The cloud never re-evaluates.
2. The Edge sends **decision records and statistics** to the cloud. The cloud may store those records.
3. The cloud **never stores transaction data**. The Console fetches transactions and their correlated data live from the PSP's Edge.
4. Cloud→Edge access goes over an **outbound mTLS tunnel opened by the Edge**, so the PSP opens no inbound firewall ports.
5. Alert, case and SAR/STR **workflow records stay in the cloud**, referencing transactions by ID only. Transaction details are pulled from the Edge when viewed. **Filed** SAR/STR/CTR documents are kept in the cloud as the regulatory record.
6. **Retention** is set by each PSP in the Console: minimum 5 years (FATF R.11), default 5 years, PSP may extend. The Edge purges its own data; cloud decision records follow the same period.
7. **Feature parity first.** Cloud-only detections are ported to the Edge before any cloud evaluation code is deleted. The deletion is gated on simulator parity sign-off.
8. **Cross-PSP intelligence** becomes opt-in indicators drawn from filed SARs only: merchant IDs and platform-keyed HMAC device tokens.
9. **Existing cloud transaction data** is back-filled to each PSP's Edge over the tunnel, then purged from the cloud.
10. **Platform admin access** to a PSP's transaction data requires the PSP setting "allow Hokeka support access". Each pull needs a reason and is written to an audit log the PSP can see.

## Current state (what the plan has to change)

- The cloud holds transaction data in many places:
  - `transactions` (`TransactionEntity`) is used by 73 BACKEND classes;
  - foreign keys point at it from `transaction_features`, `alerts`, `sar_transactions`, `case_transactions`, `alert_transactions` and `screening_activities`;
  - `Alert.panHash` stores a card hash;
  - mobile-money, multi-asset/crypto and travel-rule transactions have their own cloud stores.
- **Existing PII leak (removed in R10c):** `edge-host/.../channel/HokekaAiDecisionClient.java` POSTed the full enriched feature bag (pan_hash, amount, merchant…) to `/api/v1/edge/decision` for every ALERT/HOLD. The client and the endpoint are gone.
- **Edge storage won't hold 5 years.**
  - `AerospikeFeatureStore` set `txn` uses a TTL of `EDGE_RETENTION_DAYS`, default 90 days.
  - Aerospike keeps its primary index in RAM: at 1M transactions/day for 5 years, that is about 117 GB of index alone.
  - It has no range queries or pagination.
- `EdgeAssessmentShipper` buffers decisions in memory, so they are lost if the Edge restarts.
- The Edge does no sanctions screening. The cloud's `RealTimeTransactionScreeningService` screens the merchant and the counterparty.
- **Foundations already in place:**
  - Rust `RuleBundle` accepts unknown fields;
  - the Edge has an outbound mTLS `RestClient` (`SecureChannel`) and HSE-1 sealing;
  - the cloud has `EdgeIdentityResolver`;
  - Redis and Java 21 are on both sides;
  - no WebSocket dependency exists yet on either side.

## Target architecture

```
 PSP premises (one Edge Site = 1..N nodes sharing one store)          Hokeka cloud
 ┌──────────────────────────────────────────────┐            ┌──────────────────────────────────┐
 │ PSP API ─POST /edge/evaluate─► Edge Host ×N  │            │ Console (SPA)                    │
 │                │ JNI               │         │            │   │ /api/v1/transactions (GET)   │
 │            Edge Engine (Rust)      │         │            │   ▼                              │
 │                │                   ▼         │  outbound  │ Control Plane                    │
 │   Aerospike (velocity, bundle)  Edge Ledger  │  wss+mTLS  │  ├ TunnelGateway (WS) ◄──────────┼─ nginx
 │   site-scoped counters          (Postgres:   │═══════════►│  ├ TunnelRegistry (Redis)        │
 │                                 evaluations, │  requests ◄│  ├ EdgeDataProxy + access audit  │
 │                                 outbox,      │  responses►│  ├ DecisionRecordService ─► alerts│
 │                                 holds, audit)│            │  │   cases, SAR/CTR (refs by ID)  │
 │   TunnelClient ─ op handlers (read-only)     │  HTTPS     │  ├ /edge/assessments (records)   │
 │   OutboxShipper ─────────────────────────────┼──────────► │  ├ /edge/bundle, /edge/config    │
 │   RetentionPurger (config from cloud)        │◄────────── │  └ Filed SAR/CTR document store  │
 └──────────────────────────────────────────────┘            └──────────────────────────────────┘
```

## Decision record (the only per-transaction data that leaves the Edge)

**Allowed fields:**
- `edgeAssessmentId` (UUID, primary key)
- `txnRef`: the PSP's opaque transaction id, max 128 characters. The Edge rejects 13–19-digit values that pass the Luhn check, so a PAN can't be sent as a reference.
- `edgeNodeId`, `siteId`
- `evaluatedAt`, truncated to the second
- `decision`, `score`
- `triggeredRuleIds[]`, `ruleVersionIds[]`, `bundleVersion`, `bundleHash`
- `evaluator` (native or java), `failClosedCode` (enum), `latencyMs`
- `merchantId` (cloud-onboarded master data)
- compliance flags: `ctrRequired`, `sanctionsHit` plus watchlist entry ids (public-list ids), `typologyCodes[]`

**Forbidden fields:**
- PAN or pan_hash
- any customer, account, device or IP identifier
- names
- the per-transaction amount or currency, and the country
- free-text reasons. The cloud renders reason text from rule ids instead.

**Aggregates** (`EdgeMetricsReport` v2): hourly counts and amount sums, broken down per merchant and per currency, plus decision counts. Never per customer.

## Tunnel: WebSocket over TLS 1.3 mTLS

**Why WebSocket:**
- Cloud side: `spring-boot-starter-websocket`.
- Edge side: the JDK's `java.net.http.WebSocket` with the existing `SSLContext`, so no new Edge dependency.
- nginx: `Upgrade` passthrough with `ssl_verify_client on`.
- Port 443 only.

**Rejected options:**
- gRPC: too much toolchain and proxy friction.
- HTTP/2 long-poll: kept only as a fallback transport using the same frames, for proxies that strip `Upgrade`.

**Connection:**
- Endpoint: `wss://edge.hokeka.com/api/v1/edge/tunnel`, on a dedicated vhost with `ssl_verify_client on`.
- Reconnect uses jittered backoff from 1 s up to 60 s. The node must be ACTIVE.
- Ping every 20 s; the session is treated as dead after 60 s without one.

**Frames:** JSON text, at most 1 MiB.
```
{ "v":1, "type":"hello|hello.ack|request|response|chunk|cancel|credit|notify|error|ping|pong",
  "id":"<uuid>", "corrId":"<request id>", "ts":..., "nonce":"...", "sealed":"<HSE-1 b64>" }
```

- **`request`** (cloud → Edge). Sealed body:
  `{op, params, deadlineMs, page:{limit≤200,cursor}, actor:{userId,role,pspId,purpose,caseId?}, auditId}`.
  It is signed with the cloud's Ed25519 key. The Edge verifies the signature and the nonce through `ReplayGuard`.
- **`response` / `chunk`** (Edge → cloud). Sealed to the cloud's X25519 key:
  `{status, items[], nextCursor, seq, last, incomplete?}`.
- **Read-only op allowlist, enforced on the Edge:**

  | Op | Purpose |
  |---|---|
  | `txn.search` | search by time range, merchantId, decision, ruleId, txnRef |
  | `txn.get`, `txn.batchGet` | fetch one transaction, or up to 200 ids |
  | `txn.related` | the Edge resolves pan_hash linkage internally and never returns the hash |
  | `filing.extract` | data needed to file a SAR/CTR |
  | `export.stream` | chunks of 500 with credit-based backpressure |
  | `ledger.backfill` | one-off import of legacy cloud data |
  | `ledger.health` | ledger status |

  The PSP can restrict ops further with `EDGE_TUNNEL_OPS`, or disable the tunnel entirely.
- **`notify`** (cloud → Edge): `config.updated`, `bundle.updated`, `hold.place`, `hold.release`.

**Routing and failure handling:**
- **Registry:** a Redis hash `tunnel:psp:{pspId}` maps to sessions `{edgeId, siteId, cpInstance, inflight}`. Redis pub/sub routes requests across cloud instances.
- **Load:** the least-busy node gets the request. Each session allows 16 requests in flight; beyond that the cloud returns `429 EDGE_BUSY`.
- **Multiple sites per PSP:** fan out, merge results by `(evaluatedAt desc, id)`, and use a composite cursor.
- **Deadlines:** 8 s for interactive calls, 300 s for exports. The Edge applies the deadline as `statement_timeout`.
- **Errors:**
  - `503 EDGE_OFFLINE`, with `lastSeenAt`;
  - `504 EDGE_TIMEOUT`;
  - `200` with `incomplete:true` and `unavailableSites[]` when only some sites answer.
- **Audit:** every pull writes a cloud `edge_data_access_log` row and an Edge-local `edge_access_log` row sharing one `auditId`.
- **No payload storage:** the cloud never logs, caches or persists payloads. A test asserts this.

## Edge storage: on-Edge PostgreSQL ledger, Aerospike for velocity only

- **Why Postgres:** durable, queryable, cheap monthly `DROP PARTITION` purges, and shared by all nodes in a site.
- **Deployment:** Postgres 16 is added to `edge-host/deploy/docker-compose.yml` and the installers.
- **Tables** (Flyway inside edge-host):

  | Table | Contents |
  |---|---|
  | `edge_evaluations` | Partitioned monthly by `evaluated_at`. Keys and location: `edge_assessment_id uuid PK`, `txn_ref`, `site_id`, `node_id`, `evaluated_at`. Transaction: `pan_hash`, `amount_cents`, `currency`, `merchant_id`, `mcc`, `country_code`, `channel`, `features jsonb` (pan/cvv/track2 stripped). Decision: `action`, `score`, `triggered_rule_ids bigint[]`, `rule_version_ids bigint[]`, `reasons text[]`, `bundle_version`, `bundle_hash`, `evaluator`, `latency_us`, `fail_closed_code`, `ctr_required`, `sanctions_hits jsonb`. Indexes: unique `(txn_ref)` where not null, and `(merchant_id, evaluated_at)`. |
  | `edge_outbox` | `seq, edge_assessment_id, kind, payload jsonb, created_at, shipped_at, attempts`. Replaces the in-memory shipper buffer. Written in the same local transaction as the evaluation. If the ledger write fails and `ledger.fail-closed=true`, the decision becomes HOLD. |
  | `edge_legal_holds`, `edge_held_evaluations` | Held rows are copied to `edge_held_evaluations` before their partition is dropped. |
  | `edge_config`, `edge_purge_log`, `edge_access_log` | Config history, purge history, access audit. |

- **Aerospike:** keeps velocity buckets and the bundle. The `txn` set is removed. Velocity keys become `<pan>:<hour>:<site>`.

## Retention

- **Cloud setting:** `psps.retention_years`, default 5, `CHECK ≥ 5`, maximum 25.
  - Changes go through maker-checker and are audited.
  - A reduction takes effect no sooner than 30 days after it is approved.
- **Delivery to the Edge:** a sealed **Edge config document** at `GET /api/v1/edge/config`.
  - It is polled with an ETag alongside the bundle, and fetched early when a `notify config.updated` arrives.
  - It carries retention, CTR thresholds and FX rates, limits, the merchant blocklist, the sanctions list version, the support-access flag and cross-PSP opt-in.
- **Edge enforcement:**
  - The Edge default becomes 5 years, replacing 90 days, with a hard floor of 5 years.
  - A daily purge drops partitions older than retention and keeps held rows.
- **Cloud decision records** use the same `retention_years`. Records linked to open cases or SARs are excluded from purge.

## Cloud endpoints

**New:**
- `WS /api/v1/edge/tunnel`
- `GET /api/v1/edge/config`
- `GET|PUT /api/v1/psps/{id}/data-retention`
- `GET /api/v1/edge-data/status`
- `GET /api/v1/edge-data/access-log`
- `POST /api/v1/transactions:batchGet`
- `GET /api/v1/transactions/{edgeAssessmentId}/related`
- `GET /api/v1/transactions/export` (streamed, never stored)
- `POST /api/v1/sar/{id}/file` and `POST /api/v1/ctr/{id}/file`

**Changed:**
- `GET /transactions`, `/transactions/{id}`, `/{id}/alerts`, `/{id}/assessment` and `/{id}/compliance-links` are re-backed by the tunnel. The DTO shape stays the same, plus a new field `source:"EDGE"`.
- `POST /edge/assessments` accepts decision record v2, singly or in batches, and now raises alerts and cases.

**Deleted (in R12, after parity sign-off):**
- `POST /transactions/ingest`, `/ingest/batch`
- `/transaction/result`
- the cloud stores behind `monitoring/transactions`, `mobile-money/transactions` and `virtual-assets/transactions`. Each becomes a proxy to the Edge or is removed.

## Console

- **Live pulls** through a `useEdgeData` hook, with states loading / offline / partial / timeout / busy:
  - TransactionMonitoring list/detail and RecordDetail;
  - the alert-detail transaction panels;
  - case transactions and timeline;
  - SAR draft and filing, CTR filing;
  - Customer360 history;
  - the Network graph (`txn.related`);
  - list exports.
- **Offline handling:** an `EdgeOfflineBanner` shows when an Edge is unreachable. Fields held in the cloud stay visible. React Query uses a short `gcTime` and no persistence.
- **Cloud aggregates**, built from decision records, a `decision_stats_hourly` rollup and metrics v2:
  - Dashboard stats, fraud metrics, risk distribution, volume and sparklines;
  - alert and case trends, top-risk merchants;
  - Risk and TransactionMonitoring analytics, Edge Nodes analytics, billing usage.
  - "Recent transactions" becomes recent decision records.
  - Scheduled reports are limited to aggregates.
- **New pages:**
  - Settings → Data Retention;
  - Settings → Hokeka support access, plus cross-PSP opt-in;
  - Edge Nodes: site grouping, tunnel state, ledger health, outbox backlog, last purge;
  - a data-access audit viewer that PSPs can see.

## Conflicts and resolutions

1. **Sanctions.** The Edge must not send names to aml-microservice.
   - The sanctions/PEP list snapshot (public data) is distributed to the Edge as a sealed, versioned list document.
   - The aml-microservice matcher is ported into edge-host, and counterparties are screened locally.
   - Merchant screening stays in the cloud, at onboarding and periodic rescreen. Sanctioned merchant ids reach the Edge through the config blocklist.
2. **CTR.**
   - Thresholds and FX rates come in the config document.
   - The Edge aggregates cash per card per day and sets `ctrRequired`.
   - The cloud opens the CTR workflow by reference. Filing pulls the data from the Edge and stores the filed document.
3. **Velocity across sites.**
   - Nodes in one site share an Aerospike cluster.
   - For multiple sites, counters use site-scoped keys replicated by XDR and summed on read. Without XDR, velocity is per site, and this is documented.
4. **Cross-PSP intelligence.** Opt-in indicators from filed SARs only.
5. **Cloud-only analytics** (DL4J ML, behavioural profiling, Neo4j graph, AML scenarios, cash structuring, limits):
   - port them to Edge derivations and rule IR;
   - ML ships as an Edge model artifact;
   - the graph becomes the on-demand `txn.related` op.
   - The parity gate (decision 7) applies.
   - Note: the ~60 seeded typology rules are SpEL and are currently dropped by `EdgeRuleCompiler` ("no structured rule_json"). They must be compiled to the Edge IR.
6. **AI advisory client.** `HokekaAiDecisionClient`'s feature-bag POST is removed (R10c), because it was a PII leak. AI advisory comes back later fed only by decision records.
7. **SAR drafts.** Auto-generated transaction tables are rendered when viewed and persisted only at filing. Analyst narratives are workflow data; warn about PANs in narratives and redact them.
8. **Travel Rule (VASP).** Originator/beneficiary exchange is either done by the Edge, or relayed by the cloud without persisting. Owner decision still open.
9. **Platform admin access.** Governed by decision 10.
10. **Webhooks.** IDs and decision only.
11. **Existing cloud data.** One-off `ledger.backfill` over the tunnel per PSP, then purge (decision 9).
12. **In-flight plans.** `docs/IMPLEMENTATION_PLAN.md` WP-01/02/06/07 (cloud orchestrator, canonical transaction v2) are re-scoped to the Edge ledger.

## Flyway (cloud): numbers assigned at implementation time

The highest migration after the Wave 72 simulation is **V260**. Planned:

| Migration | Contents |
|---|---|
| `psp_data_retention` | `psps.retention_years`, `support_access_enabled`, `cross_psp_opt_in`, `edge_config_versions` |
| `edge_decision_records` | extend `assessments` with `edge_node_id`, `site_id`, `txn_ref`, `evaluated_at`, `merchant_id`, `ctr_required`, `sanctions_hit`, `fail_closed_code`, `bundle_version`; plus `decision_stats_hourly` |
| `edge_data_access_log` | the cloud access audit table |
| `compliance_refs_by_id` | add `edge_assessment_id`/`txn_ref` to alerts, alert/case/sar transactions, screening activities, findings, rule logs and AI audit. Backfill, drop the FKs to `transactions`, drop `alerts.pan_hash`. |
| `regulatory_filed_documents` | filed SAR/STR/CTR documents |
| `purge_cloud_transaction_data` | runs **after** the backfill and in the same release as the entity deletion. Drops `transaction_features`, `transactions_aud`, `transactions`, and the mobile-money/multi-asset/travel-rule transaction tables. A runbook covers Neo4j, Kafka, Redis, aml-ms `aml_cache` velocity sets, logs and backups. |

## Simulator changes

- **T1/T2:** drive `/edge/evaluate`.
- **T3–T9:** assert Edge decisions.
- **D2:** ingest keys removed.
- **C2/C3:** become tunnel tenant-scoping checks.
- **L1–L3:** alerts from decision records, and filing through tunnel pulls.
- **E4:** becomes "decision record → alert".
- **New scenarios:**
  - **E6:** tunnel offline, multi-node, pagination, timeout and audit.
  - **E7:** retention propagation, the 5-year floor, purge and holds.
  - **E8:** support-access gating.
  - **N1:** residency canary, a **release gate**. Plant a canary PAN hash, amount and txnRef, then scan every cloud Postgres text column, Redis and the logs. Zero hits are allowed outside filed documents.
- `mtls-proxy.mjs` needs WebSocket upgrade support.

## Docs to rewrite

- `EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md` → `EDGE_DATA_RESIDENCY_CONTRACT.md`
- `edge-transaction-evaluation.md`
- `SYSTEM-GLOSSARY.md`: add Edge Site, Edge Ledger, Edge Tunnel, Decision Record, Edge Config; remove Dual-post
- `CLAUDE.md`
- `architecture/edge-channel-contract.md`
- `PSP_API_GUIDE.md`
- `features/API_INTEGRATION.md`, `features/TRANSACTION_MONITORING.md`
- the Edge install guides: Postgres, 5-year sizing, 443 egress only
- `IMPLEMENTATION_PLAN.md`, `ARCHITECTURE_DELTA.md`, `GAP_ANALYSIS.md`
- `tenant-isolation.md`, `encryption-posture.md`, `BILLING_PRICING.md`
- Delete `install/06-psp-api-dual-post-integration.md`.

## Tests

- **Unit:**
  - frame codec;
  - per-frame sealing and signatures;
  - op allowlist and parameter validation;
  - cursor stability;
  - retention floor;
  - purge with holds.
- **Edge ledger:** Testcontainers Postgres, an outbox crash/replay test, and a p99 evaluate regression limit of +2 ms or less.
- **Contract fixtures:** `docs/contracts/tunnel/*.json`, shared by BACKEND and edge-host.
- **BACKEND integration:** a fake Edge WebSocket client covering two cloud instances, offline, timeout, busy, multi-site merge and audit rows.
- **ArchUnit:** no cloud entity may have pan/amount/currency fields outside filed documents, and nothing may depend on the deleted pipeline.
- **Console:** vitest for `useEdgeData` and the offline banner, plus role-matrix e2e.
- **Simulator:** the N1 canary as a release gate.

## Work packages (one agent each)

| WP | Scope | Depends on |
|---|---|---|
| R0 | Residency / tunnel / decision-record / config-document spec and JSON schemas under `docs/contracts/` (docs only) | none |
| R1 | Edge ledger (Postgres, partitions) and durable outbox replacing the `EdgeAssessmentShipper` buffer; remove the Aerospike `txn` set | R0 |
| R3 | Cloud retention, support-access and cross-PSP opt-in settings; sealed `/edge/config`; Console panels; cloud decision-record purge job | R0 |
| R4 | Cloud tunnel server: WS endpoint, handshake auth, Redis registry and cross-instance routing, timeouts, access audit, nginx vhost | R0 |
| R7 | Decision record v2; alerts and cases from decision records; `decision_stats_hourly`; ID-only webhooks | R0 |
| R14a | Docs drafts | R0 |
| R2 | Edge retention purge, holds, config consumer | R1, R3 |
| R5 | Edge tunnel client: reconnect, heartbeat, signature verification, op handlers over the ledger, local access log | R1 (R4 via fixtures) |
| R8 | Refs by ID; detach Alert/Case/SAR/Screening/Finding entities from `TransactionEntity` | R7 |
| R10a | Edge CTR, FX, limits, structuring, rapid movement; compile the SpEL typology library to Edge IR | R3 |
| R10b | Sanctions list distribution and Edge matcher; cloud merchant blocklist | R3 |
| R10c | Remove the PII feature-bag POST (`HokekaAiDecisionClient`, `/edge/decision`); site-scoped velocity | R1 |
| R6 | Cloud data proxy: rewire `/transactions` GETs, batchGet, related, streamed export, support-access gate | R4, R5 |
| R9 | SAR/CTR filing from Edge pulls, filed-document store, hold notifications | R6, R8, R2 |
| R11 | Console: `useEdgeData`, offline banner, page rewiring, dashboards on rollups, Edge Nodes tunnel view, access-log viewer | R6, R7 |
| R15 | Legacy cloud data back-fill to Edge (`ledger.backfill`) with verification | R5, R6 |
| R12 | Delete the cloud ingest and evaluation path; purge migration and runbook | R8, R9, R10*, R11, R15, plus parity sign-off |
| R13 | Simulator rewrite, WebSocket mtls-proxy, N1 canary | R5, R6, R7; final run after R12 |
| R14b | Docs finalized | R12 |

**Parallel lanes:**
1. After R0: R1, R3, R4, R7 and R14a.
2. Then R2, R5, R8, R10a, R10b and R10c.
3. Then R6 and R9.
4. Then R11 and R15.
5. Then R12.
6. Then R13 and R14b.
