# Console role matrix (client testing)

Authoritative mapping between **Control Plane** roles/permissions (`BACKEND` `Permission` enum, V127/V128 seeds, `@PreAuthorize`) and **Console** navigation, routes, and actions.

Implementation: `FRONTEND/src/lib/consoleRbac.ts`, `FRONTEND/src/lib/rolePermissionTemplates.ts`.

## Personas

| Persona | Role names | Tenant |
|---------|------------|--------|
| Platform super admin | `SUPER_ADMIN`, `ADMIN` | `HOKEKA_PLATFORM` (pspId 0) |
| Platform operator | `PLATFORM_ADMIN` | `HOKEKA_PLATFORM` |
| PSP administrator | `PSP_ADMIN` | PSP tenant |
| Compliance analyst | `COMPLIANCE_OFFICER`, `INVESTIGATOR`, `ANALYST`, `PSP_ANALYST`, `CASE_MANAGER` | PSP / platform read |
| Second reviewer / MLRO | `MLRO`, `SENIOR_ANALYST` | PSP / platform |
| Screening specialist | `SCREENING_ANALYST` | PSP / platform |
| Auditor (read-only) | `AUDITOR`, `VIEWER`, `BANK_AUDITOR`, `BANK_OFFICER` | PSP / platform |
| PSP operator | `PSP_USER` | PSP tenant |

## Navigation (sidebar)

| Nav item | Required permission (any) | Platform-only |
|----------|----------------------------|---------------|
| Dashboard | *(authenticated)* | |
| Live Monitoring | `VIEW_TRANSACTION_DETAILS` | |
| Customer 360 | `VIEW_PII` or `MERCHANT_VIEW` | |
| Market Surveillance | `REPORT_VIEW` or `VIEW_TRANSACTION_DETAILS` | |
| Mobile Money / Wallet Intelligence | `VIEW_TRANSACTION_DETAILS` | |
| Alerts / Cases / Network | `VIEW_CASES` | |
| Messages | *(authenticated)* | |
| KYC | `MERCHANT_VIEW` | |
| Screening | `VIEW_SCREENING_RESULTS` | |
| Risk Rules / Limits | `MANAGE_RULES` (limits also `CONFIGURE_SYSTEM`) | |
| Regulatory Reports | `VIEW_SAR` or `REPORT_VIEW` | |
| Chargebacks / Compliance calendar | `VIEW_CASES` or `REPORT_VIEW` / `VIEW_SAR` | |
| Risk Analytics / Reports / Analytics | `REPORT_VIEW` | |
| Audit logs | `VIEW_AUDIT_LOGS` | |
| Merchants / PSPs / Runtime errors | `MERCHANT_VIEW` / `MANAGE_PSP` / `CONFIGURE_SYSTEM` | yes |
| Edge nodes | `MANAGE_PSP` or `PSP_SETTINGS_VIEW` | |
| Users | `MANAGE_USERS` | |
| Settings | `PSP_SETTINGS_VIEW`, `CONFIGURE_SYSTEM`, or `MANAGE_PSP` | |
| My Organization | `PSP_SETTINGS_VIEW` | tenant only |

## Route guards

All authenticated routes under `App.tsx` pass through `PermissionRoute`, which calls `canAccessRoute()` for the current pathname. Unauthorized users see an in-app **Access restricted** panel (not a blank screen).

## Actions

| Action | Enforcement |
|--------|-------------|
| Alert status bulk update | Roles: `SUPER_ADMIN`, `ADMIN`, `COMPLIANCE_OFFICER`, `INVESTIGATOR`, `MLRO`, `SENIOR_ANALYST` (matches `PUT /alerts/{id}/status` and `/resolve`) |
| Alert disposition + maker-checker | Same as resolve; true-positive dispositions return `202` / `pending_second_review` until a second distinct reviewer confirms |
| SAR approve / file | Permissions `APPROVE_SAR`, `FILE_SAR` |
| Rules edit | `MANAGE_RULES` |
| User / role admin | `MANAGE_USERS`, `MANAGE_ROLES` |
| Platform billing | Platform admin + `MANAGE_PSP` |
| Hokeka AI operator settings | Platform admin only (`/settings` → Hokeka AI tab) |
| Sanctions ingest trigger | `SUPER_ADMIN`, `ADMIN`, `COMPLIANCE_OFFICER`, `MLRO` |

## Feature wiring (P0)

| Feature | Console surface | API |
|---------|-----------------|-----|
| Transaction assessment | `/records/TRANSACTION/:id` | `GET /transactions/{id}/assessment` |
| Linked alerts | `/records/TRANSACTION/:id` | `GET /transactions/{id}/alerts` |
| Compliance links | `/records/TRANSACTION/:id` | `GET /compliance/links/transaction/{id}` |
| Screening activity | `/records/TRANSACTION/:id`, `/screening` | `GET /screening/activity` |
| Alert maker-checker | `/alerts` modal | `PUT /alerts/{id}/resolve` |

## Automated tests

Vitest: `FRONTEND/src/lib/consoleRbac.test.ts` — nav/route/action expectations per persona.

## Demo accounts (seeded)

See `BACKEND/src/main/resources/db/migration/V127__seed_demo_accounts.sql` (password documented in migration header).
