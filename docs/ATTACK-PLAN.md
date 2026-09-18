# Hokeka AML — Full Attack Plan
**Session:** 2026-05-14  
**Goal:** Zero stubs, zero mock data, zero placeholders. Everything wired. Aerospike expanded as a first-class database.

---

## Skills in Use This Session

| Domain | Skill | Source |
|--------|-------|--------|
| UI/UX | ui-ux-pro-max | nextlevelbuilder/ui-ux-pro-max-skill @ skills.sh |
| Frontend | frontend-design | anthropics/skills @ skills.sh |
| Planning | writing-plans | obra/superpowers @ skills.sh |
| Audit | audit | pbakaus/impeccable @ skills.sh |
| Analysis | brainstorming | obra/superpowers @ skills.sh |

---

## Architecture Direction (Updated)

- **Aerospike is ACTIVE and EXPANDING** — not being removed. It stays in aml-microservice and will be expanded for customer risk profiles, velocity counters, and hot-path caching in BACKEND via the HTTP delegation pattern.
- Two environments only: **test** and **live (prod)**. No preprod.
- V2 hot/warm/cold split is the target architecture.
- CLAUDE.md must be updated to remove the "Aerospike is being removed" note.

---

## PHASE 1 — Backend Stubs → Real Implementations

### P1-A: FraudDetectionService (HIGH)
**File:** `BACKEND/src/main/java/com/posgateway/aml/service/FraudDetectionService.java`
- Line 91: `assessDeviceRisk()` — placeholder, returns 0.0
- Line 107: `assessIpRisk()` — placeholder, returns 0.0
- Line 126: `assessVelocityRisk()` — placeholder, returns 0.0

**What to build:**
- `assessDeviceRisk()`: cross-reference device fingerprint against `device_fingerprints` table; score by: new device (high risk), device seen across multiple customers (mule flag), device blacklisted
- `assessIpRisk()`: check IP against `ip_reputation` table; score by: Tor exit node, data center ASN, country risk, velocity of IPs per customer
- `assessVelocityRisk()`: query Redis velocity counters (`aml:customer:{id}:features`) — txn count in 1h/24h/7d windows; apply velocity rule thresholds from `velocity_rules` table

### P1-B: RiskScoringService (HIGH)
**File:** `BACKEND/src/main/java/com/posgateway/aml/service/risk/RiskScoringService.java`
- Line 164, 221–226: `calculateCra()` returns default value instead of real CRA

**What to build:**
- Implement CRA using weighted scoring: customer type weight + transaction volume weight + geography weight + PEP/sanction status weight + historical alert count
- Load weights from `risk_configuration` table (already exists)
- Store result in `customer_risk_profiles` and update Aerospike cache via aml-microservice HTTP

### P1-C: MonitoringMetricsService (MEDIUM)
**File:** `BACKEND/src/main/java/com/posgateway/aml/service/MonitoringMetricsService.java`
- Line 110: AUC hardcoded to `0.5`
- Line 140: `computeAverageLatency()` returns `null`
- Line 159: Drift baseline hardcoded to `0.5`

**What to build:**
- AUC: query `alert_outcomes` table (true positives vs false positives) over rolling 30-day window; compute trapezoidal approximation
- Average latency: read from Micrometer `http.server.requests` timer (already instrumented via PrometheusMetricsService)
- Drift baseline: store rolling 30-day baseline in Redis (`aml:metrics:baseline`); compare current distribution to stored baseline

### P1-D: FrcReportingService / goAML (HIGH)
**File:** `BACKEND/src/main/java/com/posgateway/aml/service/compliance/FrcReportingService.java`
- Lines 20–65: All three report types (STR, CTR, Annual) are stub XML strings

**What to build:**
- Add JAXB bindings for goAML 3.0 schema (STR and CTR report structures)
- `generateStr()`: pull case data + transaction data + reporter metadata from DB; marshal to valid goAML STR XML
- `generateCtr()`: aggregate daily cash transactions above threshold per customer; marshal to CTR XML
- `generateAnnualComplianceReport()`: aggregate full-year stats (STR count, CTR count, alert counts, resolution rates) and produce structured XML/PDF

### P1-E: WorkflowAutomationService (MEDIUM)
**File:** `BACKEND/src/main/java/com/posgateway/aml/service/workflow/WorkflowAutomationService.java`
- Lines 52–68: Auto-approval logic commented out after schema change

**What to build:**
- Restore auto-approval: cases with risk score < LOW_THRESHOLD and no sanctions hits and no open manual review flags → auto-approve after 24h
- Use new schema: query `cases` by `risk_score`, `sanctions_hits`, `review_flags` columns
- Emit `cases.events` Kafka event on auto-approval

### P1-F: RiskAssessmentService (LOW)
**File:** `BACKEND/src/main/java/com/posgateway/aml/service/RiskAssessmentService.java`
- Line 85: `isThirdParty` always `false`

**What to build:**
- Name-match counterparty against `merchants` table using fuzzy match (Levenshtein or Jaro-Winkler)
- Flag as third-party if no match found and counterparty account is external

### P1-G: MerchantOnboardingService (LOW)
**File:** `BACKEND/src/main/java/com/posgateway/aml/service/merchant/MerchantOnboardingService.java`
- Line 309: Daily limit hardcoded to 10000
- Line 284: PSP context is a workaround

**What to build:**
- Read default daily limit from PSP config (`psp_configurations.default_merchant_daily_limit`)
- Resolve PSP from SecurityContext principal (already available via `EntityUserDetails`)

### P1-H: PrometheusMetricsService (LOW)
**File:** `BACKEND/src/main/java/com/posgateway/aml/service/PrometheusMetricsService.java`
- Line 380: Active connections gauge hardcoded to `0.0`

**What to build:**
- Read active connection count from HikariCP pool MBean (`HikariPool.ActiveConnections`) via MeterRegistry
- Fall back to DataSource connection count if HikariCP not present

---

## PHASE 2 — Frontend Placeholders → Real Features

### P2-A: Cases — Timeline View (HIGH)
**File:** `FRONTEND/src/pages/Cases/CasesPage.tsx` (lines 51–70)
- `<ComingSoonPlaceholder>` for Timeline View tab

**What to build:**
- Timeline component: chronological list of all events for visible cases (created, alert linked, manual review, status change, auto-approved)
- Data source: `GET /api/v1/cases/{id}/timeline` endpoint (needs backend endpoint too)
- Render as vertical timeline with MUI Timeline component; color-coded by event type

### P2-B: Cases — Network Graph View (HIGH)
**File:** `FRONTEND/src/pages/Cases/CasesPage.tsx` (lines 51–70)
- `<ComingSoonPlaceholder>` for Network Graph tab

**What to build:**
- Graph showing relationships: customer → transactions → merchants → counterparties → shared devices/IPs
- Use D3.js or react-force-graph (lightweight, already split into vendor-charts chunk)
- Data source: `GET /api/v1/cases/{id}/network` endpoint (needs backend endpoint too)
- Nodes color-coded by risk score; edges by transaction count

### P2-C: LoginPage — Password Reset Flow (MEDIUM)
**File:** `FRONTEND/src/pages/Auth/LoginPage.tsx` (line 57)
- `// await yourApi.sendResetEmail(resetEmail)` commented out

**What to build:**
- Wire to `POST /api/v1/auth/forgot-password` (verify endpoint exists in backend)
- Show success/error toast; disable button during request

---

## PHASE 3 — Aerospike Expansion

### P3-A: Customer Risk Profile Cache (HIGH)
**Current:** Risk profiles only in Postgres  
**Target:** After every CRA calculation, write result to Aerospike via aml-microservice  
- New endpoint in aml-microservice: `PUT /internal/cache/risk-profile/{customerId}`
- BACKEND calls this after `RiskScoringService.calculateCra()`
- Hot-path rule evaluation reads from Aerospike instead of Postgres

### P3-B: Velocity Counters in Aerospike (HIGH)
**Current:** Velocity counters in Redis sorted sets  
**Target:** Mirror velocity counters to Aerospike for sub-millisecond reads on sanctions/velocity rules  
- aml-microservice writes velocity events to Aerospike namespace `velocity`
- BACKEND reads via aml-microservice `/internal/velocity/{customerId}` endpoint
- Aerospike TTL matches the largest velocity window (7 days)

### P3-C: Device/IP Reputation Cache in Aerospike (MEDIUM)
**Current:** Device/IP lookups go straight to Postgres (when implemented)  
**Target:** Cache device reputation scores in Aerospike (namespace: `device`, set: `reputation`)  
- TTL: 1 hour for clean devices, 24 hours for flagged
- aml-microservice exposes `/internal/device/{fingerprint}` and `/internal/ip/{address}` endpoints
- FraudDetectionService calls these instead of direct DB

### P3-D: Expand Aerospike Namespaces (MEDIUM)
**Current:** aml-microservice uses default namespace  
**Target:** Structured namespace layout:
```
namespace: aml_cache
  set: sanctions       — sanctions screening results (TTL: 1h)
  set: customer_risk   — CRA scores (TTL: 30min)
  set: velocity        — velocity counters (TTL: 7d)
  set: device          — device reputation (TTL: 1h/24h)
  set: ip_reputation   — IP scores (TTL: 30min)
```

---

## PHASE 4 — Architecture & Config Cleanup

### P4-A: Update CLAUDE.md
- Remove "Aerospike is being removed" note
- Add Aerospike as active database with its role (aml-microservice, HTTP delegation pattern)
- Document new Aerospike namespaces

### P4-B: Remove Compatibility Shims
**File:** `BACKEND/.../repository/AerospikeMetricsRepository.java`
- This is a no-op shim — after P3 work, replace with real metrics via Micrometer or remove entirely

### P4-C: Wire AerospikeMetricsRepository to Real Data
- After P3 work: `load30DayMetrics()` should call aml-microservice for cached metric summaries
- `incrementCounters()` should emit Kafka event instead of being a no-op

---

## Execution Order

| Priority | Phase | Item | Effort |
|----------|-------|------|--------|
| 1 | P1-A | FraudDetectionService — device/IP/velocity | M (4h) |
| 2 | P1-B | RiskScoringService — CRA calculation | M (3h) |
| 3 | P3-A | Aerospike — customer risk profile cache | M (3h) |
| 4 | P3-B | Aerospike — velocity counters | M (3h) |
| 5 | P2-A | Cases Timeline View | L (6h) |
| 6 | P2-B | Cases Network Graph | L (8h) |
| 7 | P1-D | FrcReportingService — goAML XML | L (8h) |
| 8 | P1-C | MonitoringMetricsService — AUC/latency/drift | S (2h) |
| 9 | P1-E | WorkflowAutomationService — auto-approval | S (2h) |
| 10 | P3-C | Aerospike — device/IP cache | S (2h) |
| 11 | P3-D | Aerospike namespace restructure | S (2h) |
| 12 | P2-C | LoginPage — password reset | S (1h) |
| 13 | P1-F | RiskAssessmentService — third-party detection | S (1h) |
| 14 | P1-G | MerchantOnboardingService — configurable limits | S (1h) |
| 15 | P1-H | PrometheusMetricsService — real connection count | XS (30m) |
| 16 | P4-A | Update CLAUDE.md | XS (15m) |
| 17 | P4-B/C | Remove/upgrade shims | S (1h) |

**Total estimated effort:** ~50 engineering hours across backend, frontend, and infrastructure.

---

## Skills Assignment Per Phase

| Phase | Skill | When |
|-------|-------|------|
| P1 Backend stubs | writing-plans + backend agent | Per item |
| P2 Frontend | ui-ux-pro-max + frontend-design + audit | Per component |
| P3 Aerospike | writing-plans + DB agent | Per integration point |
| P4 Cleanup | audit | Final pass |
