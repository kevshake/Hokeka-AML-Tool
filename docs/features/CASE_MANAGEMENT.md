# Case Management

## Overview

Compliance cases are the central unit of investigation workflow. Created automatically from escalated alerts or manually, cases track the full investigation lifecycle from assignment through decision to resolution.

## Case Lifecycle

```
Alert Escalated / Manual Creation
    │
    ▼
Case Created (status=OPEN)
    │ Auto-assigned via WorkflowAutomationService (workload-balanced)
    ▼
Investigation
    │ Timeline events, network graph, audit replay
    ├── Evidence collection
    ├── Document upload
    └── AML/Sanctions re-check
    ▼
Decision (CaseDecisionService)
    ├── APPROVE → status=CLOSED_CLEARED, resolution=CLEARED
    ├── REJECT → status=CLOSED_BLOCKED, resolution=REJECTED
    ├── FILE_SAR → status=CLOSED_SAR_FILED, resolution=SAR_FILED
    ├── HOLD → stays IN_PROGRESS
    └── ESCALATE → hierarchy step up
    │
    All decisions require: ≥10 char justification, immutable audit log
    Kafka event published if producer configured
```

## Escalation Hierarchy

```
ANALYST → COMPLIANCE_OFFICER → MLRO
```

- Each level has configurable escalation rules stored in DB
- Rules checked by `checkPendingEscalations()` every 5 minutes (@Scheduled)
- Rules match on: minPriority, minRiskScore, daysOpen, minAmount
- If no user found in target role, escalates to next role up
- Load-balances to least-loaded user in the role

## Page Features

**CasesAllCases**: List of all cases with filters (status, priority, assigned to), pagination.

**CasesTimeline**: Visual timeline of all events in a case — creation, evidence collection, decisions, escalations.

**CasesNetworkGraph**: Graph visualization of relationships — merchants, transactions, alerts, cases — with depth controls.

**CasesQueues**: Work queues by status (Open, In Progress, Pending Review, Closed) with workload indicators.

## Key Components

### CaseDecisionService
- Mandatory justification (min 10 characters)
- Immutable audit log (decisions cannot be changed)
- Status transitions enforced
- Kafka event published on decision

### CaseEscalationService
- DB-managed escalation rules
- Auto-escalation check every 5 minutes
- Risk score calculation (alert base + amount factor + entity risk tier)
- Workload-balanced assignment

### CasePermissionService
- RBAC with PSP isolation
- Role-based action permissions
- Closed-case locks (no edits after closure)
- Investigator reassignment tracking

### WorkflowAutomationService
- Auto-approve low-risk merchants via DB queries
- Auto-assign by workload (fewest open cases)
- Escalate overdue cases based on SLA thresholds

### CaseActivityService
- Logs every action (create, assign, escalate, decision, comment)
- Timeline reconstruction from activity records
- Full audit trail for regulatory review