# PSP Management

## Overview

Multi-tenant Payment Service Provider management. Each PSP is a fully isolated tenant with its own merchants, transactions, alerts, cases, configuration, and billing.

## PSP Entity Structure

```
PSP Profile
    ├── Company Info (legalName, tradingName, country, regNumber, taxId)
    ├── Contact Info (email, phone, address)
    ├── Directors (list → CBK annual reporting)
    ├── Shareholders (list → CBK annual reporting)
    ├── Trustees (list → CBK annual reporting)
    ├── Senior Management (list → CBK annual reporting)
    ├── Products (list → CBK monthly reporting)
    ├── Trust Accounts (list → CBK daily reporting)
    ├── Tariffs (list → CBK monthly reporting)
    ├── CBK Configuration (institutionCode, clientId, environment)
    └── Branding Theme (primaryColor, logo, font, buttonStyle)
```

## Multi-Tenant Isolation

| Layer | Isolation Mechanism |
|---|---|
| Database | All tables have psp_id column |
| JPA | PspIsolationService validates PSP scope |
| Security | JWT token contains PSP ID |
| API | @PreAuthorize checks PSP match |
| Queries | All repository queries filtered by psp_id |
| Cache | Aerospike namespaced by PSP |

## Roles

| Role | Scope | Permissions |
|---|---|---|
| SUPER_ADMIN | Platform-wide | Full system access |
| ADMIN | Platform-wide | All management |
| COMPLIANCE_OFFICER | Platform/PSP | Case decisions, SAR filing |
| INVESTIGATOR | Platform/PSP | Case investigation |
| ANALYST | PSP | Alert review, case work |
| PSP_ADMIN | Own PSP | User management, billing view |
| PSP_USER | Own PSP | Read-only access |
| AUDITOR | Platform/PSP | Log viewing |
| MLRO | Platform/PSP | Escalation endpoint |

## Onboarding Flow

```
1. Registration → POST /psps/register (public) or admin creates
2. Activation → Admin activates (status=ACTIVE)
3. Users → PSP_ADMIN created
4. Configuration → CBK credentials, rules, thresholds
5. Integration → API keys used in production
6. Go Live → CBK environment promoted to live
```

## Page Features

**PspsListPage**: All PSPs with quick status, contact, and action buttons.

**PspConfigPage**: 10-tab configuration:
1. Company — edit legal name, trading name, country, contact details
2. CBK Reporting — configure CBK credentials, environment promotion
3. Directors — manage directors list
4. Shareholders — manage shareholders list
5. Trustees — manage trustees list
6. Senior Management — manage senior management list
7. Products — manage products list
8. Trust Accounts — manage trust accounts
9. Tariffs — manage transaction tariffs
10. Billing — view billing info and invoice history