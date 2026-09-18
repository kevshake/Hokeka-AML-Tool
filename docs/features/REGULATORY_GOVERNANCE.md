# Regulatory Deadline And Rule Governance

> **Status:** Implemented | **Last updated:** July 2026

## SAR Deadline Governance

SAR/STR deadlines are calculated from `suspicionAroseAt`, not record creation time. The selected policy ID, policy code, calculation time, warning time, breach state, and notification timestamps are persisted on the SAR and copied into the compliance calendar.

`V159__regulatory_deadline_governance.sql` seeds:

- `KE-POCAMLA-STR-2D`: Kenya, two calendar days from suspicion, effective 17 November 2023.
- `DEFAULT-SAR-30D`: an explicit fallback that deployments must replace with applicable jurisdiction rules.

Policy resolution prefers a PSP-specific rule, then a jurisdiction rule, then the default, while respecting effective dates. Units can be hours, calendar days, or business days.

The Kenya policy is based on regulation 38 of the [Proceeds of Crime and Anti-Money Laundering Regulations, 2023](https://new.kenyalaw.org/akn/ke/act/ln/2023/153/eng%402023-11-17) and section 44 of POCAMLA. The [Financial Reporting Centre](https://www.frc.go.ke/?page_id=25) also describes the two-day reporting requirement.

## SAR Maker-Checker

- The authenticated user is always the actor; client-supplied user IDs are ignored.
- Only the creator can submit a draft or rejected SAR for review.
- The creator cannot approve or reject the same SAR.
- PSP scope is enforced for submit, approve, reject, and file actions.
- Rejection requires durable review notes; approval also records reviewer and decision time.
- Filing is allowed only after approval and records whether the deadline was breached.
- Permissions fail closed when authentication, a role mapping, or an authority is absent.

The Transaction Monitoring SAR page captures the suspicion timestamp and shows policy, deadline, breach state, and review evidence.

## Rule Maker-Checker

Rules use immutable `rule_versions` snapshots with SHA-256 content hashes. Create, update, enable, disable, retirement, and rollback are proposals; they do not change runtime behavior until a different authorized user approves them.

Controls include:

- One pending proposal per rule.
- Maker cannot review their own proposal.
- Mandatory approval or rejection reason.
- PSP/global review scope enforcement.
- Immediate or future effective activation.
- Immutable history, superseded versions, and rollback proposals.
- Drools reload only after activation.
- Pending-approval queue and snapshot review in Rule Studio.
- `RGOV_001` report for maker, reviewer, hash, snapshot, effective date, and activation evidence.

## Migrations

- `V159__regulatory_deadline_governance.sql`
- `V160__rule_maker_checker_versions.sql`
- `V161__multi_domain_signal_taxonomy.sql`
- `V163__sar_review_evidence.sql`
- `V166__market_and_governance_reports.sql`

