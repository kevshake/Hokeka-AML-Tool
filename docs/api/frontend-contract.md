# Frontend API Contract Inventory

**Issue:** HOK-132 (parent HOK-130). Single source of truth listing every backend endpoint the frontend currently calls.

**Frontend audited:** `FRONTEND/` (React + MUI dashboard). The marketing site under `website/` makes no API calls.

**Base URL convention:** All paths in this document are written relative to the Spring Boot context path `/api/v1` (`server.servlet.context-path=/api/v1`). The frontend `apiClient` (`FRONTEND/src/lib/apiClient.ts`) resolves a relative endpoint via `${VITE_API_URL}${API_VERSION}/${endpoint}` where `API_VERSION = "/api/v1"`.

So when a row says `Path: cases/{id}`, the on-the-wire URL is `/api/v1/cases/{id}`.

## Conventions

- **Auth:** all calls go through `apiClient` with `credentials: "include"` (session cookie). Direct `fetch()` calls in the user-management pages also rely on the cookie. There is no Bearer token in normal flows; `token` in `localStorage` is a placeholder string `"session"`. The exception is `useDownloadReport` which forwards `Authorization: Bearer ${localStorage.token}` — likely dead code.
- **Tenancy header:** `apiClient` automatically sets `X-PSP-ID: <int>` from `sessionStorage._psp` on every request. `0` = Super Admin. The header is the only PSP signal; never put `pspId` in URLs or request bodies unless explicitly part of the resource.
- **Content type:** `application/json` on every JSON request. Reports download is `application/octet-stream`.
- **Error shape:** `{ status, error, errorCode?, message, details?, traceId? }` — see `ApiClient.request`.
- **Pagination:** Spring `Page<T>` shape — `{ content, totalElements, totalPages, size, number }` — used wherever the table says "paginated".

Status legend in the tables below:
- ✅ implemented in the current Spring Boot service at the path the frontend calls
- ⚠️ partially implemented — exists on the backend but at a different path/verb, or has a naming/path mismatch
- ❌ not implemented anywhere in `BACKEND/`
- ❓ ambiguous — duplicate routes or unclear which controller wins

---

## 1. Authentication & session

Triggered by: login flow, signup page, all authenticated routes (session check on app load).

| Method | Path | Request | Response | Page / hook | Backend |
|--------|------|---------|----------|-------------|---------|
| POST | `auth/login` | `{ username: string, password: string }` | `{ user: User, token?: string, redirectUrl?: string }` | `contexts/AuthContext.tsx` `login()` | ✅ `AuthenticationController.login` |
| GET | `auth/me` | — | `User` (with `psp`, `role.permissions[]`, `pspId`) | `AuthContext.checkSession` | ✅ `AuthenticationController.me` |
| POST | `auth/logout` | — | 200 | `AuthContext.logout` | ✅ `AuthenticationController.logout` |
| POST | `auth/register` | `{ username, email, firstName, lastName, password }` | `{ message? }` | `pages/Auth/SignupPage.tsx` | ❌ **MISSING** — no public registration endpoint exists. Either remove the signup page or add a controller. |

**Notes / questions**
- Auth calls in `AuthContext` and `SignupPage` use raw `fetch("/api/v1/auth/...")` and bypass `apiClient`. They do **not** send `X-PSP-ID`. That is correct for `login` / `register` (no session yet) but inconsistent for `logout` / `me` — should be migrated to `apiClient` once the contract is wired up. Inventory only — no code changes in this issue.
- `login` response: the frontend reads `data.user`, `data.token`, `data.redirectUrl`. Confirm the backend payload matches all three fields.

---

## 2. Users, roles, PSPs

Triggered by: Settings → Users tab, Settings → Roles tab, Profile, every page that gates by role.

| Method | Path | Request | Response | Page / hook | Backend |
|--------|------|---------|----------|-------------|---------|
| GET | `users?page&size&pspId` | — | `Page<User>` | `useUsers` (queries.ts), Users page | ✅ `UserController.list` |
| GET | `users/me` | — | `User` | `useCurrentUser` | ✅ `UserController.me` |
| PUT | `users/me` | `Partial<User>` (firstName, lastName, email) | `User` | `pages/Profile/ProfilePage.tsx` | ✅ `UserController` `@PutMapping("/me")` |
| PUT | `users/me/password` | `{ currentPassword, newPassword }` (shape inferred — confirm) | 200 | `ProfilePage.tsx` | ✅ `UserController` `@PutMapping("/me/password")` |
| POST | `users` | `{ username, email, firstName, lastName, password, roleId, pspId?, enabled }` | `User` | `pages/Users/UsersTab.tsx` (raw fetch) | ✅ `UserController.create` |
| PUT | `users/{id}` | `{ email, firstName, lastName, roleId, pspId?, enabled, password? }` | `User` | `UsersTab.tsx` (raw fetch) | ✅ `UserController.update` |
| DELETE | `users/{id}` | — | 204 | `UsersTab.tsx` (raw fetch) | ✅ `UserController.delete` |
| PATCH | `users/{id}/toggle` | `{ enabled: boolean }` | `User` | `UsersTab.tsx` (raw fetch) | ✅ `UserController.toggle` |
| GET | `roles` | — | `Role[]` | `useRoles` and `RolesTab.tsx` (raw fetch) | ✅ `RoleController.list` (also exposed at `auth/roles` — duplicate ❓) |
| POST | `roles` | `{ name, description, pspId?: number\|null, permissions: Permission[] }` | `Role` | `RolesTab.tsx` (raw fetch) | ✅ `RoleController.create` |
| PUT | `roles/{id}` | same as POST | `Role` | `RolesTab.tsx` (raw fetch) | ✅ `RoleController.update` |
| DELETE | `roles/{id}` | — | 204 | `RolesTab.tsx` (raw fetch) | ✅ `RoleController.delete` |
| GET | `psps` | — | `Psp[]` | `useAllPsps` | ❓ `PspController` is mounted at `/psps` but the inventory grep did not turn up a top-level `@GetMapping`. Confirm the list endpoint exists; otherwise ❌. |
| GET | `psps/{pspId}` | — | `Psp` | `usePsp` | ❓ Confirm `PspController.get`. |
| GET | `psp` (singular) | — | `Psp[]` | `RolesTab.tsx` line 67 (raw fetch) | ❌ **TYPO** — there is no `/psp` controller, only `/psps`. The dropdown in Roles is fetching a 404. Flag for the wire-up issue. |

---

## 3. Dashboard (home page)

Triggered by: `pages/Dashboard/*`.

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `reporting/summary` | — | `DashboardStats` | `useDashboardStats` | ✅ `ReportingController.summary` |
| GET | `dashboard/stats` | — | object | `useDashboardGlobalStats` | ✅ `DashboardController.stats` |
| GET | `dashboard/transaction-volume?days={n}` | `days` query, default 30 | `{ labels, values }` (confirm) | `useTransactionVolume` | ✅ `DashboardController.transactionVolume` |
| GET | `dashboard/risk-distribution` | — | object | `useRiskDistribution` | ✅ `DashboardController.riskDistribution` |
| GET | `dashboard/live-alerts?limit={n}` | `limit`, default 5 | `Alert[]` | `useLiveAlerts` | ✅ `DashboardController.liveAlerts` |
| GET | `dashboard/recent-transactions?limit={n}` | `limit`, default 5 | `Transaction[]` | `useRecentTransactions` | ✅ `DashboardController.recentTransactions` |

---

## 4. Cases & SAR

Triggered by: `pages/Cases/*`, `pages/RegulatoryReports/*`.

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `compliance/cases?page&size&status` | — | `Page<Case>` | `useCases` | ✅ `ComplianceCaseController.list` |
| GET | `compliance/cases/{id}` | — | `Case` | `useCase` | ✅ `ComplianceCaseController.get` |
| POST | `compliance/cases/workflow/create` | `{ caseReference, description, priority, creatorUserId? }` | `Case` | `useCreateCase` | ✅ `ComplianceCaseWorkflowController.create` |
| GET | `cases/{caseId}/timeline` | — | timeline events | `useCaseTimeline` | ❓ **AMBIGUOUS** — both `CaseManagementController` and `CaseWorkflowController` register `/cases/{id}/timeline`. Spring will fail to start in strict mode; confirm which one is wired. |
| GET | `cases/{caseId}/network` | — | network graph | `useCaseNetwork` | ✅ `CaseNetworkController` (also `CaseWorkflowController.graph` exposes `/cases/{id}/graph` — duplicate concept, different path) |
| GET | `compliance/sar?status={status?}` | — | `SarReport[]` | `useSarReports` | ✅ `ComplianceReportingController.list` |
| POST | `compliance/sar/workflow/create` | `{ sarReference, narrative, suspiciousActivityType, jurisdiction?, sarType?, creatorUserId }` | `SarReport` | `useCreateSar` | ✅ `SarWorkflowController.create` |

---

## 5. Alerts

Triggered by: `pages/Alerts/*`, dashboard live alerts.

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `alerts?page&size&status` | — | `Page<Alert>` | `useAlerts` | ✅ `AlertController.list` |
| PUT | `alerts/{id}/status` | `{ status: string }` | `Alert` | `useUpdateAlertStatus` | ⚠️ **PATH MISMATCH** — backend exposes `alerts/{id}/resolve` (PUT). Either rename the frontend call or add `/status` on the backend. The UI sends arbitrary status values, not just "resolved", so adding `/status` is probably correct. |

---

## 6. Transactions

Triggered by: `pages/TransactionMonitoring/*`, `pages/Merchants/*`.

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `transactions?page&size&pspId` | — | `Page<Transaction>` | `useTransactions` | ✅ `TransactionController.list` |
| GET | `monitoring/dashboard/stats` | — | object | `useMonitoringDashboardStats` (30s polling) | ✅ `TransactionMonitoringController.dashboardStats` |
| GET | `monitoring/risk-distribution` | — | `Record<string, number>` | `useMonitoringRiskDistribution` | ✅ |
| GET | `monitoring/risk-indicators` | — | `RiskIndicator[]` | `useMonitoringRiskIndicators` | ✅ |
| GET | `monitoring/recent-activity` | — | `Activity[]` | `useMonitoringRecentActivity` (30s polling) | ✅ |
| GET | `monitoring/transactions?page&size&riskLevel&decision` | — | `Page<Transaction>` | `useMonitoringTransactions` (30s polling) | ✅ |

---

## 7. Merchants & KYC documents

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `merchants?page&size` | — | `Page<Merchant>` | `useMerchants`, `KycDocumentsPage` | ✅ `MerchantController.list` |
| POST | `merchants` | `{ merchantId, businessName, mcc?, kycStatus?, contractStatus? }` | `Merchant` | `useCreateMerchant` | ✅ `MerchantController.create` |
| GET | `merchants/{merchantId}/documents` | — | `MerchantDocument[]` | `KycDocumentsPage` | ✅ `DocumentController` |

---

## 8. Audit logs

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `audit/logs?page&size&username&actionType&entityType&entityId&success&pspId&start&end` | — | `Page<AuditLog>` | `useAuditLogs` | ✅ `AuditLogController.list` |

---

## 9. Risk analytics

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `analytics/risk/heatmap/{type}` where `type ∈ {customer, merchant}` | — | `Record<string, number>` | `useRiskHeatmap` | ✅ `RiskAnalyticsController` (`heatmap/customer`, `heatmap/merchant`) |
| GET | `analytics/risk/trends?days={n}` | — | `{ labels?, data? }` | `useRiskTrends` | ✅ `RiskAnalyticsController.trends` |

---

## 10. Compliance calendar

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `compliance/calendar/upcoming` | — | `Deadline[]` | `useUpcomingDeadlines` | ✅ |
| GET | `compliance/calendar/overdue` | — | `Deadline[]` | `useOverdueDeadlines` | ✅ |
| POST | `compliance/calendar` | `{ title, description?, dueDate, deadlineType? }` | `Deadline` | `useCreateDeadline` | ⚠️ **PATH MISMATCH** — backend `POST` is at `compliance/calendar/deadlines`, not `compliance/calendar`. Either move the route or update the hook in the wire-up issue. |

---

## 11. Rules engine (AML rules + velocity + risk thresholds)

Triggered by: `pages/RulesGeneration/*`, `pages/LimitsAml/*`.

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `rules` | — | `AmlRule[]` | `useAmlRules` | ✅ `RulesController.list` |
| GET | `rules/{id}` | — | `AmlRule` | `useAmlRule` | ✅ |
| POST | `rules` | `AmlRule` | `AmlRule` | `useCreateAmlRule` | ✅ |
| PUT | `rules/{id}` | `AmlRule` | `AmlRule` | `useUpdateAmlRule` | ✅ |
| DELETE | `rules/{id}` | — | 204 | `useDeleteAmlRule` | ✅ |
| POST | `rules/{id}/enable` | — | `AmlRule` | `useEnableAmlRule` | ✅ |
| POST | `rules/{id}/disable` | — | `AmlRule` | `useDisableAmlRule` | ✅ |
| GET | `rules/{id}/effectiveness` | — | object | `useRuleEffectiveness` | ✅ |
| GET | `limits/velocity-rules` | — | `VelocityRule[]` | `useVelocityRules` | ✅ `LimitsManagementController` |
| POST | `limits/velocity-rules` | `VelocityRule` | `VelocityRule` | `useCreateVelocityRule` | ✅ |
| PUT | `limits/velocity-rules/{id}` | `VelocityRule` | `VelocityRule` | `useUpdateVelocityRule` | ✅ |
| DELETE | `limits/velocity-rules/{id}` | — | 204 | `useDeleteVelocityRule` | ✅ |
| GET | `limits/risk-thresholds` | — | `RiskThreshold[]` | `useRiskThresholds` | ✅ |
| POST | `limits/risk-thresholds` | `RiskThreshold` | `RiskThreshold` | `useCreateRiskThreshold` | ✅ |
| POST | `limits/aml` | `{ ... }` (see `pages/LimitsAml/LimitsAmlPage.tsx:15`) | object | inline `apiClient.post` | ❌ **MISSING** — `LimitsManagementController` does not expose `/limits/aml`. Flag for the wire-up issue: pick a real endpoint or remove the call. |

---

## 12. Reports Center

Triggered by: `pages/ReportsCenter/*`. **This entire block is the highest-risk area.** The frontend calls `reports/...` (relative to `/api/v1`), but the backend `ReportController` and `ReportDefinitionController` use `@RequestMapping("/api/reports")` and `@RequestMapping("/api/reports/definitions")`. Combined with the `server.servlet.context-path=/api/v1`, those resolve to `/api/v1/api/reports/...` — a double `/api` prefix. Every row below is at least ⚠️.

| Method | Path called by FE | Backend path actually exposed | Status |
|--------|-------------------|-------------------------------|--------|
| GET | `reports/definitions` | `/api/v1/api/reports/definitions` | ⚠️ path mismatch |
| GET | `reports/categories` | `/api/v1/api/reports/definitions/categories` | ⚠️ path mismatch |
| POST | `reports/preview` | `/api/v1/api/reports/preview` | ⚠️ |
| POST | `reports/chart` | `/api/v1/api/reports/chart` | ⚠️ |
| POST | `reports/generate` | `/api/v1/api/reports/generate` | ⚠️ |
| POST | `reports/export` | none | ❌ MISSING |
| POST | `reports/schedule` | `/api/v1/api/reports/schedule` (POST) | ⚠️ |
| GET | `reports/scheduled` | `/api/v1/api/reports/schedule` (GET) | ⚠️ different name |
| DELETE | `reports/schedule/{id}` | `/api/v1/api/reports/schedule/{scheduleId}` | ⚠️ |
| GET | `reports/history?page&size&reportId&status&startDate&endDate` | `/api/v1/api/reports/history` | ⚠️ |
| GET | `reports/history/{id}` | `/api/v1/api/reports/{id}` (no `/history/`) | ⚠️ |
| DELETE | `reports/history/{id}` | `/api/v1/api/reports/{id}` (no `/history/`) | ⚠️ |
| GET | `reports/progress/{id}` | none — closest is `/api/v1/api/reports/status/{executionId}` | ⚠️ rename or add `/progress/{id}` |
| GET (raw fetch) | `reports/download/{id}?format={fmt}` | `/api/v1/api/reports/download/{id}` | ⚠️ |

**Question for backend:** the cleanest fix is to drop the `@RequestMapping("/api/reports...")` prefix on the two report controllers (the context path already gives them `/api/v1`). That makes every row above ✅ in one step except `reports/export`, `reports/scheduled` (rename), `reports/history/{id}` (move under `/history`), and `reports/progress/{id}` (add or alias to `/status`).

---

## 13. Regulatory reports

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `reporting/regulatory/{TYPE}` (TYPE upper-cased: `CTR`, `LCTR`, `IFTR`) | — | report payload | `useRegulatoryReport` | ⚠️ **PATH MISMATCH** — backend `RegulatoryReportingController` is mounted at `/regulatory`, exposing `/regulatory/ctr`, `/regulatory/lctr`, `/regulatory/iftr` (lowercase). Frontend calls `/api/v1/reporting/regulatory/CTR` — wrong prefix and wrong case. Pick one shape and align. |

---

## 14. CBK reporting

Triggered by: `pages/ReportsCenter/components/CbkSubmissionPanel.tsx`.

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `compliance/cbk/reports?period={p}&from={d}&to={d}` | period ∈ daily/weekly/monthly/quarterly/semi-annual/annual; ISO dates | `{ content: CbkReportRow[], totalElements, totalPages }` | `useCbkReports` | ✅ `CbkReportController` |
| POST | `compliance/cbk/reports/submit` | `{ reportId, period, from, to, parameters? }` | `{ referenceNumber, status, submittedAt, message }` | `useCbkSubmit` | ✅ |

---

## 15. Sanctions screening

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| POST | `sanctions/screen` | `{ name: string }` | screening result | `pages/Screening/ScreeningPage.tsx` | ✅ `SanctionsScreeningController.screen` |

---

## 16. Messages (in-app inbox)

Triggered by: `components/Layout/Sidebar.tsx` (unread badge), `pages/Messages/*`.

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `messages` | — | `Message[]` | `MessagesPage` | ✅ `MessagesController.list` |
| PUT | `messages/{id}/read` | `{}` | 200 | `MessagesPage` | ✅ |
| GET | `messages/unread/count` | — | `{ count: number }` | `Sidebar` (catches errors → `{count:0}`) | ✅ |

---

## 17. Settings (PSP themes + system)

Triggered by: `pages/Settings/SettingsPage.tsx`.

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `settings/psps` | — | `Psp[]` | inline | ✅ `SettingsController` |
| GET | `settings/themes/presets` | — | `ThemePresets` | inline | ✅ |
| GET | `settings/psps/{id}/theme` | — | `PspTheme` | inline | ✅ |
| PUT | `settings/psps/{id}/theme` | `PspTheme` | `PspTheme` | inline | ✅ |
| GET | `settings/system` | — | `SystemSettings` | inline | ✅ |
| PUT | `settings/system` | `SystemSettings` | `SystemSettings` | inline | ✅ |

---

## 18. Grafana embedding

| Method | Path | Request | Response | Hook | Backend |
|--------|------|---------|----------|------|---------|
| GET | `grafana/dashboards` | — | `GrafanaDashboard[]` | `useGrafanaDashboards` | ✅ `GrafanaUserContextController` |

---

## Cross-cutting summary — what backend needs to ship

The wire-up issue (separate from this inventory) needs the following decisions before the dashboard is fully green:

1. **`auth/register`** — does Hokeka support self-service signup at all? If yes, build the endpoint; if no, remove `SignupPage` from the router.
2. **`/api/reports*` controllers** — drop the redundant `@RequestMapping("/api/reports")` prefix from `ReportController`, `ReportDefinitionController`, `RegulatorySubmissionController`. The context path already provides `/api/v1`.
3. **`reports/export`, `reports/progress/{id}`, `reports/scheduled`, `reports/history/{id}`** — add or alias these to match the frontend hook names. Cheapest path: add Spring controller methods, since the frontend hook surface is more user-facing.
4. **`alerts/{id}/status`** — replace the existing `/resolve` with a `/status` endpoint that accepts arbitrary status transitions, OR change the hook.
5. **`compliance/calendar` POST** — move to `compliance/calendar/deadlines` on the FE, or expose a top-level POST on the controller (deadlines is the only resource it creates today).
6. **`limits/aml` POST** — decide whether `LimitsAmlPage` should call an existing endpoint (probably `limits/risk-thresholds` or `limits/velocity-rules`) or whether a new aggregate endpoint is needed.
7. **`reporting/regulatory/{TYPE}`** — decide whether the canonical path is `/reporting/regulatory/ctr` or `/regulatory/ctr` and align both sides.
8. **`/psp` (singular)** — fix the typo in `RolesTab.tsx` (currently 404s) — frontend issue, not backend.
9. **PSP list/get** — confirm `GET /psps` and `GET /psps/{id}` exist in `PspController`. The grep didn't find a top-level `@GetMapping`. If missing, add them.
10. **Duplicate `cases/{id}/timeline`** — `CaseManagementController` and `CaseWorkflowController` both register it. Pick one and delete the other before Spring fails to start in strict mapping mode.

## Open questions for the backend developer

- Is the contract above (paths, methods, request bodies) acceptable as the canonical interface, or should we converge on something different (e.g. drop `compliance/` prefix everywhere, standardise `reports/...` vs `reporting/...`)?
- For paginated endpoints, can we standardise on Spring's `Pageable` query params (`page`, `size`, `sort`) across the board? Today most endpoints use this but the contract isn't enforced.
- For `X-PSP-ID`: is the header authoritative, or does the backend re-derive from the session principal? If the latter, the FE doesn't strictly need to send it — confirm.
- For `auth/login` response: what is the canonical shape? `{ user, token, redirectUrl }` is what the FE reads; does the backend always return all three?

## Source files (for the backend dev)

- `FRONTEND/src/lib/apiClient.ts` — HTTP client and tenancy header
- `FRONTEND/src/config/api.ts` — base URL convention
- `FRONTEND/src/features/api/queries.ts` — read-side hooks (Dashboard, Cases, Alerts, Transactions, Merchants, Audit, Risk, Calendar, Monitoring, Rules, Limits, PSP, Regulatory)
- `FRONTEND/src/features/api/mutations.ts` — write-side hooks (Cases, Rules, Velocity, Thresholds, Alerts, Merchants, SAR, Calendar)
- `FRONTEND/src/features/api/reportQueries.ts` — Reports Center hooks
- `FRONTEND/src/features/api/cbkReportQueries.ts` — CBK report hooks
- `FRONTEND/src/contexts/AuthContext.tsx` — auth/me, auth/login, auth/logout (raw fetch)
- `FRONTEND/src/pages/Auth/SignupPage.tsx` — auth/register (raw fetch)
- `FRONTEND/src/pages/Users/UsersTab.tsx`, `RolesTab.tsx` — users/roles/psp (raw fetch — should migrate to apiClient eventually)
- `FRONTEND/src/pages/Profile/ProfilePage.tsx`, `Messages/MessagesPage.tsx`, `Settings/SettingsPage.tsx`, `Screening/ScreeningPage.tsx`, `KycDocuments/KycDocumentsPage.tsx`, `LimitsAml/LimitsAmlPage.tsx`, `ReportsCenter/components/CbkSubmissionPanel.tsx` — page-local apiClient usages
- `BACKEND/src/main/resources/application.properties` — `server.servlet.context-path=/api/v1`
