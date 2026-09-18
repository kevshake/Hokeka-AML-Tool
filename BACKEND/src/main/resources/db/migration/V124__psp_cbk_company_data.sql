-- ============================================================
-- V124: PSP CBK company data tables
--
-- Backs the 11 CBK GDI reporting entities for PSP company
-- configuration: directors, shareholders, trustees, senior
-- management, products, trust accounts, and tariff templates.
-- Also adds CBK columns to psps (idempotent ADD COLUMN IF NOT EXISTS).
--
-- No FK constraint on psp_id to psps because we verified the
-- PK column is psp_id (V3) -- but we use raw BIGINT NOT NULL
-- plus an index on every child table (per project hard rules).
-- ============================================================

-- ---------------------------------------------------------------
-- 1. CBK columns on psps table (already in entity; add idempotently)
-- ---------------------------------------------------------------
ALTER TABLE psps ADD COLUMN IF NOT EXISTS cbk_institution_code   VARCHAR(32);
ALTER TABLE psps ADD COLUMN IF NOT EXISTS cbk_reporting_enabled  BOOLEAN DEFAULT FALSE;
ALTER TABLE psps ADD COLUMN IF NOT EXISTS cbk_client_id          VARCHAR(128);
ALTER TABLE psps ADD COLUMN IF NOT EXISTS cbk_client_secret      VARCHAR(256);

-- ---------------------------------------------------------------
-- 2. psp_directors  (#2 SCHED_OF_DIR)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psp_directors (
    id                    BIGSERIAL     PRIMARY KEY,
    psp_id                BIGINT        NOT NULL,
    director_names        VARCHAR(512),
    director_gender       VARCHAR(16),
    type_of_director      VARCHAR(64),
    dob                   DATE,
    nationality           VARCHAR(64),
    resident_country      VARCHAR(64),
    id_no_passport        VARCHAR(128),
    pin                   VARCHAR(64),
    contact_number        VARCHAR(64),
    qualifications        TEXT,
    other_directorships   TEXT,
    date_of_appointment   DATE,
    date_of_retirement    DATE,
    retirement_reason     TEXT,
    disclosures           TEXT,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_psp_directors_psp_id ON psp_directors (psp_id);

COMMENT ON TABLE  psp_directors IS 'CBK GDI #2 – Schedule of Directors (annual, Jan 5)';

-- ---------------------------------------------------------------
-- 3. psp_shareholders  (#4 SCHED_OF_SHARE_HLDRS)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psp_shareholders (
    id                      BIGSERIAL     PRIMARY KEY,
    psp_id                  BIGINT        NOT NULL,
    shareholder_name        VARCHAR(512),
    shareholder_gender      VARCHAR(16),
    shareholder_type        VARCHAR(64),
    dob_or_reg_date         DATE,
    nationality             VARCHAR(64),
    resident_country        VARCHAR(64),
    country_of_inc          VARCHAR(64),
    id_no_passport          VARCHAR(128),
    pin                     VARCHAR(64),
    contact_number          VARCHAR(64),
    qualifications          TEXT,
    previous_employment     TEXT,
    onboarding_date         DATE,
    no_of_shares_held       BIGINT,
    share_value             NUMERIC(18,4),
    percentage_of_share     NUMERIC(7,4),
    created_at              TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_psp_shareholders_psp_id ON psp_shareholders (psp_id);

COMMENT ON TABLE  psp_shareholders IS 'CBK GDI #4 – Schedule of Shareholders (annual, Jan 4)';

-- ---------------------------------------------------------------
-- 4. psp_trustees  (#3 SCHED_OF_TRUSTEES)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psp_trustees (
    id                       BIGSERIAL     PRIMARY KEY,
    psp_id                   BIGINT        NOT NULL,
    trust_comp_name          VARCHAR(512),
    directors_trust_comp     TEXT,
    trustee_names            VARCHAR(512),
    trustee_gender           VARCHAR(16),
    dob                      DATE,
    nationality              VARCHAR(64),
    resident_country         VARCHAR(64),
    id_no_passport           VARCHAR(128),
    pin                      VARCHAR(64),
    contact_number           VARCHAR(64),
    qualifications           TEXT,
    others_trusteeships      TEXT,
    disclosures              TEXT,
    shareholders             TEXT,
    shareholding_percentage  NUMERIC(7,4),
    created_at               TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_psp_trustees_psp_id ON psp_trustees (psp_id);

COMMENT ON TABLE  psp_trustees IS 'CBK GDI #3 – Schedule of Trustees (annual, Jan 5)';

-- ---------------------------------------------------------------
-- 5. psp_senior_management  (#1 SENIOR_MNGT_SCHEDULE)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psp_senior_management (
    id                    BIGSERIAL     PRIMARY KEY,
    psp_id                BIGINT        NOT NULL,
    officer_names         VARCHAR(512),
    gender                VARCHAR(16),
    designation           VARCHAR(128),
    dob                   DATE,
    nationality           VARCHAR(64),
    id_no                 VARCHAR(128),
    tax_id                VARCHAR(128),
    qualification         TEXT,
    date_of_emp           DATE,
    emp_type              VARCHAR(64),
    retirement_dt         DATE,
    external_affliates    TEXT,
    other_disclosure      TEXT,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_psp_senior_management_psp_id ON psp_senior_management (psp_id);

COMMENT ON TABLE  psp_senior_management IS 'CBK GDI #1 – Senior Management Schedule (annual, Jan 5)';

-- ---------------------------------------------------------------
-- 6. psp_products  (#10 PSP_PRODUCTS_INFO)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psp_products (
    id                           BIGSERIAL     PRIMARY KEY,
    psp_id                       BIGINT        NOT NULL,
    product_name                 VARCHAR(256),
    product_ownership_flag       VARCHAR(16),
    product_ownership_category   VARCHAR(64),
    product_partner_name         VARCHAR(256),
    product_transaction_code     VARCHAR(64),
    gender_segment               VARCHAR(16),
    status_code                  VARCHAR(32),
    band_code                    VARCHAR(32),
    no_of_customers              BIGINT,
    no_of_transactions           BIGINT,
    value_of_transactions        NUMERIC(18,4),
    created_at                   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_psp_products_psp_id ON psp_products (psp_id);

COMMENT ON TABLE  psp_products IS 'CBK GDI #10 – Products Info (monthly, day 1)';

-- ---------------------------------------------------------------
-- 7. psp_trust_accounts  (#11 TRUSTACCOUNT_DATA)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psp_trust_accounts (
    id                              BIGSERIAL     PRIMARY KEY,
    psp_id                          BIGINT        NOT NULL,
    bank_id                         VARCHAR(64),
    bank_account_number             VARCHAR(128),
    trust_acc_dr_type_code          VARCHAR(32),
    org_receiving_donation          VARCHAR(256),
    sector_code                     VARCHAR(32),
    trust_acc_int_utilized_details  TEXT,
    opening_balance                 NUMERIC(18,4),
    principal_amount                NUMERIC(18,4),
    interest_earned                 NUMERIC(18,4),
    closing_balance                 NUMERIC(18,4),
    interest_utilized               NUMERIC(18,4),
    trust_fields                    TEXT,
    as_of_date                      DATE,
    created_at                      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_psp_trust_accounts_psp_id ON psp_trust_accounts (psp_id);

COMMENT ON TABLE  psp_trust_accounts IS 'CBK GDI #11 – Trust Account balances (daily)';

-- ---------------------------------------------------------------
-- 8. psp_tariff_templates  (#15 PAYMENT_GATEWAY_TARIFFS)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psp_tariff_templates (
    id                            BIGSERIAL     PRIMARY KEY,
    psp_id                        BIGINT        NOT NULL,
    channel_used                  VARCHAR(128),
    channel_partner_name          VARCHAR(256),
    charge_description            TEXT,
    percentage_transaction_cost   NUMERIC(7,4),
    absolute_transaction_cost     NUMERIC(18,4),
    effective_from                DATE,
    effective_to                  DATE,
    created_at                    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_psp_tariff_templates_psp_id ON psp_tariff_templates (psp_id);

COMMENT ON TABLE  psp_tariff_templates IS 'CBK GDI #15 – Payment Gateway Tariff Templates (monthly)';
