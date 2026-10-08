CREATE TABLE psp_api_keys (
    api_key_id BIGSERIAL PRIMARY KEY,
    key_prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    psp_id BIGINT NOT NULL REFERENCES psps(psp_id),
    created_by BIGINT REFERENCES platform_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rotated_at TIMESTAMP,
    revoked_at TIMESTAMP
);

CREATE INDEX idx_psp_api_keys_psp ON psp_api_keys (psp_id);

CREATE TABLE psp_payment_methods (
    payment_method_id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL REFERENCES psps(psp_id),
    token_vault_ref VARCHAR(512) NOT NULL,
    last4 VARCHAR(4) NOT NULL,
    brand VARCHAR(32) NOT NULL,
    expiry_month INTEGER NOT NULL CHECK (expiry_month BETWEEN 1 AND 12),
    expiry_year INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_psp_payment_methods_active
    ON psp_payment_methods (psp_id, is_active);
