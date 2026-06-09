-- V145: Real monitoring metrics support
-- (Renumbered from duplicate V140; compliance review tracking retains V140.)
--
-- Adds the columns required by MonitoringMetricsService to compute true
-- AUC, latency percentiles, and PSI drift from per-transaction data
-- instead of stubbed placeholders.

-- ─────────────────────────────────────────────────────────────────
-- transaction_features: capture per-scoring latency + provenance
-- ─────────────────────────────────────────────────────────────────
ALTER TABLE transaction_features
    ADD COLUMN IF NOT EXISTS latency_ms     INTEGER,
    ADD COLUMN IF NOT EXISTS model_version  TEXT,
    ADD COLUMN IF NOT EXISTS psp_id         BIGINT;

-- Percentile/AUC queries scan by date and group by score; latency queries
-- scan by date and aggregate latency_ms. Composite index keeps both fast.
CREATE INDEX IF NOT EXISTS idx_features_scored_at_score
    ON transaction_features(scored_at, score)
    WHERE score IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_features_scored_at_latency
    ON transaction_features(scored_at)
    WHERE latency_ms IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_features_psp_scored_at
    ON transaction_features(psp_id, scored_at)
    WHERE psp_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────
-- model_metrics: persist full distribution, drift baseline, totals
-- ─────────────────────────────────────────────────────────────────
ALTER TABLE model_metrics
    ADD COLUMN IF NOT EXISTS p50_latency_ms     DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS p95_latency_ms     DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS p99_latency_ms     DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS total_scored       INTEGER,
    ADD COLUMN IF NOT EXISTS total_labeled      INTEGER,
    ADD COLUMN IF NOT EXISTS fraud_count        INTEGER,
    ADD COLUMN IF NOT EXISTS baseline_avg_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS psi_drift          DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS model_version      TEXT;

-- Unique on date so the daily computer can upsert/refresh idempotently.
-- Some legacy rows may have NULL date; only constrain the populated rows.
CREATE UNIQUE INDEX IF NOT EXISTS uk_model_metrics_date
    ON model_metrics(date)
    WHERE date IS NOT NULL;
