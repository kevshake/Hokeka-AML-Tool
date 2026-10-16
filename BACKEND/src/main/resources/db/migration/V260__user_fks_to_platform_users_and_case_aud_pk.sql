-- V260__user_fks_to_platform_users_and_case_aud_pk.sql
-- Purpose (found by the PSP simulator on a database built from Flyway):
--
-- 1. 24 foreign keys still reference the legacy, empty psp_users(user_id) table although the
--    owning entities (SuspiciousActivityReport, CaseNote, CaseEvidence, CaseActivity,
--    CaseTransaction, EscalationRule, UserSkill, limits / thresholds / velocity / country rules)
--    join to User = platform_users(id). Every insert that records who acted failed, e.g.
--      POST /compliance/sar/workflow/create -> violates foreign key constraint "fk_sar_created_by"
--    V243 fixed the same defect for api_usage_logs only. Each constraint is recreated with the
--    same name and ON DELETE action, pointing at platform_users(id). NOT VALID: new writes are
--    enforced, while rows written by older deployments (ids from psp_users) are not re-checked.
--
-- 2. SAR_001 report v3 (V179) joined psp_users to match those keys; v4 joins platform_users again.
--
-- 3. compliance_cases_aud kept the legacy primary key (id, rev) while ComplianceCase's id column
--    is case_id, so Envers' audit insert (case_id, rev, ...) violated "id NOT NULL" and every case
--    creation failed with 503. The key moves to (case_id, rev); id becomes a nullable legacy column.
--
-- Tables affected: the 15 tables holding those FKs, report_definitions, compliance_cases_aud
-- Audited entities: ComplianceCase (audit table key)
-- Idempotent: constraints are only recreated while they still reference psp_users; the report row
--             uses ON CONFLICT; the PK change is guarded.

DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT c.conrelid::regclass AS tbl, c.conname, pg_get_constraintdef(c.oid) AS def
        FROM pg_constraint c
        WHERE c.contype = 'f' AND c.confrelid = to_regclass('public.psp_users')
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.tbl, fk.conname);
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s NOT VALID', fk.tbl, fk.conname,
                       replace(fk.def, 'REFERENCES psp_users(user_id)', 'REFERENCES platform_users(id)'));
    END LOOP;
END$$;

UPDATE report_definitions d
SET is_active = FALSE
FROM reports r
WHERE d.report_id = r.id AND r.report_code = 'SAR_001' AND d.version < 4;

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    aggregations, group_by_fields, order_by_default, is_active, created_by, created_at
)
SELECT d.report_id, 4,
       replace(
           replace(d.sql_query,
               'LEFT JOIN psp_users creator ON creator.user_id = sar.created_by_user_id',
               'LEFT JOIN platform_users creator ON creator.id = sar.created_by_user_id'),
           'LEFT JOIN psp_users reviewer ON reviewer.user_id = sar.reviewed_by_user_id',
           'LEFT JOIN platform_users reviewer ON reviewer.id = sar.reviewed_by_user_id'),
       d.count_query, d.parameters, d.filters, d.columns, d.aggregations,
       d.group_by_fields, d.order_by_default, TRUE, d.created_by, CURRENT_TIMESTAMP
FROM report_definitions d
JOIN reports r ON r.id = d.report_id
WHERE r.report_code = 'SAR_001' AND d.version = 3
ON CONFLICT (report_id, version) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
        WHERE c.conrelid = to_regclass('public.compliance_cases_aud') AND c.contype = 'p' AND a.attname = 'id'
    ) THEN
        UPDATE compliance_cases_aud SET case_id = id WHERE case_id IS NULL;
        ALTER TABLE compliance_cases_aud DROP CONSTRAINT compliance_cases_aud_pkey;
        ALTER TABLE compliance_cases_aud ALTER COLUMN id DROP NOT NULL;
        ALTER TABLE compliance_cases_aud ALTER COLUMN case_id SET NOT NULL;
        ALTER TABLE compliance_cases_aud ADD CONSTRAINT compliance_cases_aud_pkey PRIMARY KEY (case_id, rev);
    END IF;
END$$;
