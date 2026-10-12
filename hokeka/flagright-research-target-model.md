# Target model for Hokeka (Flagright-aligned research, Oct 2026)

See repository root `docs/` gap register and `IMPLEMENTATION_PLAN.md` for delivery status.

## AssessmentContext (WP-01 partial)

- **Assessment:** `assessmentId`, `pspId`, trigger (`TXN` | `TXN_EVENT` | …), `triggerRef`, `txnId`, `parentAssessmentId`, `edgeAssessmentId`, `versions` JSON, `contextHash`, `decision`, `latencyMs`, `createdAt`.
- **Finding:** `findingId`, `assessmentId`, `transactionId`, `partyId`, `sourceType` (`RULE` | `RISK_FACTOR` | `SCREENING` | `LIST` | `LIMIT` | `ML` | …), `sourceId`, `sourceVersion`, `phase`, `shadow`, `nature`, `severity`, `triggered`, `score`, `evidence`, `featureReferences`, `explanation`, `proposedAction(s)`, `createdAt`.
- PSP-facing AI text uses **"Hokeka AI recommendation"** only (no provider/model names).

## WP-01 delivery (Control Plane)

- Tables: `assessments`, `findings`; `rule_execution_logs.rule_version_id`, `rule_execution_logs.assessment_id`.
- Shadow mode: decisions unchanged; findings recorded async on ingest.
- Read APIs: `GET /api/v1/assessments/{id}`, `GET /api/v1/transactions/{id}/assessment`; ingest response adds `assessmentId`.
