# User Management

## Overview

Role-based access control (RBAC) with fine-grained permissions. Users can be platform-level (no PSP) or scoped to a specific PSP. All actions are logged in the audit trail.

## Role Model

```
Role
    ├── name (unique)
    ├── description
    ├── psp (nullable — null = platform role)
    └── permissions (Set<Permission>)
```

### Available Permissions

| Category | Permissions |
|---|---|
| Case Management | CASE_READ, CASE_WRITE, CASE_DELETE, CASE_ASSIGN, CASE_ESCALATE, CASE_DECIDE |
| Alert Management | ALERT_READ, ALERT_WRITE, ALERT_RESOLVE, ALERT_BULK |
| Transaction | TRANSACTION_READ, TRANSACTION_SCREEN |
| User Management | USER_READ, USER_CREATE, USER_UPDATE, USER_DELETE |
| PSP Management | PSP_READ, PSP_UPDATE, PSP_DELETE |
| Reporting | REPORT_READ, REPORT_CREATE, REPORT_EXPORT, REPORT_SCHEDULE |
| Screening | SCREENING_READ, SCREENING_EXECUTE |
| Settings | SETTINGS_READ, SETTINGS_UPDATE |
| Audit | AUDIT_READ |
| Billing | BILLING_READ, BILLING_MANAGE |
| CBK | CBK_READ, CBK_CONFIGURE, CBK_PROMOTE |

### Default Roles

| Role | Permissions |
|---|---|
| SUPER_ADMIN | All permissions |
| ADMIN | All except CBK_PROMOTE |
| COMPLIANCE_OFFICER | Case/Alert/Report/Screening + read |
| INVESTIGATOR | Case/Alert/Transaction + read |
| ANALYST | Case/Alert (limited) + read |
| PSP_ADMIN | PSP-scoped + USER_CREATE + BILLING_READ |
| PSP_USER | Read-only (PSP-scoped) |
| AUDITOR | AUDIT_READ only |
| MLRO | Case/CBK/Screening escalated |

## User Entity

```
User
    ├── username (unique)
    ├── email
    ├── firstName, lastName
    ├── password (hashed)
    ├── enabled
    ├── role → Role
    ├── psp → Psp (nullable)
    └── createdAt, lastLogin
```

## Audit Logging

Every user action is logged:

| Action | Entity | Example |
|---|---|---|
| LOGIN | User | User logged in |
| CREATE | Case | Case #123 created |
| UPDATE | Merchant | Merchant KYC updated |
| DELETE | Alert | Alert deleted |
| VIEW | Report | Report generated |
| EXPORT | Data | CSV exported |
| OVERRIDE | Permission | Override applied |

Audit logs are available on the **AuditLogsPage** with filters:
- Date range (start/end)
- Action type
- Username
- PSP (admin only)

## Page Features

**UsersTab**: User CRUD table with:
- Username, name, email, role badge, PSP, status badge
- Add/Edit dialog (username, name, email, password, role, PSP, enabled toggle)
- Delete confirmation
- Enable/disable toggle
- Pagination

**RolesTab**: Role management with:
- Role name, description, scope badge (System vs PSP-specific)
- Permission assignment via expandable categories with checkboxes
- Category-level toggle (select/deselect all)
- Individual permission toggles
- Create/edit/delete dialogs

**ProfilePage**: User profile with:
- Avatar with initials
- Role, PSP, status badges
- Personal information tab (username, email, first/last name)
- Security tab (password change with show/hide toggle)
- Permissions tab (all granted permissions with status)