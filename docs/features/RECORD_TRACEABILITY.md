# Record Traceability and Report Provenance

> **Status:** Implemented | **Last updated:** July 2026

Hokeka exposes a PSP-scoped record trail for following investigations from a source record through its related activity, alerts, cases, and report executions.

## Supported Record Types

| Record type | Related data returned |
|---|---|
| Alert | Triggering transaction, merchant, multi-asset customer, disposition and source reference |
| Case | Subject merchant, contributing case alerts and case occurrences |
| Transaction | Merchant, generated alerts, decision and score context |
| Merchant | Transactions, alerts, cases, onboarding and risk state |
| Multi-asset customer | Ownership/control links, transactions, alerts and risk activity |
| Multi-asset transaction | Customer, decision, Travel Rule state and risk signals |
| Report execution | Report source selector, source records, result-row links and calculation summary |

All other persisted JPA entities are available by their upper-snake-case entity name. Examples include `CHARGEBACK_DISPUTE`, `MERCHANT_DOCUMENT`, `SUSPICIOUS_ACTIVITY_REPORT`, `REGULATORY_SUBMISSION`, `CBK_SUBMISSION`, `USER`, `PSP`, `RULE_DEFINITION`, `VELOCITY_RULE`, and `RISK_THRESHOLD`. The metadata renderer returns safe scalar fields, ORM relationships, and recognized scalar foreign keys while withholding secrets, tokens, file paths, hashes, and raw payloads.

## API

`GET /api/v1/records/{recordType}/{recordId}` returns a stable detail graph:

```json
{
  "recordType": "ALERT",
  "recordId": "452",
  "title": "Alert #452",
  "data": { "status": "open", "severity": "CRITICAL" },
  "relatedRecords": [
    {
      "recordType": "TRANSACTION",
      "recordId": "9910",
      "relationship": "TRIGGERED_BY_TRANSACTION"
    }
  ],
  "occurrences": []
}
```

Every related record points back to the same endpoint. The backend validates the current PSP before it returns either a root record or any relation, preventing cross-tenant graph traversal.

## Frontend Navigation

The shared detail route is `/records/:recordType/:recordId`. Alert, case, merchant, transaction-monitoring, Customer 360 relationship, chargeback, KYC document, SAR, CBK submission, audit, billing, subscription, user, role, PSP, rules, regulatory-report, report-preview, and report-history views link to it. The detail screen presents record data, related records, chronological occurrences, and a report action that carries the selected record into the Reports Center.

Audit-log records resolve their dynamic `entityType` and `entityId` into an `AUDITED_RECORD` relation. Report preview cells use the same identifier-to-record mapping as backend report provenance, so transaction, merchant, case, alert, regulatory, billing, market, mobile-money, and virtual-asset identifiers remain clickable before and after report generation.

## Reporting Provenance

All generated Reports Center executions now persist three immutable JSON structures in `report_executions`:

- `source_context`: record selector, date window and filters supplied for the run.
- `record_links`: deduplicated source records found in report rows or explicitly selected for the run.
- `calculation_summary`: row count and numeric totals calculated from the exact returned rows.

When a report is run from a record detail screen, `recordType` and `recordId` are carried to generation and scheduling. The reports page shows the selected source context and report history links back to the report execution’s trace record.

Synchronous regulatory CTR/LCTR/IFTR and scheme-pack endpoints also create completed report executions. Their response bodies expose `executionId`, and the regulatory UI links directly to the execution trace. The direct-run ledger records the source window, all numeric totals, and links found in emitted transaction, SAR, and merchant rows.

Migrations: `V157__report_record_traceability.sql` and `V158__scheme_report_trace_definitions.sql`.
