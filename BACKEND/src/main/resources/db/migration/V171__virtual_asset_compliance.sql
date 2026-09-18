CREATE TABLE vasp_directory_entries (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    trading_name VARCHAR(255),
    jurisdiction VARCHAR(3) NOT NULL,
    regulator_name VARCHAR(255),
    licence_number VARCHAR(160),
    licence_status VARCHAR(32) NOT NULL,
    licence_valid_until DATE,
    registration_number VARCHAR(160),
    website VARCHAR(500),
    travel_rule_protocols JSONB NOT NULL DEFAULT '[]'::jsonb,
    sanctions_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SCREENED',
    sanctions_screened_at TIMESTAMP,
    beneficial_ownership JSONB NOT NULL DEFAULT '[]'::jsonb,
    wallet_clusters JSONB NOT NULL DEFAULT '[]'::jsonb,
    risk_score INTEGER NOT NULL DEFAULT 0,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
    transfer_decision VARCHAR(24) NOT NULL DEFAULT 'REVIEW',
    review_notes TEXT,
    last_reviewed_at TIMESTAMP,
    next_review_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_vasp_directory_licence UNIQUE (psp_id, jurisdiction, licence_number),
    CONSTRAINT chk_vasp_risk_score CHECK (risk_score BETWEEN 0 AND 100)
);

CREATE TABLE crypto_wallet_profiles (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    asset_account_id BIGINT NOT NULL REFERENCES asset_accounts(id) ON DELETE RESTRICT,
    customer_id BIGINT NOT NULL REFERENCES multi_asset_customers(id) ON DELETE RESTRICT,
    vasp_id BIGINT REFERENCES vasp_directory_entries(id) ON DELETE SET NULL,
    wallet_address VARCHAR(512) NOT NULL,
    network VARCHAR(64) NOT NULL,
    address_label VARCHAR(255),
    ownership_type VARCHAR(24) NOT NULL DEFAULT 'CUSTOMER',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    screening_interval_hours INTEGER NOT NULL DEFAULT 24,
    last_screened_at TIMESTAMP,
    next_screening_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    latest_risk_score INTEGER,
    latest_categories JSONB NOT NULL DEFAULT '[]'::jsonb,
    latest_provider VARCHAR(128),
    latest_provider_reference VARCHAR(255),
    screening_available BOOLEAN NOT NULL DEFAULT FALSE,
    screening_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_crypto_wallet_profile UNIQUE (psp_id, network, wallet_address),
    CONSTRAINT uq_crypto_wallet_account UNIQUE (psp_id, asset_account_id),
    CONSTRAINT chk_wallet_screening_interval CHECK (screening_interval_hours BETWEEN 1 AND 8760),
    CONSTRAINT chk_wallet_risk_score CHECK (latest_risk_score IS NULL OR latest_risk_score BETWEEN 0 AND 100)
);

CREATE TABLE wallet_screening_records (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    wallet_profile_id BIGINT NOT NULL REFERENCES crypto_wallet_profiles(id) ON DELETE RESTRICT,
    transaction_id BIGINT REFERENCES multi_asset_transactions(id) ON DELETE RESTRICT,
    trigger_type VARCHAR(32) NOT NULL,
    provider VARCHAR(128) NOT NULL,
    provider_reference VARCHAR(255),
    available BOOLEAN NOT NULL,
    risk_score INTEGER,
    categories JSONB NOT NULL DEFAULT '[]'::jsonb,
    direct_exposure_percent NUMERIC(8, 5),
    indirect_exposure_percent NUMERIC(8, 5),
    maximum_exposure_depth INTEGER,
    attributions JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    unavailable_reason TEXT,
    screened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retain_until DATE NOT NULL,
    CONSTRAINT chk_wallet_screening_score CHECK (risk_score IS NULL OR risk_score BETWEEN 0 AND 100),
    CONSTRAINT chk_wallet_direct_exposure CHECK (direct_exposure_percent IS NULL OR direct_exposure_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_wallet_indirect_exposure CHECK (indirect_exposure_percent IS NULL OR indirect_exposure_percent BETWEEN 0 AND 100)
);

CREATE TABLE travel_rule_jurisdiction_policies (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    jurisdiction VARCHAR(3) NOT NULL,
    policy_code VARCHAR(80) NOT NULL,
    threshold_usd NUMERIC(24, 8) NOT NULL DEFAULT 0,
    applies_to_all_transfers BOOLEAN NOT NULL DEFAULT FALSE,
    required_fields JSONB NOT NULL DEFAULT '[]'::jsonb,
    verify_originator BOOLEAN NOT NULL DEFAULT TRUE,
    verify_beneficiary BOOLEAN NOT NULL DEFAULT TRUE,
    accepted_protocols JSONB NOT NULL DEFAULT '[]'::jsonb,
    retention_years INTEGER NOT NULL DEFAULT 7,
    effective_from TIMESTAMP NOT NULL,
    effective_until TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    legal_reference VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_travel_rule_policy UNIQUE (psp_id, policy_code),
    CONSTRAINT chk_travel_rule_threshold CHECK (threshold_usd >= 0),
    CONSTRAINT chk_travel_rule_retention CHECK (retention_years >= 7)
);

CREATE TABLE travel_rule_transfers (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL REFERENCES multi_asset_transactions(id) ON DELETE RESTRICT,
    policy_id BIGINT REFERENCES travel_rule_jurisdiction_policies(id) ON DELETE RESTRICT,
    originator_vasp_id BIGINT REFERENCES vasp_directory_entries(id) ON DELETE RESTRICT,
    beneficiary_vasp_id BIGINT REFERENCES vasp_directory_entries(id) ON DELETE RESTRICT,
    jurisdiction VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    originator_verification VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    beneficiary_verification VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    protocol VARCHAR(64),
    encrypted_payload TEXT,
    payload_hash VARCHAR(64),
    provider_message_id VARCHAR(255),
    transmission_attempts INTEGER NOT NULL DEFAULT 0,
    transmitted_at TIMESTAMP,
    acknowledged_at TIMESTAMP,
    failure_reason TEXT,
    retain_until DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_travel_rule_transfer UNIQUE (psp_id, transaction_id)
);

CREATE TABLE travel_rule_transmission_attempts (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    travel_rule_transfer_id BIGINT NOT NULL REFERENCES travel_rule_transfers(id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    protocol VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    provider_message_id VARCHAR(255),
    response_code VARCHAR(64),
    failure_reason TEXT,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retain_until DATE NOT NULL,
    CONSTRAINT uq_travel_rule_attempt UNIQUE (travel_rule_transfer_id, attempt_number),
    CONSTRAINT uq_travel_rule_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE virtual_asset_regulator_access_grants (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    regulator_name VARCHAR(255) NOT NULL,
    jurisdiction VARCHAR(3) NOT NULL,
    access_key_hash VARCHAR(64) NOT NULL UNIQUE,
    scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    allowed_ip_addresses JSONB NOT NULL DEFAULT '[]'::jsonb,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    last_accessed_at TIMESTAMP,
    created_by VARCHAR(160) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE virtual_asset_regulator_access_logs (
    id BIGSERIAL PRIMARY KEY,
    grant_id BIGINT NOT NULL REFERENCES virtual_asset_regulator_access_grants(id) ON DELETE RESTRICT,
    psp_id BIGINT NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    query_hash VARCHAR(64) NOT NULL,
    source_ip VARCHAR(64) NOT NULL,
    user_agent VARCHAR(500),
    rows_returned INTEGER NOT NULL,
    accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retain_until DATE NOT NULL
);

CREATE INDEX idx_vasp_directory_risk ON vasp_directory_entries(psp_id, risk_score DESC, next_review_at);
CREATE INDEX idx_crypto_wallet_due ON crypto_wallet_profiles(psp_id, status, next_screening_at);
CREATE INDEX idx_wallet_screening_history ON wallet_screening_records(wallet_profile_id, screened_at DESC);
CREATE INDEX idx_wallet_screening_transaction ON wallet_screening_records(transaction_id) WHERE transaction_id IS NOT NULL;
CREATE INDEX idx_travel_rule_transfer_status ON travel_rule_transfers(psp_id, status, created_at DESC);
CREATE INDEX idx_regulator_grant_active ON virtual_asset_regulator_access_grants(access_key_hash, expires_at) WHERE revoked_at IS NULL;
CREATE INDEX idx_regulator_access_log_time ON virtual_asset_regulator_access_logs(psp_id, accessed_at DESC);

CREATE OR REPLACE FUNCTION prevent_virtual_asset_evidence_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION '% is append-only compliance evidence', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_wallet_screening_immutable
    BEFORE UPDATE OR DELETE ON wallet_screening_records
    FOR EACH ROW EXECUTE FUNCTION prevent_virtual_asset_evidence_mutation();
CREATE TRIGGER trg_travel_rule_attempt_immutable
    BEFORE UPDATE OR DELETE ON travel_rule_transmission_attempts
    FOR EACH ROW EXECUTE FUNCTION prevent_virtual_asset_evidence_mutation();
CREATE TRIGGER trg_regulator_access_log_immutable
    BEFORE UPDATE OR DELETE ON virtual_asset_regulator_access_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_virtual_asset_evidence_mutation();

COMMENT ON COLUMN travel_rule_transfers.encrypted_payload IS
    'AES-256-GCM ciphertext; plaintext originator and beneficiary data must not be stored in this table';
COMMENT ON TABLE wallet_screening_records IS
    'Append-only wallet attribution and exposure history retained for at least seven years';
COMMENT ON TABLE virtual_asset_regulator_access_logs IS
    'Append-only audit of automated regulator read-only access';

INSERT INTO travel_rule_jurisdiction_policies (
    psp_id, jurisdiction, policy_code, threshold_usd, applies_to_all_transfers,
    required_fields, verify_originator, verify_beneficiary, accepted_protocols,
    retention_years, effective_from, legal_reference
)
SELECT p.psp_id, 'KE', 'KE_VASP_2025_BASELINE', 0, TRUE,
       '["originatorName","originatorAccount","originatorAddress","originatorCountry","beneficiaryName","beneficiaryAccount","beneficiaryVasp"]'::jsonb,
       TRUE, TRUE, '["TRISA","OPENVASP","SYGNA","NOTABENE","CUSTOM_API"]'::jsonb,
       7, TIMESTAMP '2025-11-04 00:00:00',
       'Virtual Asset Service Providers Act, 2025; POCAMLA and applicable regulations'
FROM psps p
ON CONFLICT (psp_id, policy_code) DO NOTHING;
