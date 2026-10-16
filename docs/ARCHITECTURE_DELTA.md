# Hokeka: Architecture Delta (Current vs Target "No Silos" Design)

Companion to `GAP_ANALYSIS.md` (row IDs in brackets). Based on a read-only review of `kevshake/Hokeka-AML-Tool@main` on 2026-10-04 (EAT).

## 0. Data residency update (2026-10-17)

Sections 1–5 below describe the code and the target as of 2026-10-04. The owner decisions of 2026-10-16 replace part of that target. The plan of record is [`architecture/EDGE_DATA_RESIDENCY_PLAN.md`](architecture/EDGE_DATA_RESIDENCY_PLAN.md); the contract is [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md).

**New target, in short:**
- The Edge Node is the only component that evaluates transactions. The Control Plane never re-decides.
- The Edge records every evaluation in a PostgreSQL ledger on PSP premises. The PSP sets retention (5 to 25 years, default 5).
- The Edge sends decision records (IDs, action, score, rule IDs, bundle version; no PAN, amount or features) to the cloud. The cloud raises alerts and cases from them.
- The cloud never stores transaction data. The Console reads it live from the Edge over a tunnel the Edge opens (outbound mTLS WebSocket, port 443).
- Filed SAR/STR/CTR documents are the only cloud copy of transaction data.

**What changed in code since 2026-10-04:**

| Area | Was (sections 1.1, 4.1) | Now | Still to do |
|---|---|---|---|
| Edge transaction record | `recordTransaction` → Aerospike `txn` set, TTL `EDGE_RETENTION_DAYS` (default 90 days) | `EvaluationRecorder` → Edge Ledger (`com.hokeka.edge.ledger`, Postgres 16, `edge_evaluations` partitioned by month). The Aerospike `txn` set is gone; Aerospike keeps velocity buckets (26 h TTL) and the bundle. | Retention purge and legal holds (R2). No purge runs yet, so the ledger only grows. |
| Edge → cloud decision | none (dual-post), later a v1 JSON envelope from an in-memory `EdgeAssessmentShipper` | Decision record v2, written to `edge_outbox` in the same transaction as the evaluation, shipped sealed (HSE-1, `hokeka.decision.records`) by `OutboxShipper` to `POST /api/v1/edge/assessments`. A ledger write failure answers `HOLD / LEDGER_UNAVAILABLE` when `ledger.fail-closed=true`. | v1 acceptance removed in R12. |
| Cloud workflow from Edge decisions | none | `DecisionRecordWorkflowService` raises alerts (source type `EDGE_DECISION`), cases and CTR deadlines by `edgeAssessmentId`; `decision_stats_hourly` rollup (V262). | Detach Alert/Case/SAR from `TransactionEntity` (R8). |
| PSP data settings | none | Retention, support access, cross-PSP opt-in with maker-checker (V261); sealed `GET /api/v1/edge/config`. | Edge-side config consumer (R2). |
| Cloud → Edge reads | none | Tunnel server: `WS /api/v1/edge/tunnel` (`com.posgateway.aml.edge.tunnel`), Redis registry, sealed relay, `edge_data_access_log` (V263), nginx vhost `BACKEND/nginx/edge-tunnel.conf`. | Edge tunnel client (R5); cloud data proxy for the Console (R6); Console live pulls (R11). |
| Cloud transaction stores | `transactions`, `transaction_features`, mobile-money / multi-asset / travel-rule stores | Unchanged. `/api/v1/transactions/ingest` still writes them. | Back-fill to each Edge (R15), then delete the ingest path and purge (R12). |
| AI advisory from the Edge | `HokekaAiDecisionClient.enrichBorderline` → `/api/v1/edge/decision` with the full feature bag | Unchanged. This is a known class-T leak. | Remove the feature-bag POST (R10c). |
| Edge sanctions | none | none | Sanctions list distribution and Edge matcher (R10b). |
| Velocity keys | `<panHash>:yyyyMMddHH` | Unchanged. Velocity is per Aerospike cluster, so per site. | Site-scoped keys for multi-site PSPs (R10c). |

**Parts of this document that no longer apply:**
- Section 4.1 "Ship to the CP" and "adopt mode", and section 4.2 "Edge rails: CP stages 1–10 run in adopt mode". The Edge decides; the cloud does not hydrate or re-run stages for an Edge transaction.
- Section 4.2 "Canonical stores": Transaction, TransactionEvent and the payment/device/network identities move to the Edge ledger. Assessment in the cloud means the decision record. Findings that carry evidence values stay on the Edge.
- Section 4.4 rows "Control Plane sync" for transactions: there is no cloud sync path once R12 lands. Sanctions screening of counterparties moves to the Edge (R10b); merchant screening stays in the cloud.
- Silo S1 (two decisions per transaction) is resolved by design. It is fully closed when R12 deletes the cloud ingest path.

## 1. Current flow, end to end (as of 2026-10-04)

### 1.1 Edge Node (edge-host Java + edge-engine Rust + Aerospike)

```
PSP switch ──► POST /edge/evaluate  (edge-host EdgeController)
                 │
                 ├─ AerospikeFeatureStore.deriveFeaturesDetailed(panHash)
                 │     → pan_txn_count_1h, pan_txn_count_24h, pan_amount_sum_24h (hourly buckets, 24h max)
                 │     → store unavailable ⇒ HOLD (fail-closed)
                 ├─ EdgeEngine → rule-core (Rust, JNI) / EdgeRuleInterpreter (Java parity)
                 │     ops EQ NE GT GTE LT LTE IN CONTAINS, all/any/not; compute-all, max severity, summed score
                 │     Decision{action ∈ ALLOW/ALERT/HOLD/BLOCK, score, triggered_rule_ids, reasons}
                 ├─ HokekaAiDecisionClient.enrichBorderline → Control Plane /edge/decision
                 │     (psps.ai_inline_mode default FALSE; ai_inline_budget_ms default 500)
                 └─ recordTransaction → Aerospike (TTL), response to switch
  RuleBundlePoller ◄── Control Plane EdgeBundleDistributionService (bundle from EdgeRuleCompiler)
  MetricsShipper   ──► Control Plane (metrics only)
```

- **What the Edge does not do:**
  - No sanctions, lists or screening.
  - No device, IP or payment-identity aggregates.
  - It does not create a cloud transaction, alert, case or webhook (`docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`). _2026-10-17: no longer true for alerts, cases and webhooks. Decision records v2 raise them (R7, section 0). It still creates no cloud transaction, by design._
- **Bundle provenance:** the bundle version is `max(updated_at)*1000 + count`, with no `rule_version_id` per rule [M4, M5].
- **Rule compatibility:** rules whose `rule_json` uses unknown fields, operators or shapes are **skipped with a WARN**. This includes everything saved by the Console Visual Rule Builder (`{groups:[…]}`) [E2, E9].

### 1.2 Control Plane (BACKEND Spring Boot + aml-microservice)

```
PSP ──► POST /api/v1/transactions/ingest (Idempotency-Key) | /ingest/batch
  TransactionIngestionService
    ├─ requires numeric Merchant (only subject type)
    ├─ KRS = RiskScoringService.calculateKrs(merchant)          [risk model #1, @Value weights]
    ├─ originCountry = IpGeoService(ip) else BIN  → used for TRS only, NOT stored
    ├─ TRS = calculateTrs(origin, merchantCountry, amount)
    ├─ CRA = (merchant.cra + TRS)/2 → merchant.cra (0–100 scale)   ⟂ calculateCra persists 0–1  [scale conflict]
    ├─ pre-decision from TRS level (later overwritten)              [duplicate truth]
    └─ Kafka outbox transactions.raw
  {FraudDetection | AsyncFraudDetection | HighConcurrencyFraud}Orchestrator   (flag-selected; three pipelines)
    ├─ FeatureExtractionService / OptimizedFeatureExtractionService
    ├─ ScoringService
    │    ├─ L1 Aerospike cache (aml-microservice) / L2 Redis cache
    │    ├─ RulesExecutionService  (Drools aml-rules.drl + DRL rules + RegulatoryComplianceService + SpEL per PSP)
    │    │     compute-all → strongestDecision; SUSPEND→BLOCK, FLAG→REVIEW; error → HOLD
    │    │     RuleFeatureEnrichmentService: ~50 hard-coded SQL features (PAN/merchant keyed)
    │    │     rule_execution_logs (no rule_version_id)
    │    ├─ XGBoost (scoring.service.enabled=false ⇒ ml_score 0) / DL4J (off)
    │    └─ AI advisory FRAUD_SCORING (borderline)
    └─ DecisionEngine.evaluate   ── SHORT-CIRCUIT ──
         limits → blacklist(pan/terminal/ip) → sanctions(RealTimeTransactionScreeningService) → cross-PSP
         → rule_decision → ML thresholds → AML escalation
         ├─ Alert row(s) (one per action, single txn_id, rule names as text)
         ├─ BLOCK ⇒ PAN auto-blacklisted 30d (global, not tenant-scoped)
         ├─ CaseCreationService.triggerCaseFromRule (one open case per merchant; CaseAlert copy, fake ruleVersion)
         ├─ transaction_features (feature_json, risk_details, model_version)
         ├─ webhook RISK_ALERT via event_outbox; Kafka alerts.generated
         └─ AI advisory TRANSACTION_RISK / ALERT_TRIAGE → ai_decision_audit
```

**Side modules with their own subjects, transactions and risk logic:**
- **Multi-asset:** `MultiAssetCustomer`, `MultiAssetRiskEngine`, relationships V151–V153.
- **Mobile money:** network edges V167.
- **Crypto/wallet intelligence.**
- **G2 content monitoring:** `MonitoringAlert`, V231.
- **Market surveillance.**
- **Periodic jobs** for rescreening, KYC refresh and sanctions delta (`NewListMatchAlertService`).

### 1.3 Console (FRONTEND React/Vite)

- **Investigation:** Alerts, Cases (AllCases, Queues, Timeline, NetworkGraph), Transaction Monitoring (Live, Analytics, Reports, SARs), Screening, Customer 360, Merchants, KYC documents.
- **Rules:** Rules Generation (VisualRuleBuilder, RuleEditorModal, governance versions/approve), Limits & AML.
- **Settings and admin:** Webhooks, AI, Platform Admin; Edge Nodes, Audit Logs, Regulatory Reports.
- **No surfaces exist for:** lists, risk-model configuration, simulation/shadow results, webhook delivery logs, consumer users, or a transaction's findings and alerts.

## 2. Target design (from the target model §22–§30)

```
Event plane (txn | txn event | user event | list/screening delta | disposition)
  → AssessmentContext (one per trigger; frozen inputs + versions)
  → engines (risk, screening, lists, rules, aggregates/anomaly, ML/AI, network) — each emits Findings
  → Findings bus (common schema, provenance, versions)
  → Decision orchestrator (compute-all; BLOCK > SUSPEND > FLAG > ALLOW; DecisionActions)
  → Alert/Case (linked to exact findings + transactions) → state changes (user state, tags, lists, CRA)
  → webhooks → future AssessmentContexts see outcomes + dispositions
```

## 3. Where the silos are today

| # | Silo / duplicate truth | Evidence | Consequence | Rows |
|---|---|---|---|---|
| S1 | **Two independent decisions per transaction (Edge vs cloud).** | Dual-post contract; no edge-decision field in `TransactionRequestDTO`. | Stored decision ≠ acted-on decision. Edge hits are not logged. _2026-10-17: resolved by design (Edge is sole decider; decision record v2 carries rule ids and versions). The cloud still decides on `/ingest` until R12._ | K16 |
| S2 | **Three CP orchestrators.** | `FraudDetectionOrchestrator`, `AsyncFraudDetectionOrchestrator`, `HighConcurrencyFraudOrchestrator`. | Behaviour depends on a flag, and fixes must be made three times. | A1 |
| S3 | **Pre-decision vs final decision.** | `TransactionIngestionService` sets the decision from TRS, then `DecisionEngine` overwrites it. | Transient wrong state. Webhooks or reads in between can see it. | K4 |
| S4 | **Four rule/risk engines.** | Drools, SpEL, Easy Rules `RiskRulesEngine`, Edge `rule-core`. | Different operators and features. Parity is unverifiable. | E9, E14 |
| S5 | **Four risk calculations, two CRA scales.** | `calculateKrs/Trs/updateCra`, `calculateCra` (0–1), `CustomerRiskProfilingService`, `RiskRulesEngine`. | CRA is not comparable, and the band and risk-param logic has no single input. | D1–D3 |
| S6 | **Three alert truths.** | `alerts`, `case_alerts` (no FK, raw transaction JSON copy), `monitoring_alerts`. | Status and disposition drift. Disposition back-fill cannot find the right rule. | H1, H7 |
| S7 | **Engine outputs in five formats.** | `reasons` strings, `triggered_rules` JSON names, `rule_execution_logs`, `case_alerts`, `ai_decision_audit`. | There is no per-transaction explanation with versions. | K2 |
| S8 | **Two blacklist truths.** | DB with TTL (`PaymentBlacklistService`) vs Redis-only reads in `RuleFeatureEnrichmentService`. | A TTL entry can block in `DecisionEngine` while the rule feature says "not listed", or the reverse. | E6, I2 |
| S9 | **Global (cross-tenant) lists.** | `payment_blacklist_entries` UNIQUE(type, value), no `psp_id`. | Tenant isolation issue, unless that behaviour is intended (owner decision D-6). | I3 |
| S10 | **Side-module subjects.** | `MultiAssetCustomer`, mobile money, crypto: each has its own transactions and risk. | The same person or wallet is assessed in silos, and network findings are not shared. | C1, B5, L1 |
| S11 | **Fake rule provenance.** | `CaseAlert.ruleVersion = "v<year>.<month>"`, rule looked up by name. | Rule lineage cannot be audited. The system rule can be picked instead of the PSP copy. | M4, H6 |
| S12 | **Dispositions only partially feed back.** | Back-fill marks **all** rules on the transaction TP/FP. No effect on CRA, lists, user state or ML. | Effectiveness metrics are polluted and the loop does not close. | H14 |
| S13 | **Screening results are not persisted.** | `TransactionScreeningResult` is in-memory. | Payment-time screening cannot be evidenced, and there is no isNew/disposition reuse. | G10, G11 |
| S14 | **AI as a parallel decision record.** | `ai_decision_audit.baseline_decision/final_decision`. | When AI leaves shadow mode, its influence sits outside the decision record. | J5, J6 |
| S15 | **Console and Edge rule formats differ.** | Visual builder `{groups}` vs compiler `{all/any}`. | Rules can look active in the Console without ever running at the Edge. | E2 |

## 4. Recommended target mapped onto the three modules

### 4.1 Edge Node: inline, synchronous, latency-bound

**Responsibility:** produce the authoritative **sync decision** for card/real-time rails using only data that can be served locally.

- **Inline at the Edge (sync):**
  1. Parse and normalize the transaction into the canonical shape (type, state=CREATED, payment-details union, device, IP).
  2. Resolve payment identity and device keys locally (hash).
  3. Read hydrated features from Aerospike:
     - velocity/amount aggregates by PAN, payment id, device, IP and (when known) user;
     - add 1m/5m buckets.
  4. Run list lookups against tenant-scoped blacklist/whitelist **replicas** in Aerospike, pushed from the CP list versions.
  5. Optionally screen exact or near-exact names against a compact sanctions index (owner decision D-8). Fuzzy and adverse-media screening stay in the CP.
  6. Run sync rule instances (`execution_mode=SYNC`, `placement=EDGE`) from the bundle, with eligibility filter → detection → risk-band parameters (the subject's CRA band is replicated to Aerospike).
  7. Emit Edge Findings and resolve with the **same** precedence code as the CP orchestrator (shared Rust/Java library, parity-tested).
  8. Optional AI inline only when `ai_inline_mode=true`, inside `ai_inline_budget_ms`. It returns a Finding; it never overrides alone unless the owner enables that.
- **Latency budget (recommendation, needs owner sign-off D-9):**
  - p99 ≤ 50 ms for steps 1–7 at the Edge;
  - the AI inline path is bounded by `ai_inline_budget_ms` (default 500 ms) and only on borderline outcomes;
  - fail-closed behaviour stays as today (HOLD → SUSPEND).
- **Ship to the CP:** an `EdgeAssessmentEnvelope` {edgeAssessmentId, bundleVersion, rule_version_ids hit, findings, decision, featureSnapshot hash} sent asynchronously (outbox on the Edge, at-least-once). Alternatively the PSP's dual-post carries `edgeAssessmentId`. The CP then **adopts** the Edge decision as the transaction's sync decision instead of re-deciding [K16].

### 4.2 Control Plane: system of record, async engines, orchestration

- **Canonical stores:**
  - Party (consumer/business/person/legal entity) + Ownership;
  - Payment/Device/Network identities and Relationship;
  - Transaction + TransactionEvent;
  - UserEvent + UserSnapshot;
  - versioned Rule/RiskModel/List/ScreeningConfiguration/FeatureDefinition;
  - Assessment, Finding, Decision, DecisionAction;
  - Alert (+AlertTransaction, AlertFinding), Case (+CaseAlert FK, CaseFinding);
  - Disposition, SAR, WebhookEvent/Delivery, AuditEvent.
- **One assessment pipeline.** Evolve `FraudDetectionOrchestrator` into the single `AssessmentPipeline` and collapse the async and high-concurrency variants into execution strategies of the same pipeline. It implements the 14 stages:
  - **Sync path** (non-edge rails or PSPs without an Edge): stages 1–10 inside the `/ingest` request.
  - **Edge rails:** CP stages 1–10 run in **adopt mode**. They hydrate, record the Edge findings, and add CP-only sync findings only if the PSP opted into cloud-sync. The decision is not changed.
  - **Async phase** (after the response, via Kafka/outbox): fuzzy screening of counterparties, async rule instances (weekly/monthly alert frequency), ML/anomaly, network, and AI advisory. These write Findings with `phase=ASYNC`, which can create alerts, cases and state changes but **never change the returned decision** [F4].
- **Decision orchestrator.** Evolve `DecisionEngine` into `DecisionOrchestrator`:
  - compute-all over Findings;
  - precedence BLOCK > SUSPEND > FLAG > ALLOW;
  - emits DecisionActions (alert/case creation, list add, user state/tag change, CRA lock respect);
  - limits, blacklist, sanctions and cross-PSP become engines that emit Findings, not early returns.
- **Feedback.** A `DispositionService` writes a Disposition per alert/finding and applies feedback actions: whitelist with TTL, CRA factor override, list add, and label export for ML. `RuleEffectivenessService` reads finding-level dispositions (not transaction-level back-fill).
- **Bundles.** `EdgeBundleDistributionService` publishes a **manifest**: {bundleHash, rule_version_ids, list_version_ids, risk_model_version, feature_definition_version} [M5].
- **AI.** `AiDecisionGateway` results are written as Findings (`sourceType=ML`, sourceId=engine code, sourceVersion=prompt_version + model id). `ai_decision_audit` stays as the cost/latency/raw-response audit only.

### 4.3 Console

- **Assessment view per transaction:** findings, decision, actions, versions, and linked alerts/cases. This is the "explain" screen.
- **Alerts:** multi-transaction list, findings, comments, assignment, freeze; alerts-by-transaction.
- **Configuration pages, all versioned with maker-checker:**
  - Lists (CRUD/import/TTL/history);
  - Risk Model (factors, weights, bands, modes, CRA lock, versions);
  - Rule builder emitting the **one** AST (eligibility, aggregates, list variables, Row Match, risk-band params, hit actions, execution mode/placement);
  - Simulation and Shadow results.
- **Webhooks:** delivery log, retry, re-enable, rotate.
- **Parties:** consumer/business profiles, directors/shareholders/UBOs, events and snapshot history.
- **Provider neutrality:** PSP-facing surfaces never name the AI provider (keep `AiDisclosureSanitizer`).

### 4.4 Placement summary

| Concern | Edge (sync) | Control Plane sync | Control Plane async |
|---|---|---|---|
| Canonical normalization + identity keys | ✔ | ✔ (non-edge rails) | — |
| Velocity/amount aggregates (≤24h, 1m/5m) | ✔ Aerospike | ✔ | — |
| Long-window aggregates, baselines (7d/30d/peer) | read precomputed values | read precomputed values | ✔ compute + push to Aerospike |
| Lists (black/white/custom) | ✔ replica | ✔ | list maintenance, TTL expiry |
| Sanctions exact/compact index | optional (D-8) | ✔ | — |
| Fuzzy sanctions/PEP/adverse media | — | merchant cached | ✔ counterparties, persons, ongoing |
| Sync rule instances | ✔ | ✔ | — |
| Async rule instances (alert frequency) | — | — | ✔ |
| Risk scoring KRS/TRS/CRA | read CRA band | TRS | KRS/CRA recompute on user events |
| ML / anomaly / network / AI | AI inline optional | — | ✔ |
| Decision resolution | ✔ (authoritative for edge rails) | ✔ (non-edge rails) | never changes decision |
| Alerts, cases, SAR, webhooks | — | ✔ | ✔ |

## 5. Data contracts to introduce first

- **`Finding`**
  - findingId (UUID), assessmentId, txnId?, partyId?, counterpartyPartyId?
  - sourceType ∈ {RULE, RISK_FACTOR, SCREENING, LIST, LIMIT, ML, NETWORK, DEVICE, BEHAVIOR, MANUAL, SYSTEM}
  - sourceId, sourceVersion, phase ∈ {EDGE_SYNC, CP_SYNC, ASYNC}, shadow:boolean
  - nature (AML/FRAUD/SCREENING/…), severity, triggered, score
  - proposedAction ∈ {ALLOW, FLAG, SUSPEND, BLOCK}, proposedActions[] (state/list/tag)
  - evidence JSONB, featureReferences[] (featureDefinitionVersion + values), explanation, createdAt
- **`Assessment`**
  - assessmentId, pspId, trigger ∈ {TXN, TXN_EVENT, USER_EVENT, SCREENING_DELTA, DISPOSITION, BATCH}
  - triggerRef, parentAssessmentId, edgeAssessmentId
  - contextSnapshot JSONB (or hash + pointer), versions JSONB {ruleBundle, rules[], riskModel, lists[], screeningConfig, featureDefs, userSnapshots[]}
  - decision, decidedAt, latencyMs
- **`Decision` / `DecisionAction`:** decision + reasonFindingIds[]; action type, target, payload, status, executedAt.

These are additive tables. Existing columns (`transactions.decision`, `triggered_rules`, `transaction_features`) stay populated during migration, filled from the orchestrator output, until the Console and API read the new model.
