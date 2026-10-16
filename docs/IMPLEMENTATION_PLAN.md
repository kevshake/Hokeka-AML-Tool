# Hokeka: Implementation Plan to the Target Model

Inputs: `GAP_ANALYSIS.md` (row IDs in brackets) and `ARCHITECTURE_DELTA.md`.

**Principles:**
- Evolve existing classes. There are no parallel rewrites, and there is one runtime path (Console → Control Plane → Edge).
- Every package is additive first: new tables are written alongside existing columns, a flag switches reads, and the old path is then deleted in the same phase.
- Core first.

**Conventions:**
- Flyway numbers are assigned when a package is built. The migration numbers written in the packages below are from 2026-10-04 and are stale: V261–V263 are now taken by the data residency work (`V261__psp_data_retention`, `V262__edge_decision_records`, `V263__edge_data_access_log`). Use the next free number.
- `BE` = `BACKEND/src/main/java/com/posgateway/aml`, `FE` = `FRONTEND/src`.
- Sizes: **S** ≤ 3 dev-days, **M** ≤ 2 weeks, **L** ≤ 4 weeks. Split L packages into 2–3 PRs where noted.
- Every package needs unit and integration tests (Testcontainers Postgres where DB is touched), an updated API doc / OpenAPI, and a `docs/UI_COVERAGE_MATRIX.md` update when the Console changes.

---

## Data residency re-scope (2026-10-17)

The owner decisions of 2026-10-16 change the ground this plan was written on. The plan of record for them is [`architecture/EDGE_DATA_RESIDENCY_PLAN.md`](architecture/EDGE_DATA_RESIDENCY_PLAN.md). The normative contract is [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md).

**What changed:**
- Only the Edge Node evaluates transactions. The Control Plane never re-evaluates, re-scores or overrides an Edge decision.
- The cloud never stores transaction data. It stores decision records only: IDs, action, score, rule IDs and bundle version. It stores no PAN, amount or features.
- The Edge keeps every evaluation in a PostgreSQL ledger on PSP premises. The PSP sets retention in the Console: minimum and default 5 years, maximum 25.
- The Console reads transaction details live from the PSP's Edge over an outbound mTLS WebSocket tunnel that the Edge opens.
- Alerts, cases and SAR/STR workflow stay in the cloud. They reference transactions by `edgeAssessmentId` and `txnRef` only.

**How this plan reads now:**
- Any package below that adds columns to the cloud `transactions` table, or reads transactions from it, is superseded. Its intent moves to the Edge ledger or to the decision record. The notes under WP-01, WP-02, WP-04, WP-06, WP-07, WP-08 and WP-11 say where.
- Plan conflict 12 applies: WP-01, WP-02, WP-06 and WP-07 are re-scoped to the Edge ledger.
- New cloud work must not read or write `TransactionEntity`. R8 detaches the workflow entities from it, and R12 deletes it.

**Residency work packages:**

| WP | Scope | Status |
|---|---|---|
| R0 | Contract, JSON schemas and fixtures under `docs/contracts/` | Built (commit `8b460fefa`) |
| R1 | Edge Ledger (Postgres 16, monthly partitions) and durable outbox; Aerospike `txn` set removed | Built (`com.hokeka.edge.ledger`, `OutboxShipper`) |
| R3 | PSP retention, support access and cross-PSP opt-in with maker-checker; sealed `GET /api/v1/edge/config`; Console settings panels; cloud decision-record purge job | Built (`service.residency`, V261) |
| R4 | Cloud tunnel server: WebSocket endpoint, handshake, Redis registry and relay, timeouts, access audit, nginx vhost | Built (`com.posgateway.aml.edge.tunnel`, V263, `BACKEND/nginx/edge-tunnel.conf`) |
| R7 | Decision record v2 at `POST /api/v1/edge/assessments`; alerts and cases from decision records; `decision_stats_hourly`; ID-only webhooks | Built (`service.edge.records`, V262) |
| R14a | Docs drafts | This docs pass |
| R2 | Edge retention purge, legal holds, Edge config consumer | Planned |
| R5 | Edge tunnel client and read-only op handlers over the ledger | Planned |
| R6 | Cloud data proxy: `/transactions` GETs re-backed by the tunnel, batchGet, related, export, support-access gate on the Console API | Planned |
| R8 | Workflow references by ID; detach Alert/Case/SAR/Screening/Finding from `TransactionEntity` | Planned |
| R9 | SAR/CTR filing from Edge pulls; filed-document store; hold notifications | Planned |
| R10a | Edge CTR, FX, limits, structuring; SpEL typology library compiled to Edge IR | Planned |
| R10b | Sanctions list distribution and Edge matcher; merchant blocklist | Planned |
| R10c | Remove the feature-bag POST to `/edge/decision`; site-scoped velocity | Done |
| R11 | Console live pulls (`useEdgeData`), offline banner, dashboards on rollups, access-log viewer | Planned |
| R15 | Back-fill legacy cloud transaction data to each Edge, then verify | Planned |
| R12 | Delete the cloud ingest and evaluation path; purge migration | Planned, gated on parity sign-off |
| R13 | Simulator rewrite and N1 residency canary | Planned |
| R14b | Docs finalised | Planned, after R12 |

"Built" means the code is merged on the integration branch. It does not mean the end-to-end flow runs: until R5 ships, no Edge answers tunnel requests, so the cloud tunnel server has no peer.

---

## Phase 0: Hygiene that removes silent errors (parallel, S)

### WP-00 Tenant-scoped lists and a single blacklist truth [I3, I2, E6, S8, S9]
- **Scope:**
  - Add `psp_id` (nullable = platform-wide list, explicitly flagged) to `payment_blacklist_entries`.
  - Change the unique key to (psp_id, entry_type, entry_value).
  - Tenant-scope the Redis keys `aml:blacklist:{psp}:{type}`.
  - Make `RuleFeatureEnrichmentService.enrichBlacklistHits` use `PaymentBlacklistService` (DB TTL-aware + cache) instead of Redis-only.
  - Make `DecisionEngine.takeBlockAction` write the tenant's PSP id.
- **Modules:** CP.
- **Migration:** `V250__payment_blacklist_tenant_scope.sql` (add column, backfill from `created_by` user PSP where derivable, else platform-wide; new unique index).
- **API:** none.
- **Console:** none.
- **Tests:**
  - A PSP-A block does not affect PSP-B (unless the list is platform-wide).
  - Rule feature and DecisionEngine agree on TTL entries.
- **Depends on:** owner decision D-6.
- **Size:** S.

### WP-0A Risk score hygiene [D3, S3, S5]
- **Scope:**
  - Pick one CRA scale (0–100) and fix `RiskScoringService.calculateCra` persisting `cra/100`.
  - Remove the TRS-based pre-decision in `TransactionIngestionService`; keep TRS as data.
  - Rename the MCC weight property (`nationality` → `mcc`).
  - Delete the unused `calculateOverallRisk` or mark it test-only.
- **Modules:** CP.
- **Migration:** `V240__cra_scale_normalize.sql` (rescale values ≤ 1; merchants + transactions).
- **Tests:** CRA identical across both writers; ingest no longer sets a decision.
- **Size:** S.

### WP-0B Rule format safety [E2, E9, S15]
- **Scope:**
  - Make `VisualRuleBuilder.tsx` emit the `{all|any:[{field,operator,value}]}` IR used by `EdgeRuleCompiler`.
  - Limit the field picker to the compiler's FIELD_MAP.
  - On save, the CP validates `rule_json` with the compiler and **rejects** unknown fields/ops instead of the Edge silently skipping them.
  - Show "not edge-deployable" in the Console.
- **Modules:** Console, CP.
- **API:** `POST /rules/validate`.
- **Tests:** compiler round-trip; a visual rule appears in the bundle.
- **Size:** S.

### WP-0C Webhook path cleanup [O2, O5]
- **Scope:**
  - Route all emitters through `WebhookOutboxService` and remove the legacy `@Async WebhookService.sendWebhook` path.
  - Fix the WebhooksTab copy (RISK_ALERT + CASE_UPDATE + MERCHANT_STATUS_CHANGE).
- **Modules:** CP, Console.
- **Size:** S.

---

## Phase 1: Core spine (AssessmentContext, Findings, orchestrator, provenance)

### WP-01 Assessment and Finding ledger, recorded in shadow [K1, K2, K3, Q11, E12]
- **Re-scoped (residency):** findings are produced where the evaluation runs, which is the Edge. Per-transaction findings, evidence and feature values belong in the Edge ledger (`edge_evaluations`), not in cloud tables. The cloud `assessments` table now holds decision records v2 (V262: ids, decision, score, rule ids, rule version ids, bundle version, flags). A cloud `findings` table may hold only class-D data (rule ids, codes, flags), never evidence values. The adapters around `DecisionEngine` below apply only to the cloud ingest path, which R12 deletes; do not build them.
- **Scope (original, 2026-10-04):**
  - Introduce `Assessment` and `Finding`, plus `FindingRecorder` with adapters called from the existing code:
    - the limits check inside `DecisionEngine` (`limits/LimitsManagementController` config);
    - `PaymentBlacklistService` (LIST);
    - `RealTimeTransactionScreeningService` (SCREENING; matches carried as evidence);
    - cross-PSP check;
    - `RulesExecutionService`: per rule, RULE with `rule_version_id` = `RuleDefinition.current_version_id`;
    - `RegulatoryComplianceService` (CTR/STR);
    - `ScoringService` (ML score + model_version);
    - `RiskScoringService` (RISK_FACTOR for KRS/TRS components).
  - Record versions per assessment.
  - Decisions are **not** changed: `DecisionEngine` still decides, and the assessment stores its decision.
- **Modules:** CP.
- **Migrations:** `V238__assessments_findings.sql`
  - `assessments` (id UUID, psp_id, trigger, trigger_ref, parent_assessment_id, edge_assessment_id, versions JSONB, context_hash, decision, latency_ms, created_at);
  - `findings` (all fields in ARCHITECTURE_DELTA §5, indexes on assessment_id, txn_id, party, source_type+source_id);
  - `ALTER rule_execution_logs ADD rule_version_id, assessment_id`.
  - Partition `findings` by month if TimescaleDB is used (V215).
- **API:**
  - `GET /api/v1/transactions/{id}/assessment` (assessment + findings);
  - `POST /transactions/ingest` response adds `assessmentId` (additive).
- **Console:** read-only "Assessment" panel in transaction detail (`TransactionMonitoring/Live` drawer and `RecordDetail`).
- **Tests:**
  - every engine path writes a finding (including limits/blacklist short-circuit cases);
  - versions are captured;
  - p95 latency regression ≤ 5 ms (batch insert, async write via outbox if needed).
- **Depends on:** WP-0A.
- **Size:** L. Split: 01a schema + recorder + rules/limits/blacklist; 01b screening/ML/risk adapters + API/Console.

### WP-02 Compute-all DecisionOrchestrator with ALLOW/FLAG/SUSPEND/BLOCK [F1, F2, K4, K5, A1]
- **Re-scoped (residency):** the decision is made on the Edge, so compute-all resolution belongs in `rule-core` and its Java parity `EdgeRuleInterpreter`. The cloud keeps only the workflow half: turning a decision record into alerts, cases and webhooks (`DecisionRecordWorkflowService`, R7). Do not add `transactions.decision_v2`; the cloud transaction table is deleted in R12. A vocabulary change (FLAG/SUSPEND) is a decision record version change under contract §12.
- **Scope (original, 2026-10-04):**
  - Refactor `DecisionEngine` into `DecisionOrchestrator`. All engines run and emit findings, then resolution is BLOCK > SUSPEND > FLAG > ALLOW, with deterministic tie-break by severity then source priority.
  - Map HOLD → SUSPEND and ALERT → FLAG (owner decision D-7). Keep the `transactions.decision` legacy value via a mapping column during transition.
  - Emit `DecisionAction`s (alert creation, blacklist add, case trigger, webhook) instead of inline side effects.
  - Run behind `hokeka.decision.orchestrator=shadow|live`. In shadow, log disagreements with the old engine; flip to live after N days at ≤ agreed disagreement.
  - Make `RulesExecutionService` and `EdgeRuleCompiler.normaliseAction` stop collapsing SUSPEND→BLOCK and FLAG→REVIEW/ALERT.
- **Modules:** CP, Edge (`rule-core` action enum gains FLAG/SUSPEND; Java parity `EdgeRuleInterpreter`).
- **Migrations:** `V239__decisions_actions.sql` (`decisions`, `decision_actions`; `transactions.decision_v2`).
- **API:**
  - ingest response `decision ∈ {ALLOW, FLAG, SUSPEND, BLOCK}`, `hitRules[]` {ruleInstanceId, ruleVersionId, name, nature, action}, `executedRules[]`, `assessmentId`;
  - keep `action` legacy for one release (versioned API `v1` additive fields).
  - Webhook payloads carry the new decision.
- **Console:** decision chips and filters (Alerts, Live monitoring, Dashboard) for FLAG/SUSPEND.
- **Tests:**
  - precedence table tests;
  - golden-file parity between Java orchestrator resolution and Rust `rule-core`;
  - shadow comparison job.
- **Depends on:** WP-01.
- **Size:** L.

### WP-03 Alert linkage and rule-version provenance [H1, H2, H6, H7, M4, A9, H5]
- **Scope:**
  - Add `alert_transactions` and `alert_findings`. `DecisionOrchestrator` creates alerts from findings.
  - Add `case_alerts.alert_id` FK (backfill by txn+rule name where possible). `CaseAlert` stops copying raw transactions; it references them.
  - Replace the fake `ruleVersion "v<year>.<month>"` in `CaseCreationService.triggerCaseFromRule` with `rule_version_id`.
  - Resolve rules by id, not `findFirstByNameOrderByIdAsc`. The same fix applies in `AlertTuningService`.
  - Add `alerts.case_id`, `rule_instance_id`, `rule_version_id`, and a status enum.
  - Replace hard DELETE with a soft close.
- **Modules:** CP, Console.
- **Migrations:** `V240__alert_links.sql`.
- **API:**
  - `GET /transactions/{id}/alerts`;
  - `GET /alerts/{id}` includes transactions + findings + rule version;
  - `DELETE /alerts/{id}` → 405 / soft close.
- **Console:** Alert detail shows transactions, findings, rule version (link to the rule version diff); transaction detail lists alerts.
- **Tests:** backfill migration test; rule copy vs system rule resolution; provenance end to end (rule version → finding → alert → case).
- **Depends on:** WP-01 (WP-02 recommended).
- **Size:** M.

### WP-04 Edge decision adoption and bundle manifest [K16, E12, M5, S1]
- **Superseded (residency):** the Edge is the only decider, so there is nothing to adopt. Decision record v2 (R7) replaces the `EdgeAssessmentEnvelope`: the Edge writes it to `edge_outbox` with the evaluation, and `OutboxShipper` posts it sealed to `POST /api/v1/edge/assessments`. The envelope carries no feature snapshot (contract §4). The v1 adopt path and `TransactionRequestDTO.edgeAssessmentId` stay only until R12. The bundle manifest hash (`EdgeRuleCompiler.manifestHash`) is in use and travels as `bundleHash` on each record.
- **Scope (original, 2026-10-04):**
  - The Edge emits an `EdgeAssessmentEnvelope` (edgeAssessmentId, bundle manifest hash, rule_version_ids hit, findings, decision, feature snapshot) through an Edge-side outbox to a new CP endpoint.
  - The dual-post `/ingest` accepts `edgeAssessmentId`. The CP links them and runs **adopt mode** (records Edge findings, does not re-decide sync).
  - `EdgeBundleDistributionService` publishes a manifest of rule_version_ids (and later list/risk versions). The bundle version becomes a content hash.
- **Modules:** Edge (edge-host `EdgeController`, `RuleBundlePoller`, new `AssessmentShipper` next to `MetricsShipper`; `rule-core` Decision gains rule version per hit), CP (`EdgeDecisionService`, `TransactionController`).
- **Migrations:** `V251__edge_assessment_link.sql` (`assessments.edge_assessment_id` unique, `context_hash` widened to 128, `edge_bundles` manifest table).
- **API:**
  - `POST /api/v1/edge/assessments` (mTLS, edge enrollment V207);
  - `TransactionRequestDTO.edgeAssessmentId`;
  - update `docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`.
- **Console:** Edge Nodes page shows bundle manifest + adoption rate; the transaction assessment shows "decided at Edge (bundle X)".
- **Tests:**
  - envelope idempotency, out-of-order arrival (envelope before or after ingest);
  - Rust/Java parity on per-hit versions.
- **Depends on:** WP-01, WP-02; owner decision D-9.
- **Size:** L.

### WP-05 Screening activity and screening Findings [G10, G11, A16, K11]
- **Scope:**
  - Persist `screening_searches` (searchId, screeningConfigId/version, entity type, partyId, txnId, list snapshot version, isHit, isNew, reason, ruleInstanceIds) and `screening_matches` (matched list record, matchScore, algorithm version, disposition) for real-time, onboarding and periodic screening.
  - Each match becomes a SCREENING Finding. Score thresholds route to FLAG/SUSPEND/BLOCK instead of any-match → block.
  - `ScreeningWhitelist` / `ScreeningOverride` become dispositions keyed on (party, list record).
- **Modules:** CP (`RealTimeTransactionScreeningService`, `AmlScreeningOrchestrator`, `PeriodicRescreeningService`, aml-microservice returns the algorithm + list version), Console.
- **Migrations:** `V242__screening_activity.sql`.
- **API:** `GET /screening/activity?partyId|txnId`, `POST /screening/matches/{id}/disposition`.
- **Console:** Screening page side-by-side comparison (input vs list record), match disposition, activity log.
- **Tests:** isNew logic; disposition suppresses the same pair on rescreen; threshold routing.
- **Depends on:** WP-01.
- **Size:** M.

### WP-06 One assessment pipeline (collapse the orchestrators) [A1, S2]
- **Re-scoped (residency):** the three cloud orchestrators run only behind the cloud ingest path, which R12 deletes after parity sign-off. Do not merge them. Port their detections to the Edge instead (R10a, R10b, plan conflict 5), then delete them in R12.
- **Scope (original, 2026-10-04):**
  - Make `FraudDetectionOrchestrator` the `AssessmentPipeline`.
  - `AsyncFraudDetectionOrchestrator` and `HighConcurrencyFraudOrchestrator` become execution strategies (thread pool / reactive) of the same staged pipeline: identity → hydrate → features → risk → screening → rules → ML → consolidate → decide → actions.
  - Delete the duplicate logic.
- **Modules:** CP.
- **Tests:** identical findings/decisions across strategies on a fixed corpus.
- **Depends on:** WP-02.
- **Size:** M.

---

## Phase 2: Core data model (parties, canonical transaction, events, identities)

### WP-07 Canonical transaction v2 [B1–B8, Q4]
- **Re-scoped (residency):** the canonical transaction lives in the Edge ledger. New fields (type, state, parties, payment details, device, IP geo, tags) go into `edge_evaluations` typed columns or its hygienic `features` bag (contract §2.4), and into the `/edge/evaluate` request. They are class T: the cloud sees them only inside an audited tunnel response (R5, R6) and never stores them. Do not add `V243__transaction_canonical_v2` to the cloud. Mobile money and multi-asset transactions move to the Edge or are removed in R12.
- **Scope (original, 2026-10-04):**
  - Add `type` enum, `state` enum, `origin_party_id` / `destination_party_id`, and origin/destination amount + currency + country.
  - Add `payment_details` JSONB per side with `method` (CARD, IBAN, ACH, UPI, WALLET, SWIFT, MPESA/MOBILE_MONEY, CHECK, CASH, CRYPTO).
  - Add `device` JSONB, persisted IP country/ASN/proxy, `tags` JSONB, `metadata` JSONB, `related_transaction_ids`, `product_type`, `promotion_code`, `reference`, `jurisdiction`, `origin_funds_info`.
  - `TransactionRequestDTO` v2 (additive). The card ISO fields map into CARD payment details.
  - Fix `ip_high_risk_country` to use the IP country.
  - Mobile money and multi-asset ingest map into the same entity, so the side modules become views (no new silo).
- **Modules:** CP, Edge (canonical parse + fields in FIELD_MAP).
- **Migrations:** `V243__transaction_canonical_v2.sql`.
- **API:** v2 request schema; filter schema exposes the new fields.
- **Console:** transaction detail shows both sides, payment method, device, IP geo, tags.
- **Tests:** mapping from ISO 8583/EMV, mobile money, multi-asset payloads; backward compatibility for v1 payloads.
- **Depends on:** WP-01.
- **Size:** L (PRs: schema + DTO; module mappings; Edge fields).

### WP-08 Transaction events and re-assessment [A3, B2, P2]
- **Re-scoped (residency):** transaction events and re-assessment run on the Edge, against the ledger. The cloud receives a new decision record per re-assessment. No cloud `transaction_events` table.
- **Scope (original, 2026-10-04):**
  - `POST /transactions/{id}/events` (state change + updated fields, idempotent by eventId) creates a `transaction_events` row and a new assessment (trigger=TXN_EVENT, parent=prior).
  - Async rules re-run. A state change is a webhook.
- **Modules:** CP.
- **Migrations:** `V244__transaction_events.sql`.
- **Console:** event timeline on the transaction.
- **Tests:** refund/reversal re-triggers; idempotency; state transitions validated.
- **Depends on:** WP-07.
- **Size:** M.

### WP-09 Party model: consumer and business users, ownership [C1–C4, Q1, Q2, A4, A5]
- **Scope:**
  - Introduce `parties` (type CONSUMER/BUSINESS, psp_id, external_id, state, kyc_status, tags, risk fields), `consumer_profiles`, `business_profiles`, `persons`, and `ownerships` (role DIRECTOR/SHAREHOLDER/UBO, %, from/to).
  - `Merchant` becomes a business party: add `merchants.party_id` and keep `Merchant` as the PSP-merchant extension; `BeneficialOwner` migrates to `ownerships` + `persons`.
  - `MultiAssetCustomer` maps to `parties`.
  - Party CRUD + search API.
  - Apply `@Audited` to party tables.
- **Modules:** CP, Console.
- **Migrations:** `V245__parties.sql`, `V246__merchant_party_backfill.sql`.
- **API:**
  - `POST/PUT/GET /api/v1/consumer-users`, `/business-users` (with directors/shareholders/UBOs);
  - `GET /users/search`;
  - user comments `POST /users/{id}/comments`.
- **Console:** Consumer/Business user pages (evolve Customer360 + Merchants; do not add a parallel page), directors/shareholders tabs.
- **Tests:** backfill integrity (merchant ↔ party 1:1); tenant filter on parties; ownership screening hooks.
- **Depends on:** owner decision D-1.
- **Size:** L.

### WP-10 User events, versioned snapshots and user webhooks [C6, C4, C5, Q5, O3]
- **Scope:**
  - `POST /users/{id}/events` produces `user_events` and a `user_snapshots` (version, hash) row.
  - Each assessment records the snapshot version.
  - Re-run KRS/CRA and screening on relevant changes (new director, nationality change).
  - Webhooks USER_STATE_CHANGED, KYC_STATUS_CHANGED, USER_TAGS_UPDATED, CRA_UPDATED.
- **Modules:** CP, Console.
- **Migrations:** `V247__user_events_snapshots.sql`.
- **Console:** party history (snapshot diff).
- **Depends on:** WP-09.
- **Size:** M.

### WP-11 Payment, device and network identity normalization [B9, Q3, K7, E5]
- **Re-scoped (residency):** payment, device and IP identities are class E/T and stay on the Edge. Linkage is served by the tunnel op `txn.related`, which never returns the key value (R5). No cloud identity tables keyed on PAN, device or IP.
- **Scope (original, 2026-10-04):**
  - `payment_identities` (method, normalized hashed key, e.g. PAN hash, IBAN, MSISDN, wallet), `device_identities`, `network_identities` (IP, /24, ASN).
  - Resolve them at ingest (stage 2) and link transactions to identities.
  - Correct the mislabeled features (`distinct_payment_ids`, `distinct_banks`, `unique_counterparties`, wallet blacklist).
- **Modules:** CP, Edge (same normalization library for Aerospike keys).
- **Migrations:** `V248__identities.sql`.
- **Tests:** normalization vectors shared Java/Rust; feature correctness.
- **Depends on:** WP-07.
- **Size:** M.

---

## Phase 3: Core risk, rules, lists, investigation, feedback

### WP-12 Versioned risk model: factors, bands, modes, CRA lock [D1–D10, Q6]
- **Scope:**
  - `risk_models` + `risk_model_versions` (snapshot, hash, comment, maker-checker, restore), following the `rule_versions` pattern.
  - Factor definitions for consumer, business and transaction, with weights, risk-level tables and mode include/exclude/override.
  - Configurable bands VERY_LOW..VERY_HIGH.
  - CRA = w·KRS + (1−w)·avg(TRS).
  - `cra_locked` / `locked_by` / `locked_until` / reason, respected by every writer.
  - Factor contributions are written as RISK_FACTOR findings.
  - Retire `CustomerRiskProfilingService` and `RiskRulesEngine` as separate models (re-express their logic as factors).
  - Country risk tables become factor tables.
- **Modules:** CP, Console, Edge (CRA band replicated to Aerospike).
- **Migrations:** `V249__risk_model_versioning.sql`, `V250__party_risk_lock.sql`.
- **API:** `/risk-models` CRUD/versions/approve/restore; `POST /users/{id}/cra-lock`.
- **Console:** new Risk Model page under Rules/Settings; CRA lock control on the party page.
- **Tests:** formula tests; versions referenced by assessments; lock respected under concurrent ingest.
- **Depends on:** WP-09, WP-01; owner decision D-10.
- **Size:** L.

### WP-13 Rule instance model: eligibility filters, risk-band parameters, hit actions, execution mode [E11, D5, F5, F6, A6, E1]
- **Scope:**
  - Extend `RuleDefinition` (instance) with:
    - `filters` (txn types, methods, countries, party types, bands, tags);
    - `parameters_by_band`;
    - `hit_actions` (set user state / KYC / tag / add to list with TTL & side / create alert / create case);
    - `execution_mode` SYNC|ASYNC;
    - `placement` EDGE|CP;
    - `alert_frequency`, `nature`, `labels`;
    - `template_id` + parameter overrides (templates stay as `system_managed` rows).
  - The governance snapshot includes all of these.
  - The orchestrator applies hit actions as DecisionActions.
  - Eligibility outcome is recorded as "not eligible" (no Finding) vs "no hit".
- **Modules:** CP, Edge (compiler + interpreter support filters + band params), Console (RuleEditorModal).
- **Migrations:** `V251__rule_instance_extensions.sql`.
- **API:** `GET /rules/schema` (filter + variable schema) [A7]; `/rule-templates`.
- **Tests:** band param resolution; filters; hit actions per side (origin/destination/all); Edge parity.
- **Depends on:** WP-02, WP-12 (bands), WP-09 (state/tags).
- **Size:** L.

### WP-14 Feature registry and aggregate DSL [E3, E4, E5, E7, E8, E9, Q9, K9]
- **Scope:**
  - `feature_definitions` (versioned): entity, aggregate (fn SUM/AVG/COUNT/MAX/MIN/UNIQUE, field, window incl. 1m/5m, group-by key = party/payment identity/device/IP/name/address, filters), and list variables.
  - Extend the AST: operators between/null/regex/fuzzy/array any-all/date; transforms.
  - Compile to SQL/Timescale in the CP and to Aerospike counters at the Edge.
  - Migrate the hard-coded `RuleFeatureEnrichmentService` features into definitions (keep the names; fix the labels).
  - Drools/SpEL become legacy-only (no new rules).
- **Modules:** CP, Edge (`AerospikeFeatureStore` generic counters keyed by definition), Console (builder fields from `/rules/schema`).
- **Migrations:** `V252__feature_definitions.sql`.
- **Tests:** per-operator Java/Rust parity suite; aggregate correctness vs SQL; window boundaries.
- **Depends on:** WP-11, WP-13.
- **Size:** L (PRs: registry + CP compiler; Edge counters; Console builder).

### WP-15 Generic lists, versioning, Row Match, Console Lists page [I1, I2, I4, I5, I7, I8, E10, A12, G4]
- **Scope:**
  - `lists` (psp_id, type BLACKLIST/WHITELIST/CUSTOM, key schema = one or more columns), `list_entries` (values JSONB, metadata, expires_at), `list_versions` / entry audit, `list_matches` as LIST findings.
  - Migrate `payment_blacklist_entries` and `custom_watchlist_entries` into `lists` (keep views for compatibility during the transition).
  - Row Match operator.
  - Edge replica via the bundle or an Aerospike push.
  - `LIST_UPDATED` webhook.
- **Modules:** CP, Edge, Console.
- **Migrations:** `V253__generic_lists.sql`, `V254__lists_backfill.sql`.
- **API:** `/lists`, `/lists/{id}/entries` (CRUD, bulk import CSV, TTL).
- **Console:** Lists page (CRUD/import/history).
- **Depends on:** WP-00, WP-14 (operator).
- **Size:** M/L.

### WP-16 Async rules, alert consolidation, accumulation and freeze [F3, F4, H3, H4]
- **Scope:**
  - Async phase runner (Kafka consumer on `transactions.raw` / assessment events) for `execution_mode=ASYNC` rules, with alert frequency (instant/daily/weekly/monthly).
  - Alert consolidation key (rule instance + subject [+ counterparty]) with an open window; new hits append to `alert_transactions`/`alert_findings` unless the alert is frozen.
  - Precedence: async never mutates the returned decision.
- **Modules:** CP.
- **Migrations:** `V255__alert_consolidation.sql` (`alerts.consolidation_key`, `frozen_at`).
- **Depends on:** WP-03, WP-13.
- **Size:** M.

### WP-17 Alert investigation parity [H13, A8, H12, A10]
- **Scope:**
  - Alert comments, assignment records (with history), tags and evidence: reuse `CaseNote` / `CaseEvidence` patterns as generic `comments` / `evidence` with a subject type.
  - Webhooks on every alert and case status transition.
  - Pre-signed upload/download for evidence (`EvidenceStorageService`).
- **Modules:** CP, Console.
- **Migrations:** `V256__alert_comments_assignments.sql`.
- **Depends on:** WP-03.
- **Size:** M.

### WP-18 Disposition feedback loop [H14, Q15, K20]
- **Scope:**
  - `dispositions` per alert and per finding (reason codes).
  - `RuleEffectivenessService` reads finding-level dispositions; stop the transaction-wide back-fill in `AlertController.resolve`.
  - Configurable feedback actions:
    - TP → CRA factor override / user state / list add;
    - FP → TTL whitelist for (rule, subject) or screening pair.
  - ML label export (only if D-3 = yes).
- **Modules:** CP, Console (disposition dialog shows the actions to be applied).
- **Migrations:** `V257__dispositions.sql`.
- **Depends on:** WP-03, WP-12, WP-15.
- **Size:** M.

### WP-19 Webhook catalogue and failed-delivery management [O3, O4, A13, I7]
- **Scope:**
  - Event types for: user state, KYC, tags, CRA, transaction status, alert status, case status, batch, list updates, SAR status.
  - Per-attempt `webhook_deliveries` history.
  - API to list failed deliveries, retry/replay, re-enable a subscription, and rotate the secret (dual-secret overlap window).
- **Modules:** CP, Console (WebhooksTab delivery log/retry).
- **Migrations:** `V258__webhook_deliveries.sql`.
- **Depends on:** WP-0C.
- **Size:** M.

### WP-20 Screening configurations and coverage [G1–G7, G9, A15]
- **Scope:**
  - `screening_configurations` (versioned) per subject type: transaction counterparty, consumer, business, director/shareholder. Each sets lists, fuzziness, PEP tiers, adverse media, and filters.
  - Counterparty screening on per configuration (D-4).
  - Person-level adverse media.
  - Ongoing monitoring flag + cadence by risk band per party.
  - Sanctions download enabled with freshness alerting (D-5).
- **Modules:** CP, Console.
- **Migrations:** `V259__screening_configurations.sql`.
- **Depends on:** WP-05, WP-09, WP-12.
- **Size:** M.

---

## Phase 4: Important (simulation, shadow, network, anomaly, ML, CRM, batch)

| WP | Title | Scope summary | Modules | Migrations | Depends on | Size |
|---|---|---|---|---|---|---|
| WP-21 | Simulation / backtest [M2, M1] | `simulation_runs` replays historical assessments/transactions against a draft rule version (findings flagged `simulation`). Reports hits, alerts and estimated FP (from dispositions). Add SIMULATING lifecycle state and gate approval on a run. Console Simulation page. Replaces the unwired `RuleAbTestingService`. | CP, Console | V260 | WP-01, WP-13, WP-14 | L |
| WP-22 | Shadow rules [M3] | `execution_mode=SHADOW` produces findings with `shadow=true`, excluded from resolution. Comparison dashboard vs live. Works at the Edge too (bundle flag). | CP, Edge, Console | V261 | WP-02, WP-13 | M |
| WP-23 | Relationship graph + network findings [L1, L2, H11] | `relationships` (party↔party, party↔identity; type, confidence, valid_from/to, evidence) fed by ownership, shared identities and counterparties. Absorb `MultiAssetCustomerRelationship` and `MobileMoneyNetworkEdge`. NETWORK findings via SQL first; Neo4j optional (D-2). Console Network page enabled on SQL data. | CP, Console | V262 | WP-09, WP-11 | L |
| WP-24 | Dynamic baseline anomaly rules [J1] | Baseline aggregate definitions (rolling mean/std or percentile per subject/peer group) computed async and pushed to Aerospike; BEHAVIOR findings. Migrate the deviation features. | CP, Edge | V263 | WP-14 | M |
| WP-25 | ML findings + labels [J2, K13] | `ScoringService` and DL4J outputs become ML findings with model version + feature references. Label pipeline from dispositions. Still off by default until D-3. | CP | — | WP-01, WP-18 | M |
| WP-26 | CRM linkage [P1, A14, Q16] | `crm_records` (system, external id, URL) linked to party/case. API + Console field. | CP, Console | V264 | WP-09 | S |
| WP-27 | Batch resource [A2, P3] | `batches` (id, type txn/user/event, status, counts) with async processing, per-row results endpoint and a `BATCH_COMPLETED` webhook. Evolve `BatchTransactionIngestionService`. | CP | V265 | WP-07, WP-19 | M |

## Phase 5: Advanced (AI, threshold optimisation, SAR, QA)

| WP | Title | Scope summary | Modules | Depends on | Size |
|---|---|---|---|---|---|
| WP-28 | AI as Findings; AI rule builder into governance [J3, J5, J6] | `AiDecisionGateway` results are written as findings (sourceVersion = prompt_version + model id). `ai_decision_audit` keeps cost/latency only. `/rules/generate` produces a DRAFT rule version in the AST that must pass simulation + shadow. Provider stays unnamed on PSP surfaces. | CP, Console | WP-01, WP-21 | M |
| WP-29 | Threshold recommender with backtest [J4] | `AlertTuningService` proposals are backtested via a simulation run before entering maker-checker. Use rule id, not name. | CP, Console | WP-21 | M |
| WP-30 | SAR links + lifecycle events [N1, N2, N3, Q17] | `sar_alerts`, `sar_findings`; SAR status webhooks; `sar_messages` (regulator acks/errors). Enable transports per jurisdiction per D-11. | CP, Console | WP-03, WP-19 | M |
| WP-31 | QA sampling and SLA analytics [H15] | Random/targeted QA sampling of closed alerts/cases, reviewer scoring, investigator metrics. | CP, Console | WP-17 | S/M |

---

## Sequencing summary

```
Phase 0 (parallel): WP-00, WP-0A, WP-0B, WP-0C
Phase 1: WP-01 → WP-02 → {WP-03, WP-05, WP-06} → WP-04
Phase 2: WP-07 → {WP-08, WP-11};  WP-09 → WP-10
Phase 3: WP-12 → WP-13 → WP-14 → {WP-15, WP-16}; WP-17, WP-18, WP-19, WP-20
Phase 4: WP-21 → WP-22; WP-23, WP-24, WP-25, WP-26, WP-27
Phase 5: WP-28, WP-29, WP-30, WP-31
```

The residency packages (R0–R15, above) take precedence where they overlap. Phase 1 and 2 exit criteria that mention `/ingest` or cloud transactions are read against the Edge ledger and decision records.

**Exit criteria per phase:**
- **Phase 1:** every `/ingest` and every Edge decision has one assessment with findings and versions. One decision per transaction. Alerts link to findings, transactions and rule versions.
- **Phase 2:** card, mobile money and multi-asset transactions use the canonical model. Consumer and business parties with ownership exist, and events re-trigger assessment.
- **Phase 3:** risk bands drive rule parameters. Lists are generic and tenant-scoped. Dispositions change future assessments. The full webhook catalogue is delivered.

## Product-owner decisions needed

| ID | Decision | Why it blocks | Blocks |
|---|---|---|---|
| D-1 | **Party scope:** should Hokeka model PSP/bank **end customers** (consumers and non-merchant businesses) as first-class parties, or merchants only? | Defines C1/C3 and the whole party model. | WP-09, WP-10 |
| D-2 | **Graph analysis:** enable Neo4j, or keep the network on Postgres/SQL? | Infrastructure on the Hostinger VPS, plus the dead Cypher code. | WP-23 |
| D-3 | **ML scoring:** turn on XGBoost scoring (`scoring.service.enabled`) and/or DL4J? Where is the model hosted and who owns training labels? | Currently ml_score = 0, and "ML unavailable" behaviour returns HOLD when enabled. | WP-25, WP-18 (labels) |
| D-4 | **Counterparty screening default** (`screening.realtime.screen-counterparty`, currently false): on for all PSPs, or per screening configuration? | Latency and false-positive load vs regulatory coverage. | WP-20 |
| D-5 | **Sanctions download** (`sanctions.download.enabled=false`): enable in production, which lists (UN, OFAC, EU, UK, local Kenya), and what freshness SLA? | Screening quality. | WP-20 |
| D-6 | **List tenancy:** should a BLOCK at one PSP blacklist the PAN for all PSPs (today's global behaviour), or only that PSP, with cross-PSP sharing via the existing `CrossPspFraudFlag` consortium mechanism? | Data isolation and consortium terms. | WP-00, WP-15 |
| D-7 | **Decision semantics:** confirm HOLD → SUSPEND and ALERT → FLAG mapping, the meaning of SUSPEND for PSPs (hold funds pending review?), and the legacy API deprecation window. | API contract. | WP-02 |
| D-8 | **Edge sync scope:** which checks must run inline at the Edge (lists yes; exact sanctions index? risk band? AI inline?) and data residency of replicated lists/party bands on Edge nodes. **Partly decided 2026-10-16:** every check runs at the Edge (sole decider); sanctions screening moves to the Edge (R10b). Party bands and AI inline remain open. | Edge design. | WP-04, WP-14, WP-15 |
| D-9 | **Edge latency budget and authority:** confirm a p99 target (proposed ≤ 50 ms excluding AI inline), and that the Edge decision is authoritative for edge rails (CP adopt mode, no re-decision). **Authority decided 2026-10-16:** the Edge is the only decider for every rail. The p99 target is still open; the Edge ledger write is held to +2 ms p99 (`EvaluateLatencyRegressionTest`). | Single decision per transaction. | WP-04 |
| D-10 | **Risk-band scheme:** 5 bands VERY_LOW..VERY_HIGH and their default boundaries; the KRS/TRS weighting in CRA; who may lock CRA and for how long. | Risk model. | WP-12, WP-13 |
| D-11 | **SAR transport per jurisdiction:** which regulators are live per tenant (Kenya FRC goAML, CBK, FinCEN, FCA, etc.), manual vs API filing, and receipt handling. | Filing. | WP-30 |
| D-12 | **AI beyond shadow:** may any AI engine move from `advisory_only=true` to influencing decisions (and which), and may an Edge-side AI recommendation fed only by decision records be enabled per PSP (the feature-bag inline path was removed in R10c)? | Governance and model risk. | WP-28 |
| D-13 | **Webhook event catalogue and payload versioning:** which events PSPs get by default; the signature scheme stays HMAC-SHA256 "ts.body". | API contract. | WP-19 |
| D-14 | **CRM integration target(s)** (none found in code): which CRM systems, and is linkage by reference or sync? | Scope. | WP-26 |
| D-15 | **Retention:** findings/assessment retention period and partitioning (volume grows by about N findings per transaction), aligned with record-keeping law (e.g. 7 years in Kenya POCAMLA; confirm). **Decided 2026-10-16:** each PSP sets retention in the Console, minimum and default 5 years (FATF R.11), maximum 25, maker-checker, reductions take effect 30 days after approval (V261). A PSP under a 7-year law sets 7. | Storage. | WP-01 |
| D-16 | **Side modules:** confirm that multi-asset, mobile money and crypto modules fold into the canonical transaction/party model (no separate risk engines). | No-silo principle. | WP-07, WP-09 |
