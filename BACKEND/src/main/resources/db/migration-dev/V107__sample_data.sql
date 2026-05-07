-- V107__sample_data.sql
-- Comprehensive sample data for all entities in the database
-- Uses actual schema from migrations V1-V106

-- ============================================================================
-- 1. ADDITIONAL PSPs (beyond V99)
-- ============================================================================
INSERT INTO psps (psp_code, legal_name, trading_name, country, contact_email, status, billing_plan, created_at)
VALUES 
    ('PAYPAL_PSP', 'PayPal Holdings Inc.', 'PayPal', 'USA', 'admin@paypal.com', 'ACTIVE', 'SUBSCRIPTION', NOW() - INTERVAL '6 months'),
    ('STRIPE_PSP', 'Stripe Inc.', 'Stripe', 'USA', 'admin@stripe.com', 'ACTIVE', 'SUBSCRIPTION', NOW() - INTERVAL '3 months'),
    ('SQUARE_PSP', 'Square Inc.', 'Square', 'USA', 'admin@square.com', 'ACTIVE', 'PAY_AS_YOU_GO', NOW() - INTERVAL '2 months')
ON CONFLICT (psp_code) DO NOTHING;

-- ============================================================================
-- 2. ADDITIONAL ROLES for new PSPs
-- ============================================================================
INSERT INTO roles (name, description, psp_id)
SELECT 'ADMIN', 'PayPal Administrator', (SELECT psp_id FROM psps WHERE psp_code = 'PAYPAL_PSP')
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN' AND psp_id = (SELECT psp_id FROM psps WHERE psp_code = 'PAYPAL_PSP'));

INSERT INTO roles (name, description, psp_id)
SELECT 'COMPLIANCE_OFFICER', 'PayPal Compliance Officer', (SELECT psp_id FROM psps WHERE psp_code = 'PAYPAL_PSP')
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'COMPLIANCE_OFFICER' AND psp_id = (SELECT psp_id FROM psps WHERE psp_code = 'PAYPAL_PSP'));

-- ============================================================================
-- 3. ADDITIONAL USERS
-- ============================================================================
INSERT INTO platform_users (username, password_hash, email, first_name, last_name, role_id, psp_id, enabled, created_at)
SELECT 'paypal_admin', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'admin@paypal.com', 'PayPal', 'Admin',
       (SELECT id FROM roles WHERE name = 'ADMIN' AND psp_id = (SELECT psp_id FROM psps WHERE psp_code = 'PAYPAL_PSP')),
       (SELECT psp_id FROM psps WHERE psp_code = 'PAYPAL_PSP'), true, NOW()
WHERE NOT EXISTS (SELECT 1 FROM platform_users WHERE username = 'paypal_admin');

-- ============================================================================
-- 4. MERCHANTS (using actual schema from V2)
-- ============================================================================
-- Merchants use legal_name, trading_name per V2 schema
INSERT INTO merchants (
    legal_name, trading_name, country, registration_number, tax_id, mcc, business_type,
    expected_monthly_volume, transaction_channel, website,
    address_street, address_city, address_state, address_postal_code, address_country,
    status, created_at, updated_at
)
SELECT 
    'TechFlow Retail Ltd', 'TechFlow Retail', 'USA', 'REG001', 'TAX001', '5999', 'CORPORATION',
    50000000, 'ONLINE', 'https://techflow-retail.com',
    '123 Main St', 'New York', 'NY', '10001', 'USA',
    'ACTIVE', NOW() - INTERVAL '1 year', NOW()
WHERE NOT EXISTS (SELECT 1 FROM merchants WHERE registration_number = 'REG001' AND country = 'USA');

INSERT INTO merchants (
    legal_name, trading_name, country, registration_number, tax_id, mcc, business_type,
    expected_monthly_volume, transaction_channel, website,
    address_street, address_city, address_state, address_postal_code, address_country,
    status, created_at, updated_at
)
SELECT 
    'Global Commerce Inc', 'Global Commerce', 'GBR', 'REG002', 'TAX002', '5999', 'CORPORATION',
    100000000, 'ONLINE', 'https://global-commerce.com',
    '456 High St', 'London', NULL, 'SW1A 1AA', 'GBR',
    'ACTIVE', NOW() - INTERVAL '8 months', NOW()
WHERE NOT EXISTS (SELECT 1 FROM merchants WHERE registration_number = 'REG002' AND country = 'GBR');

INSERT INTO merchants (
    legal_name, trading_name, country, registration_number, tax_id, mcc, business_type,
    expected_monthly_volume, transaction_channel, website,
    address_street, address_city, address_state, address_postal_code, address_country,
    status, created_at, updated_at
)
SELECT 
    'High Risk Trading Co', 'High Risk Trading', 'CHN', 'REG003', 'TAX003', '5999', 'CORPORATION',
    200000000, 'ONLINE', 'https://hrt-co.com',
    '789 Trade Ave', 'Shanghai', NULL, '200000', 'CHN',
    'PENDING_SCREENING', NOW() - INTERVAL '2 weeks', NOW()
WHERE NOT EXISTS (SELECT 1 FROM merchants WHERE registration_number = 'REG003' AND country = 'CHN');

-- ============================================================================
-- 5. TRANSACTIONS (using actual schema from V1)
-- ============================================================================
-- Insert 50 low-risk transactions
DO $$
DECLARE
    i INTEGER;
    merch_id BIGINT;
    txn_time TIMESTAMP;
BEGIN
    SELECT merchant_id INTO merch_id FROM merchants WHERE registration_number = 'REG001' AND country = 'USA' LIMIT 1;
    
    IF merch_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM transactions WHERE pan_hash = 'hash_sample_1') THEN
        FOR i IN 1..50 LOOP
            txn_time := NOW() - INTERVAL '1 day' - (i * INTERVAL '1 minute');
            INSERT INTO transactions (
                iso_msg, pan_hash, merchant_id, terminal_id, amount_cents, currency, txn_ts,
                emv_tags, acquirer_response, created_at
            ) VALUES (
                '0200', 'hash_sample_' || i, merch_id::TEXT, 'TERM001', 50000, 'USD', txn_time,
                '{}', '00', txn_time
            );
        END LOOP;
    END IF;
END $$;

-- Insert 20 high-risk transactions
DO $$
DECLARE
    i INTEGER;
    merch_id BIGINT;
    txn_time TIMESTAMP;
BEGIN
    SELECT merchant_id INTO merch_id FROM merchants WHERE registration_number = 'REG002' AND country = 'GBR' LIMIT 1;
    
    IF merch_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM transactions WHERE pan_hash = 'hash_high_sample_1') THEN
        FOR i IN 1..20 LOOP
            txn_time := NOW() - INTERVAL '2 days' - (i * INTERVAL '1 minute');
            INSERT INTO transactions (
                iso_msg, pan_hash, merchant_id, terminal_id, amount_cents, currency, txn_ts,
                emv_tags, acquirer_response, created_at
            ) VALUES (
                '0200', 'hash_high_sample_' || i, merch_id::TEXT, 'TERM002', 150000, 'GBP', txn_time,
                '{}', '00', txn_time
            );
        END LOOP;
    END IF;
END $$;

-- ============================================================================
-- 6. COMPLIANCE CASES (using actual schema from V2)
-- ============================================================================
DO $$
DECLARE
    merch_id BIGINT;
BEGIN
    SELECT merchant_id INTO merch_id FROM merchants WHERE registration_number = 'REG002' AND country = 'GBR' LIMIT 1;
    
    IF merch_id IS NOT NULL THEN
        INSERT INTO compliance_cases (
            merchant_id, case_type, case_status, priority,
            assigned_to, resolution, created_at, due_date
        ) VALUES (
            merch_id, 'ALERT', 'OPEN', 'HIGH',
            NULL, NULL, NOW() - INTERVAL '5 days', NOW() + INTERVAL '2 days'
        );
    END IF;
END $$;

DO $$
DECLARE
    merch_id BIGINT;
BEGIN
    SELECT merchant_id INTO merch_id FROM merchants WHERE registration_number = 'REG003' AND country = 'CHN' LIMIT 1;
    
    IF merch_id IS NOT NULL THEN
        INSERT INTO compliance_cases (
            merchant_id, case_type, case_status, priority,
            assigned_to, resolution, created_at, due_date
        ) VALUES (
            merch_id, 'ONBOARDING', 'OPEN', 'MEDIUM',
            NULL, NULL, NOW() - INTERVAL '10 days', NOW() + INTERVAL '5 days'
        );
    END IF;
END $$;

-- ============================================================================
-- 7. ALERTS (using actual schema from V1)
-- ============================================================================
DO $$
DECLARE
    merch_id BIGINT;
BEGIN
    SELECT merchant_id INTO merch_id FROM merchants WHERE registration_number = 'REG002' AND country = 'GBR' LIMIT 1;
    
    IF merch_id IS NOT NULL THEN
        INSERT INTO alerts (
            txn_id, score, action, reason, status, investigator, notes, created_at
        ) VALUES (
            NULL, 0.85, 'REVIEW', 'Multiple high-value transactions', 'open', NULL, 'Investigation required', NOW() - INTERVAL '1 day'
        );
    END IF;
END $$;

-- ============================================================================
-- 8. AUDIT LOGS (Sample actions using actual schema from V4)
-- ============================================================================
DO $$
DECLARE
    admin_id INTEGER;
BEGIN
    SELECT id INTO admin_id FROM platform_users WHERE username = 'admin' LIMIT 1;
    
    IF admin_id IS NOT NULL THEN
        INSERT INTO audit_logs_enhanced (
            user_id, username, user_role, action_type, entity_type, entity_id,
            timestamp, ip_address, success, reason
        ) VALUES (
            admin_id::TEXT, 'admin', 'ADMIN', 'LOGIN', 'USER', admin_id::TEXT,
            NOW() - INTERVAL '1 hour', '192.168.1.1', true, 'User login'
        );
    END IF;
END $$;

-- ============================================================================
-- 9. HIGH RISK COUNTRIES (Additional)
-- ============================================================================
INSERT INTO high_risk_countries (country_code, country_name, risk_level, added_at)
VALUES 
    ('CN', 'China', 'HIGH', NOW()),
    ('RU', 'Russia', 'HIGH', NOW())
ON CONFLICT (country_code) DO NOTHING;

-- ============================================================================
-- END OF SAMPLE DATA
-- ============================================================================

-- ============================================================================
-- # Migration gating notes
-- ============================================================================
-- This file lives in db/migration-dev/, NOT db/migration/. It is loaded ONLY
-- by the `dev` Spring profile via:
--     spring.flyway.locations=classpath:db/migration,classpath:db/migration-dev
--
-- Production profiles (`prod`, `production`) load only classpath:db/migration
-- and therefore NEVER apply this seed data.
--
-- Existing-prod deploy steps (DBs that previously ran V107 from db/migration/):
--   1. The row for V107 already lives in flyway_schema_history.
--   2. After this change, V107 no longer exists under classpath:db/migration.
--   3. spring.flyway.ignore-missing-migrations=true (prod) tolerates the gap.
--   4. No data change. No DELETE from flyway_schema_history is required.
--   NOTE: if regulators require *removing* the seed rows from prod, do that
--   via a NEW forward migration (e.g. V120__purge_dev_seed_data.sql) — never
--   by editing or repairing flyway_schema_history.
--
-- Clean-prod deploy steps (fresh DB):
--   1. V107 is simply absent from the active locations.
--   2. flyway_schema_history will have a gap at version 107 (V108+ apply).
--
-- Existing-dev deploy steps (DBs that previously ran V107):
--   1. V107 is still found, same checksum, same content. Re-validation passes
--      (validate-on-migrate=false on dev).
-- ============================================================================
