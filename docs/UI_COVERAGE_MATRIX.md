# UI coverage matrix (wave 2)

Maps documented product capabilities to **Console** routes and **install docs site** pages. Status legend:

| Status | Meaning |
|--------|---------|
| **implemented+aligned** | Feature exists; glass/token alignment applied on touched surfaces |
| **implemented-but-inconsistent** | Feature exists; still uses legacy styling or partial UX |
| **missing** | No Console route or docs page for the documented flow |
| **broken** | Route exists but known dead UI, 403 wall, or docs/code drift |
| **blocker:** … | Documented gap; Console route not applicable this wave |

Sources: `docs/install/01–06`, `docs/install/README.md`, `docs/AML-FRAUD-COVERAGE-GAP-REGISTER.md`, live `FRONTEND/src/App.tsx`.

## Install & topology (process docs)

| Feature / flow | Console | Docs site | Status |
|----------------|---------|-----------|--------|
| Install process map (roles, topology) | `/dashboard` (overview KPIs) | `/` (README index) | implemented+aligned |
| Control Plane cloud stack | `/settings` → Platform Admin; operator doc deep links | `/docs/03-control-plane` (operator build only) | implemented+aligned |
| Console deployment | `/settings` (theme/system) | `/docs/02-console-dashboard` (operator build only) | implemented+aligned |
| Packages / CDN / edge release | `/edge-nodes` (bundle version column) | `/docs/04-packages-cdn-and-edge-release` | implemented+aligned |
| Edge Node install & enrollment | `/edge-nodes`, Setup wizard | `/docs/01-client-edge-node` | implemented+aligned |
| Local developer stack | Dev-only (no prod route) | `/docs/05-local-developer-stack` | implemented+aligned |
| PSP dual-post API integration | `/settings` → Webhooks; `/transaction-monitoring` | `/docs/06-psp-api-dual-post-integration` | implemented+aligned |

## Console domains (functional)

| Feature / flow | Console route / component | Docs reference | Status |
|----------------|---------------------------|----------------|--------|
| Dashboard home | `/dashboard` → `DashboardPage` | 02-console-dashboard | implemented+aligned |
| Alerts & triage | `/alerts` → `AlertsPage` | Gap register §3; matrix | implemented+aligned |
| Cases | `/cases/*` → `CasesPage` | Gap register §3 | implemented+aligned |
| Merchants / KYB | `/merchants` | Gap register §2 | implemented+aligned |
| Screening (incl. PEP / sanctions UX) | `/screening` | Gap register P0 #1–2; 06 dual-post | implemented+aligned |
| Transaction monitoring (batch path) | `/transaction-monitoring/*` | Gap register P0 #3; 06 dual-post | implemented+aligned |
| Rules | `/rules-generation` | Gap register §3 | implemented+aligned |
| Risk analytics | `/risk-analytics` | Gap register §4 | implemented+aligned |
| Regulatory / SAR | `/reports`, `/regulatory-reports` | Gap register §6 | implemented+aligned |
| KYC documents | `/kyc-documents` | Gap register §2 | implemented+aligned |
| Network graph (case-linked) | `/network` → `NetworkAnalysisPage` | Gap register §4 graph (case API) | implemented+aligned |
| Platform billing (revenue) | `/billing` → `BillingPage` | Platform Admin billing rates | implemented+aligned |
| PSP billing (tenant) | `/settings` → Billing tab / `Psps` → Billing | 02-console | implemented+aligned |
| Webhooks | `/settings` → Webhooks | 06 dual-post | implemented+aligned |
| Edge fleet | `/edge-nodes` | 01-client-edge-node | implemented+aligned |
| Platform admin (invites, API keys, rates) | `/settings` → Platform Admin | 03-control-plane | implemented+aligned |
| PSP theme / branding | `/settings` → PSP Theme; `/psps/:id` branding | 02-console | implemented+aligned |
| Users & roles | `/users/*` | 02-console | implemented+aligned |
| Audit logs | `/audit` | Gap register §6 | implemented+aligned |
| Chargebacks | `/chargebacks` | `docs/chargeback/*` | implemented+aligned |
| Crypto / VASP | *(backend module)* | features docs | blocker: no Console surface; backend-only module |
| Notification bell | `NotificationBellMenu` in `HokekaHeader` | `/messages` API | implemented+aligned |
| Hokeka AI recommendation (PSP-facing) | `AiVerdictPanel`, screening flows | 06 (no vendor names) | implemented+aligned |
| JEV operator settings | `/settings` → JEV AI (platform only) | Hidden from docs-site PSP build | implemented+aligned |

## Summary counts

| Status | Count |
|--------|------:|
| implemented+aligned | 28 |
| implemented-but-inconsistent | 0 |
| missing | 0 |
| broken | 0 |
| blocker | 1 |

## Wave 2 completed

1. **Global MUI table pass** — `MuiTableContainer` theme + `.hokeka-mui-table-shell`; Rules, Chargebacks, and theme defaults for remaining MUI tables.
2. **Network analysis UI** — `/network` route + sidebar nav; case graph via `/api/v1/cases/{id}/network`.
3. **Notification center** — Bell popover on `/messages` API (unread count, mark read, glass empty/loading states).
4. **Operator docs deep links** — Platform Admin links when `VITE_INCLUDE_OPERATOR_DOCS=true`.
5. **Marketing site glass pass** — Hero/product motion rgba → shared token RGB variables.
6. **Print stylesheet** — `docs-site/src/print.css` (light token roles for export).
7. **Coverage register sync** — `scripts/verify-ui-coverage-matrix.mjs` in CI; P0 rows mapped to `/screening` and `/transaction-monitoring`.
