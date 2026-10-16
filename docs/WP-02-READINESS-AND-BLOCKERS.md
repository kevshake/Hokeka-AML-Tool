# WP-02 readiness: blockers and unified fix plan

**Purpose:** After WP-01 (shadow Assessment + Finding ledger, PR #29), this document lists everything that must be resolved before—and during—a **single coordinated cutover** to WP-02 (compute-all `DecisionOrchestrator`, ALLOW/FLAG/SUSPEND/BLOCK). Use it as the master checklist so engineering, product, and ops can fix blockers **in one program of work**, not as a sequence of surprise dependencies.

**References:** `IMPLEMENTATION_PLAN.md` (WP-00, WP-0A, WP-01, WP-02, WP-03, WP-04), `GAP_ANALYSIS.md` (F1, F2, K4, K16, D3, D-7, D-9, D-15), `ARCHITECTURE_DELTA.md`, `docs/AML-FRAUD-COVERAGE-GAP-REGISTER.md`, `hokeka/flagright-research-target-model.md`.

**WP-01 delivered (baseline):** Flyway `V238`, `assessments` / `findings`, `rule_execution_logs.rule_version_id` + `assessment_id`, shadow hooks on ingest, read APIs, `assessmentId` on ingest response. **Decision behaviour unchanged.**

---

## 1. What “fix all at once” means

WP-02 is not a isolated refactor of `DecisionEngine`. It touches vocabulary, persistence, side effects, API contract, Edge parity, Console UX, and shadow comparison. A **unified release** should include:

| Track | Outcome |
|--------|---------|
| **Hygiene (WP-0A, WP-00)** | One CRA scale, no ingest pre-decision, tenant-scoped blacklist truth (if D-6 confirms) |
| **Orchestration (WP-02)** | Compute-all engines → findings → resolution; shadow flag → live cutover |
| **Provenance (WP-01 finish + WP-03 start)** | Rich `versions` JSON, context hash, alert/rule links (minimal slice for ingest explainability) |
| **Edge (WP-02 + WP-04 slice)** | FLAG/SUSPEND in `rule-core`; bundle manifest with `rule_version_id`s (no full adopt mode required for first live cutover if dual-post unchanged) |
| **Console (WP-01b + WP-02 UI)** | Assessment panel + FLAG/SUSPEND chips |
| **Product sign-off** | D-7, D-9, D-15 (and D-6 if WP-00 in scope) |

Trying to ship WP-02 live **without** the hygiene and API/Edge rows below will reproduce today’s silent disagreements (scale, short-circuit, collapsed actions, Edge vs cloud).

---

## 2. Owner decisions (must be recorded before build)

These are **hard gates**. Without written defaults, implementers will guess and parity tests will fail.

| ID | Decision | Blocks | Recommended default (if owner silent) |
|----|----------|--------|--------------------------------------|
| **D-7** | HOLD → SUSPEND, ALERT → FLAG; meaning of SUSPEND for PSPs; legacy `action` deprecation window | API, webhooks, Console, DB columns | Map HOLD→SUSPEND, ALERT/FLAG→FLAG; keep `action` on ingest for 1 release; add `decision` field |
| **D-9** | Edge authority vs cloud on edge rails; p99 budget | WP-04 adopt mode priority | Cloud orchestrator live for non-edge; edge stays authoritative on edge rails until WP-04 (document only in WP-02) |
| **D-15** | Findings/assessment retention; partitioning | Ops, migration strategy | 7 years assessments + findings; monthly partition already optional in V238 |
| **D-6** | Global vs tenant blacklist on BLOCK | WP-00 + list findings | Defer WP-00 from WP-02 cutover unless owner picks tenant scope |
| **D-3** | ML on/off in production | ML findings weight in resolution | Keep ML advisory in shadow; resolution uses ML findings but does not auto-BLOCK until D-3 yes |
| **D-12** | AI beyond shadow | AI finding → resolution | AI findings **excluded** from resolution in WP-02 live (same as today) |

Document choices in `docs/PRODUCT-DECISIONS.md` or the gap register before merging WP-02 live.

---

## 3. WP-01 gaps still open (complete in same program as WP-02)

These were in WP-01 scope but **not** in PR #29. They block a trustworthy orchestrator and Console “explain”.

### 3.1 Console: Assessment panel (WP-01b)

| Current | Target | Fix |
|---------|--------|-----|
| No route/component loads `/transactions/{id}/assessment` | Transaction detail shows assessment + findings | **FE:** `FRONTEND/src/pages/TransactionMonitoring/` (Live drawer) and/or `RecordDetail`; React Query hook calling `GET /api/v1/transactions/{id}/assessment`; render finding list (sourceType, severity, triggered, explanation—no AI vendor text) |
| `docs/UI_COVERAGE_MATRIX.md` not updated | Matrix row for assessment explain | Add row; link to API |

**Acceptance:** Operator opens a txn → sees same findings count/types as API; PSP role cannot see other tenant.

### 3.2 Assessment `versions` and `context_hash`

| Current | Target | Fix |
|---------|--------|-----|
| `TransactionAssessmentService.buildInitialVersions()` only sets `riskModel: CRA-v1`, `assessmentLedger: WP-01-shadow` | Snapshot of rule bundle, screening list versions, risk model version, feature-def version (per target model) | **BE:** After scoring/rules, merge into `assessments.versions` JSON: `ruleBundleHash`, `ruleVersionIds[]`, `screeningListVersion`, `riskModelVersion`, `mlModelVersion`; compute `context_hash` (SHA-256 of canonical txn + merchant snapshot ids) |
| No frozen context payload | Optional `context_snapshot` or hash-only pointer | Phase 1: hash + txn id + merchant id; Phase 2: JSONB snapshot column if needed for replay |

**Evidence:** `BACKEND/.../TransactionAssessmentService.java` (`CRA-v1` constant).

**Acceptance:** `GET /assessments/{id}` returns non-empty `versions` on a typical ingest with rules enabled.

### 3.3 Engine coverage on short-circuit paths

| Current | Target | Fix |
|---------|--------|-----|
| `DecisionEngine.evaluate` **returns early** on limits / blacklist / screening / cross-PSP | WP-02 compute-all: every sync engine runs; findings already exist for paths that **ran** | **WP-02:** Remove early return; run all sync engines, collect findings, **then** resolve. Until then, assessments on BLOCK-at-limits txs **lack** screening/rule/ML findings |
| Async AI finding may arrive after assessment `completeAssessment` | AI findings still link via txn → latest assessment lookup | **BE:** `AiDecisionGateway.recordAiFinding` already re-binds scope via `findFirstByTxnIdOrderByCreatedAtDesc`; optionally delay assessment finalize until async phase boundary or attach AI findings to same assessment id explicitly in context |

**Acceptance (WP-02):** Integration test: limits BLOCK txn still has SCREENING + RULE findings with `triggered=false` or explicit “not evaluated” SYSTEM finding—prefer **all engines executed**.

### 3.4 Performance budget (WP-01 acceptance)

| Current | Target | Fix |
|---------|--------|-----|
| Async batched finding writes; no p95 proof | p95 ingest regression ≤ 5 ms vs baseline | Add benchmark test or load test job; tune `hokeka.assessment.finding.batch-size`; consider outbox for findings if JDBC contends |

---

## 4. WP-0A / WP-00 hygiene (strongly recommended in same release)

### 4.1 WP-0A — Risk score hygiene [D3, S3, S5]

| Issue | Evidence | Fix |
|-------|----------|-----|
| CRA written 0–100 at ingest, 0–1 elsewhere | `TransactionIngestionService` `updateCra`; `RiskScoringService.calculateCra` | Pick **0–100** everywhere; migration `V237__cra_scale_normalize.sql`; single formula KRS×w + avg(TRS)×(1−w) |
| TRS pre-decision at ingest (duplicate truth) | Ingest sets decision from TRS before orchestrator | Remove pre-decision; orchestrator/orchestrator-only decision |
| MCC weight property misnamed `nationality` | `RiskScoringService` @Value | Rename property; config docs |

**Why it blocks WP-02:** RISK_FACTOR findings and band-driven rule params (later WP-12) need one CRA scale. Resolution tie-breaks using severity + score assume comparable numbers.

### 4.2 WP-00 — Blacklist tenant scope [D-6]

| Issue | Evidence | Fix |
|-------|----------|-----|
| Global blacklist; Redis vs DB TTL mismatch | `payment_blacklist_entries`, `RuleFeatureEnrichmentService` vs `DecisionEngine` | `V236`, tenant Redis keys, enrichment uses `PaymentBlacklistService` |

**Why it blocks WP-02:** LIST findings and BLOCK `DecisionAction` must target the **correct tenant list**. Skip if D-6 chooses global consortium behaviour explicitly.

---

## 5. WP-02 core — DecisionOrchestrator (main body of work)

### 5.1 Short-circuit vs compute-all

| Current | Target | Fix |
|---------|--------|-----|
| `DecisionEngine.evaluate`: limits → hard rules → rules → ML thresholds | All sync engines run; findings bus; then resolve | Introduce `DecisionOrchestrator` (evolve `DecisionEngine`): stages: limits, list, screening, cross-PSP, rules, ML thresholds, AML escalation; each stage **records finding** (already via `AssessmentEngineRecorder`) and returns proposed action; **resolver** applies BLOCK > SUSPEND > FLAG > ALLOW |
| Hidden engines on early BLOCK | Full explainability | Same refactor; no `return` until resolver completes |

**Files (primary):** `DecisionEngine.java` → split `DecisionOrchestrator` + `DecisionResolver`; `FraudDetectionOrchestrator` / async / ultra call orchestrator only.

**Tests:** Precedence table (matrix of finding severities/actions); golden cases from `DecisionEngineTest` must pass with **same legacy action** when `hokeka.decision.orchestrator=shadow` and legacy path kept.

### 5.2 Vocabulary: ALLOW / FLAG / SUSPEND / BLOCK

| Current | Target | Fix |
|---------|--------|-----|
| ALLOW, ALERT, HOLD, BLOCK | ALLOW, FLAG, SUSPEND, BLOCK | D-7 mapping; `transactions.decision_v2` (V239); keep `decision` / `action` legacy columns populated via mapper |
| `mapRuleDecisionToAction`: SUSPEND→BLOCK | Native SUSPEND | `RulesExecutionService.normalizeRuleAction`, `DecisionEngine.mapRuleDecisionToAction` |
| `EdgeRuleCompiler.normaliseAction`: FLAG→ALERT, SUSPEND→BLOCK | Edge emits FLAG/SUSPEND | Update `IR_ACTIONS`, Rust `rule-core` enum, `EdgeRuleInterpreter`, bundle compatibility tests |

**Migration:** `V239__decisions_actions.sql` — `decisions`, `decision_actions`, `transactions.decision_v2`.

**API (additive v1):** Ingest response fields: `decision`, `hitRules[]`, `executedRules[]`, `assessmentId` (partially done); deprecate mapping table in OpenAPI.

### 5.3 Side effects → DecisionAction

| Current | Target | Fix |
|---------|--------|-----|
| Inline `takeBlockAction`, `createAlert`, `caseCreationService`, blacklist add, webhooks inside `DecisionEngine` | Orchestrator emits `DecisionAction` rows; executor applies | Extract `DecisionActionExecutor`: ALERT_CREATE, CASE_CREATE, LIST_ADD, WEBHOOK_RISK_ALERT; idempotent per txn+action type |
| Assessment stores `decision` string only | Link `decisions` row to `assessment_id` | FK assessment → decision; store `reason_finding_ids[]` |

### 5.4 Shadow mode and cutover

| Current | Target | Fix |
|---------|--------|-----|
| No flag | `hokeka.decision.orchestrator=legacy|shadow|live` | **shadow:** run legacy + orchestrator; log disagreements to table or metric; **live:** orchestrator only |
| N/A | Disagreement dashboard / alert | Scheduled job: count mismatch by PSP; gate flip on ≤ agreed rate |

**Property:** add to `application.properties` + `BACKEND/DEPLOYMENT.md`.

**Acceptance:** 7 days shadow with &lt;0.1% disagreement on action (owner threshold) before `live`.

### 5.5 Three fraud orchestrators

| Current | Target | Fix |
|---------|--------|-----|
| `FraudDetectionOrchestrator`, `AsyncFraudDetectionOrchestrator`, `HighConcurrencyFraudOrchestrator` duplicate pipelines | WP-06: one pipeline, execution strategies | For WP-02: ensure **all three** call the same `DecisionOrchestrator` and assessment hook (WP-01 already hooks all three); long-term collapse in WP-06 |

---

## 6. Edge and dual-post (WP-02 + WP-04 slice)

Full WP-04 (adopt mode, `POST /edge/assessments`) can follow WP-02 live on cloud rails, but **action parity** cannot wait.

| Gap | Evidence | Fix |
|-----|----------|-----|
| Edge decision never stored on cloud txn | Dual-post contract; no `edgeAssessmentId` on ingest DTO | **Minimum for unified program:** extend ingest DTO with optional `edgeAssessmentId`; link `assessments.edge_assessment_id` (column exists V238) |
| Bundle without per-rule version | `EdgeRuleCompiler` bundle stamp | **WP-04 manifest:** publish `rule_version_ids[]` in bundle; assessment.versions records manifest hash |
| Edge FLAG/SUSPEND collapsed | `EdgeRuleCompiler.normaliseAction` | Rust + Java parity tests (WP-02 tests section) |

**Owner D-9:** If edge remains authoritative for card rails, cloud WP-02 live must **not** re-decide those txns—or disagreements are expected. Document mode per PSP.

---

## 7. Alerts, cases, webhooks (WP-03 dependency slice)

WP-03 is separate but WP-02 **creates alerts** today via `DecisionEngine.createAlert`. For one coherent cutover:

| Gap | Fix in unified program |
|-----|-------------------------|
| Alerts not linked to findings | When creating alert from orchestrator, insert `alert_findings` / store finding ids (WP-03 migration `V240`) — **minimal:** JSON column on alert with finding UUIDs until join table lands |
| Fake `CaseAlert.ruleVersion` | Pass `rule_version_id` from finding into case trigger |
| Webhook payload still HOLD/ALERT | Add `decision` field per D-7; version webhook schema in docs |

Without this slice, investigators see new vocabulary in API but old alert truth in Console.

---

## 8. Screening and persistence (WP-05 overlap)

| Gap | Impact on WP-02 | Fix |
|-----|-----------------|-----|
| Transaction screening in-memory only | SCREENING findings exist (WP-01) but no `screening_searches` | WP-05 tables; until then, evidence JSON in finding is the regulator-facing record |
| Counterparty screening off by default | Fewer SCREENING findings | D-4; config flag documented per PSP |

Not a hard blocker for orchestrator **logic**, but a blocker for **regulatory explain** parity.

---

## 9. Risk model versioning (WP-12 overlap)

| Gap | Current in WP-01 | Fix |
|-----|------------------|-----|
| `sourceVersion: CRA-v1` hard-coded | Findings + assessments.versions | Replace with `risk_model_versions.id` when WP-12 lands; interim: store weight snapshot hash in `versions` JSON at assessment time |

WP-02 can live with interim strings if WP-0A fixes scale.

---

## 10. AI and ML in resolution

| Component | WP-01 behaviour | WP-02 rule |
|-----------|-----------------|------------|
| `ScoringService` ML score | ML finding recorded | Resolver uses ML finding **proposedAction** only if D-3 enables ML in production |
| `AiDecisionGateway` | Shadow finding, “Hokeka AI recommendation” | **Exclude from resolution** unless D-12; never change ingest decision from AI in WP-02 |

---

## 11. Infrastructure and ops

| Item | Detail |
|------|--------|
| **Flyway order** | V238 (WP-01) → V239 (WP-02) → V240 (WP-03); reserve V236/V237 for WP-00/0A if in same release |
| **Timescale** | V238 optional hypertable on `findings`; confirm D-15 retention vs chunk policy |
| **Indexes** | WP-02 adds decision queries; index `assessments(decision)`, `findings(assessment_id, triggered)` already present |
| **Feature flags** | `hokeka.assessment.recording.enabled`, `hokeka.decision.orchestrator` — document in DEPLOYMENT.md |

---

## 12. Testing matrix (define “done” for unified fix)

| Test | Purpose |
|------|---------|
| Precedence unit tests | BLOCK beats SUSPEND beats FLAG beats ALLOW; tie-break severity then source priority |
| Shadow comparison | Legacy vs orchestrator same txn corpus; zero unexpected BLOCK→ALLOW |
| Rust/Java parity | Edge bundle rule hits same action enum as CP |
| Tenant isolation | Assessment + findings + decisions scoped by `psp_id` |
| Integration Testcontainers | V239 migrations; decision + decision_actions inserted on ingest |
| FE e2e (optional) | Assessment panel renders findings |
| Performance | p95 ingest ≤ baseline + 5 ms with recording + shadow |

---

## 13. Suggested execution order (one program, many PRs)

All items below can be **one epic** with parallel PRs; merge order matters.

```text
1. Product: D-7, D-9, D-15 (+ D-6 if WP-00 in scope) — written defaults
2. WP-0A (+ optional WP-00) — V237/V236
3. WP-02 backend: V239, DecisionOrchestrator, shadow flag, DecisionAction executor
4. WP-01b FE: assessment panel + UI matrix
5. Edge: rule-core FLAG/SUSPEND + compiler normaliseAction
6. WP-03 slice: alert↔finding ids, webhook decision field
7. WP-04 slice: edgeAssessmentId + bundle manifest in versions
8. Shadow soak → flip hokeka.decision.orchestrator=live
9. WP-06 (orchestrator collapse) — can trail live if needed
```

---

## 14. Checklist summary (copy for Jira/Linear)

- [ ] D-7 decision semantics documented and implemented
- [ ] D-9 edge vs cloud authority documented per PSP
- [ ] D-15 retention/partition signed off
- [ ] WP-0A CRA scale + no ingest pre-decision
- [ ] (Optional) WP-00 tenant blacklist per D-6
- [ ] DecisionOrchestrator compute-all + resolver precedence
- [ ] V239 decisions + decision_actions + decision_v2
- [ ] hokeka.decision.orchestrator shadow → live gate
- [ ] RulesExecutionService + EdgeRuleCompiler + rule-core FLAG/SUSPEND parity
- [ ] DecisionAction executor (alerts, cases, lists, webhooks)
- [ ] Assessment versions JSON + context_hash populated
- [ ] Console assessment panel + FLAG/SUSPEND chips
- [ ] Ingest API: decision, hitRules, executedRules (legacy action retained)
- [ ] Alert/finding linkage minimal slice
- [ ] Shadow disagreement metrics + soak
- [ ] Full CI green (BACKEND, Edge Rust, FRONTEND)

---

## 15. Related PRs and code anchors (WP-01)

| Area | Location |
|------|----------|
| Schema | `BACKEND/src/main/resources/db/migration/V238__assessments_findings.sql` |
| Assessment lifecycle | `.../service/assessment/TransactionAssessmentService.java`, `FraudPipelineAssessmentHook.java` |
| Finding adapters | `.../service/assessment/AssessmentEngineRecorder.java`, `FindingRecorder.java` |
| Legacy decision path | `.../service/DecisionEngine.java` (short-circuit — WP-02 replaces) |
| Read API | `AssessmentController`, `TransactionController` `GET /{id}/assessment` |
| Draft PR | https://github.com/kevshake/Hokeka-AML-Tool/pull/29 |

---

_Last updated: 2026-10-07 (post WP-01 PR #29)._
