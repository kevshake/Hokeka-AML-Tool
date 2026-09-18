-- Normalized, append-only merchant-verification signal store.
--
-- Every internal check and external-provider adapter result during merchant underwriting
-- is flattened into one row here, so an underwriting decision is always reconstructable
-- from its evidence. run_id correlates all signals from a single verification run.

CREATE TABLE IF NOT EXISTS merchant_verification_signals (
    id                     BIGSERIAL    PRIMARY KEY,
    merchant_id            BIGINT       NOT NULL,
    run_id                 VARCHAR(64)  NOT NULL,
    signal_code            VARCHAR(80)  NOT NULL,
    severity               VARCHAR(16)  NOT NULL,
    source                 VARCHAR(48)  NOT NULL,
    confidence             DOUBLE PRECISION,
    requires_manual_review BOOLEAN      DEFAULT FALSE,
    evidence_reference     VARCHAR(255),
    detail                 TEXT,
    observed_at            TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mvs_merchant ON merchant_verification_signals (merchant_id);
CREATE INDEX IF NOT EXISTS idx_mvs_run ON merchant_verification_signals (run_id);
