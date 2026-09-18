-- ============================================================================
-- V128: Additional compliance roles with legally distinct meaning
--
-- Purpose: V127 seeds the canonical role set
-- (SUPER_ADMIN, PLATFORM_ADMIN, PSP_ADMIN, PSP_USER, COMPLIANCE_OFFICER) but
-- several controllers reference roles whose legal/regulatory meaning is
-- distinct from the canonical set:
--
--   * MLRO          — Money Laundering Reporting Officer. A regulated role in
--                     many jurisdictions (UK, KE, EU). Has authority to file
--                     SARs and sign off on regulatory submissions.
--   * CASE_MANAGER  — Owns case lifecycle (assignment, escalation, closure)
--                     but not SAR approval or risk-rule changes.
--   * AUDITOR       — Read-only audit access. Legally separated from operators
--                     to preserve four-eyes / separation-of-duties.
--
-- These three roles are seeded both globally (psp_id IS NULL) and for each
-- demo PSP, mirroring V127's pattern. Permissions are scoped to the role's
-- function: AUDITOR is read-only, CASE_MANAGER owns case workflow without
-- SAR approval, MLRO owns SAR/regulatory submission workflow.
--
-- Idempotent: every INSERT is gated by NOT EXISTS, so re-running on a
-- populated DB is a no-op.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Global MLRO / CASE_MANAGER / AUDITOR roles (psp_id IS NULL)
-- ----------------------------------------------------------------------------
INSERT INTO roles (name, description, psp_id)
SELECT 'MLRO',
       'Money Laundering Reporting Officer. Regulated role with authority to '
       || 'approve and file Suspicious Activity Reports and regulatory submissions.',
       NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'MLRO' AND psp_id IS NULL);

INSERT INTO roles (name, description, psp_id)
SELECT 'CASE_MANAGER',
       'Compliance case manager. Owns case lifecycle (assignment, escalation, '
       || 'closure) and case-evidence handling. Cannot approve SARs.',
       NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'CASE_MANAGER' AND psp_id IS NULL);

INSERT INTO roles (name, description, psp_id)
SELECT 'AUDITOR',
       'Read-only auditor. Reads cases, SARs, audit logs, transactions, and '
       || 'screening results. No write access — preserves separation of duties.',
       NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'AUDITOR' AND psp_id IS NULL);

-- ----------------------------------------------------------------------------
-- 2. Per-PSP MLRO / CASE_MANAGER / AUDITOR roles for each demo PSP
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    psp_code_val VARCHAR;
    psp_id_val BIGINT;
    role_name VARCHAR;
    role_desc VARCHAR;
BEGIN
    FOR psp_code_val IN SELECT unnest(ARRAY['DEMO_VELOCITY', 'DEMO_MWANANCHI', 'DEMO_APEX'])
    LOOP
        SELECT psp_id INTO psp_id_val FROM psps WHERE psp_code = psp_code_val;
        IF psp_id_val IS NULL THEN CONTINUE; END IF;

        FOR role_name, role_desc IN
            SELECT * FROM (VALUES
                ('MLRO',
                 'Money Laundering Reporting Officer for this PSP. Approves SARs and signs off regulatory submissions.'),
                ('CASE_MANAGER',
                 'Case manager for this PSP. Owns case lifecycle without SAR approval authority.'),
                ('AUDITOR',
                 'Read-only auditor for this PSP. No write access to cases, SARs, or rules.')
            ) AS t(n, d)
        LOOP
            IF NOT EXISTS (SELECT 1 FROM roles WHERE name = role_name AND psp_id = psp_id_val) THEN
                INSERT INTO roles (name, description, psp_id)
                VALUES (role_name, role_desc, psp_id_val);
            END IF;
        END LOOP;
    END LOOP;
END$$;

-- ----------------------------------------------------------------------------
-- 3. role_permissions_dynamic — populate permissions for each role
-- ----------------------------------------------------------------------------
-- MLRO (global + per-PSP) — full SAR / regulatory submission authority +
-- case ownership. No system / role / user mgmt (those stay with PSP_ADMIN).
INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('CREATE_CASES'),('ASSIGN_CASES'),('CLOSE_CASES'),
    ('ESCALATE_CASES'),('REOPEN_CASES'),('ADD_CASE_NOTES'),('ADD_CASE_EVIDENCE'),
    ('VIEW_SAR'),('CREATE_SAR'),('APPROVE_SAR'),('FILE_SAR'),('REJECT_SAR'),('AMEND_SAR'),
    ('VIEW_PII'),('EXPORT_DATA'),
    ('VIEW_TRANSACTION_DETAILS'),('VIEW_SCREENING_RESULTS'),
    ('MANAGE_WATCHLISTS'),('WHITELIST_ENTITY'),('OVERRIDE_SCREENING_MATCH'),
    ('VIEW_AUDIT_LOGS'),
    ('MERCHANT_VIEW'),('REPORT_VIEW')
) p(permission)
WHERE r.name = 'MLRO'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );

-- CASE_MANAGER (global + per-PSP) — case workflow only. NO SAR approval.
INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('CREATE_CASES'),('ASSIGN_CASES'),('CLOSE_CASES'),
    ('ESCALATE_CASES'),('REOPEN_CASES'),('ADD_CASE_NOTES'),('ADD_CASE_EVIDENCE'),
    ('VIEW_SAR'),('CREATE_SAR'),
    ('VIEW_TRANSACTION_DETAILS'),('VIEW_SCREENING_RESULTS'),
    ('MERCHANT_VIEW'),('REPORT_VIEW')
) p(permission)
WHERE r.name = 'CASE_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );

-- AUDITOR (global + per-PSP) — read-only across cases, SARs, transactions,
-- screening, and audit logs. No write permissions, no PII export.
INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('VIEW_SAR'),
    ('VIEW_TRANSACTION_DETAILS'),('VIEW_SCREENING_RESULTS'),
    ('VIEW_AUDIT_LOGS'),
    ('MERCHANT_VIEW'),('REPORT_VIEW')
) p(permission)
WHERE r.name = 'AUDITOR'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );
