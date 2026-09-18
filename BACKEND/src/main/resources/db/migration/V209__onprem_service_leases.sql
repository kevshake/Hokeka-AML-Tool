-- ============================================================
-- On-prem PSP service licensing / lease registry
-- Migration: V209__onprem_service_leases.sql
-- Entity:    com.posgateway.aml.entity.onprem.OnPremInstance
--
-- Central Hokeka cloud table that authorises PSP on-prem AML
-- instances via client-credentials + multi-day signed leases.
-- Check-in times are server-assigned and jittered across the day
-- so fleets do not stampede the auth server simultaneously.
-- ============================================================

CREATE TABLE IF NOT EXISTS onprem_instances (
    id                      BIGSERIAL PRIMARY KEY,
    psp_id                  BIGINT         NOT NULL,
    instance_id             VARCHAR(128)   NOT NULL,
    client_id               VARCHAR(128)   NOT NULL,
    client_secret_hash      VARCHAR(255)   NOT NULL,
    display_name            VARCHAR(255)   NOT NULL,
    status                  VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
    approved_days           INTEGER        NOT NULL DEFAULT 7,
    lease_until             TIMESTAMP WITH TIME ZONE,
    next_check_at           TIMESTAMP WITH TIME ZONE,
    last_lease_jti          VARCHAR(64),
    last_seen_at            TIMESTAMP WITH TIME ZONE,
    hostname                VARCHAR(255),
    agent_version           VARCHAR(64),
    revoked_at              TIMESTAMP WITH TIME ZONE,
    revoked_by              VARCHAR(128),
    created_by              VARCHAR(128),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_onprem_instances_instance_id UNIQUE (instance_id),
    CONSTRAINT uq_onprem_instances_client_id UNIQUE (client_id),
    CONSTRAINT ck_onprem_instances_approved_days CHECK (approved_days >= 1 AND approved_days <= 365),
    CONSTRAINT ck_onprem_instances_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_onprem_instances_psp_id       ON onprem_instances (psp_id);
CREATE INDEX IF NOT EXISTS idx_onprem_instances_status       ON onprem_instances (status);
CREATE INDEX IF NOT EXISTS idx_onprem_instances_next_check   ON onprem_instances (next_check_at);
CREATE INDEX IF NOT EXISTS idx_onprem_instances_lease_until  ON onprem_instances (lease_until);

COMMENT ON TABLE  onprem_instances                        IS 'On-prem PSP AML instance registry; gates multi-day service leases from Hokeka central auth';
COMMENT ON COLUMN onprem_instances.instance_id            IS 'Stable machine identity asserted by the on-prem instance';
COMMENT ON COLUMN onprem_instances.client_id              IS 'OAuth-style client id for service-to-service auth';
COMMENT ON COLUMN onprem_instances.client_secret_hash      IS 'BCrypt hash of the client secret; raw secret is never stored';
COMMENT ON COLUMN onprem_instances.approved_days          IS 'Admin-configured lease length in days granted on each successful auth/renewal';
COMMENT ON COLUMN onprem_instances.lease_until            IS 'Current lease expiry (UTC); on-prem must stop if this passes without renewal';
COMMENT ON COLUMN onprem_instances.next_check_at          IS 'Server-assigned next mandatory check-in instant (jittered across the day)';
COMMENT ON COLUMN onprem_instances.status                 IS 'ACTIVE | SUSPENDED | REVOKED — only ACTIVE may receive leases';

-- Envers audit mirror
CREATE TABLE IF NOT EXISTS onprem_instances_aud (
    id                      BIGINT         NOT NULL,
    rev                     INTEGER        NOT NULL,
    revtype                 SMALLINT,
    psp_id                  BIGINT,
    instance_id             VARCHAR(128),
    client_id               VARCHAR(128),
    client_secret_hash      VARCHAR(255),
    display_name            VARCHAR(255),
    status                  VARCHAR(16),
    approved_days           INTEGER,
    lease_until             TIMESTAMP WITH TIME ZONE,
    next_check_at           TIMESTAMP WITH TIME ZONE,
    last_lease_jti          VARCHAR(64),
    last_seen_at            TIMESTAMP WITH TIME ZONE,
    hostname                VARCHAR(255),
    agent_version           VARCHAR(64),
    revoked_at              TIMESTAMP WITH TIME ZONE,
    revoked_by              VARCHAR(128),
    created_by              VARCHAR(128),
    created_at              TIMESTAMP WITH TIME ZONE,
    updated_at              TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id, rev)
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_onprem_instances_aud_rev') THEN
        ALTER TABLE onprem_instances_aud
            ADD CONSTRAINT fk_onprem_instances_aud_rev
            FOREIGN KEY (rev) REFERENCES revinfo (rev);
    END IF;
EXCEPTION
    WHEN undefined_table THEN
        RAISE NOTICE 'revinfo not present yet — skipping FK for onprem_instances_aud';
END $$;
