-- Edge Ledger, schema version 2 (EDGE_DATA_RESIDENCY_CONTRACT.md §7.6, §7.10, §7.15, §8, §11;
-- plan work packages R2 and R5). Adds what the retention purge, legal holds and the Edge Tunnel op
-- handlers need on top of V1. Never edit this file once released: add V3+.

-- ── legal hold predicate ─────────────────────────────────────────────────────────────────────────
-- True when an ACTIVE hold covers the evaluation (§7.10 scopes): an IDS hold listing the id, or a
-- MERCHANT_RANGE hold on the row's merchant with from <= evaluated_at <= to (inclusive: the conservative
-- reading of a hold's end, §12.2). This one function is the
-- authority for "is this row held": the purge's hold copy, the insert trigger and every tunnel read
-- use it, so they can never disagree. search_path is pinned to the ledger schema at creation.
CREATE FUNCTION edge_is_held(p_id uuid, p_merchant_id text, p_evaluated_at timestamptz)
    RETURNS boolean
    LANGUAGE sql
    STABLE
    SET search_path FROM CURRENT
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM edge_legal_holds h
         WHERE h.released_at IS NULL
           AND ((h.scope_kind = 'IDS' AND p_id = ANY (h.edge_assessment_ids))
             OR (h.scope_kind = 'MERCHANT_RANGE'
                 AND p_merchant_id IS NOT NULL
                 AND h.merchant_id = p_merchant_id
                 AND p_evaluated_at >= h.range_from
                 AND p_evaluated_at <= h.range_to)))
$$;

-- The active hold ids covering one row, sorted (NULL when none): what edge_held_evaluations.hold_ids
-- records when a held row is copied out of a partition that is being dropped.
CREATE FUNCTION edge_hold_ids(p_id uuid, p_merchant_id text, p_evaluated_at timestamptz)
    RETURNS uuid[]
    LANGUAGE sql
    STABLE
    SET search_path FROM CURRENT
AS $$
    SELECT array_agg(h.hold_id ORDER BY h.hold_id::text)
      FROM edge_legal_holds h
     WHERE h.released_at IS NULL
       AND ((h.scope_kind = 'IDS' AND p_id = ANY (h.edge_assessment_ids))
         OR (h.scope_kind = 'MERCHANT_RANGE'
             AND p_merchant_id IS NOT NULL
             AND h.merchant_id = p_merchant_id
             AND p_evaluated_at >= h.range_from
             AND p_evaluated_at <= h.range_to))
$$;

-- "A merchant-range hold also covers rows inserted later inside the range" (§7.10): new rows that an
-- active hold already covers are marked on the way in. A BEFORE ROW trigger on the partitioned
-- parent (PostgreSQL 13+) that only touches legal_hold, never the partition key.
CREATE FUNCTION edge_mark_held_on_insert()
    RETURNS trigger
    LANGUAGE plpgsql
    SET search_path FROM CURRENT
AS $$
BEGIN
    IF NOT NEW.legal_hold AND edge_is_held(NEW.edge_assessment_id, NEW.merchant_id, NEW.evaluated_at) THEN
        NEW.legal_hold := true;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER edge_evaluations_mark_held
    BEFORE INSERT ON edge_evaluations
    FOR EACH ROW EXECUTE FUNCTION edge_mark_held_on_insert();

-- Merchant-range holds look rows up by merchant and time (edge_evaluations_merchant_time_idx exists
-- since V1); held copies need the same for release bookkeeping. Marked live rows are few, so a
-- partial index makes ledger.health's held-row count and hold release cheap.
CREATE INDEX edge_held_evaluations_merchant_time_idx ON edge_held_evaluations (merchant_id, evaluated_at);
CREATE INDEX edge_evaluations_legal_hold_idx ON edge_evaluations (evaluated_at) WHERE legal_hold;

-- ── readable evaluations ─────────────────────────────────────────────────────────────────────────
-- Every op reads live and held rows alike ("held rows remain readable by every op", §8.2). A row is
-- in exactly one of the two tables: it is copied into edge_held_evaluations in the same transaction
-- that drops its partition. Both tables carry an (evaluated_at DESC, edge_assessment_id DESC) index,
-- so keyset pages over the view are a merge of two index scans.
CREATE VIEW edge_all_evaluations AS
SELECT edge_assessment_id, txn_ref, site_id, node_id, evaluated_at, origin, pan_hash, amount_cents, currency,
       merchant_id, mcc, country_code, channel, features, action, score, triggered_rule_ids, rule_version_ids,
       reasons, bundle_version, bundle_hash, evaluator, latency_us, fail_closed_code, ctr_required, sanctions_hit,
       sanctions_hits, typology_codes, legal_hold, backfill_id, legacy_transaction_id
  FROM edge_evaluations
UNION ALL
SELECT edge_assessment_id, txn_ref, site_id, node_id, evaluated_at, origin, pan_hash, amount_cents, currency,
       merchant_id, mcc, country_code, channel, features, action, score, triggered_rule_ids, rule_version_ids,
       reasons, bundle_version, bundle_hash, evaluator, latency_us, fail_closed_code, ctr_required, sanctions_hit,
       sanctions_hits, typology_codes, legal_hold, backfill_id, legacy_transaction_id
  FROM edge_held_evaluations;

COMMENT ON VIEW edge_all_evaluations IS
    'Live (edge_evaluations) and held (edge_held_evaluations) rows: what every tunnel op reads.';

-- ── txn.related link keys (§7.6) ─────────────────────────────────────────────────────────────────
-- CARD = pan_hash, DEVICE = features.device_id, ACCOUNT = features.account_id / customer_id,
-- IP = features.ip_address (MERCHANT uses edge_evaluations_merchant_time_idx). Partial indexes, so a
-- row without the key costs nothing on the evaluate path.
CREATE INDEX edge_evaluations_pan_hash_idx ON edge_evaluations (pan_hash, evaluated_at)
    WHERE pan_hash IS NOT NULL;
CREATE INDEX edge_evaluations_device_idx ON edge_evaluations ((features ->> 'device_id'), evaluated_at)
    WHERE features ? 'device_id';
CREATE INDEX edge_evaluations_account_idx ON edge_evaluations ((features ->> 'account_id'), evaluated_at)
    WHERE features ? 'account_id';
CREATE INDEX edge_evaluations_customer_idx ON edge_evaluations ((features ->> 'customer_id'), evaluated_at)
    WHERE features ? 'customer_id';
CREATE INDEX edge_evaluations_ip_idx ON edge_evaluations ((features ->> 'ip_address'), evaluated_at)
    WHERE features ? 'ip_address';

-- ── ledger.backfill (§11) ────────────────────────────────────────────────────────────────────────
-- One row per backfill run. state: OPEN until COMMIT verifies (VERIFIED) or the cloud aborts
-- (ABORTED). A COMMIT that does not verify leaves the run OPEN so batches can be re-sent; the last
-- outcome is kept in last_commit_state.
CREATE TABLE edge_backfill_runs (
    backfill_id       uuid        PRIMARY KEY,
    state             text        NOT NULL,
    source_from       timestamptz NOT NULL,
    source_to         timestamptz NOT NULL,
    expected_rows     bigint      NOT NULL,
    retention_cutoff  timestamptz NOT NULL,
    next_batch_seq    bigint      NOT NULL DEFAULT 0,
    accepted          bigint      NOT NULL DEFAULT 0,
    duplicates        bigint      NOT NULL DEFAULT 0,
    rejected          bigint      NOT NULL DEFAULT 0,
    last_commit_state text,
    begun_at          timestamptz NOT NULL DEFAULT now(),
    finished_at       timestamptz,
    node_id           text        NOT NULL,
    CONSTRAINT edge_backfill_runs_state_chk CHECK (state IN ('OPEN', 'VERIFIED', 'ABORTED')),
    CONSTRAINT edge_backfill_runs_commit_chk CHECK (
        last_commit_state IS NULL OR last_commit_state IN ('VERIFIED', 'VERIFY_MISMATCH')),
    CONSTRAINT edge_backfill_runs_counts_chk CHECK (
        expected_rows >= 0 AND accepted >= 0 AND duplicates >= 0 AND rejected >= 0 AND next_batch_seq >= 0)
);

-- The legacy rows a run holds (accepted or found as exact duplicates), for the COMMIT digest
-- (§11 step 5: SHA-256 over the sorted, newline-joined legacyTransactionIds the Edge holds for this
-- backfillId). Ids only, never row content.
CREATE TABLE edge_backfill_rows (
    backfill_id           uuid NOT NULL REFERENCES edge_backfill_runs (backfill_id) ON DELETE CASCADE,
    legacy_transaction_id text NOT NULL,
    edge_assessment_id    uuid NOT NULL,
    CONSTRAINT edge_backfill_rows_pk PRIMARY KEY (backfill_id, legacy_transaction_id)
);

-- ── access log, export ids ───────────────────────────────────────────────────────────────────────
-- export.stream can return up to 1 000 000 rows; their ids are recorded here one chunk per row
-- instead of growing edge_access_log.returned_ids by rewriting it on every chunk. Purged with the
-- access-log row it belongs to.
CREATE TABLE edge_access_log_exports (
    access_log_id bigint  NOT NULL REFERENCES edge_access_log (id) ON DELETE CASCADE,
    chunk_seq     integer NOT NULL,
    returned_ids  uuid[]  NOT NULL,
    CONSTRAINT edge_access_log_exports_pk PRIMARY KEY (access_log_id, chunk_seq),
    CONSTRAINT edge_access_log_exports_seq_chk CHECK (chunk_seq >= 0)
);

-- Retention bookkeeping that ledger.health and the next run read.
CREATE INDEX edge_purge_log_outcome_idx ON edge_purge_log (run_at DESC) WHERE outcome = 'OK';
CREATE INDEX edge_config_accepted_at_idx ON edge_config (accepted_at);
