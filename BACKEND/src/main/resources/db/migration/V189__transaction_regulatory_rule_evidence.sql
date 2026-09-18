ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS sar_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ctr_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS rule_decision VARCHAR(20),
    ADD COLUMN IF NOT EXISTS triggered_rules TEXT;

CREATE INDEX IF NOT EXISTS idx_transactions_regulatory_flags
    ON transactions (psp_id, ctr_required, sar_required, txn_ts DESC);

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS sar_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ctr_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS triggered_rules TEXT;

CREATE INDEX IF NOT EXISTS idx_alerts_regulatory_flags
    ON alerts (psp_id, ctr_required, sar_required, created_at DESC);
