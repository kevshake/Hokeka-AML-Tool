# Tenant-Scoped Limits and Enforcement

> **Status:** Implemented | **Last updated:** July 2026

Transaction limits are persisted per PSP and evaluated in the live decision path. Platform limits use a null `psp_id` and act as inherited defaults. A PSP can create an override with the same name, risk level, country code, or velocity-rule name without affecting another tenant.

## Configuration

- `AML_TRANSACTION` controls the maximum value of a single transaction.
- `AML_DAILY` controls aggregate PSP transaction value for the current day.
- Merchant controls support per-transaction, daily, weekly, and monthly limits.
- Risk thresholds, velocity rules, country rules, and global limits are returned only for the platform scope or the current PSP plus inherited platform defaults.
- PSP users cannot modify platform-owned records or records belonging to another PSP.

`GET /api/v1/limits/aml` returns the effective persisted values. `POST /api/v1/limits/aml` performs an idempotent upsert in the authenticated PSP scope. The frontend loads those values before editing them.

## Decision Path

`TransactionLimitEnforcementService` evaluates merchant limits and effective PSP limits before a transaction decision is finalized. Breaches produce a canonical `BLOCK` decision with evidence describing the configured value and observed volume. A transaction with no PSP scope is blocked because limits cannot be evaluated safely.

Velocity scoring reads only active platform rules and active rules for the transaction PSP. A velocity-control failure adds a manual-review risk factor and a critical score instead of silently returning no risk.

## Isolation

Reporting summaries, case/SAR counts, merchant totals, and audit counts use the authenticated PSP scope. Only platform administrators can request cross-PSP aggregates. Automatic case assignment filters eligible users to the case PSP and rejects manual cross-PSP assignments.

Migration: `V191__tenant_scoped_limit_configuration.sql`.
