-- WP-02: Canonical decision vocabulary alongside legacy transaction.decision.

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS decision_v2 VARCHAR(16);

COMMENT ON COLUMN transactions.decision_v2 IS 'Canonical decision ALLOW|FLAG|SUSPEND|BLOCK (HOLD→SUSPEND, ALERT→FLAG)';

CREATE INDEX IF NOT EXISTS idx_txn_decision_v2 ON transactions (decision_v2);
