# Change Log — Wiring Fixes by Claude

This file documents concrete code changes made by the automated wiring audit, with references
to the exact functions/files/lines involved, so they can be reviewed and traced. Audit findings
that are **not yet implemented** live in `TODO.md` (Waves 14–16); only items **actually changed**
are recorded here.

---

## 2026-07-15 — Fix W14-1 (BLOCKER): `alerts` table missing 4 mapped columns

### What was wrong
`entity/Alert.java` maps four `@Column`s that **no Flyway migration ever created** on the `alerts`
table:

| Entity field (`Alert.java`) | `@Column` name | Java type | Line |
|---|---|---|---|
| `severity`          | `severity`            | `String` (`INFO`/`WARN`/`CRITICAL`) | `Alert.java:60-61` |
| `merchantId`        | `merchant_id`         | `Long`                              | `Alert.java:45-46` |
| `dispositionReason` | `disposition_reason`  | `String` (`columnDefinition="TEXT"`)| `Alert.java:71-72` |
| `disposedBy`        | `disposed_by`         | `String` (investigator name)        | `Alert.java:74-75` |

Migration history that *should* have covered them but did not:
- `V1__Initial_Schema.sql:46-56` — created `alerts` without any of the four.
- `V5__compliance_case_merchant.sql:3-4` — added `merchant_id` to **`compliance_cases`**, not `alerts`.
- `V10__enhanced_screening_features.sql:5-6` — added only `disposition` + `disposed_at`.
- `V152__multi_asset_alert_bridge.sql:1-4` — added `psp_id`, `multi_asset_customer_id`,
  `source_type`, `source_reference`.

**Impact:** Production sets `spring.jpa.hibernate.ddl-auto=validate`
(`application.properties:91`, `application-production.properties:40`). On a clean deploy Hibernate
validates entity mappings against the live schema at startup and **aborts** with
`Schema-validation: missing column [severity] in table [alerts]`. Dev/testenv masked the gap because
they use `ddl-auto=create-drop`/`update`, which auto-adds the columns. Consumers that already assume
the columns exist: `V109__report_definitions_seed.sql:299,311` (`a.severity`), and the multi-asset
alert bridge (`service/multiasset/MultiAssetService` → `Alert.setSeverity(...)`).

### The fix
Added **`BACKEND/src/main/resources/db/migration/V162__alerts_missing_columns.sql`** (V162 was the
next free version after your in-flight V159–V161). It performs an idempotent additive `ALTER TABLE`:

```sql
ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS severity           VARCHAR(20),
    ADD COLUMN IF NOT EXISTS merchant_id        BIGINT,
    ADD COLUMN IF NOT EXISTS disposition_reason TEXT,
    ADD COLUMN IF NOT EXISTS disposed_by        VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_alerts_merchant_id ON alerts(merchant_id);
CREATE INDEX IF NOT EXISTS idx_alerts_severity    ON alerts(severity);
```

Column types mirror the entity fields exactly (see table above). `IF NOT EXISTS` makes it safe on any
DB where an earlier `ddl-auto` run already created some columns. Two indexes added:
`idx_alerts_merchant_id` (merchant-scoped alert filtering + the multi-asset bridge) and
`idx_alerts_severity` (the V109 report seed filters/aggregates on `a.severity`).

### Correction to the original TODO note
The W14-1 note speculated `disposed_by` might be a `User` FK. `Alert.java:74-75` confirms it is a
plain `String` (investigator name), so the column is `VARCHAR(255)`, **not** a FK.

### Verification performed
- Column DDL types matched against `Alert.java` field types — exact.
- Flyway version uniqueness re-checked — no duplicate version numbers; V162 is the next free slot
  after your V159/V160/V161.
- Index names checked — `idx_alerts_merchant_id`/`idx_alerts_severity` not defined by any other
  migration (and `IF NOT EXISTS` guards regardless).
- `Alert` is **not** `@Audited` (only `@Entity`, `Alert.java:11-17`), so no matching change is needed
  in `alerts_aud` (Envers validates aud tables only for audited entities).
- **Not run against the live DB on purpose:** `mvn flyway:migrate` would also apply your in-flight
  V159–V161, which may not be ready. Run it yourself when your migration batch is complete:
  `cd BACKEND && mvn flyway:migrate` (or let the app apply on next boot).

### Files changed
- **Added:** `BACKEND/src/main/resources/db/migration/V162__alerts_missing_columns.sql`
- No Java changed (the entity mapping was already correct; only the schema was missing).

---

## 2026-07-15 — Fix W17-1 / W17-2 (BLOCKER): `velocity_rules` & `risk_thresholds` missing `psp_id`

### What was wrong
Two limits entities map a `psp_id` column that `V9__limits_aml_management.sql` never created:

| Entity | `@Column` | Java type | Line | Table created (no psp_id) |
|---|---|---|---|---|
| `entity/limits/VelocityRule`   | `psp_id` | `Long` (nullable) | `VelocityRule.java:61`   | `V9:71-92` |
| `entity/limits/RiskThreshold`  | `psp_id` | `Long` (nullable) | `RiskThreshold.java:55`  | `V9:49-68` |

`psp_id` is null for global/super-admin rules and set for PSP-specific rules. No migration after V9
adds the column. **Impact:** identical to W14-1 — under `ddl-auto=validate` (prod/testenv) Hibernate
schema validation fails at startup ("missing column [psp_id] in table [velocity_rules]") and the app
won't boot on a clean/validated schema. Dev's `ddl-auto=create-drop` masked it.

### The fix
Added **`BACKEND/src/main/resources/db/migration/V165__limits_psp_id_columns.sql`** (V165 = next free
version after your in-flight V163/V164). Idempotent additive DDL:

```sql
ALTER TABLE velocity_rules   ADD COLUMN IF NOT EXISTS psp_id BIGINT;
ALTER TABLE risk_thresholds  ADD COLUMN IF NOT EXISTS psp_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_velocity_rules_psp_id  ON velocity_rules(psp_id);
CREATE INDEX IF NOT EXISTS idx_risk_thresholds_psp_id ON risk_thresholds(psp_id);
```

`Long` → `BIGINT`, nullable, matching the entity fields. Indexed because both tables are queried
PSP-scoped. Neither entity is `@Audited`, so no `*_aud` change needed.

### Verification performed
- Re-validated (twice, ~15 min apart) that no migration adds `psp_id` to either table and you hadn't
  added it — still missing at fix time.
- Version uniqueness + index-name uniqueness checked — no collisions; V165 is the next free slot after
  your V163/V164 (my earlier V162 was accepted without collision, confirming append-then-you-number-past).
- Not run against the live DB (same reason as V162 — your migration batch is in flight).

### Files changed
- **Added:** `BACKEND/src/main/resources/db/migration/V165__limits_psp_id_columns.sql`

---

## 2026-07-15 — Fix W16-1 (BLOCKER): usage-based billing produced $0 for every request

### What was wrong
The live metering path — `config/UsageTrackingFilter` → `service/psp/ApiUsageTrackingService.logRequest`
→ `BillingService.calculateUsageCost` — looks up `billing_rates` by the **exact** `service_type` string
(`BillingRateRepository.findDefaultRate` / `findActiveRateForPsp`). The filter emitted a URL-derived
vocabulary (`TRANSACTION_PROCESSING`, `SANCTIONS_SCREENING`, `AML_CHECK`, `SCREENING`, …) that had
**zero overlap** with the seeded `billing_rates` vocabulary (V149: `TRANSACTION_MONITORING`,
`AML_SCREENING`, `SANCTIONS_SCREENING_PERSON/_ORGANIZATION`, `KYC_VERIFICATION`,
`COMPLIANCE_CASE_CREATION`, …). So `calculateUsageCost` returned `BigDecimal.ZERO` for every request →
`api_usage_logs` rows saved `billable=false, cost=0` → `generateMonthlyInvoice`'s
`getUsageSummaryByService` (which filters `billable=true`) returned no rows → **$0, line-item-less invoices.**
Secondary breaks: currency hardcoded `"USD"`; generated invoices left `DRAFT` which `PaymentController`
refuses to accept for payment; the filter billed every GET (dashboard/list reads), over-counting.

### The fix (compiles clean; `mvn clean compile` exit 0)
1. **`config/UsageTrackingFilter.java`** — rewrote the URL→service map to emit the **canonical** seeded
   vocabulary; made it **method-aware** via a new `record ServiceMapping(String serviceType,
   Set<String> billableMethods)` so only work-performing verbs bill (POST screen/check/ingest/assess/
   generate/onboard/case-create/SAR/CBK) — dashboard/list GETs no longer over-count; added `/aml/detection`
   coverage; dropped self-billing of `/billing/*`; switched `ConcurrentHashMap`→`LinkedHashMap` for
   deterministic first-match ordering (person/organization before generic `/sanctions/screen`). New signature
   `resolveServiceType(path, method)`.
2. **`BACKEND/.../db/migration/V169__seed_remaining_billing_rates.sql`** — seeds the 4 canonical types V149
   lacked: `RISK_ASSESSMENT` (0.15), `REPORT_GENERATION` (0.25), `SAR_FILING` (3.00), `CBK_REPORTING` (1.50),
   psp_id NULL default, PER_REQUEST, idempotent (mirrors V149 style). The other filter types were already
   seeded in V149.
3. **`service/psp/BillingService.java`** — added `getEffectiveCurrency(pspId, serviceType)` (rate currency,
   USD fallback); changed `generateMonthlyInvoice` to issue invoices `SENT` (they are emailed on creation) so
   they are immediately payable.
4. **`service/psp/ApiUsageTrackingService.java`** — stamps the rate's currency (`getEffectiveCurrency`)
   instead of the hardcoded `"USD"`.

Net result: a metered request now resolves a non-zero rate → `billable=true` with the correct cost →
`getUsageSummaryByService` returns rows → invoice line items = `usage × rate` (recomputed against the
effective/tiered rate) → invoice is `SENT` and payable.

### Not done here (logged in TODO.md as W20-1 / W20-2 — need product/deploy decisions)
- `MeteringEventPublisher` is now redundant with the filter (wire it for point-of-work metering or delete it);
  Engine B (`BillingCalculationEngine`/tiers/subscriptions) is still disconnected from the invoice path.
- Payment env/config: `MPESA_CALLBACK_URL` is a placeholder default; invoice currency (USD) vs M-Pesa (KES)
  needs reconciliation for the KE market.
- I did **not** run `flyway:migrate` (your V166–V168 batch is in flight). Apply V169 when ready.

### Files changed
- **Modified:** `config/UsageTrackingFilter.java`, `service/psp/BillingService.java`,
  `service/psp/ApiUsageTrackingService.java`
- **Added:** `db/migration/V169__seed_remaining_billing_rates.sql`

---

## 2026-07-15 — Fix W20-3 (BLOCKER): `psps.branding_theme` missing migration

`entity/psp/Psp.java:101` maps `@Column(name="branding_theme", length=50)` (default `"default"`) but no
migration created the column (V6/V7 added other theme fields, not this). Under `ddl-auto=validate` the app
fails startup ("missing column [branding_theme] in table [psps]"). **Fix:** added
`db/migration/V170__psps_branding_theme_column.sql` →
`ALTER TABLE psps ADD COLUMN IF NOT EXISTS branding_theme VARCHAR(50) DEFAULT 'default';` (idempotent).

### Files changed
- **Added:** `BACKEND/src/main/resources/db/migration/V170__psps_branding_theme_column.sql`

---

## 2026-07-15 — Fix W22-1 (BLOCKER): aml-microservice won't boot (duplicate YAML key)

### What was wrong
`aml-microservice/src/main/resources/application.yml` had **two top-level `aml:` keys** — one at line 12
(`internal-auth-key`) and another at line 23 (`risk:`). Spring Boot's `OriginTrackedYamlLoader` sets
`allowDuplicateKeys(false)`, so SnakeYAML throws `found duplicate key aml` and the application context
fails to load — **the microservice does not start** with the shipped config (the duplicate is present in
`target/classes/application.yml` too). Even if duplicates were tolerated, the second `aml:` block would
replace the first and silently drop `aml.internal-auth-key`, disabling `InternalAuthFilter` (the shared-key
guard on `/internal/**`).

### The fix
Merged both into a **single** `aml:` block with `internal-auth-key` and `risk:` as siblings, and left
`sanctions:` as its own top-level block. Verified: no duplicate top-level keys, YAML parses, and both
`aml.internal-auth-key` and `aml.risk.*` resolve. No behavior change beyond making the service boot and
keeping the auth key wired.

### Files changed
- **Modified:** `aml-microservice/src/main/resources/application.yml`

---

## 2026-07-16 — Fix W23-1 (BLOCKER): `merchants` table missing `kra_pin` + `cr12_number`

### What was wrong
`entity/merchant/Merchant.java` maps two **real persistent** `@Column`s (Phase 29 "Kenyan
Specific Fields") that **no Flyway migration ever created** on the `merchants` table:

| Entity field (`Merchant.java`) | `@Column` name | Java type | Line |
|---|---|---|---|
| `kraPin`     | `kra_pin`     | `String` length=50  (Kenya Revenue Authority PIN)      | `Merchant.java:46-47` |
| `cr12Number` | `cr12_number` | `String` length=100 (CR12 company ownership cert ref)  | `Merchant.java:49-50` |

They are fully wired — constructor arg (`Merchant.java:165,180-181`), getters/setters
(`261-273`), and builder (`527-528,591-597,774-775`) all reference them; they are **not**
`@Transient`. The `merchants` table is created in `V2__sanctions_screening_schema.sql` and
extended by later migrations, but none of them ever add these two columns.

**Impact:** identical boot-fatal class to W14-1/W17-1/W20-3. Production/testenv set
`spring.jpa.hibernate.ddl-auto=validate`; on a clean deploy Hibernate validates the mapping
against the live schema at startup and **aborts** with
`Schema-validation: missing column [kra_pin] in table [merchants]`. Dev masked it via
`create-drop`/`update`.

### The fix
Added **`BACKEND/src/main/resources/db/migration/V185__merchants_kenyan_fields.sql`**. (Originally
drafted as V175, but you were appending migrations for the virtual-asset / wallet-screening / VASP
build-out at the same time and twice took the next number this fix landed on — V175
(`transaction_wallet_screening_evidence`) then V178 (`vasp_screening_report`). Flyway aborts on
duplicate versions, so rather than keep chasing your fast-moving tail this orthogonal merchants fix
was placed at **V185**, gapped above the active frontier. Flyway allows version gaps and applies in
order, so this is safe; renumber only if your sequence ever reaches V185.)
Idempotent additive `ALTER TABLE`:

```sql
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS kra_pin VARCHAR(50);
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS cr12_number VARCHAR(100);
```

Types mirror the entity exactly (length 50 / 100 → `VARCHAR(50)` / `VARCHAR(100)`). `Merchant`
is **not** `@Audited` (no Envers on this entity), so no `merchants_aud` columns are required.
Both fields are nullable in the entity (no `nullable=false`), so existing rows get `NULL`.

### Verification
- Confirmed zero migrations reference `kra_pin`/`cr12_number` before this change (`grep -ril`).
- Confirmed `Merchant.java` still maps them as real columns during the pre-fix wait window
  (user's V171–V174 were unrelated virtual-asset work; `merchants` untouched).
- Confirmed no V175 version collision.

### Files changed
- **Added:** `BACKEND/src/main/resources/db/migration/V185__merchants_kenyan_fields.sql`

---

## 2026-07-16 — Fix W25-1 & W25-2 (BLOCKERS): PSP self-service billing shows plan/cost (billing-$0 final mile)

### What was wrong
After the backend billing metering fix, the PSP's own self-service billing tab still could not display
its cost/plan because the React component read DTO fields that don't exist on the backend responses:

**W25-1 — "Current Plan" card crashed.** `FRONTEND/src/pages/Psps/tabs/BillingTab.tsx` typed the
subscription with a **nested** `tier: {name, monthlyFee, currency, includedChecks}` and rendered
`subscription.tier.name` etc. The real payload `SubscriptionResponse` is **flat** (`tierName`,
`monthlyFeeUsd`, `billingCurrency`, `contractStart`, `contractEnd`, `trialEndsAt`) with **no `tier`
object — so `subscription.tier` was `undefined` and `subscription.tier.name` threw a `TypeError` during
render whenever a PSP actually had a subscription.

**W25-2 — "Current Month Usage" cost rendered `$NaN`.** The component read `usage.estimatedCost`,
`usage.currency`, `usage.periodStart/periodEnd`, and breakdown `line.cost`/`line.requestCount`. The real
`UsageSummaryResponse` returns `totalCostUsd`, `period`, and breakdown `costUsd`/`count` — none of the
names the UI used — so the PSP's real month-to-date cost (the whole point of the billing fix) showed as
`$NaN` and the per-service cost column as `NaN`.

### The fix
**Backend — `BACKEND/src/main/java/com/posgateway/aml/dto/billing/SubscriptionResponse.java`:**
added an `includedChecks` field (Integer) so the plan card's "Included Checks / month" has a real source.
Populated in `from(Subscription s)` from the already-EAGER `s.getPricingTier().getIncludedChecks()`
(`PricingTier.java:169`). Added getter/setter. No stub — real tier data.

**Frontend — `FRONTEND/src/pages/Psps/tabs/BillingTab.tsx`:**
- Flattened the `Subscription` TS interface to match `SubscriptionResponse` (`subscriptionId`, `tierCode`,
  `tierName`, `monthlyFeeUsd`, `billingCurrency`, `includedChecks`, `contractStart`, `contractEnd`,
  `trialEndsAt`); removed the now-unused `PricingTier` interface.
- Corrected `UsageLineItem` (`count`, `costUsd`) and `CurrentUsage` (`period`, `totalCostUsd`) interfaces.
- Remapped every render site:
  - plan card: `tier.name`→`tierName` (×2), `tier.monthlyFee`→`monthlyFeeUsd`, `tier.currency`→
    `billingCurrency` (×2), `tier.includedChecks`→`includedChecks`, `trialEndDate`→`trialEndsAt` (×2),
    `startDate`→`contractStart`, `endDate`→`contractEnd` (×2).
  - usage card: `estimatedCost`/`currency`→`totalCostUsd`/`"USD"`, `periodStart`/`periodEnd`→`period`,
    `line.requestCount`→`line.count`, `line.cost`→`line.costUsd`.

### Verification
- Frontend `npx tsc --noEmit` — exit 0, no errors (whole project).
- Backend `mvn -q -o clean compile -DskipTests` — exit 0.
- Grep confirmed zero remaining stale field references (`.tier.`, `.startDate`, `.estimatedCost`,
  `line.cost`, etc.) in `BillingTab.tsx`.
- Server-side tenant isolation unchanged: `/subscriptions/psp/{pspId}` and `/billing/usage/{pspId}/current`
  still enforce `currentUser.getPsp()`, so a PSP only ever sees its own plan/usage.

### Files changed
- **Modified:** `BACKEND/src/main/java/com/posgateway/aml/dto/billing/SubscriptionResponse.java`
- **Modified:** `FRONTEND/src/pages/Psps/tabs/BillingTab.tsx`

---

## 2026-07-16 — Fix W27-1 / W20-7 (BLOCKER): 7 PSP org-config tabs broken by `/cbk/` path mismatch

### What was wrong
The PSP configuration page's 7 CBK sub-resource tabs (Directors, Shareholders, Trustees, Senior
Management, Products, Trust Accounts, Tariffs) could neither list, add, nor delete records — every
request 404'd — because the frontend paths did not match the backend controllers.

Backend: each CBK sub-resource is mapped under `/psps/{pspId}/cbk/{entity}` — e.g.
`controller/psp/cbk/PspDirectorController.java:24` = `@RequestMapping("/psps/{pspId}/cbk/directors")`,
with `GET`/`POST` on the collection and `DELETE /{id}` (`:64,:82,:140`); 9 sibling controllers likewise.

Frontend, before the fix:
- **Reads** — `PspConfigPage.tsx:48` did `apiClient.get('psps/${pspId}/${entity}')` → `psps/1/directors`
  (no `cbk` segment) → 404. No `.catch`, so the tab just rendered "No records found."
- **Writes** — `components/Common/PspListCrud.tsx` built `fetch('/api/v1/${apiPath}/${pspId}')` where the
  7 tabs passed `apiPath="psps/{entity}"`, i.e. POST `/api/v1/psps/directors/1` and DELETE
  `/api/v1/psps/directors/1/{id}` — pspId in the wrong position and no `cbk` → 404. (The earlier audit
  note that "writes correctly target /cbk/" was wrong; both sides were broken.)
- **Silent fake success** — `PspListCrud` used raw `fetch` with no `res.ok` check, so a 404 fell through
  to the success toast ("Added successfully.") even though nothing was saved (the W20-7a issue).

### The fix (9 files, frontend `tsc --noEmit` clean)
- **`FRONTEND/src/components/Common/PspListCrud.tsx`**: centralized the URL in a helper
  `cbkBase(apiPath, pspId) = '/api/v1/psps/${pspId}/cbk/${apiPath}'`; POST now hits `cbkBase(...)`,
  DELETE `cbkBase(...)/${id}`. Added `if (!res.ok) throw new Error('HTTP ' + res.status)` to both
  `handleAdd` and `handleDelete` so a failed call shows the error toast instead of a false success.
  `apiPath` is now the bare entity segment.
- **7 tab files** (`DirectorsTab`, `ShareholdersTab`, `TrusteesTab`, `SeniorManagementTab`, `ProductsTab`,
  `TrustAccountsTab`, `TariffsTab`): changed `apiPath="psps/{entity}"` → `apiPath="{entity}"`.
- **`FRONTEND/src/pages/Psps/PspConfigPage.tsx:48`**: read path → `psps/${pspId}/cbk/${LIST_QUERIES[listKey]}`.

Result: list, add, and delete on all 7 tabs now hit the real `/psps/{pspId}/cbk/{entity}` routes, and a
genuine failure surfaces an error instead of a fake success. Verified the trustees route exists
(`PspTrusteeController.java:24`) and that `PspListCrud` has exactly these 7 consumers (safe to change centrally).

### Verification
- `npx tsc --noEmit` — exit 0, no errors.
- Confirmed all 10 backend CBK controllers use the `/psps/{pspId}/cbk/...` base; matched each of the 7
  entity segments the UI sends.

### Files changed
- **Modified:** `FRONTEND/src/components/Common/PspListCrud.tsx`
- **Modified:** `FRONTEND/src/pages/Psps/PspConfigPage.tsx`
- **Modified:** `FRONTEND/src/pages/Psps/tabs/{Directors,Shareholders,Trustees,SeniorManagement,Products,TrustAccounts,Tariffs}Tab.tsx`

---

## 2026-07-16 — Fix W38-2 (compliance BLOCKER): sanctions watchlist empty-list safeguard

### What was wrong
The sanctions dataset is loaded into the aml-microservice Aerospike set by
`service/download/SanctionsListDownloadService` (daily OpenSanctions download → `POST /internal/v1/sanctions/ingest`),
but that service is **disabled by default** (`@Value("${sanctions.download.enabled:false}")`, early-returns when false)
and requires `sanctions.opensanctions.url` (no default). So if a deployment never enables it, the watchlist stays
**empty** and **every sanctions screen silently returns "no match"** — an invisible, worst-case AML failure (a core
compliance control disabled with zero signal). Nothing surfaced the empty-list condition.

### The fix (code only — no deployment config touched)
Turned the silent failure into a **loud, monitorable, fail-closable** condition using the existing
`SanctionsCountClient.getCount()` (→ microservice `GET /internal/v1/sanctions/count`, returns record count or -1):

- **Added `config/health/SanctionsDataHealthIndicator.java`** (`@Component("sanctionsData")`, Spring Actuator
  `HealthIndicator`): `/actuator/health` now reports `sanctionsData` = UP (with `watchlistRecords`) when count>0,
  **DOWN "EMPTY"** when count==0, DOWN "UNAVAILABLE" when count<0. Gives monitoring/alerting a runtime signal.
- **Added `config/startup/SanctionsDataStartupCheck.java`** (`ApplicationListener<ApplicationReadyEvent>`, mirrors
  `EnvVarStartupValidator`): at boot, logs a single INFO line when the watchlist is loaded; a **loud multi-line ERROR
  banner** when it is EMPTY (count==0, microservice reachable); and WARN-only when the count is unavailable (count<0,
  likely a startup race). Under the `production` profile it **fails closed by default** (throws to abort boot) so an
  operator cannot unknowingly run the AML platform with no sanctions data — gated by
  `sanctions.startup.fail-closed-on-empty` (default = true in prod, false elsewhere) so dev/test still boot when the
  microservice isn't up.

Both are read-only w.r.t. screening — they add visibility/guardrails, never change match behaviour. Deployment config
(`sanctions.download.enabled=true` + `sanctions.opensanctions.url`) is still the operator's to set; the fix guarantees
a missing/empty watchlist is now impossible to miss (and blocks prod boot rather than passing every screen silently).

### Verification
- `mvn -q -o clean compile -DskipTests` — exit 0.
- Uses only the existing `SanctionsCountClient` (@Component) + the microservice `/internal/v1/sanctions/count` endpoint
  (`SanctionsController:57` → `SanctionsService.count()`), both confirmed present.

### Files changed
- **Added:** `BACKEND/src/main/java/com/posgateway/aml/config/health/SanctionsDataHealthIndicator.java`
- **Added:** `BACKEND/src/main/java/com/posgateway/aml/config/startup/SanctionsDataStartupCheck.java`
