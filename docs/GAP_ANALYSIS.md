# Hokeka AML Tool: Gap Analysis against the Target Model

- **Target model:** `/workspace/hokeka/flagright-research-target-model.md` (§1–§34)
- **Code reviewed:** GitHub `kevshake/Hokeka-AML-Tool`, branch `main`. Read-only via the GitHub connector, as of 2026-10-04 (Africa/Nairobi, EAT).
- **Scope:** Console (`FRONTEND/`), Control Plane (`BACKEND/` + `aml-microservice/`), Edge Node (`edge-host/`, `edge-engine/`, Aerospike). `legacy_ui` was excluded.
- **Method:** Every row below was checked against source (entities, controllers, services, Flyway migrations, Console routes/components). Docs (`AGENTS.md`, `docs/AML-FRAUD-COVERAGE-GAP-REGISTER*`, `docs/UI_COVERAGE_MATRIX.md`, `docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`) were used as pointers and cross-checked. Items not confirmed in code say "not verified".
- **Status rule:** If something exists but is backend-only, off by default, merchant-only where the target needs any party, or not wired end-to-end, it is marked **Partial**, and the missing piece is spelled out.
- **Doc vs code discrepancies:** `AGENTS.md` says Java 17 while the runtime brief says Java 21. `docs/UI_COVERAGE_MATRIX.md` marks every route "implemented + aligned", yet the Lists, risk-config, simulation and webhook-delivery surfaces do not exist in `FRONTEND/src/App.tsx`.

## 0. Data residency update (2026-10-17)

The rows below were assessed on 2026-10-04. The owner decisions of 2026-10-16 ([`architecture/EDGE_DATA_RESIDENCY_PLAN.md`](architecture/EDGE_DATA_RESIDENCY_PLAN.md), contract [`EDGE_DATA_RESIDENCY_CONTRACT.md`](EDGE_DATA_RESIDENCY_CONTRACT.md)) change two things for every row:

- **Where a gap is fixed.** Only the Edge evaluates transactions. A gap in transaction evaluation, features or findings is fixed on the Edge (`edge-host`, `edge-engine`), not in the cloud pipeline. The cloud pipeline behind `/api/v1/transactions/ingest` is deleted in R12.
- **What the cloud may store.** The cloud stores decision records, workflow records and filed SAR/STR/CTR documents. It never stores transaction data (PAN, amount, features, names). Rows whose "What is missing" adds a column to the cloud `transactions` table (B1–B9, B4 especially) now mean a column in the Edge ledger.

**Rows changed by the residency work:**

| Row | Was | Now | Status | Evidence / open WP |
|---|---|---|---|---|
| K16 | Missing: Edge and cloud decide independently; dual-post | Edge is the only decider. Decision record v2 reaches the cloud and raises alerts and cases. | Partial | Done: `OutboxShipper`, `EdgeResidencyController` `POST /api/v1/edge/assessments`, `service.edge.records.DecisionRecordWorkflowService`, V262. Open: the cloud still re-decides on `/ingest` until R12. |
| E12 | Edge hits not logged in CP | Each decision record carries `triggeredRuleIds`, `ruleVersionIds`, `bundleVersion`, `bundleHash`. | Partial | Done: V262 columns on `assessments`. Open: rule-execution evidence stays on the Edge ledger; the cloud `rule_execution_logs` path dies with R12. |
| M5 (Edge bundles) | Bundle version = `max(updated_at)*1000 + count` | Bundle manifest hash `EdgeRuleCompiler.manifestHash` over `ruleId:ruleVersionId` lines, sent as `bundleHash`. | Partial | Lists, risk config and screening config are still unversioned. |
| B10 | Idempotent ingestion implemented | Still implemented, but on a path being removed. Decision record ingest is idempotent on `edgeAssessmentId` (`record_digest`, V262). `/edge/evaluate` is not idempotent: a retry with the same `txn_id` is evaluated again, and `edge_txn_refs` points the reference at the newest evaluation. | Partial | R12 deletes `/ingest`. Evaluate-side idempotency is not assigned to a WP. |
| B1–B9 | Missing/partial on cloud `TransactionEntity` | Target store is the Edge ledger (`edge_evaluations`). | Unchanged status | Re-scoped WP-07, WP-11 (see `IMPLEMENTATION_PLAN.md`). |
| G6 | Payment-time screening in the cloud (`RealTimeTransactionScreeningService`) | Cloud screening runs only on the ingest path. The Edge does no sanctions screening. | Partial | R10b: sanctions list snapshot to the Edge and an Edge matcher; merchant screening stays in the cloud. |
| new: R-1 | — | Edge retention. The 90-day Aerospike `txn` TTL is gone. Evaluations live in the Postgres ledger, sized for 5 years. | Partial | Done: R1 (`com.hokeka.edge.ledger`), PSP setting V261 (5..25 years, maker-checker, 30-day delay on reductions), sealed `/edge/config`. Open: the Edge does not yet read `/edge/config` or purge (R2). |
| new: R-2 | — | PII leak: `HokekaAiDecisionClient` posts the full feature bag (pan_hash, amount, merchant) to `/api/v1/edge/decision` for borderline decisions. | Open | R10c removes it. The Hokeka AI recommendation returns later fed only by decision records. |
| new: R-3 | — | Velocity across sites. Velocity keys are `<panHash>:yyyyMMddHH` in one Aerospike cluster, so velocity is per site. | Open | R10c: site-scoped keys, summed over XDR where present. |
| new: R-4 | — | Cloud still stores transactions (`transactions`, `transaction_features`, mobile-money, multi-asset, travel-rule stores). | Open | R15 back-fills each PSP's Edge, then R12 purges. |
| new: R-5 | — | Console reads transactions from the cloud database. | Open | Tunnel server built (R4). Edge tunnel client (R5), cloud data proxy (R6) and Console live pulls (R11) are planned. |
| new: R-6 | — | Cloud-only detections (SpEL typology rules, DL4J, behavioural profiling, CTR, limits) have no Edge equivalent; the ~60 seeded SpEL typologies are skipped by `EdgeRuleCompiler`. | Open | R10a; parity sign-off gates R12. |

The status counts in 1.1 and the top-10 list in 1.2 are from 2026-10-04 and are not recomputed here. Gap #3 in 1.2 is the K16 row above.

## 1. Executive summary

### 1.1 Status counts (164 capability rows)

| Status | Total | Core | Important | Advanced |
|---|---|---|---|---|
| Implemented | 14 | 10 | 3 | 1 |
| Partial | 95 | 73 | 15 | 7 |
| Missing | 55 | 41 | 14 | 0 |
| N/A | 0 | 0 | 0 | 0 |
| **Total** | **164** | **124** | **32** | **8** |

**What already works:**
- Idempotent ingestion.
- Rule versioning with maker-checker (`rule_versions`, V160).
- Fuzzy/transliteration name matching.
- Fail-closed behaviour.
- Case comments, evidence and chain of custody, assignments and related cases.
- Tamper-evident audit (V232).
- A first-class SAR with deadlines, maker-checker and regulator transports.
- HMAC-signed webhooks over a durable outbox.
- The AI layer: shadow by default, provider-neutral, audited in `ai_*`.

**The main gap is architectural, not feature count.** Each engine (limits, blacklist, sanctions, cross-PSP, Drools/SpEL rules, Easy Rules risk, ML, AI, Edge rules) produces its own output format:
- reason strings
- rule-name JSON
- `rule_execution_logs`
- `case_alerts` copies
- `ai_decision_audit`
- an Edge decision that is never sent to the cloud

There is no AssessmentContext, no common Finding, and no single compute-all orchestrator. The data model centres on card transactions and merchants: no consumer users, no origin/destination parties, no transaction types/states/events, and no payment-identity normalization.

### 1.2 Top 10 gaps by impact

| # | Gap | Rows | Why it matters |
|---|---|---|---|
| 1 | **No AssessmentContext and no common Finding model.** Engine outputs are strings and JSON names spread over 5+ tables, with no provenance or versions. | K1, K2, K3, Q11 | Decisions cannot be explained or replayed per transaction. This blocks simulation, shadow, feedback and regulator-grade traceability. |
| 2 | **The decision engine short-circuits and has no FLAG/SUSPEND.** `DecisionEngine` returns on the first hit (limits → blacklist → sanctions → cross-PSP → rules → ML). Its vocabulary is ALLOW/ALERT/HOLD/BLOCK, and SUSPEND is mapped to BLOCK. | F1, F2, K4, K5 | Later engines' results are never recorded, so suspend/flag semantics drift and the target precedence BLOCK > SUSPEND > FLAG cannot be implemented. |
| 3 | **The Edge and the Control Plane decide independently.** _(2026-10-17: superseded; the Edge is the sole decider, see section 0.)_ The integrator must dual-post. The Edge decision, its rule hits and its bundle version never reach the cloud record. | K16, E12, E14 | The decision the PSP acted on can differ from what Hokeka stores, alerts on and reports. |
| 4 | **No party model.** No consumer users, no origin/destination users, no directors/shareholders for business users, and no user events or versioned snapshots. Merchant is the only subject. | C1, C3, C6, B3, Q1, Q2, Q5 | Bank and wallet customers cannot be monitored or risk-scored (KRS/CRA), and KYC changes cannot trigger re-assessment. |
| 5 | **The transaction is not canonical.** No type or state enum, no transaction events, no payment-details union (card only, while mobile money and crypto are separate silos), and no payment/device/IP identity normalization. IP country is computed but discarded. | B1, B2, B5, B8, B9, A3, P2 | Non-card rails go through side modules, aggregates by payment id or device are impossible or mislabeled, and refunds or reversals never re-trigger assessment. |
| 6 | **Alerts are not linked to findings, transactions or rule versions, and there are three alert truths** (`alerts`, `case_alerts` with no FK, `monitoring_alerts`). Each alert has one `txn_id`. `CaseAlert.ruleVersion` is faked as "v<year>.<month>". There is no alerts-by-transaction lookup. | H1, H2, H3, H6, H7, M4, A9 | You cannot answer "which rule version generated this alert" or "show every transaction behind this alert", and investigators see copies that drift. |
| 7 | **The risk model is fragmented.** Four risk calculations exist. CRA is written on a 0–100 scale at ingest and a 0–1 scale elsewhere. There are no risk bands, band-driven rule parameters, include/exclude/override modes, CRA lock, or versioned risk config. | D1–D9, Q6 | Risk scores are not comparable or auditable, and rules cannot be tuned per risk band (a Core regulatory expectation for RBA). |
| 8 | **Lists are not tenant-scoped and not generic.** `payment_blacklist_entries` is globally unique with no `psp_id`, Redis keys are global, and Redis and DB disagree on TTL entries. There are no metadata or multi-field rows, no Row Match, no versioning and no Console page. | I1–I8, E6, E10 | A BLOCK at one PSP auto-blacklists a PAN for every PSP for 30 days, which needs an explicit owner decision. Lists also cannot drive Row Match or whitelisting reliably. |
| 9 | **Rule DSL and features are hard-coded.** About 50 SQL features keyed on PAN/merchant, several mislabeled (devices counted as "payment ids", merchant country used as "IP country"). There is no aggregate DSL, no eligibility filters and no transforms. Console Visual Rule Builder output is silently skipped by the Edge compiler. | E2–E11, E14, A7 | PSPs cannot build the target's by-user/payment-id/IP/device aggregates, and rules shown as active in the Console may not run at the Edge. |
| 10 | **Screening leaves no activity record and creates no findings. Counterparty screening is off by default.** Transaction-time hits live only in memory, and any match blocks. | G6, G9, G10, G11, A16 | Payment-time screening cannot be evidenced to a regulator, there is no match disposition loop at transaction time, and counterparties go unscreened by default. |

### 1.3 Recommended first three work packages

These are taken from `IMPLEMENTATION_PLAN.md`. _2026-10-17: WP-01 and WP-02 are re-scoped to the Edge ledger; see the "Data residency re-scope" section of [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md)._
1. **WP-01: Assessment and Finding ledger, recorded in shadow.**
   - Add the `assessments` and `findings` tables (+ `rule_version_id` on `rule_execution_logs`).
   - Add a `FindingRecorder` that captures outputs from limits, blacklist, sanctions, cross-PSP, rules, ML and AI with source versions on every `/ingest`.
   - Decisions do not change. **Size: L.**
2. **WP-02: Compute-all DecisionOrchestrator with ALLOW/FLAG/SUSPEND/BLOCK.**
   - Replace `DecisionEngine`'s short-circuit with Finding resolution (BLOCK > SUSPEND > FLAG > ALLOW).
   - Write `decisions` + `decision_actions`.
   - Shadow-compare against the old engine behind a flag, then cut over.
   - API returns `hitRules`/`executedRules` + `assessmentId`. **Size: L.**
3. **WP-03: Alert linkage and rule-version provenance.**
   - Add `alert_transactions`, `alert_findings`, and `case_alerts.alert_id` FK.
   - Use real `rule_version_id` (remove the fake "v<year>.<month>") and resolve rules by id instead of name.
   - Add `GET /transactions/{id}/alerts` and Console transaction→alerts/findings panels.
   - The Edge bundle manifest carries `rule_version_id`s. **Size: M.**

Run in parallel (S): **WP-00 list tenant scoping** (add `psp_id` to `payment_blacklist_entries`, tenant-scope the Redis keys, read the DB TTL entries in rule enrichment), pending owner decision D-6.

## 2. Capability matrix

Legend: **Status** = Implemented / Partial / Missing / N/A. **Module** = Console (FRONTEND), CP (Control Plane: BACKEND + aml-microservice), Edge (edge-host + edge-engine + Aerospike). Paths are relative to the repo root. `BE` = `BACKEND/src/main/java/com/posgateway/aml`, `MIG` = `BACKEND/src/main/resources/db/migration`, `FE` = `FRONTEND/src`.

### 2.1 API surface (target §1)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| A1 | Sync transaction verification returning decision, executed/hit rules and risk details | Partial | CP, Edge | `BE/controller/TransactionController` `POST /api/v1/transactions/ingest` (Idempotency-Key) → `FraudDetectionOrchestrator` → response `txnId, score, action, reasons, latency, riskDetails{krs,trs,cra,mlScore}`. Edge `edge-host EdgeController POST /edge/evaluate` → `Decision{action, score, triggered_rule_ids, reasons}`. | Decisions are ALLOW/ALERT/HOLD/BLOCK, not ALLOW/FLAG/SUSPEND/BLOCK. No `executedRules` vs `hitRules` split, and no rule instance id, rule version, per-hit nature/score/labels or hit actions in the response. No `assessmentId`. Three orchestrators selected by flags (`FraudDetectionOrchestrator`, `AsyncFraudDetectionOrchestrator`, `HighConcurrencyFraudOrchestrator`) can return different shapes. The edge response and the cloud response are two separate decisions (see K16). | Core |
| A2 | Batch ingestion (transactions, users, events) | Partial | CP | `POST /api/v1/transactions/ingest/batch` (synchronous list), `BE/controller/BatchController`, `BatchTransactionIngestionService`, `BatchScoringService`. | No batch resource with a batchId, status or progress. No async batch processing with per-row results that can be fetched later, and no batch-completed webhook. No batch import for users or events, because those entities do not exist. | Core |
| A3 | Transaction events API (status updates that re-trigger assessment) | Missing | CP | No endpoint found. `TransactionController` has only ingest, batch, GET by id and list. `TransactionEntity` has no state column (only `decision`, `acquirer_response`). | `POST /transactions/{id}/events` (state change, updated fields), a `transaction_events` table, re-running assessment on the event, and a transaction-status webhook. | Core |
| A4 | Consumer user API and consumer user events | Missing | CP | No consumer/end-customer entity or controller in the core path. `entity/User` is the Console login user. `multiasset/MultiAssetController` + `MultiAssetCustomer` are a separate module with a thin profile. | Consumer user create/update/get, user events (`/users/{id}/events`), a KYC status and user state API, and webhooks for user state, KYC and tags. | Core |
| A5 | Business user API and business user events | Partial | CP, Console | `psp/MerchantController` (merchant onboarding = business user), `underwriting/MerchantVerificationController` (MIG V202–V203), `BeneficialOwner` entity. Console `pages/Merchants`, `pages/KycDocuments`. | No business-user events endpoint, no versioned snapshots, no directors/shareholders model for merchants (see C3), and no tags. The API assumes a numeric merchant id belonging to a PSP, so a bank's corporate customers who are not merchants cannot be modelled. | Core |
| A6 | Rule templates and rule instances management | Partial | CP, Console | `BE/controller/RulesController` `/rules` CRUD, `/{id}/enable|disable`, `/{id}/versions`, `/governance/*`, `/{id}/rollback`. Seeds/taxonomy MIG V139, V141–V143 (`external_code` R-n, typology). Per-PSP copies MIG V205/V206 (`derived_from_rule_id`). Console `pages/RulesGeneration`, `pages/Rules/components/RuleEditorModal`. | Instances are full copies of templates, not "template + parameter overrides". The instance has no execution mode (sync/async/shadow), eligibility filter, risk-band parameter sets, hit actions, alert frequency or labels/nature. | Core |
| A7 | Rule filter schema endpoint (what fields/filters are available) | Missing | CP | Not found. Fields are hard-coded: `EdgeRuleCompiler.FIELD_MAP` (15 fields), `RuleFeatureKeys`, and Console `VisualRuleBuilder.tsx` (9 fields). | A single schema endpoint (`GET /rules/schema`) driven by a FeatureDefinition registry and shared by the Console builder, the CP compiler and the Edge bundle. | Important |
| A8 | Alert management API (list, get, status, assign, comment, tags) | Partial | CP, Console | `BE/controller/AlertController` `/alerts` GET list/`{id}`/`count/active`, `PUT /{id}/resolve`, `PUT /{id}/status`, `DELETE /{id}` (hard delete), `GET /disposition-stats`. Console `pages/Alerts`, `components/Alerts/LiveAlertQueue`. | No assignment API (only an `investigator` string), no comment objects (only a `notes` text field), no tags, and no webhook on status change. Hard DELETE removes regulatory evidence and should be a soft close. | Core |
| A9 | Alert IDs by transaction ID | Missing | CP | Index `idx_alert_txn` on `alerts.txn_id` exists, but there is no endpoint for it. | `GET /transactions/{id}/alerts`, which needs the `alert_transactions` join (H2), because alerts can span several transactions. | Core |
| A10 | Evidence URL and pre-signed upload | Partial | CP, Console | `BE/controller/EvidenceController`, `case_management/EvidenceStorageService`, `entity/compliance/CaseEvidence`, `EvidenceChainOfCustody` (+service). Searching for `presigned` in the backend found nothing. | No pre-signed upload/download URL flow, and evidence attaches to cases only (not to alerts, users or SARs as first-class evidence). | Important |
| A11 | User comments and user search | Partial | CP, Console | `search/GlobalSearchController`; Console `pages/Customer360`, `pages/RecordDetail` (`records/:recordType/:recordId`). | No comments on users/merchants, and no search over a unified Party model (there isn't one). | Important |
| A12 | Lists management API (CRUD, entries, TTL, import) | Partial | CP | Services: `security/PaymentBlacklistService`, `sanctions/CustomWatchlistService`, `ScreeningWhitelistService`, `ScreeningOverrideService`. The repo tree has no dedicated blacklist/list controller. Whether custom watchlists are exposed through the sanctions controllers was not verified. | A generic `/lists` + `/lists/{id}/entries` API with list types, TTL, metadata, multi-field rows, bulk import, versioning and a list-updated webhook. | Core |
| A13 | Failed webhook delivery management | Missing | CP, Console | The outbox stores `attempts`, `last_error` and `FAILED` (MIG V221, `WebhookOutboxDispatcher`), but `WebhookSubscriptionController` exposes only subscribe/list/delete. | List failed deliveries, manual retry/replay, re-enable an auto-disabled subscription, secret rotation, and a Console delivery log. | Important |
| A14 | CRM link | Missing | CP | Code search for `crm` in `BACKEND/src/main/java` returned 0 hits. | A CRMRecord link (external CRM id/URL per party/case) and API. | Important |
| A15 | Update ongoing-monitoring status per entity | Partial | CP | `rescreening/PeriodicRescreeningService`, `sanctions/PeriodicSanctionsScreeningService`, `Merchant.nextScreeningDue/lastScreenedAt`. | No API or flag to turn ongoing monitoring on or off per user/business/director, and no cadence per risk band. | Important |
| A16 | Screening activity API | Missing | CP | Transaction screening results are in-memory only (`RealTimeTransactionScreeningService` → `TransactionScreeningResult`). | A persisted ScreeningActivity and `GET /screening/activity?userId|txnId`. | Core |
| A17 | Management plane coverage overall (~46 target ops) | Partial | CP | Rules, cases, alerts (partial), evidence, webhooks (subscribe/list/delete) and SAR workflow exist. | Missing ops: filter schema, alert comments/assignments, alerts-by-txn, pre-signed upload, user comments, list CRUD, failed webhooks, CRM link, monitoring status, screening activity. | Core |

### 2.2 Transaction data model (target §2–3)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| B1 | Transaction type enum (DEPOSIT, TRANSFER, EXTERNAL_PAYMENT, WITHDRAWAL, REFUND, OTHER…) | Missing | CP | `entity/TransactionEntity` has only `direction` and `channel_type`; `TransactionRequestDTO` matches. | A `type` enum column, ingestion mapping, rule variable and edge field. | Core |
| B2 | Transaction state enum and lifecycle (CREATED…SUCCESSFUL/DECLINED/REFUNDED/REVERSED…) | Missing | CP | Only `decision` (ALLOW/ALERT/HOLD/BLOCK) and `acquirer_response`. | A `transaction_state` column with transitions driven by transaction events (A3), and state as a rule input. | Core |
| B3 | Origin and destination users | Missing | CP | Subjects are `merchant_id` + `pan_hash` (+ `customer_account_reference`, encrypted `customer_email`). | `origin_user_id` / `destination_user_id` linked to a Party model, so either side can be a consumer, a business or unknown. | Core |
| B4 | Origin/destination amounts, currencies and countries | Partial | CP | `amount_cents`, `currency`, `merchant_country`, DTO `countryCode`. The origin country is derived at ingest (`IpGeoService` → BIN fallback) but is **not stored**. | Destination amount/currency (FX), explicit origin/destination countries persisted on the transaction. | Core |
| B5 | Payment-details union (CARD, IBAN, ACH, UPI, WALLET, SWIFT, MPESA/mobile money, CHECK…) | Partial | CP | Card-centric only: `pan_hash`, `card_brand/type/class`, `BinLookupService`, `emv_tags`. Mobile money lives in a separate module (`controller/mobilemoney`, `MobileMoneyNetworkEdge` MIG V167); crypto is separate. | A polymorphic `payment_details` per side (method + typed fields) in the canonical transaction, so mobile money, bank and wallet rails go through the same pipeline. | Core |
| B6 | originFundsInfo, relatedTransactionIds, productType, promotionCode, reference, metadata, tags, jurisdiction, customObject | Missing | CP | Only `client_reference`, plus `emv_tags` JSON. | All of these fields, with tags and metadata usable as rule variables. | Important |
| B7 | Device info (fingerprint, OS, model, app version, IP, battery, emulator…) | Partial | CP | `device_fingerprint`, `ip_address` only. | A structured device object per side, persisted as a DeviceIdentity (see B9). | Core |
| B8 | IP → geo/country usable in rules; VPN/proxy | Partial | CP, Edge | `enrichment/IpGeoService` (ipapi.co + Redis) is used only for TRS at ingest. `IpReputationService`; edge `FIELD_MAP` has `ip_vpn_or_proxy`. The gap register says VPN detection is hard-coded prefixes and there is no impossible-travel / IP-vs-billing check. `RuleFeatureEnrichmentService` feature `ip_high_risk_country` actually uses **merchant country**, not IP. | Persist the IP country/ASN/proxy flags on the transaction, expose them as rule variables in both CP and Edge, fix the mislabeled feature, add impossible travel. | Core |
| B9 | Payment-identity normalization (stable identity across PAN/IBAN/wallet/MSISDN/device/IP) | Missing | CP | Only `pan_hash`. `distinct_payment_ids` in `RuleFeatureEnrichmentService` is actually distinct **devices**, and the "wallet" blacklist uses the device fingerprint. | A PaymentIdentity / DeviceIdentity / NetworkIdentity table with normalized, hashed keys and identity resolution at ingest (lifecycle stage 2). | Core |
| B10 | Idempotent ingestion | Implemented | CP | `Idempotency-Key` header; `(psp_id, client_reference)` uniqueness; MIG V221 `webhook_outbox_and_ingest_idempotency`. | — (keep as-is; extend to transaction events and user events). | Core |

### 2.3 Users: consumers, businesses, directors/shareholders/UBOs, events (target §4–5)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| C1 | Consumer user profile (name, DOB, nationality, residence, documents, contact, employment, source of funds, PEP status, KYC status, user state, tags) | Missing | CP, Console | No consumer entity in the transaction-monitoring path. `MultiAssetCustomer` (`multi_asset_customers`: psp_id, external_customer_id, customer_type, display_name, country_code, risk_tier, kyc_status) is a thin, separate module with its own `MultiAssetRiskEngine`. Console `pages/Customer360`. | A ConsumerUser entity linked to transactions as origin/destination, with full KYC attributes, state, tags, risk (KRS/CRA) and screening. | Core |
| C2 | Business user profile (legal entity, registration, MCC, financials, addresses, operating countries) | Partial | CP, Console | `entity/merchant/Merchant` (legal/trading name, registration_number, tax_id, kra_pin, cr12_number, is_pep, mcc, business_type, expected_monthly_volume, website, address, operating_countries, kyc_status, status, krs, cra). Console `pages/Merchants`, `pages/KycDocuments/KycMerchantDetailPage`. | Financials (revenue, turnover bands), a legal-entity type, tags, a user-state enum (status is free text), and support for business customers who are not merchants. `Merchant` is **not** `@Audited`. | Core |
| C3 | Directors, shareholders and UBOs on business users (screened as persons) | Partial | CP, Console | `entity/merchant/BeneficialOwner` + `OwnerScreeningResult`, `kyc/BeneficialOwnershipService`, `analytics/CorporateStructureService`, `corporate/CorporateIntelligenceService`, `OpenCorporatesClient`. `psp/cbk/PspDirector` / `PspShareholder` (+ Console `Psps` tabs) describe **the PSP itself** for CBK, not its customers. | Director and shareholder roles for merchants/business users (Ownership/DirectorRelationship with %, role, dates), each screened as a Person, with changes recorded as user events. | Core |
| C4 | User state and KYC status lifecycle with state-change events | Partial | CP | `Merchant.status` (free text, default `PENDING_SCREENING`), `kyc_status` (PENDING/APPROVED/REJECTED), `MERCHANT_STATUS_CHANGE` webhook type. | State/KYC enums with allowed transitions, reason and actor, written as UserEvents, and KYC-status webhooks. | Core |
| C5 | User tags (key/value) | Missing | CP | Not found on Merchant or MultiAssetCustomer. | Tags on users, settable by API and by rule-hit actions (F5). | Important |
| C6 | User events with a versioned UserSnapshot | Missing | CP | No user events table. `Merchant` is not Envers-audited, so there is no point-in-time snapshot. | A `user_events` table plus `user_snapshots` (version, hash), so each assessment references the snapshot version it used. | Core |
| C7 | Internal identity verification / document KYC | Partial | CP, Console | `underwriting/MerchantVerificationController` (MIG V202–V203), `kyc/*` (TriggerBasedKycService, PeriodicKycRefreshService, RiskBasedCddService), Console `pages/KycDocuments`. Internal (no third-party IDV), as stated. | Merchant/UBO only. No consumer IDV, and IDV results are not emitted as Findings. | Important |
| C8 | EDD workflow | Partial | CP, Console | `edd/EnhancedDueDiligenceService`, `EnhancedDueDiligenceRequest`, `EddEvidenceEvent` (MIG V122), AI `KYC_EDD` engine (advisory). The gap register says EDD is manual. | Automatic EDD triggers from risk band or findings, and EDD outcomes feeding back into CRA (risk override / lock). | Important |

### 2.4 Risk scoring: KRS / TRS / CRA (target §6–8)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| D1 | KRS: weighted average of configurable KYC risk factors | Partial | CP | `risk/RiskScoringService.calculateKrs(merchant)`: country 0.5, MCC 0.3 (the property is named `nationality`), new-merchant 0.2. Weights come from `@Value` properties. Persisted `merchants.krs`, `transactions.krs`. | Factor library and per-factor risk-level tables configurable by the PSP; consumer factors (nationality, residence, age, occupation, source of funds, PEP); business factors (legal type, UBO PEP, industry, years in business). | Core |
| D2 | TRS: transaction risk factors | Partial | CP | `RiskScoringService.calculateTrs(origin, dest, amount)` weights 0.3/0.3/0.4. Origin = IP country or BIN country (`TransactionIngestionService`). | Configurable factors (payment method, type, channel, device, IP/proxy, counterparty risk, currency, time of day), and factor results stored per assessment. | Core |
| D3 | CRA combining KRS and average TRS (one consistent formula) | Partial | CP | Ingest: `updateCra = (currentCra + trs)/2` saved raw (0–100) to `merchants.cra`. `RiskScoringService.calculateCra(features)` persists `cra/100` (0–1) plus a `merchant_risk_scores` row `rulesVersion="CRA-v1"` (MIG V104). `CustomerRiskProfilingService` (`/risk/customer/{merchantId}/rating`) and `RiskRulesEngine` (Easy Rules) are two more independent risk models. `calculateOverallRisk` has no production callers. | One CRA formula (KRS × w + avg(TRS) × (1−w)) on one scale, one source of truth, with the other models retired or turned into factors. | Core |
| D4 | Risk levels/bands VERY_LOW…VERY_HIGH with configurable boundaries | Missing | CP | `risk_level` strings LOW/MEDIUM/HIGH/CRITICAL. `ai/decision/AiBandsService` covers AI bands only. | A configurable 5-band scheme per tenant, used everywhere (rules, alert priority, monitoring cadence). | Core |
| D5 | Risk-band-driven rule parameters | Missing | CP, Edge | `RuleDefinition.parameters` is a single JSON object. | Parameter sets per band (`parametersByRiskLevel`), resolved at evaluation from the subject's CRA band, in both CP and the Edge bundle. | Core |
| D6 | Factor modes include / exclude / override | Missing | CP | Not found. | Per-factor mode, plus manual override of a factor value with reason/actor. | Core |
| D7 | CRA lock (manual override that freezes CRA) | Missing | CP, Console | Not found (`Merchant.cra` is overwritten on every ingest). | `cra_locked`, `locked_by`, `locked_until` and a reason, respected by all writers, with a Console control. | Core |
| D8 | Versioned risk configuration with comments and restore | Missing | CP, Console | Weights live in Spring properties. `merchant_risk_scores.rules_version` is the constant string `"CRA-v1"`. | `risk_model_versions` (snapshot, hash, comment, maker-checker, restore) like `rule_versions`, referenced by every risk result. Console risk-config page (none in `App.tsx`). | Core |
| D9 | Risk factor results stored per assessment (RiskAssessment / FactorResult) | Missing | CP | Only aggregate `krs/trs/cra` columns and `merchant_risk_scores` rows. | Per-factor contributions with the model version, emitted as RISK_FACTOR Findings. | Core |
| D10 | Country risk configuration | Partial | CP | `risk/CountryRiskScoreController`, `HighRiskCountryController`, `CountryRiskScore`, `HighRiskCountry`. | Not versioned. Console coverage not verified. Should become a factor table inside the versioned risk model. | Important |

### 2.5 Rules engine and DSL (target §9–12)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| E1 | Rule template library | Partial | CP | MIG V139/V141–V143 taxonomy and seeds, `RuleDefinition.category/typology/checks_for/external_code`, `rules/aml-rules.drl`, `RuleProvisioningService`. | Templates depend on hard-coded SQL features (E4). There is no parameter schema per template for safe instantiation, and no eligibility filters or risk-band params. | Core |
| E2 | Scenario / visual rule builder | Partial | Console, CP | `FE/components/VisualRuleBuilder.tsx` (used by `pages/RulesGeneration/RulesGenerationPage.tsx`): 9 fields, ops `>= <= > < == != contains in`, saves `ruleType SPEL` + `ruleJson {groups:[…]}`. | The output shape `{groups}` is not understood by `edge/EdgeRuleCompiler` (it expects `all/any/not/field/operator`), so visual rules are **skipped from the edge bundle**. Fields like `velocityScore` and `levenshteinDistance` do not map to real features. No aggregates, windows, list references, eligibility or risk-band params. | Core |
| E3 | Entity variables (transaction, user, payment, device fields) | Partial | CP, Edge | SpEL `#tx.*` (`rules/SpelRuleExecutor`), Drools facts, edge `FIELD_MAP` (15 fields: amount, country_code, merchant_id, mcc, currency, channel, pan_hash, ml_score, page_rank, connection_count, pan_txn_count_1h, krs, cra, trs, ip_vpn_or_proxy). | A single typed variable catalogue (FeatureDefinition) covering transaction, origin/destination user, payment identity, device, IP geo, tags/metadata, identical in CP and Edge. | Core |
| E4 | Aggregate variables (SUM/AVG/COUNT/MAX/MIN/UNIQUE) with time windows and group-by | Partial | CP, Edge | `rules/RuleFeatureEnrichmentService` / `RuleFeatureKeys`: ~50 hard-coded SQL features with fixed windows (1h/24h/7d/30d). Edge `AerospikeFeatureStore`: `pan_txn_count_1h/24h`, `pan_amount_sum_24h` (hourly buckets, 24h max). The gap register says there are no 1m/5m windows. | A declarative aggregate definition (function, field, window, group-by key, filters) compiled to SQL/Timescale in CP and to Aerospike counters at the Edge. | Core |
| E5 | Aggregation keyed by user / payment id / name / address / IP / device | Partial | CP, Edge | Keys used today: PAN hash and merchant everywhere. In CP: IP (`distinct_users_per_ip`, `payment_id_use_count` = IP txn count) and device (`distinct_users_per_payment_id` = merchants per device). Several features are mislabeled (`distinct_payment_ids` = devices; `distinct_banks` = card brands; `unique_counterparties` = merchants per PAN; `address_changes` = proxy). The Edge has PAN only. | Group-by over origin/destination user, normalized payment identity, name, address, device and IP, with correct labels. | Core |
| E6 | List variables (value in list X) | Partial | CP | `RuleFeatureEnrichmentService.enrichBlacklistHits` (pan/device/payment_details/wallet/card_country/reference_keyword) reads **Redis only** (`FeatureCacheService`), so it misses DB TTL entries. `DecisionEngine` blacklist check reads the DB. | List membership as a typed rule variable over the generic List model (I1), consistent between CP and Edge. | Core |
| E7 | Rich operators (between, null/not-null, fuzzy, array contains-any/all, regex, starts/ends) | Partial | CP, Edge | Edge `rule-core` / `EdgeRuleInterpreter`: EQ NE GT GTE LT LTE IN CONTAINS + all/any/not. SpEL can express more but is ungoverned free text. | between, is_null, regex, fuzzy (Levenshtein/Jaro), array any/all, date/time ops in the governed AST, implemented identically in Java and Rust. | Core |
| E8 | Transforms (lowercase, trim, normalize, substring, date parts, currency conversion) | Missing | CP, Edge | Not present in `rule_json` IR or `EdgeRuleCompiler`. | Transform nodes in the AST. | Important |
| E9 | Rule AST (single, versioned, compiled to Edge and CP) | Partial | CP, Edge | `rule_json` all/any/not IR compiled by `EdgeRuleCompiler` → `rule-core` (Rust) / `EdgeRuleInterpreter` (Java parity). CP also runs DRL and SpEL. | One AST as the source of truth for all rule types. DRL/SpEL limited to legacy/system rules, or compiled from the AST. Unknown fields/ops currently cause the rule to be **silently skipped** at the Edge (WARN only). | Core |
| E10 | Row Match (multi-field list row match, e.g. name+DOB+country) | Missing | CP | Lists are single-value (`payment_blacklist_entries.entry_value`, `CustomWatchlistEntry.entity_name`). | Multi-column list rows and a Row Match operator. | Core |
| E11 | Eligibility filters separate from detection logic | Missing | CP, Edge | `RuleDefinition.applies_to` (Transaction/User) only. | A filter block (txn types, payment methods, countries, user types/segments, risk bands, tags) evaluated before the detection logic, with "not eligible" recorded separately from "no hit". | Core |
| E12 | Rule execution logging with version provenance | Partial | CP | MIG V119 `rule_execution_logs` (rule_id, psp_id, txn_id, micros, result MATCH/NO_MATCH/ERROR, disposition back-fill). | `rule_version_id`, `assessment_id`, evaluated variable values (evidence) and the Edge bundle version. Edge hits are not logged in CP at all. | Core |
| E13 | Rule versioning and maker-checker | Implemented | CP, Console | MIG V160 `rule_versions` (snapshot JSONB, content_hash, change_type, lifecycle, reviewed_by, effective_from, supersedes), `RuleGovernanceService`, `RulesController /{id}/versions`, `/governance/versions/{vid}/approve|reject`, `/{id}/rollback`. `RuleDefinition` is `@Audited`. Console calls the governance endpoints from `FE/features/api/queries.ts` / `mutations.ts`. | Gaps sit downstream (E12, M4): versions are not referenced by executions, edge bundles or case alerts. | Core |
| E14 | One rule semantics across Edge and Control Plane | Partial | CP, Edge | The Edge evaluates the compiled `rule_json` subset. CP evaluates Drools + SpEL + `RegulatoryComplianceService` + `RiskRulesEngine` (Easy Rules). AGENTS.md documents three CP rule engines. | Shared AST/operators/features, a parity test suite between Rust/Java and CP, and an explicit placement for each rule (Edge-sync vs CP-async). | Core |

### 2.6 Execution and decisioning (target §13–14)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| F1 | Sync decision vocabulary ALLOW / FLAG / SUSPEND / BLOCK | Partial | CP, Edge | `DecisionEngine` actions BLOCK/HOLD/ALERT/ALLOW. `RulesExecutionService` maps SUSPEND→BLOCK and FLAG→REVIEW. `EdgeRuleCompiler.normaliseAction` maps FLAG→ALERT and SUSPEND→BLOCK. | Native FLAG and SUSPEND end to end (API, DB, Edge, webhooks, Console), with HOLD mapped to SUSPEND (owner decision). | Core |
| F2 | Compute all findings, then resolve deterministically (BLOCK > SUSPEND > FLAG > ALLOW) | Partial | CP, Edge | Compute-all exists inside `RulesExecutionService.strongestDecision` and edge `rule-core` (max severity). `DecisionEngine.evaluate` **short-circuits**: limits → blacklist → sanctions → cross-PSP → rule decision → ML thresholds → AML escalation. | Evaluate every engine, collect Findings, then resolve in one orchestrator with a documented precedence and tie-break. Today a limits BLOCK hides screening, rule and ML results from the record. | Core |
| F3 | Async post-processing rules with alert frequency (instant / daily / weekly / monthly) | Partial | CP | Scheduled jobs: `PeriodicRescreeningService`, `PeriodicKycRefreshService`, `PeriodicSanctionsScreeningService`, Kafka outbox consumers. | A rule execution mode `ASYNC` with an alert-creation frequency setting, run on the assessment after the sync decision. | Core |
| F4 | Sync vs async precedence (async never changes the returned decision; it can raise alerts and state changes) | Missing | CP | Not modelled. AI advisory runs asynchronously after the decision (`AiEngineAdvisor.adviseAsync`) but has no precedence model. | Explicit phases in the orchestrator and recorded `phase` on each Finding/action. | Core |
| F5 | Rule-hit actions on user state, KYC status, PEP flag and tags (for all / origin / destination) | Missing | CP | `RuleDefinition.action_type` is the transaction decision only. | A `hit_actions` structure per rule instance (set user state, KYC status, tag, add to list, create alert/case), applied by the state-propagation stage with audit. | Core |
| F6 | Rule-hit actions on lists (add counterparty/payment id to list for N days) | Partial | CP | Hard-coded: `DecisionEngine.takeBlockAction` adds the PAN to the blacklist for 30 days (DNH) on any BLOCK. | Configurable per rule (list, key, TTL, side), and tenant scoped (I3). | Core |
| F7 | Fail-closed behaviour on dependency failure | Implemented | CP, Edge | Rule errors → HOLD (`RulesExecutionService`); sanctions unavailable → HOLD/BLOCK; ML unavailable → HOLD (`ScoringService`); Edge feature store down → HOLD. | Keep it, and record dependency failures as Findings (`sourceType=SYSTEM`) so they are explainable. | Core |

### 2.7 Screening (target §15–16)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| G1 | Sanctions list ingest | Partial | CP | `sanctions/SanctionsDownloadController`, `SanctionsListController`, `SanctionsList`, `WatchlistUpdate`, MIG V2/V10; aml-microservice `SanctionsService` (Aerospike). The gap register says `sanctions.download.enabled=false` by default. | Enabled by default with monitored freshness. List version recorded on each screening result. | Core |
| G2 | PEP screening/tagging | Partial | CP | `Merchant.is_pep`, `BeneficialOwner` isPep, `merchant_pep_hit` feature. | PEP for consumers and directors/shareholders, a PEP tier/level, and PEP as a risk factor plus Finding. | Core |
| G3 | Adverse media | Partial | CP | `GdeltAdverseMediaClient`, AI `ADVERSE_MEDIA` engine (advisory). The gap register says it is company-level and flag-gated. | Person-level adverse media for UBOs, directors and consumers, with results stored as screening matches/Findings. | Core |
| G4 | Custom/internal watchlists | Partial | CP | `CustomWatchlist` + `CustomWatchlistEntry` (entity_name, entity_type, match_reason, risk_level). | No TTL, no multi-field rows, not tenant-scoped (not verified), and not part of the generic List model. | Core |
| G5 | Onboarding screening (business + owners) | Partial | CP, Console | `MerchantScreeningResult`, `OwnerScreeningResult`, `aml/AmlScreeningOrchestrator`, `BatchScreeningController`; Console `pages/Screening`, `components/ScreeningResults`. | Consumer onboarding screening, and director/shareholder screening (C3). | Core |
| G6 | Payment-time screening of counterparties | Partial | CP | `sanctions/RealTimeTransactionScreeningService`: screens the merchant name per transaction (cached). Counterparty (cardholder name from ISO CH_NAME / DE-43 / EMV 5F20) only if `screening.realtime.screen-counterparty=true` (default **false**). Any match → `blocking=true`. | Counterparty screening on by default (owner decision), score-threshold routing (FLAG/SUSPEND vs BLOCK), and screening of beneficiary names on non-card rails. | Core |
| G7 | Ongoing screening (rescreening + new list delta alerts) | Partial | CP | `PeriodicRescreeningService`, `PeriodicSanctionsScreeningService`, `NewListMatchAlertService`. The gap register says daily rescreening covers merchants + UBOs only. | Coverage of consumers, directors and counterparties, cadence by risk band, and each run recorded as ScreeningActivity. | Core |
| G8 | Fuzzy and transliteration matching | Implemented | CP | aml-microservice `SanctionsService` (DoubleMetaphone, Jaccard, Levenshtein, NFD transliteration); `sanctions/NameMatchingService`. | Configurable thresholds per screening configuration, plus the matching algorithm version recorded on the match. | Core |
| G9 | Separate screening rule types (transaction / consumer / business incl. directors & shareholders) with configurable thresholds | Missing | CP | `RuleDefinition.category=SCREENING` exists, but screening is hard-coded services, not rule instances. | A ScreeningConfiguration per subject type (lists, fuzziness, entity types, filters), each referenced by `ruleInstanceIds`. | Core |
| G10 | ScreeningActivity record (searchId, screeningId, entity, userId, txnId, isHit, isNew, reason, ruleInstanceIds) | Missing | CP | `TransactionScreeningResult` is in-memory; only reason strings reach `DecisionEngine`. | A persisted `screening_searches` / `screening_matches` per run, for both transaction and periodic screening. | Core |
| G11 | Screening hits as Findings with matchScore, disposition and side-by-side comparison | Partial | CP, Console | Merchant/owner results are persisted. `ScreeningOverride`, `ScreeningWhitelist` (entity_id, expires_at) give dispositions/whitelisting. Console `components/ScreeningResults`. AI `SANCTIONS_DISAMBIGUATION` advisory. | Transaction-time hits are not persisted. No SCREENING Finding, no side-by-side comparison of input vs list record, and the match disposition does not feed future screening of the same pair (beyond whitelist). | Core |

### 2.8 Alerts, cases and investigation (target §17–18)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| H1 | Alert fields (caseId, priority, tags, assignments, transaction ids, rule instance/version, nature) | Partial | CP | `entity/Alert` (`alerts`): single `txn_id`, score, action, reason, free-text status, `investigator` string, merchant_id, psp_id, multi_asset_customer_id, source_type/reference, severity, triggered_rules (names JSON), notes, disposition enum + reason/by/at. | `case_id` FK, priority (separate from severity), tags, assignment records, rule_instance_id + rule_version_id, status enum. | Core |
| H2 | Alert ↔ multiple transactions (AlertTransaction) | Missing | CP | Single `txn_id` per alert. | An `alert_transactions` join table, so an alert accumulates every transaction that hit the same rule for the same subject within the alert window. | Core |
| H3 | Alert consolidation and accumulation (per subject, or per counterparty) | Partial | CP | `DecisionEngine` creates one alert per action, possibly several per transaction. `case_management/CaseCreationService` keeps **one open case per merchant** and appends a `CaseAlert` per hit. | An alert-level consolidation key (rule instance + subject [+ counterparty]) with an open-alert window and a per-rule setting "consolidated vs per counterparty". | Core |
| H4 | Alert freeze status (stop accumulating new transactions) | Missing | CP | Not found. | A `frozen` flag/time on the alert, set on review start or by rule config. | Important |
| H5 | Alert lookup by transaction | Missing | CP, Console | No endpoint (see A9). | API, plus a Console transaction detail panel listing alerts/findings. | Core |
| H6 | Alert ↔ Finding link (exact findings and rule version that produced the alert) | Missing | CP | Alerts copy rule names as text. `CaseAlert.ruleVersion` is faked as `"v<year>.<month>"` from `updatedAt`, and the rule is resolved by name (`findFirstByNameOrderByIdAsc`), which can pick the system default instead of the PSP copy. | An `alert_findings` join, with Findings carrying `rule_version_id`. | Core |
| H7 | Case fields (alertIds, relatedCases, assignments, priority, SLA, queue, escalation) | Partial | CP, Console | `entity/compliance/ComplianceCase` (`@Audited`; status enum, priority, slaDeadline, assignedTo/By/At, escalated*, queue, relatedCases M2M `case_relationships`), `CaseAlert` (MIG V102, V230), `CaseTransaction`. Console `pages/Cases` (AllCases, Queues, Timeline, NetworkGraph). | `CaseAlert` duplicates alert data with **no FK to `alerts`**. There are separate `CaseEntity` / `MonitoringAlert` entities (parallel truths). Cases are merchant-keyed only. | Core |
| H8 | Case comments | Implemented | CP, Console | `CaseNote`, `case_management/CaseManagementController` activities, Console Cases timeline. | Comment attachments and mentions are optional. Align with a generic Comment model shared with alerts (H13). | Core |
| H9 | Case evidence with chain of custody | Implemented | CP, Console | `CaseEvidence`, `EvidenceChainOfCustody` + service, `EvidenceController`, `EvidenceStorageService`; SAR review evidence MIG V163. | Pre-signed upload (A10). | Core |
| H10 | Assignments and assignment history | Implemented | CP, Console | `case_management/CaseAssignmentService` (manual + auto-assign), `CaseActivity`, `CaseAuditLog`, Envers on `ComplianceCase`, `CaseManagementController` queues; Console `Cases/Queues`. | Case-level only; alerts have none (H13). | Core |
| H11 | Related cases | Implemented | CP, Console | `case_relationships` M2M, `CaseNetworkController /cases/{id}/network`, `CaseNetworkService`; Console `Cases/NetworkGraph`. | Relationship type/reason, and auto-linking from the shared Relationship graph (L1). | Important |
| H12 | Case/alert status webhooks | Partial | CP | `CASE_UPDATE` emitted from `CaseCreationService` on create/append. `AlertController PUT /{id}/status` emits no webhook. Emission on every case transition was not verified. | Webhooks on every alert and case status transition. | Core |
| H13 | Alert comments, assignment and evidence | Missing | CP, Console | `Alert.notes` text and `Alert.investigator` string only. | Comment/assignment/evidence objects on alerts with history. | Core |
| H14 | Dispositions feed back into future assessments | Partial | CP | `AlertController.resolve` back-fills `rule_execution_logs.disposition` for **all** rules on the transaction (not only the alert's rules) → `RuleEffectivenessService` → `AlertTuningService`. `FalsePositiveFeedbackService` / `FalsePositiveFeedback`; `ScreeningWhitelist` from screening FPs. The gap register says retraining does not exist. | Disposition stored per Finding/alert. True positives raise CRA / set user state / add to lists; false positives suppress the same pattern (whitelist with TTL). Feedback into ML labels and the Edge bundle. | Core |
| H15 | QA / SLA analytics | Partial | CP, Console | `CaseManagementController` SLA/metrics/dashboard, `ComplianceCase.slaDeadline`; Console `Analytics`, `ReportsCenter`. | QA sampling of closed alerts/cases, investigator-level quality metrics. | Advanced |

### 2.9 Lists (target §19)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| I1 | List objects with types (blacklist / whitelist / custom), subtypes (user id, payment id, device, IP, name, country…) | Partial | CP | `PaymentBlacklistEntry` (entry_type, entry_value), `CustomWatchlist(+Entry)`, `ScreeningWhitelist`. Three unrelated stores. | A generic `lists` / `list_entries` model with list type and key type, used by rules, screening and Edge. | Core |
| I2 | TTL per entry | Partial | CP | `payment_blacklist_entries.expires_at` (MIG V204), `ScreeningWhitelist.expires_at`. The Redis blacklist set holds permanent entries only. `CustomWatchlistEntry` has no TTL. | Uniform TTL handled the same in DB, Redis and Edge. | Core |
| I3 | Tenant scoping of lists | Missing | CP | `payment_blacklist_entries` is UNIQUE(entry_type, entry_value) with **no psp_id**. Redis keys `aml:blacklist:{type}` are not tenant-scoped. | `psp_id` on all list data (with optional platform-wide lists explicitly marked). This is also a data-isolation issue. | Core |
| I4 | Metadata and multi-field rows | Missing | CP | Single value + reason. | JSON metadata, multi-column rows (feeds Row Match E10). | Core |
| I5 | List versioning and audit | Missing | CP | No list versions. Entries carry only `created_by`. | `list_versions` or entry-level audit events, with the list version recorded on ListMatch Findings. | Core |
| I6 | Automatic list actions from rules/dispositions | Partial | CP | Hard-coded 30-day PAN blacklist on BLOCK (`DecisionEngine.takeBlockAction`). | Configurable actions (F6), and disposition-driven whitelist/blacklist actions (H14). | Core |
| I7 | List-updated webhooks | Missing | CP | Webhook types RISK_ALERT, CASE_UPDATE, MERCHANT_STATUS_CHANGE only. | A `LIST_UPDATED` event. | Important |
| I8 | Console list management | Missing | Console | No list route in `FE/App.tsx`. A code search for `blacklist` in FRONTEND finds only text in `RulesGenerationPage.tsx`. | A Lists page (CRUD, import, TTL, history). | Core |

### 2.10 Dynamic anomaly rules, ML and AI (target §20–21)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| J1 | Dynamic baseline anomaly rules (per-entity baseline vs current) | Partial | CP | Deviation features `txn_count_deviation_ratio`, `volume_deviation_ratio`, `avg_amount_spike_ratio`, `peer_group_volume_ratio` (MIG V232 peer group rule), `analytics/BehavioralProfilingService`, `BehavioralAnalyticsService`. | Baselines are hard-coded SQL keyed on PAN/merchant. No configurable baseline window/metric/deviation per rule, none at the Edge, and none for consumers/payment identities. | Important |
| J2 | ML anomaly / ML scoring | Partial | CP | `ScoringService` (XGBoost via `scoring.service.enabled`, default **false** → ml_score 0), `deeplearning/DL4JAnomalyService` (`dl4j.enabled=false`), MIG V190 model scoring evidence. | Off by default (owner decision). When on, the output is a score in `riskDetails`, not an ML Finding with model version and feature references. No training/labels pipeline from dispositions. | Important |
| J3 | AI rule builder | Partial | CP, Console | `ai/AiRuleGeneratorService`, `RulesController /rules/generate` (preview, not persisted; needs `AI_RULE_GENERATOR_ENABLED` + key), prompt `hokeka-ai/rule_suggestion`. | Output should be a draft rule in the AST that enters the governance flow (draft → simulate → shadow → approve). It cannot be simulated today (no simulation, M2). | Advanced |
| J4 | Threshold recommender | Partial | CP | `alert/AlertTuningService.suggestTuning` (FP-rate heuristic: raise first sensitivity parameter by 10%/25%), `applyRecommendation` → `RuleGovernanceService.proposeUpdate` (maker-checker). `AlertTuningController`, MIG V150. | No backtest of the proposed threshold against history. Heuristic only. Uses rule-name lookup (`findFirstByNameOrderByIdAsc`). | Advanced |
| J5 | AI forensics / investigation assistance | Partial | CP, Console | AI engines `ALERT_TRIAGE`, `CASE_TRIAGE`, `SANCTIONS_DISAMBIGUATION`, `KYC_EDD`, `G2_CONTENT`, `ADVERSE_MEDIA`, `FRAUD_SCORING`, `TRANSACTION_RISK` (MIG V233, `advisory_only` default TRUE), `ai/decision/AiDecisionGateway`, `AiAuditService`, `AiDisclosureSanitizer` (provider never named). `compliance/SarContentGenerationService`. Console `components/Ai/AiVerdictPanel`, `Settings/AiSettingsTab`. Shadow by default with rules fallback. | AI output lives in `ai_decision_audit` (a separate truth). It should be written as Findings (`sourceType=ML/AI`, model id, prompt version, cited signals) linked to the assessment, alert or case. | Advanced |
| J6 | AI writes Findings, never a separate decision truth | Missing | CP | `ai_decision_audit.baseline_decision/final_decision` duplicates decision state. | Route AI output through the Findings bus. The orchestrator alone decides, with AI influence gated by `advisory_only`. | Important |

### 2.11 AssessmentContext, Findings, orchestrator and the 14-stage lifecycle (target §22–26)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| K1 | AssessmentContext (one shared context per trigger) | Missing | CP, Edge | Pieces are scattered: `TransactionFeatures` (`transaction_features.feature_json`, `risk_details`, `model_version`), `riskDetails` map in `ScoringService`, Edge feature map. | An `assessments` record + in-memory context (assessmentId, trigger, txn snapshot, subjects + snapshot versions, payment identities, devices, network, historical features, risk, screening, list matches, findings, alerts, cases, prior state changes, candidate actions, final decision, model/rule/list/risk versions, evidence refs). | Core |
| K2 | Common Finding model with provenance | Missing | CP | Engine outputs are strings (`reasons`), JSON names (`triggered_rules`), `rule_execution_logs` rows, `CaseAlert` copies and `ai_decision_audit` rows. | A `findings` table (findingId, assessmentId, txnId, userId, counterpartyId, sourceType, sourceId + sourceVersion, nature, severity, triggered, score, evidence, featureReferences, explanation, proposedActions, createdAt). | Core |
| K3 | Findings record the versions used | Partial | CP | `transaction_features.model_version`, `ai_decision_audit.prompt_version/model_id`, edge bundle version (`/edge/status`). | rule_version_id, risk_model_version, list_version, screening_config + list snapshot version, feature definition version, user snapshot version. | Core |
| K4 | Single decision orchestrator | Partial | CP | `DecisionEngine` (short-circuit, F2). Pre-decision set in `TransactionIngestionService` from TRS, then overwritten. The Edge decides independently. | One orchestrator consuming Findings with BLOCK > SUSPEND > FLAG > ALLOW, emitting Decision + DecisionActions. | Core |
| K5 | Decision and DecisionAction records | Partial | CP | `transactions.decision`, `rule_decision`, `triggered_rules`, `transaction_features.risk_details`. | `decisions` + `decision_actions` (alert created, list add, state change, webhook) linked to findings. | Core |
| K6 | Stage 1: Ingest | Partial | CP, Edge | `/transactions/ingest`, `/ingest/batch`, `/edge/evaluate`, Kafka outbox `transactions.raw` (MIG V188). | No transaction/user event triggers, and the Edge and cloud ingests are not linked. | Core |
| K7 | Stage 2: Identity resolution | Missing | CP | — | Resolve origin/destination parties, payment/device/network identities (B9). | Core |
| K8 | Stage 3: Hydration (user snapshot, history, lists, risk, prior state) | Partial | CP | `RuleFeatureEnrichmentService`, `FeatureStoreService`, `OptimizedFeatureExtractionService`, Aerospike/Redis caches. | Hydrated data is not frozen into a context with versions. Three caches can disagree. | Core |
| K9 | Stage 4: Feature computation | Partial | CP, Edge | Hard-coded features (E4); Edge 3 features. | Feature registry with definitions/versions shared with the Edge. | Core |
| K10 | Stage 5: Risk scoring | Partial | CP | `RiskScoringService` (D1–D3). | Versioned model, factor findings, bands. | Core |
| K11 | Stage 6: Screening | Partial | CP | `RealTimeTransactionScreeningService` (G6). | Persisted activity + findings (G10–G11). | Core |
| K12 | Stage 7: Rules | Partial | CP, Edge | `RulesExecutionService`, Edge `rule-core`. | Findings with rule versions, eligibility, hit actions. | Core |
| K13 | Stage 8: ML | Partial | CP | `ScoringService` (off by default). | ML Findings. | Important |
| K14 | Stage 9: Findings consolidation | Missing | CP | — | Dedupe/merge findings per nature/subject before decision. | Core |
| K15 | Stage 10: Decision | Partial | CP, Edge | `DecisionEngine`, Edge `EdgeEngine`. | K4. | Core |
| K16 | Edge decision carried into the Control Plane assessment (one decision per transaction). _2026-10-17: now Partial, see section 0._ | Missing | Edge, CP | `docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`: the Edge creates no cloud transaction/alert/webhook, the integrator must dual-post to `/ingest`, and the cloud re-decides. `TransactionRequestDTO` has no edge-decision field. | The Edge ships its Decision + Findings (bundle + rule versions) to CP (via `MetricsShipper`-like channel or ingest field). The CP assessment adopts the Edge sync decision as authoritative for the transaction and adds async findings. | Core |
| K17 | Stage 11: Alert/Case creation from findings | Partial | CP | `DecisionEngine` alerts, `CaseCreationService`, `AlertToCaseService`. | Created from findings, with links (H2, H6). | Core |
| K18 | Stage 12: State propagation (user state, tags, lists, CRA) | Partial | CP | CRA update at ingest, hard-coded PAN blacklist on BLOCK. | Configurable actions (F5/F6) recorded as DecisionActions. | Core |
| K19 | Stage 13: Webhooks | Partial | CP | Outbox webhooks (O1–O3). | Full event catalogue (O3). | Core |
| K20 | Stage 14: Future effect (outcomes feed the next assessment) | Partial | CP | Rolling CRA, history-based features, whitelist after FP. | Dispositions, state changes and list changes visible to the next AssessmentContext with versions (H14). | Core |

### 2.12 Relationships and network (target §27)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| L1 | Relationship object (type, confidence, validity period, evidence) | Partial | CP | `MultiAssetCustomerRelationship` (MIG V151–V153), `MobileMoneyNetworkEdge` (MIG V167), `entity/graph/*` Neo4j nodes, `CaseNetworkService`, `analytics/LinkAnalysisService`. Each is a separate, module-specific graph. | One `relationships` table (party↔party, party↔identity) with confidence, valid_from/to and evidence, fed by identity resolution, ownership and shared identities. | Important |
| L2 | Network analysis producing findings | Partial | CP, Console | `network/GraphAnalysisController`, `graph/Neo4j*` (Neo4j **off** by default; the gap register says the Cypher is dead code). Edge `FIELD_MAP` has `page_rank`/`connection_count`. Console `pages/Network/NetworkAnalysisPage` (disabled state). | NETWORK Findings computed from L1 (SQL first; Neo4j optional, owner decision). | Important |

### 2.13 Feedback, simulation, shadow, lineage, versioning (target §28–30)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| M1 | Rule lifecycle configure → simulate → shadow → live | Partial | CP | `RuleLifecycleStatus` DRAFT, PENDING_APPROVAL, APPROVED, ACTIVE, REJECTED, SUPERSEDED, RETIRED. | SIMULATING and SHADOW states with gates, e.g. "must have a simulation run before approval". | Important |
| M2 | Simulation / backtest (SimulationRun) | Missing | CP, Console | No simulation service or route. `alert/RuleAbTestingService` only stores variants and TP counts (`RuleAbTest`, `RuleAbTestResult`). No evidence it is called from rule execution. | Replay of historical transactions/assessments against a draft rule version, with hit/alert/FP-estimate reports and a Console page. | Important |
| M3 | Shadow rules (ShadowRun) | Missing | CP, Edge | AI has shadow mode (`advisory_only`, `psps.ai_inline_mode=false`); rules have none. | Rule instances in SHADOW mode produce Findings flagged `shadow=true` that never affect the decision, with a comparison dashboard. | Important |
| M4 | "Which rule version generated this alert?" | Missing | CP | Fake `ruleVersion "v<year>.<month>"` in `CaseCreationService.triggerCaseFromRule`. `rule_execution_logs` has no version. The Edge bundle version is not tied to `rule_versions`. | Findings carry `rule_version_id`, alerts link findings, and the Edge bundle manifest lists `rule_version_id`s. | Core |
| M5 | Version everything (rules, risk config, lists, screening config, models, prompts, edge bundles, users) | Partial | CP, Edge | Rules: yes (V160). Prompts: `ai_engine_settings.prompt_version`. Models: `model_version` string. Audit: Envers on rules/cases/SAR. Missing for risk config, lists, screening config, users (Merchant not audited), feature definitions. Edge bundle version = `max(updated_at)*1000+count`. | Versioned tables for each, plus a content-hash bundle manifest. | Core |
| M6 | Tamper-evident audit trail | Implemented | CP, Console | MIG V232 audit hash chain; Envers `@Audited` on `RuleDefinition`, `ComplianceCase`, `SuspiciousActivityReport`; Console `pages/AuditLogs`. | Extend to findings/decisions (append-only). | Core |

### 2.14 SAR (target §31)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| N1 | SAR as a first-class object with lifecycle | Implemented | CP, Console | `entity/compliance/SuspiciousActivityReport` (`@Audited`; status `SarStatus` DRAFT…, maker/reviewer/approver/filer, jurisdiction, filing deadline + policy, `suspicion_arose_at`, narrative, case link, `sar_transactions` M2M, amendments `amends_sar_id`), `compliance/SarWorkflowController`, `SarWorkflowService`, `SarTemplate`, MIG V4/V163. The gap register says FILED is impossible without a receipt. Console `TransactionMonitoring/Sars`, `pages/RegulatoryReports`. | Links to alerts and findings (only case + transactions). | Advanced |
| N2 | SAR lifecycle events (SARMessage, webhooks) | Partial | CP | `reporting/RegulatorySubmission` + `RegulatorySubmissionAttempt`, `RegulatorySubmissionController`. | SAR status webhooks (target lists SAR events) and regulator message/acknowledgement objects. | Advanced |
| N3 | SAR transport per jurisdiction | Partial | CP | `client/regulator/FincenSubmissionClient`, `FcaSubmissionClient`, `FrcSubmissionClient`, `OfacSubmissionClient`, `CtrFilingBridgeService`; Console `RegulatoryReports/CbkSubmissions`. | Which transports are production-enabled per tenant/jurisdiction is an owner decision. | Advanced |

### 2.15 Webhooks (target §1)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| O1 | HMAC-SHA256 signed webhooks with timestamp | Implemented | CP | `psp/WebhookService.sign` hex HMAC-SHA256 over `"<timestamp>.<body>"`, headers `X-Hokeka-Signature`, `X-Hokeka-Timestamp`, `X-Hokeka-Event`; https-only subscriptions. | Secret rotation with an overlap window (O4). | Important |
| O2 | Durable delivery with retries and backoff | Implemented | CP | `WebhookOutboxService` / `WebhookOutboxDispatcher` / `WebhookDeliveryClient`, `event_outbox` channel WEBHOOK (MIG V221): attempts, last_error, exponential backoff to 300s, max 15 attempts, FAILED status, auto-disable after 5 consecutive failures. | Remove the legacy direct `@Async WebhookService.sendWebhook` path (two delivery paths). | Important |
| O3 | Event catalogue (user state, KYC, tags, CRA, txn status, case/alert status, batch, list updates, SAR) | Partial | CP | Types RISK_ALERT, CASE_UPDATE, MERCHANT_STATUS_CHANGE only (`WebhookSubscriptionController`). | All other target events. | Important |
| O4 | Failed-delivery management | Missing | CP, Console | See A13. | List/retry/replay/re-enable/rotate. | Important |
| O5 | Console webhook management | Partial | Console | `pages/Settings/WebhooksTab`: create/delete, shows active + failureCount. Copy says "Only RISK_ALERT is currently delivered", which is stale now that `CaseCreationService` emits CASE_UPDATE. | Delivery log, retry, event-type selection for all types, accurate copy. | Important |

### 2.16 CRM, transaction events, batch (target §1, §32)

| ID | Capability | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| P1 | CRM linkage (CRMRecord on parties/cases) | Missing | CP, Console | 0 code hits for `crm`. | CRM record model + link API + Console field on case/party. | Important |
| P2 | Transaction events re-triggering assessment | Missing | CP | See A3/B2. | Event → new assessment (trigger=TXN_EVENT) referencing the prior assessment. | Core |
| P3 | Batch ingestion with status and results | Partial | CP | See A2. | Batch resource + webhook. | Core |

### 2.17 Minimum data model (target §32)

| ID | Target entity group | Status | Module | Evidence | What is missing | Priority |
|---|---|---|---|---|---|---|
| Q1 | Party, ConsumerUser, BusinessUser, LegalEntity, Person | Partial | CP | `Merchant` (business), `BeneficialOwner` (person-ish), `MultiAssetCustomer` (separate). | A unified Party with consumer/business subtypes and Person/LegalEntity. | Core |
| Q2 | Ownership / DirectorRelationship | Partial | CP | `BeneficialOwner` (UBO only). | Directors, shareholders, %, dates. | Core |
| Q3 | PaymentIdentity, DeviceIdentity, NetworkIdentity | Missing | CP | — | See B9. | Core |
| Q4 | Transaction, TransactionEvent | Partial | CP | `TransactionEntity`; no events. | B1–B6, A3. | Core |
| Q5 | UserEvent (+ snapshot) | Missing | CP | — | C6. | Core |
| Q6 | RiskModel, RiskFactor, RiskFactorVersion, RiskAssessment, RiskFactorResult | Missing | CP | Only `merchant_risk_scores` (MIG V104) and `country_risk_scores` / high-risk countries. | D1–D9. | Core |
| Q7 | Rule, RuleVersion, RuleInstance, RuleExecution, RuleFinding | Partial | CP | `rule_definitions`, `rule_versions` (V160), `rule_execution_logs` (V119). | RuleInstance separation, version on execution, RuleFinding. | Core |
| Q8 | ScreeningConfiguration, ScreeningSearch, ScreeningMatch, ScreeningEvidence | Partial | CP | `MerchantScreeningResult`, `OwnerScreeningResult`, `ScreeningOverride`, `ScreeningWhitelist`. | Configuration, search and match records for all subjects and the transaction path. | Core |
| Q9 | FeatureDefinition, FeatureSnapshot, AggregateObservation | Partial | CP, Edge | `transaction_features` (feature_json snapshot), `feature/FeatureStoreService`, Aerospike buckets, TimescaleDB (MIG V215). | Feature definition registry with versions. | Core |
| Q10 | List, ListVersion, ListEntry, ListMatch | Partial | CP | Three separate list stores (I1). | Generic model + versions + matches. | Core |
| Q11 | Finding, Decision, DecisionAction | Missing | CP | — | K2, K5. | Core |
| Q12 | Alert, AlertTransaction, AlertFinding | Partial | CP | `alerts`. | Join tables (H2, H6). | Core |
| Q13 | Case, CaseAlert, CaseFinding, CaseComment, CaseAssignment | Partial | CP | `compliance_cases`, `case_alerts` (no FK to alerts), `CaseNote`, `CaseActivity`. | FK alert link, CaseFinding. | Core |
| Q14 | Evidence | Implemented | CP | `CaseEvidence`, `EvidenceChainOfCustody`, `sar_review_evidence` (V163). | — | Core |
| Q15 | Disposition | Partial | CP | `AlertDisposition` enum on `alerts`, `rule_execution_logs.disposition`, `FalsePositiveFeedback`. | Disposition entity per alert/finding with reason codes and feedback actions. | Core |
| Q16 | CRMRecord, Relationship | Missing | CP | Module-specific relationship tables only (L1). | P1, L1. | Important |
| Q17 | SAR, SARSubmission, SARMessage | Partial | CP | `suspicious_activity_reports`, `regulatory_submissions(+attempts)`. | SARMessage/acks + SAR webhooks. | Advanced |
| Q18 | WebhookEvent, WebhookDelivery | Partial | CP | `event_outbox` (WEBHOOK) rows double as event+delivery. | Per-attempt delivery history, failed-delivery API. | Important |
| Q19 | AuditEvent | Implemented | CP | Audit log with hash chain (V232), Envers. | — | Core |
