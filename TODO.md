# TODO — Full Platform Completion (no stubs, no mocks, no placeholders)
_Last updated: 2026-09-18_

## Wave 70 — Locked product decisions (2026-09-18)

Shipped on `cursor/locked-product-decisions-36cb` (single PR from main + Wave 69 tenant/search):

- **W47 AeroORM** — Vendored `aeroorm-java/0.3.0` in-repo; `risk_profile` cache path in `aml-microservice` cut to AeroORM (`AerospikeCacheService`, `AmlCheckService`); raw-client dual path removed for that set; CI builds aeroorm then microservice tests.
- **W45 Settlement hash** — V223 `merchants.cbk_settlement_account_hash`; HMAC via `PiiLookupHasher`; linkage in `MerchantLinkageService`; change detection compares hash only.
- **W29-2 Internal auto-approve** — `InternalIdvAutoApproveService` + orchestrator hook; manual default v1 IDV; documented in `docs/features/KYC_DOCUMENT_EVIDENCE.md`.
- **W19-1 / W20-5 RBAC** — Platform admins only manage users; PSP users `/users/me` only; V224 permissions; SecurityConfig ordering; `POST /psps/users` platform-only.
- **W49-8 ML blend** — Configurable `risk.weights.krs|trs|cra|ml` (defaults 0.25/0.35/0.25/0.15) in `RiskScoringService.calculateOverallRisk`.
- **W49-11 Internal auth fail-closed** — Production requires `AML_MS_INTERNAL_KEY` / `aml.internal-auth-key`; startup validator + filter 503; documented in `BACKEND/DEPLOYMENT.md`.
- **W18-6 Signal taxonomy modes** — V226 `psps.signal_taxonomy_mode`; `MultiAssetRiskEngine` respects PSP mode; API + Settings Platform Admin UI.
- **W20-2 Card annual billing** — M-Pesa disabled in production (`billing.mpesa.enabled=false`); `CardBillingService` + gateway client; `AnniversaryBillingScheduler`; card payment methods API + Billing UI.
- **W20-10 / W33-2 Invite-only** — V227 `onboarding_invites`; admin API; `/psps/register` platform-only; merchant onboard requires invite filter.
- **W27-4 Billing overrides** — `BillingRateAdminController` CRUD; Platform Admin UI tab.
- **W26-7 Platform API keys** — V228 `psp_api_keys`; `/admin/psp-api-keys` platform-only; hashed storage.
- **W34-1 SKUs** — V225 seeds WALLET_SCREENING $0.08, VASP $2.50, EDD $15, TRAVEL_RULE $0.12; usage filter mappings.
- **Release signing (13)** — `scripts/generate-release-key.sh --pin`; `docs/INSTALL.md` Hostinger key ceremony.
- **packages.hokeka.com Hostinger (14)** — `scripts/publish-packages-hostinger.sh`; release workflow SSH/rsync path (AWS optional).
- **Capacity (15)** — `DynamicCapacityService` adapts rate-limit RPM from observed TPS.

**OPS-REQUIRED (Hostinger):** DNS `packages.hokeka.com` → VPS; run `generate-release-key.sh --pin` on offline host; set repo secrets / VPS env files per `docs/INSTALL.md`.

## Wave 69 — W35-1, W20-17, W18-7, gap register (2026-09-18)

- **W35-1** — Hibernate `@Filter` tenant backstop on `TransactionEntity`, `Alert`, `ComplianceCase`,
  `User`, `Merchant`; `PspFilterEnabler` + `PspTenantWriteGuard`; V222 backfills `alerts.psp_id`;
  documented in `docs/architecture/tenant-isolation.md`. Integration test proves cross-tenant
  `findById` is hidden.
- **W20-17** — `GET /api/v1/search?q=` tenant-scoped global search (transactions, alerts, cases,
  merchants); OpenAPI tag; FRONTEND `GlobalSearchDialog` wired to header ⌘K stub.
- **W18-7** — `SANCTIONS` signals from real `AerospikeSanctionsScreeningService` matches in
  `MultiAssetRiskEngine`; `CYBER` signals from transaction metadata and
  `CyberIncidentRiskSignalBridge` (metadata-linked transactions only).
- **Gap register** — drift fixed for batch→DecisionEngine, funnel/TBML endpoints, empty Aerospike
  fail-closed; aml-microservice test for connected-but-empty dataset.

**Still deferred (NEEDS-DECISION / infra):** W47 AeroORM, W45 settlement hash, W29-2 OCR/IDV,
W21-7 travel-rule wire contract, plus ~13 product items (RBAC `/users/me`, ML blend, InternalAuthFilter
fail-closed, public register, M-Pesa domain, billing pricing, …).

## Wave 68 — System communication follow-ups (2026-09-18)

Closed the four engineering actions from `docs/SYSTEM-COMMUNICATION-AND-TX-PATH-ANALYSIS.md`:

- **Dual-post integrator contract** — `docs/EDGE_AND_CLOUD_DUAL_POST_CONTRACT.md`; cross-links in analysis doc, `edge-transaction-evaluation.md`, `PSP_API_GUIDE.md`.
- **Webhook durability** — `event_outbox` channel `WEBHOOK` (V221), `WebhookOutboxService` + `WebhookOutboxDispatcher`; `RISK_ALERT` enqueued transactionally with alert write.
- **Dormant webhook producers wired** — `CASE_UPDATE` (case create/decision), `MERCHANT_STATUS_CHANGE` (`PspService` status paths).
- **Pre-auth hardening** — batch Aerospike velocity reads; `featurestore.fail-closed` default true; ingest idempotency via `Idempotency-Key` / `clientReference`.

Also updated W36-2 gap note: `CASE_UPDATE` / `MERCHANT_STATUS_CHANGE` no longer unwired.

## Wave 67 — 2 LARGE-bucket items turned out tractable once traced (2026-08-27, continued)

- **`W26-8`** — "no webhook/notification settings on `Psp` at all" turned out already
  substantially solved by this session's own earlier `W36-2` fix (real, tenant-scoped
  `WebhookSubscriptionController`), just missing a frontend. Added a "Webhooks" tab to Settings
  (PSP self-service): create/list/remove subscriptions, shows the signing secret once, honest
  about only `RISK_ALERT` being delivered at the time (all three event types now wired — Wave 68).
- **`W36-4`** — "reconcile the remaining ~22 documented PSP API endpoints" was an audit task, not
  a feature build. Checked every one against an actual controller mapping; all matched except one
  cosmetic path-variable name (fixed). Recorded the completed reconciliation directly in the doc.

**Genuinely remaining** (LARGE bucket, real multi-file/multi-day builds, not attempted this
session): `W47` (AeroORM — deliberately deferred pending live-cluster certification before
touching a compliance-critical cache path), `W45` (settlement-account deterministic-hash linkage —
needs a schema/crypto design decision), `W29-2` (OCR/IDV — needs a vendor choice), `W35-1`
(DB-level tenant isolation backstop via Hibernate filters or Postgres RLS — cross-cutting across
every tenant-scoped entity), `W20-17` search half (no backend search endpoint exists at all),
`W18-7` (wiring SANCTIONS/CYBER signal types needs new integrations across two unrelated domains),
`W21-7` (per-jurisdiction travel-rule threshold needs a wire-contract change). **NEEDS-DECISION
bucket** (13 items, Wave 59/62) is unchanged — these need the user's business/product judgment, not
code.

**47 items closed total this session.**

---

## Wave 66 — SMALL queue exhausted (2026-08-27, continued)

- **`W19-3`** — all 14 KRS/TRS/CRA weight/lookback-window constants were hardcoded; externalized
  via `@Value` under `risk.weights.*`, defaults unchanged. 2 tests prove the weights actually flow
  into the calculation, not just that defaults are preserved.
- **`W19-4`** — `UserSkillController`'s `@PreAuthorize`s referenced `MANAGE_SKILLS`/
  `CERTIFY_SKILLS`, neither registered in `Permission`. Registered both, mirrored into the
  frontend permission picker so they're actually assignable from the Roles UI. 1 test.
- **`W19-5`** — **more serious than logged.** The global `PSP_ADMIN` template role (what every
  PSP registered outside the one-time demo seed actually falls back to) granted only view
  permissions — no `MANAGE_USERS`, no `MANAGE_RULES` — contradicting the role's entire purpose.
  Widened to match V127's richer definition; Java fix + migration V217 for already-provisioned
  databases. 1 test.
- **`W18-5`** — historical `CRYPTO_SCREENING_UNAVAILABLE`/`CRYPTO_FIAT_VALUE_MISSING`/
  `CRYPTO_TRAVEL_RULE_INCOMPLETE` rows were bucketed as `signal_type='AML'` by V161's one-time
  backfill, inconsistent with what identical new rows get today (`CRYPTO_EXPOSURE`, per
  `MultiAssetRiskEngine.signalTypeFor`). Corrective migration V219.
- **`W19-2`** — `SCREENING_ANALYST` and `PSP_ANALYST` are documented `UserRole` enum values
  referenced across 17+ `@PreAuthorize` annotations, but neither had a seeded `Role` row —
  permanently dead branches. `APP_CONTROLLER` (machine/service-account role, checked by raw
  string comparison) was also never seeded. All three seeded, Java + migration V218. 2 tests.
- **`W18-4`** — `RulesController.approveVersion/rejectVersion/proposeRollback` passed
  `getCurrentUser()` unguarded into `RuleGovernanceService`, NPEing to a 500 instead of a proper
  401 when the principal's username doesn't resolve. Matched the existing `createRule` null-check.
  Separately, migration V220 retroactively backfills any `rule_definitions` row V160 left "active
  but versionless" (only possible if `platform_users` was empty when V160 ran). The third V160
  sub-item (`content_hash` mismatch) confirmed harmless — field is never read — no action. 3 tests.
- **`W21-3`** — investigated fully, **confirmed already fixed (stale item).** Traced
  `RecordTrailService`: `MOBILE_MONEY_TRANSACTION_CONTEXT` already has a curated lookup (§multi-
  asset-transaction detail), and `MOBILE_MONEY_NETWORK_EDGE` already has real bidirectional
  curated linking via `addMobileMoneyLinks`, wired into every `genericDetail` call. No code change.

**The SMALL queue from Wave 59 is now exhausted** — every item has been fixed, reclassified to
LARGE, merged into an existing NEEDS-DECISION item, or confirmed stale/already-fixed, with
evidence for each. Remaining work is the LARGE bucket (6 items needing real new features/backend
support) and the NEEDS-DECISION bucket (13 items needing a human business call, not code) from
Wave 59/62, both unchanged. 45 items closed total this session.

---

## Wave 65 — 9 more closed (2026-08-27, continued): W14-5, W27-2/3/6/7/8, W14-10 frontend half, W18-2 investigated

- **`W14-5`** — sanctions screening (`SanctionsScreenClient`, `SanctionsCountClient`) shared the
  `amlMicroservice` circuit breaker with the unrelated AML risk-scoring path; a burst of legitimate
  sanctions-service 503s opened the shared breaker and degraded scoring too. Gave sanctions its own
  dedicated breaker. 3 tests.
- **`W27-2`/`W27-3`** — manual invoice generation and billing notifications (`POST .../invoice/
  generate`, `POST .../notify`) both existed with zero frontend callers. Wired a "Generate Invoice"
  dialog and a per-row "Send Reminder"/"Resend Invoice" action into BillingPage's Invoices tab.
- **`W27-6`** — the consolidated cross-PSP billing roster (`GET /admin/psp-billing/summary`) had
  zero callers; BillingPage had no single "at a glance, is this PSP current" view. Added as a new
  "PSP Roster" tab.
- **`W27-7`** — narrower than logged once traced: PSP user creation/management is already fully
  covered by the existing `/users` page (PSP_ADMIN already has it in their sidebar, correctly
  tenant-scoped per this session's earlier `UserController` fix). The real remaining gap was an
  admin-triggered password-reset action — added, reusing the existing self-service reset-request
  flow rather than building a new admin-set-password endpoint.
- **`W27-8`** — register form's `billingCycle` was silently dropped by `PspRegistrationRequest` (no
  such field existed). Added end to end: DTO + builder + `PspService.registerPsp`. 2 tests.
- **`W14-10` (frontend half)** — while touching `UsersTab.tsx` for W27-7, also converted its save/
  delete/toggle mutations from raw `fetch()` to `apiClient` (added `apiClient.patch`, which didn't
  exist). The backend already independently enforces tenant isolation on these endpoints regardless
  of this fix (this session's earlier `UserController.requireSamePsp`), but the frontend side of the
  original gap is now closed too.
- **`W18-2`** — investigated in depth, **not fixed — the literal suggested fix would have been a
  regression.** Traced `RuleGovernanceService`'s full state machine: `RuleDefinition.enabled` is
  already correctly managed through every lifecycle transition (`false` during DRAFT/PENDING_APPROVAL,
  only flips per the proposer's own snapshot on activation). Filtering the rule loader by
  `lifecycle_status='ACTIVE'` as literally suggested would break the legitimate case where an
  *approved-but-not-yet-effective* future-dated rule update must keep the OLD rule content running
  until its `effectiveFrom` arrives — during that window `RuleDefinition.lifecycleStatus` is
  `APPROVED`, not `ACTIVE`, even though the rule correctly should still evaluate. No code change made.

34 items closed total this session (27 previously + 7 this wave: W14-5, W27-2, W27-3, W27-6, W27-7,
W27-8, W14-10-frontend-half). `typecheck`/`lint` clean on every frontend change; BACKEND compiles
clean throughout.

---

## Wave 64 — Investigated 3 more; reclassified rather than force partial fixes (2026-08-27)

- **`W19-6`** — investigated, **merged into `W49-8` (NEEDS-DECISION)**, not fixed. Confirmed
  `RiskScoringService.calculateOverallRisk` is dead code with zero callers, and the "latent bug" is
  that `mlScore` is computed and returned in the result map but never actually blended into
  `finalScore` — this is the exact same open question `W49-8` already flags as needing a business
  decision (should ML score be blended in, or is the separation deliberate?). Deleting or "fixing"
  this method would unilaterally answer that question rather than actually getting a decision.
- **`W21-7`** — investigated, **reclassified to LARGE**. Wiring a real per-jurisdiction travel-rule
  threshold into `MultiAssetRiskEngine` (matching `VirtualAssetComplianceService`'s
  `TravelRuleJurisdictionPolicy` lookup) needs a jurisdiction field added to the ingest request DTO
  (a wire-contract change) plus new repository wiring — a real multi-file feature, not a
  single-value swap.
- **`W21-3`** — investigated, **still open, correctly scoped as SMALL-ish but not closed this
  session**. Confirmed the mechanism: `RecordTrailService.addMobileMoneyProfileLink` is a curated
  helper doing a targeted indexed query for `MobileMoneyRiskProfile` (needed because it isn't
  reachable via a direct JPA association from the calling context); `MOBILE_MONEY_TRANSACTION_CONTEXT`
  and `MOBILE_MONEY_NETWORK_EDGE` have no equivalent helper, so if either has the same
  no-direct-association situation, their related-record links go silently missing rather than
  falling back to something visibly broken. Real fix: find where each would need to be looked up,
  confirm whether a JPA association actually exists (if it does, `genericLinks` already covers it
  and there's no bug), and add matching curated helpers only where one's genuinely missing — not
  yet traced far enough to say definitively which of the two (if either) needs one.

27 items closed, 3 more investigated-and-accurately-reclassified/deferred this wave (not silently
dropped). Remaining SMALL queue: `W14-5`, `W21-3` (see above), `W18-2`, `W18-4`, `W18-5`,
`W19-2..W19-5`, `W27-2`, `W27-3`, `W27-6..W27-8`.

---

## Wave 63 — Closed 2 more; W36-2 revealed the webhook delivery pipeline was entirely dead (2026-08-27)

- **`W49-P3`** — last of the original 9 Lombok-`@EqualsAndHashCode`-over-mutable-fields entities
  (`MerchantTransactionLimit`), the other 8 already fixed. Removed the annotation entirely (falls
  back to identity equals/hashCode — matches every sibling entity in the codebase, none of which
  carry one); nothing depended on the old value-based semantics.
- **`W36-2`** — built the missing `POST /webhooks/subscribe` (and `GET`/`DELETE`
  `/webhooks/subscriptions`), every operation tenant-scoped. **Found something bigger while
  building it**: `WebhookService.sendWebhook` was called from nowhere in the entire codebase — the
  full subscribe→deliver pipeline existed except for the one thing that makes it useful. Shipping
  only the controller would have been its own stub. Wired the `RISK_ALERT` trigger into
  `DecisionEngine.createAlert` (the one canonical alert-creation path every decision branch uses),
  best-effort so a webhook failure can never affect the alert/Kafka path. `CASE_UPDATE` and
  `MERCHANT_STATUS_CHANGE` were unwired at the time — **now wired (Wave 68)** via durable outbox.
  Also corrected the doc's request/response
  shape, which had documented a richer aspirational feature (multi-event arrays, client-supplied
  secret, 8 event types) than what the real entity ever supported. 9 tests.

27 items closed total this session. Remaining SMALL queue: `W14-5`, `W21-3`, `W21-7`, `W18-2`,
`W18-4`, `W18-5`, `W19-2..W19-6`, `W27-2`, `W27-3`, `W27-6..W27-8`. LARGE and NEEDS-DECISION buckets
unchanged from Wave 62/59.

---

## Wave 62 — Closed 9 more items; 2 reclassified LARGE on closer inspection (2026-08-26)

- **`W31-2`/`W27-5`** (same finding, logged twice) — deleted dead `PspAdminController`
  (`/admin/psp/*`): zero frontend callers, zero other backend references, fully superseded by
  `PspController`'s tenant-scoped equivalents. Compiles clean with it removed.
- **`W31-1`** — built the missing admin viewer for `GET /admin/runtime-errors`: new
  `RuntimeErrorsPage` (paginated, expandable stack-trace rows), routed and added to the
  ADMINISTRATION sidebar section (platform-admin only, matching the endpoint's role restriction).
- **`W20-17`** (partial) — deleted the two confirmed-dead frontend files (`Reports/ReportsPage.tsx`,
  `Psps/tabs/CrudTab.tsx`, zero importers). The other half (unwired header search) is **reclassified
  to LARGE**: there is no backend search endpoint at all — building real cross-entity search is a
  new feature, not a small fix.
- **`W36-6`/`W36-5`** — V147 (already applied) seeded `billing_rates` in a vocabulary
  `URL_SERVICE_MAP` no longer produces (`TRANSACTION_PROCESSING`, `SANCTIONS_SCREENING`,
  `AML_CHECK`, `SCREENING`, `MERCHANT_ONBOARDING`); new migration `V216` deactivates those five
  (never edits V147 itself). **Found a real revenue-leak while verifying**: `SAR_FILING` and
  `CBK_REPORTING` were never seeded at all — every SAR filing and CBK submission has billed $0
  since those routes existed; V216 seeds both. Also fixed `UsageTrackingFilter`'s javadoc (wrongly
  claimed "V149 + V167") and regenerated `docs/features/BILLING_PRICING.md`'s service-type table
  from the actual current code — caught and corrected a wrong claim of my own about
  `API_CALL_GENERIC` mid-edit (seeded but never actually produced; unmatched paths are untracked,
  not billed at a generic fallback rate). 4 tests. **Honest limitation:** no live Postgres this
  session to execute V216 against — hand-verified against V149's already-applied pattern only.
- **`W42-1`** — added missing startup WARN specs for `DOCUMENT_ANTIVIRUS_ENABLED` and
  `BLOCKCHAIN_ANALYTICS_ENABLED`, matching the existing pattern for other off-by-default controls.
- **`W20-13`** — `CompanyTab` was missing `billingPlan`/`currency`/`paymentTerms`/`isTestMode` —
  confirmed all four are genuinely accepted by the backend `PUT /psps/{id}`, just absent from the
  form. Added as editable fields; `status` shown read-only (it's managed via a separate lifecycle
  endpoint, not general profile edit). Caught myself asserting an unverified behavioral claim about
  `isTestMode` mid-edit and corrected it to only state what `grep` actually confirmed.
- **`W20-14`** — **more precisely scoped than logged.** Role/PSP *assignment* changes on a user
  already evict correctly (`UserService`). The real gap was `RoleService`: editing a role's own
  permission set had zero cache eviction anywhere, leaving every cached user holding that role
  authorizing against stale (over-)permissions for up to the 5-minute TTL. Added
  `@CacheEvict(cacheNames="users", allEntries=true)` to all three mutating methods, matching
  `UserService`'s existing pattern. 3 tests.
- **`W18-8`** — `Customer360Page` risk-signal cards never rendered `signalType`, despite it existing
  on both the API response and the frontend type. Added.
- **`W18-7`** — investigated, **reclassified to LARGE**: `SANCTIONS` and `CYBER` signal types have
  no producer anywhere because there is no code path that turns a sanctions match or a cyber
  incident into a `MultiAssetRiskSignal` at all (sanctions screening runs through a wholly separate
  `Alert`-based mechanism). Wiring either in is a new integration, not a small fix.

`typecheck`/`lint` clean on every frontend change; BACKEND compiles clean throughout.

Remaining SMALL queue: `W14-5`, `W21-3`, `W21-7`, `W18-2`, `W18-4`, `W18-5`, `W19-2..W19-6`, `W27-2`,
`W27-3`, `W27-6..W27-8`, `W36-2`, `W49-P3`. LARGE bucket grows by two (`W20-17` search half,
`W18-7`); NEEDS-DECISION unchanged — see Wave 59.

---

## Wave 61 — Closed 10 more items from the SMALL queue (2026-08-26, same day continuation)

Continued working the priority-ordered SMALL queue. All ten below are fixed, tested (or, for
doc-only items, verified against real source), compiled, and committed.

- **`W14-4`** — sanctionType was the literal "Sanctions match" for every match regardless of list;
  now derived per-match (PEP-level vs specific list name). 1 test.
- **`W14-7`** — no-op ternary in `AlertToCaseService` simplified; `AlertController.resolveAlert`'s
  `disposingUser` resolution was instanceof-only (silently dropped attribution for any non-`User`
  principal shape) — added a username-lookup fallback. 1 new test, existing test updated for the
  new constructor param.
- **`W14-11`** — `useMonitoringDashboardStats`/`useTransactionStats` shared a React Query key
  despite different types and configs; gave the former its own key.
- **`W14-12`** — `/regulatory-reports` was routed but had no sidebar entry; added under COMPLIANCE.
- **`W22-4`** — `AmlCheckService`'s fallback transaction id was `"TXN-" + currentTimeMillis()`,
  defeating the Aerospike score cache for any caller omitting `transactionId` (a fresh key every
  call). Replaced with a deterministic SHA-256 hash of the request's own content. 2 tests.
- **`W22-2`** — Aerospike `NAMESPACE = "aml_cache"` was hardcoded identically in three separate
  files (`AerospikeCacheService`, `AmlCheckService`, `SanctionsService`); externalized via
  `aerospike.namespace` in all three, same default. Full aml-microservice suite passes.
- **`W20-8`** — **more serious than logged.** `PSP_USER` *and* `USER` were both missing from
  `UserRole` — both are real fallback role names in `AuthenticationController.register()`, and any
  account actually registered under either fallback crashed with an uncaught
  `IllegalArgumentException` at ~20 `UserRole.valueOf(...)` call sites (case management, every PSP
  CBK-filing controller, case permissions/escalation). Added both; every consumer is a positive
  allow-list so no other branch needed touching. 1 test.
- **`W21-1`** — dead `edges` capture in `MobileMoneyService` removed.
- **`W18-1`** — `RegulatoryDeadlinePolicy.createdAt` was `NOT NULL` with nothing stamping it; added
  `@PrePersist`, same pattern already used by sibling compliance entities. 2 tests.
- **`W32-1`** — `RolesTab`/`UsersTab` used native `alert()` on failure instead of `TwSnackbar`
  (unlike the rest of the app). Fixed all 3 call sites. `typecheck`/`lint` clean.
- **`W36-1`, `W36-3`** — doc fixes: `/auth/refresh` → `/auth/session/refresh`; the documented
  `/transactions/batch-score` (with a `{transactions:[...]}` body) doesn't exist at all — pointed
  the doc at the real endpoint offering that capability, `POST /transactions/ingest/batch` (bare
  array body), and separately documented the unrelated admin-only `/batch/score/yesterday` trigger
  so the two aren't confused.

Remaining SMALL queue: `W14-5`, `W22-5` (reclassified, see Wave 60), `W20-13`, `W20-14`, `W20-17`,
`W21-3`, `W21-7`, `W18-2`, `W18-4`, `W18-5`, `W18-7`, `W18-8`, `W19-2..W19-6`, `W27-2`, `W27-3`,
`W27-5..W27-8`, `W31-1`, `W31-2`, `W36-2`, `W36-5`, `W36-6`, `W42-1`, `W49-P3`. LARGE and
NEEDS-DECISION buckets unchanged — see Wave 59.

---

## Wave 60 — Closed 6 more items from Wave 59's SMALL queue (2026-08-26, same day continuation)

Worked the priority-ordered SMALL queue from Wave 59 directly (security/correctness first). All six
below are fixed, tested, compiled, and committed — not just triaged.

- **`W20-9`** (both halves) — `TransactionMonitoringService.getMonitoringSARs()` and
  `AlertDispositionService`'s stats/distribution methods now scope by the caller's PSP (reusing
  `SuspiciousActivityReportRepository.findByPspId`, and a new `AlertRepository.
  findAlertsInTimeRangeForPsp`), falling back to the unscoped query only for platform admins.
  4 tests.
- **`W20-12`** — email-based login always failed post-authentication because the login handler
  re-queried `findByUsername()` with the raw (possibly-email) request field instead of the already-
  resolved `authentication.getName()`. Fixed; 1 test.
- **`W37-3`** — new `MpesaCallbackIpAllowlistFilter` (Spring's `IpAddressMatcher`, real CIDR
  support) gates the M-Pesa callback endpoint. Deliberately not hardcoding Safaricom's IP ranges —
  those are operator infra data, not source; configure via `MPESA_CALLBACK_ALLOWED_IPS`,
  `EnvVarStartupValidator` WARNs when unset. No-op (matches prior behavior) until configured. 4
  tests.
- **`W14-6`** — `AmlScreeningOrchestrator.saveScreeningResult` threw for every merchant with an
  actual sanctions match (`convertValue(List, Map.class)` can't convert a non-empty list). Fixed by
  converting matches to `List<Map>` first, then wrapping. 1 test.
- **`W37-4`** — `BillingService.computeTieredCost`'s `up_to` field crashed with
  `ClassCastException` if it arrived as a JSON string instead of a number. Now parses defensively
  like the adjacent `rate` field already did. 3 tests.
- **`W22-5` reclassified, not fixed** — re-checked and found less serious than logged: all three
  current consumers of the (possibly-null) `AerospikeClient` bean (`AerospikeCacheService`,
  `AmlCheckService`, `SanctionsService`) already null-check via `isConnected()`/`aerospikeClient !=
  null` before every use, so the NPE risk the item described doesn't currently exist. Building a
  proper no-op sentinel for a third-party client class is disproportionate for a risk that isn't
  live. Left as a defensive-hardening nice-to-have, not a bug.

Remaining SMALL queue (from Wave 59, unchanged): `W14-4`, `W14-5`, `W14-7`, `W14-11`, `W14-12`,
`W20-8`, `W20-13`, `W20-14`, `W20-17`, `W21-1`, `W21-3`, `W21-7`, `W22-2`, `W22-4`, `W18-1`, `W18-2`,
`W18-4`, `W18-5`, `W18-7`, `W18-8`, `W19-2..W19-6`, `W27-2`, `W27-3`, `W27-5..W27-8`, `W31-1`,
`W31-2`, `W32-1`, `W36-1..W36-3`, `W36-5`, `W36-6`, `W42-1`, `W49-P3`. LARGE and NEEDS-DECISION
buckets unchanged — see Wave 59.

---

## Wave 59 — Full backlog re-triage of all 186 open items; IDOR fix; HTTP-status fix (2026-08-26)

Every `- [ ]` item across Waves 6–58 (186 total) was re-verified against current source by three
parallel read-only audits, one per third of the log. **This wave is the authoritative status for
every item it lists below** — the original checkbox further down in the file was left unticked
(retroactively editing ~90 scattered lines across 39 waves was not a good use of a single session),
but its true status is whatever is recorded here, not what its own checkbox still shows.

### Fixed this session, with tests

- **Cross-tenant IDOR in `UserController`** (this is `W14-10`, and it was worse than that item's
  own description: not just "bypasses the PSP header", but `updateUser`/`deleteUser` had **no PSP-
  ownership check at all** — any PSP admin with `MANAGE_USERS` could edit or delete another PSP's
  users by guessing a numeric id. `createUser` and the `PATCH .../toggle` endpoint already did this
  correctly; `updateUser`, `deleteUser`, and the legacy unused `POST /{id}/{action}` toggle did not.
  Fixed by extracting the check both already used into `requireSamePsp()` and calling it from all
  three. `BACKEND/src/test/java/.../controller/UserControllerTenantIsolationTest.java` (5 tests,
  passing) proves: cross-tenant update/delete/toggle now throw, same-tenant and platform-admin
  callers are unaffected, and the mutation never runs before the check (not just that an exception
  surfaces after).
- **`SecurityException` → 500, not 403, platform-wide.** Found while writing the above test: no
  `@ExceptionHandler` existed for `java.lang.SecurityException` (as opposed to Spring Security's
  `AccessDeniedException`), so it fell through to the generic `RuntimeException` handler and
  returned 500 Internal Server Error for what is actually an authorization denial. Every controller
  that does `throw new SecurityException(...)` (UserController, PspController, and others) was
  affected — requests were still correctly blocked, but misclassified as internal errors rather than
  403s, which misleads monitoring/alerting keyed off status code. Fixed with a dedicated handler in
  `GlobalExceptionHandler` (more specific than the `RuntimeException` one, so Spring prefers it
  automatically). Covered by the same `UserControllerTenantIsolationTest` run.
- **Edge `psp_id` schema-drift** (carried over from Wave 58, now committed alongside this wave):
  `EdgeRuleInterpreter`/`EdgeEngine` — see Wave 58 below for detail.

### VERIFIED FIXED (confirmed already resolved; superseded checkbox left as-is further down)

Everything the three audits confirmed already fixed — sanctions fail-open, PEP pipeline, batch→
DecisionEngine wiring, BLOCK→blacklist, M-Pesa idempotency/underpayment, CBK idempotency key,
constant-time auth compare, per-key locking, CTR/SAR auto-detection, rule-eval exception fail-
closed, sanctions phonetic/n-gram recall, PSP cross-tenant IDOR on `PspController` (already correct
— only `UserController` had the gap), actuator lockdown, edge PSP enrollment + authorization UI
(both fully built, Wave 56 checkboxes just never ticked), Kafka/Drools/Neo4j async pipeline (Wave 28
resolution), and ~15 more. Full list with file:line evidence is in this wave's originating audit
transcripts; not reproduced here to keep this file scannable — spot-check any specific item against
current source rather than trusting either the old checkbox or this summary blindly.

### GENUINELY OPEN — SMALL (real gaps, small fixes; not yet done — next session's queue)

Priority order: security/correctness first, then silent-failure/data-loss, then cheap perf, then the
rest. Each still needs the file:line fix and a test before being ticked — do not check these off
without doing that work.

- `W20-9` — `TransactionMonitoringService.getMonitoringSARs` and `AlertDispositionService`'s
  disposition-stats path still call unscoped `findAll`/`findAlertsInTimeRange`: a cross-tenant read
  leak (PSP A can see PSP B's SARs/disposition stats). **Highest priority of this batch** — same
  class of bug as the IDOR just fixed, but a read leak instead of a write one.
- `W20-12` — email-login broken: `AuthenticationController` only tries `findByUsername`, never
  `findByEmail`, even though `CustomUserDetailsService` accepts either.
- `W37-3` — M-Pesa callback endpoint has no Daraja IP allowlist; only the generic rate limiter
  guards it.
- `W14-6` — `AmlScreeningOrchestrator.convertValue(matches, Map.class)` throws whenever any
  sanctions match exists, which is caught and rethrown, failing the save. Wrap in `Map.of("matches",
  ...)` instead.
- `W22-5` — `AerospikeConfig` returns a bare `null` client on connection failure instead of an
  explicit disabled-client sentinel — silent NPE risk downstream.
- `W37-4` — `BillingService.computeTieredCost`'s `up_to` cast throws `ClassCastException` if the
  JSON value is a string instead of a number; parse defensively like the adjacent `rate` field does.
- `W14-4`, `W14-5`, `W14-7`, `W14-11`, `W14-12`, `W20-8`, `W20-13`, `W20-14`, `W20-17`, `W21-1`,
  `W21-3`, `W21-7`, `W22-2`, `W22-4`, `W18-1`, `W18-2`, `W18-4`, `W18-5`, `W18-7`, `W18-8`,
  `W19-2..W19-6`, `W27-2`, `W27-3`, `W27-5..W27-8`, `W31-1`, `W31-2`, `W32-1`, `W36-1..W36-3`,
  `W36-5`, `W36-6`, `W42-1`, `W49-P3` — full descriptions and exact fixes in this wave's audit
  transcripts; smaller/cosmetic than the ones called out above.

### GENUINELY OPEN — LARGE (real gaps, multi-file efforts; scope noted, not started)

- `W47` — flip a live `aml-microservice` cache path onto AeroORM; deliberately deferred pending
  `mvn verify` + live-cluster certification of the ORM scaffold before touching a compliance-
  critical path.
- `W45` — settlement-account-change deterministic-hash linkage in the KYB/KYC framework (needs a
  new column + migration; current encryption uses random IV so can't hash-compare as-is).
- `W29-2` — no automated OCR/IDV document verification; still a pure manual approve/reject toggle.
- `W26-8` — no webhook/notification settings on `Psp` at all (no fields, no endpoints).
- `W35-1` — tenant isolation is application-code-enforced only; no DB/ORM backstop (Hibernate
  `@Filter` or Postgres RLS) across Alert/Merchant/Case/Transaction/etc. Cross-cutting, multi-entity.
- `W36-4` — full doc↔code reconciliation of the remaining ~22 documented PSP API endpoints.

### NEEDS A PRODUCT/HUMAN DECISION (not code gaps — pick before any fix is written)

- `W19-1`/`W20-5` — `UserController`'s class-level `@PreAuthorize` locks PSP admins out of `/users/
  me` and user management entirely; the existing TODO text explicitly says this needs a human RBAC
  call, not an auto-fix.
- `W49-8` — should ML score blend into `calculateOverallRisk`, or is the separation deliberate?
- `W49-11` — should `InternalAuthFilter` fail closed (not open) specifically under the `production`
  profile?
- `W18-6` — should signal taxonomy (`signalType`/`productDomain`) affect decisioning, or stay
  reporting-only as it is today?
- `W20-2` — real production M-Pesa callback domain + a KES-vs-USD currency decision.
- `W20-10`/`W33-2` — `/psps/register` and `/merchants/onboard` are public+unauthenticated with only
  generic rate-limiting; is that the intended posture, or does it need vetted onboarding/captcha?
- `W27-4` — scope of a per-PSP billing-rate override feature before building CRUD for it.
- `W26-7` — does SaaS self-service need per-PSP API keys?
- `W34-1` — new AML services (wallet/VASP/EDD/travel-rule screening) bill $0; pricing model for them
  is undecided.
- Plus the five infra-only items already logged in Wave 57 (signing key generation, CDN bucket
  provisioning, capacity-assumption validation against real pilot volume) — unchanged, still
  someone-on-a-trusted-host actions, not code.

### Doc drift found and fixed

- `install.sh --edge-image` existed but wasn't documented in `docs/edge-client-install-guide.md` —
  added.

---

## Wave 58 — Real installation test of the edge + rules loop; TODO audit (2026-08-21)

Goal per the standing directive: verify the platform actually works end to end — both the control
plane (rules management, transaction monitoring) and the client-side edge installation — by testing
against a REAL running installation, not by re-reading code; audit the accumulated TODO backlog for
what's genuinely still open vs. already shipped; write the client-facing operational documentation
that was still missing.

### Real end-to-end installation test performed

Built and ran the ACTUAL release artifacts locally against real infrastructure — this is not a code
read, every result below was observed from a live running process:

- Built `edge_engine.dll` from source (`cargo build --release -p edge-jni`) and `edge-host-0.1.0.jar`
  (`mvn package`), the same artifacts the release pipeline (Wave 57) publishes.
- Started a real Aerospike 6.4 container with the project's actual `hokeka` namespace (via env-var
  templating, since the image's entrypoint clobbers a mounted `aerospike.conf` — noted as a
  local-dev-only friction point, not a product bug).
- Generated a real EC P-384 TLS keystore with `keytool`, matching what `install.sh` generates.
- Launched the real jar with the real native core loaded via JNI, pointed at the real Aerospike.
- [x] **Proved live:** native core loads via JNI (`evaluator: native` once a bundle is loaded),
  Aerospike feature store connects to the real `hokeka` namespace, `/edge/status` and
  `/actuator/health` report correctly.
- [x] **Proved live:** ALLOW/BLOCK/HOLD all resolve correctly against the real native evaluator;
  combined-trigger severity-max (BLOCK out-ranks HOLD, both rule ids listed) confirmed; additive
  score confirmed (90+50=140).
- [x] **Proved live:** velocity feature derivation round-trips through real Aerospike — 3 sequential
  transactions on the same `pan_hash` showed derived `pan_txn_count_1h` of 0, 1, 2, correctly
  triggering an ALERT rule only on the 3rd; caller-supplied `pan_txn_count_1h` override confirmed to
  take precedence over the derived value, exactly as documented in
  `docs/edge-transaction-evaluation.md` §2.3.

### Real bug found live, fixed, and re-verified live

- [x] **Bundle-validator schema drift between the Java fallback and the Rust native core, closed.**
  Publishing a hand-built rule bundle (missing the required `psp_id` field) to the live node — with
  the native core loaded, the normal healthy state — was rejected with a terse, generic error
  (`"native core rejected the verified rule IR as malformed"`) naming no field or rule. Root cause:
  the Rust `RuleBundle` struct (`edge-engine/crates/rule-core/src/lib.rs`) requires `psp_id`, but
  `EdgeRuleInterpreter.validateBundle()` never checked for it — and worse, on any node with the
  native core loaded, `EdgeEngine.loadVerifiedBundle()` calls native FIRST, so the Java-side
  validator (even after tightening it) was never reached until after the fact, meaning error quality
  silently depended on which evaluator happened to be active. Fixed in two parts:
  1. `EdgeRuleInterpreter.validateBundle()` tightened to match the Rust IR schema exactly: requires
     `psp_id`, requires `id`+`name` on every rule, and recursively validates the `all`/`any`/`not`/
     `cmp` condition tree (unknown types, missing `field`/`op`/`value`, unknown operators, missing
     children arrays) — matching every `#[serde]`-required field in the Rust structs.
  2. `EdgeEngine.loadVerifiedBundle()` now calls this validator **before** the native call, on every
     node regardless of which evaluator is active, so a bad publish gets the specific message
     (`"rule bundle IR has no integer 'psp_id' (required by the Rust RuleBundle struct)"`) always,
     not only on fallback-mode nodes.
  - Re-verified live after the fix: the same missing-`psp_id` bundle now gets the specific message;
    a valid bundle still loads (`evaluator: native`); the full ALLOW/BLOCK/HOLD regression from
    above still passes identically. No behavior change on the happy path.
  - Test suite: 60/60 passing, 0 failures, 0 errors — includes 7 new tests in
    `EdgeRuleInterpreterValidationTest` and 2 new/fixed tests in `EdgeEngineNativeRoutingTest` (one
    existing test's fixture was updated because the fix correctly changed which exception type a
    malformed-JSON publish throws; the test's actual intent — "a native rejection preserves the
    previous bundle" — was preserved by giving it a structurally-valid-but-native-rejected payload
    instead, rather than weakening the new validation to keep the old test passing unchanged).
  - `docs/edge-transaction-evaluation.md` §6 updated with the bundle's required top-level/rule/
    condition fields, calling out `psp_id` specifically as easy to miss.

### TODO backlog audit — stale items found and corrected

The pre-existing backlog (186 open items going in) is an append-only log across many "waves" and
does not self-correct when later work fixes an earlier-logged gap. Spot-checked against actual
current source (not the log's own description) rather than assumed current:

- [x] **P0-1 (sanctions fail-open on empty data) — VERIFIED FIXED.**
- [x] **W14-2/W14-3 (sanctions/PEP degrade on the microservice path) — VERIFIED FIXED.**
- [x] **P0-2 (PEP classification dead) — VERIFIED FIXED.** `SanctionsListDownloadService` derives
  `pepLevel` (PEP/RCA) from OpenSanctions `role.pep*`/`role.rca` topics during ingest; consumed
  downstream by `RiskScoringService`, `CustomerRiskProfilingService`, `RuleFeatureEnrichmentService`,
  `AmlScreeningOrchestrator`, `MerchantOnboardingService` and others — a live, wired pipeline, not
  dead code.
- [x] **P0-3 (BatchScoringService bypasses DecisionEngine, no alerts from settled monitoring) —
  VERIFIED FIXED.** `batchScoreYesterdayTransactions()` routes every scored transaction through
  `decisionEngine.evaluate(...)`, with an inline comment documenting the exact fix.
- [x] **P1-8 (DecisionEngine BLOCK never calls PaymentBlacklistService) — VERIFIED FIXED.**
  `DecisionEngine` registers a Do-Not-Honour blacklist entry on every BLOCK, via
  `PaymentBlacklistService` (`takeBlockAction`/`createAlert` region, DecisionEngine.java).
- [x] **PSP edge enrollment + authorization backend — VERIFIED ALREADY BUILT**, not missing as the
  log claimed. `EdgeEnrollmentService` (498 lines) implements the full PENDING→APPROVED→ACTIVE→
  SUSPENDED/REVOKED/REJECTED state machine with key pinning and tenant isolation;
  `EdgeAdminController` exposes it at `/edge/nodes/*` with platform-only approve/reject.
- [x] **PSP Edge Nodes authorization UI — VERIFIED ALREADY BUILT AND ROUTED.**
  `FRONTEND/src/pages/EdgeNodes/` (EdgeNodesPage, SetupWizard, NodeDetailDrawer, etc.), routed at
  `/edge-nodes` in `App.tsx`. My own earlier install-guide documentation describing "approve the
  node in the portal" was already correct — no doc fix was needed there.
- [x] **F6 (RuleEditorModal edit never hydrates selectedParameters, Save wipes rule parameters) —
  VERIFIED FIXED.** The `useEffect` on `[editingRule, open]` rebuilds `selectedParameters` from
  `editingRule.parameters`, with an inline comment documenting the exact same bug and fix.

**Pattern observed:** every P0/P1 item spot-checked this session was already fixed, several with a
code comment explicitly documenting the prior bug. This strongly suggests the bulk of the remaining
~180 unchecked items in this file are similarly stale rather than genuinely open — but that was not
exhaustively re-verified this session (no agent budget remained after the weekly limit was hit
mid-session; see below). **Recommend a dedicated future pass to re-verify and check off the rest of
the backlog rather than treating the current checkbox state as ground truth.**

### New client-facing documentation

- [x] **`docs/client-fraud-monitoring-guide.md`** — the operational guide for a PSP's fraud/
  compliance team: the full detection loop (edge real-time decision → control-plane monitoring →
  alert → case → resolution → regulatory filing) across BOTH the edge node and the control plane.
  Covers: `/transaction-monitoring`, `/alerts` (severity, disposition codes), `/cases` (lifecycle,
  priority, SLA, the five ways a case can open), `/rules-generation` (AML/velocity/threshold rules,
  AI-assisted drafting, severity-design guidance), `/edge-nodes` (fleet health signals),
  `/screening`, `/regulatory-reports`, a worked end-to-end example, and a practical triage playbook.
  Grounded in real entity fields/enum values read from source (`Alert`, `AlertDisposition`,
  `ComplianceCase`, `CaseStatus`, `CasePriority`) and real routes from `App.tsx`, not invented.

### Known limitation hit during this session

- Background verification agents (the `Explore` subagent type) hit the account's weekly API limit
  mid-session and failed to complete; the fork-type agent used for the code fix above ran on the
  same account and succeeded, so forks and plain agents may draw from different pools or the limit
  reset between attempts — not confirmed either way. Net effect: broader TODO-backlog verification
  beyond the items above was not attempted this session past that point; the live installation test
  and the fix that came out of it were done directly rather than via subagent.

**Superseded by verification above (was previously listed as open in this file under earlier
waves):**
- P0-1, P0-2, P0-3, P1-8, W14-2, W14-3, F6 — see verified-fixed list above.
- "Build PSP edge enrollment + authorization backend", "Build the PSP Edge Nodes authorization UI" —
  see verified-already-built list above.

## Wave 57 — Edge release pipeline + canonical install docs (2026-08-14)

Goal: close the two gaps that block any external client from installing — there is no package
server behind `packages.hokeka.com`, and `SHA256SUMS` is unsigned (checksums protect against
corruption and a bad mirror, not a compromised origin).

- [x] Canonical `docs/INSTALL.md`; `BACKEND/DEPLOYMENT.md` marked superseded
  — resolved the control-plane conflict in favour of `docker-compose.prod.yml` (it already reflects
  Aerospike moving out of the backend into `aml-ms-prod`, which `DEPLOYMENT.md` predates).
- [x] Tag-triggered release workflow (`.github/workflows/release.yml`)
  — `plan → verify → {native, jar} → publish`. Re-runs the HSE-1 interop suite (a tag can point at a
  commit that never saw a PR). Builds linux x86_64 + aarch64 (cross) + windows dll. Artifacts upload
  first, channel manifest flips last; existing version prefixes are never overwritten.
- [x] GPG signature verification in `install.sh` (pinned key, fail-closed)
  — `verify_signature()` runs before any digest is trusted; throwaway keyring, fingerprint asserted.
  Tested against a real GPG key: 6/6 (valid, tampered, missing .asc, unset pin, wrong fingerprint,
  explicit bypass). CI guard added asserting the call ordering can't regress.
- [x] Docs updated to reflect the release pipeline

- [x] Client-facing documentation set
  — `docs/edge-client-install-guide.md` (requirements per distro, sizing formula + tiers, network
  matrix, pre-flight, install/verify/harden/upgrade), `docs/edge-transaction-evaluation.md` (request
  schema, derived features, rule resolution semantics, fail-closed vs fail-soft),
  `docs/edge-install-troubleshooting.md` (failure modes by root cause).

**Wave 57b — implementation of the open findings (2026-08-14):**
- [x] **Retention unified at 90 days, single source of truth.** `--retention-days` writes BOTH the
  Aerospike namespace `default-ttl` and the edge's `EDGE_RETENTION_DAYS`; all five places
  (aerospike.conf, compose, .env.example, installer, FeatureStoreConfig) now agree. The effective
  retention was always 90 — records carry explicit TTLs that override the namespace default — so
  only capacity planning was wrong, by 3×. Effective value is now logged at startup.
- [x] **Capacity sizing computed, not guessed.** `--peak-tps` derives the data file size from
  throughput × retention (+30% headroom) and reports the index RAM, warning above 4 GB;
  `--data-file-size` overrides. Validation rejects malformed input. Templating is idempotent.
  Tested: 6 cases incl. 50 TPS×90d → 240G/23 GB index, matching hand calculation.
- [x] **musl core published; Alpine native now works.** New `x86_64-unknown-linux-musl` release leg
  whose JNI crossing runs inside an Alpine container. Installer selects the core by detected libc;
  refuses only musl/aarch64 (nothing published). `apk` case added to the JRE installer.
  Tested: 4 gate cases + 3 artifact-selection cases.
- [x] **aarch64 JNI now actually executes** — the leg moved from cross-compilation to a native
  `ubuntu-24.04-arm` runner. Every published core is exercised on its own platform.
- [x] **Release signing key tooling** — `scripts/generate-release-key.sh [--pin]`. Proven end to
  end: generate → pin into install.sh → sign a manifest → pinned installer accepts genuine and
  rejects tampered. `.release-key/` and `*.asc` added to .gitignore.

**Remaining — requires infrastructure or a human decision, not code:**
- [ ] Generate the production signing key on an offline/dedicated host and set the three repo
  secrets. Do NOT generate it on a developer laptop — that key signs artifacts every PSP edge node
  executes as root.
- [ ] Provision the `packages.hokeka.com` bucket + CDN; set the AWS/OIDC secrets.
- [ ] Validate the ~512 B/record disk assumption against real pilot volumes.

**Superseded findings (now implemented above):**
- [x] **Alpine/musl native installs no longer fail silently.** `detect_libc()` probes for
  `ld-musl-*.so.1`, a musl `ldd`, and `ID=alpine`; `detect_platform()` refuses `--native` on musl
  with a message naming container mode as the fix. Container mode is unaffected. Tested against the
  real function: 4/4 (glibc+native, glibc+container, musl+native refused, musl+container). CI guard
  added. *Still open:* publishing an actual musl build target, if Alpine native is ever required.
- [ ] **Retention is contradictory.** `aerospike.conf` sets `default-ttl 30d`; the architecture doc
  says 90d default. 3× swing in RAM and disk sizing. Settle it and make the docs agree.
- [ ] **Shipped `filesize 16G` only covers ~13 TPS at 30d retention.** Anything above the Evaluation
  tier silently begins evicting velocity history. Consider failing the installer when filesize is
  obviously undersized for the declared tier.

**Still blocked on infrastructure, not code:**
- [ ] Provision the `packages.hokeka.com` bucket + CDN; set the six release secrets (INSTALL.md §4.2)
- [ ] Generate the release signing key; pin its public half into `install.sh` (INSTALL.md §4.3).
  Until then the installer is fail-closed and will refuse to install — deliberately.
- [ ] ARM runner so the aarch64 JNI boundary is actually executed before promising ARM support

## Wave 56 — Encrypted PSP channel, edge authorization, unified design system (2026-07-20)

Goal: prove every byte to/from PSP on-prem nodes is encrypted; require explicit authorization of a
PSP edge at setup (with the interface for it); unify admin + PSP dashboards on the website palette.

**Gaps confirmed by inspection before starting:**
- `edge-host/src/main/resources/application.yml` sets `server.port: 8443` but has **no `server.ssl`** —
  the comment claims a TLS sidecar terminates it, and `deploy/docker-compose.yml` has no sidecar.
  The edge transaction API is plain HTTP today.
- No outbound control-plane channel exists (no rule poller, no metrics shipper), so nothing enforces
  encryption on that hop yet. `POST /edge/bundle` accepts a **plaintext** IR bundle with **no auth**.
- Dashboard palette diverges from the website: website is dark editorial (`--gold #d3b371`,
  `--teal #75b7ab`, ink `#f5f2eb`, bg `#080909`, Manrope/DM Sans, 3px radius); the dashboard defaults
  to light-mode `#8B4049`/`#C9A961` across 13 unrelated brand themes.

**Additional findings from the read-only encryption/authorization audit (verified by grep, not assumed):**
- **`Psp.isActive()` has ZERO callers.** `entity/psp/Psp.java:68` defines `status` (PENDING/ACTIVE/
  SUSPENDED/TERMINATED) and `isActive()` at line 806, and `activate()` is invoked from
  `PspAdminController.java:63` / `PspService.java:155` — but **no request path ever checks it**, while
  `SecurityConfig.java:147` leaves `/api/v1/psps/register` `permitAll()`. A self-registered PENDING PSP
  is therefore never actually blocked. The approval state is decorative. → must be enforced fail-closed.
- **No app-level TLS enforcement.** `application-production.properties:24-27` has every `server.ssl.*`
  line commented out; nginx terminates TLS (`nginx/fraud-detector-api.conf`, TLSv1.2+1.3 + HSTS — good).
  But there is no `requiresChannel().requiresSecure()`, so Spring reached directly serves plaintext.
  `server.forward-headers-strategy=native` is set, so `X-Forwarded-Proto` is available to enforce on.
- **`/actuator/**` is `permitAll()`** (`SecurityConfig.java:114`). Mitigated today by nginx
  (`allow 127.0.0.1; deny all;`) so not currently exposed — but worth defence-in-depth at the Spring layer.

### Channel encryption
- [x] Enforce + prove TLS on the edge transaction API (TLSv1.3, strong suites, plaintext refused)
  — `edge-host`: real `server.ssl` (PKCS12, TLSv1.3, 3 AEAD suites, configurable client-auth),
  `EdgeTlsGuard` (BeanFactoryPostProcessor) refuses to boot without keystore/password, installers
  generate a self-signed P-384 keystore into `secrets/` (mode 600), compose mounts + env wired.
- [ ] Enforce PSP activation (fix `isActive()` never being called) + secure-channel in production
- [x] Build the encrypted control-plane channel (rule pull + metrics push, HSE-1 inside mTLS)
  — `com.hokeka.edge.channel`: `ControlPlaneProperties`, `SecureChannel` (mTLS/TLSv1.3, rejects
  `http://`), `ReplayGuard` + `SealedEnvelopeCodec` (§4 envelope, 10k LRU, 300s skew),
  `RuleBundlePoller` (If-None-Match, verify-then-swap, retains previous bundle on any failure),
  `MetricsShipper` (aggregate-only `EdgeMetricsReport`, store-and-forward).
  **Split settled:** crypto + replay validation in Java (once per poll), evaluation native — the
  poller publishes the unwrapped IR through the Rust `loadVerifiedIr` JNI entry point; a `-1`
  (malformed IR) keeps the previous bundle AND does not advance the ETag, so the next poll retries
  instead of being answered 304. `/edge/status` reports the live `evaluator` (native vs fallback).
  Executed against the real `edge_engine.dll`: `mvn test -Pnative-core` → 3/3.
  **Warm standby:** the verified IR is mirrored into the Java interpreter, so a native evaluation
  fault degrades to correct decisions instead of HOLDing the PSP's entire live traffic. Latched (one
  native attempt per bundle publish, no per-transaction flapping), logged at ERROR, and surfaced on
  `/edge/status` as `nativeDegraded` / `nativeDegradedReason` / `standbyBundleReady`. A node that
  never loaded a verified bundle still answers HOLD.
- [x] **Native fast path restored (found during review of the edge work, fixed in `edge-engine`).**
  The Rust JNI `loadBundle` ABI predated the contract §4 replay envelope, so every control-plane
  bundle silently fell back to the Java interpreter — defeating the entire point of the Rust core,
  and invisibly. Added `Java_com_hokeka_edge_EdgeEngine_loadVerifiedIr`, which settles the split:
  crypto + replay validation stay in Java (once per poll interval, not perf-critical), evaluation
  runs natively (per-transaction, TPS-critical). Malformed IR returns -1 and leaves the previously
  active bundle in place, so a bad publish can never disarm a running edge.
  `cargo test --workspace` → **17/17, zero warnings** (4 new edge-jni, 8 hse-crypto, 5 rule-core).
  Also removed an `unused_mut` warning so the workspace builds clean.
- [x] **Add control-plane distribution + metrics-ingest endpoints** — `EdgeDistributionController`
  (`/enroll`, `/bundle` with ETag→304, `/metrics`), `EdgeAdminController`, `EdgeEnrollmentService`,
  `EdgeReplayGuard`, Flyway **V207**. Identity is resolved from the **mTLS client certificate**
  (or nginx `X-SSL-Client-*` only from a trusted proxy); `X-Hokeka-Edge-Id` is never an
  authentication factor, only cross-checked — a mismatch is 403 and `sealFor` is never called.
  BACKEND suite **226 → 292, 66 added, 0 broken**.
  - Caught a genuine incompatibility: the control plane was sealing the **bare rule IR**, which every
    edge would have rejected as "replay envelope has no payload". Now wrapped per contract §4.
  - **Bug found in existing code:** `EdgeRuleInterpreter.valuesEqual`/`evalCmp` used
    `expected.isNumber() ? expected.asDouble() : parseNum(...)` — mixing `double` and `Double` arms
    triggers binary numeric promotion, which **unboxes the null**. So *any* string comparison in a
    rule (`channel == "BRANCH"`, `currency == "KES"`) threw NPE at the edge instead of evaluating.
    The pre-existing test never exercised a non-numeric literal.
  - Tenant self-assertion is **enforced, not logged**: the `pspId` an edge sends in its enrollment
    body must equal the PSP the enrollment code belongs to, else `403` naming both values. It grants
    no privilege (the code binds the tenant), but a node whose local config names the wrong PSP would
    mislabel its metrics, logs and support diagnostics — unacceptable in a compliance product, and
    setup is the moment it is cheapest to fix. Checked **before any mutation**, so a refusal leaves
    the code unused and the node APPROVED: the operator corrects the config and retries with the
    same code. A blank assertion is not a mismatch and is allowed.
  - Envers mirror verified against the repo's actual audit config: only
    `hibernate.envers.store_data_at_delete=false` is set, and there is no `@RevisionEntity`,
    `@RevisionListener`, `global_with_modified_flag`, `@AuditTable`, `@AuditOverride` or
    `@NotAudited` anywhere — so `DefaultAuditStrategy` applies and `edge_nodes_aud` needs **no**
    `revend`/`revend_tstmp` and no `_mod` flag columns. Diffed programmatically: `edge_nodes` 20/20
    columns vs entity, `edge_nodes_aud` = those 20 + exactly `rev`/`revtype`, `edge_metrics_reports`
    19/19 (`EdgeMetricsRecord` is deliberately **not** `@Audited`, so no `_aud` mirror is required).

> [!WARNING]
> ### ⚠️ V207 HAS NEVER BEEN EXECUTED — RUN IT BEFORE MERGE
> `BACKEND/src/main/resources/db/migration/V207__edge_node_enrollment.sql` (tables `edge_nodes`,
> `edge_nodes_aud`, `edge_metrics_reports`) has **only ever been verified by static diff against the
> entity classes.** No Docker daemon and no usable local psql credentials were available in either
> agent's environment, so the SQL has never been parsed by a real PostgreSQL server.
>
> **Why this is load-bearing:** `spring.jpa.hibernate.ddl-auto=validate` is set in both the default
> and `production` profiles. Any column/type mismatch — or a plain SQL syntax error in the migration —
> is a **hard boot failure of the whole application**, not a warning, and it will surface first in
> whatever environment runs Flyway first.
>
> **Before merge:** apply to a scratch database and confirm the app boots, e.g.
> `docker run --rm -e POSTGRES_PASSWORD=pw -p 5433:5432 postgres:16`, then
> `mvn flyway:migrate` with `DATABASE_URL/USERNAME/PASSWORD` pointed at it, then start the app with
> `--spring.profiles.active=production` and confirm Hibernate validation passes.
> CI cannot catch this today: the `backend` job runs `mvn -B test`, which never touches Flyway.
- [x] **Write channel-encryption proof tests** — proven at all three layers (crypto/transport/
  control-plane), see the sub-items above and the consolidated matrix in
  `docs/architecture/encryption-posture.md`.
- [x] **CI added — there was none in the repo at all** (`.github/workflows/ci.yml`, 5 jobs).
  This is what makes the two defect fixes load-bearing rather than decorative: the `edge-interop` job
  **regenerates the HSE-1 vector from current Java, then runs the Rust suite against it**, and fails
  if the vector wasn't rewritten. Without that ordering a committed-but-stale vector gives a false
  green — the Rust test would validate against a snapshot of old Java. Also runs BACKEND + edge-host
  suites, FRONTEND typecheck/lint/build, installer syntax, and asserts **Aerospike still has no
  published port** so the DB-isolation guarantee can't silently regress.
  Verified locally before committing to it: `cargo clippy -D warnings` clean, `cargo fmt --all`
  applied (it was failing), workspace tests still 17/17, and the workflow YAML parses.
  - [x] **`backend` job confirmed runnable on a bare runner** (no service containers needed):
    - Ran the full suite with the datasource pointed at a dead port
      (`DATABASE_URL=jdbc:postgresql://127.0.0.1:1/nonexistent`) → **292/292 green**, proving no test
      quietly used the developer machine's local PostgreSQL. Kafka/Neo4j/Aerospike/Redis were not
      listening at all during the run, so those are proven unnecessary by construction.
      There are no `@SpringBootTest` tests and no Testcontainers; the four `@WebMvcTest` slices use
      `@ActiveProfiles("test")`, which disables Flyway/JPA/Kafka/Neo4j and mocks all collaborators.
    - `pom.xml` declares **no custom `<repositories>`/`<pluginRepositories>` and no `system`-scoped
      dependencies**, so a clean runner resolves everything from Maven Central without credentials.
    - **JDK note:** local builds run on **JDK 25**, CI pins **JDK 21 Temurin**, and the pom uses
      `<source>/<target>` (not `<release>`), which does *not* guard against using post-21 APIs.
      Re-verified explicitly with `mvn -Dmaven.compiler.release=21 clean test-compile` → 885 main +
      77 test sources compile against the **JDK 21 API signatures**, BUILD SUCCESS. So no post-21
      API is referenced anywhere in BACKEND. The `-Dnet.bytebuddy.experimental=true` surefire
      argLine exists only because ByteBuddy 1.14.10 predates JDK 25; it is inert on 21.
    - Only unavoidable runner requirement: `ClamAvDocumentScannerTest` binds an ephemeral loopback
      `ServerSocket(0)`, which GitHub-hosted runners permit. `IpReputationBenchmarkTest` is a
      compute-only probe (~8 s, **no assertions**), so a slower runner cannot make it fail.
  - [x] **Crypto layer PROVEN** — `edge-engine/crates/hse-crypto` 8/8 green (`cargo test -p hse-crypto`).
    Found and fixed: the Java↔Rust interop test was `#[ignore]`d, so the default suite silently
    skipped it and a wire-format regression on either side would NOT have been caught. It now runs by
    default, alongside new negative proofs: no-plaintext-on-the-wire (every 8-byte window of the
    payload asserted absent from the envelope), tampered ciphertext → BadSignature, forged signature
    → BadSignature, wrong edge key → Decrypt, unpinned control-plane key → BadSignature, wrong
    context (bundle-opened-as-metrics) → Decrypt, malformed/truncated framing → never partial plaintext.
  - [x] **Edge transport + envelope layer PROVEN** — `edge-host` `mvn test` 40/40 green: real Tomcat
    on TLS 1.3 serves `/edge/status` while a plaintext request to the same port is refused
    (`EdgeTransactionApiTlsIntegrationTest`); the app aborts at startup with an actionable message
    when TLS material is absent; `SealedEnvelopeCodecTest` proves no-plaintext, tamper, untrusted
    signer, wrong context, replay, stale timestamp and wrong-edge-id rejection.

### PSP setup authorization
- [ ] Build PSP edge enrollment + authorization backend (PENDING→APPROVED→ACTIVE→REVOKED)
- [x] Wire edge-side first-boot activation (fail-closed until authorized)
  — `com.hokeka.edge.activation.ActivationService`: generates X25519+Ed25519 on first boot, persists
  credentials POSIX-600, POSTs `/api/v1/edge/enroll` with the enrollment code, retries until ACTIVE.
  `EdgeEngine.evaluate` returns HOLD and `/edge/status` reports `unauthorized` until then; the
  unauthenticated `POST /edge/bundle` is now `@Profile("dev")` only (404 in production).
- [ ] Build the PSP Edge Nodes authorization UI (admin + PSP views, setup wizard)

### Design system
- [ ] Extract the website palette into shared dashboard design tokens
- [ ] Apply the unified Hokeka theme across admin + PSP dashboards

## Wave 55 — Edge-distributed platform: crypto backbone + Rust core (2026-07-20)

Committed topology: control plane (Hokeka) + per-PSP on-prem edge (Rust core, JNI to Java host,
Aerospike). Custom encrypted envelope under TLS so rule logic can't leak in transit. Design:
`docs/architecture/edge-distributed-platform.md`.

**Verified in Java (compiled + tested here — 221 backend tests green):**
- [x] **HSE-1 (Hokeka Secure Envelope)** — custom encrypted+signed wire format on vetted primitives
  (X25519 ECDH + HKDF-SHA256 + ChaCha20-Poly1305 AEAD + Ed25519 sig), under TLS. Seal/open +
  raw-key codecs. `HokekaSecureEnvelopeTest`: round-trip + rejects tamper/untrusted-signer/wrong-
  key/context-mismatch; plaintext never appears in the envelope (6 tests).
- [x] **Control-plane bundle service** — compiles rules → the edge **Rule IR** JSON, HSE-1 seals for
  a target edge; `EdgeBundleServiceTest` builds→seals→opens→asserts IR (1 test). Emits the
  cross-language interop vector `edge-engine/test-vectors/hse1_vector.json`.
- [x] **Metrics contract** — `EdgeMetricsReport`: aggregate-only (counts/decisions/rule-hits/
  latency/health), **provably no PII/txn data** — the allow-list the control-plane ingest enforces.

**Rust edge core — COMPILED + TESTED here (installed rustup + GNU toolchain on this box):**
- [x] `edge-engine/` Cargo workspace + `rule-core` (Rule IR + interpreter; **5 unit tests pass**).
- [x] `hse-crypto` — HSE-1 open side (x25519/ed25519-dalek, hkdf, chacha20poly1305). **Cross-language
  interop test PASSES**: Rust opens the Java-sealed `hse1_vector.json` to the exact plaintext →
  X25519/Ed25519/HKDF/ChaCha20-Poly1305 + wire format agree byte-for-byte across Java & Rust.
- [x] `edge-jni` — JNI bridge; **release build produces `edge_engine.dll`** (the native lib the Java
  host loads), RCU/ArcSwap hot-swap + fail-closed evaluate.

**Edge host core (Java, non-Spring reference impl) — COMPILED + TESTED (5 tests):**
- [x] `EdgeRuleInterpreter` — Java interpreter matching Rust `rule-core` (dev/fallback + cross-check
  oracle); fail-closed with no bundle.
- [x] `EdgeMetricsAggregator` — decisions → `EdgeMetricsReport`; asserted aggregate-only (no
  PAN/customer/amount in the serialized report).
- [x] `EdgeTokenValidator` — offline validation of central-issued Ed25519 tokens; rejects
  expired/wrong-signer/wrong-audience/tampered.

**Edge Spring Boot host + Jib packaging — COMPILED + TESTED + IMAGE BUILT:**
- [x] `edge-host/` standalone Spring Boot module (`com.hokeka:edge-host`): `EdgeHostApplication`,
  `EdgeController` (/edge/evaluate, /edge/status, /edge/bundle on virtual threads), `EdgeEngine`
  (loads native `edge_engine` via JNI, else Java `EdgeRuleInterpreter` fallback — always functional).
  2 tests pass (fallback evaluate + fail-closed). Rust `edge-jni` exports renamed to
  `Java_com_hokeka_edge_EdgeEngine_*` to match the host class; recompiled.
- [x] **Jib** container build (no Dockerfile, no Docker daemon): `mvn -pl edge-host package jib:buildTar`
  → 130 MB OCI image on `eclipse-temurin:25-jre`. Verified entrypoint sets
  `-Djava.library.path=/opt/hokeka/lib` + main class, port 8443 exposed, `/opt/hokeka/lib/` staged
  for the native `.so`. Also `jib:dockerBuild` (local daemon) / `jib:build` (push to registry).
- [x] **Install guide PDF** `docs/edge-engine/Hokeka-Edge-Install-Guide.pdf` (16 pp) — prerequisites,
  OS matrix, 5 install methods (container/apt/dnf/zypper/tarball/Windows/Helm), Aerospike, config,
  provisioning, health checks, troubleshooting.

**Cross-platform one-command deployment + DB isolation — VALIDATED:**
- [x] `edge-host/deploy/` installers: `install.sh` (all Linux distros — detects apt/dnf/zypper/pacman/apk,
  installs Docker/Podman or does a `--native` Temurin-25 + Aerospike install) and `install.ps1`
  (Windows, Docker Desktop via winget). Both syntax-validated (bash `-n`, PS parser).
- [x] `docker-compose.yml` — isolated stack: **Aerospike has NO published port** (private network),
  edge API bound to 127.0.0.1 by default. `docker compose config` VALID; isolation asserted
  (aerospike ports = none). Enforces "database reachable only by the edge app".
- [x] `aerospike/aerospike.conf` (hokeka namespace) + native hardening (loopback bind + firewall 3000).
- [x] `deploy/README.md` — usage + security model + honest advisory (Aerospike-on-Windows needs a
  container; CE = network isolation, EE = DB auth+TLS for defence-in-depth).

**Still to build (next waves):** rule poller+hot-swap loop + metrics shipper wiring in the host;
Aerospike feature-store client; control-plane rule-IR compiler from the live `RuleDefinition` catalog;
distribution + metrics-ingest APIs (mTLS); PSP credential/JWKS issuance; CI to cross-build the Linux
`libedge_engine.so` and stage it for Jib.

_Verified: BACKEND `mvn test` = 226 green; `edge-engine` `cargo test --workspace` = green +
`--ignored` interop pass; release `edge_engine.dll` built._


## Wave 53 — Transaction validation scenarios, IP/VPN hardening, backtesting (2026-07-19)

Goal: simulate PSP→Hokeka transactions, test repeatedly/backtest for errors, harden IP manipulation
/ fake-IP / VPN detection (customer + PSP), mitigate risky transactions, improve speed. Verified with
**two consecutive full passes = 213 tests, BUILD SUCCESS** (deterministic, no flakiness).

- [x] **IP manipulation / VPN / fake-IP detection (was missing):** new `IpReputationService` classifies
  a connection IP — malformed, private/reserved/loopback/link-local/CGN (masked source), VPN/proxy/
  datacenter via configurable CIDRs (`ip.reputation.anonymizing-cidrs`), and IP-vs-declared-country
  geo-mismatch. High-confidence checks are **fully local (no DNS, no network)** → fast, deterministic.
- [x] **Wired into risk with mitigation:** `FraudDetectionService.assessIpRisk` now adds score for
  manipulated IP (30), anonymising VPN/proxy (35), geo-mismatch (20) — for customer AND PSP-origin IPs,
  reusing a single cached GeoIP lookup (no extra latency). Unified the legacy prefix-based `detectVpn`
  (used by TransactionMonitoringService) onto `IpReputationService` + cloud-prefix fallback.
- [x] **Speed:** replaced any DNS-triggering IP parsing with octet-wise decode (`getByAddress`) — never
  resolves DNS; deterministic checks avoid the network path entirely.
- [x] **Scenario + backtest suites:** `IpReputationServiceTest` (28 cases: public/private/CGN/link-local/
  loopback/malformed/VPN-CIDR/geo-mismatch/IPv6/disabled + a 25-pass backtest) and
  `FraudDetectionServiceVpnScenarioTest` (12 cases through the monitoring path + a 20-pass backtest).
- [x] Full suite run twice back-to-back — 213/213 both times (no flaky/nondeterministic behaviour).

_Config: `ip.reputation.enabled` (default true), `ip.reputation.anonymizing-cidrs` (ops threat-intel).
Implemented in the main working tree (uncommitted). NOT committed/pushed — git disposition pending._


## Wave 52 — Dynamic per-PSP rules; default rules seed & protect PSP profiles (2026-07-19)

Goal: no hardcoded rules — validation uses DB dynamic rules editable by PSPs/banks; initial default
rules can't be deleted by PSPs; each PSP gets its own editable copy of the defaults; defaults define
the initial PSP config profile. Verified `mvn test` = **173 tests, BUILD SUCCESS**.

- [x] **No hardcoded rules:** removed `DroolsRulesService.evaluateProgrammaticRules` (hardcoded
  ML-score/betweenness/velocity/volume blocks). When no compiled DRL exists, evaluation is fully
  DB-dynamic (RulesExecutionService SpEL/dynamic rules) + DecisionEngine thresholds — no code rules.
- [x] **Per-PSP evaluation:** `RulesExecutionService` now evaluates the transaction's PSP's own
  enabled rules (`findByEnabledTrueAndPspIdOrderByPriorityDesc`), falling back to global system
  defaults only if that PSP has no copies yet (was: evaluated ALL PSPs' rules for every txn).
- [x] **Copy defaults into each PSP profile:** added `RuleDefinition.derivedFromRuleId` +
  `RuleProvisioningService.copyDefaultRulesToPsp(pspId)` (clones systemManaged defaults → PSP-scoped
  editable copies, idempotent). Hooked into `PspService.registerPsp`. Backfill migration `V206` for
  existing PSPs. Rule names now unique per `(name, psp_id)` (migration `V205`; entity constraint).
- [x] **Undeletable defaults + copies:** delete guard (RulesController + RuleGovernanceService.
  proposeRetirement) blocks deleting `systemManaged` rules AND PSP copies of defaults
  (`derivedFromRuleId != null`) — they can be edited/disabled, not deleted. PSP custom rules stay
  deletable.
- [x] Tests: `RuleProvisioningServiceTest` (3); updated `RulesExecutionServiceTest` (per-PSP load),
  `RuleGovernanceServiceTest`/`AlertTuningServiceTest` (name-lookup scoping). Names no longer
  globally unique → `findByName` replaced by `findByNameAndPspId` (uniqueness) + `findFirstBy…`
  (metadata) across governance/tuning/feedback/case-creation.

_Migrations: V205 (derived_from_rule_id + (name,psp_id) unique + Envers aud column), V206 (backfill).
Implemented in the main working tree (uncommitted). NOT committed/pushed — git disposition pending._


## Wave 51 — Transaction decisioning: DNH rule, ciphered card, 6-month purge (2026-07-19)

Goal: API-consumed transactions pass the fastest check through rules + risk engines to a decision;
add a Do-Not-Honour rule (decline that card for a month); store cards comparably-ciphered; flush
transactions older than 6 months. Implemented in the MAIN working tree (the real code lives there,
uncommitted); verified with `mvn test` = **170 tests, BUILD SUCCESS**.

- [x] **Fastest check → decision (verify):** `POST /transactions/ingest` → `ingestTransaction` →
  orchestrator (features → score → `DecisionEngine.evaluate`). `evaluate()` runs the **blacklist
  hard-rule short-circuit BEFORE the ML scoring thresholds** (pan/terminal/ip) → immediate BLOCK.
  Rules (Drools/SpEL/dynamic) + risk engines feed the decision. This is the fastest-check path.
- [x] **Do-Not-Honour rule (30-day card decline):** a BLOCK sets acquirer response `05` (Do Not
  Honor); `DecisionEngine.takeBlockAction` now registers the card fingerprint on the blacklist with
  a **30-day expiry** (`fraud.dnh.block-days`, default 30). `PaymentBlacklistService` extended with
  optional expiry: `isBlacklisted` uses an expiry-aware indexed query (`existsActiveNonExpired`);
  time-boxed blocks aren't cached (so they lapse); a repeat DNH extends but never shortens the
  window; permanent blocks unaffected. Migration `V204__payment_blacklist_expiry.sql` (expires_at +
  lookup index). Any future attempt on that card short-circuits to BLOCK until the window lapses.
- [x] **Comparable ciphered card:** `TransactionIngestionService.hashPan` now uses the keyed
  HMAC-SHA256 `PiiLookupHasher` (deterministic → comparable for velocity + DNH matching; keyed →
  not brute-forceable from a DB dump, unlike the previous unkeyed SHA-256). Dev/test fall back to
  SHA-256 when no key; production enforces `PII_LOOKUP_HMAC_KEY` at startup.
- [x] **6-month purge:** `TransactionRetentionService` (@Scheduled daily 02:30) deletes transactions
  + their `transaction_features`/`alerts` rows older than `transaction.retention.months` (6), in
  bounded per-batch transactions. **Compliance guard:** transactions linked to a SAR
  (`sar_transactions`) or a case (`case_transactions`) are excluded (regulatory hold; also avoids FK
  violations).
- [x] Tests: `PaymentBlacklistServiceTest` (5, DNH expiry/extend-only/cache logic);
  `TransactionIngestionServiceTest` updated for the HMAC path. Full suite 170 green.

_Note: implemented in the main working tree as uncommitted changes (that is where the real current
code is — the committed branches are an older snapshot). NOT committed/pushed — git disposition is
pending your decision, and a rogue background agent has been auto-pushing, so I left it local._


## Wave 50 — Virtual threads (Project Loom) end to end (2026-07-19)

Goal: implement virtual threads incl. all prerequisites, app runs seamlessly.

Prerequisites (already in the working tree from prior work):
- [x] BACKEND on Java 21 (pom `java.version`/compiler 21); runtime JVM is Java 25.
- [x] BACKEND + microservice Docker images on `eclipse-temurin:25` (25 removes `synchronized` carrier-pinning, JEP 491).
- [x] `spring.threads.virtual.enabled=${VIRTUAL_THREADS_ENABLED:true}` in BACKEND `application.properties` and microservice `application.yml` (no profile disables it).
- [x] I/O-bound qualified `@Async` executors virtual (`AsyncConfig`, `UltraHighThroughputConfig` `ultraTransactionExecutor`); CPU-bound ND4J/DL4J executors deliberately kept on bounded platform pools.

Fixed this session (the real gap):
- [x] **Default `@Async` executor was NOT virtual.** 16 bare `@Async` methods (audit, notifications, email,
  billing, workflow, api-usage, case-enrichment) fell back to a platform-thread `SimpleAsyncTaskExecutor`
  because 7 custom `Executor` beans suppress Spring Boot's auto default via `@ConditionalOnMissingBean(Executor.class)`.
  Fix: added `AsyncConfig.applicationTaskExecutor()` bean under names `{applicationTaskExecutor, taskExecutor}`
  — virtual, concurrency-limited, with a task-termination timeout so in-flight fire-and-forget tasks aren't
  dropped on deploy/shutdown.
- [x] **Proof test** `VirtualThreadExecutorTest` — asserts default + all domain executors dispatch on
  `Thread.isVirtual()` threads. 2 tests pass.
- [x] Verified: no raw `Executors.new`/`new Thread(` to migrate; no custom `TaskScheduler` (so `@Scheduled`
  uses the auto virtual scheduler); Tomcat request handling virtual via the property.

Verification: BACKEND `mvn test` **163 + 2 proof = green, BUILD SUCCESS**; microservice `mvn test` **21 green**.
Residual (needs a live env, can't run here): full-app load test on Java 21/25 incl. DL4J/ND4J reflection
(may need `--add-opens` in JAVA_OPTS under heavy ND4J); HikariCP max is now the DB-bound throughput ceiling.


## Wave 49 — Audit Cycle 2: fresh FULL-SYSTEM audit in STRICT order (2026-07-18)

Run explicitly to satisfy check→TODO→complete-all-checks→THEN-fix across the WHOLE system in one
compliant cycle. Phase 1 = check only (5 parallel READ-ONLY Explore agents; no edit tools, so no fix
can occur during checking). Phase 2 = log ALL findings here. Phase 3 = confirm all checks complete.
Phase 4 = fix (only after every check is done).

- [ ] **Phase 1 — CHECK (in progress):** read-only agents across controllers/auth/isolation,
  risk/money/calc, persistence/JPA/tx/migrations, frontend pages/hooks, microservice/integrations.
- [ ] **Phase 2 — LOG:** every returned finding recorded below before any fix.
- [ ] **Phase 3 — COMPLETE:** confirm all five scopes reported.
- [ ] **Phase 4 — FIX:** apply fixes only after Phase 3; re-verify (compile + tests).

_Findings (logged as agents report; fixes applied only in Phase 4):_

- [x] **W49-1 (LOW) FIXED** `controller/psp/cbk/PspCyberIncidentController.java:103` — `create()` sets
  `.createdBy(dto.getCreatedBy())` from the request body instead of the authenticated principal. A PSP_ADMIN
  can POST `{"createdBy":"someone.else"}` and persist a CBK cyber-incident regulatory record with a forged
  author (audit/non-repudiation corruption). Tenant isolation still holds (`canAccess`), so no escalation.
  `update()` is unaffected. Fix (Phase 4): derive createdBy from the security principal, ignore the client field.
  _(controllers/auth/isolation scope — reported; no other HIGH/MED IDOR/@PreAuthorize/bypass found.)_

- [x] **W49-2 (HIGH) FIXED** `service/monitoring/TransactionMonitoringService.java:108-119` — `getTransactionsByPsp`
  admin path (`pspId==null`) does `transactionRepository.findAll()` then filters `txnTs` in Java; PSP path also
  loads all PSP txns unbounded then windows in memory. Callers: `getTopRiskIndicators:214`,
  `generateDeclineReport:615`, `generateMonitoringSummary:656`. Admin dashboard/report loads the entire
  transactions table into the JVM → OOM/GC at scale. Fix (Phase 4): time-bounded (+PSP-scoped) repo queries.
- [x] **W49-3 (HIGH) FIXED** `service/BatchScoringService.java:125-131` — `backfillFeatures(limit)` does
  `findAll().stream().filter(featuresRepository.findByTxnId(...)).limit(limit)` → full txn table materialized +
  one `findByTxnId` per row scanned (N+1). Reachable via `POST /batch/backfill/features` (ADMIN). Fix: repo
  query for unfeatured txns with a DB-side limit (NOT EXISTS / left-join + `Pageable`).
- [x] **W49-4 (MED) FIXED** `service/case_management/CaseNetworkService.java:95-97` — related-SAR lookup uses
  `sarRepository.findAll().stream().filter(...getComplianceCase().getId()...)` (all SARs + lazy per-SAR).
  Fix: `findByComplianceCase_Id(caseId)`.
- [x] **W49-5 (MED) FIXED** `service/sanctions/ScreeningCoverageService.java:55,70,77` — `getCoverageReport` calls
  `merchantRepository.findAll()` three times for count/min/max. Fix: COUNT + MIN/MAX aggregate queries.
- [x] **W49-6 (MED) FIXED** `service/analytics/BehavioralAnalyticsService.java:126-130` — `getPeerGroup` does
  `findAll().stream().filter(same MCC).limit(20)`. Fix: `findByMcc(...)` with DB-side limit (`Pageable`).
- [x] **W49-7 (LOW-MED) FIXED** `rules/RuleFeatureEnrichmentService.java:581-590` `isConfiguredHighRiskCountry` —
  **fail-open**: `catch (RuntimeException) { return false; }` sets `country_high_risk=false` on a lookup outage;
  sibling `onboarding_high_risk_country` (:566) is unguarded → inconsistent. Fix: on lookup failure don't
  emit a benign value — propagate/mark unavailable (fail-closed), consistent with the sibling signal.
- [ ] **W49-8 (LOW, confirm intent)** `risk/RiskScoringService.java:176-179` `calculateOverallRisk` — `mlScore`
  computed but never blended (`finalScore = krs*.3 + trs*.4 + cra*.3`, weights already sum to 1.0); ML only
  affects outcome via a BLOCK rule. If this composite is a decision score, XGBoost prob is numerically inert.
  Intent ambiguous — **confirm before changing** (may be deliberate). Fix (Phase 4): confirm, then blend or
  document as intentional.
- [x] **W49-9 (LOW) FIXED** `mobilemoney/MobileMoneyRiskEngine.java:217-218` — night-window test
  `hour>=start && hour<end` only handles non-wrapping windows; a `22→05` config makes it always false →
  `MOBILE_UNUSUAL_NIGHT_ACTIVITY` silently never fires. Fix: handle wrap-around (start>end).
- [x] **W49-10 (MED) FIXED** `FRONTEND/src/pages/Psps/PspsListPage.tsx:141-150` (`handleStatusChange`/`handleDelete`)
  — `await mutateAsync(...)` with no try/catch and the `useUpdatePspStatus`/`useDeletePsp` mutations
  (`features/api/mutations.ts:430-445`) have no `onError`. Same class as fixed F4/F7. Failed delete/status
  change rejects unhandled → no error UI; status-change closes the menu first so the user believes a
  privileged action succeeded when it silently failed. Fix: try/catch + error snackbar (match `handleCreate`).
  _(persistence, risk/money, frontend scopes reported. Prior N1–N4/P1–P4, A1–A6, F1–F8 all re-verified STILL
  fixed. No Flyway dup versions.)_

- [ ] **W49-11 (LOW, accepted design)** `aml-microservice .../config/InternalAuthFilter.java:31-37` — when
  `aml.internal-auth-key` is unset the filter bypasses all `/internal/**` (loud WARN only). Same deliberate
  dev behavior C8 accepted. **Not auto-fixing** (would change security posture / break dev); recommend a
  separate prod-hardening decision to fail-closed when profile=production.
- [x] **W49-12 (LOW) FIXED** `aml-microservice .../AerospikeCacheService.java:105-129` — accumulated amount written
  to a bin misleadingly named `total_ms` (leftover time name); no consumer reads it. Fix: rename bin to
  `total_amount` for clarity (no readers → safe).
- [ ] **W49-13 (LOW, observational)** same file `:91-116` — velocity counter TTL resets each write (no sliding
  window) → effectively unbounded for an active customer. No consumer today (see W49-12). Left as a note; not
  a live bug. Revisit if/when an amount-velocity rule consumes this cache.

### Phase status
- [x] **Phase 1 — CHECK complete:** all 5 read-only scopes reported (controllers/auth, risk/money,
  persistence, frontend, microservice/integrations). No edits made during checking.
- [x] **Phase 2 — LOG complete:** W49-1 … W49-13 all recorded above before any fix.
- [x] **Phase 3 — COMPLETE:** all scopes in; prior waves re-verified still fixed.
- [x] **Phase 4 — FIX complete:** W49-1..7,9,10,12 fixed & verified (backend 163 tests green; FE typecheck+lint green; MS compiles). W49-8 held for intent-confirmation; W49-11 accepted design; W49-13 observational (no consumer).

## Wave 47 — AeroORM integration into aml-microservice (2026-07-17)

- [x] Investigated AeroOrm (`C:\Users\kevsh\Documents\AeroOrm`). Found the Java adapter did **not compile**
  (2 real bugs). **Fixed** them in `AeroRepository.java`: generics name clash (`key(ID)`/`key(T)` same
  erasure → renamed entity variant to `keyOf(T)`) and `new Bin(name, Object)` → `new Bin(name, Value.get(v))`.
- [x] `mvn -DskipTests install` → `com.aeroorm:aeroorm-java:0.3.0` in local Maven repo.
- [x] Added the dependency to `aml-microservice/pom.xml` with exclusions (its `aerospike-client-jdk21`
  needs JDK 21; service is JDK 17 → excluded, runs on `aerospike-client-jdk8:9.2.0`; gremlin-driver excluded).
  Verified: microservice compiles; dependency tree shows aeroorm-java + only the jdk8 client.
- [x] Documented advancement roadmap in `AeroOrm/docs/16-java-adapter-hokeka-integration-todo.md`
  (JDK variant, screening query-mode gap, missing Java resilience layer, live-cluster certification).
- [ ] **Flip a live path onto AeroRepository** — do incrementally on the lowest-risk path (velocity/
  risk-profile cache) behind the existing interface WITH raw-client fallback + a config flag; keep the
  sanctions screening path on the raw client until AeroORM is `mvn verify` + live-cluster certified.
  _(Deferred deliberately: AeroORM Java is an uncertified scaffold on a compliance path.)_

## Wave 48 — Late persistence-agent findings (2026-07-18) — LOGGED, verify then fix

A persistence/N+1 audit agent (launched earlier, completed late) surfaced new verified findings.
Logged here first; verify, then fix (in order).

- [x] **N1 (HIGH) FIXED** `MerchantController.getAllMerchants` N+1 (was 1+3N: per-element `getMerchantById`
  = redundant `findById` + `findLatestByMerchantId` + owners). Fix: `MerchantOnboardingService.getMerchantResponses(List<Merchant>)`
  builds the whole page from 2 batch queries (`findByMerchant_MerchantIdInOrderByScreenedAtDesc` +
  `findByMerchant_MerchantIdIn`) via a shared `buildMerchantResponse` — byte-identical output, no per-merchant
  re-query. Controller keeps the per-merchant fallback for resilience. (Full suite green.)
- [x] **N2 (HIGH) FIXED** `CaseEscalationService` — hoisted `findByEnabledTrue()` out of the per-case loop
  (fetch active rules once per run; public single-case method unchanged, delegates to a rules-passed overload).
- [x] **N3 (HIGH) FIXED** `ComplianceCase` — added `@JsonIdentityInfo(property="id")` so the self-referential
  `relatedCases` (and any bidirectional links) serialize as an id reference on re-encounter → no StackOverflow.
  (Compile + case tests green.)
- [x] **N4 (MED) FIXED** `CaseSlaService.updateAllCaseAging` per-case `findByPspAndRole` — added a per-run
  supervisor cache keyed by `<pspId>|<role>` (`computeIfAbsent`), so a run with many breached cases sharing
  an assignee PSP+role issues one supervisor lookup instead of one per case. (Full suite green.)
- [x] **FLY1 — NOT an anomaly (verified)** `V148__transactions_legacy_column_sync.sql` is a defensive/idempotent
  sync function for legacy NOT-NULL columns that `V1__Initial_Schema` created; it references no missing columns.
- [x] LazyInit in `CaseEnrichmentService` @Async (agent's 4th finding) — **already fixed (P4)** via managed re-fetch.

## Wave 46 — Full-system code anomaly audit (2026-07-17)

Proof of checks in `docs/SYSTEM-AUDIT-2026-07.md`. Mechanical baseline all green
(BACKEND compile+test-compile+163 tests, microservice compile, FE typecheck+lint+build).
Logic-anomaly agents interrupted by session limit (resets 11:30am) — batches 2-5 must re-run.

### Fixes applied (compile-verified; FeatureExtraction + Rules tests green)
- [x] **A1 (HIGH)** `TransactionStatisticsService.incrementCounter` — now atomic Redis `increment` + expire
  (JSON serializer stores Long as native int → INCRBY wire-compatible). Fixes fail-open velocity under-count.
- [x] **A2 (MED)** `TransactionStatisticsService` count/amount — 24h fast-path now only for `hours==24`;
  any other window sums the per-hour buckets.
- [x] **A3 (MED)** `RuleFeatureEnrichmentService` `volume_ratio_t1_t2` — both sides in cents now (was 100× off).
- [x] **A4 (LOW)** `RuleFeatureEnrichmentService` — removed the constant-1.0 line; `daily_txn_count_ratio`
  is now the real day-over-day ratio and always set (dormant-reactivation → spike value).
- [x] **A5 (MED)** `OptimizedFeatureExtractionService.parseCvmMethod` — aligned to the categorical 0/1/2/3
  encoding used by `FeatureExtractionService`.
- [x] **A6 (MED)** `BehavioralProfilingService` — window anchored to `getTransactionTimestamp()` with an
  exclusive upper bound (excludes the current/future txns from its own mean/stddev).

### Checks passed (no fix needed)
- [x] **Flyway migration integrity** — no true duplicate versions (114 migrations; earlier V2/V3 flag was a
  regex artifact: `V2` vs `V2_1` = versions 2 and 2.1).

### Batches 2/4/5 findings (VERIFIED by agents; FIX after all checks complete)

**Tenant-isolation / IDOR (backend controllers) — HIGH first**
- [x] **B1 (HIGH) FIXED** `TransactionLimitController.setTemporaryLimit` — added `pspIsolationService.validateMerchantAccess(merchant)` before mutation (→403 cross-tenant).
- [x] **B2 (HIGH) FIXED** `ComplianceCaseWorkflowController` — added `guardCaseAccess(caseId)` (loads case + `validateCaseAccess`) BEFORE the service mutation on assign/updateStatus/escalate; removed the after-commit 403 check.
- [x] **B3 (HIGH) FIXED** `ExportController` cases/sars/audit — PSP-scoped (`exportScopePspId()`; platform admin sees all, PSP user filtered to own pspId).
- [x] **B4 (HIGH) FIXED** `EvidenceController` upload/list/download — `validateCaseAccess` on all three; uploader derived from authenticated principal (removed client `uploadedBy` param).
- [x] **B5 (MED) FIXED** `MonitoringAlertController.listForMerchant` — loads merchant + `validateMerchantAccess`.
- [x] **B6 (MED) FIXED** `CaseManagementController` getCaseActivities + getSlaStatus — `validatePspAccess` added.
- [x] **B7 (MED) FIXED** `ComplianceCalendarService.markDeadlineCompleted` — `validatePspAccess(deadline.getPspId())`.
- [x] **B8 (MED) FIXED** `ReportResultController.saveRow` — restricted to platform admins + rowNumber NPE guard (closes regulatory-row forge). _Read PSP-scoping needs execution→PSP join — still open below._
- [x] **B9 (MED) FIXED** `AlertAnalyticsController` — restricted to platform roles (no cross-tenant metrics for PSP users) + null-createdAt guard.
- [x] **B10 (LOW) FIXED** `DashboardController.getCasesByMerchant` — validates merchant belongs to caller's PSP.
- [ ] **B8-read (MED)** `ReportResultController` GET endpoints — PSP-scope reads via execution→PSP join (still open).
- [ ] **B11 (LOW)** `controller/BatchController` (37,51) + `controller/MonitoringController` (88) — global recompute jobs reachable by PSP_USER (over-privilege → restrict roles).

**Frontend (batch 4) — VERIFIED**
- [x] **F1 (HIGH) FIXED** `UsersTab.tsx` — admin-only guard (useAuth; non-admins get a restricted view).
- [x] **F2 (HIGH) FIXED** `RolesTab.tsx` — admin-only guard.
- [x] **F3 (HIGH) FIXED** `RulesGenerationPage.tsx:757` — `rule.maxAmount` null-guarded.
- [ ] **F4 (MED)** `RulesGenerationPage.tsx:269` `handleDeleteRule` — no try/catch → unhandled rejection, no error UI.
- [ ] **F5 (MED)** `RolesTab.tsx:139` (+63,201) — `role.permissions.length` unguarded → whole list crashes if permissions null.
- [ ] **F6 (MED)** `pages/Rules/components/RuleEditorModal.tsx:92` — edit never hydrates `selectedParameters`; Save wipes all rule parameters. 
- [ ] **F7 (MED)** `pages/Settings/SettingsPage.tsx:225` (+141) — save handlers await mutateAsync with no catch → unhandled rejection.
- [ ] **F8 (LOW)** `SettingsPage.tsx:506` — `parseInt(e.target.value)` → NaN when field cleared, submitted to backend.

**Microservice + integration clients (batch 5) — VERIFIED (safety classifier was unavailable; re-verify before fixing)**
- [x] **C1 (HIGH) FIXED** `PaymentController.mpesaCallback` — requires a shared-secret `token` query param (constant-time compare) that only Safaricom receives via the registered `CallBackURL`; forged callbacks without the token are ack'd but NOT processed. Loud warning when `mpesa.callback.secret` unset. _(Set the secret + register `.../callback?token=SECRET` in prod.)_
- [ ] **C2 (MED)** `MpesaService:249` — marks PAID with stored attempt amount, ignores callback `Amount` (no under-payment check).
- [ ] **C3 (MED)** `MpesaService:89,149` — `.block()` with no timeout → thread-pool exhaustion if Daraja stalls.
- [ ] **C4 (MED)** `integration/cbk/CbkGdiClient` (125,290) — `@Retry` on non-idempotent POST filing, no idempotency key → duplicate regulatory filing.
- [ ] **C5 (MED)** `client/regulator/FrcSubmissionClient.extractStatus` (242,255) — defaults `RECEIVED` on empty/unparseable 2xx; returns success with null reference (fail-open on parse). Mirror CBK's fail-closed.
- [ ] **C6 (MED)** `aml-microservice AerospikeCacheService:104` — `Operation.add(new Bin("total_ms", 0L))` always adds zero → amount-velocity totals never accumulate.
- [ ] **C7 (LOW)** `integration/cbk/CbkTokenService:81` — `synchronized` on interned string (global monitor collision risk).
- [ ] **C8 (LOW)** `aml-microservice InternalAuthFilter` (53,60) — non-constant-time key compare; fail-open when key blank.

**Persistence (batch 3) — COMPLETE (inline)**
- [ ] **P1 (MED)** `service/case_management/CaseArchivalService.java:61,115` — `findAll().stream().filter` on scheduled archival → full-table scan; replace with a date/status-bounded query.
- [ ] **P2 (MED)** `service/MonitoringMetricsService.java:233` — `findAll().stream()` on scheduled metrics → bounded query.
- [ ] **P3 (LOW-MED)** 9 JPA entities use Lombok `@Data`/`@EqualsAndHashCode` (chargeback/limits/risk/rules) → equals/hashCode over mutable fields; switch to id-based or `@EqualsAndHashCode(onlyExplicitlyIncluded)`.
- [ ] **P4 (MED)** `service/case_management/CaseEnrichmentService.java:146,176` — `@Async` methods mutate lazy `cCase.getNotes()` on a detached entity → re-load case or save note via repository.
- Verified non-issues: no @Transactional-on-private (false positive), `CaseQueueService.findAll()` small config table, no duplicate Flyway versions.

_Note: agents also re-verified many controllers/clients CLEAN (see docs/SYSTEM-AUDIT-2026-07.md); MerchantController settlement exposure = NOT a leak (DTO masks; entity paths PSP-scoped)._

## Wave 44 — Remove stub/dummy integrations; wire live engines end to end

Decision (user): the platform runs on its own independent engines/rules — no external KYC vendor.

- [x] **Sumsub KYC vendor removed.** Verified against official Sumsub docs that the adapter used
  fabricated endpoints (`/screenings/merchants`, `/screenings/individuals`) and a fabricated
  response shape (top-level `sanctions`/`pep`/`adverseMedia` arrays); the real Sumsub AML flow is
  applicant-based + async (`GET /resources/api/applicants/{id}/amlCase`, `hits[]`/`review`). Left
  enabled, it would 404 → silent Aerospike fallback → cached as a genuine screen.
  Fix: routed `AmlScreeningOrchestrator`, `BatchScreeningController`, and `CaseEnrichmentService`
  to the platform's own independent engine (`AerospikeSanctionsScreeningService` → aml-microservice);
  removed fabricated `ExternalAmlResponse` SUCCESS/200/cost writes; deleted `SumsubAmlService`;
  removed Sumsub env-var specs from `EnvVarStartupValidator`; set `sumsub.enabled=false` (inert) in
  all profiles. Backend compiles clean; no frontend references. Fail-closed `UNAVAILABLE` preserved.
- [x] **9-agent AML/fraud coverage audit** vs both PSP research briefs → evidence-based gap register
  at `docs/AML-FRAUD-COVERAGE-GAP-REGISTER.md` (every row traced to file:line + reachability).
  Key framing: this is a detection/monitoring/reporting system, NOT a payment router — routing-tier
  controls (FRA-018 retry suppression, 3DS, refund execution, external risk adapters, pay-API
  idempotency) are OUT OF SCOPE, not gaps.
- [x] **Temporary-limit control wired live** (was inert): `TransactionLimitService.setTemporaryLimit`
  now persists to `merchant_transaction_limits` (V200 adds temporary_daily_limit/expires_at/set_by),
  `TransactionLimitEnforcementService` honors `effectiveDailyLimit()` until expiry, controller passes
  the acting user. Compile-verified.
- [ ] Remediate remaining reachable stubs (P1) + fail-open P0 items — see register §9. In progress.

### Register P0/P1 remediation checklist (from docs/AML-FRAUD-COVERAGE-GAP-REGISTER.md §9)
- [ ] P0-1 Sanctions empty-data → CLEAR (fail-open on empty list) — close at microservice.
- [ ] P0-2 PEP classification dead (ingest never tags PEP/pepLevel).
- [ ] P0-3 Batch monitoring raises no alerts (BatchScoringService bypasses DecisionEngine).
- [x] P1-4 Temporary-limit control (done above).
- [ ] P1-5 BehavioralProfilingService fake z-score (claims σ, computes mean×5).
- [ ] P1-6 Wire orphaned detectors (funnel, TBML) + refund-cycling/circular features to alerts.
- [ ] P1-7 SchemeReportingController pack export ignores type (CSV/PDF).
- [ ] P1-8 DecisionEngine BLOCK never calls PaymentBlacklistService.
- [x] P1-9 Misleading "nightly retrain" comments corrected (FeedbackLabelingService, BatchScoringService).
- [x] P1-5 BehavioralProfilingService now computes a real population σ / z-score (≥3σ) vs merchant history.
- [x] P1-6 Wired orphaned detectors: `/aml/detection/funnel-accounts` + `/trade-based-ml` endpoints.
- [x] P1-7 SchemeReportingController pack export honors format=JSON|CSV (real CSV serializer, injection-guarded).
- [x] P1-8 DecisionEngine BLOCK now adds PAN to PaymentBlacklistService (idempotent, best-effort).
- [x] P0-3 BatchScoringService routes settled txns through DecisionEngine (alerts/cases) + windowed query (no findAll scan).
- [x] P0-1 Sanctions empty-data → CLEAR fixed: `SanctionsService.screenName` now fails **closed**
  (UNAVAILABLE) on an empty dataset via a cached `hasSanctionsData()` (count>0, 60s TTL). aml-microservice compiles.
- [x] **P0-2 PEP classification wired end-to-end** (both modules compile; new test passes):
  1. BACKEND `SanctionsListDownloadService.parseSanctionEntity` — extracts `properties.topics`, derives
     pepLevel (`role.pep*`→PEP, `role.rca`→RCA), adds to wire map.
  2. aml-microservice `SanctionsIngestRequest.SanctionsEntity` + `SanctionsScreenResponse.MatchDto` — carry `pepLevel`.
  3. aml-microservice `SanctionsService.ingestEntities` — writes a `pepLevel` Aerospike bin; match builder reads it.
  4. BACKEND `SanctionsScreenClient…MatchDto` record — `pepLevel` component.
  5. BACKEND `AerospikeSanctionsScreeningService.screenName` — maps `pepLevel` into `Match`; the `isPep`
     branch in `AmlScreeningOrchestrator` now fires. Test `pepLevelFromMicroserviceFlowsIntoMatch` proves it.

## Wave 45 — PSP KYC toggle + deep KYB/KYC framework (user request)

- [x] **Per-PSP KYC toggle** (admin portal): `Psp.kycEnabled` (V201, default true/safe); platform-admin-only
  `PUT /psps/{id}/kyc-config?enabled=`; onboarding waiver path (merchant → ACTIVE, kycStatus=NOT_REQUIRED,
  audited) when disabled; exposed via PspResponse/mapper; Tailwind **KYC tab** in PspConfigPage
  (platform-admin toggle, read-only for PSP admins). Backend compiles; FE typecheck clean.
- [ ] **Deep KYB/KYC framework** (Option A: internal controls live, external vendors fail-closed). Phases:
  - [x] **P1 Foundation** (compile-verified, wired live): normalized signal store
    (`MerchantVerificationSignal` + repo + V202); `ExternalVerificationProvider` interface +
    `AbstractExternalVerificationProvider` fail-closed base + 5 adapters (MATCH Pro, VMSS, Smile ID,
    registry, bank-name — all UNAVAILABLE→manual-review until credentialed); `HardStopEvaluator`;
    explainable weighted `UnderwritingOutcome` (8 domains) + `MerchantVerificationOrchestrator`
    (internal checks + adapters + persist + hard-stops + score + decision);
    `POST /underwriting/merchants/{id}/verify` + `GET .../signals`; wired into onboarding as
    escalate-only (hard-stop/EDD/REJECT pushes APPROVE→REVIEW, never downgrades).
  - [x] **P2 Linkage/reincarnation** (compile-verified, wired): `MerchantLinkageService` detects
    merchants sharing plaintext identifiers (registration number, contact email, website) and emits a
    `MERCHANT_REINCARNATION` (HIGH, manual-review) signal when a linked merchant is BLOCKED/TERMINATED/
    REJECTED, else `LINKED_MERCHANT_SHARED_IDENTIFIER` (MEDIUM); wired into the orchestrator
    (HISTORY_NETWORK domain). Repo finders added. _UBO-shared (hashed PII) + settlement-account
    (encrypted) linkage remain as extensions._
  - [x] **P3a Progressive/conditional activation** (compile-verified, wired): `MerchantActivationControl`
    (V203) + `ProgressiveActivationService` applies a REAL reduced daily limit (EDD→25%, controls→50%)
    for a 30-day monitoring window and records graduated state; wired into onboarding after limit-set.
  - [x] **P3b Settlement-account-change controls**: `MerchantUpdateService` detects settlement change →
    persists a HIGH `SETTLEMENT_ACCOUNT_CHANGED` verification signal (manual review) + forces re-verify/re-screen.
  - [x] **P4a Transaction-laundering internal checks**: `TransactionLaunderingCheckService` wires the real
    detectors (structuring/funnel/TBML/round-dollar) over a 30-day window into underwriting signals.
  - [x] **P4b EDD auto-trigger**: `EnhancedDueDiligenceService.ensureEddInitiated` (idempotent) auto-fires
    from the orchestrator on an ENHANCED_DUE_DILIGENCE outcome (was manual-only).
  - [x] **P4c Change-triggered re-KYB**: material merchant change (identity/country/settlement) forces a
    fresh `verify()` + re-screen + resets `nextScreeningDue`.
  - [x] **P2-ext UBO-shared linkage**: `MerchantLinkageService` also links merchants sharing a beneficial
    owner (keyed national-ID / passport hash). _Settlement-account linkage still needs a deterministic
    hash column (encrypted with random IV today) — noted for a future migration._
  - [x] **Verification**: no stub/mock markers in any new underwriting code; BACKEND test-compile + both
    modules compile; new `MerchantVerificationOrchestratorTest` (REJECT on no-UBO hard stop) + updated
    `MerchantUpdateServiceTest` + sanctions PEP test all green (7/7 in the focused run).

## Wave 43 - Regulatory cash evidence and sanctions recall

- [x] Keep Aerospike exclusively in `aml-microservice`.
- [x] Replace first-two-letter sanctions candidate retrieval with alias,
  phonetic, token-prefix, and n-gram LIST index keys plus scan fallback.
- [x] Make rule evaluation errors and unsupported actions hold for review.
- [x] Add explicit cash classification to transaction ingestion and events.
- [x] Apply Kenya USD 15,000-equivalent cash reporting with approved, fresh FX
  evidence and fail-closed unavailable-rate handling.
- [x] Persist CTR conversion provenance and expose it in record trails and
  regulatory reports.
- [x] Add regulatory FX approval APIs and operator UI.
- [x] Remove the legacy inline rule seeder and hardcoded country/block examples
  from active rule paths.
- [x] Refresh FATF country statuses through February 2026 with a forward
  migration.

---

## ⭐ PRIORITY INDEX — Wiring Audit Waves 14–21 (2026-07-15)

Full-app wiring audit (Claude). Details in the per-wave sections below. Fixes I applied are documented in
`docs/CHANGES-BY-CLAUDE.md` (migrations **not** applied to your DB — run `flyway:migrate` when your batch is ready).

### 🚨 COMPLIANCE-CRITICAL — read first (deep AML bug-hunt, Waves 38–42, 2026-07-16)
These are the highest-stakes findings — AML correctness/compliance, on live paths, invisible to build/route checks:
| ID | Risk | One-line |
|----|------|----------|
| **W38-2** | ✅ **FIXED (safeguard)** | Empty watchlist no longer silent: added `SanctionsDataHealthIndicator` (`/actuator/health`→DOWN when empty) + `SanctionsDataStartupCheck` (loud boot banner; **fail-closed in prod** by default). Compile-verified. You still must set `sanctions.download.enabled=true` + `sanctions.opensanctions.url` in prod to LOAD the list — but a missing/empty watchlist now blocks prod boot instead of passing every screen. See CHANGES-BY-CLAUDE.md. |
| **W38-1** | 🔴 Sanctions **false negatives** (live path) | Prefix-index only matches candidates sharing the query's first 2 chars; misses first-letter transliteration variants (Osama/Usama). Root cause = ingest indexes primary name only. Fix: prefix-per-alias or phonetic key. |
| **W40-1** | 🔴 **CTR/SAR auto-detection dead** | `ctr_required`/`sar_required` come only from Drools (never fires) → always false; the real CTR-threshold service has zero callers. No large txn auto-flagged for a CTR. (Manual SAR filing works.) |
| **W42-1** | 🟠 Compliance controls **off by default** | Sanctions-download, ClamAV (W29-5), Sumsub IDV (W29-2), blockchain/wallet analytics, all SMTP email — each `enabled:false` default → silently do nothing in prod unless enabled. Needs a prod-readiness checklist + startup assertions. |
| **W39-1/2** | 🟠 Rule engine **fail-open** | A rule whose SpEL throws silently doesn't fire (no escalation); `strongestDecision` ignores unrecognized actions (a rule set to "REVIEW" doesn't escalate). |
| **W16-2/W14-2** | 🟠 Sanctions **fail-open** on outage | Backend `DecisionEngine` returns null (allow) when screening unavailable. Microservice already fixed to fail-closed; mirror into backend. |
_All severities traced to reachability; concrete fixes + file:line in Waves 38–42 below. W37 = M-Pesa payment robustness; W34-1 = new-service billing revenue leak._

### ✅ Blockers FIXED (7) — all verified; boot-fatal issues closed
| ID | What | Fix |
|----|------|-----|
| W14-1 | `alerts` missing severity/merchant_id/disposition_reason/disposed_by | `V162` |
| W17-1/2 | `velocity_rules`/`risk_thresholds` missing `psp_id` | `V165` |
| W16-1 | **Billing bills $0** (metering vocab ≠ rates) | `UsageTrackingFilter` rewrite + `V169` + currency + payable invoice — **verified: all emitted types have a seeded rate** |
| W20-3 | `psps.branding_theme` missing column | `V170` |
| W22-1 | **aml-microservice won't boot** (duplicate `aml:` YAML key) | merged `application.yml` block — verified parses |
| W23-1 | `merchants` missing `kra_pin`/`cr12_number` (Phase 29 Kenyan fields; `Merchant.java:46,49`) | `V185` (gapped above your fast-moving V171–V178 frontier after 2 collisions) — same `validate` boot-fatal class; merchants not @Audited so no _aud cols. Renumber if your sequence reaches V185. |

_Comprehensive `psp_id` scan: all 22 mapping tables now have a backing migration._

### 🔴 Blocker LEFT for you (needs a decision, not auto-fixed)
- **W17-13** — `V147` seeds ISO alpha-3 codes into `country_risk_scores.country_code CHAR(2)` → Flyway aborts on a
  clean DB. Choose alpha-2 (recommended) vs widen PK; if V147 already applied, fix forward. (Don't want me to edit
  a possibly-applied migration.)
  - **RE-CONFIRMED 2026-07-16 (evidence):** `V147__reference_data_seed_and_indexes.sql` INSERTs alpha-3 codes into
    `country_risk_scores` — `'PRK','IRN','MMR','SYR','YEM','SDN','LBY','SOM','CAF','SSD','AFG','IRQ','KEN'`. Target column
    is `CHAR(2) PRIMARY KEY` (`V132:21`, whose own comment says "ISO 3166-1 alpha-2 is the natural primary key"), and
    `CountryRiskScore.java:22` maps `length=2`. Postgres raises `value too long for type character(2)` → migration fails
    → **boot-fatal on clean DB**. ⚠️ You are ALSO reorganizing V147 right now (`V147__e2e_audit_gaps.sql` staged for
    deletion) — so Claude is NOT touching it. **Decision needed:** (a) change the V147 seeds to alpha-2
    (`KP,IR,MM,SY,YE,SD,LY,SO,CF,SS,AF,IQ,KE`) — recommended, matches schema+entity; or (b) widen column to VARCHAR(3)
    (new migration) AND bump `CountryRiskScore.countryCode` to `length=3` AND audit every caller that compares
    country_code (mixed alpha-2/alpha-3 would silently mismatch). Option (a) is far less invasive.

### 🟠 Top HIGH items to implement (SaaS-critical)
- **W34-1** — **new AML services bill $0 (revenue leak)**: crypto wallet/VASP/travel-rule/EDD screening are neither
  mapped in `UsageTrackingFilter` nor seeded in `billing_rates` → every such AML check runs free. Fix = filter mapping +
  rate seed for each (same 2-leg discipline as W16-1). Directly impacts SaaS cost-calculation. (Wave 34.)
- **W16-2 / W14-2** — sanctions screening **fails OPEN** on outage (fail-closed signal dropped: `DecisionEngine:296`).
  ✅ microservice now fails-closed (REVIEW + SANCTIONS_UNAVAILABLE, tested); remaining: mirror into backend `DecisionEngine`.
- **W40-1** — **CTR/SAR auto-detection is DEAD (regulatory gap)**: `ctr_required`/`sar_required` come only from Drools
  (which never fires — W28-3) so they're always false; the correct `RegulatoryComplianceService` CTR-threshold logic has
  zero callers. No large transaction is ever auto-flagged for a CTR. Compliance-critical for a CBK AML SaaS. (Wave 40.)
- **W38-2** — **sanctions list may be EMPTY (worst-case)**: `SanctionsListDownloadService` (OpenSanctions→ingest) is
  `sanctions.download.enabled=false` by default + needs `sanctions.opensanctions.url` (no default). If not enabled in
  prod, the watchlist is never loaded → every screen passes. Verify config + add an empty-watchlist startup assertion/
  alarm (fail-closed). Even more fundamental than W38-1. (Wave 38.)
- **W38-1** — **sanctions RECALL GAP (false negatives)**: the microservice prefix-index fast path only fuzzy-matches
  candidates sharing the query's first 2 chars; full-scan fallback fires only on Aerospike error → first-letter
  transliteration variants (Osama/Usama, etc.) are never evaluated → missed sanctions matches. Compliance-grade; verify
  if this service is on the live decision path. Fix via phonetic-key index or prefix fan-out. (Wave 38.)
- ✅ **W20-16 / W26-1 / W26-5 — DONE & VERIFIED (2026-07-16):** sidebar revamp — `My Organization → /organization` nav
  (W26-1/W26-2 self-profile nav), Users nav role-gated via `canManagePspUsers` (W26-5), and **logout wired**
  (`HokekaSidebar.tsx:594` + `HokekaHeader.tsx:97` → `useAuth().logout()`; W20-16 gap closed). `/organization` route
  landed (`App.tsx:109` → self-service `PspConfigPage` via `useMyPsp`→`/psps/me`). ✅ **Claude verified: frontend
  `tsc --noEmit` exit 0** — nav→route→page chain type-clean & deployable.
  **→ Core SaaS journey COMPLETE: PSP logs in → "My Organization" → sees org profile in full → billing/cost → logout.**
- **W20-5/6** — PSP tenants can't see their own profile (My Profile 403; no `/psps/me`; nav 403s).
- ✅ **Cross-PSP IDOR cluster RESOLVED (2026-07-16)** — **W29-1** (KYC docs) fixed via `requireMerchantAccess`;
  **W26-4** (`/psps/{id}` profile/update/CBK/user-create) fixed via `canAccessPsp` (platform-admin exempt, else
  `pspId == currentUser.getPsp().getPspId()`). Remaining W20-4/W20-9 were the original broad notes now covered by
  these guards — spot-check any other `/{id}` reads for the same pattern if time permits.
- ✅ **W20-7/7a FIXED** (as W27-1) — 7 PSP-config tabs 404 (`/cbk/` path mismatch, reads+writes) **and fake success**
  (no `response.ok` check) — centralized URL in `PspListCrud` + `res.ok` guards + read-path fix. tsc clean.
- **W19-1** — `UserController` `@PreAuthorize` uses `hasAnyRole` with permission names → PSP admins locked out.
- **W26-1..4** — **PSP cannot see "their profile in full"** (core SaaS goal): no nav to `/profile` (header/sidebar
  user buttons are no-op `ChevronDown`s), org-profile page unreachable (PspsList 403s for PSP), no `/psps/me` self
  endpoint, and `/psps/{id}` IDOR (no ownership check). Deepens W20-5/6. Full spec + fixes in Wave 26.
- ✅ **W25-1/2 FIXED** — **billing-$0 fix's final mile**: PSP self-service `BillingTab.tsx` now reads the real flat
  DTO fields → Current-Plan card renders, month cost no longer `$NaN`. Added `includedChecks` to `SubscriptionResponse`.
  Frontend tsc clean. (W25-3..8 still open.)
- ✅ **W24-1..7 FIXED** — tenant-safe report SQL, database aliases, durable queue state, typed dates, corrected SAR
  provenance, surfaced count failures, real XLSX, recurring execution, SMTP delivery, and delivery evidence.
- **W15/W28** — RE-VERIFIED: **live scoring works via SpEL** (`/aml-check` → `RulesExecutionService` → `SpelRuleExecutor`).
  Inert async layer: Kafka enrichment dead-ends at `transactions.enriched` (no consumer); alerts pipeline dead both
  ends (topic mismatch `alerts.generated` vs `aml.transaction.alerts`); Drools loads but never fires (no `DROOLS_DRL`
  seed — all SPEL); Neo4j never written (0 callers + disabled). Not boot-fatal. Full evidence in Wave 28.
- **W21-4..7** — crypto/VASP build-out — mostly DONE (verified 2026-07-16): ✅ `WalletScreeningRecord` persisted
  (W21-6), ✅ `TravelRuleGatewayClient` is a real reactive WebClient (externalized config, idempotency, HTTPS guard,
  disabled-by-default) genuinely called from `VirtualAssetComplianceService.transmit`. Still open: **W21-7** — decisioning
  should read `TravelRuleJurisdictionPolicy` (seeded `KE_VASP_2025_BASELINE`) instead of the hardcoded threshold default.

### 📋 Feature verdicts
Clean & fully wired: **mobile-money**, market-surveillance (V164), report-traceability, multi-asset, V159 deadlines,
V160 rule maker-checker, monitoring/admin/chargeback, notification, risk module, auth foundation,
**Analytics/RiskAnalytics/TransactionMonitoring dashboards** (2026-07-16 — all real-API-backed, no mock/placeholder;
TxMon SARs tab uses `useSarReports`/`useCreateSar`, real list+create), **KYC upload/verify flow** (Wave 29 — works,
but see W29-1 isolation gap), **network-graph UI** (relational-backed, unaffected by dead Neo4j — W28-4 note),
**chargeback/disputes** (2026-07-16 — Verifi RDR webhook `VerifiRdrWebhookService` ingests → clean read-only viewer
whose buttons all navigate to linked cases/records; schema fully backed, not @Audited; read-only-by-design),
**Customer360** (multi-asset customer/account/relationship/transaction CRUD, all real POSTs) + **Messages**
(`GET /messages` → `MessagesController:21`) — both real-API-backed, no stubs (2026-07-16).
**Report-scheduling build-out** (your in-flight work, verified 2026-07-16): `ReportSchedule`/`ReportScheduleHistory`
entities + repos + migration `V180__scheduled_report_delivery_evidence.sql` all consistent — V180 adds psp_id
(backfilled from report_schedules)/output_format/recipients(JSONB)/delivered_at + index; `recipients` correctly
mapped `@JdbcTypeCode(SqlTypes.JSON) List<String>`. No `validate` gap. Well-wired.
**Frontend route completeness** (verified 2026-07-16): every `*Page.tsx` is reachable — all lazy-loaded/routed in
`App.tsx` (64 route/lazy refs) except Login/Signup/Reports which are direct-imported+routed. No unrouted/orphan pages
(zero-warning lint would've failed on an unused import). The W20-16/W26-1 "orphaned" concern was about missing NAV LINKS
(now fixed — see the DONE W20-16/W26-1/W26-5 above), NOT unrouted pages.
**Sidebar nav-link ↔ route reconciliation** (verified 2026-07-16 after your sidebar revamp): ALL 20 sidebar `to:` paths
(`/dashboard`, `/customer-360`, `/organization`, `/cases`, `/users`, etc.) have a matching `App.tsx` route (incl. wildcard
routes `cases/*`, `transaction-monitoring/*`, `users/*`). **Zero broken nav links** — every nav button goes to a real page.
**Regulator virtual-asset access API** (`/regulator/virtual-assets/**`, verified 2026-07-16): the SecurityConfig
`permitAll()` is intentional + safe — each endpoint calls `validateGrant(rawKey, sourceIp, scope)`
(`VirtualAssetComplianceService:674`) enforcing hashed access-key lookup, revoked/expiry checks, IP-allowlist,
scope-based authz, and PII-scope gating (defense-in-depth token auth, audited). Well-architected external access.
**Record-trail viewer** (`/records/{type}/{id}`, verified 2026-07-16): elegantly two-tier — 7 hand-tuned rich types
(ALERT/CASE/TRANSACTION/MERCHANT/MULTI_ASSET_CUSTOMER+TRANSACTION/REPORT_EXECUTION) + a reflective `genericDetail`
that resolves ANY mapped JPA entity by class-name↔token normalization (`normalizeType`), with PSP-scoping + auto
relationship links. All 28 frontend-navigated record types (PSP/SAR/CHARGEBACK_DISPUTE/USER/rules/crypto/vasp/
mobile-money/market/CBK_SUBMISSION…) resolve to real data pages — every `/records/...` button works. mobile-money
domain also fully verified (V167 backs all 3 entities; 4 endpoints↔hooks match).
Now landed & audited CLEAN (2026-07-16): **virtual-asset/VASP compliance** — V171–V179 fully wired, including transaction and VASP sanctions evidence, reports, and FK indexes. Earlier crypto/VASP build-out notes W21-4..8 are superseded by this landing. Dormant/dead-wire: see W15/W16/W17.

---

## Wave 14 — Wiring Audit Findings (2026-07-15) 🔎 — TO IMPLEMENT

Continuous wiring audit of the current working tree (multi-asset, record-trail, sanctions,
alert-tuning, reporting). Backend `mvn clean compile` = **SUCCESS** (exit 0); Flyway versions
have no collisions (V146–148 correctly renumbered to V154–156). Items below are gaps found on
top of a compiling build — ordered by severity. Each has file:line so you can implement in full.

### 🔴 BLOCKER

- [x] **W14-1 — `alerts` table missing 4 mapped columns (prod startup will fail).** ✅ FIXED 2026-07-15
  → added `V162__alerts_missing_columns.sql` (severity/merchant_id/disposition_reason/disposed_by + 2 indexes).
  Correction: `disposed_by` is a `String` (investigator name), not a User FK → `VARCHAR(255)`.
  See `docs/CHANGES-BY-CLAUDE.md`. NOT yet applied to your DB (avoided `flyway:migrate` so it wouldn't
  apply your in-flight V159–161); apply when your batch is ready.
  `Alert.java` maps `@Column`s that **no Flyway migration creates**: `severity` (line 60),
  `merchant_id` (line 45), `disposition_reason` (line 71), `disposed_by` (line 74).
  - V1 `alerts` block (`V1__Initial_Schema.sql:46-56`) has none of them; `V5` `merchant_id` is on
    `compliance_cases`, not `alerts`; V10 adds only `disposition`/`disposed_at`; V152 adds only
    `psp_id`/`multi_asset_customer_id`/`source_type`/`source_reference`.
  - Production runs `spring.jpa.hibernate.ddl-auto=validate` (`application.properties:91`,
    `application-production.properties:40`) → a **clean deploy aborts at startup** with
    "missing column [severity] in table [alerts]". Also `V109__report_definitions_seed.sql:299`
    queries `a.severity`, and the multi-asset bridge calls `setSeverity(...)`.
  - **Fix:** add `V159__alerts_missing_columns.sql` →
    `ALTER TABLE alerts ADD COLUMN IF NOT EXISTS severity VARCHAR(20);`
    `ADD COLUMN IF NOT EXISTS merchant_id BIGINT;`
    `ADD COLUMN IF NOT EXISTS disposition_reason TEXT;`
    `ADD COLUMN IF NOT EXISTS disposed_by BIGINT;` (confirm `disposed_by` type vs the entity —
    it maps to a `User`; verify FK/type). Add an index on `merchant_id` for alert filtering.

### 🟠 HIGH

- [ ] **W14-2 — Sanctions screening fails OPEN on outage (compliance risk).**
  `RealTimeTransactionScreeningService.java:125` sets `shouldBlock = ... || (screeningUnavailable
  && blockOnUnavailable)` but produces **no matches** in that path. The consumer
  `DecisionEngine.java:296` gates blocking on `result.hasMatches() && result.shouldBlock()`, so the
  fail-closed signal is dropped and the transaction **proceeds** despite `block-on-unavailable:true`.
  `TransactionScreeningResult.isScreeningUnavailable()` is set-but-never-read.
  - **Fix:** in DecisionEngine, block when `result.isScreeningUnavailable() && blockOnUnavailable`
    regardless of `hasMatches()`; or have the service surface a synthetic "unavailable" match. Add a
    test asserting fail-closed behavior.
  - **RE-CONFIRMED 2026-07-16 — still present, TWO fail-open vectors** in `DecisionEngine.checkSanctionsScreening`:
    (a) `if (realTimeScreeningService == null) return null;` → no block when the screening bean is absent; and
    (b) the `catch (Exception e)` block returns `null` with an explicit comment *"Don't block on screening errors -
    fail open for availability."* So both a missing service AND a screening exception silently approve the txn.
    For sanctions this should fail-CLOSED (BLOCK or HOLD-for-review), not open. ⚠️ NOTE: `SanctionsScreenClient.java`
    is currently modified in git (you're editing it) — Claude did NOT touch this; verify/adjust when you're done.
    The `SanctionsScreenClient` fallback correctly returns `null` and documents "callers MUST treat null as
    unavailable" (`:35`) — the bug is that `DecisionEngine` treats null as "OK/allow" instead of "unavailable/hold".
  - ✅ **PARTIAL PROGRESS (verified 2026-07-16): the aml-microservice now FAILS CLOSED.** You implemented + tested it —
    `SanctionsAvailabilityTest`: disconnected Aerospike → `screenName` status `UNAVAILABLE` (no silent pass); and
    `unavailableSanctionsForcesTransactionReview` asserts decision `REVIEW` + riskLevel `MEDIUM` +
    `SANCTIONS_UNAVAILABLE` indicator when sanctions are down. Exactly the fail-closed pattern. **Remaining:** mirror
    this in the BACKEND `DecisionEngine.checkSanctionsScreening` (still returns `null`/allow on unavailable) so the
    backend real-time path also holds/reviews instead of failing open. (Backend `SanctionsScreenClient` still in your
    active edits — Claude not touching it.)

### 🟡 MEDIUM

- [ ] **W14-3 — PEP detection silently degrades on the microservice path.**
  `AerospikeSanctionsScreeningService.java:64-77` maps the microservice `MatchDto`, which carries
  only `matchedName/similarityScore/listName/entityId` — so `ScreeningResult.Match.pepLevel`
  (and `aliases/nationality/programs/position/dateOfBirth/rawData`) is never populated. But
  `AmlScreeningOrchestrator` reads `pepLevel` at lines 148, 245, 281 to decide PEP status → on this
  path PEP detection collapses to `"PEP".equals(listName)` only.
  - **Fix:** extend `MatchDto`/`SanctionsScreenResponse` (microservice) to emit `pepLevel` etc., and
    map them through; or document that PEP enrichment only comes from the Aerospike-direct path.

### 🟢 LOW / cleanup

- [ ] **W14-4 —** `AerospikeSanctionsScreeningService.java:75` hardcodes `sanctionType = "Sanctions match"`
  for every match → derived `sanctionsMatch` flag is always true when any match exists (non-discriminating).
- [ ] **W14-5 —** `AerospikeSanctionsScreeningService.java:151-159` — microservice returns HTTP 503 for
  `UNAVAILABLE`, so `mapStatus("UNAVAILABLE")` is unreachable and the 503 trips the **shared**
  `amlMicroservice` circuit breaker, degrading the AML-score path during sanctions outages. Consider a
  dedicated breaker or a 200-body UNAVAILABLE contract.
- [ ] **W14-6 —** `AmlScreeningOrchestrator.java:204` (pre-existing) `objectMapper.convertValue(
  result.getMatches() /*List*/, Map.class)` — type mismatch that can throw whenever matches exist;
  verify and switch to a proper `Map` payload.
- [ ] **W14-7 —** `AlertToCaseService.java:156` redundant no-op ternary; and `AlertController.java:258`
  sets `disposingUser` only when the principal `instanceof User` — otherwise the `CASE_CREATED`
  timeline activity row is silently skipped (case still created & linked). Confirm principal type.
- [ ] **W14-8 —** `SchemeReportingController.java:72-73` still carries the MVP comment "returning JSON as
  a file … production would convert to CSV/PDF". Implement real CSV/PDF export or remove the note.
- [ ] **W14-9 (no-consumer endpoints)** — backend endpoints with no frontend UI (Swagger-only):
  `AlertTuningController` (`POST /alerts/tuning/suggest`, `GET /alerts/tuning/pending`,
  `POST /alerts/tuning/{id}/apply`) and `GET /multi-asset/signals`. Either build UI or confirm intent.

### 🟡 MEDIUM (frontend)

- [ ] **W14-10 — User admin writes bypass `apiClient` and drop the PSP header.**
  `FRONTEND/src/pages/Users/UsersTab.tsx:27-60` — save/delete/toggle mutations use raw
  `fetch("/api/v1/users…")` instead of `apiClient`, so they omit the `X-PSP-ID` header and
  `credentials:"include"` that every other call sends. Endpoints exist (`POST /users`, `PUT /users/{id}`,
  `DELETE /users/{id}`, `PATCH /users/{id}/toggle` — `UserController.java`), but if the backend enforces
  PSP scoping via that header these writes are mis-scoped or rejected. The reads on the same page correctly
  use `apiClient`. **Fix:** route these through `apiClient.post/put/delete/patch`.

### 🟢 LOW (frontend) / cleanup

- [ ] **W14-11 —** `queries.ts:374 & 872` — `useMonitoringDashboardStats` and `useTransactionStats` both
  hit `monitoring/dashboard/stats` under the **same** `queryKey ["monitoring","dashboard-stats"]` but are
  typed differently (`Record<string,unknown>` vs `TransactionStats`) → React Query cache-key collision.
  Give one a distinct key.
- [ ] **W14-12 —** `/regulatory-reports` (`App.tsx:99`) is a full standalone page with **no sidebar entry
  point** (reachable only via deep link). Add a nav link or confirm it's intentionally deep-link-only.
  (Also `/chargebacks`, `/billing`, `/analytics`, `/compliance-calendar` have no sidebar entry — likely intentional.)

> Fully-clean areas (audited, no action): **frontend↔backend API wiring** — all ~90 endpoints across
> `queries.ts`/`mutations.ts`/per-page calls resolve to real controller mappings; no mock/placeholder data;
> routes↔sidebar↔components consistent; `types/index.ts` exports consumed correctly.
> Report-traceability/record-trail (entities↔V157/V158 match, `ReportRunTraceService` invoked,
> `RecordDetailPage` wired); multi-asset module (entities↔V151/153 match column-by-column,
> `BlockchainAnalyticsClient` bean+config present, `Customer360Page` wired); `RuleEffectivenessService`
> package move is clean (all 6 consumers resolve). Backend compiles; Flyway versions collision-free.

---

## Wave 42 — Off-by-default AML/compliance controls (prod config audit) (bug-hunt) (2026-07-16) 🔎

- [ ] **W42-1 (HIGH, deployment/compliance) — several AML/compliance controls default to DISABLED; if prod doesn't
  enable them they silently do nothing.** Systematic sweep of `@Value("${...enabled:false}")`. The compliance-critical
  ones (each = a silent gap unless the deployment sets it true + configures deps):
  - `sanctions.download.enabled=false` — watchlist never loaded → all screening passes (**W38-2**, worst-case).
  - `blockchain.analytics.enabled=false` (`BlockchainAnalyticsClient`) — **crypto wallet risk/sanctions screening
    off** → `WalletScreeningRecord` risk data unavailable (undercuts the W21-6 wallet-screening feature at runtime).
  - `sumsub.enabled=false` (`SumsubAmlService`) — **KYC identity verification (Sumsub IDV) off** → this is WHY W29-2
    saw "no real verification": the IDV integration EXISTS but is disabled by default. Enable + configure for real KYC.
  - `app.document.antivirus.enabled=false` (`ClamAvDocumentScanner`) — **document AV scanning off by default** →
    refines W29-5: the ClamAV scan the upload path advertises only runs if enabled; verify prod sets it true (else
    "fails closed" isn't true).
  - `travel-rule.gateway.enabled=false` — VASP travel-rule transmission won't send (expected for dev; confirm prod).
  - `notifications.email-enabled=false` (NotificationService + ReportDeliveryService) — **invoice emails, alert/report
    delivery via SMTP off** → billing invoices (W16/W25) and report delivery don't actually email unless enabled.
  - (Lower risk / intentional-off: `auth.emergency-reset.enabled`, `vgs.proxy.enabled`, `ai.rule-generator.enabled`,
    `server.http2.enabled`, `ultra.throughput.enable.reactive`, `slack.enabled`.)
  **Fix:** produce a **production readiness checklist** of required feature flags + their deps, and add startup
  assertions (extend `EnvVarStartupValidator`) that alarm when a compliance-critical control (sanctions download, AV,
  IDV, blockchain analytics) is disabled in a `production` profile. Off-by-default is fine for dev; the risk is a prod
  deploy silently missing an AML control.
  - **Implementation note (2026-07-16): `EnvVarStartupValidator` is the right place + already has the machinery** —
    profile-aware `EnvVarSpec.requiredIf(...)`, an OK/WARN/MISSING banner, and a `.env.missing` stub. Add specs for
    `sanctions.download.enabled` / `app.document.antivirus.enabled` / `sumsub.enabled` / `blockchain.analytics.enabled`
    that surface WARN when false under `production`. ⚠️ BUT the validator **deliberately never fails the boot** (its
    doc: "log-based prompts... without the JVM crashing") — so a WARN is consistent with the design; escalating
    **sanctions-download-disabled to a hard fail-closed** (which is safest for AML) would be an intentional deviation
    from that log-only policy — your call which compliance flags justify blocking startup vs just alarming.

---

## Wave 41 — AML decision ignores rules in the ML-scoring fallback branch (bug-hunt) (2026-07-16) 🔎

- [ ] **W41-1 (LOW — verified DEAD in practice, but fix for safety) — the `deriveDecision` fallback keys the AML
  decision purely off the ML score, ignoring the rule engine's BLOCK/HOLD.** ✅ **Severity corrected 2026-07-16:**
  `FraudDetectionOrchestrator` is an unconditional `@Service` (`:22`), so the bean is always present → `orchestrator
  != null` is always true → **Branch B never executes** (it's dead code; the `@Autowired(required=false)` is just
  defensive). The LIVE path (Branch A) runs `orchestrator.processTransaction` → `DecisionEngine` (`FraudDetectionOrchestrator:29`),
  which DOES aggregate rules+ML+sanctions into the action — so rules ARE enforced on the real path. W41-1 is only a
  latent risk if `orchestrator` ever fails to construct (its own deps go missing). Still worth fixing the dead branch to
  fold `rule_decision` into `deriveDecision` (defense-in-depth), but NOT a live bug. Full detail: `AmlCheckController.check` has two branches:
  (A) `if (orchestrator != null)` → `result.getAction()` (`:129-134`, decision-engine path — incorporates rules); and
  (B) the `else if (scoringService != null …)` fallback (`:145-158`) → `response.put("decision", deriveDecision(scored.getScore()))`.
  `deriveDecision` (`AmlCheckController` bottom) is ML-score-only: `>=0.85 → DECLINED, >=0.5 → MANUAL_REVIEW, else
  APPROVED`. But `scored.getRiskDetails()` carries the rule engine's `rule_decision` (BLOCK/HOLD from
  `RulesExecutionService.strongestDecision`), and `deriveDecision` **never consults it**. So in Branch B a txn the RULES
  say BLOCK but ML scores low (e.g. 0.2) is **APPROVED** → rule-based controls bypassed. Also `ScoringService.scoreTransaction`
  returns `score = ML score only` (the rule `scoreAdjustment` is computed but not folded into the returned score).
  **Severity depends on reachability:** if the `orchestrator` bean is always present, Branch B is dead; if `orchestrator`
  can be null (not configured in some profile), Branch B is live and rules are silently not enforced. **Fix:** in Branch
  B, combine `rule_decision` with the ML-derived decision using the same `strongestDecision` monotonic escalation (a rule
  BLOCK/HOLD must win over a low ML score), OR route everything through the orchestrator. **Verify whether `orchestrator`
  is ever null** (which profiles) to set priority.

---

## Wave 40 — CTR/SAR auto-detection is non-functional (regulatory gap) (bug-hunt) (2026-07-16) 🔎

- [ ] **W40-1 (HIGH, regulatory reporting — CTR/SAR never auto-flagged).** The transaction scoring output surfaces
  `ctr_required`/`sar_required` (`ScoringService.java:262-263`, from `RuleEvaluationResult`), but those booleans are
  set **only from the Drools result** (`RulesExecutionService.java:98-99` — `ctrRequired = ctrRequired ||
  droolsResult.isCtrRequired()`), and **Drools never fires** (W28-3: no `DROOLS_DRL` rule seeded, all rules are SPEL).
  → `ctr_required`/`sar_required` are **permanently false** on the live path. Meanwhile the ONE component with correct
  CBK CTR-threshold logic — `compliance/RegulatoryComplianceService.java:186` (`@Value compliance.cbk.ctr.threshold.kes
  = 1,000,000`, "Amount exceeds CBK CTR threshold") — has **ZERO callers** (grep of the class name outside its own file
  = empty; never `@Autowired`/injected). So **no transaction over the CBK CTR threshold is ever auto-flagged** for a
  Currency Transaction Report through the live pipeline. For a Kenyan AML/CBK SaaS this is a serious compliance gap
  (missed CTRs = regulatory violation). **Fix (decide the intended design):** either (a) wire the SPEL path to set
  ctr/sar from an amount-threshold check (or invoke `RegulatoryComplianceService`) instead of the dead Drools branch,
  or (b) seed the `DROOLS_DRL` rule so Drools actually runs (W28-3) and produces ctr/sar, or (c) if CTR/SAR are meant to
  be filed manually by compliance officers via `SarWorkflowService`, remove the misleading always-false `ctr_required`/
  `sar_required` from the scoring output. Confirm which — but today the auto-CTR path is dead end-to-end.
  (Also noted: `RuleDataSeeder` R-2 flags amount ≥ 10000 while CBK CTR is KES 1,000,000 — reconcile the amount regimes/
  currency so "large amount" rules and CTR threshold agree.)
  - **DE-RISK NOTE (2026-07-16): manual SAR FILING works well — the gap is auto-DETECTION only.** `SarWorkflowService`
    is a robust maker-checker state machine (DRAFT→PENDING_REVIEW→APPROVED→FILED; creator can't approve/reject own;
    FILE_SAR permission + PSP isolation + state-transition validation — compliance-grade). So officers CAN file SARs via
    a controlled workflow; W40-1's defect narrows to "the system never AUTO-flags which txns need a CTR/SAR" (an officer
    must notice manually). Lowers W40-1 from "no SAR capability" to "no auto-triage" — still important for CTR
    (threshold-mechanical, expected automatic) but SAR has a working manual path.

---

## Wave 39 — Rule-engine decision-path correctness (bug-hunt) (2026-07-16) 🔎

Bug-hunt of the live SpEL rule path `RulesExecutionService.evaluateTransaction` (`BACKEND/.../service/rules/`).
The `strongestDecision` escalation is otherwise correct + monotonic (BLOCK sticky, HOLD>REVIEW, verified). Two gaps:

- [ ] **W39-1 (MEDIUM, AML fail-open) — a rule whose SpEL expression throws silently doesn't fire, with no runtime
  escalation.** `:108-111` catches `RuntimeException`, logs ERROR, and leaves `triggered=false` → the transaction
  proceeds as if the rule passed. A malformed/misconfigured critical rule (or a missing feature) = a silently-disabled
  AML control. Only an `effectivenessService.recordExecution(...,ERROR)` row is written; nothing surfaces at decision
  time. **Fix (compliance call):** for rules flagged mandatory/critical, treat an evaluation ERROR as fail-CLOSED
  (escalate that txn to REVIEW/HOLD) rather than silently skipping; and alert when a rule's ERROR rate spikes.
- [ ] **W39-2 (LOW-MEDIUM, correctness) — `strongestDecision` ignores unrecognized rule actions (no fail-safe default).**
  `:158-169` maps BLOCK/SUSPEND→BLOCK, HOLD→HOLD, ALERT/FLAG→REVIEW, else `return current`. A rule configured with
  action `"REVIEW"` (a plausible value) — or any typo'd/new action outside that set — **triggers** (adds score + reason)
  **but does NOT escalate the decision** (stays ALLOW). So an analyst who sets a rule's action to "REVIEW" gets a
  no-op decision. **Fix:** add an explicit `REVIEW`→REVIEW branch and a fail-safe `default` that escalates any unknown
  non-empty action to at least REVIEW (never silently ignore an action on a triggered rule). Verify V142/V143 seed
  actions are all in the recognized set.

---

## Wave 38 — Sanctions matching RECALL GAP (AML false-negative risk) (bug-hunt) (2026-07-16) 🔎

- [x] **W38-2 (HIGH) — ✅ FIXED 2026-07-16 (safeguard; compile-verified).** Added `config/health/SanctionsDataHealthIndicator`
  (`/actuator/health` → `sanctionsData` DOWN when the watchlist count is 0/unavailable) + `config/startup/SanctionsDataStartupCheck`
  (loud ERROR banner at boot when empty; **fails closed under `production`** by default via
  `sanctions.startup.fail-closed-on-empty`, alarm-only in dev/test). Uses existing `SanctionsCountClient` →
  microservice `/internal/v1/sanctions/count`. The empty-list condition is no longer silent. **You still own the config**
  to actually LOAD the list (`sanctions.download.enabled=true` + `sanctions.opensanctions.url`). See CHANGES-BY-CLAUDE.md.
  Original finding: sanctions data download is DISABLED by
  default; if not enabled in the deployment, ALL screening returns clear. `service/download/SanctionsListDownloadService.java`
  downloads OpenSanctions daily (`@Scheduled` cron `:87`) and pushes entities to the microservice ingest
  (`POST /internal/v1/sanctions/ingest`) — but it is gated by `@Value("${sanctions.download.enabled:false}")` (`:67-68`,
  early-returns when false, `:90-92`) and requires `sanctions.opensanctions.url` (`:73`, **no default**). So unless the
  deployment sets `sanctions.download.enabled=true` AND a valid OpenSanctions URL, the Aerospike sanctions set is never
  populated → **every sanctions screen returns no matches (everything passes)** — more fundamental than the W38-1 recall
  gap. **Fix/verify:** (1) confirm prod config sets `sanctions.download.enabled=true` + the URL (and the microservice's
  ingest key); (2) add a startup assertion / health indicator that the sanctions set is non-empty and fresh (record
  count + last-load timestamp), and **fail-closed or alarm** if it's empty — an empty watchlist silently disabling all
  sanctions screening is the worst-case AML failure. (`EnvVarStartupValidator` exists — extend it to assert these.)



- [ ] **W38-1 (HIGH, AML correctness — sanctions false negatives) — the microservice prefix-index fast path misses
  first-character name variants.** `aml-microservice/.../service/SanctionsService.java`: `screen()` takes a fast path
  `screenByPrefix` that queries Aerospike with `Filter.equal(BIN_PREFIX, extractPrefix(normalizedQuery))` — the **first
  2 chars** of the normalized name (`:37,128,163-172`). Only candidates sharing those exact 2 chars are ever passed to
  the fuzzy `similarity()` matcher. The full-scan fallback (`screenByScan`) fires **only inside the `catch` on an
  Aerospike exception** (`:176-181`) — NOT when the prefix query succeeds but the real match has a different prefix. So a
  sanctioned entity whose name differs from the query in the first 1-2 chars is **never evaluated** → **missed match**.
  This defeats fuzzy matching for the exact class sanctions screening must catch: transliteration/spelling variants that
  differ at the start — **Osama/Usama** (O↔U), Youssef/Yousef, Abdul/Abdel, Mohammed/Muhammad, etc. OFAC/UN guidance
  explicitly warns about first-letter transliteration variants. **This is a compliance-grade recall gap.** **Fix options
  (your call — AML/compliance decision):** (a) fan out the prefix query over the query prefix + known first-char variant
  set; (b) index on a **phonetic** key (Soundex/Double-Metaphone/Beider-Morse) instead of literal first-2-chars, so
  Osama/Usama share a bucket; (c) periodically also run a full fuzzy scan; or (d) only use the prefix index to *narrow*,
  and full-scan when the top prefix-score is below a recall threshold. (Contrast: the fuzzy `similarity()` itself and the
  `>=threshold`/`>=FLAGGED` banding look correct; the gap is purely candidate *retrieval* recall.)
  - ✅ **CONFIRMED ON THE LIVE PATH (2026-07-16):** backend real-time txn screening
    `RealTimeTransactionScreeningService` → `AerospikeSanctionsScreeningService.screenName:51` →
    `sanctionsScreenClient.screen(...)` → the aml-microservice `SanctionsService` with this prefix recall gap. So it
    affects REAL transaction sanctions screening, not a cache/secondary layer → **W38-1 is a genuine live compliance
    risk, HIGH priority.** (The backend `NameMatchingService` does full Levenshtein fuzzy matching but is NOT on this
    real-time path — the real-time path relies entirely on the microservice's prefix-limited candidate retrieval.)
  - ✅ **ALIASES DO NOT MITIGATE IT (2026-07-16):** at ingest (`SanctionsService.ingestEntities:266`) the `prefix` bin is
    computed from the **primary name only** (`extractPrefix(normalizedName)`), one record per entity; aliases are stored
    as a CSV bin (`:270`) and only similarity-checked *after* retrieval (`:219-221`). So a listed alias whose first 2
    chars differ from the primary name (primary "Osama…" indexed under `OS`, alias "Usama…") is **never retrieved** by
    the alias's own prefix (`US`). **Cleanest fix:** at ingest, write a prefix-index entry per name+alias (multi-valued
    prefix bin, or one record per (entity, name-variant)) so any variant's first-2 chars can retrieve the entity — less
    invasive than a full phonetic re-index, and directly closes the recall hole.

---

## Wave 37 — M-Pesa payment callback robustness (bug-hunt) (2026-07-16) 🔎

Bug-hunt of the money-path `MpesaService.processCallback` (`integration/mpesa/MpesaService.java:204`, invoked by the
public `POST /billing/payments/mpesa/callback`). Two genuine gaps:

- [ ] **W37-1 (MEDIUM, correctness — payment idempotency) — duplicate Daraja callbacks reprocess.** Daraja retries the
  STK callback until it gets a 200 and can deliver duplicates, but `processCallback` has **no idempotency guard**: it
  looks up the `PaymentAttempt` by `checkoutRequestId` and, if `ResultCode==0`, unconditionally re-sets SUCCESS and calls
  `invoice.markAsPaid(...)` (`:241-250`). `Invoice.markAsPaid` (`entity/psp/Invoice.java:546`) is itself unguarded — it
  just sets `status="PAID"`, `paidAt=LocalDateTime.now()`, ref, amount. So a repeat callback overwrites the original
  `paidAt`, redundantly saves, and any future "invoice became paid" side-effect (email, ledger, event) would double-fire.
  **Fix:** early-return if `attempt.getStatus()` is already terminal (SUCCESS/FAILED/CANCELLED) OR the invoice is already
  PAID — process the transition once.
- [ ] **W37-2 (MEDIUM, correctness — no amount validation) — invoice marked paid for the REQUESTED amount, not the
  amount actually paid.** `:248` `invoice.markAsPaid(mpesaReceiptNumber, attempt.getAmount())` uses the STK-push amount;
  the callback's `CallbackMetadata` `Amount` field is parsed for `MpesaReceiptNumber` only and the `Amount` value is
  ignored. A partial/mismatched payment (or a manipulated callback) still marks the invoice fully PAID. **Fix:** extract
  `Amount` from the callback metadata and verify it equals `attempt.getAmount()` (within tolerance) before `markAsPaid`;
  on mismatch, record a discrepancy status instead of PAID. (Related: `initiateSTKPush:133` sends `amount.intValue()` —
  truncates to whole KES rounding DOWN, so a 100.50 invoice charges 100 but is later marked paid for the full attempt
  amount. Inherent to KES/M-Pesa; consider `setScale(0, HALF_UP)` + reconciling the attempt amount to what's charged.)
- [ ] **W37-4 (LOW, billing robustness) — `computeTieredCost` parses `up_to` non-defensively (inconsistent with `rate`).**
  `BillingService.java:135` casts `((Number) upToRaw).longValue()` — throws `ClassCastException` if `tier_config`'s
  `up_to` is a JSON **string** (`"10000"`) rather than a number, which crashes `calculateUsageCost` for that line. Yet
  `rate` two lines up IS parsed defensively (`new BigDecimal(rateRaw.toString())`, handles string-or-number). The tier
  math itself is correct (verified: count 150k over 0.005/0.004/0.003 tiers → 560). **Fix:** parse `up_to` the same
  defensive way, e.g. `Long.parseLong(upToRaw.toString().trim())` (guard null→MAX). Also consider validating tiers are
  ascending by `up_to` (out-of-order config silently mis-bills via `Math.max(0, upTo-consumed)`→0-slice). Low sev (only
  bites if a TIERED rate is configured with a string `up_to`), but it's a latent hard-to-diagnose billing failure.
- [ ] **W37-3 (LOW, security note) — the callback is `permitAll` with no authenticity check beyond an unguessable
  `checkoutRequestId`.** Standard M-Pesa posture (Daraja provides no signature), but production should restrict
  `POST /billing/payments/mpesa/callback` to Safaricom's Daraja IP ranges (nginx allow-list or a filter) so a forged
  success callback with a leaked/guessed `checkoutRequestId` can't mark an invoice paid without real payment.

---

## Wave 36 — PSP API docs vs code: documented endpoints that 404 (2026-07-16) 🔎

Fresh angle on "buttons/pages do what they are **documented** to do" — checked `docs/PSP_API_GUIDE.md` (the guide PSPs
integrate against) vs actual controllers. Spot-checked the suspicious endpoints; **3 of them are wrong** → a PSP
following the guide gets 404s. (A full doc↔code reconciliation of all ~25 documented endpoints is warranted.)

- [ ] **W36-1 (MEDIUM, docs) — `POST /api/v1/auth/refresh` documented but wrong path.** Actual session refresh is
  `POST /api/v1/auth/session/refresh` (`SessionController.java:21,68` — `@RequestMapping("/auth/session")` +
  `@PostMapping("/refresh")`). **Fix:** correct the guide to `/auth/session/refresh` (or add an `/auth/refresh` alias).
- [ ] **W36-2 (MEDIUM, docs+feature) — `POST /api/v1/webhooks/subscribe` documented but DOES NOT EXIST.** No webhook-
  subscription endpoint anywhere; only inbound Verifi/chargeback webhook receivers exist. A PSP can't register a
  callback URL for AML results despite the guide promising it. Ties to **W26-8** (no webhook settings on `Psp`).
  **Fix:** either build the webhook-subscribe capability (a real SaaS integration need) or remove it from the guide.
- [ ] **W36-3 (MEDIUM, docs) — `POST /api/v1/transactions/batch-score` documented but wrong path.** Actual batch
  scoring is `POST /api/v1/batch/score/yesterday` (`BatchController.java:19,37` — `@RequestMapping("/batch")`). No
  batch endpoint under `/transactions`. **Fix:** correct the guide (and consider whether a general
  `/transactions/batch-score` accepting a supplied batch is intended vs the yesterday-only cron trigger).
- [ ] **W36-4 (LOW) — reconcile the rest.** The other ~22 documented endpoints (billing, subscriptions, cases,
  reports, cbk, risk, sanctions) matched on spot-check, but do a full pass: for each documented `METHOD /path`, confirm
  a controller mapping exists with that exact verb+path. Doc drift here directly violates the "documented behavior" goal.
- [ ] **W36-5 (MEDIUM, docs — billing/cost, directly on-goal) — `docs/features/BILLING_PRICING.md` documents the
  PRE-W16-1 (broken) billing vocabulary.** Its "Billable Endpoints" table (`:72-74`) still lists
  `TRANSACTION_PROCESSING | /transactions/*`, `SANCTIONS_SCREENING | /screening/*`, `AML_CHECK | /aml/*` — the exact
  stale service-type names + wrong paths that were the W16-1 $0-billing root cause. Actual (post-fix) vocab:
  `TRANSACTION_MONITORING` (on `/transactions/ingest`), `SANCTIONS_SCREENING_PERSON/ORGANIZATION` (on `/sanctions/screen`;
  `/screening/*`→`AML_SCREENING`), `AML_SCREENING` (on `/aml/check|detection`). Also uses `CASE_MANAGEMENT` where the
  real type is `COMPLIANCE_CASE_CREATION`, and omits the new services (wallet/VASP/travel-rule/EDD — see W34-1). **Fix:**
  regenerate the billable-endpoints + service-type table from the current `UsageTrackingFilter` map + seeded
  `billing_rates` so the cost documentation matches how billing actually computes. (Anyone using this doc for
  cost-calculation gets wrong service names/prices.)

---

- [ ] **W36-6 (LOW, billing cruft) — `V147` seeds DEAD `billing_rates` rows in the old vocabulary.** `V147:67-69`
  `INSERT INTO billing_rates (... service_type ...) FROM (VALUES ('TRANSACTION_PROCESSING'),('SANCTIONS_SCREENING'),
  ('AML_CHECK'), ...)`. Post-W16-1 the `UsageTrackingFilter` emits `TRANSACTION_MONITORING`/`SANCTIONS_SCREENING_PERSON`
  /`AML_SCREENING`, so these V147 rows never match `findDefaultRate` → they're orphan/dead, and `billing_rates` now holds
  two competing vocabularies (V147 old + V149/V169 canonical). Harmless to billing (dead rows) but confusing. Since you're
  already reworking V147 (see W17-13 alpha-3 blocker), drop or rename these rows to the canonical vocab in the same pass.
  (Only non-doc stale-vocab site left; the rest is a javadoc comment at `PrometheusMetricsService.java:1059` — trivial.)

---

## Wave 35 — Multi-tenancy isolation model (defense-in-depth) (2026-07-16) 🔎

**Verified real (not stub):** `RlsContextFilter` (`config/RlsContextFilter.java`) extracts the authenticated user's
pspId → sets `RlsContextHolder` ThreadLocal per request (cleared in `finally`); ~10 CBK controllers consume
`getCurrentPspId()` to scope queries. Combined with the explicit `canAccessPsp`/`requireMerchantAccess` guards you
added, tenant isolation IS enforced.

- [ ] **W35-1 (LOW, defense-in-depth) — isolation is application-enforced only; no DB/ORM backstop.** There is NO
  Hibernate `@FilterDef`/`@Filter` and no Postgres row-level-security policy — every repository query/controller must
  *remember* to scope by pspId (via `RlsContextHolder` or an explicit check). A single forgotten scope = cross-tenant
  leak — which is exactly how W26-4 (`/psps/{id}`) and W29-1 (KYC docs) IDORs arose. **Recommend (your call):** add a
  Hibernate `@FilterDef(name="pspFilter", ...)` auto-enabled from `RlsContextHolder.getCurrentPspId()` on the key
  tenant-owned entities (Alert, Merchant, Case, Transaction, MultiAssetCustomer, etc.), OR Postgres RLS policies keyed
  on a `SET app.current_psp` the filter issues — so isolation holds even when an individual query forgets to scope.
  Not urgent (the explicit guards work today), but it would convert a recurring bug class into a structural guarantee.

---

## Wave 34 — Billing coverage of NEW AML services (revenue-leak check) (2026-07-16) 🔎

- [ ] **W34-1 (MEDIUM — ✅ CONFIRMED 2026-07-16 — new AML services bill $0, revenue leak).** The new revenue-bearing
  AML operations you built are billed on NEITHER of the two required legs:
  - **Not mapped** in `UsageTrackingFilter` (static block `:71-94`) — it maps only the original 9: SANCTIONS_SCREENING_
    PERSON/ORG, AML_SCREENING, TRANSACTION_MONITORING, RISK_ASSESSMENT, REPORT_GENERATION, KYC_VERIFICATION,
    COMPLIANCE_CASE_CREATION, SAR_FILING, CBK_REPORTING. No pattern for `/virtual-assets/**` (wallet screening,
    travel-rule) or `/compliance/kyc/merchants/{id}/beneficial-owners/{id}/screen` (EDD/UBO screen). (The filter's
    header comment now cites "V149 + V167" but the mappings weren't extended — dropped logic.)
  - **No rate seeded** — `billing_rates` has exactly 11 types (V149/V167/V169): the 9 above + `API_CALL_GENERIC` +
    (dupe). **Zero** rows for `WALLET_SCREENING`/`VASP_SCREENING`/`TRAVEL_RULE_TRANSFER`/`EDD_SCREENING`. So even if
    mapped, `calculateUsageCost` returns ZERO (the exact W16-1 root cause).
  **Impact:** every crypto wallet screen, VASP screen, travel-rule transfer, and EDD/UBO screen = a free AML check =
  direct revenue leak for the SaaS. ⚠️ You are ACTIVELY EDITING `UsageTrackingFilter` (extending coverage) — Claude
  deferred. **Exact billable endpoints (confirmed 2026-07-16):**
  - `POST /api/v1/virtual-assets/vasps/{id}/screen` → e.g. `VASP_SCREENING`
  - `POST /api/v1/virtual-assets/wallets/{id}/screen` → e.g. `WALLET_SCREENING`
  - `POST /api/v1/virtual-assets/travel-rule/transfers` → e.g. `TRAVEL_RULE_TRANSFER`
  - `POST /api/v1/compliance/kyc/merchants/{mid}/beneficial-owners/{oid}/screen` → e.g. `EDD_SCREENING`
  (POST `/wallets`, `/travel-rule/policies` are config, not per-screen billable — don't meter those.)
  **Fix (2 legs) — ready-to-paste leg 1** (add to `UsageTrackingFilter` static block after line 91):
  ```java
  // Virtual-asset compliance — discrete crypto/VASP screening AML checks (billable per screen).
  map("^/api/v1/virtual-assets/wallets/[^/]+/screen.*", "WALLET_SCREENING", "POST");
  map("^/api/v1/virtual-assets/vasps/[^/]+/screen.*",   "VASP_SCREENING",   "POST");
  // Enhanced Due Diligence — beneficial-owner (UBO) screening.
  map("^/api/v1/compliance/kyc/merchants/[^/]+/beneficial-owners/[^/]+/screen.*", "EDD_SCREENING", "POST");
  ```
  leg 2: new `V188+__seed_virtual_asset_billing_rates.sql` seeding a `billing_rates` row per new `service_type`
  (mirror V149/V169 shape). Both legs or it stays $0.
  - ⚠️ **TWO DECISIONS ARE YOURS (why Claude did NOT auto-implement — analogous to the RBAC/pricing calls):**
    1. **Prices** for WALLET_SCREENING / VASP_SCREENING / EDD_SCREENING (and travel-rule) — business/pricing decision;
       Claude won't invent your service prices.
    2. **Travel-rule billing model** — `POST /virtual-assets/travel-rule/transfers` is only the CREATE, but a transfer's
       lifecycle also has `POST .../transfers/{id}/transmit` and `.../verify-identity` (all POST). A coarse
       `travel-rule/transfers.*` pattern would **DOUBLE-BILL** one transfer across its steps. Decide whether to bill
       once at create (use an anchored pattern that excludes `/{id}/...` sub-paths) or per lifecycle step, THEN add the
       mapping. (This is why travel-rule is deliberately left out of the ready-to-paste block above.)

---

## Wave 33 — Security config: public actuator metrics (2026-07-16) 🔎

- [ ] **W33-1 (MEDIUM, info-disclosure — 🔒 your decision, Claude won't edit SecurityConfig) — actuator metrics/prometheus
  are publicly readable.** `SecurityConfig` maps `/actuator/**` → `permitAll` (line ~114) and exposure is
  `management.endpoints.web.exposure.include=health,info,metrics,prometheus` (`application.properties:403`,
  `application-production.properties:159`). So **anonymous** users can hit `/actuator/metrics` and `/actuator/prometheus`
  → leaks request counts/latencies, endpoint names, JVM/heap stats, error rates (recon-useful). `health`/`info` public is
  fine (`health.show-details=when-authorized` in prod). **Fix (you choose):** restrict `/actuator/prometheus` +
  `/actuator/metrics` to authenticated/internal (e.g. separate matcher requiring a monitoring role or IP allowlist, or
  scrape via a non-public management port) while keeping `/actuator/health` + `/actuator/info` public for probes.
- [ ] **W33-2 (LOW — verify) — `/api/v1/merchants/onboard` is `permitAll` (public self-onboarding).** Likely intentional
  for signup, and `ProductionRateLimitFilter` (100 RPM general) applies under `production` — but confirm onboarding
  specifically is rate-limited/captcha'd so it can't be scripted to mass-create merchant records. Also verify
  `/psps/register` (also public) has the same protection.

---

## Wave 32 — Frontend no-op/stub sweep (2026-07-16) 🔎

Swept all 68 frontend page files for stub signals. **Result: essentially clean** — no `onClick={() => {}}` no-op
buttons, no "coming soon"/"under construction"/placeholder pages, no `TODO/FIXME/STUB` comments in any page. 49/68
pages call APIs directly; the other 19 are router shells (e.g. `TransactionMonitoringPage`), presentational
sub-components, or use `features/api` hooks. Strongly supports the "all pages/buttons work as intended" goal.
Known real gaps remain the ones already logged (W26-1 header/sidebar Profile+Logout no-op; W25-3 no Generate-Invoice
button; W27-2/3 admin invoice-generate/notify; W30-1 limits UI) — those are missing FEATURES, not stub buttons.

- [ ] **W32-1 (LOW, consistency) — 3 native `alert()` error calls** instead of the app's `TwSnackbar`:
  `Users/RolesTab.tsx:52` (delete role fail), `Users/UsersTab.tsx:48` (delete user fail), `:62` (status update fail).
  They're on real mutations (functional, not stubs) but bypass the consistent snackbar UX + look unstyled. Swap to
  `TwSnackbar`.

---

## Wave 31 — Monitoring / admin domain (2026-07-16) 🔎

- [ ] **W31-1 (MEDIUM) — runtime-error tracking is populated but has no admin viewer.** `RuntimeErrorController`
  (`/admin/runtime-errors`) is **genuinely fed** server-side — `GlobalExceptionHandler`, `RuntimeErrorService`,
  `Http2HealthMonitorService`, and `RestClientService` all record runtime errors — but **no frontend calls
  `/admin/runtime-errors`** (grep = 0). So an operator can't see captured errors from the UI (operational blind spot
  for a "lightly-there management" console). **Fix:** add an admin Runtime Errors page (list + detail) wired to the
  existing endpoints. Cheap win — the data is already there.
- [ ] **W31-2 (LOW, cross-ref W27-5) — `PspAdminController` (`/admin/psp/*`) dead** (activate/suspend/terminate/theme/
  delete): zero frontend callers; the FE uses `/psps/*` instead. Also requires an `X-User-Role` header the FE never
  sends. Delete it or migrate the FE to it (one source of truth for PSP lifecycle).

**CLEAN:** `MonitoringAlertController` (`/monitoring/alerts` — summary/merchant/detail/acknowledge) is wired to
`MonitoringAlertsPanel` + Dashboard + Screening pages; `TransactionMonitoringController` (`/monitoring/dashboard/stats`
etc.) verified real-API-backed earlier (Wave feature verdicts).

---

## Wave 30 — Limits/thresholds UI vs backend (2026-07-16) 🔎

- [ ] **W30-1 (MEDIUM) — Limits UI exposes ~1 of ~10 backend capabilities.** `LimitsManagementController` (`/limits`)
  offers dashboard-stats, merchant limits (GET/POST), global limits (GET/POST/PUT/DELETE), aml limits (POST),
  **risk-thresholds (GET/POST)**, **velocity-rules (GET/POST/PUT/DELETE)**, country-compliance (GET/POST). But the only
  frontend page `pages/LimitsAml/LimitsAmlPage.tsx` calls **just `POST /limits/aml`** (transaction+daily limit). No UI
  for velocity-rules or risk-thresholds — notably the very tables my V165 psp_id fix backs are unmanageable from the UI.
  **Fix:** build limits-management tabs (merchant/global/velocity/thresholds/country) against the existing endpoints.
- [ ] **W30-2 (LOW) — `LimitsAmlPage` can't show current limits.** There is **no `GET /limits/aml`** (only `POST` at
  `LimitsManagementController.java:124`); the form's `transactionLimit`/`dailyLimit` default to empty strings and never
  load existing values, so it always renders blank and a save overwrites blindly. **Fix:** add a GET to hydrate the form.

---

## Wave 29 — KYC / document / CDD domain (2026-07-16) 🔎

Audited customer-due-diligence (documents, EDD, UBO). **Schema CLEAN** — all 4 entities (`MerchantDocument`,
`DocumentAccessLog`, `EnhancedDueDiligenceRequest`, `BeneficialOwner`) fully backed by V121/V122/V2, none @Audited,
no boot-fatal gaps. **Upload→list→preview→manual-verify works end-to-end** (`DocumentController` + `KycDocumentsPage.tsx`,
paths match, real persistence to `./uploads/{merchantId}/`). Issues below.

- [x] **W29-1 (HIGH, security IDOR) — FIXED 2026-07-16.** All metadata, upload, review, preview, and download paths enforce merchant PSP ownership; review is role restricted. ✅ _Claude verified: `requireMerchantAccess(merchantId)` (throws `AccessDeniedException` unless `user.psp == merchant.psp`; platform-admin exempt) is called by upload (`:76`), list (`:89`), verify (`:207`); role-gated `@PreAuthorize` on each._ Previous finding: Only 2 of 5
  `DocumentController` endpoints enforce PSP ownership (`serveDocument:122-129` for file/download). The other 3 are
  guarded by `isAuthenticated()` only, **no PSP scoping / role limit**:
  - `GET /merchants/{id}/documents` — any authenticated user enumerates ANY merchant's KYC doc metadata (name/type/status).
  - `POST /merchants/{id}/documents` — any authenticated user uploads docs to ANY merchant.
  - `PUT /documents/{id}/verify?approved=` — any authenticated user (incl. AUDITOR or another PSP's user) can
    approve/reject ANY KYC document.
  The frontend sends `X-PSP-ID` on upload but the backend never consults it. **Fix:** enforce that the merchant
  belongs to `currentUser.getPsp()` on list/upload/verify, and restrict verify to compliance/PSP_ADMIN roles.
  (Same isolation class as W26-4 / W20-4.)
- [ ] **W29-2 (HIGH, stub) — no automated identity/document verification.** `DocumentManagementService.verifyDocument:71`
  just sets status VERIFIED/REJECTED from the caller's `approved` boolean — no OCR, no external IDV/KYC provider, no
  checksum/authenticity/expiry check. Pure manual toggle. If SaaS KYC needs real IDV, integrate a provider; otherwise
  document that verification is manual-review-only.
- [x] **W29-3 (MEDIUM) — FIXED 2026-07-16.** Upload API/UI capture and persist `expiryDate`, feeding the existing expiry queries and scheduled tracking. Previous finding:
  `DocumentManagementService.uploadDocument:61-66` leaves `expiryDate` null, so `findExpiringDocuments` and the
  `KycExpirationTrackingService` daily cron (`:128`) never find anything. **Fix:** capture expiry on upload (form field
  or OCR) so the refresh/expiration crons actually fire.
- [x] **W29-4 (MEDIUM — FIXED 2026-07-16) — CDD/UBO/EDD exposed end to end.** Tenant-safe APIs and `/kyc-documents/{id}` now provide CDD/completeness, UBO CRUD and live screening, EDD checklist/history, encrypted identifiers, keyed relationship lookup, and append-only evidence events.
  > ✅ **Claude verified (2026-07-16):** `KycDueDiligenceController` `/compliance/kyc/merchants/{merchantId}` exposes
  > `GET /overview`, beneficial-owner CRUD + `POST /{id}/screen`, `GET/POST /edd` + `PUT /edd/items/{code}` with
  > role-gated `@PreAuthorize` (writes → admin/compliance/MLRO/PSP_ADMIN; uses REAL role names — does NOT repeat the
  > W19-1 bug). Frontend `KycMerchantDetailPage.tsx` paths all match the controller. `EddEvidenceEvent` backed by V187,
  > UBO PII via AES-GCM converters, evidence append-only. Well-wired end-to-end.
- [x] **W29-5 (LOW) — FIXED 2026-07-16.** Uploads enforce size, allowed MIME and magic bytes, path confinement, SHA-256 evidence, and real ClamAV INSTREAM scanning; production fails closed. Previous finding: upload has no file-type/size/content validation or virus scan (only a `..` path-traversal
  guard, `DocumentManagementService:36`). Add allow-list of MIME types + size cap + AV scan for a public SaaS upload.
- [x] **W29-6 (LOW — FIXED 2026-07-16) — corporate graph and document controls wired.** The due-diligence workspace calls the tenant-filtered graph with clickable record trails; document rows provide open, download, and trace controls. Previous finding: `GET /compliance/kyc/corporate-graph/{merchantId}` had no caller;
  `GET /documents/{id}/download` exists but no download button in the UI (only "Open"/inline). Wire or remove.

**CLEAN:** schema (all backed), upload/list/preview/manual-verify flow, PSP-scoped+audited file stream/download,
frontend↔backend contract (no path mismatches).

---

## Wave 28 — W15 re-verified: Kafka/Drools/Neo4j async layer inert (SpEL path works) (2026-07-16) 🔎

Evidence-based re-audit of the W15 dead-wire claim — **confirmed with one key correction**. ✅ **The live AML rule
evaluation is NOT dead:** transaction scoring runs **synchronously via SpEL** — `AmlCheckController.scoreTransaction`
(`:153`) → `ScoringService` → `RulesExecutionService.evaluateTransaction` (`:63`) → `SpelRuleExecutor`, using the
enabled SPEL rules seeded in V142/V143. So `/aml-check` scoring genuinely works. What's inert is the **async
Kafka/Drools/Neo4j layer** and the ingestion→alert→case flow. None of these are boot-fatal (Kafka off in `testenv`,
Neo4j off by default); they are architectural/data gaps to decide on and implement.

- ✅ **PRODUCER-SIDE RELIABILITY IMPLEMENTED BY YOU (verified 2026-07-16):** transactional **Outbox pattern** —
  `entity/integration/OutboxEvent` (`event_outbox`, backed by `V188__durable_event_outbox.sql`, status/retry/
  `next_attempt_at`/`published_at` + ready-index) + `service/kafka/KafkaOutboxDispatcher` (`@Scheduled(fixedDelay 1s)`,
  drains ready rows → `kafkaTemplate.send().get(timeout)`, gated on `kafka.enabled`). `DecisionEngine:438` writes the
  alert + its event atomically to the outbox. Well-wired — fixes the fire-and-forget publish reliability. ⚠️ Still verify
  the **consumer side** (below): the outbox makes publishing reliable but doesn't itself add a consumer for
  `transactions.enriched` or reconcile the `alerts.generated` vs `aml.transaction.alerts` topic-name mismatch — confirm
  the dispatched events land on topics that actually have consumers.
- [ ] **W28-1 (HIGH) — Kafka enrichment pipeline dead-ends at `transactions.enriched`.** ingest →
  `TransactionIngestionService.publishRawTransactionEvent:213` → topic `transactions.raw` →
  `FeatureEngineService.onRawTransaction:67` (updates Redis velocity) → re-publishes `transactions.enriched:146` →
  **NO CONSUMER**. The velocity-enriched event is written to Kafka and discarded — never feeds rules/scoring.
  **Fix:** add a consumer on `transactions.enriched` that runs scoring/DecisionEngine, OR remove the pipeline if
  synchronous SpEL scoring is the intended design (decide first).
  - **DE-RISK NOTE (2026-07-16, bug-hunt): the dead enriched pipeline does NOT break velocity/structuring detection.**
    The live `/aml-check` scoring path's `FeatureExtractionService:154-164` recomputes velocity features **from the DB**
    (`transactionRepository.countByMerchantInTimeWindow` / `sumAmountByMerchantInTimeWindow` + PAN velocity), NOT from the
    Redis counters `FeatureEngineService` writes off the dead `transactions.enriched` event. So velocity/structuring
    rules DO get real data even with Kafka off. The Redis counters + enriched event are redundant with the DB path —
    lowers W28-1 urgency (it's cleanup/perf, not an AML-detection gap). (The DB velocity queries do run per-sce; watch
    their cost at volume — indexed time-window counts.)
- [ ] **W28-2 (HIGH) — alerts Kafka pipeline dead on BOTH ends (topic-name mismatch).** `DecisionEngine.publishAlertGeneratedEvent:454`
  produces to `alerts.generated` (**no consumer**); `TransactionAlertConsumer.consumeAlert:32` listens on
  `aml.transaction.alerts` (**no producer**). So Kafka-driven case creation from alerts never happens — the two halves
  use different topic names. **Fix:** align the topic name (produce+consume the same topic) so alert→case works, OR
  drop it if cases are created synchronously.
- [ ] **W28-3 (HIGH) — Drools loads but `fireAllRules()` never runs.** `DroolsRulesService.java:200` fires only via
  `evaluate():132` ← `RulesExecutionService.java:93`, gated `if ("DROOLS_DRL".equals(rule.getRuleType()))`. **No
  `DROOLS_DRL` rule is ever seeded** — every V142/V143 rule row is `SPEL`. So the 10 regulatory rules in
  `resources/rules/aml-rules.drl` (CTR/SAR/OFAC/velocity) are compiled at boot and sit **inert**. **Fix:** either seed
  a `DROOLS_DRL` rule row (activating the DRL) or intentionally retire the DRL if SpEL covers those rules. NOT
  config-disabled — purely a missing seed row. (Easy Rules is not on the live path at all.)
- [ ] **W28-4 (MEDIUM) — Neo4j graph is never written.** `Neo4jGraphIngestionService` (writes `:62/:84/:98/:112`) has
  **zero callers** AND is `@ConditionalOnProperty(neo4j.enabled=false by default)`. So network-analysis graph inputs
  (pageRank/betweenness/communityId the DRL references) have no source. **Fix:** call the ingestion service from the
  transaction path + enable Neo4j where network analytics are needed, OR mark the feature explicitly disabled.
  - **DE-RISK NOTE (2026-07-16):** the dead Neo4j does NOT break any user-facing page. `controller/network/` is empty,
    and the Cases network-graph UI (`FRONTEND/.../CasesNetworkGraph.tsx` → `CaseNetworkController` →
    `CaseNetworkService.buildNetworkGraph`) is built entirely from **relational** JPA repos (ComplianceCase/CaseTransaction/
    Transaction/SAR/User), not Neo4j. So the network visualization "works as intended" today. Neo4j being inert only
    affects the (also-inert, W28-3) Drools graph-analytics features — nothing currently rendered. Lowers real urgency.
- [ ] **W28-5 (LOW) — orphaned topics** confirm scaffolding left unfinished: `features.updates` (no producer/consumer),
  `transactions.audit` + `aml.compliance.alert` (one side only). Wire or remove.
- [ ] **W28-6 (NOTE) — primary ingestion controller does not trigger rule eval inline.**
  `TransactionController.ingestTransaction:124` → `TransactionIngestionService` computes a trivial TRS-based
  `riskLevel`/`decision` inline (`:168-171`) and fire-and-forget publishes `transactions.raw` — it does NOT call
  scoring/DecisionEngine. Full rule evaluation lives only behind the separate `/aml-check` entry point. Confirm this
  split is intended (ingestion = lightweight, /aml-check = full scoring) or wire ingestion to scoring.

---

## Wave 27 — Platform ADMIN "manage PSPs" console (2026-07-16) 🔎

Audited the admin operator managing OTHER PSPs (the "management to manage the Psps + cost calculations" goal).
**PSP lifecycle + subscription-tier billing are CLEAN and admin-reachable** (see bottom). Gaps are in the billing
*operations* layer + one confirmed path-mismatch blocker.

- [x] **W27-1 (BLOCKER, confirms+locates+resolves W20-7) — 7 PSP org-config tabs 404 via `/cbk/` path mismatch.** ✅ FIXED.
  Reality was worse than the earlier note: **both reads AND writes** were mismatched. `PspConfigPage.tsx:48` GET was
  `psps/{pspId}/{entity}`, and `PspListCrud.tsx` built writes as `psps/{entity}/{pspId}` (POST) / `.../{id}` (DELETE) —
  neither matched the backend `/psps/{pspId}/cbk/{entity}` (`PspDirectorController.java:24` + 6 CBK siblings, all
  `GET/POST` on collection, `DELETE /{id}`). So all 7 tabs' list/add/delete failed; worse, `PspListCrud` used raw
  `fetch` with no `res.ok` check → 404s reported "Added successfully" (silent fake success, the W20-7a issue).
  **Fix applied (9 files, frontend tsc clean):** centralized the URL in `PspListCrud` to `/api/v1/psps/{pspId}/cbk/{apiPath}`
  (+ `/{id}` for delete) and added `if (!res.ok) throw` to both add & delete; changed all 7 tabs' `apiPath` from
  `"psps/{entity}"` to the bare `"{entity}"`; fixed the `PspConfigPage` read to `psps/{pspId}/cbk/{entity}`. See
  `docs/CHANGES-BY-CLAUDE.md`.
- [ ] **W27-2 (HIGH) — manual invoice generation is UI-unreachable.** Both `POST /billing/invoices/generate`
  (`BillingController.java:86`) and `POST /admin/psp-billing/{pspId}/invoice/generate`
  (`PspAdminBillingController.java:191`) exist but have **zero** frontend callers (grep `invoice/generate` in FRONTEND
  = 0). Invoices are only ever created by the scheduled job → an admin cannot issue an off-cycle invoice. **Fix:** add
  a "Generate Invoice" button on BillingPage wired to a mutation on `/billing/invoices/generate`.
- [ ] **W27-3 (HIGH) — billing notifications / dunning reminders unreachable.** `POST /admin/psp-billing/{pspId}/notify`
  (`PspAdminBillingController.java:230`) has zero callers and no equivalent → admins cannot send invoice/overdue
  reminders from the UI. **Fix:** surface a "Notify"/"Send reminder" action.
- [ ] **W27-4 (HIGH) — no per-PSP rate / pricing override.** `BillingRate` entity + `GET /billing/rates` exist but
  there is **no** create/update endpoint for `BillingRate` or `PricingTier` (both GET-only) and no UI → the only
  pricing levers are tier assignment + `discountPercentage` on the subscription. If PSP-specific pricing is a SaaS
  requirement, add rate CRUD (backend + admin UI). Decide scope.
- [ ] **W27-5 (MEDIUM) — entire `PspAdminController` (`/admin/psp/*`) is dead code.** create/activate/suspend/
  terminate/theme/delete all have zero callers; the FE uses `PspController` (`/psps`) instead. It also requires an
  `X-User-Role` header + `MANAGE_PSP` perm the FE never sends → even if called it would fail. **Fix:** delete the
  controller OR migrate the FE to it and drop the duplication (one source of truth for PSP lifecycle).
- [ ] **W27-6 (MEDIUM) — `/admin/psp-billing/summary` consolidated roster unused.** BillingPage rebuilds a partial
  cross-PSP view from `/subscriptions` + `/billing/invoices` instead of the single-call KPI summary. Wire it or remove it.
- [ ] **W27-7 (MEDIUM) — no admin PSP-user provisioning / access reset UI.** `POST /psps/users` (`PspController:118`)
  exists but has no button; `PspConfigPage` has no Users tab and there's no password-reset endpoint surfaced. Admins
  cannot create/manage a PSP's login users from the console.
- [ ] **W27-8 (LOW) — register form sends `billingCycle` that the DTO drops.** PspsListPage register posts
  `billingCycle` but `PspRegistrationRequest` has no such field → silently ignored (non-fatal; either add the field or
  drop it from the form + set cycle at subscription time).

**CLEAN (admin, end-to-end):** onboard PSP (`POST /psps`), list (`GET /psps`), activate/suspend/terminate
(`PUT /psps/{id}/status`), delete (`DELETE /psps/{id}`), edit org (`PUT /psps/{id}`), CBK config (GET/PUT
`/psps/{id}/cbk-config`, PUT ADMIN-only), subscription tier assign/change/cancel (`/subscriptions` CRUD, tiers from
`/pricing/tiers`), per-PSP usage/cost/invoices/revenue viewing + invoice status update + PDF. All ADMIN/SUPER_ADMIN gated.

---

## Wave 26 — PSP self-service PROFILE: tenant cannot see "their profile in full" (2026-07-16) 🔎

End-to-end audit of the goal "psps logging in and seeing their profiles here in full." Auth is **session-based**
(Spring Security session cookie, NOT JWT); `/auth/me` (`AuthenticationController.java:225`) correctly returns
`pspId`+`psp{code,name}`+theme, and the principal `User.getPsp().getPspId()` is genuinely populated (used in
BillingController/SubscriptionController). Personal profile (name/email/password) and self-service Billing work.
**But the ORG profile is unreachable for a PSP tenant**, and the active shell has dead user menus.

- [x] **W26-1 / W20-16 (BLOCKER) — FIXED 2026-07-16.** The active header/sidebar expose wired Profile and Logout actions. Previous finding: no Profile/Logout menu in the active shell (`HokekaSidebar.tsx`
  right now — Claude is NOT touching it). `HokekaHeader.tsx:73-81` (avatar) and `HokekaSidebar.tsx:557` (footer user
  button) render a `ChevronDown` with **no onClick/menu**; no Profile nav item and **no way to log out**.
  **GOOD NEWS — the infra already exists, it's purely unwired in the active shell:**
  - Backend logout: `POST /api/v1/auth/logout` (`AuthenticationController.java:304`) — session invalidation + audit log.
  - Frontend: `contexts/AuthContext.tsx:194` `logout()` already calls that endpoint and clears auth; exposed via
    `useAuth()` (`:244`). The dead `components/Layout/Header.tsx` already uses it — the **active** `HokekaHeader`/
    `HokekaSidebar` just never import `useAuth`/`logout` (confirmed: neither references it).
  - `ProfilePage` is fully wired (`/auth/me`, `PUT /users/me`, `PUT /users/me/password`), reachable only by URL.
  **Fix (whenever you're done in the file):** add a dropdown to the header avatar / sidebar footer button with
  "My Profile" → `navigate('/profile')` and "Log out" → `useAuth().logout()`. No backend work needed.
- [x] **W26-2 / W26-3 ✅ IMPLEMENTED BY YOU (verified well-wired 2026-07-16)** — `PspConfigPage.tsx` now has
  self-service mode (`selfService = !pspId` → `useMyPsp`), and the backing `GET /psps/me` (`PspController.java:76`)
  exists and returns the caller's own PSP; `useMyPsp` (`queries.ts:881`) calls `psps/me`. Frontend↔backend match
  confirmed; duplicate `@GetMapping("")` also removed. A PSP can now load their own org profile without knowing an id.
  (Still open from Wave 26: W26-4 IDOR on `/psps/{id}`, W26-5 nav gating, W26-6 self-branding, W26-7 API-keys/KYC,
  W26-8 webhooks, and the W26-1 header/sidebar Profile+Logout menu.)
  _(W26-2 "no reachable org-profile route" and W26-3 "no /psps/me self endpoint" — both resolved by the implementation
  above: self-service `PspConfigPage` + `GET /psps/me`. Still verify a NAV LINK/route to the self-service page exists
  for PSP users, and that PSP-config child writes are PSP-scoped, not just the read.)
- [x] **W26-4 (HIGH, security IDOR) — FIXED 2026-07-16.** PSP profile, update, CBK config, and user creation reject cross-tenant IDs. Previous finding: `/psps/{id}` had no ownership check. `GET /psps/{id}` (`PspController.java:66`),
  `PUT /psps/{id}` (`:110`), `GET /psps/{id}/cbk-config` (`:139`) allow PSP_ADMIN with **no check that {id} == caller's
  pspId** → any PSP_ADMIN can read/modify ANY other PSP's org + CBK OAuth config by id. (Same class as W20-4.)
  **Fix:** enforce `id == currentUser.getPsp().getPspId()` for PSP_ADMIN (admins exempt).
- [x] **W26-5 (MEDIUM) — FIXED 2026-07-16.** Active navigation is tenant-aware and exposes My Organization instead of platform administration routes. Previous finding: active `HokekaSidebar` showed admin-only nav to PSP users (PSPs/Users/Merchants/Settings;
  `HokekaSidebar.tsx:159-261` renders one static list, no role/pspId gating) → PSP users click into dead-end 403s.
  **Fix:** filter nav by role/`isPspUser`.
- [x] **W26-6 (MEDIUM) — FIXED 2026-07-16.** PSP_ADMIN can persist and immediately apply its own logo, colors, typography, and radius. ✅ _Claude verified end-to-end: `BrandingTab.tsx` GET/PUT `settings/psps/{id}/theme` ↔ `SettingsController.updatePspTheme:274` — `@PreAuthorize` allows `PSP_ADMIN`, `canAccessPsp` enforces ownership (403 cross-PSP), persists to `psps.branding_theme` (the V170 column). `PspConfigPage:116` passes `effectivePspId` (self-service works)._ Previous finding: PSP could not self-edit branding/theme. Theme tab gated `!isPspUser`
  (`SettingsPage.tsx:236,241`) → admin-only, yet the theme is applied to the PSP via `/auth/me`. Give PSP_ADMIN a
  self-branding editor (pairs with `psps.branding_theme` added in V170).
- [ ] **W26-7 (MEDIUM) — no PSP self-view of KYC/onboarding/compliance status** (admin-set only) and **no API
  key/credential management** (the `Psp` entity has no apiKey/secret at all — only per-PSP CBK OAuth client managed by
  admin). If PSPs are meant to call the AML API programmatically as a SaaS, an API-key issue/rotate facet is missing
  entirely. Decide whether SaaS API access needs per-PSP keys.
- [ ] **W26-8 (LOW) — no notification/webhook settings** on `Psp` (no fields/endpoints) — PSPs can't configure
  callbacks for AML results.

**CLEAN for PSP (works end-to-end):** personal profile (name/email/password) IF URL reached; self-service Billing
(Settings→Billing) fully nav-reachable and ownership-scoped; CompanyTab org edit correctly targets `PUT /psps/{id}`.

---

## Wave 25 — PSP self-service billing UI: cost/plan don't reach the tenant (2026-07-16) 🔎

End-to-end audit of the PSP billing/cost experience after the backend billing-$0 fix. **Backend + admin UI + tenant
isolation are CLEAN and fully wired** (admin Revenue dashboard, Subscriptions CRUD, Invoices+Mark-Paid+PDF, PSP
invoice-history + Pay via M-Pesa STK/bank + bank-details all work; every PSP-scoped endpoint enforces
`currentUser.getPsp()` server-side, `X-PSP-ID` header ignored → no cross-tenant leak). BUT the PSP's own
self-service `FRONTEND/src/pages/Psps/tabs/BillingTab.tsx` reads DTO fields that don't exist → the recent billing
fix's cost numbers never render for the PSP. **These two are the final mile of the billing-$0 blocker — CLAUDE will
fix on next cadence pass (files confirmed settled in git).**

- [x] **W25-1 (BLOCKER, frontend) — PSP "Current Plan" card crashes: nested `tier` object the API never returns.** ✅ FIXED — `BillingTab.tsx` `Subscription` interface flattened + all render sites remapped to `tierName`/`monthlyFeeUsd`/`billingCurrency`/`contractStart`/`contractEnd`/`trialEndsAt`; added `includedChecks` to `SubscriptionResponse` (from eager `PricingTier.getIncludedChecks()`). Frontend tsc clean. See `docs/CHANGES-BY-CLAUDE.md`.
  `SubscriptionResponse` is FLAT (`dto/billing/SubscriptionResponse.java:18-27`): `tierCode, tierName,
  monthlyFeeUsd, billingCurrency, contractStart, contractEnd, status, trialEndsAt`. `BillingTab.tsx` reads a nested
  `subscription.tier.*` + wrong date names → `TypeError` on render whenever the PSP HAS a subscription. Exact fixes:
  - `:632,:635` `subscription.tier.name` → `subscription.tierName`
  - `:691` `subscription.tier.monthlyFee` → `subscription.monthlyFeeUsd`
  - `:692,:741` `subscription.tier.currency` → `subscription.billingCurrency`
  - `:660,:670` `subscription.trialEndDate` → `subscription.trialEndsAt`
  - `:762` `subscription.startDate` → `subscription.contractStart`; `:765,:779` `subscription.endDate` → `subscription.contractEnd`
  - `:725` `subscription.tier.includedChecks` → **NO flat field exists.** DECISION: add `includedRequests` to
    `SubscriptionResponse` (sourced from the tier/BillingRate) OR look it up via `/pricing/tiers` by `tierCode` OR
    drop the line. (Claude will add `includedRequests` to the DTO — no-stub, keeps the card complete.)
  - Also fix the TS interface at `BillingTab.tsx:46-62` (nested `tier`) to the flat shape.
- [x] **W25-2 (BLOCKER, frontend) — PSP "Current Month Usage" cost renders `$NaN`: wrong usage field names.** ✅ FIXED — `BillingTab.tsx` `CurrentUsage`/`UsageLineItem` interfaces + render sites remapped to `totalCostUsd`/`period`/`count`/`costUsd`. Frontend tsc clean. See `docs/CHANGES-BY-CLAUDE.md`.
  `UsageSummaryResponse` (`dto/billing/UsageSummaryResponse.java:11-16,54-56`): `period, totalRequests,
  billableRequests, totalCostUsd, breakdown[{serviceType, count, costUsd}]`. `BillingTab.tsx` reads non-existent
  `estimatedCost/currency/periodStart/periodEnd/cost/requestCount`. Exact fixes:
  - `:850` `usage.estimatedCost, usage.currency` → `usage.totalCostUsd, "USD"` (no currency field; costs are USD)
  - `:835-836` `usage.periodStart`/`usage.periodEnd` → single `usage.period` string
  - `:889` `line.requestCount` → `line.count`; `:892` `line.cost` → `line.costUsd`
  - Fix the corresponding TS types too. (`totalRequests`/`billableRequests` already match — only money/period broken.)
- [ ] **W25-3 (MEDIUM) — no "Generate Invoice" action** anywhere: `POST /billing/invoices/generate` and
  `POST /admin/psp-billing/{pspId}/invoice/generate` have zero callers; invoices only created by the scheduled job.
- [ ] **W25-4 (MEDIUM) — admin invoice pagination is cosmetic:** `useInvoices` sends `page/size` + types `PageResponse`
  (`queries.ts:728`) but `/billing/invoices` returns an unpaged `List` (`BillingController.java:98`) → all invoices on page 0.
- [ ] **W25-5 (LOW) — entire `PspAdminBillingController` (`/admin/psp-billing/*`) is dead** (summary, per-PSP usage/
  invoices, invoice-generate, **notify** emails) — zero frontend callers; admin UI uses `/billing/*` + `/subscriptions`.
- [ ] **W25-6 (LOW) — rate card hidden:** `GET /billing/rates` never called → PSPs can't see per-service pricing.
- [ ] **W25-7 (LOW) — payment-attempt history never surfaced:** `GET /billing/payments/{invoiceId}` uncalled; no
  pending/failed STK visibility (PSP only sees status flip on refetch).
- [ ] **W25-8 (LOW) — currency inconsistency USD vs KES:** admin usage costs force-format `"USD"`
  (`BillingPage.tsx:992,1021`), `RevenueSummaryResponse` hardcodes USD (`BillingController.java:393`), but payments
  default KES via M-Pesa (`PaymentController.java:135`) and invoices show `inv.currency` → invoice currency may not
  match the USD-labeled usage cost that produced it.

---

## Wave 24 — Market/governance report-generation subsystem (V166 + ReportGenerationService) (2026-07-16) 🔎

**Resolved 2026-07-16:** W24-1 through W24-7 are implemented. Tenant reports now require an explicit bound `:pspId`; native-query aliases come from Hibernate metadata; the queue row is durable before async work starts; dates are typed; SAR user joins are corrected by V179; count failures are surfaced; and XLSX is a real OpenXML workbook. Scheduled execution, SMTP attachment delivery, and dispatch/delivery history were also wired through V180.
> ✅ **Claude independently verified (2026-07-16):** confirmed in code — `applyPspIsolation` now *requires* a bound `:pspId` named param (SecurityException otherwise) & binds it (no more `replaceAll("(?i)where")` injection or ambiguity); `nativeQuery.setTupleTransformer((tuple, aliases)->…)` replaces the SELECT-string parser; `normalizeParameter` coerces `dateFrom`/`dateTo`→`LocalDateTime` (dateTo→end-of-day); failure path persists a FAILED row with report+triggerType set (no more NOT-NULL crash). One tiny residual: the *pre-first-save* failure branch (`existing.isEmpty()`, `:259-268`) returns a FAILED DTO without persisting a DB row — a status-poll hitting the DB could still see none; negligible if the execution row is saved up-front. Otherwise fully well-wired.

Audited V166 seeds + `service/reporting/ReportGenerationService.java` + `ReportRunTraceService.java` + report
controllers. **No boot-fatal blockers** (V166 creates no tables; `ReportExecution` fully backed by V108+V157,
not @Audited). Generation chain IS wired end-to-end (POST `/reports/generate` → async `generateReport` →
`executeReportQuery` → inline trace → persist `ReportExecution` → export). Tracing IS invoked
(`RegulatoryReportingController:202`, `SchemeReportingController:69`). But several **runtime** bugs will make the
three V166 reports (MKT_001/MKT_002/RGOV_001) and SAR_001 fail or return wrong data at RUN time:

- [x] **W24-1 (HIGH, runtime) — ambiguous `psp_id` injection breaks all PSP-scoped report runs.**
  `ReportGenerationService.applyPspIsolation:513-531` does `sql.replaceAll("(?i)where", "WHERE psp_id = <id> AND ")`
  with an **unqualified** `psp_id`. All V166 reports are multi-table joins where 2+ joined tables own `psp_id`
  (MKT_001: `market_surveillance_signals`+`multi_asset_customers`; MKT_002: `market_orders`+`multi_asset_customers`;
  RGOV_001: `rule_versions`+`rule_definitions`) → Postgres `column reference "psp_id" is ambiguous` at run time.
  Platform admins unaffected. **Fix:** qualify with the driving table alias (e.g. per-report configured alias), or
  wrap the report SQL as a subquery and filter the outer `psp_id`. Also switch to a **bound** `:pspId` param
  (currently string-concatenated, `:520`).
- [x] **W24-2 (HIGH, runtime) — `extractColumnNames:544-572` mis-parses SELECT lists → wrong/dropped result keys.**
  Naive `cols.split(",")` splits inside function calls (`COALESCE(SUM(e.quantity), 0)` MKT_002 V166:54; `COALESCE(MAX(s.score),0)`
  :56; `COALESCE(sar.filed_at, NOW())` SAR_001 :120) and `indexOf("from")` grabs the `FROM` inside
  `EXTRACT(EPOCH FROM (...))` (:120), truncating the list. Rows are `Object[]` mapped positionally → wrong column
  keys, dropped columns. Code self-flags it as a stub ("in production use ResultSet metadata", :545). **Fix:** use
  `ResultSetMetaData`/JDBC column labels instead of string-parsing SQL.
- [x] **W24-3 (HIGH, runtime) — early failures never persist a FAILED row → client polls PENDING forever.**
  `generateReport` catch `:194-209`: if the exception is thrown before the first `save()` at `:139` (invalid
  `reportType` :104, or "no active definition" :108-110), the `orElseGet` fallback `:196-203` builds a
  `ReportExecution` with **no `report` and no `triggerType`**; `report_id`/`trigger_type` are `NOT NULL` (V108:70,74)
  → the failure-recording save itself throws, nothing is written, and `GET /reports/status/{id}` returns PENDING
  indefinitely (`:273-280`). **Fix:** set report+triggerType (or a sentinel) on the fallback, or persist a FAILED
  stub row up front before validation.
- [x] **W24-4 (MEDIUM, runtime) — date params bound as String against TIMESTAMP.** `executeDynamicQuery:459-468`
  binds the raw request map; `dateFrom`/`dateTo` arrive as ISO Strings but V166 queries use `created_at BETWEEN
  :dateFrom AND :dateTo` → likely PG `operator does not exist: timestamp >= character varying`. Coerce to
  `LocalDateTime` before binding (controller currently only parses them into `execution.setDateFrom`, not the param map).
- [x] **W24-5 (LOW) — SAR_001 joins wrong user table.** V166:122-123 joins `platform_users` on
  `sar.created_by_user_id`/`reviewed_by_user_id`, but those FKs reference `psp_users(user_id)`
  (`V4__compliance_sar_audit.sql:60-61`). LEFT JOIN so no error, but usernames resolve NULL. MKT/RGOV reports join
  `platform_users` correctly.
- [x] **W24-6 (LOW) — count-query failures swallowed to `0L`.** `executeCountQuery:504-507` returns 0 on any
  exception (incl. W24-1 ambiguity) → preview silently reports `totalCount=0`/`hasMore=false` even when rows exist.
- [x] **W24-7 (LOW) — "Excel"/"XLSX" export is really CSV.** `exportReport:596,608` routes EXCEL/XLSX to `exportToCSV`
  and writes a `.csv` file while recording the requested format — no true xlsx output.

---

## Wave 23 — merchants Kenyan fields + virtual-asset (V171–V174) re-audit (2026-07-16) 🔎

Column-level missing-migration sweep + re-audit of the newest in-flight virtual-asset build-out.

- [x] **W23-1 (BLOCKER) — `merchants` missing `kra_pin` + `cr12_number`** (Phase 29 Kenyan fields).
  `Merchant.java:46-47,49-50` map `@Column(name="kra_pin", length=50)` / `@Column(name="cr12_number", length=100)`
  as real persistent columns (constructor `:165`, getters/setters `:261-273`, builder `:527-528,591-597`), but no
  migration ever created them (table born in `V2`). Under `ddl-auto=validate` → boot-fatal
  `missing column [kra_pin] in table [merchants]`. ✅ FIXED — `V185__merchants_kenyan_fields.sql` (idempotent
  `ADD COLUMN IF NOT EXISTS`; nullable; not @Audited so no `_aud` cols). Placed at V185 (gapped above your
  fast-moving V171–V178 frontier) after your concurrent migrations twice took V175 then V178. See
  `docs/CHANGES-BY-CLAUDE.md`.

**Virtual-asset migrations V171–V174 — audited CLEAN (no blockers).** All 8 tables in `V171` have fully-matching
entities under `entity/crypto/*` (VaspDirectoryEntry, CryptoWalletProfile, WalletScreeningRecord,
TravelRuleJurisdictionPolicy, TravelRuleTransfer, TravelRuleTransmissionAttempt, VirtualAssetRegulatorAccessGrant/Log),
all 8 repositories under `repository/crypto/*`, service `service/crypto/VirtualAssetComplianceService.java`, and
controllers `VirtualAssetComplianceController` (`/virtual-assets`) + `VirtualAssetRegulatorAccessController`
(`/regulator/virtual-assets`). Code and migrations in lockstep; FK targets all pre-exist; `@Immutable` append-only
entities correctly match the DB mutation-prevention triggers; V173's 6 ALTER columns exactly match
`TravelRuleTransfer.java:28-33`. No @Audited/Envers → no `_aud` tables needed. Only minor FK-index nits below.

- [x] **W23-2 (LOW, perf) — missing FK indexes on virtual-asset join columns** (Postgres doesn't auto-index FKs;
  report queries seq-scan). All in `V171__virtual_asset_compliance.sql`:
  `crypto_wallet_profiles.customer_id` (:34, joined VA_001 `V172:21`), `.vasp_id` (:35, LEFT JOIN VA_003 `V172:67`),
  `.asset_account_id` (:33, only non-leftmost of `uq_crypto_wallet_account`);
  `travel_rule_transfers.policy_id`/`originator_vasp_id`/`beneficiary_vasp_id` (:109-111, joined VA_002 `V172:45-47`);
  `virtual_asset_regulator_access_logs.grant_id` (:164, joined VA_004 `V172:85`). Add btree indexes on each in a new
  migration when the reports go hot. (Well-covered FKs already indexed — no action there.)

---

## Wave 22 — aml-microservice internal wiring (2026-07-15) 🔎

The standalone `com.hokeka` microservice is genuinely wired (real Aerospike reads/writes, real
Jaccard+Levenshtein sanctions matching, config-driven thresholds/lists, graceful degradation when Aerospike
is down, contract matches BACKEND's `SanctionsScreenClient` exactly). One boot blocker (fixed) + minor notes.

- [x] **W22-1 (BLOCKER) — microservice wouldn't boot: duplicate top-level `aml:` key** in
  `aml-microservice/.../application.yml` (Spring `allowDuplicateKeys=false` → context load fails; also dropped
  `aml.internal-auth-key`, disabling `InternalAuthFilter`). ✅ FIXED — merged into one `aml:` block; verified
  parses with auth key intact. See `docs/CHANGES-BY-CLAUDE.md`.
- [ ] **W22-2 (MEDIUM)** — Aerospike namespace `aml_cache` + set names (`sanctions`/`risk_profile`/`velocity`/
  `device`/`ip_reputation`) are hardcoded constants (`SanctionsService.java:54-55`,
  `AerospikeCacheService.java:34-40`), not config-driven — no per-env override. Externalize via `@Value`.
- [ ] **W22-3 (LOW)** — `AerospikeCacheService.recordVelocity:104` does `Operation.add(new Bin("total_ms", 0L))`
  (always adds 0) → the `total_ms` counter is meaningless. Pass the real elapsed ms.
- [ ] **W22-4 (LOW)** — `AmlCheckService.check:63` synthesizes `TXN-<timestamp>` when `transactionId` is null →
  unique cache key every call, so the Aerospike score cache never hits for those. Use a stable key or skip caching.
- [ ] **W22-5 (LOW)** — `AerospikeConfig.aerospikeClient()` returns `null` on connect failure → a Spring `NullBean`
  (works with the `@Autowired(required=false)` guards, but fragile). Consider an explicit "disabled" client wrapper.
  Also `FLAGGED_THRESHOLD=0.95` (`SanctionsService.java:61`) is a hardcoded constant (documented in the DTO contract).

---

## Wave 21 — Newest In-Flight Features: Crypto/VASP + Mobile-Money (2026-07-15) 🔎

Audited the two newest feature builds. Mobile-money is done & clean; crypto/VASP is mid-construction
(data layer complete, service/controller layer not yet written — these are build-out items, not bugs).

### ✅ Mobile-money — COMPLETE & fully wired (no blocker/high)
Schema↔V167/V168 parity exact; `MobileMoneyController`→`MobileMoneyService`/`MobileMoneyRiskEngine`→repos→
entities all resolve; risk engine is real (8 scenario groups → ~16 typed signals: shared-device/SIM, structuring,
rapid cash-in/out, SIM-swap, impossible geo-velocity, agent float anomaly, etc.), wired into the AML pipeline
(combines with multi-asset score, sets `RiskDecision`, persists signals, raises `MOBILE_MONEY_NETWORK` alert
≥20); frontend `MobileMoneyPage` routed + sidebar + hooks hit real endpoints; no stubs. Polish only:
- [ ] **W21-1 (LOW)** — `MobileMoneyService.java:90` dead assignment (`edges` assigned, never read — `response()`
  re-queries). Remove.
- [ ] **W21-2 (LOW)** — `mobilemoney.*` risk thresholds use inline `@Value` defaults but aren't declared in any
  `application-*.properties` — add them for prod tunability.
- [ ] **W21-3 (LOW)** — curated record-trail cross-links exist only for `MOBILE_MONEY_RISK_PROFILE`
  (`RecordTrailService.java:266`); `TRANSACTION_CONTEXT`/`NETWORK_EDGE` deep-links work via the generic
  fallback only. Add curated links if desired.

### 🟠 Crypto/VASP/Travel-Rule — MID-CONSTRUCTION (data layer done; finish the build)
V171 schema↔entity parity is CLEAN (8 tables, JSONB, enum widths, `@Immutable` append-only + DB triggers all
match — `validate` will pass). Data layer complete (8 entities, 8 repos, `BlockchainAnalyticsClient` +
`TravelRuleGatewayClient`, `VirtualAssetDtos`). Remaining build-out:
- [ ] **W21-4 (HIGH) — no service/controller layer.** `controller/crypto`, `service/crypto` don't exist; all 8
  crypto repos + `VirtualAssetDtos` have zero consumers. Build `VaspDirectoryService`, `CryptoWalletProfileService`
  (+ periodic screening scheduler via `findTop…NextScreeningAt`), `TravelRuleService`, `RegulatorAccessService`,
  and controllers consuming the DTOs.
- [ ] **W21-5 (HIGH) — `TravelRuleGatewayClient` orphaned + unconfigured.** No caller, and its
  `travel-rule.gateway.*` `@Value` keys are absent from `application.properties` (runs on defaults, never invoked)
  → travel-rule messages are never transmitted. Wire it into a transmission flow (create `TravelRuleTransfer` +
  `TravelRuleTransmissionAttempt`) and add the config keys.
- [x] **W21-6 ✅ IMPLEMENTED BY YOU (verified 2026-07-16) — wallet screening now persisted (compliance-grade).**
  `VirtualAssetComplianceService.persistScreening` builds a full `WalletScreeningRecord` (pspId/wallet/customer/txn,
  provider ref, risk score, categories, direct/indirect exposure %, attributions), an evidence map with a **hashed**
  wallet address (privacy-preserving), `retainUntil = now + 7 years`, and `screeningRepository.save(record)`; raises a
  wallet alert on high-risk or unavailable PRE_WITHDRAWAL. Append-only evidence retention satisfied. Well-wired.
- [ ] **W21-7 (MEDIUM — PARTIALLY DONE, verified 2026-07-16) — two travel-rule paths disagree on threshold source.**
  ✅ The dedicated VASP transfer workflow now reads the policy: `VirtualAssetComplianceService:299`
  `policyRepository.findActive(pspId, jurisdiction, executedAt)` → sets `transfer.setPolicy(...)`, uses
  `policy.getRetentionYears()` (min 7), and `evaluateTransfer` applies policy requirements. ⚠️ BUT the multi-asset risk
  path still hardcodes it: `MultiAssetRiskEngine:212,249` gates travel-rule "required?" on the `@Value`
  `multiasset.crypto.travel-rule-threshold-usd:1000` default and never consults `TravelRuleJurisdictionPolicy`. So a
  jurisdiction whose policy threshold ≠ $1000 gets inconsistent decisions between the risk engine and the transfer
  workflow. **Fix:** have `MultiAssetRiskEngine` also look up the active jurisdiction policy threshold (fallback to the
  config default) so both paths agree.
- [ ] **W21-8 (LOW)** — no frontend for VASP directory / wallet screening / travel-rule transfers / regulator
  grants (expected — API not built yet). `BlockchainAnalyticsClient` IS wired into decisioning already.

---

## Wave 20 — SaaS Readiness: PSP login / management / billing / pages (2026-07-15) 🔎

Goal: PSP tenants log in & see their profile in full; platform manages PSPs; AML service costs are
calculated; all pages/links/buttons work. Below: the billing fix I applied, plus remaining gaps to implement.

### ✅ FIXED THIS SESSION — Billing $0 blocker (W16-1) end-to-end

- [x] **Metering vocabulary aligned + rates seeded.** Rewrote `config/UsageTrackingFilter` to emit the
  canonical `billing_rates` vocabulary (`TRANSACTION_MONITORING`, `AML_SCREENING`,
  `SANCTIONS_SCREENING_PERSON`/`_ORGANIZATION`, `KYC_VERIFICATION`, `COMPLIANCE_CASE_CREATION`,
  `RISK_ASSESSMENT`, `REPORT_GENERATION`, `SAR_FILING`, `CBK_REPORTING`), made it **method-aware**
  (only work-performing verbs bill — dashboard/list GETs no longer over-count), added `/aml/detection`
  coverage, dropped self-billing of `/billing/*`, and switched to a deterministic `LinkedHashMap`
  (first-match ordering was previously undefined in a `ConcurrentHashMap`).
- [x] **`V169__seed_remaining_billing_rates.sql`** seeds the 4 canonical types V149 lacked
  (`RISK_ASSESSMENT` 0.15, `REPORT_GENERATION` 0.25, `SAR_FILING` 3.00, `CBK_REPORTING` 1.50), idempotent.
- [x] **Currency no longer hardcoded** — `BillingService.getEffectiveCurrency()` added; `ApiUsageTrackingService`
  now stamps the rate's currency instead of `"USD"`.
- [x] **Invoices are now payable** — `generateMonthlyInvoice` issues `SENT` (it is emailed on creation) instead
  of `DRAFT`, which `PaymentController` rejected. Net: usage → non-zero cost → `billable=true` →
  `getUsageSummaryByService` returns rows → invoice line items = usage×rate → payable. See `docs/CHANGES-BY-CLAUDE.md`.
- [ ] **W20-1 (follow-up, MEDIUM) — retire or wire `MeteringEventPublisher`.** It's dead (no callers) now that
  the filter is the canonical meter. Either delete it or call it from the AML services for point-of-work metering
  (don't double-bill with the filter). Also unify Engine B (`BillingCalculationEngine`/`pricing_tiers`/
  `subscriptions`) with the invoice path, or make `calculateUsageCost` apply the PSP's subscription/tier
  (currently the invoice ignores per-PSP plans).
- [ ] **W20-2 (config, HIGH) — payment loop env.** Set a real `MPESA_CALLBACK_URL` (default is the placeholder
  `https://your-domain.com/...`, so STK callbacks never mark invoices PAID), and reconcile invoice currency vs
  M-Pesa (invoices default `USD`; Daraja collects `KES` as an integer — `MpesaService.java:133`). For the KE
  market, default `psp.currency` to `KES` or convert before STK push.

### 🔴 BLOCKER (also fixed / newly found)

- [x] **W20-3 — `psps.branding_theme` missing migration → boot fails under `validate`.** ✅ FIXED →
  `V170__psps_branding_theme_column.sql` (`ADD COLUMN IF NOT EXISTS branding_theme VARCHAR(50) DEFAULT 'default'`).
  `Psp.java:101` mapped it with no backing migration. See `docs/CHANGES-BY-CLAUDE.md`.
- [ ] **W20-4 — PSP tenant cross-PSP WRITE/READ via `PspController` (IDOR).** `PUT /psps/{id}`
  (`controller/psp/PspController.java:110`) allows `PSP_ADMIN` with **no ownership check** → a PSP_ADMIN can
  overwrite ANY PSP's profile; `GET /psps/{id}` (:65) and `GET /psps/{id}/cbk-config` (:139) let them READ any
  tenant's full record (legal name, encrypted reg#/taxId, CBK creds). `POST /psps/users` (:118) similarly
  unscoped. **Fix:** call `PspIsolationService.validatePspAccess(id)` in each, as the CBK sub-controllers do.

### 🟠 HIGH — PSP self-serve is broken (the core "PSPs log in and see their profile in full" ask)

- [ ] **W20-5 — "My Profile" page is 403 for PSP users.** `ProfilePage` calls `GET/PUT users/me`, but
  `UserController.java:19`'s broken `@PreAuthorize` (W19-1) + `SecurityConfig.java:131` gate `/users/**` to admin
  roles → PSP_ADMIN/PSP_USER get 403 on their own profile. **Fix W19-1** (`hasAnyAuthority`) AND add PSP_USER to
  the `/users/me` access, or serve `/users/me` to any authenticated user (the page already has the user from `/auth/me`).
- [ ] **W20-6 — PSP users can't reach their own PSP profile via nav.** `GET /psps` (`PspController.java:56`) is
  gated `SUPER_ADMIN/ADMIN/COMPLIANCE_OFFICER/INVESTIGATOR` → PSP roles 403; and `PspsListPage` (the only nav
  entry to the PSP config page) is its sole data source. There is **no "my PSP" endpoint/route** for a tenant.
  **Fix:** add `GET /psps/me` (returns the caller's own PSP) + a self-serve profile route, or allow PSP roles to
  read their own PSP.
- [ ] **W20-7 — 7 of 10 PSP-config tabs are dead (path mismatch).** Directors/Shareholders/Trustees/Senior-Mgmt/
  Products/Trust-Accounts/Tariffs tabs fetch `psps/{id}/{directors…}` and write `psps/directors/{id}` (
  `PspConfigPage.tsx:48`, `PspListCrud.tsx:28,47`, `DirectorsTab.tsx:6`), but the backend maps them under
  `psps/{id}/cbk/{…}` (`PspDirectorController.java:24`) → 404 read+write. Correct hooks exist
  (`useCreatePspDirector` → `psps/{id}/cbk/directors`, `mutations.ts:384`) but the tabs don't use them; payload
  also sends `{name}` vs entity `directorNames`. **Fix:** point the tab wrappers at the `cbk` paths + correct payload.
- [ ] **W20-8 — PSP_USER 500s on case views.** `model/UserRole` enum has no `PSP_USER`, so
  `UserRole.valueOf("PSP_USER")` throws in `CasePermissionService:45` → `GET /cases/{id}/timeline|graph|audit/replay`
  return 500 for PSP_USER. **Fix:** add `PSP_USER` to the enum with correct (tenant-scoped) permissions.
- [ ] **W20-9 — cross-tenant read leaks (PSP user sees other PSPs' data).** `GET /transactions/{id}`
  (`TransactionController.java:306`, bare findById); `GET /monitoring/sars` (`TransactionMonitoringService.java:368`
  `findAll()`); `GET /alerts/disposition-stats` (`AlertDispositionService.java:85,117` no pspId);
  `/cases/{id}/activities|sla` (no ownership check); `GET /billing/rates?pspId=` (unchecked param). **Fix:** apply
  `PspIsolationService` scoping to each.
- [ ] **W20-10 — public unauthenticated PSP creation.** `POST /psps/register` (`PspController.java:91`) has no
  `@PreAuthorize` and is `permitAll` (`SecurityConfig.java:145`) → anyone can create a real PSP tenant row. **Fix:**
  gate it or move to a proper vetted onboarding flow.

### 🟡 MEDIUM / 🟢 LOW (SaaS)

- [ ] **W20-11 —** no per-route/nav role gating — `HokekaSidebar`/`App.tsx` show PSPs/Users/Settings to every
  authenticated user, so PSP users are led to pages whose APIs then 403/404. Add role-aware nav + route guards.
- [ ] **W20-12 —** email-login path broken: `CustomUserDetailsService` accepts email, but
  `AuthenticationController.java:154` reloads with `findByUsername` only → email login 401s (username works today).
- [ ] **W20-13 —** `CompanyTab` omits entity fields the DTO already exposes (`status`, `isTestMode`,
  `billingPlan/Cycle`, `currency`, `paymentTerms`) — the PSP can't see their full profile. `registerPsp` also drops
  `billingCycle` sent by the register dialog.
- [ ] **W20-14 —** `loadUserByUsername` `@Cacheable(5-min TTL)` → role/PSP changes lag up to 5 min on the auth path.
### Pages / links / buttons sweep (completed) — most of the app is correctly wired

Route/nav integrity is good: 25 routes all resolve to real components; every `HokekaSidebar` link resolves;
Alerts/Cases/Dashboard/Screening/KYC/Merchants/Users/Settings/TransactionMonitoring/Customer360/RiskAnalytics/
Analytics/AuditLogs/RegulatoryReports/Limits/RulesGeneration/ReportsCenter/Chargebacks/ComplianceCalendar/
Messages all have their primary actions wired to real endpoints, no mock-data pages. Remaining gaps:

- [ ] **W20-7a (HIGH, extends W20-7) — PSP CRUD tabs fake success on failure.** `components/Common/PspListCrud.tsx:28,47`
  never checks `response.ok`, so Add/Delete fire "Added successfully."/"Removed." toasts and call `onRefresh()` even
  on a 404 — a silent no-op masquerading as success (on top of the `/cbk/` path mismatch). **Fix:** guard on
  `response.ok`, surface real errors, and correct the URL shape to `psps/{pspId}/cbk/{entity}[/{id}]`.
- [ ] **W20-16 (HIGH — upgraded — NO LOGOUT + 6 orphan pages).** `components/header/HokekaHeader.tsx:60`
  (notifications bell), `:73` (user avatar), and `components/sidebar/HokekaSidebar.tsx:553` (bottom user button)
  all have **no `onClick`**. Logout only exists in the **dead** `components/Layout/Header.tsx` shell — so after the
  app switched from `Layout` to `HokekaLayout`, **there is now no way to log out anywhere in the UI**, and the
  avatar has no menu. Consequently the built+working pages `/profile`, `/messages`, `/billing`, `/chargebacks`,
  `/compliance-calendar` (and `/regulatory-reports`, whose only link is the never-rendered
  `components/compliance/ComplianceHealth.tsx`) are reachable only by typing the URL. **Fix:** add a user menu
  (Profile / Billing / Messages / **Logout** via `AuthContext.logout`) + a notifications dropdown, and add sidebar
  entries for the orphaned pages.
- [ ] **W20-17 (LOW) — dead frontend files** (unused, remove to avoid confusion): `pages/Reports/ReportsPage.tsx`
  (superseded by `ReportsCenterPage`), `pages/Psps/tabs/CrudTab.tsx`, and the old `components/Layout/Sidebar.tsx`
  + `Layout/Header.tsx` (replaced by `HokekaLayout`/`HokekaSidebar`/`HokekaHeader`). Header search input
  (`HokekaHeader.tsx:40`, "⌘K") is not wired to anything.



Previously-unaudited stable modules. Risk module is cleanly wired; auth has one real authorization bug
plus permission-model consistency gaps.

### 🟠 HIGH

- [ ] **W19-1 — PSP admins locked out of user management (broken `@PreAuthorize`).** ⚠️ RE-CONFIRMED still present +
  SETTLED 2026-07-16 (`UserController.java:19` unchanged after your auth pass — this one got left behind while W26-4/
  W29-1 were fixed). 🔒 **NEEDS YOUR DECISION — Claude will NOT auto-apply** (it's an RBAC allow-list change; the
  permission classifier correctly blocked Claude from widening it unilaterally). **Recommended fix (mirrors the correct
  sibling `RoleController.java:19`):** change line 19 to
  `@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PLATFORM_ADMIN','ROLE_PSP_ADMIN','MANAGE_USERS','MANAGE_ROLES')")`
  — you decide exactly which roles/permissions belong. The per-method `permissionService.hasPermission(...,MANAGE_USERS)`
  + PSP-isolation checks (lines 60,108,131,165,179,304) already provide the fine-grained gate, so this only unblocks the
  class-level door.
  `controller/UserController.java:19` uses `@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGE_USERS','MANAGE_ROLES')")`.
  `hasAnyRole` prepends `ROLE_`, so `MANAGE_USERS`/`MANAGE_ROLES` are tested as `ROLE_MANAGE_USERS`/
  `ROLE_MANAGE_ROLES` — authorities never granted (they exist only as bare permission authorities) — and the
  expression omits `ROLE_PSP_ADMIN`. So only `SUPER_ADMIN`/`ADMIN` reach any `/users` endpoint; `PSP_ADMIN`
  (seeded WITH `MANAGE_USERS` in V127) is blocked at the class gate, making the correct in-method
  `permissionService.hasPermission(...,MANAGE_USERS)` + PSP-isolation checks (lines 60,108,131,165,179,304)
  unreachable. Sibling `RoleController.java:19` does this right with `hasAnyAuthority('ROLE_PSP_ADMIN',…,
  'MANAGE_USERS','MANAGE_ROLES')`. **Fix:** switch `UserController` to `hasAnyAuthority(...)` with the same
  authority set as `RoleController`.

### 🟡 MEDIUM

- [ ] **W19-2 — dead authorization branches: roles referenced but never seeded.** `@PreAuthorize` expressions
  reference roles that exist in no seed (V127/V128) or `RoleService`: `SCREENING_ANALYST`
  (`MerchantController.java:37,133,215`), `PSP_ANALYST` (`MerchantController.java:133,215`,
  `TransactionController.java:323`), `APP_CONTROLLER` (`PspReportingConfigController.java:26,36`,
  `SchemeReportingController.java:54`). No user can hold them → those access paths are unreachable. Seed the
  roles or remove the branches.
- [ ] **W19-3 — risk weights hardcoded, not config-driven.** `service/risk/RiskScoringService.java:48-67`
  keeps CRA/KRS/TRS weights + volume/alert tier cutoffs as `static final` constants (only MCC risk is
  externalized via `MccRiskConfig`). Tuning the risk model requires a redeploy. Consider
  `@ConfigurationProperties`/DB-backed weights (comments already acknowledge this as interim).

### 🟢 LOW

- [ ] **W19-4 —** `UserSkillController.java:48,150,164` gate on `hasAuthority('MANAGE_SKILLS')`/`'CERTIFY_SKILLS'`
  but neither is in the `Permission` enum (`model/Permission.java`) → never grantable; only the SUPER_ADMIN/ADMIN
  fallback works. Add the permissions or drop the checks.
- [ ] **W19-5 —** dual role-seed sources: `RoleService.initDefaultRoles()` (`ADMIN`/`INVESTIGATOR`/`ANALYST`/
  `VIEWER`, runtime `@PostConstruct`) vs V127/V128 (`PLATFORM_ADMIN`/`PSP_USER`/`MLRO`/`CASE_MANAGER`/`AUDITOR`).
  Both idempotent, but `ADMIN` (all-powerful in `PermissionService`) exists only if the bean's `@PostConstruct`
  runs — consolidate the role catalog into migrations.
- [ ] **W19-6 —** `RiskScoringService.calculateOverallRisk:160` is a public method invoked nowhere (dead), and
  contains a latent `Long.valueOf(txnId)` that would throw on a non-numeric id (harmless only because never
  called); `calculateKrs:121-123` multiplies MCC score by `W_NATIONALITY` (0.3) — weight-constant naming
  mismatch (not a wiring defect). Remove/rename.

> Fully-clean (verified): Auth chains resolve, all tables backed (`platform_users`/`roles`/`role_permissions`
> V14, `role_permissions_dynamic` V18, `password_reset_tokens` V121), no orphaned auth beans; Risk module
> fully wired (`RiskScoringService` invoked from `TransactionIngestionService:109,124,129`,
> `CustomerRiskProfilingService` consumed widely, entities V132/V11 backed); frontend Users/Roles/RiskAnalytics
> pages call real endpoints, no mock data.

---

## Wave 18 — In-Flight New-Feature Audit (2026-07-15) 🔎 — IN PROGRESS

Auditing the feature sets you're actively building (V159–V165 + market surveillance) as they settle,
so findings reflect finished code, not moving targets.

- ✅ **V159 regulatory-deadline governance — VERIFIED CLEAN.** entity↔`regulatory_deadline_policies`
  columns/types/enum-widths all match; repo `findByReportTypeAndActiveTrue` backed; service consumed by
  `SarWorkflowService.java:81-87` (not orphaned); V159 seed within limits; frontend renders
  `deadlinePolicyCode`/`deadlineBreached` via the SAR object. No blocker/high/medium.
  - [ ] **W18-1 (LOW)** — `RegulatoryDeadlinePolicy.createdAt` is `nullable=false` with no
    `@CreationTimestamp`/default/setter. Harmless today (rows only seeded via V159 SQL, repo is read-only),
    but a future JPA insert of a policy would fail. Add `@CreationTimestamp` if policies ever get created via JPA.
- ✅ **V164 market surveillance + V163 SAR review evidence — VERIFIED FULLY CLEAN.** entity↔migration
  parity exact (`NUMERIC(28,10)`, enum widths safe), `MarketSurveillanceService` has real 6-scenario
  detection (layering/spoofing/wash-trade/off-market/marking-the-close/prearranged), FKs resolve,
  frontend fully wired (route/page/sidebar/queries/mutations). SAR `reviewed_at`/`review_notes` match
  V163 incl `_aud`. No gaps at any severity. (Info-only: V163 main-table ALTER omits `IF NOT EXISTS`.)
- ✅ **V160 rule maker-checker/versioning — wired end-to-end** (schema↔entity incl `_aud` match, approval
  flow gates activation, frontend `RulesGenerationPage` consumes pending/versions/approve/reject). Gaps:
  - [ ] **W18-2 (MEDIUM) — lifecycle_status not enforced by the execution path.**
    `DroolsRulesService.reloadRules:83` loads rules via `findByEnabledTrueOrderByPriorityDesc()` — filters
    on `enabled` only, never reads `lifecycle_status`. A rule `enabled=true` but
    `SUPERSEDED`/`PENDING`/`REJECTED` would still fire. The governance flow keeps them in sync today, but
    the guarantee lives only in the `enabled` flag. **Fix:** also filter `lifecycle_status='ACTIVE'` in the loader.
  - [ ] **W18-3 (MEDIUM) — maker-checker bypass via tuning.** `AlertTuningService.applyRecommendation:112-126`
    mutates a live rule's `parameters` and `ruleRepository.save()` directly — no `RuleVersion`, no approval,
    no `pendingVersionId`. An operator-applied tuning change edits an ACTIVE rule outside the four-eyes flow.
    **Fix:** route tuning through `RuleGovernanceService.proposeUpdate`.
  - [ ] **W18-4 (LOW)** — V160 backfill `WHERE created_by IS NOT NULL` can leave "active but versionless"
    rules (`lifecycle_status=ACTIVE`, `current_version_id=NULL`); `content_hash` computed differently in
    migration vs Java `hash()` (never read, so harmless); `RuleGovernanceService` approve/reject/rollback
    use `getCurrentUser()` without a null check → NPE(500) edge case if principal absent from DB.
- ✅ **V161 multi-domain signal taxonomy — wired, schema↔entity exact.** Gaps:
  - [ ] **W18-5 (MEDIUM) — backfill ↔ runtime taxonomy mismatch.** `V161:46-52` backfills
    `CRYPTO_SCREENING_UNAVAILABLE`/`CRYPTO_FIAT_VALUE_MISSING`/`CRYPTO_TRAVEL_RULE_INCOMPLETE` as signal_type
    `AML`, but runtime `MultiAssetRiskEngine.signalTypeFor:310-313` maps **every** `CRYPTO_*` code to
    `CRYPTO_EXPOSURE` → the same signal_code has two signal_types (historical vs new rows). Align the backfill
    CASE with `signalTypeFor`.
  - [ ] **W18-6 (MEDIUM — confirm intent)** — the taxonomy is **stored metadata, not wired into decisioning**:
    `RiskDecision` (`MultiAssetRiskEngine.java:53-57`) is derived purely from summed `scoreImpact`;
    `FinancialCrimeSignalType` is assigned post-hoc and `productDomain` is never read during scoring. If signal
    discipline was meant to affect the decision/severity/alert action, it's unwired; if it's reporting-only, OK.
  - [ ] **W18-7 (LOW)** — enum constants never produced by any code path: `FinancialCrimeSignalType.SANCTIONS`/
    `FRAUD`/`CYBER` (advertised in V161:70 comment); `ProductDomain.SECURITIES_MARKET_SURVEILLANCE`/
    `TOKENIZED_FIAT_CBDC` (only reachable if a caller passes `request.productDomain()` explicitly).
  - [ ] **W18-8 (LOW)** — `Customer360Page.tsx:215` risk-signals list shows `signalCode`/`scoreImpact`/
    `severity`/`description` but not the new `signalType` (typed + returned by API, never displayed).

---

## Wave 15 — Beyond-the-Diff Wiring Audit (2026-07-15) 🔎 — TO IMPLEMENT

Audit of subsystems NOT touched by the current diff: scheduled jobs, Kafka pipeline, Neo4j graph,
rule engines. `@EnableScheduling` present, all 34 crons valid, no stub job bodies. Findings below are
about **dead/dormant wiring** — code that exists and compiles but never actually runs end-to-end.

### 🟠 HIGH — core pipeline dead-ends (verify intent, then wire or remove)

- [ ] **W15-1 — Kafka enrichment pipeline dead-ends.** `FeatureEngineService.java:146` publishes to
  `transactions.enriched` but **no `@KafkaListener` consumes it**. The pipeline goes raw→enriched→∅.
- [ ] **W15-2 — `alerts.generated` has no consumer.** `DecisionEngine.java:454` publishes it; `KafkaConfig.java:130`
  javadoc says downstream case-creation/notification consume it, but no consumer exists.
- [ ] **W15-3 — Reporting listens to the wrong alert topic.** `ReportingConsumer.java:70` listens on
  `aml.compliance.alert`, which **nothing produces**; alerts actually go to `alerts.generated` (W15-2).
  Almost certainly a topic-name mismatch → reporting alert metrics never populate. **Fix:** point the
  listener at `alerts.generated` (and confirm W15-2's producer stays).
- [ ] **W15-4 — Neo4j graph is never written.** `Neo4jGraphIngestionService.java:23`
  (`ingestTransaction`/`linkMerchants`/`updateMerchantMetrics`) has **zero callers** — even with
  `neo4j.enabled=true` no graph data is ingested. The only reachable network endpoint
  (`CaseNetworkController` → `CaseNetworkService`, `GET /cases/{id}/network`) is built on **JPA**, not
  Neo4j. **Fix:** call the ingestion service from the transaction pipeline, or drop the Neo4j module if
  the JPA-based network view is the intended one. (Neo4j is gated off by default, so not a startup blocker.)
- [ ] **W15-5 — Drools loaded but never fires.** `DroolsRulesService` compiles `rules/aml-rules.drl`
  (10 rules) into a KieContainer at `@PostConstruct`, but `RulesExecutionService.java:91` only calls
  `droolsService.evaluate()` for `rule_type='DROOLS_DRL'` rules — and **every seeded rule is `SPEL`
  (60 rows, zero DROOLS_DRL)**. So the DRL rules never run in the decision path. **Fix:** seed the
  intended rules as `DROOLS_DRL`, or convert `aml-rules.drl` logic to SpEL, or remove the dead engine.
  (SpEL and Easy Rules ARE fired correctly — this is Drools-only.)

### 🟡 MEDIUM — dormant features / partial coverage

- [ ] **W15-6 — Sanctions list auto-refresh disabled everywhere.** `SanctionsListDownloadService.java:87,105`
  is gated by `sanctions.download.enabled`, which is `false` in `application.properties:174` **and** in dev,
  testenv, and production profiles → the OpenSanctions list is never auto-refreshed in any committed config.
  Confirm this is intentional (manual refresh?) or enable it in prod.
- [ ] **W15-7 — CBK regulatory submissions never register by default.** `CbkScheduler.java:28`
  `@ConditionalOnProperty(cbk.enabled=true)` has **no `matchIfMissing`**, and `cbk.enabled` defaults `false`
  → all 6 CBK submission cron jobs are absent under every default profile. Set `CBK_ENABLED=true` where CBK
  filing is required.
- [ ] **W15-8 — Kafka orphans / audit sink.** `KafkaConfig.java:112,41` declares `features.updates` topic
  with zero producers/consumers (orphan); `AuditLogService.java:160` publishes `transactions.audit` with no
  in-repo consumer; `TransactionAlertConsumer.java:32` listens on hardcoded `aml.transaction.alerts` (no topic
  bean, `auto-create=false`). Confirm which are external-integration sinks vs. genuinely dead.
- [ ] **W15-9 — Neo4j read path & orphan nodes.** Graph repo Cypher methods
  (`findConnectedMerchants`/`findNetworkWithin3Hops`/`findHighInfluenceMerchants`/…) are never called; `@Node`
  entities `DeviceNode`/`AccountNode`/`AccountTransfer`/`MerchantRelationship` have no repository. Depends on W15-4.
- [ ] **W15-10 — Drools programmatic fallback incomplete.** `DroolsRulesService.evaluateProgrammaticRules:214`
  implements only 3 of the 10 DRL rules (CTR, structuring, OFAC-country); if the KieContainer build fails the
  other 7 (graph/ML/velocity/merchant-volume) silently don't run.

### 🟢 LOW — contention / efficiency

- [ ] **W15-11 —** `PeriodicSanctionsScreeningService.java:50` and `PeriodicRescreeningService.java:53` both
  fire at `0 0 3 * * *` and scan the same `findMerchantsNeedingRescreening(today)` → duplicated heavy screening
  against DB + sanctions API. Former has no enable-guard. Stagger them or dedupe.
- [ ] **W15-12 —** 02:00 job cluster (`BatchScoringService:51`, `PeriodicKycRefreshService:56`,
  `EnhancedAuditService:151` bulk-delete, `CaseArchivalService:51`) contends; `BatchScoringService` and
  `CaseArchivalService` use `transactionRepository.findAll().stream()` / `findAll().stream()` to filter one day
  → full-table load, memory/DB scalability risk. Use date-ranged queries and stagger the cron times.

> Fully-clean (verified, no action): `@EnableScheduling` live; ~20 scheduled jobs cleanly gated; Kafka broker/
> group-id/serialization config sound; 3/8 topics fully wired (case lifecycle/decision, transactions.raw→enriched);
> SpEL (60 rules) and Easy Rules (11 rules) load and fire; rule files have no orphans/missing paths.
> NOTE: a sub-audit flagged `RuleEffectivenessService` as a deletion/compile-break — **false positive**, it is a
> package move (`service.alert`→`service.rules`) and `mvn compile` succeeds.

---

## Wave 16 — Domain Wiring Audit: Security / KYC-Doc-Chargeback / Crypto-Billing (2026-07-15) 🔎 — TO IMPLEMENT

Audit of security config and four business domains. No STARTUP blockers (all entities have backing
migrations; `@EnableMethodSecurity` present so `@PreAuthorize` is enforced). But one **functional
blocker** (billing bills $0), several security-hardening gaps, and a large orphaned KYC/Document tier.

### 🔴 FUNCTIONAL BLOCKER

- [ ] **W16-1 — Usage-based billing produces $0 for every request (VERIFIED).** The only live metering
  path, `config/UsageTrackingFilter.java:51-63`, emits service-type names (`TRANSACTION_PROCESSING`,
  `SANCTIONS_SCREENING`, `AML_CHECK`, `SCREENING`, `RISK_ASSESSMENT`, `REPORT_GENERATION`,
  `CASE_MANAGEMENT`, `ALERT_MANAGEMENT`, `MERCHANT_ONBOARDING`, `MERCHANT_MANAGEMENT`, `SAR_FILING`,
  `CBK_REPORTING`, `BILLING_OPERATIONS`). The seeded `billing_rates` (`V3__…:281+`, `V149`) use a
  **disjoint** vocabulary (`TRANSACTION_MONITORING`, `AML_SCREENING`, `SANCTIONS_SCREENING_PERSON`,
  `SANCTIONS_SCREENING_ORGANIZATION`, `KYC_VERIFICATION`, `COMPLIANCE_CASE_CREATION`,
  `MERCHANT_RESCREENING`, `API_CALL_GENERIC`). **Confirmed zero exact-string overlap** →
  `BillingService.calculateUsageCost` (`ApiUsageTrackingService.java:53`) returns ZERO → every
  `ApiUsageLog` saved `billable=false, cost=0` → `generateMonthlyInvoice` emits $0 invoices. The Wave 13
  "billing pipeline" fires end-to-end but bills nothing. **Fix:** reconcile the two vocabularies — either
  map filter service-types → seeded rate keys, or re-seed `billing_rates` with the filter's keys (add a
  `V159`/`V160` seed), and add a test asserting a non-zero cost for a known service type.
- [ ] **W16-2 (HIGH, same root cause) —** `service/billing/MeteringEventPublisher.java`
  (`recordAmlScreening`/`recordKycVerification`/`recordTransactionMonitoring`, which DO use the seeded
  vocab) has **no callers** (only a comment ref at `AsyncConfig.java:58`). The correctly-priced path is
  dead code. Decide: wire `MeteringEventPublisher` into the real screening/KYC/txn flows and retire the
  filter's ad-hoc types, or delete it. (Even then its `SANCTIONS_SCREENING` ≠ seeded `..._PERSON/_ORG`.)

### 🟠 HIGH — security hardening

- [ ] **W16-3 — CSRF disabled blanket-wide under session-cookie auth.** `config/SecurityConfig.java:86`
  `.csrf(csrf -> csrf.disable())` while auth uses `JSESSIONID` cookies. `CookieCsrfTokenRepository` is
  imported (line 16) but unused; `CsrfController` `/auth/csrf` reads a `_csrf` attribute that's never
  populated → the whole CSRF flow (frontend `X-CSRF-Token`, `custom.csrf.*` prod props) is inert and
  session endpoints are CSRF-exposed. **Fix:** enable `CookieCsrfTokenRepository.withHttpOnlyFalse()`
  (ignoring truly stateless/webhook paths), or document why session-CSRF is acceptable.
- [ ] **W16-4 — `prod` profile gets none of the production hardening.** `SecurityHeadersFilter.java:25`,
  `ProductionRateLimitFilter.java:31`, `WebMvcConfig.java:28` are `@Profile("production")` only, but
  `application-prod.properties:2` documents itself as loaded under `SPRING_PROFILES_ACTIVE=prod`, and
  `EnvVarStartupValidator:88-90` treats `prod`==`production`. Booting under `prod` silently drops
  HSTS/CSP/X-Frame-Options, the rate limiter, and all CORS. **Fix:** add `prod` to the `@Profile` list
  (`@Profile({"prod","production"})`) or standardize on one profile name.

### 🟠 HIGH — orphaned business logic (built, migrated, never reachable)

- [ ] **W16-5 — Entire KYC CDD/EDD/UBO/trigger tier is dead code.** No REST controller or caller reaches:
  `service/kyc/RiskBasedCddService.java:17`, `service/kyc/BeneficialOwnershipService.java:18`,
  `service/edd/EnhancedDueDiligenceService.java:12` (persists `edd_requests`),
  `service/kyc/TriggerBasedKycService.java:31` (never wired into transaction processing). Real
  implementations with backing migrations, but unreachable. **Fix:** add controllers/wire
  `TriggerBasedKycService` into the txn pipeline, or remove if superseded. (Only `PeriodicKycRefreshService`
  + `KycCompletenessService` actually run.)
- [ ] **W16-6 — Document versioning / search / access-control services orphaned.**
  `service/document/DocumentVersionService.java:19` (uploads always persist `version=1`, chain never built —
  schema V121 supports it), `DocumentSearchService.java:18` (no search endpoint),
  `DocumentAccessControlService.java:22` (`DocumentController` does an inline PSP check instead, so
  `canAccessDocument`/`logAccess` never run and the `document_access_logs` table (V121) is never populated).
  **Fix:** wire versioning into `DocumentManagementService.upload`, expose search, and route access checks
  through the access-control service (also replace its placeholder role-only model at line 65).

### 🟡 MEDIUM

- [ ] **W16-7 —** `/actuator/**` is `permitAll` (`SecurityConfig.java:114`) while `metrics,prometheus` are
  exposed (`application.properties:392`) → unauthenticated internal-metrics disclosure. Restrict actuator
  (except `/health`) to an admin role or internal network.
- [ ] **W16-8 —** OpenAPI JSON public in prod: prod disables Swagger UI but not `springdoc.api-docs.enabled`,
  and the chain permitAll's `/v3/api-docs/**` (`SecurityConfig.java:119-127`) → full API schema retrievable
  in production. Disable `api-docs` in prod or secure the path.
- [ ] **W16-9 —** `corsConfigurationSource` bean is never consumed by the security chain (no `http.cors()` in
  `SecurityConfig`); only MVC-level `addCorsMappings` (`WebMvcConfig.java:86`) is active → preflight for
  security-rejected requests uncovered. Add `.cors(Customizer.withDefaults())` to the chain. (Allowlist itself
  is safe — hokeka.com origins, no wildcard-with-credentials.)
- [ ] **W16-10 —** `KycExpirationTrackingService.java:58-128` read API (`getExpiringDocuments` etc.) exposed by
  no controller (the `@Scheduled` sweep runs, but the data is never surfaced to a UI).
- [ ] **W16-11 —** `KycDocumentsPage.tsx:208,212` sends `search=<term>` to `GET /merchants`, but
  `MerchantController.getAllMerchants` only declares `page`/`size` → Spring drops the param, merchant search
  is a silent no-op. **Fix:** add a `search` param + `Specification`/query filter to `MerchantController`.
- [ ] **W16-12 —** M-Pesa default `mpesa.callback-url` is the placeholder `https://your-domain.com/...`
  (`application.properties:315`) → STK callbacks never arrive (invoices never auto-mark PAID) unless overridden
  in prod. Set it in the production config.
- [ ] **W16-13 —** `document_access_logs` table (V121) is a dead table — its only writer
  (`DocumentAccessControlService.logAccess`) is orphaned (see W16-6); controller audits via `AuditService`
  instead. Wire it or drop the table.

### 🟢 LOW / cleanup

- [ ] **W16-14 —** committed default secrets/placeholders: `application.properties:314` hardcoded
  `mpesa.passkey` (Safaricom sandbox value), `application-testenv.properties:5,56` test DB password + JWT
  fallback (self-labelled testenv-only). Ensure prod overrides via env and none leak to a prod profile.
- [ ] **W16-15 —** `config/SecurityConfig.java:24` `@ConditionalOnProperty(spring.security.enabled,
  matchIfMissing=true)` — a single flag set false removes the whole config **including** `@EnableMethodSecurity`,
  silently disabling every `@PreAuthorize`. Remove the kill-switch or split method-security out.
- [ ] **W16-16 —** `config/RlsContextFilter.java` has no `@Order` → its position vs Spring Security's
  `DelegatingFilterProxy` isn't pinned; PSP row-level-security context could be unset for some requests. Pin order.
- [ ] **W16-17 —** Crypto/VASP named domain is empty scaffolding: `controller/crypto`, `controller/vasp`,
  `entity/crypto`, `entity/vasp`, `repository/crypto`, `service/crypto` dirs contain **zero files**. Real
  crypto/travel-rule logic lives under `multiasset` (cleanly wired). Remove the empty dirs or implement.
- [ ] **W16-18 —** `ApiUsageTrackingService.java:66` hardcodes `costCurrency("USD")` (self-noted "should come
  from billing rate"); `ChargebackDispute.complianceCaseId` set-never-read.

> Fully-clean (verified): Chargeback/Verifi RDR end-to-end (controllers→services→repos, V144/V155 match);
> Document core CRUD + real filesystem storage (path-traversal guarded); M-Pesa STK/OAuth/callback fully
> implemented; billing structural chain + frontend wired; method security enforced; one filter chain,
> secure-by-default `anyRequest().authenticated()`; multiasset crypto path real (wallet screening + travel rule).

---

## Wave 17 — Untouched-Domain Audit: Limits / Monitoring / Admin / Calendar (2026-07-15) 🔎 — TO IMPLEMENT

Audit of domains not touched by Waves 14–16 and NOT in the user's in-flight V159–161 set.
Two boot-fatal BLOCKERs (same class as W14-1) plus two HIGH UI-path bugs. _analytics/notification/
search/FATF audit still running — appended when complete._

### 🔴 BLOCKER — prod `ddl-auto=validate` boot failure (VERIFIED)

- [x] **W17-1 — `velocity_rules` missing `psp_id`.** ✅ FIXED 2026-07-15 → `V165__limits_psp_id_columns.sql`.
- [x] **W17-2 — `risk_thresholds` missing `psp_id`.** ✅ FIXED 2026-07-15 → same `V165` migration
  (`ADD COLUMN IF NOT EXISTS psp_id BIGINT` on both + indexes). See `docs/CHANGES-BY-CLAUDE.md`.
  Not applied to your DB (avoided `flyway:migrate` mid-batch); apply when your migration set is ready.
  > COMPREHENSIVE psp_id VERIFICATION (2026-07-15): scanned all 22 entities mapping `psp_id` — every table
  > now has a backing migration (`alerts`→V152, `velocity_rules`/`risk_thresholds`→V165, all others pre-existing).
  > **No remaining psp_id blockers.**

### 🟠 HIGH — only UI-exercised paths are broken

- [ ] **W17-3 — Limits "save" 500s on NOT NULL.** `LimitsManagementController.java:136-147` (`POST /limits/aml`,
  the only endpoint the frontend calls) persists `GlobalLimit` setting only `limitType`/`limitValue`, but
  `GlobalLimit.name` and `.period` are `@Column(nullable=false)` (V9 NOT NULL) with no `@PrePersist` default →
  every save from the UI throws a NOT NULL violation. **Fix:** default/require `name`+`period`.
- [ ] **W17-4 — Compliance-calendar "Create Deadline" 400s.** `ComplianceCalendarPage.tsx:19,31` +
  `features/api/mutations.ts:274` send `dueDate` as date-only `"YYYY-MM-DD"`, but
  `ComplianceCalendarController` `CreateDeadlineFrontendRequest.dueDate:91` is a `LocalDateTime` requiring a
  time component → create request 400s. **Fix:** accept `LocalDate` (or append time) on one side.

### 🟡 MEDIUM

- [ ] **W17-5 —** `ComplianceCalendarPage.tsx:62,84` render `deadline.dueDate`, but the entity serializes the
  field as `deadlineDate` → date renders empty for every calendar item. Align the field name.
- [ ] **W17-6 —** `TransactionMonitoringService.java:501` (`getSanctionsStatus` via `toTransactionDTO:394`)
  makes a synchronous per-row call to the AML microservice → up to ~100 external HTTP round-trips per
  `/monitoring/transactions` page (N+1 external call). Batch or cache it.
- [ ] **W17-7 —** `limits`: `country_compliance_rules` and `global_limits` are CRUD-persisted but never read at
  runtime — `global_limits` is dashboard-aggregated but not enforced against any transaction; country rules have
  no consumer. Either enforce them in `DecisionEngine` or mark as reporting-only.
- [ ] **W17-8 —** `PspAdminController.java:35,60,68,76,84,103` declare `@RequestHeader("X-User-Role")` without
  `required=false` → requests 400 if header absent, and the value is then ignored (real auth is
  `permissionService.hasPermission`). Remove the dead required param.
- [ ] **W17-9 —** admin controllers have no frontend consumer: PSP admin UI targets `/psps`
  (`controller/psp/PspController`), not `/admin/psp`; `/admin/runtime-errors` has no page. Confirm intent.

### 🟢 LOW

- [ ] **W17-10 —** `LimitsManagementService.java:143-152` (`getAllGlobalLimits`) swallows exceptions and returns
  `[]`, masking DB errors. `RiskThreshold.perTransactionLimit`/`velocityLimit` + `VelocityRule.maxAmount` stored
  but never read at decision time (velocity uses only `maxTransactions`/`timeWindowMinutes`).
- [ ] **W17-11 —** `TransactionMonitoringService.java:441-443` legacy `getRiskScore` fallback has inverted
  thresholds (`>100k→75` wins before `>50k→95`); affects only rows with null `trs`. Reorder.
- [ ] **W17-12 —** dead-from-FE endpoints: `TransactionMonitoringController.java:72-93` (`/monitoring/sars`,
  `/reports/declines`, `/reports/summary`); `POST /compliance/calendar/deadlines` + `.../{id}/complete`.

> Fully-clean (verified): Monitoring core (endpoints→service→repo, `monitoring_alerts` V2+V154 backed, no
> orphans, FE real); Admin core (`RuntimeErrorService` live, `runtime_errors` V101, Psp theme V7);
> compliance-calendar backend chain (`ComplianceDeadline`, tables V18/V121/V118/V159 backed, `@Scheduled`
> deadline check live). Enforcement note: merchant txn limits ARE hard-blocked in `DecisionEngine:232-241`;
> velocity rules ARE scored in `FraudDetectionService:401-446`; risk thresholds only used at onboarding.

### 🔴 BLOCKER (analytics/notification/search/FATF audit) — VERIFIED

- [ ] **W17-13 — FATF `V147` seed aborts Flyway on a clean DB (data too long).**
  `V147__reference_data_seed_and_indexes.sql:35-47` inserts ISO **alpha-3** codes (`'PRK'`,`'IRN'`,`'MMR'`,…)
  into `country_risk_scores.country_code`, which is `CHAR(2) PRIMARY KEY` (`V132:21`; entity
  `CountryRiskScore.java:22`). Postgres rejects 3-char values ("value too long for type character(2)"); the
  `WHERE NOT EXISTS` guard doesn't prevent it because the alpha-3 rows don't yet exist → the INSERT executes →
  migration aborts → app won't start on a clean/prod DB. **Confirmed by reading both files.**
  **Fix (your call — do NOT let me auto-edit a possibly-applied migration):** either (a) change V147's seed to
  alpha-2 codes (`KP`,`IR`,`MM`,…) to match the rest of the system, or (b) if alpha-3 is intended, widen
  `country_code` to `VARCHAR(3)` in a NEW migration and migrate the PK — but note the whole system keys country
  risk by alpha-2, so alpha-3 rows would be dead data (W17-14). Alpha-2 (option a) is strongly preferred.
  If V147 already applied on your dev DB, the fix belongs in a new forward migration, not an edit to V147.

### 🟠 HIGH (analytics/notification/search/FATF)

- [ ] **W17-14 — Global search is advertised UI over a non-existent backend.** There is no `controller/search`,
  no search endpoint, no Elasticsearch. `components/Layout/Header.tsx:194-206` and
  `components/header/HokekaHeader.tsx:40-45` render global search bars (placeholder "search across
  dashboard/transactions/cases/reports", ⌘K hint) with no `value`/`onChange`/handler — decorative UI for a
  missing feature. `service/document/DocumentSearchService.java:18` is dead code (orphaned, in-memory
  `findAll()`+filter, not real search). **Fix:** build a real search endpoint + wire the bars, or remove the bars.

### 🟡 MEDIUM (analytics/notification/search/FATF)

- [ ] **W17-15 —** `AnalyticsPage.tsx:144-148` "Decision Breakdown" pie reads
  `approvedTransactions`/`declinedTransactions`/`manualReviewTransactions` keys that
  `DashboardController.getGlobalStats` never emits → chart is permanently empty. Emit the keys or change the chart.
- [ ] **W17-16 —** orphaned @Service beans (injected nowhere, no scheduler/listener → dead):
  `service/analytics/BehavioralAnalyticsService.java:21` (`compareToPeerGroup`/`detectDormantAccountReactivation`);
  `compliance/RegulatoryComplianceService.java:24` (FATF `isFatfBlacklisted`/`isFatfGreylisted` BLOCK+STR logic —
  real enforcement instead lives in `RiskScoringService`/`AmlService`); `NotificationService.java:110`
  `sendComplianceReportCallback` (log-only stub, webhook channel has no transport).
- [ ] **W17-17 —** `RegulatoryComplianceService.java:57-71` hardcodes FATF black/grey lists as `Set.of(...)`
  "As of 2024" — a third parallel source of truth vs the two DB tables (`high_risk_countries`,
  `country_risk_scores`). Consistency trap; consolidate on the DB source.

### 🟢 LOW (analytics/notification/search/FATF)

- [ ] **W17-18 —** `DashboardController.java:200-201` `openCasesDelta`/`highRiskCustomersDelta` hardcoded `0.0`
  (honest default, not fabricated) — compute real deltas. `CountryRiskScore.java:15` javadoc says table created by
  V130 but it's actually V132 (doc drift). Header notification bell (`HokekaHeader.tsx:66`) fed `alertCount` not
  message-unread and has no onClick. `CountryRiskScoreController` (`/risk/country-scores`) has no FE consumer.

> Fully-clean (verified): Analytics chains resolve (`ModelMetrics` V1+V145 backed, no stubs); Notification
> end-to-end (real JavaMailSender email + Slack webhook + Kafka `NotificationConsumer` + in-app `Message` V123,
> FE wired; email/Slack fail-soft-disabled by default); FATF `CountryRiskScore`/`HighRiskCountry` schema backed,
> static fallback lists are intentional DB-first fallbacks.

---

## Wave 13 — End-to-End Wiring Completion (this session) ✅

### W13-A: Usage Tracking → Billing Pipeline (CRITICAL GAP FIX) ✅

- [x] **UsageTrackingFilter** — Created `UsageTrackingFilter.java` (OncePerRequestFilter) intercepts every `/api/v1/*` request.
- [x] Resolves service type from URL pattern (12 mappings: TRANSACTION_PROCESSING, SANCTIONS_SCREENING, AML_CHECK, MERCHANT_ONBOARDING, REPORT_GENERATION, CASE_MANAGEMENT, ALERT_MANAGEMENT, SAR_FILING, CBK_REPORTING, BILLING_OPERATIONS, etc.).
- [x] Extracts PSP ID from SecurityContext and fires `ApiUsageTrackingService.logRequest()` asynchronously.
- [x] **Critical fix**: `ApiUsageTrackingService.logRequest()` was NEVER called from anywhere — billing pipeline was completely dormant. The filter fixes this.
- [x] Cost per transaction calculated in real-time via `BillingService.calculateUsageCost(pspId, serviceType, 1)`.
- [x] PSP consumption now flows: API → UsageTrackingFilter → ApiUsageLog → Invoice Generation.

### W13-B: Hardcoded Country/MCC Risk Lists → Config-Driven ✅

- [x] **AmlCheckService (microservice)** — `HIGH_RISK_COUNTRIES`/`MEDIUM_RISK_COUNTRIES` static Set.of() replaced with `@Value`-injected fields from `application.yml`.
- [x] **FraudDetectionService** — FATF_HIGH_RISK_COUNTRIES static Set replaced with `@Value`-injected `fallbackHighRiskCountries` (configurable per env).
- [x] **AmlService** — Same treatment via `aml.fallback-high-risk-countries`.
- [x] **RiskScoringService** — MCC_RISK static HashMap replaced with `MccRiskConfig @ConfigurationProperties(prefix="risk.mcc")` class.
- [x] All defaults preserve existing FATF/MCC values.

### W13-C: DB-Backed Country Risk Management Controllers ✅

- [x] **HighRiskCountryController** (`/api/v1/risk/high-risk-countries`) — Full CRUD (GET list, GET/{id}, POST, PUT/{id}, DELETE/{id}). DB-backed `high_risk_countries` table. Access: ADMIN/COMPLIANCE_OFFICER manage, all authenticated read.
- [x] **CountryRiskScoreController** (`/api/v1/risk/country-scores`) — Full CRUD for `country_risk_scores` table (numeric scores, FATF tiers, blacklist/greylist status).
- [x] Primary data source for `FraudDetectionService.isHighRiskCountry()`, `AmlService.assessGeographicRisk()`, `RiskScoringService.getCountryRisk()`.

### W13-D: Alert → Case Escalation Bridge ✅

- [x] **AlertToCaseService** — Auto-creates `ComplianceCase` when alerts resolved with case-worthy dispositions (ESCALATED, TRUE_POSITIVE_* , MERGED_WITH_CASE, PENDING_INFORMATION, ONGOING_MONITORING).
- [x] **Escalation Matrix**:
  - Score ≥0.9 → CRITICAL (4h SLA) → immediate escalation to MLRO
  - Score ≥0.7 → HIGH (24h SLA) → escalate to COMPLIANCE_OFFICER
  - Score ≥0.4 → MEDIUM (3d SLA) → assign to ANALYST
  - ELSE → LOW (7d SLA) → assign to ANALYST
- [x] Decision outcomes: APPROVE→CLOSED_CLEARED, REJECT→CLOSED_BLOCKED, FILE_SAR→CLOSED_SAR_FILED.
- [x] Auto-assign via `WorkflowAutomationService` (workload balance).
- [x] Auto-escalation check via `CaseEscalationService.checkAutomaticEscalation()`.
- [x] Wired into `AlertController.resolveAlert()`.

### W13-E: CBK Reporting — Full Real-Data Audit + Fraud Incident Auto-Population ✅

- [x] **AlertFraudIncidentBridge** — Auto-creates `PspFraudIncident` when alert disposed TRUE_POSITIVE (BLOCKED/SAR_FILED/REPORTED). Feeds daily CBK GDI FRAUD_INCIDENTS endpoint from real alert data.
- [x] **Verified all 17 CBK GDI endpoints use real DB data**:
  - Annual (4): SENIOR_MANAGEMENT, DIRECTORS, TRUSTEES, SHAREHOLDERS → Psp*Repository
  - Monthly (5): CUSTOMER_COMPLAINTS, PRODUCTS_INFO, CARD_BRANDS, TRANSACTION_DETAILS, TRANSACTION_TARIFFS → Psp*Repository + TransactionRepository
  - Daily (8): CYBER_INCIDENT, FRAUD_INCIDENTS, SYSTEM_STABILITY, SYSTEM_ACTIVITY, TRUST_ACCOUNT, BILLING_TEMPLATE, MERCHANT_TRANSACTIONS, FAILED_TRANSACTIONS → Psp*Repository + TransactionRepository

### W13-F: Cross-PSP Fraud Intelligence ✅

- [x] **CrossPspFraudFlag entity** + Flyway V146 migration (`cross_psp_fraud_flags` table). Tracks MERCHANT_ID, PAN_HASH, TERMINAL, and NAME flags.
- [x] **CrossPspFraudIntelligenceService**:
  - WRITE: Auto-populated from TRUE_POSITIVE alerts via `AlertFraudIncidentBridge`. Flags merchant ID, PAN hash, terminal, merchant trading name. Uses `NameMatchingService` (DoubleMetaphone + Levenshtein) for fuzzy name matching (≥80% similarity). Escalates risk level on repeat cross-PSP flags.
  - READ: `screenTransaction(txn)` — exact match + fuzzy name match against flagged entities. BLOCK if high risk or multi-PSP; HOLD otherwise.
- [x] **DecisionEngine integration**: 2nd hard rule (after sanctions, before ML scoring).

### W13-G: Dashboard Analytics Refresh Scheduler ✅

- [x] **DashboardAnalyticsRefresher** + **DashboardCache** — Recalculates live dashboard KPIs every 4 minutes (`0 */4 * * * *`). Conditionally disabled via `dashboard.analytics.refresh.enabled=false`.

### W13-H: Report Export Verification ✅

- [x] **All exports verified real**: ReportExportService (PDF/CSV/XML), ReportGenerationService (native SQL), DashboardController (real aggregates), RiskAnalyticsController, TransactionMonitoringController.

---

## Wave 12 — Dashboard redesign (Tailwind + Shadcn migration) 🚧

Pixel-match the Hokeka AML mockup; full migration off MUI to Tailwind + Shadcn + lucide + framer-motion + recharts + react-simple-maps.

- [x] **#69** Phase 1 — Tailwind+Shadcn foundation (deps, tailwind.config, CSS vars, Inter font, cn helper)
- [x] **#70** Phase 2 — DashboardLayout: 280px dark sidebar (#07142E), grouped nav, Hokeka SVG logo, header
- [x] **#71** Phase 3 — Dashboard widgets: 6 KPI cards, Risk Gauge, Live Alert Queue, Risk Heatmap, Investigation Cases donut, Alert Trends, Screening Results, Top Risk Merchants, Compliance Health
  - [x] Create `src/hooks/useDashboard.ts` with typed React Query hooks
  - [x] Build `src/components/kpi/KpiCard.tsx`
  - [x] Build `src/components/charts/RiskGauge.tsx`
  - [x] Build `src/components/Alerts/LiveAlertQueue.tsx`
  - [x] Build `src/components/charts/RiskHeatmap.tsx`
  - [x] Build `src/components/Cases/InvestigationCases.tsx`
  - [x] Build `src/components/charts/AlertTrends.tsx`
  - [x] Build `src/components/Alerts/ScreeningResults.tsx`
  - [x] Build `src/components/Alerts/TopRiskMerchants.tsx`
  - [x] Build `src/components/compliance/ComplianceHealth.tsx`
  - [x] Replace `src/pages/Dashboard/DashboardPage.tsx`
  - [x] `npm run typecheck` passes with zero errors
- [x] **#72** Phase 4 — Backend aggregate endpoints (extended `/dashboard/stats` + new `/risk-heatmap`, `/cases/closed-recent`, `/screening/results-today`, `/merchants/top-risk`, `/compliance/health`, `/alerts/trends`); V140 migration for CDD/EDD review timestamps; all widgets now consume real data
- [ ] **#73** Phase 5 — Migrate remaining pages off MUI, drop @mui/material (long tail — separate session)
  - [x] Phase 5a — Shared Tailwind component library (TwTable, TwPagination, TwBadge, TwSnackbar, TwInput, TwSelect)
  - [x] Phase 5b — AlertsPage, AuditLogsPage, MerchantsPage, KycDocumentsPage migrated (4 pages)
  - [x] Phase 5c — TransactionMonitoringLive/Reports/Sars migrated (3 pages)
  - [ ] Phase 5d — Remaining 15+ pages + AnalyticsPage + ReportsCenter components

---

## Wave 11 — Frontend cleanup (Task #68) ✅

- [x] **#68** Deleted orphaned `RolesPage.tsx` + `pages/Roles/` dir; added `/limits-aml` sidebar nav link; removed 7 empty page dirs; `npm run typecheck` passes clean.

---

## Wave 10 — Stub fixes (Task #67) ✅

- [x] **#67A** CBK controllers hardened: all 11 PSP CBK controllers + `CbkReportController` rewritten — NPE-safe `getCurrentUser()`/`getCurrentPspId()`.
- [x] **#67B** `PeriodicSanctionsScreeningService` — placeholder removed; new `CaseCreationService.triggerCaseFromSanctionsForMerchant()`.
- [x] **#67C** `SarContentGenerationService` — `MerchantRepository` injected; placeholder fallbacks removed.
- [x] **#67D** `mvn clean compile` BUILD SUCCESS — 648 sources compile.

---

## Wave 9 — Reports, Analytics, Data Output + DB Indexes ✅

- [x] **#59** `DashboardController` sanctions status + fraud metrics — all real DB queries.
- [x] **#60** `ReportExportService` — complete rewrite: real OpenPDF PDF, real CSV, no file-system writes.
- [x] **#61** `RegulatoryReportingService` — indexed queries, PSP-scoped stats endpoint.
- [x] **#62** `AnalyticsPage.tsx` — native Recharts analytics (4 tabs), Grafana fallback.
- [x] **#63** `V135__production_performance_indexes.sql` — 26 new composite/partial indexes.

---

## Wave 8 — Zero Stubs: Fraud Scoring, Risk, Kafka, Compliance ✅

- [x] **#53** `FraudDetectionService` — replaced 3 hardcoded-0 stubs (device/IP/behavioral risk).
- [x] **#54** `RiskScoringService.calculateCra()` — 5-dimension weighted CRA.
- [x] **#55** `FeatureExtractionService` — proper EMV CVMR 3-byte parsing.
- [x] **#56** `ComplianceReportingService.generateFincenXml()` — full goAML-pattern SAR XML.
- [x] **#57** UserService, WorkflowAutomationService, PrometheusMetricsService — all real DB/connections.
- [x] **#58** Kafka expanded 3→8 topics, ingestion pipeline end-to-end.

---

## Wave 7 — PSP Self-Serve Billing + Payments ✅

- [x] **#51** `SettingsPage.tsx` — PSP users see billing tab; platform admins see Theme + System Settings.
- [x] **#52** M-Pesa payment capability end-to-end (Daraja STK Push, callback, invoice auto-mark PAID).

---

## Wave 6 — SaaS Billing (full end-to-end) ✅

- [x] **#47** Expanded `BillingController` (+7 endpoints) + `SubscriptionController` (7 CRUD endpoints).
- [x] **#48** `InvoicePdfService` (OpenPDF A4 branded PDF), `BillingEmailService`, `DunningScheduler`.
- [x] **#49** `BillingPage.tsx` (admin, 4 tabs): Revenue Dashboard, Subscriptions, Invoices, Usage.
- [x] **#50** `BillingTab.tsx` added to `PspConfigPage` (tab #9): plan, usage, invoice history.

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

---

## Wave 28 Resolution - Durable Event, Drools, and Neo4j Wiring (2026-07-16)

- [x] **W28-1** - Removed the unowned `transactions.enriched` hop. `transactions.raw` now performs only an idempotent Redis feature projection; the API fraud orchestrator remains the single scoring path.
- [x] **W28-2** - Removed the mismatched demonstration consumer on `aml.transaction.alerts`. Persisted alerts publish durably to `alerts.generated`, reporting consumes that topic, and configured alert-to-case escalation remains synchronous to prevent duplicate cases.
- [x] **W28-3** - `RulesExecutionService` now evaluates the regulatory Drools baseline exactly once for every transaction, independent of database rule type. The complete programmatic fallback mirrors all active DRL rules.
- [x] **W28-4** - Neo4j consumes committed `transactions.raw` events in a dedicated group and upserts real PostgreSQL merchant/transaction data. Conditional repository configuration and the named Neo4j transaction manager no longer interfere with JPA.
- [x] **W28-5** - Removed orphan topics `transactions.enriched`, `features.updates`, and `aml.compliance.alert`; `transactions.audit` has a durable producer and every remaining topic has an explicit owner.
- [x] **W28-6** - Corrected the audit finding: `TransactionController` already invokes the synchronous fraud orchestrators after persistence, so no duplicate enriched-event scoring consumer was added.
- [x] **Reliability** - Transaction, alert, audit, and case producers use `event_outbox` with Kafka acknowledgement and retry. Reporting and Redis projections are idempotent. Production Compose runs Kafka, Redis, and Neo4j while Aerospike remains owned by `aml-microservice`.

---

## Wave 29 Resolution - Decision, Feedback, Model Evidence, and Tracing (2026-07-16)

- [x] Canonicalized new transaction outcomes to `ALLOW`, `ALERT`, `HOLD`, and `BLOCK` while preserving historical report compatibility.
- [x] Replaced the no-op gateway-result counter path with PSP-scoped transaction updates and durable audit outbox events.
- [x] Scheme metrics now read real transaction and Verifi dispute records; removed the historical backend Aerospike metrics repository and its empty increment method.
- [x] CBK card-brand and billing-classification queries now use their real normalized source columns.
- [x] Removed fabricated XGBoost weights; model explanations are persisted only when returned by the configured model service.
- [x] External scoring and enabled anomaly-model failures now hold for review rather than fail open.
- [x] DL4J requires a trained model file and never initializes a random production model.
- [x] Transaction record trails include scoring inputs, model/rule evidence, SAR/CTR flags, latency, and related alerts.
- [x] Replaced empty tracing configuration with opt-in OTLP request spans.
- [x] Removed the orphaned in-memory `AerospikeFeatureStore`; backend features remain Redis-backed and Aerospike remains in `aml-microservice`.

---

## Wave 30 Resolution - Tenant Limits, Reporting Isolation, and Record Navigation (2026-07-16)

- [x] Rebuilt AML limits as PSP-scoped, idempotent persisted controls and wired the frontend to load effective values.
- [x] Added tenant-aware database uniqueness for global limits, risk thresholds, velocity rules, and country-compliance rules.
- [x] Enforced merchant per-transaction, daily, weekly, and monthly limits plus PSP transaction and daily limits in the live decision engine.
- [x] Replaced dashboard in-memory transaction scans with PSP-scoped database aggregates.
- [x] Scoped legacy reporting summaries and audit metrics to the authenticated PSP; only platform administrators aggregate all tenants.
- [x] Restricted automatic and manual case assignment to users in the case PSP.
- [x] Expanded record navigation to audit logs, roles, invoices, subscriptions, disputes, CBK submissions, and report-preview identifiers.
- [x] Extended report provenance mappings and audit-record relations for the new navigable record types.
# Wave 31 - Prepaid and tokenized-fiat controls

- Separated `TOKENIZED_FIAT` from the `CRYPTO` asset class across the backend and Customer 360 UI.
- Added prepaid rapid-redemption, refund-velocity, and programme-provenance controls.
- Added tokenized-fiat issuer, ledger, daily-velocity, and cross-border controls without invoking crypto screening or Travel Rule logic.
- Added migration `V192` for existing tokenized-fiat account and transaction rows.

---

## Wave 32 Resolution - Corporate Intelligence and FIX Market Feeds (2026-07-16)

- [x] Added live OpenCorporates exact/fuzzy legal-entity verification and GDELT one-year adverse-media lead collection.
- [x] Persisted provider provenance, registry candidates, article sources, decisions, seven-year retention, and SHA-256 evidence in migration `V193`.
- [x] Wired corporate intelligence into onboarding, quick merchant drafts, manual KYC checks, per-merchant scheduled refresh, alerts, record trails, and report `KYC_004`.
- [x] Removed first-PSP selection, synthetic registration numbers, auto-approved KYC, active contracts, and default low-risk state from quick merchant creation.
- [x] Added QuickFIX/J 3.0 and authenticated FIX 4.4 acceptor/initiator sessions with persistent sequence/message stores.
- [x] Mapped NewOrderSingle, OrderCancelRequest, and ExecutionReport into the existing PSP-scoped surveillance engine.
- [x] Persisted sanitized, hash-verifiable, idempotent FIX receipts linked to orders/executions in migration `V194` and report `MKT_003`.
- [x] Added live FIX session/message monitoring and complete record navigation among feed receipts, orders, executions, signals, alerts, and reports.
- [x] Kept Aerospike exclusively in `aml-microservice`.
