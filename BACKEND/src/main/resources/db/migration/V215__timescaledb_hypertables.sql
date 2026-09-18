-- V215: TimescaleDB hypertables for the analytics/counts pushed from the on-prem edge engines.
--
-- ARCHITECTURE: rule evaluation runs on customer premises (edge-host + Rust core). The edge pushes
-- AGGREGATE analytics and counts to this control plane, whose database is PostgreSQL + TimescaleDB.
-- The two tables that receive that firehose are time-series by nature and are converted here:
--   * edge_metrics_reports — the aggregate windows pushed by each edge node (window_start)
--   * api_usage_logs       — per-request metering counts (request_timestamp)
--
-- WHY A MIGRATION IS NEEDED AT ALL: TimescaleDB requires the partitioning (time) column to be part
-- of EVERY unique index on a hypertable. Both tables were created with a single-column BIGSERIAL
-- primary key that excludes the time column, so `create_hypertable()` fails outright with
-- "cannot create a unique index without the column ... used in partitioning". The primary key is
-- therefore widened to (id, <time column>) before conversion. The surrogate id stays first and stays
-- unique-by-sequence, so the JPA @Id mapping is unchanged and Hibernate ddl-auto=validate still
-- passes (validation checks tables/columns, not primary-key composition).
--
-- NOT CONVERTED — `transactions`: four other tables carry foreign keys REFERENCING transactions
-- (transaction_features, alerts, sar_transactions, case_transactions). TimescaleDB does not permit a
-- foreign key that points AT a hypertable, so converting it would mean dropping that referential
-- integrity — a data-integrity trade-off that must be an explicit decision, not a silent migration.
-- Tracked in TODO.md.
--
-- SAFETY: the whole migration is a guarded no-op when the TimescaleDB extension is not available, so
-- a plain-PostgreSQL deployment (dev, CI, or a customer that has not installed the extension) runs it
-- cleanly instead of aborting the entire Flyway run — the V147 failure mode. Idempotent: re-running
-- detects the existing hypertable and does nothing.

-- Applied to EVERY deployment (not just TimescaleDB): request_timestamp was created nullable
-- (V3: "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"), but a metering row with no timestamp cannot be
-- attributed to a billing period or a quota window — it is silently unbillable. It is also a hard
-- prerequisite for the primary-key widening and hypertable partitioning below. Backfill, then tighten,
-- so plain-PostgreSQL and TimescaleDB deployments keep an identical schema.
UPDATE api_usage_logs SET request_timestamp = CURRENT_TIMESTAMP WHERE request_timestamp IS NULL;
ALTER TABLE api_usage_logs ALTER COLUMN request_timestamp SET NOT NULL;

DO $$
DECLARE
    has_extension BOOLEAN;
BEGIN
    SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'timescaledb')
      INTO has_extension;

    IF NOT has_extension THEN
        RAISE NOTICE 'TimescaleDB not available — skipping hypertable conversion (plain PostgreSQL is fully supported).';
        RETURN;
    END IF;

    CREATE EXTENSION IF NOT EXISTS timescaledb;

    -- ── edge_metrics_reports: aggregate analytics pushed from each on-prem edge ──────────────
    IF NOT EXISTS (SELECT 1 FROM timescaledb_information.hypertables
                   WHERE hypertable_name = 'edge_metrics_reports') THEN
        -- Widen the PK to include the partitioning column (required by TimescaleDB).
        IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'edge_metrics_reports_pkey') THEN
            ALTER TABLE edge_metrics_reports DROP CONSTRAINT edge_metrics_reports_pkey;
        END IF;
        ALTER TABLE edge_metrics_reports ADD PRIMARY KEY (id, window_start);

        PERFORM create_hypertable('edge_metrics_reports', 'window_start',
                                  if_not_exists  => TRUE,
                                  migrate_data   => TRUE,
                                  chunk_time_interval => INTERVAL '7 days');
        RAISE NOTICE 'edge_metrics_reports converted to a hypertable on window_start.';
    END IF;

    -- ── api_usage_logs: per-request metering counts that drive quota + invoicing ─────────────
    IF NOT EXISTS (SELECT 1 FROM timescaledb_information.hypertables
                   WHERE hypertable_name = 'api_usage_logs') THEN
        -- request_timestamp is already NOT NULL by this point (tightened above), which both the
        -- primary key and TimescaleDB partitioning require.
        IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'api_usage_logs_pkey') THEN
            ALTER TABLE api_usage_logs DROP CONSTRAINT api_usage_logs_pkey;
        END IF;
        ALTER TABLE api_usage_logs ADD PRIMARY KEY (log_id, request_timestamp);

        PERFORM create_hypertable('api_usage_logs', 'request_timestamp',
                                  if_not_exists  => TRUE,
                                  migrate_data   => TRUE,
                                  chunk_time_interval => INTERVAL '7 days');
        RAISE NOTICE 'api_usage_logs converted to a hypertable on request_timestamp.';
    END IF;
END
$$;

-- Time-ordered access paths for the hot read patterns: "this PSP's recent windows" (edge analytics
-- dashboards) and "this PSP's usage in a billing period" (quota checks + invoice generation).
-- Created outside the DO block so they also benefit a plain-PostgreSQL deployment.
CREATE INDEX IF NOT EXISTS idx_edge_metrics_psp_window_desc
    ON edge_metrics_reports (psp_id, window_start DESC);

CREATE INDEX IF NOT EXISTS idx_api_usage_psp_ts_desc
    ON api_usage_logs (psp_id, request_timestamp DESC);
