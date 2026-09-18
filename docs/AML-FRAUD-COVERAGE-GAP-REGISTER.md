# AML / Fraud Control Coverage & Gap Register

_Evidence-based mapping of the platform against two PSP AML/fraud research briefs (generic PSP framework + Kenya/POCAMLA PSP architecture). Compiled 2026-07-16 from a 9-agent reachability audit of `BACKEND/src/main/java`. Every row is traced to `file:line` and to the controller/scheduler/listener that reaches it._

## 0. Scope boundary (read first)

**This platform is an AML/fraud-detection + regulatory-reporting system that *ingests* transactions and gateway results. It is NOT a card-payment router.** There is no outbound MPGS/Cybersource/ISO 8583 routing engine (the only ISO 8583 artefact is `util/Iso8583Utils.java`, an inbound DE-39 response-code map consumed *after* an external gateway authorises).

Consequently a whole class of controls from research doc #2 belongs to the **acquiring/routing tier, which lives elsewhere** and is out of scope here:

- Cross-route retry-abuse suppression (FRA-018), payment-attempt identity across routes
- Dynamic 3DS orchestration (frictionless vs challenge)
- Refund-to-original-instrument *execution* (FRA-016) and a captured-vs-refunded ledger
- External risk-provider adapters (Cybersource Decision Manager / Mastercard Gateway)
- Pay-API idempotency keys

These are recorded below as **OUT-OF-SCOPE (routing tier)** rather than gaps, so they aren't mistaken for missing detection controls.

## 1. Legend

| Status | Meaning |
|---|---|
| ✅ PRESENT | Real, reachable, live |
| 🟡 PARTIAL | Real but incomplete / gated off by default / narrow |
| 🟠 STUB | Code exists but pretends to work, is orphaned/unreachable, or is feature-only |
| 🔴 MISSING | Not implemented |
| ⬜ OUT-OF-SCOPE | Belongs to the routing/acquiring tier, not this system |

---

## 2. KYC / KYB / Screening

| Capability | Status | Note (evidence) |
|---|---|---|
| Business KYB (registration, ownership, UBO capture + validation) | ✅ | `MerchantOnboardingService` (reg#/tax/UBO %≤100, ID required); OpenCorporates registry match (token-gated → REVIEW when off) |
| Risk-based onboarding (LOW auto-approve vs REVIEW/REJECT) | ✅ | `MerchantOnboardingService.calculateRiskScore/makeDecision`; tiered SIMPLIFIED/STANDARD/ENHANCED CDD |
| Sanctions screening, fail-closed on outage | ✅ | Fail-closed everywhere (UNAVAILABLE→HOLD/REVIEW): `DecisionEngine`, `RealTimeTransactionScreeningService`, periodic jobs |
| Fuzzy matching (phonetic/typo/transliteration) | ✅ | aml-microservice `SanctionsService`: DoubleMetaphone + Jaccard/Levenshtein + NFD transliteration |
| Adverse media (GDELT) | 🟡 | Real HTTPS GDELT client, wired to onboarding + periodic; company-level only, flag-gated |
| Daily rescreening | 🟡 | Daily crons rescreen **merchants+UBOs due** only (not end-customers); two overlapping 03:00 jobs on the same due-set |
| **Sanctions data present** | 🔴/🟡 | **`sanctions.download.enabled=false` by default; empty Aerospike set returns CLEAR (fail-open on empty data).** Highest screening risk despite correct outage handling. (Startup health guard exists — see W38-2 — but the empty-data→CLEAR path must be closed at the microservice.) |
| **PEP screening** | 🟠 | Ingest never tags entities `PEP`/`pepLevel`, so the `isPep` branch effectively never fires; no current/former/RCA; family/associate is a manual checkbox |
| Individual CIP / cardholder counterparty screening | 🟡 | Merchant/B2B-centric; individuals exist only as UBOs; gov-IDs captured but not authenticated; counterparty screening ships `screen-counterparty=false` |
| Document/ID verification | 🟡 | Storage + ClamAV malware scan + **manual** human verification; no automated IDV/OCR/liveness |
| EDD (source of funds/wealth) | 🟡 | Requirement computed, but `initiateEdd` is manual; SOF/SOW are attestation booleans |

## 3. Transaction monitoring & rules

| Capability | Status | Note |
|---|---|---|
| Decision engine ALLOW/REVIEW/HOLD/BLOCK, fail-closed | 🟡 | Strong fail-closed (sanctions/scoring/anomaly/pipeline down → HOLD). **No CHALLENGE/step-up** action |
| Structuring/smurfing (cash- & FX-aware) | ✅ | `CashStructuringDetectionService`: cash-classified, FX-to-USD, 24h window, USD 15k @ 0.80 floor |
| Threshold rules (large vs norms, rapid accumulation) | ✅ | DB rules + `DecisionEngine.checkAmlRules` + `RiskScoringService` |
| Rule-engine error handling | ✅ | Throwing rule → HOLD; unknown action → HOLD (fail-closed, not silently skipped) |
| Alert generation + auto case routing | ✅ | `DecisionEngine.createAlert` + Kafka outbox + `CaseCreationService` |
| Velocity sliding windows | 🟡 | 1h/24h/7d/30d present; **1m/5m missing**; hot-path uses SQL COUNT not the Redis counters |
| Geographic rules | 🟡 | High-risk jurisdiction present; **no true impossible-travel (time/distance), no IP-vs-billing** (entity carries only merchantCountry) |
| Behavioral rules | 🟡 | Most present; the computed `zscore_amount` feeds ML only — **no rule consumes it** |
| Counterparty (shell/offshore) | 🟡 | Screening hits wired; shell/offshore is **keyword-match only** |
| Post-transaction batch monitoring | 🟠 | `BatchScoringService` re-scores + `findAll()` full-table scan but **never routes through DecisionEngine** → settled-txn monitoring raises no alerts/cases |
| Payment-message / Travel-Rule completeness (core card path) | 🔴 | Exists only in the separate crypto/VASP module, not the core rules taxonomy |
| Enforced pre-auth latency budget | 🔴 | No deadline guard (but this is ingest, not a synchronous pre-auth gate) |

## 4. Fraud signals & ML

| Capability | Status | Note |
|---|---|---|
| Feature engineering (velocity, real z-score, device/network) | ✅ | Computed from persisted history; real z-score in `FeatureExtractionService` |
| Feature store (Redis) | ✅ | Wired into rules execution |
| IP geolocation, BIN lookup | ✅ | Real HTTP (ipapi.co + Redis cache) / real DB (`bin_ranges`) |
| Card-testing detection | ✅ | Device >5 merchants/24h heuristic, live |
| ML scoring (external XGBoost) | 🟡 | Real HTTP call but `scoring.service.enabled=false` in all profiles → **rules-only, constant `ml_score=0.0`** (honestly labeled) |
| DL4J anomaly autoencoder | 🟡 | Genuine load-from-artifact (never random); `dl4j.enabled=false`, no model path → inert |
| **Behavioral z-score** | 🟠 | `BehavioralProfilingService` Javadoc claims Z-score/sigma but computes plain `mean×5`; sigma constant unused |
| **Model retraining / feedback loop** | 🟠 | Labels/chargebacks/FP collected & persisted (real), but comments claim a "nightly retrain" that **does not exist** — no trainer consumes them |
| Proxy/VPN detection | 🟠 | Hardcoded `/8` IP-prefix guesses; no reputation/ASN |
| Graph fraud-ring / money-trail / cycle / mule-proximity | 🟠 | Genuine Cypher but **dead code** (no callers, no `controller/network`); Neo4j off by default |
| Email/phone intelligence, behavioral biometrics, ATO, synthetic-ID, promo abuse, triangulation | 🔴 | Not implemented |
| Noisy-OR signal fusion | 🔴 | Not implemented (decision uses rule→score clamping) |

## 5. Merchant-centric AML (doc #2 core)

| Capability | Status | Note |
|---|---|---|
| Structuring / rapid-movement / round-dollar scenarios | ✅ | `AmlDetectionController` `/aml/detection` exposes these three |
| Volume-vs-expected & business-age risk | ✅ | `RiskScoringService.scoreVolume/scoreBusinessAge` |
| Website / transaction-laundering monitoring | 🟡 | `ContentMonitoringService` daily single-page keyword scan → case; **no crawler, redirect chains, product classification, content-vs-onboarding diff, or evidence retention** |
| Merchant expected-profile baseline | 🟡 | Only `expectedMonthlyVolume` stored; no expected ticket/currencies/refund-ratio/cross-border-ratio/business-model |
| Linked-merchant network / reincarnation | 🟡 | `LinkAnalysisService` matches device+IP vs blocked merchants only; no shared UBO/phone/email/domain/settlement/address/webhook |
| Funnel-account / trade-based-ML detectors | 🟠 | Real in `AmlScenarioDetectionService` but **no controller/scheduler reaches them** |
| Peer-group comparison, dormant reactivation | 🟠 | Fully coded in `BehavioralAnalyticsService` but **orphaned (no caller)** |
| Rapid refund cycling, circular funds | 🟠 | Computed as features (`refund_share_pct`, `circular_trading_count`) that **drive no rule/alert** |
| Shared settlement account, bust-out, MCC-behavior inconsistency, self-funding, sudden-growth-vs-historical | 🔴 | Not implemented |
| Settlement-account-change sensitivity (rescreen/alert) | 🟠 | Change recorded to audit map only; no rescreen trigger / frequency / third-party check |

## 6. Reporting, governance, jurisdiction

| Capability | Status | Note |
|---|---|---|
| SAR/STR generation + filing, fails visibly | ✅ | Real OpenPDF + real mTLS/HMAC/XML transports; disabled → `SUBMISSION_PENDING`; **FILED impossible without genuine upstream receipt** |
| Submission lifecycle (maker-checker) | ✅ | Preparer≠approver enforced; manual FILED blocked; attempts hashed |
| Risk tiering drives workflows | ✅ | CDD depth, review cadence, case escalation |
| FATF country risk (current) | ✅ | DB-seeded, curated to Feb 2026 (`V196`); **no live feed / scheduled refresh** |
| Fraud vs AML engine separation | ✅ | Two distinct engines/scores/workflows; status = max(aml, fraud) but AML alerting independent |
| CVV handling | ✅ | No CVV stored/logged anywhere |
| PII/CDD field encryption | 🟡 | AES-GCM on email/national-ID/passport/PSP secrets; **settlement/bank account numbers plaintext** |
| **CTR auto-filing** | 🟠 | Detection is live/event-driven but the detection→filing bridge is **entirely manual/pull; no scheduled auto-file** |
| **Audit tamper-evidence** | 🟠 | HMAC-SHA256 per row **but never verified anywhere**, omits before/after/reason, and is **not hash-chained** (deletions/reordering undetectable) |
| **PAN storage** | 🟠 | App trusts upstream token; `pan_hash` stored **unencrypted & indexed** — raw PAN in `accountNumber` would persist in clear |
| Suspicion-timestamp lifecycle (2-day STR clock) | 🟡 | On SAR only (`suspicion_arose_at`, default source `REPORT_CREATED`); **case/alert lack `suspicion_formed_at`/`investigation_started_at`/`mlro_notified_at`** |
| 7-year retention | 🟡 | Enforced for docs/cases/crypto/reports; **not clearly for core `TransactionEntity`; audit retention defaults to 90 days** |
| Maker-checker for **ML model** changes | 🔴 | Rules & reporting have it; model promotion does not |
| Tipping-off safeguard | 🔴 | No explicit neutral-messaging guardrail |
| Verification of Payee (VOP/CoP) | 🔴 | Absent |
| Multi-jurisdiction abstraction | 🟡 | No central `Jurisdiction`; only US/UK/KE reporting routes; thresholds per-PSP not per-jurisdiction |

## 7. Scheme / portfolio monitoring

| Capability | Status | Note |
|---|---|---|
| Chargeback/dispute ingest (Verifi RDR) | ✅ | Webhook → `chargeback_disputes` |
| Rule versioning (effective-dated maker-checker) | ✅ | `rule_versions` / `V160` — the model the items below should adopt |
| Entity graph (merchant↔account↔device↔txn) | 🟡 | Neo4j via Kafka projector; **no Card/IP/UBO nodes** |
| Visa VAMP / enumeration, Mastercard SAFE / fraud-bps / EFM-ECM | 🟠 | Only legacy **VFMP/HECM** simulators; "fraud" is a BLOCK/DECLINE proxy, not real fraud reports |
| TC40 / TC15 / SAFE feed ingestion | 🔴 | Absent |
| Predictive exposure dashboard | 🔴 | Only current stage; no projection/distance-to-threshold/contributors |
| Graduated remediation lifecycle on merchant | 🟠 | `Merchant.status` free-text; simulator stages computed then discarded |
| **Scheme thresholds config-driven/effective-dated** | 🔴 | **Hardcoded Java literals** in `VfmpSimulator`/`HecmSimulator`; no config table, no `source_document_version` |
| MCC risk config-driven | 🟠 | Hardcoded in `MccRiskConfig`; `mcc_risk` table never created |

## 8. Out-of-scope (routing/acquiring tier)

⬜ Cross-route retry suppression (FRA-018) · payment-attempt identity across routes · dynamic 3DS · refund-to-original execution + captured/refunded ledger · external risk-provider adapters · pay-API idempotency. *(This system consumes gateway results; it does not route payments.)*

---

## 9. Prioritized remediation

**P0 — can produce a wrong AML outcome on a live path**
1. Sanctions **empty-data → CLEAR** (close the fail-open at the microscore even when the list is empty).
2. **PEP classification dead** — tag `PEP`/`pepLevel` at ingest so the `isPep` path fires.
3. **Batch monitoring raises no alerts** — route `BatchScoringService` results through `DecisionEngine`; replace `findAll()` scan.

**P1 — reachable stub / inert control ("dummy code")**
4. ~~`TransactionLimitService.setTemporaryLimit` persisted nothing~~ ✅ **FIXED** (V200 + enforcement).
5. `BehavioralProfilingService` fake z-score → compute real σ/z-score.
6. Wire orphaned real detectors: `detectFunnelAccounts`, `detectTradeBasedMl` → `AmlDetectionController`; wire refund-cycling & circular-funds features to a rule/alert.
7. `SchemeReportingController` pack export ignores type → real CSV/PDF.
8. `DecisionEngine` BLOCK → invoke existing `PaymentBlacklistService`.
9. Remove misleading "nightly retrain" comments (or build the trainer).

**P2 — compliance hardening**
10. Audit **hash-chaining** + a verification path; include before/after/reason in HMAC.
11. Encrypt settlement/bank account numbers; guard `pan_hash` against raw PAN.
12. Suspicion lifecycle timestamps on case/alert; 7-year retention for `TransactionEntity`; audit-retention default ≠ 90d.
13. CTR detection→filing bridge (scheduled).
14. Scheme thresholds + MCC risk → effective-dated config tables (reuse the `rule_versions` pattern).

**P3 — capability build-out (features, not stubs)**
Shared-settlement / bust-out / self-funding scenarios · richer expected-profile baseline · website crawler/redirect/product-classification · VAMP/SAFE + TC40 ingestion · maker-checker for model changes · VOP/CoP · jurisdiction abstraction · email/phone intelligence.

---

_Completed remediations: **Sumsub KYC vendor removed** (fabricated endpoints; screening now on the independent engine) and **temporary-limit control wired live** (#4). Both compile-verified._
