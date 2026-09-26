# UI coverage matrix (wave 1)

Maps documented product capabilities to **Console** routes and **install docs site** pages. Status legend:

| Status | Meaning |
|--------|---------|
| **implemented+aligned** | Feature exists; wave 1 glass/token alignment applied on touched surfaces |
| **implemented-but-inconsistent** | Feature exists; still uses legacy styling or partial UX |
| **missing** | No Console route or docs page for the documented flow |
| **broken** | Route exists but known dead UI, 403 wall, or docs/code drift |

Sources: `docs/install/01–06`, `docs/install/README.md`, `docs/AML-FRAUD-COVERAGE-GAP-REGISTER.md`, live `FRONTEND/src/App.tsx`.

## Install & topology (process docs)

| Feature / flow | Console | Docs site | Status |
|----------------|---------|-----------|--------|
| Install process map (roles, topology) | `/dashboard` (overview KPIs) | `/` (README index) | implemented+aligned |
| Control Plane cloud stack | Platform: `/settings` → Platform Admin; ops use API | `/docs/03-control-plane` (operator build only) | implemented-but-inconsistent |
| Console deployment | `/settings` (theme/system) | `/docs/02-console-dashboard` (operator build only) | implemented+aligned |
| Packages / CDN / edge release | `/edge-nodes` (bundle version column) | `/docs/04-packages-cdn-and-edge-release` | implemented+aligned |
| Edge Node install & enrollment | `/edge-nodes`, Setup wizard | `/docs/01-client-edge-node` | implemented+aligned |
| Local developer stack | Dev-only (no prod route) | `/docs/05-local-developer-stack` | implemented+aligned |
| PSP dual-post API integration | `/settings` → Webhooks; `/transaction-monitoring` | `/docs/06-psp-api-dual-post-integration` | implemented+aligned |

## Console domains (functional)

| Feature / flow | Console route / component | Docs reference | Status |
|----------------|---------------------------|----------------|--------|
| Dashboard home | `/dashboard` → `DashboardPage` | 02-console-dashboard | implemented+aligned |
| Alerts & triage | `/alerts` → `AlertsPage` | Gap register §3 | implemented+aligned |
| Cases | `/cases/*` → `CasesPage` | Gap register §3 | implemented+aligned |
| Merchants / KYB | `/merchants` | Gap register §2 | implemented+aligned |
| Screening | `/screening` | Gap register §2 | implemented+aligned |
| Transaction monitoring | `/transaction-monitoring/*` | 06 dual-post | implemented+aligned |
| Rules | `/rules-generation` | Gap register §3 | implemented-but-inconsistent |
| Risk analytics | `/risk-analytics` | Gap register §4 | implemented-but-inconsistent |
| Regulatory / SAR | `/reports`, `/regulatory-reports` | Gap register §6 | implemented-but-inconsistent |
| KYC documents | `/kyc-documents` | Gap register §2 | implemented-but-inconsistent |
| Network graph | *(no top-level nav route)* | Gap register §4 (dead code) | missing |
| Platform billing (revenue) | `/billing` → `BillingPage` | Platform Admin billing rates | implemented+aligned |
| PSP billing (tenant) | `/settings` → Billing tab / `Psps` → Billing | 02-console | implemented+aligned |
| Webhooks | `/settings` → Webhooks | 06 dual-post | implemented+aligned |
| Edge fleet | `/edge-nodes` | 01-client-edge-node | implemented+aligned |
| Platform admin (invites, API keys, rates) | `/settings` → Platform Admin | 03-control-plane | implemented+aligned |
| PSP theme / branding | `/settings` → PSP Theme; `/psps/:id` branding | 02-console | implemented+aligned |
| Users & roles | `/users/*` | 02-console | implemented+aligned |
| Audit logs | `/audit` | Gap register §6 | implemented-but-inconsistent |
| Chargebacks | `/chargebacks` | `docs/chargeback/*` | implemented-but-inconsistent |
| Crypto / VASP | *(backend module)* | features docs | missing |
| Notification bell | `HokekaHeader` | Gap register (dead UI) | broken |
| Hokeka AI recommendation (PSP-facing) | `AiVerdictPanel`, screening flows | 06 (no vendor names) | implemented+aligned |
| JEV operator settings | `/settings` → JEV AI (platform only) | Hidden from docs-site PSP build | implemented+aligned |

## Summary counts

| Status | Count |
|--------|------:|
| implemented+aligned | 18 |
| implemented-but-inconsistent | 9 |
| missing | 2 |
| broken | 1 |

## Wave 2 follow-ups

1. **Global MUI data-grid pass** — Replace remaining default `Table`/`Paper` stacks on Rules, Regulatory Reports, and Audit with shared `hokeka-table` / glass shells; add Storybook or visual regression for token drift.
2. **Network analysis UI** — Add `/network` route wired to live Neo4j graph services (today orphaned per gap register); align with glass table + graph canvas tokens.
3. **Notification center** — Implement bell dropdown in `HokekaHeader` (subscribe to `/api/v1/notifications`); empty/loading states on glass popover.
4. **Operator docs in PSP build** — Optional deep-link from Console Platform Admin to operator-only install pages when `VITE_INCLUDE_OPERATOR_DOCS=true`; shared header component between Console and docs-site.
5. **Marketing site (`website/`) full glass pass** — Wave 1 imports shared `tokens.css`; wave 2 replaces remaining hard-coded rgba in hero motion and product frames with CSS variables.
6. **Light-mode docs export** — PDF/print stylesheet for install docs using token roles (secondary surface) without forking palette hex.
7. **Coverage register sync** — Refresh gap-register rows marked stale (batch scoring, funnel/TBML) and link each P0 row to Console route in this matrix automatically via CI check.
