-- R8 (docs/architecture/EDGE_DATA_RESIDENCY_PLAN.md, contract invariant I4): cloud workflow records
-- reference transactions by ID only.
--
-- Every workflow table that pointed at `transactions` gets the decision-record references the
-- contract names: edge_assessment_id (UUID), txn_ref (the PSP's opaque id, <= 128 chars) and site_id.
-- The foreign keys to `transactions` are dropped. The legacy id columns (alerts.txn_id,
-- alert_transactions.txn_id, case_transactions.transaction_id, sar_transactions.txn_id,
-- screening_activities.txn_id, findings.txn_id) stay as plain nullable ids: legacy cloud ingest runs
-- until R12 and the R15 back-fill to the Edge needs them.
--
-- Back-fill rule for legacy rows (one rule, also used by TransactionReferenceResolver in Java):
--   * the transaction's assessment is the TXN-triggered assessments row of the same PSP and txn_id,
--     preferring one adopted from the Edge (edge_assessment_id set), then the oldest (created_at, id);
--   * edge_assessment_id = that assessment's edge_assessment_id, else its own id. Contract section 11
--     makes "the existing cloud assessment id" the edgeAssessmentId of a back-filled legacy row, so
--     the reference stays valid after R15;
--   * txn_ref = the assessment's txn_ref, else transactions.client_reference when it is a valid txnRef
--     (1..128 printable ASCII, no spaces, not shaped like a card number);
--   * site_id = the assessment's site_id (legacy cloud rows have none).
-- Rows whose transaction has no assessment keep a null edge_assessment_id; R15 mints the UUIDv5 for
-- them (contract section 11 step 2) from the kept legacy id.
--
-- Also: alerts.pan_hash is nulled and locked to NULL (a card hash is Edge-only data, contract 2.2);
-- case_alerts.raw_data snapshots of whole transactions are rewritten to references; transaction-time
-- screening rows lose the screened name (a counterparty name is transaction data).

-- ── 1. Reference columns ────────────────────────────────────────────────────────────────────────
ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS edge_assessment_id UUID,
    ADD COLUMN IF NOT EXISTS txn_ref            VARCHAR(128),
    ADD COLUMN IF NOT EXISTS site_id            VARCHAR(64);

ALTER TABLE alert_transactions
    ADD COLUMN IF NOT EXISTS edge_assessment_id UUID,
    ADD COLUMN IF NOT EXISTS txn_ref            VARCHAR(128),
    ADD COLUMN IF NOT EXISTS site_id            VARCHAR(64);

ALTER TABLE case_transactions
    ADD COLUMN IF NOT EXISTS edge_assessment_id UUID,
    ADD COLUMN IF NOT EXISTS txn_ref            VARCHAR(128),
    ADD COLUMN IF NOT EXISTS site_id            VARCHAR(64);

ALTER TABLE case_transactions_aud
    ADD COLUMN IF NOT EXISTS edge_assessment_id UUID,
    ADD COLUMN IF NOT EXISTS txn_ref            VARCHAR(128),
    ADD COLUMN IF NOT EXISTS site_id            VARCHAR(64);

ALTER TABLE sar_transactions
    ADD COLUMN IF NOT EXISTS edge_assessment_id UUID,
    ADD COLUMN IF NOT EXISTS txn_ref            VARCHAR(128),
    ADD COLUMN IF NOT EXISTS site_id            VARCHAR(64);

ALTER TABLE sar_transactions_aud
    ADD COLUMN IF NOT EXISTS edge_assessment_id UUID,
    ADD COLUMN IF NOT EXISTS txn_ref            VARCHAR(128),
    ADD COLUMN IF NOT EXISTS site_id            VARCHAR(64);

ALTER TABLE screening_activities
    ADD COLUMN IF NOT EXISTS edge_assessment_id UUID,
    ADD COLUMN IF NOT EXISTS txn_ref            VARCHAR(128),
    ADD COLUMN IF NOT EXISTS site_id            VARCHAR(64);

ALTER TABLE findings
    ADD COLUMN IF NOT EXISTS edge_assessment_id UUID,
    ADD COLUMN IF NOT EXISTS txn_ref            VARCHAR(128),
    ADD COLUMN IF NOT EXISTS site_id            VARCHAR(64);

-- ── 2. Drop every foreign key from a workflow table to transactions ─────────────────────────────
-- The V1/V4/V8/V239 constraints were partly inline (generated names), so they are found by catalog.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT c.conname, c.conrelid::regclass AS tbl
          FROM pg_constraint c
         WHERE c.contype = 'f'
           AND c.confrelid = 'transactions'::regclass
           AND c.conrelid IN ('alerts'::regclass, 'alert_transactions'::regclass,
                              'case_transactions'::regclass, 'sar_transactions'::regclass,
                              'screening_activities'::regclass, 'findings'::regclass)
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.tbl, fk.conname);
    END LOOP;
END $$;

ALTER TABLE alert_transactions ALTER COLUMN txn_id DROP NOT NULL;
ALTER TABLE case_transactions ALTER COLUMN transaction_id DROP NOT NULL;

-- sar_transactions was a pure (sar_id, txn_id) join table. It becomes the SarTransaction entity:
-- surrogate id, nullable legacy txn_id, reference columns.
ALTER TABLE sar_transactions ADD COLUMN IF NOT EXISTS id BIGSERIAL;
DO $$
DECLARE
    pk_name TEXT;
BEGIN
    SELECT conname INTO pk_name FROM pg_constraint
     WHERE conrelid = 'sar_transactions'::regclass AND contype = 'p';
    IF pk_name IS NOT NULL AND pk_name <> 'pk_sar_transactions' THEN
        EXECUTE format('ALTER TABLE sar_transactions DROP CONSTRAINT %I', pk_name);
        ALTER TABLE sar_transactions ADD CONSTRAINT pk_sar_transactions PRIMARY KEY (id);
    ELSIF pk_name IS NULL THEN
        ALTER TABLE sar_transactions ADD CONSTRAINT pk_sar_transactions PRIMARY KEY (id);
    END IF;
END $$;
ALTER TABLE sar_transactions ALTER COLUMN txn_id DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_sar_transactions_sar_txn
    ON sar_transactions (sar_id, txn_id) WHERE txn_id IS NOT NULL;

-- sar_transactions_aud was the Envers middle table of the @ManyToMany; it becomes the entity audit
-- table (PK id, rev). Existing audit rows keep their history: each (sar_id, txn_id) pair gets the id
-- of its live join row, or a fresh id from the same sequence when the row has since been removed.
ALTER TABLE sar_transactions_aud ADD COLUMN IF NOT EXISTS id BIGINT;
UPDATE sar_transactions_aud a
   SET id = s.id
  FROM sar_transactions s
 WHERE a.id IS NULL AND s.sar_id = a.sar_id AND s.txn_id = a.txn_id;
WITH orphan_pairs AS (
    SELECT DISTINCT sar_id, txn_id FROM sar_transactions_aud WHERE id IS NULL
), minted AS (
    SELECT sar_id, txn_id, nextval(pg_get_serial_sequence('sar_transactions', 'id')) AS new_id
      FROM orphan_pairs
)
UPDATE sar_transactions_aud a
   SET id = m.new_id
  FROM minted m
 WHERE a.id IS NULL AND a.sar_id = m.sar_id AND a.txn_id = m.txn_id;
DO $$
DECLARE
    pk_name TEXT;
BEGIN
    SELECT conname INTO pk_name FROM pg_constraint
     WHERE conrelid = 'sar_transactions_aud'::regclass AND contype = 'p';
    IF pk_name IS NOT NULL AND pk_name <> 'pk_sar_transactions_aud' THEN
        EXECUTE format('ALTER TABLE sar_transactions_aud DROP CONSTRAINT %I', pk_name);
    END IF;
END $$;
ALTER TABLE sar_transactions_aud ALTER COLUMN id SET NOT NULL;
ALTER TABLE sar_transactions_aud ALTER COLUMN sar_id DROP NOT NULL;
ALTER TABLE sar_transactions_aud ALTER COLUMN txn_id DROP NOT NULL;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pk_sar_transactions_aud') THEN
        ALTER TABLE sar_transactions_aud ADD CONSTRAINT pk_sar_transactions_aud PRIMARY KEY (id, rev);
    END IF;
END $$;

-- ── 3. Back-fill ────────────────────────────────────────────────────────────────────────────────
-- Tenant column first: workflow lookups are PSP-scoped, and alerts created by legacy ingest before
-- psp_id existed only knew their PSP through the transaction.
UPDATE alerts a
   SET psp_id = t.psp_id
  FROM transactions t
 WHERE a.psp_id IS NULL AND a.txn_id = t.txn_id AND t.psp_id IS NOT NULL;

-- Valid txnRef (contract 2.5): 1..128 printable ASCII without spaces, not 13..19 digits once dashes
-- are removed (stricter than the Luhn guard: any card-shaped number is refused).
CREATE FUNCTION pg_temp.r8_valid_txn_ref(ref TEXT) RETURNS BOOLEAN
    LANGUAGE sql IMMUTABLE AS $fn$
    SELECT ref IS NOT NULL
       AND ref ~ '^[!-~]{1,128}$'
       AND regexp_replace(ref, '-', '', 'g') !~ '^[0-9]{13,19}$'
$fn$;

-- txn ids referenced from case_alerts.raw_data transaction snapshots ({"txnId":…,"amountCents":…}).
CREATE FUNCTION pg_temp.r8_snapshot_txn_id(raw TEXT) RETURNS BIGINT
    LANGUAGE plpgsql IMMUTABLE AS $fn$
DECLARE
    j JSONB;
BEGIN
    IF raw IS NULL OR raw NOT LIKE '{%' THEN
        RETURN NULL;
    END IF;
    BEGIN
        j := raw::jsonb;
    EXCEPTION WHEN others THEN
        RETURN NULL;
    END;
    IF jsonb_typeof(j) = 'object' AND j ? 'txnId'
       AND (j ? 'amountCents' OR j ? 'panHash' OR j ? 'isoMsg' OR j ? 'currency' OR j ? 'ipAddress') THEN
        BEGIN
            RETURN (j ->> 'txnId')::BIGINT;
        EXCEPTION WHEN others THEN
            RETURN -1;
        END;
    END IF;
    RETURN NULL;
END
$fn$;

-- One reference per legacy transaction that any workflow row points at.
CREATE TEMP TABLE r8_txn_refs ON COMMIT DROP AS
WITH referenced AS (
    SELECT txn_id FROM alerts WHERE txn_id IS NOT NULL
    UNION SELECT txn_id FROM alert_transactions WHERE txn_id IS NOT NULL
    UNION SELECT transaction_id FROM case_transactions WHERE transaction_id IS NOT NULL
    UNION SELECT txn_id FROM sar_transactions WHERE txn_id IS NOT NULL
    UNION SELECT txn_id FROM screening_activities WHERE txn_id IS NOT NULL
    UNION SELECT txn_id FROM findings WHERE txn_id IS NOT NULL
    UNION SELECT pg_temp.r8_snapshot_txn_id(raw_data) FROM case_alerts WHERE raw_data IS NOT NULL
)
SELECT t.txn_id,
       t.psp_id,
       COALESCE(pick.edge_assessment_id, pick.id) AS edge_assessment_id,
       COALESCE(pick.txn_ref,
                CASE WHEN pg_temp.r8_valid_txn_ref(t.client_reference) THEN t.client_reference END) AS txn_ref,
       pick.site_id
  FROM transactions t
  JOIN referenced r ON r.txn_id = t.txn_id
  LEFT JOIN LATERAL (
        SELECT a.id, a.edge_assessment_id, a.txn_ref, a.site_id
          FROM assessments a
         WHERE a.txn_id = t.txn_id
           AND a.psp_id = t.psp_id
           AND a.trigger_type = 'TXN'
         ORDER BY (a.edge_assessment_id IS NULL), a.created_at, a.id
         LIMIT 1) pick ON TRUE;

CREATE UNIQUE INDEX ON r8_txn_refs (txn_id);

-- alerts raised from decision records (R7) carry the record id as source_reference.
UPDATE alerts al
   SET edge_assessment_id = d.edge_assessment_id,
       txn_ref            = d.txn_ref,
       site_id            = d.site_id
  FROM assessments d
 WHERE al.edge_assessment_id IS NULL
   AND al.source_type = 'EDGE_DECISION'
   AND al.source_reference ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
   AND d.edge_assessment_id = al.source_reference::uuid
   AND d.psp_id = al.psp_id;

UPDATE alerts al
   SET edge_assessment_id = r.edge_assessment_id,
       txn_ref            = r.txn_ref,
       site_id            = r.site_id
  FROM r8_txn_refs r
 WHERE al.edge_assessment_id IS NULL AND al.txn_ref IS NULL
   AND al.txn_id = r.txn_id
   AND (al.psp_id IS NULL OR al.psp_id = r.psp_id);

UPDATE alert_transactions x
   SET edge_assessment_id = r.edge_assessment_id,
       txn_ref            = r.txn_ref,
       site_id            = r.site_id
  FROM r8_txn_refs r, alerts al
 WHERE x.edge_assessment_id IS NULL AND x.txn_ref IS NULL
   AND x.txn_id = r.txn_id
   AND al.alert_id = x.alert_id
   AND (al.psp_id IS NULL OR al.psp_id = r.psp_id);

-- Every decision-record alert gets its primary link row (R7 alerts had none: no transactions row).
INSERT INTO alert_transactions (alert_id, txn_id, primary_link, linked_at, edge_assessment_id, txn_ref, site_id)
SELECT al.alert_id, NULL, TRUE, COALESCE(al.created_at, NOW()), al.edge_assessment_id, al.txn_ref, al.site_id
  FROM alerts al
 WHERE al.source_type = 'EDGE_DECISION'
   AND al.edge_assessment_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM alert_transactions x
                    WHERE x.alert_id = al.alert_id AND x.edge_assessment_id = al.edge_assessment_id);

UPDATE case_transactions ct
   SET edge_assessment_id = r.edge_assessment_id,
       txn_ref            = r.txn_ref,
       site_id            = r.site_id
  FROM r8_txn_refs r, compliance_cases c
 WHERE ct.edge_assessment_id IS NULL AND ct.txn_ref IS NULL
   AND ct.transaction_id = r.txn_id
   AND c.case_id = ct.case_id
   AND (c.psp_id IS NULL OR c.psp_id = r.psp_id);

UPDATE sar_transactions st
   SET edge_assessment_id = r.edge_assessment_id,
       txn_ref            = r.txn_ref,
       site_id            = r.site_id
  FROM r8_txn_refs r, suspicious_activity_reports s
 WHERE st.edge_assessment_id IS NULL AND st.txn_ref IS NULL
   AND st.txn_id = r.txn_id
   AND s.id = st.sar_id
   AND (s.psp_id IS NULL OR s.psp_id = r.psp_id);

UPDATE screening_activities sa
   SET edge_assessment_id = r.edge_assessment_id,
       txn_ref            = r.txn_ref,
       site_id            = r.site_id
  FROM r8_txn_refs r
 WHERE sa.edge_assessment_id IS NULL AND sa.txn_ref IS NULL
   AND sa.txn_id = r.txn_id
   AND (sa.psp_id IS NULL OR sa.psp_id = r.psp_id);

-- Findings of a legacy transaction take the transaction's reference; findings of an Edge decision
-- record (no txn_id) take the record's own Edge id.
UPDATE findings f
   SET edge_assessment_id = r.edge_assessment_id,
       txn_ref            = r.txn_ref,
       site_id            = r.site_id
  FROM r8_txn_refs r, assessments a
 WHERE f.edge_assessment_id IS NULL AND f.txn_ref IS NULL
   AND f.txn_id = r.txn_id
   AND a.id = f.assessment_id
   AND a.psp_id = r.psp_id;

UPDATE findings f
   SET edge_assessment_id = a.edge_assessment_id,
       txn_ref            = a.txn_ref,
       site_id            = a.site_id
  FROM assessments a
 WHERE f.edge_assessment_id IS NULL AND f.txn_ref IS NULL
   AND f.txn_id IS NULL
   AND a.id = f.assessment_id
   AND a.edge_assessment_id IS NOT NULL;

-- Audit history of the link tables mirrors the live rows' references.
UPDATE case_transactions_aud a
   SET edge_assessment_id = ct.edge_assessment_id,
       txn_ref            = ct.txn_ref,
       site_id            = ct.site_id
  FROM case_transactions ct
 WHERE a.id = ct.id AND a.edge_assessment_id IS NULL AND a.txn_ref IS NULL;

UPDATE sar_transactions_aud a
   SET edge_assessment_id = st.edge_assessment_id,
       txn_ref            = st.txn_ref,
       site_id            = st.site_id
  FROM sar_transactions st
 WHERE a.id = st.id AND a.edge_assessment_id IS NULL AND a.txn_ref IS NULL;

-- ── 4. Transaction data that workflow rows copied into the cloud ────────────────────────────────
-- alerts.pan_hash: card identity is Edge-only. Nothing reads it any more (the only query using it,
-- AlertRepository.findByPanHashAndMerchantIdOrderByCreatedAtDesc, had no caller and is removed).
DROP INDEX IF EXISTS idx_alerts_pan_hash;
UPDATE alerts SET pan_hash = NULL WHERE pan_hash IS NOT NULL;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_alerts_pan_hash_null') THEN
        ALTER TABLE alerts ADD CONSTRAINT ck_alerts_pan_hash_null CHECK (pan_hash IS NULL);
    END IF;
END $$;
COMMENT ON COLUMN alerts.pan_hash IS 'Retired by V264 (R8): always NULL; card identity stays on the Edge. Dropped by the R12 purge migration.';

-- case_alerts.raw_data held Jackson snapshots of the whole TransactionEntity (amount, pan_hash, IP,
-- device…). Rewrite them to the reference document CaseCreationService now writes.
CREATE FUNCTION pg_temp.r8_case_alert_refs(raw TEXT) RETURNS TEXT
    LANGUAGE plpgsql AS $fn$
DECLARE
    tid BIGINT := pg_temp.r8_snapshot_txn_id(raw);
    ref RECORD;
BEGIN
    IF tid IS NULL THEN
        RETURN raw;
    END IF;
    IF tid = -1 THEN
        RETURN jsonb_build_object('source', 'LEGACY_TRANSACTION')::text;
    END IF;
    SELECT r.edge_assessment_id, r.txn_ref, r.site_id INTO ref FROM r8_txn_refs r WHERE r.txn_id = tid;
    RETURN jsonb_strip_nulls(jsonb_build_object(
            'source', 'LEGACY_TRANSACTION',
            'transactionId', tid,
            'edgeAssessmentId', ref.edge_assessment_id,
            'txnRef', ref.txn_ref,
            'siteId', ref.site_id))::text;
END
$fn$;

UPDATE case_alerts
   SET raw_data = pg_temp.r8_case_alert_refs(raw_data)
 WHERE pg_temp.r8_snapshot_txn_id(raw_data) IS NOT NULL;

UPDATE case_alerts_aud
   SET raw_data = pg_temp.r8_case_alert_refs(raw_data)
 WHERE pg_temp.r8_snapshot_txn_id(raw_data) IS NOT NULL;

-- Transaction-time screening stored the screened (merchant or counterparty) name; a counterparty
-- name is transaction data. The merchant stays identified by merchant_id.
UPDATE screening_activities SET subject_name = NULL
 WHERE subject_type = 'TRANSACTION' AND subject_name IS NOT NULL;

-- ── 5. Integrity and lookups ────────────────────────────────────────────────────────────────────
-- A link row must identify its transaction somehow.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_alert_transactions_ref') THEN
        ALTER TABLE alert_transactions ADD CONSTRAINT ck_alert_transactions_ref
            CHECK (txn_id IS NOT NULL OR edge_assessment_id IS NOT NULL OR txn_ref IS NOT NULL);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_case_transactions_ref') THEN
        ALTER TABLE case_transactions ADD CONSTRAINT ck_case_transactions_ref
            CHECK (transaction_id IS NOT NULL OR edge_assessment_id IS NOT NULL OR txn_ref IS NOT NULL);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_sar_transactions_ref') THEN
        ALTER TABLE sar_transactions ADD CONSTRAINT ck_sar_transactions_ref
            CHECK (txn_id IS NOT NULL OR edge_assessment_id IS NOT NULL OR txn_ref IS NOT NULL);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_alert_transactions_alert_edge
    ON alert_transactions (alert_id, edge_assessment_id) WHERE edge_assessment_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_sar_transactions_sar_edge
    ON sar_transactions (sar_id, edge_assessment_id) WHERE edge_assessment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_alerts_psp_edge_assessment
    ON alerts (psp_id, edge_assessment_id) WHERE edge_assessment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_alerts_psp_txn_ref
    ON alerts (psp_id, txn_ref) WHERE txn_ref IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_alert_transactions_edge
    ON alert_transactions (edge_assessment_id) WHERE edge_assessment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_case_transactions_edge
    ON case_transactions (edge_assessment_id) WHERE edge_assessment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sar_transactions_edge
    ON sar_transactions (edge_assessment_id) WHERE edge_assessment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_screening_activities_edge
    ON screening_activities (edge_assessment_id) WHERE edge_assessment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_findings_edge
    ON findings (edge_assessment_id) WHERE edge_assessment_id IS NOT NULL;

COMMENT ON COLUMN alerts.edge_assessment_id IS 'Decision record (or, for legacy ingest, the cloud assessment id that R15 back-fills under) of the alerted transaction';
COMMENT ON COLUMN alerts.txn_ref IS 'PSP opaque transaction id (contract 2.5); never a card number';
COMMENT ON COLUMN alerts.txn_id IS 'Legacy cloud transactions.txn_id, no FK since V264; kept until R12 for legacy ingest and the R15 back-fill';
COMMENT ON TABLE sar_transactions IS 'SAR to transaction references (V264: by edge_assessment_id/txn_ref; txn_id legacy, no FK)';
