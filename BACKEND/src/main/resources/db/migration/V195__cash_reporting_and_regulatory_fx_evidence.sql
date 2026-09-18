ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS cash_transaction BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ctr_evaluation_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS ctr_usd_equivalent NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS ctr_threshold_usd NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS ctr_rate_source VARCHAR(160),
    ADD COLUMN IF NOT EXISTS ctr_rate_effective_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ctr_evaluated_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_transactions_cash_reporting
    ON transactions (psp_id, cash_transaction, ctr_required, txn_ts DESC);

ALTER TABLE currency_rates
    ADD COLUMN IF NOT EXISTS rate_source VARCHAR(160),
    ADD COLUMN IF NOT EXISTS effective_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS regulatory_approved BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS approved_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

UPDATE currency_rates
   SET rate_source = COALESCE(rate_source, 'LEGACY_BILLING_SEED'),
       effective_at = COALESCE(effective_at, updated_at)
 WHERE rate_source IS NULL OR effective_at IS NULL;

UPDATE reports
   SET report_name = 'Kenya Cash Transaction Report',
       description = 'Cash transactions at or above the USD 15,000 equivalent threshold with approved FX evidence.',
       regulatory_template = NULL
 WHERE report_code = 'CTR_001';

UPDATE reports
   SET description = 'Potential cash structuring below the reporting threshold with conversion and rule evidence.'
 WHERE report_code = 'CTR_005';

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    order_by_default, is_active, created_by, created_at
)
SELECT r.id, 1,
    'SELECT t.txn_id, t.psp_id, t.merchant_id, t.pan_hash, t.txn_ts,
            t.amount_cents / 100.0 AS transaction_amount, t.currency,
            t.ctr_usd_equivalent, t.ctr_threshold_usd, t.ctr_evaluation_status,
            t.ctr_rate_source, t.ctr_rate_effective_at, t.ctr_evaluated_at,
            t.channel_type, t.merchant_country, t.rule_decision, t.triggered_rules
       FROM transactions t
      WHERE t.psp_id = :pspId
        AND t.txn_ts BETWEEN :dateFrom AND :dateTo
        AND t.cash_transaction = TRUE
        AND t.ctr_required = TRUE
      ORDER BY t.txn_ts DESC',
    'SELECT COUNT(*) FROM transactions t
      WHERE t.psp_id = :pspId
        AND t.txn_ts BETWEEN :dateFrom AND :dateTo
        AND t.cash_transaction = TRUE
        AND t.ctr_required = TRUE',
    '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
    '[{"field":"currency","type":"STRING"},{"field":"channel_type","type":"STRING"},{"field":"merchant_country","type":"STRING"},{"field":"ctr_evaluation_status","type":"ENUM","options":["REPORTABLE","BELOW_THRESHOLD","FX_UNAVAILABLE","NOT_CASH"]}]'::jsonb,
    '[{"name":"txn_id","type":"LONG","label":"Transaction ID"},{"name":"merchant_id","type":"STRING","label":"Merchant ID"},{"name":"txn_ts","type":"DATETIME","label":"Transaction Time"},{"name":"transaction_amount","type":"CURRENCY","label":"Original Amount"},{"name":"currency","type":"STRING","label":"Currency"},{"name":"ctr_usd_equivalent","type":"DECIMAL","label":"USD Equivalent"},{"name":"ctr_threshold_usd","type":"DECIMAL","label":"CTR Threshold USD"},{"name":"ctr_rate_source","type":"STRING","label":"FX Source"},{"name":"ctr_rate_effective_at","type":"DATETIME","label":"FX Effective At"},{"name":"channel_type","type":"STRING","label":"Channel"},{"name":"merchant_country","type":"STRING","label":"Country"}]'::jsonb,
    'txn_ts DESC', TRUE, NULL, CURRENT_TIMESTAMP
FROM reports r
WHERE r.report_code = 'CTR_001'
  AND NOT EXISTS (
      SELECT 1 FROM report_definitions d WHERE d.report_id = r.id AND d.version = 1
  );

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    order_by_default, is_active, created_by, created_at
)
SELECT r.id, 1,
    'SELECT t.txn_id, t.psp_id, t.merchant_id, t.pan_hash, t.txn_ts,
            t.amount_cents / 100.0 AS transaction_amount, t.currency,
            t.ctr_usd_equivalent, t.ctr_threshold_usd, t.ctr_evaluation_status,
            t.ctr_rate_source, t.ctr_rate_effective_at,
            t.rule_decision, t.triggered_rules, t.sar_required
       FROM transactions t
      WHERE t.psp_id = :pspId
        AND t.txn_ts BETWEEN :dateFrom AND :dateTo
        AND t.cash_transaction = TRUE
        AND t.sar_required = TRUE
        AND t.triggered_rules LIKE ''%CASH_STRUCTURING%''
      ORDER BY t.txn_ts DESC',
    'SELECT COUNT(*) FROM transactions t
      WHERE t.psp_id = :pspId
        AND t.txn_ts BETWEEN :dateFrom AND :dateTo
        AND t.cash_transaction = TRUE
        AND t.sar_required = TRUE
        AND t.triggered_rules LIKE ''%CASH_STRUCTURING%''',
    '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
    '[{"field":"currency","type":"STRING"},{"field":"rule_decision","type":"ENUM","options":["REVIEW","HOLD","BLOCK"]},{"field":"merchant_country","type":"STRING"}]'::jsonb,
    '[{"name":"txn_id","type":"LONG","label":"Transaction ID"},{"name":"merchant_id","type":"STRING","label":"Merchant ID"},{"name":"txn_ts","type":"DATETIME","label":"Transaction Time"},{"name":"transaction_amount","type":"CURRENCY","label":"Original Amount"},{"name":"currency","type":"STRING","label":"Currency"},{"name":"ctr_usd_equivalent","type":"DECIMAL","label":"USD Equivalent"},{"name":"ctr_threshold_usd","type":"DECIMAL","label":"CTR Threshold USD"},{"name":"rule_decision","type":"STRING","label":"Decision"},{"name":"triggered_rules","type":"JSON","label":"Rule Evidence"}]'::jsonb,
    'txn_ts DESC', TRUE, NULL, CURRENT_TIMESTAMP
FROM reports r
WHERE r.report_code = 'CTR_005'
  AND NOT EXISTS (
      SELECT 1 FROM report_definitions d WHERE d.report_id = r.id AND d.version = 1
  );
