CREATE TABLE onboarding_invites (
    invite_id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    psp_id BIGINT REFERENCES psps(psp_id),
    role VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_onboarding_invites_active
    ON onboarding_invites (token_hash, expires_at)
    WHERE used_at IS NULL;
