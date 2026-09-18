-- Do-Not-Honour support: time-boxed payment blacklist entries.
-- expires_at NULL  = permanent block (e.g. confirmed fraud / lost-stolen PAN).
-- expires_at set   = block is effective only while expires_at > now() (the 30-day
--                    auto-decline applied to a card after a Do-Not-Honour / BLOCK decision).
ALTER TABLE payment_blacklist_entries
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

-- Index the exact predicate the transaction decision path hits (fastest check):
-- entry_type + entry_value + active, with expires_at read from the row.
CREATE INDEX IF NOT EXISTS idx_payment_blacklist_lookup
    ON payment_blacklist_entries (entry_type, entry_value, active);
