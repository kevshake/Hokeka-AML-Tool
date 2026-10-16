-- V259__compliance_cases_legacy_not_null.sql
-- Purpose: compliance_cases was first created by V2 for merchant-onboarding cases with
--          merchant_id BIGINT NOT NULL and case_type VARCHAR(50) NOT NULL. The ComplianceCase
--          entity that now owns the table treats the merchant as optional (workflow cases,
--          alert/rule cases, network cases) and does not map case_type at all, so on a database
--          built from Flyway every case insert failed:
--            ERROR: null value in column "case_type" of relation "compliance_cases" violates
--                   not-null constraint
--          (rule-triggered cases and POST /compliance/cases/workflow/create — found by the PSP
--          simulator). Hibernate ddl validation does not check nullability, so boot passed.
--          Both legacy columns become nullable; existing rows keep their values.
-- Tables affected: compliance_cases
-- Audited entities: ComplianceCase (compliance_cases_aud columns are already nullable)
-- Idempotent: DROP NOT NULL on an already-nullable column is a no-op.

ALTER TABLE compliance_cases ALTER COLUMN case_type DROP NOT NULL;
ALTER TABLE compliance_cases ALTER COLUMN merchant_id DROP NOT NULL;
