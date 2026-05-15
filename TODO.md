# TODO — Full Platform Completion (no stubs, no mocks, no placeholders)
_Last updated: 2026-05-14_

> **Goal:** Zero stubs, zero mock data, zero coming-soon pages, full Aerospike integration for caching/speed.
> **Skills source:** https://www.skills.sh/ — install relevant skills via `npx skillsadd <owner/repo>` as needed per domain.

---

## Wave 1 — Security ✅

- [x] **#32** Fix `PricingController` — added `@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")` at class level
- [x] **#33** Fix `ClientController` (→ ADMIN) + `TransactionController` per-method RBAC (ingest=PSP roles, read=all roles, admin=SUPER_ADMIN/ADMIN)

---

## Wave 2 — Backend stub completions ✅

- [x] **#34** `FrcReportingService` — real goAML 4.x XML for STR (alerts+transactions), CTR (full transaction block), Annual (live DB counts, `ANNREP` format). Commons-lang3 XML escaping, `@Value`-injected entity ID/name.
- [x] **#35** `AmlService` — geographic risk check: DB-first via `HighRiskCountryRepository`, fallback to 15-country FATF static set. +20 risk score on hit.
- [x] **#36** `ComplianceDashboardService` — team workload was already implemented; removed stale placeholder comment.
- [x] **#37** `CaseEnrichmentService` — `MerchantRepository` injected; KYC enrichment wired: fetches merchant by ID, extracts legalName/riskLevel/kycStatus, calls Sumsub screening.
- [x] **#38** `MonitoringMetricsService` — `computeAverageLatency()` from `scoredAt − createdAt` across feature batch; `computeAUC()` real trapezoidal ROC from labeled transactions, DB fallback to `ModelMetrics.auc`, never hardcoded.
- [x] **#39** `RiskScoringService` — `CountryRiskRepository` injected; DB-first lookup with static COUNTRY_RISK map fallback in all 3 overloads.
- [x] **#40** `CaseEscalationService` — 3-component composite score: alert base (0–50) + txn amount log-scale (0–30) + entity risk tier (0–20), capped 0–100.

---

## Wave 3 — Pagination / Data integrity ✅

- [x] **#41** `AuditLogRepository` + `AuditLogController` — byEntity/byUser/byRange → `Page<AuditLog>` with page/size params (default 20, capped 100), DESC timestamp sort. `LimitsManagementController` → `Page<MerchantTransactionLimit>`. DashboardController was already capped.

---

## Wave 4 — Frontend completions ✅

- [x] **#42** `/limits-aml` route added to `App.tsx`. `SettingsPage.tsx` already wired to `/settings/system` — confirmed.
- [x] **#43** `CasesTimeline.tsx` built — real chronological view of 50 most-recent cases, color-coded status icons, PSP filter, "View Case" button. Replaces placeholder in `CasesPage.tsx`.
- [x] **#44** `CasesNetworkGraph.tsx` built — SVG force-directed graph, case selector panel, node detail side panel, drag/zoom, color-coded by entity type (CASE/MERCHANT/TRANSACTION/SAR/USER). Replaces placeholder in `CasesPage.tsx`.

---

## Wave 5 — Aerospike + Settings persistence ✅

- [x] **#45** Caffeine L2 cache wired in BACKEND (Aerospike was removed from BACKEND — Caffeine already on classpath). `CacheConfig.java` rewritten: 9 named caches with individual TTLs (psps=15m, users=5m, sanctions=10m, dashboard-kpis=60s, cbk-config=30m). `@Cacheable` on PspService.getPsp, CustomUserDetailsService.loadUserByUsername, AerospikeSanctionsScreeningService.screenName. `@CacheEvict` on all mutating methods.
- [x] **#46** `V133__user_settings.sql` + `UserSettings` entity + `UserSettingsRepository` + `SettingsController` wired — per-user theme/notifications/refresh/timezone/dateFormat/itemsPerPage persisted to DB. Class auth relaxed to `isAuthenticated()`; admin sub-endpoints explicitly re-locked to SUPER_ADMIN/ADMIN.

---

## Wave 8 — Zero Stubs: Fraud Scoring, Risk, Kafka Pipeline, Compliance Reporting ✅

- [x] **#53** `FraudDetectionService` — replaced 3 hardcoded-0 stubs: `assessDeviceRisk` (device fingerprint velocity + fraud-alert cross-join), `assessIpRisk` (IP velocity + HighRiskCountry DB + FATF fallback), `assessBehavioralRisk` (amount vs 30-day avg, unusual hours). `TransactionMonitoringService` `getDeviceRisk`/`isVpnDetected` now delegate to real scoring; VPN detection uses RFC-1918 exclusion + cloud-prefix heuristic. `TransactionRepository` +4 JPQL queries.
- [x] **#54** `RiskScoringService.calculateCra()` — 5-dimension weighted CRA (amount risk 20pts + KRS 25pts + TRS 25pts + geographic 15pts + velocity 15pts). `ReportController /chart` — real data: PIE/ALERTSTATUS from AlertRepository, LINE/BAR from daily TransactionRepository aggregation with PSP scoping and configurable date window.
- [x] **#55** `FeatureExtractionService.parseCvmMethod()` — proper EMV CVMR 3-byte parsing (PIN/Signature/No-CVM/unknown). `MonitoringMetricsService` baseline — 30-day rolling average AUC from `ModelMetricsRepository.findAverageAucSince()` (new query), never hardcoded. `CaseCreationService` rule version — live lookup from `RuleDefinitionRepository` yielding `"v<year>.<month>"`, fallback `"v0.0"`.
- [x] **#56** `ComplianceReportingService.generateFincenXml()` — full goAML-pattern SAR XML (entity header, sar_details block, filing_officer, reason narrative, per-transaction blocks, commons-lang3 escaping). `SarContentGenerationService` — fixed `getFullName()` NoSuchMethodError, added 8 missing substitution keys, post-substitution `{{...}}` validation replaces unfilled tokens with `[NOT PROVIDED]`. `SchemeMonitoringReportGenerator` — stale mock comment removed (implementation was already real).
- [x] **#57** `UserService` — replaced hardcoded `"super.admin@aml.com"` with `findFirstByRole_NameOrderByIdAsc("SUPER_ADMIN")` (new repo method). `WorkflowAutomationService` — real case-closure loop via `complianceCaseRepository.findByMerchantId()`. `PrometheusMetricsService` — HikariCP-backed gauges for `system.active.connections`, pool size, pool active (instanceof check, `0.0` fallback). `PasswordResetService` — real HTML email via new `EmailNotificationService.sendPasswordResetEmail()`.
- [x] **#58** Kafka expanded 3→8 topics: +`transactions.raw` (12 partitions, 7d), `transactions.enriched` (12p, 3d), `features.updates` (6p, 1d), `transactions.audit` (6p, 30d), `alerts.generated` (6p, 7d). `TransactionIngestionService` publishes to `transactions.raw` after save. `DecisionEngine` publishes to `alerts.generated` after alert save. `AuditLogService` mirrors CREATE/UPDATE/DELETE to `transactions.audit`. `FeatureEngineService` consumer: `transactions.raw` → pre-computes CustomerFeatures via `FeatureCacheService`, publishes enriched event to `transactions.enriched`. `EnhancedAuditService` constructor updated for new KafkaTemplate param.

---

## Wave 7 — PSP Self-Serve Billing + Payments ✅

- [x] **#51** `SettingsPage.tsx` — PSP users (`pspId > 0`) now see ONLY a "Billing" tab (page title shows their PSP name). Platform admins still see Theme + System Settings. `BillingTab` rendered with `user.pspId` — no navigation to `/psps/:id/configure` required.
- [x] **#52** Payment capability end-to-end: `V134__payment_attempts.sql`, `PaymentAttempt` entity/repo, `MpesaProperties` + `MpesaService` (Daraja OAuth2 token cache, STK Push, callback processing, invoice auto-marked PAID on ResultCode=0), `PaymentController` (`POST /billing/payments/initiate`, `POST /billing/payments/mpesa/callback` [public], `GET /billing/payments/{invoiceId}`, `GET /billing/bank-details`). Security config: M-Pesa callback URL permit-all. `BillingTab.tsx`: "Pay" button on SENT/OVERDUE invoices → payment dialog (M-Pesa phone input + STK push OR Bank Transfer details display + reference submission). `mpesa.*` + `billing.bank.*` env-overridable properties.

---

## Wave 6 — SaaS Billing (full end-to-end) ✅

- [x] **#47** Expanded `BillingController` (+7 endpoints: invoice detail, status update, overdue list, PDF download, usage by month, current usage, revenue summary) + new `SubscriptionController` (7 CRUD endpoints). DTOs: `InvoiceStatusUpdateRequest`, `UsageSummaryResponse`, `RevenueSummaryResponse`, `SubscriptionRequest`, `SubscriptionResponse`. New repo queries: `sumPaidAmountForPeriod`, `sumExpectedAmountForPeriod`, `sumOverdueAmount`, `countAllByPspAndPeriod`. Full PSP-scoped isolation (PSP_ADMIN sees only own data).
- [x] **#48** `InvoicePdfService` (OpenPDF A4 branded PDF — header, bill-to, line items, totals, payment info, footer). `BillingEmailService` (async, fail-soft: invoice email with PDF attachment, dunning reminder, escalation). `DunningScheduler` (daily 09:00 overdue sweep + Monday escalation). OpenPDF `Spacer`/`LineSeparator` compat fixed. Properties wired under `billing.*`.
- [x] **#49** `BillingPage.tsx` (admin, 4 tabs): Revenue Dashboard (KPI cards + bar chart + overdue alert table), Subscriptions (CRUD dialog — PSP/tier/cycle/currency/dates/discount), Invoices (filter by PSP+status, mark-paid dialog, PDF download), Usage (PSP selector + month picker + breakdown table). `types/billing.ts` with 8 interfaces. 10 query hooks + 4 mutation hooks added to `queries.ts`/`mutations.ts`. `/billing` route in `App.tsx`. Sidebar nav item (ADMIN/SUPER_ADMIN only).
- [x] **#50** `BillingTab.tsx` added to `PspConfigPage` (tab #9): Current Plan card (tier badge, fees, contract dates, trial warning), Current Month Usage (KPI cards + breakdown, auto-refresh 60s), Invoice History (table with PDF download via `fetch`+`createObjectURL`).

---

## Done (from previous sessions)

- [x] CBK GDI API inventory — all 17 endpoints documented (`docs/integrations/CBK_API_INVENTORY.md`)
- [x] PSP entity extended — CBK fields, directors, shareholders, trustees, senior mgmt, products, trust accounts, tariffs, cyber/system incidents, complaints, fraud incidents
- [x] Flyway V124–V132 — all CBK, classification columns, deferred tables, demo seeds
- [x] `CbkGdiClient` — 17 submit methods, circuit-breaker/retry, absolute URL routing
- [x] `CbkTokenService` — per-(pspId,env) token cache, AES-GCM encrypted credentials
- [x] `CbkSubmissionOrchestrator` — all 17 endpoints wired end-to-end, date-windowed queries
- [x] `CbkScheduler` — 6 cron jobs (daily/monthly/annual), conditional on `cbk.enabled`
- [x] PSP-scoped live lock — 3-guard: global `cbk.allow-live` + per-PSP `cbkAllowLive` + per-PSP `cbkEnvironment`
- [x] `PspController` GET/PUT `/psps/{id}/cbk-config` — ADMIN-only promotion, PSP_ADMIN read-only
- [x] Frontend `CbkReportingTab` — environment promotion panel (admin only), live badge, credential fields
- [x] Frontend `PspsListPage` + `PspConfigPage` (9 tabs) — full PSP management UI
- [x] Frontend `CbkSubmissionsTab` — paginated submission history with replay button
- [x] Demo seed accounts — SUPER_ADMIN, ADMIN, 3 PSPs, PSP_ADMIN + PSP_USER per PSP
- [x] AES-GCM encryption at rest — `registrationNumber`, `taxId`, `cbkClientId`, `cbkClientSecret`
- [x] GitHub sync — all code pushed to origin/main @ `7bb46159`
