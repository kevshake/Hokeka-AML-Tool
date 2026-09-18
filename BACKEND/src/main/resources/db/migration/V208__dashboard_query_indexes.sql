-- =========================================================================
-- V208: Dashboard query indexes
-- Speeds up KPI / sparkline aggregates that filter screening results by
-- status + time window (today counts, match trends).
-- =========================================================================

CREATE INDEX IF NOT EXISTS idx_screening_status_screened_at
    ON merchant_screening_results (screening_status, screened_at DESC);

-- Alert trends / live queue: open alerts ordered by recency (psp-scoped)
CREATE INDEX IF NOT EXISTS idx_alert_status_created_desc
    ON alerts (status, created_at DESC);

ANALYZE merchant_screening_results;
ANALYZE alerts;
