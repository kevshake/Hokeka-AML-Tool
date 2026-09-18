# Cross-PSP Fraud Intelligence

## Overview

Shares confirmed fraud data across all PSPs in the multi-tenant environment. When one PSP confirms a fraudulent merchant, PAN, or terminal, all other PSPs are automatically protected.

## Data Flow

```
TRUE_POSITIVE Alert resolved
    │
    ├── AlertFraudIncidentBridge (CBK record)
    │
    └── CrossPspFraudIntelligenceService.flagEntitiesFromAlert()
        │ Flags: MERCHANT_ID, PAN_HASH, TERMINAL, NAME (trading name)
        │ Creates/updates cross_psp_fraud_flags rows
        │ (entity_value + entity_type is UNIQUE)
        │ Increments flag_count on repeat hits
        │ Source PSP tracked per flag
        │ Links back to source alert ID
        ▼
    cross_psp_fraud_flags table
        │ entity_type: "MERCHANT_ID", "PAN_HASH", "TERMINAL", "NAME"
        │ risk_level: MEDIUM (1-2 flags), HIGH (3+ flags or multi-PSP)
        │ last_flagged_at: updated on each hit
        ▼
New transaction by PSP-B
    │
    └── DecisionEngine → CrossPspFraudIntelligenceService.screenTransaction()
        │ Exact match: merchant ID, PAN hash, terminal → BLOCK
        │ Fuzzy name match: trading name via NameMatchingService
        │   (DoubleMetaphone + Levenshtein, ≥80% similarity)
        │ Multi-PSP flag → BLOCK with CROSS_PSP_FRAUD reason
        │ Single PSP flag → HOLD with advisory
        ▼
    Decision: BLOCK / HOLD (with reason "Cross-PSP fraud match")
```

## Database

Table: `cross_psp_fraud_flags`

| Column | Type | Description |
|---|---|---|
| id | BIGSERIAL | Primary key |
| entity_value | TEXT | The flagged value (merchant ID, PAN hash, etc.) |
| entity_type | TEXT | Type: MERCHANT_ID, PAN_HASH, TERMINAL, NAME |
| source_psp_id | BIGINT | PSP that flagged this entity |
| alert_id | BIGINT | Source alert ID |
| risk_level | TEXT | MEDIUM or HIGH |
| flag_count | INTEGER | Number of times flagged |
| last_flagged_at | TIMESTAMP | Last flag update |
| created_at | TIMESTAMP | First flagged |

Unique constraint: (entity_value, entity_type)

## Integration Points

| Component | Hook | Action |
|---|---|---|
| AlertFraudIncidentBridge | TRUE_POSITIVE resolution | Flags entities |
| DecisionEngine | Every transaction check | Screens against flags |
| NameMatchingService | Fuzzy name matching | Similarity scoring |
| AlertController.resolveAlert() | Disposition recording | Triggers flagging |