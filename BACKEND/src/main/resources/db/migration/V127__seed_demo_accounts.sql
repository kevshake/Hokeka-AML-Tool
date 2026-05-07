-- ============================================================================
-- V127: Seed demo accounts for Hokeka multi-tenant platform demo
--
-- Purpose: provide login-ready accounts that showcase the platform's three
-- tiers of access:
--   1. Hokeka Super Admin   — sees all PSPs, manages the platform
--   2. Hokeka Platform Admin — operates the platform (no cross-PSP user mgmt)
--   3. PSP-scoped users      — see only their own PSP's data
--
-- This migration ships with prod (NOT in db/migration-dev/) because these
-- accounts are operational starter accounts, not throwaway test fixtures.
-- All passwords default to "Hokeka2026!" (bcrypt 10 rounds, hash below);
-- operators are expected to change them on first login or by issuing an
-- admin password reset before exposing the system to anyone outside the team.
--
-- Idempotent: every INSERT is gated by NOT EXISTS / ON CONFLICT, so re-running
-- on a populated DB is a no-op.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Hokeka platform sentinel PSP + 3 demo PSPs
-- ----------------------------------------------------------------------------
-- HOKEKA_PLATFORM holds the super admin / platform admin so we don't need
-- nullable psp_id on platform_users (the User entity declares nullable=false).
INSERT INTO psps (psp_code, legal_name, trading_name, country, contact_email,
                  status, billing_plan, currency, is_test_mode, created_at)
VALUES ('HOKEKA_PLATFORM', 'Hokeka Inc.', 'Hokeka', 'KEN',
        'platform@hokeka.com', 'ACTIVE', 'INTERNAL', 'USD', false, NOW())
ON CONFLICT (psp_code) DO NOTHING;

-- Demo PSPs (the three tenants we'll show in the dashboard)
INSERT INTO psps (psp_code, legal_name, trading_name, country, contact_email,
                  status, billing_plan, currency, is_test_mode, created_at)
VALUES
    ('DEMO_VELOCITY', 'Velocity Payments LLC', 'Velocity Pay', 'USA',
     'admin@velocitypay.demo', 'ACTIVE', 'SUBSCRIPTION', 'USD', true, NOW()),
    ('DEMO_MWANANCHI', 'Mwananchi Bank Plc', 'Mwananchi', 'KEN',
     'compliance@mwananchi.demo', 'ACTIVE', 'SUBSCRIPTION', 'KES', true, NOW()),
    ('DEMO_APEX', 'Apex Remit Global Ltd', 'Apex Remit', 'GBR',
     'admin@apexremit.demo', 'ACTIVE', 'PAY_AS_YOU_GO', 'GBP', true, NOW())
ON CONFLICT (psp_code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. Global Hokeka roles (psp_id IS NULL)
-- ----------------------------------------------------------------------------
INSERT INTO roles (name, description, psp_id)
SELECT 'SUPER_ADMIN',
       'Hokeka super-administrator. Sees every PSP, every user, every case. '
       || 'Can configure the platform itself.',
       NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SUPER_ADMIN' AND psp_id IS NULL);

INSERT INTO roles (name, description, psp_id)
SELECT 'PLATFORM_ADMIN',
       'Hokeka platform administrator. Operates the platform (deploys, '
       || 'monitors, supports PSPs) but does not own customer data.',
       NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'PLATFORM_ADMIN' AND psp_id IS NULL);

-- ----------------------------------------------------------------------------
-- 3. Per-PSP roles (PSP_ADMIN, PSP_USER, COMPLIANCE_OFFICER) for each demo PSP
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
                ('PSP_ADMIN',          'PSP administrator. Manages users, rules, and merchants for this PSP.'),
                ('PSP_USER',           'PSP operator. Views cases, alerts, and screening results for this PSP.'),
                ('COMPLIANCE_OFFICER', 'Compliance officer for this PSP. Owns SAR workflow and case decisions.')
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
-- 4. role_permissions_dynamic — populates Role.permissions @ElementCollection
--    Permissions enum lives in com.posgateway.aml.model.Permission.
--    Stored as VARCHAR (Enum.name()).
-- ----------------------------------------------------------------------------
-- SUPER_ADMIN — every permission we have today
INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('CREATE_CASES'),('ASSIGN_CASES'),('CLOSE_CASES'),
    ('ESCALATE_CASES'),('REOPEN_CASES'),('ADD_CASE_NOTES'),('ADD_CASE_EVIDENCE'),
    ('VIEW_SAR'),('CREATE_SAR'),('APPROVE_SAR'),('FILE_SAR'),('REJECT_SAR'),('AMEND_SAR'),
    ('VIEW_PII'),('EXPORT_DATA'),('MODIFY_RISK_SCORES'),
    ('VIEW_TRANSACTION_DETAILS'),('VIEW_SCREENING_RESULTS'),
    ('MANAGE_WATCHLISTS'),('WHITELIST_ENTITY'),('OVERRIDE_SCREENING_MATCH'),
    ('MANAGE_USERS'),('MANAGE_ROLES'),('MANAGE_RULES'),
    ('VIEW_AUDIT_LOGS'),('CONFIGURE_SYSTEM'),
    ('MANAGE_PSP'),('MANAGE_PSP_THEME'),
    ('PSP_SETTINGS_VIEW'),('PSP_SETTINGS_EDIT'),('PSP_UI_EDIT'),
    ('MERCHANT_VIEW'),('MERCHANT_EDIT'),('REPORT_VIEW')
) p(permission)
WHERE r.name = 'SUPER_ADMIN' AND r.psp_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );

-- PLATFORM_ADMIN — operate the platform; no cross-PSP user/role mgmt
INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('VIEW_SAR'),('VIEW_AUDIT_LOGS'),
    ('VIEW_TRANSACTION_DETAILS'),('VIEW_SCREENING_RESULTS'),
    ('CONFIGURE_SYSTEM'),('MANAGE_PSP'),
    ('PSP_SETTINGS_VIEW'),('PSP_UI_EDIT'),
    ('MERCHANT_VIEW'),('REPORT_VIEW')
) p(permission)
WHERE r.name = 'PLATFORM_ADMIN' AND r.psp_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );

-- PSP_ADMIN — full control within their PSP
INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('CREATE_CASES'),('ASSIGN_CASES'),('CLOSE_CASES'),
    ('ESCALATE_CASES'),('REOPEN_CASES'),('ADD_CASE_NOTES'),('ADD_CASE_EVIDENCE'),
    ('VIEW_SAR'),('CREATE_SAR'),('APPROVE_SAR'),('FILE_SAR'),('AMEND_SAR'),
    ('VIEW_PII'),('EXPORT_DATA'),
    ('VIEW_TRANSACTION_DETAILS'),('VIEW_SCREENING_RESULTS'),
    ('MANAGE_WATCHLISTS'),('WHITELIST_ENTITY'),
    ('MANAGE_USERS'),('MANAGE_RULES'),('MANAGE_PSP_THEME'),
    ('PSP_SETTINGS_VIEW'),('PSP_SETTINGS_EDIT'),('PSP_UI_EDIT'),
    ('MERCHANT_VIEW'),('MERCHANT_EDIT'),('REPORT_VIEW')
) p(permission)
WHERE r.name = 'PSP_ADMIN' AND r.psp_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );

-- PSP_USER — read-only operator
INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('ADD_CASE_NOTES'),('VIEW_SAR'),
    ('VIEW_TRANSACTION_DETAILS'),('VIEW_SCREENING_RESULTS'),
    ('MERCHANT_VIEW'),('REPORT_VIEW')
) p(permission)
WHERE r.name = 'PSP_USER' AND r.psp_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );

-- COMPLIANCE_OFFICER — case + SAR workflow within the PSP
INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('CREATE_CASES'),('ASSIGN_CASES'),('CLOSE_CASES'),
    ('ESCALATE_CASES'),('REOPEN_CASES'),('ADD_CASE_NOTES'),('ADD_CASE_EVIDENCE'),
    ('VIEW_SAR'),('CREATE_SAR'),('APPROVE_SAR'),('FILE_SAR'),('AMEND_SAR'),
    ('VIEW_PII'),('VIEW_TRANSACTION_DETAILS'),('VIEW_SCREENING_RESULTS'),
    ('MANAGE_WATCHLISTS'),('WHITELIST_ENTITY'),
    ('MERCHANT_VIEW'),('REPORT_VIEW')
) p(permission)
WHERE r.name = 'COMPLIANCE_OFFICER' AND r.psp_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );

-- ----------------------------------------------------------------------------
-- 5. Demo platform_users
--    Password "Hokeka2026!" — bcrypt-10 hash stored below.
--    To rotate: use BCryptPasswordEncoder(10).encode("...") and replace.
-- ----------------------------------------------------------------------------
-- 5a. Hokeka Super Admin (sees every PSP)
INSERT INTO platform_users (username, password_hash, email, first_name, last_name,
                            role_id, psp_id, enabled, email_verified, created_at)
SELECT 'super.admin',
       '$2a$10$.RGw2iljgrW7KfQzbWsRV.T7uY8y.RFwf0lIoc0bPqAy6LC3QxvVa',
       'super.admin@hokeka.com', 'Hokeka', 'Super Admin',
       (SELECT id FROM roles WHERE name = 'SUPER_ADMIN' AND psp_id IS NULL),
       (SELECT psp_id FROM psps WHERE psp_code = 'HOKEKA_PLATFORM'),
       true, true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM platform_users WHERE username = 'super.admin');

-- 5b. Hokeka Platform Admin (operates platform, no PSP-tenant mgmt)
INSERT INTO platform_users (username, password_hash, email, first_name, last_name,
                            role_id, psp_id, enabled, email_verified, created_at)
SELECT 'platform.admin',
       '$2a$10$.RGw2iljgrW7KfQzbWsRV.T7uY8y.RFwf0lIoc0bPqAy6LC3QxvVa',
       'platform.admin@hokeka.com', 'Hokeka', 'Platform Admin',
       (SELECT id FROM roles WHERE name = 'PLATFORM_ADMIN' AND psp_id IS NULL),
       (SELECT psp_id FROM psps WHERE psp_code = 'HOKEKA_PLATFORM'),
       true, true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM platform_users WHERE username = 'platform.admin');

-- 5c. Velocity Pay (USD, US PSP) — PSP_ADMIN + COMPLIANCE_OFFICER + PSP_USER
INSERT INTO platform_users (username, password_hash, email, first_name, last_name,
                            role_id, psp_id, enabled, email_verified, created_at)
SELECT 'velocity.admin',
       '$2a$10$.RGw2iljgrW7KfQzbWsRV.T7uY8y.RFwf0lIoc0bPqAy6LC3QxvVa',
       'admin@velocitypay.demo', 'Velocity', 'Admin',
       (SELECT id FROM roles WHERE name = 'PSP_ADMIN'
        AND psp_id = (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_VELOCITY')),
       (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_VELOCITY'),
       true, true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM platform_users WHERE username = 'velocity.admin');

INSERT INTO platform_users (username, password_hash, email, first_name, last_name,
                            role_id, psp_id, enabled, email_verified, created_at)
SELECT 'velocity.compliance',
       '$2a$10$.RGw2iljgrW7KfQzbWsRV.T7uY8y.RFwf0lIoc0bPqAy6LC3QxvVa',
       'compliance@velocitypay.demo', 'Velocity', 'Compliance',
       (SELECT id FROM roles WHERE name = 'COMPLIANCE_OFFICER'
        AND psp_id = (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_VELOCITY')),
       (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_VELOCITY'),
       true, true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM platform_users WHERE username = 'velocity.compliance');

INSERT INTO platform_users (username, password_hash, email, first_name, last_name,
                            role_id, psp_id, enabled, email_verified, created_at)
SELECT 'velocity.user',
       '$2a$10$.RGw2iljgrW7KfQzbWsRV.T7uY8y.RFwf0lIoc0bPqAy6LC3QxvVa',
       'user@velocitypay.demo', 'Velocity', 'Operator',
       (SELECT id FROM roles WHERE name = 'PSP_USER'
        AND psp_id = (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_VELOCITY')),
       (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_VELOCITY'),
       true, true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM platform_users WHERE username = 'velocity.user');

-- 5d. Mwananchi Bank (KES, KE) — PSP_ADMIN + COMPLIANCE_OFFICER
INSERT INTO platform_users (username, password_hash, email, first_name, last_name,
                            role_id, psp_id, enabled, email_verified, created_at)
SELECT 'mwananchi.admin',
       '$2a$10$.RGw2iljgrW7KfQzbWsRV.T7uY8y.RFwf0lIoc0bPqAy6LC3QxvVa',
       'admin@mwananchi.demo', 'Mwananchi', 'Admin',
       (SELECT id FROM roles WHERE name = 'PSP_ADMIN'
        AND psp_id = (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_MWANANCHI')),
       (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_MWANANCHI'),
       true, true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM platform_users WHERE username = 'mwananchi.admin');

INSERT INTO platform_users (username, password_hash, email, first_name, last_name,
                            role_id, psp_id, enabled, email_verified, created_at)
SELECT 'mwananchi.compliance',
       '$2a$10$.RGw2iljgrW7KfQzbWsRV.T7uY8y.RFwf0lIoc0bPqAy6LC3QxvVa',
       'compliance@mwananchi.demo', 'Mwananchi', 'Compliance',
       (SELECT id FROM roles WHERE name = 'COMPLIANCE_OFFICER'
        AND psp_id = (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_MWANANCHI')),
       (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_MWANANCHI'),
       true, true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM platform_users WHERE username = 'mwananchi.compliance');

-- 5e. Apex Remit (GBP, UK) — PSP_ADMIN only
INSERT INTO platform_users (username, password_hash, email, first_name, last_name,
                            role_id, psp_id, enabled, email_verified, created_at)
SELECT 'apex.admin',
       '$2a$10$.RGw2iljgrW7KfQzbWsRV.T7uY8y.RFwf0lIoc0bPqAy6LC3QxvVa',
       'admin@apexremit.demo', 'Apex', 'Admin',
       (SELECT id FROM roles WHERE name = 'PSP_ADMIN'
        AND psp_id = (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_APEX')),
       (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_APEX'),
       true, true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM platform_users WHERE username = 'apex.admin');

-- ============================================================================
-- LOGIN CREDENTIALS (rotate immediately on go-live)
-- ============================================================================
--   Username                Password         Role / Scope
--   ----------------------- ---------------- ----------------------------------
--   super.admin             Hokeka2026!      Hokeka Super Admin (all PSPs)
--   platform.admin          Hokeka2026!      Hokeka Platform Admin (no tenants)
--   velocity.admin          Hokeka2026!      PSP_ADMIN @ Velocity Pay (USA)
--   velocity.compliance     Hokeka2026!      COMPLIANCE_OFFICER @ Velocity Pay
--   velocity.user           Hokeka2026!      PSP_USER @ Velocity Pay
--   mwananchi.admin         Hokeka2026!      PSP_ADMIN @ Mwananchi Bank (KE)
--   mwananchi.compliance    Hokeka2026!      COMPLIANCE_OFFICER @ Mwananchi
--   apex.admin              Hokeka2026!      PSP_ADMIN @ Apex Remit (UK)
-- ============================================================================
