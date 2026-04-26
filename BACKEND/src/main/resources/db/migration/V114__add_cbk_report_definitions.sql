-- ============================================================
-- HOK-58: CBK (Kenya Central Bank) Regulatory Report Definitions
-- Migration: V114__add_cbk_report_definitions.sql
-- Description: Seeds 6 CBK report entries and their query definitions
--              for daily/weekly/monthly/quarterly/semi-annual/annual
--              regulatory submissions to the Central Bank of Kenya.
-- ============================================================

-- ============================================================
-- 1. REGISTER CBK REPORTS IN MASTER REGISTRY
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, regulatory_template, enabled)
VALUES
    ('CBK_DAILY',       'CBK Daily AML Summary',             'CBK_REGULATORY', 'Daily summary of AML activity, high-value transactions (>KES 1M), alerts, and SAR filings for CBK submission',                 'REGULATORY', 'transactions', TRUE, 'CBK_AML_REPORT', TRUE),
    ('CBK_WEEKLY',      'CBK Weekly Transaction Report',     'CBK_REGULATORY', 'Weekly transaction volume, high-value flows, suspicious activity, and merchant summaries for CBK submission',                   'REGULATORY', 'transactions', TRUE, 'CBK_AML_REPORT', TRUE),
    ('CBK_MONTHLY',     'CBK Monthly AML Report',            'CBK_REGULATORY', 'Monthly AML compliance report covering transactions, alerts, SARs, and risk metrics for CBK submission',                       'REGULATORY', 'transactions', TRUE, 'CBK_AML_REPORT', TRUE),
    ('CBK_QUARTERLY',   'CBK Quarterly Compliance Report',   'CBK_REGULATORY', 'Quarterly compliance and AML performance report with trend analysis for CBK submission',                                        'REGULATORY', 'transactions', TRUE, 'CBK_AML_REPORT', TRUE),
    ('CBK_SEMI_ANNUAL', 'CBK Semi-Annual Risk Review',       'CBK_REGULATORY', 'Semi-annual AML risk review with merchant risk profiles, rule effectiveness, and regulatory metrics for CBK submission',       'REGULATORY', 'transactions', TRUE, 'CBK_AML_REPORT', TRUE),
    ('CBK_ANNUAL',      'CBK Annual Regulatory Submission',  'CBK_REGULATORY', 'Annual comprehensive AML regulatory submission covering all compliance obligations to the Central Bank of Kenya',              'REGULATORY', 'transactions', TRUE, 'CBK_AML_REPORT', TRUE)
ON CONFLICT (report_code) DO NOTHING;

-- ============================================================
-- 2. INSERT QUERY DEFINITIONS FOR EACH CBK REPORT
--    All 6 reports share the same SQL template — the period
--    is expressed via the dateFrom/dateTo parameters supplied
--    at execution time. psp_id=NULL means available to all PSPs.
-- ============================================================

-- CBK Daily AML Summary
INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, aggregations, group_by_fields, order_by_default, is_active, created_by, created_at)
SELECT
    r.id,
    1,
    'SELECT
        DATE(t.txn_ts)                                          AS report_date,
        COUNT(*)                                                AS total_transactions,
        SUM(t.amount_cents) / 100.0                            AS total_amount_kes,
        COUNT(CASE WHEN t.amount_cents >= 100000000 THEN 1 END) AS high_value_count,
        SUM(CASE WHEN t.amount_cents >= 100000000 THEN t.amount_cents ELSE 0 END) / 100.0
                                                                AS high_value_amount_kes,
        COUNT(DISTINCT t.merchant_id)                          AS unique_merchants,
        COUNT(DISTINCT t.pan_hash)                             AS unique_cards,
        (SELECT COUNT(*) FROM alerts a
            WHERE a.psp_id = :pspId
            AND a.created_at BETWEEN :dateFrom AND :dateTo)    AS total_alerts,
        (SELECT COUNT(*) FROM alerts a
            WHERE a.psp_id = :pspId
            AND a.severity = ''CRITICAL''
            AND a.created_at BETWEEN :dateFrom AND :dateTo)    AS critical_alerts,
        (SELECT COUNT(*) FROM suspicious_activity_reports sar
            WHERE sar.psp_id = :pspId
            AND sar.created_at BETWEEN :dateFrom AND :dateTo)  AS sar_count,
        (SELECT COALESCE(SUM(sar.total_suspicious_amount), 0)
            FROM suspicious_activity_reports sar
            WHERE sar.psp_id = :pspId
            AND sar.created_at BETWEEN :dateFrom AND :dateTo)  AS sar_total_amount
    FROM transactions t
    WHERE t.psp_id = :pspId
    AND t.txn_ts BETWEEN :dateFrom AND :dateTo
    GROUP BY DATE(t.txn_ts)
    ORDER BY report_date DESC',
    'SELECT COUNT(DISTINCT DATE(t.txn_ts)) FROM transactions t WHERE t.psp_id = :pspId AND t.txn_ts BETWEEN :dateFrom AND :dateTo',
    '[
        {"name": "pspId",    "type": "LONG",     "required": true,  "defaultValue": null},
        {"name": "dateFrom", "type": "DATETIME", "required": true,  "defaultValue": null},
        {"name": "dateTo",   "type": "DATETIME", "required": true,  "defaultValue": null}
    ]'::jsonb,
    '[
        {"field": "report_date", "type": "DATE_RANGE"}
    ]'::jsonb,
    '[
        {"name": "report_date",          "type": "DATE",     "label": "Date",                       "sortable": true},
        {"name": "total_transactions",   "type": "INTEGER",  "label": "Total Transactions",         "sortable": true},
        {"name": "total_amount_kes",     "type": "CURRENCY", "label": "Total Amount (KES)",         "sortable": true},
        {"name": "high_value_count",     "type": "INTEGER",  "label": "High-Value Txns (>1M KES)",  "sortable": true},
        {"name": "high_value_amount_kes","type": "CURRENCY", "label": "High-Value Amount (KES)",    "sortable": true},
        {"name": "unique_merchants",     "type": "INTEGER",  "label": "Unique Merchants",           "sortable": true},
        {"name": "unique_cards",         "type": "INTEGER",  "label": "Unique Cards",               "sortable": true},
        {"name": "total_alerts",         "type": "INTEGER",  "label": "Total Alerts",               "sortable": true},
        {"name": "critical_alerts",      "type": "INTEGER",  "label": "Critical Alerts",            "sortable": true},
        {"name": "sar_count",            "type": "INTEGER",  "label": "SARs Filed",                 "sortable": true},
        {"name": "sar_total_amount",     "type": "CURRENCY", "label": "SAR Total Amount (KES)",     "sortable": true}
    ]'::jsonb,
    '[
        {"function": "SUM", "field": "total_transactions",    "label": "Grand Total Transactions"},
        {"function": "SUM", "field": "total_amount_kes",      "label": "Grand Total Amount (KES)"},
        {"function": "SUM", "field": "high_value_count",      "label": "Total High-Value Txns"},
        {"function": "SUM", "field": "high_value_amount_kes", "label": "Total High-Value Amount"},
        {"function": "SUM", "field": "sar_count",             "label": "Total SARs"}
    ]'::jsonb,
    '["report_date"]'::jsonb,
    'report_date DESC',
    TRUE,
    NULL,
    NOW()
FROM reports r WHERE r.report_code = 'CBK_DAILY';

-- CBK Weekly Transaction Report
INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, aggregations, group_by_fields, order_by_default, is_active, created_by, created_at)
SELECT
    r.id,
    1,
    'SELECT
        DATE_TRUNC(''week'', t.txn_ts)                          AS week_start,
        COUNT(*)                                                AS total_transactions,
        SUM(t.amount_cents) / 100.0                            AS total_amount_kes,
        COUNT(CASE WHEN t.amount_cents >= 100000000 THEN 1 END) AS high_value_count,
        SUM(CASE WHEN t.amount_cents >= 100000000 THEN t.amount_cents ELSE 0 END) / 100.0
                                                                AS high_value_amount_kes,
        COUNT(DISTINCT t.merchant_id)                          AS unique_merchants,
        COUNT(DISTINCT t.pan_hash)                             AS unique_cards,
        COUNT(CASE WHEN tf.action_taken = ''BLOCK'' THEN 1 END) AS blocked_count,
        COUNT(CASE WHEN tf.action_taken = ''HOLD''  THEN 1 END) AS held_count,
        (SELECT COUNT(*) FROM alerts a
            WHERE a.psp_id = :pspId
            AND DATE_TRUNC(''week'', a.created_at) = DATE_TRUNC(''week'', t.txn_ts)) AS weekly_alerts,
        (SELECT COUNT(*) FROM suspicious_activity_reports sar
            WHERE sar.psp_id = :pspId
            AND DATE_TRUNC(''week'', sar.created_at) = DATE_TRUNC(''week'', t.txn_ts)) AS weekly_sars
    FROM transactions t
    LEFT JOIN transaction_features tf ON t.txn_id = tf.txn_id
    WHERE t.psp_id = :pspId
    AND t.txn_ts BETWEEN :dateFrom AND :dateTo
    GROUP BY DATE_TRUNC(''week'', t.txn_ts)
    ORDER BY week_start DESC',
    'SELECT COUNT(DISTINCT DATE_TRUNC(''week'', t.txn_ts)) FROM transactions t WHERE t.psp_id = :pspId AND t.txn_ts BETWEEN :dateFrom AND :dateTo',
    '[
        {"name": "pspId",    "type": "LONG",     "required": true},
        {"name": "dateFrom", "type": "DATETIME", "required": true},
        {"name": "dateTo",   "type": "DATETIME", "required": true}
    ]'::jsonb,
    '[{"field": "week_start", "type": "DATE_RANGE"}]'::jsonb,
    '[
        {"name": "week_start",           "type": "DATE",     "label": "Week Starting",              "sortable": true},
        {"name": "total_transactions",   "type": "INTEGER",  "label": "Total Transactions",         "sortable": true},
        {"name": "total_amount_kes",     "type": "CURRENCY", "label": "Total Amount (KES)",         "sortable": true},
        {"name": "high_value_count",     "type": "INTEGER",  "label": "High-Value Txns (>1M KES)",  "sortable": true},
        {"name": "high_value_amount_kes","type": "CURRENCY", "label": "High-Value Amount (KES)",    "sortable": true},
        {"name": "unique_merchants",     "type": "INTEGER",  "label": "Unique Merchants",           "sortable": true},
        {"name": "unique_cards",         "type": "INTEGER",  "label": "Unique Cards",               "sortable": true},
        {"name": "blocked_count",        "type": "INTEGER",  "label": "Blocked Transactions",       "sortable": true},
        {"name": "held_count",           "type": "INTEGER",  "label": "Held Transactions",          "sortable": true},
        {"name": "weekly_alerts",        "type": "INTEGER",  "label": "Alerts This Week",           "sortable": true},
        {"name": "weekly_sars",          "type": "INTEGER",  "label": "SARs This Week",             "sortable": true}
    ]'::jsonb,
    '[
        {"function": "SUM", "field": "total_transactions",  "label": "Period Total Transactions"},
        {"function": "SUM", "field": "total_amount_kes",    "label": "Period Total Amount (KES)"},
        {"function": "SUM", "field": "high_value_count",    "label": "Period High-Value Txns"},
        {"function": "SUM", "field": "weekly_sars",         "label": "Period Total SARs"}
    ]'::jsonb,
    '["week_start"]'::jsonb,
    'week_start DESC',
    TRUE,
    NULL,
    NOW()
FROM reports r WHERE r.report_code = 'CBK_WEEKLY';

-- CBK Monthly AML Report
INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, aggregations, group_by_fields, order_by_default, is_active, created_by, created_at)
SELECT
    r.id,
    1,
    'SELECT
        TO_CHAR(DATE_TRUNC(''month'', t.txn_ts), ''YYYY-MM'')   AS report_month,
        COUNT(*)                                                AS total_transactions,
        SUM(t.amount_cents) / 100.0                            AS total_amount_kes,
        COUNT(CASE WHEN t.amount_cents >= 100000000 THEN 1 END) AS high_value_count,
        SUM(CASE WHEN t.amount_cents >= 100000000 THEN t.amount_cents ELSE 0 END) / 100.0
                                                                AS high_value_amount_kes,
        COUNT(DISTINCT t.merchant_id)                          AS unique_merchants,
        COUNT(DISTINCT t.pan_hash)                             AS unique_cards,
        (SELECT COUNT(*) FROM alerts a
            WHERE a.psp_id = :pspId
            AND DATE_TRUNC(''month'', a.created_at) = DATE_TRUNC(''month'', t.txn_ts)) AS monthly_alerts,
        (SELECT COUNT(*) FROM alerts a
            WHERE a.psp_id = :pspId AND a.severity = ''CRITICAL''
            AND DATE_TRUNC(''month'', a.created_at) = DATE_TRUNC(''month'', t.txn_ts)) AS critical_alerts,
        (SELECT COUNT(*) FROM suspicious_activity_reports sar
            WHERE sar.psp_id = :pspId
            AND DATE_TRUNC(''month'', sar.created_at) = DATE_TRUNC(''month'', t.txn_ts)) AS sar_filed,
        (SELECT COALESCE(SUM(sar.total_suspicious_amount), 0)
            FROM suspicious_activity_reports sar
            WHERE sar.psp_id = :pspId
            AND DATE_TRUNC(''month'', sar.created_at) = DATE_TRUNC(''month'', t.txn_ts)) AS sar_total_amount
    FROM transactions t
    WHERE t.psp_id = :pspId
    AND t.txn_ts BETWEEN :dateFrom AND :dateTo
    GROUP BY DATE_TRUNC(''month'', t.txn_ts)
    ORDER BY report_month DESC',
    'SELECT COUNT(DISTINCT DATE_TRUNC(''month'', t.txn_ts)) FROM transactions t WHERE t.psp_id = :pspId AND t.txn_ts BETWEEN :dateFrom AND :dateTo',
    '[
        {"name": "pspId",    "type": "LONG",     "required": true},
        {"name": "dateFrom", "type": "DATETIME", "required": true},
        {"name": "dateTo",   "type": "DATETIME", "required": true}
    ]'::jsonb,
    '[{"field": "report_month", "type": "STRING"}]'::jsonb,
    '[
        {"name": "report_month",         "type": "STRING",   "label": "Month",                      "sortable": true},
        {"name": "total_transactions",   "type": "INTEGER",  "label": "Total Transactions",         "sortable": true},
        {"name": "total_amount_kes",     "type": "CURRENCY", "label": "Total Amount (KES)",         "sortable": true},
        {"name": "high_value_count",     "type": "INTEGER",  "label": "High-Value Txns (>1M KES)",  "sortable": true},
        {"name": "high_value_amount_kes","type": "CURRENCY", "label": "High-Value Amount (KES)",    "sortable": true},
        {"name": "unique_merchants",     "type": "INTEGER",  "label": "Unique Merchants",           "sortable": true},
        {"name": "unique_cards",         "type": "INTEGER",  "label": "Unique Cards",               "sortable": true},
        {"name": "monthly_alerts",       "type": "INTEGER",  "label": "Total Alerts",               "sortable": true},
        {"name": "critical_alerts",      "type": "INTEGER",  "label": "Critical Alerts",            "sortable": true},
        {"name": "sar_filed",            "type": "INTEGER",  "label": "SARs Filed",                 "sortable": true},
        {"name": "sar_total_amount",     "type": "CURRENCY", "label": "SAR Total Amount (KES)",     "sortable": true}
    ]'::jsonb,
    '[
        {"function": "SUM", "field": "total_transactions",  "label": "Period Total Transactions"},
        {"function": "SUM", "field": "total_amount_kes",    "label": "Period Total Amount (KES)"},
        {"function": "SUM", "field": "high_value_count",    "label": "Period High-Value Txns"},
        {"function": "SUM", "field": "sar_filed",           "label": "Period Total SARs"}
    ]'::jsonb,
    '["report_month"]'::jsonb,
    'report_month DESC',
    TRUE,
    NULL,
    NOW()
FROM reports r WHERE r.report_code = 'CBK_MONTHLY';

-- CBK Quarterly Compliance Report
INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, aggregations, group_by_fields, order_by_default, is_active, created_by, created_at)
SELECT
    r.id,
    1,
    'SELECT
        EXTRACT(YEAR FROM t.txn_ts)    AS report_year,
        EXTRACT(QUARTER FROM t.txn_ts) AS report_quarter,
        TO_CHAR(DATE_TRUNC(''quarter'', t.txn_ts), ''YYYY-"Q"Q'') AS quarter_label,
        COUNT(*)                                                AS total_transactions,
        SUM(t.amount_cents) / 100.0                            AS total_amount_kes,
        COUNT(CASE WHEN t.amount_cents >= 100000000 THEN 1 END) AS high_value_count,
        SUM(CASE WHEN t.amount_cents >= 100000000 THEN t.amount_cents ELSE 0 END) / 100.0
                                                                AS high_value_amount_kes,
        COUNT(DISTINCT t.merchant_id)                          AS unique_merchants,
        (SELECT COUNT(*) FROM alerts a
            WHERE a.psp_id = :pspId
            AND EXTRACT(YEAR FROM a.created_at)    = EXTRACT(YEAR FROM t.txn_ts)
            AND EXTRACT(QUARTER FROM a.created_at) = EXTRACT(QUARTER FROM t.txn_ts)) AS quarterly_alerts,
        (SELECT COUNT(*) FROM suspicious_activity_reports sar
            WHERE sar.psp_id = :pspId
            AND EXTRACT(YEAR FROM sar.created_at)    = EXTRACT(YEAR FROM t.txn_ts)
            AND EXTRACT(QUARTER FROM sar.created_at) = EXTRACT(QUARTER FROM t.txn_ts)) AS sar_filed,
        (SELECT COALESCE(SUM(sar.total_suspicious_amount), 0)
            FROM suspicious_activity_reports sar
            WHERE sar.psp_id = :pspId
            AND EXTRACT(YEAR FROM sar.created_at)    = EXTRACT(YEAR FROM t.txn_ts)
            AND EXTRACT(QUARTER FROM sar.created_at) = EXTRACT(QUARTER FROM t.txn_ts)) AS sar_total_amount
    FROM transactions t
    WHERE t.psp_id = :pspId
    AND t.txn_ts BETWEEN :dateFrom AND :dateTo
    GROUP BY EXTRACT(YEAR FROM t.txn_ts), EXTRACT(QUARTER FROM t.txn_ts), DATE_TRUNC(''quarter'', t.txn_ts)
    ORDER BY report_year DESC, report_quarter DESC',
    'SELECT COUNT(DISTINCT EXTRACT(QUARTER FROM t.txn_ts)) FROM transactions t WHERE t.psp_id = :pspId AND t.txn_ts BETWEEN :dateFrom AND :dateTo',
    '[
        {"name": "pspId",    "type": "LONG",     "required": true},
        {"name": "dateFrom", "type": "DATETIME", "required": true},
        {"name": "dateTo",   "type": "DATETIME", "required": true}
    ]'::jsonb,
    '[{"field": "quarter_label", "type": "STRING"}]'::jsonb,
    '[
        {"name": "quarter_label",        "type": "STRING",   "label": "Quarter",                    "sortable": true},
        {"name": "total_transactions",   "type": "INTEGER",  "label": "Total Transactions",         "sortable": true},
        {"name": "total_amount_kes",     "type": "CURRENCY", "label": "Total Amount (KES)",         "sortable": true},
        {"name": "high_value_count",     "type": "INTEGER",  "label": "High-Value Txns (>1M KES)",  "sortable": true},
        {"name": "high_value_amount_kes","type": "CURRENCY", "label": "High-Value Amount (KES)",    "sortable": true},
        {"name": "unique_merchants",     "type": "INTEGER",  "label": "Unique Merchants",           "sortable": true},
        {"name": "quarterly_alerts",     "type": "INTEGER",  "label": "Total Alerts",               "sortable": true},
        {"name": "sar_filed",            "type": "INTEGER",  "label": "SARs Filed",                 "sortable": true},
        {"name": "sar_total_amount",     "type": "CURRENCY", "label": "SAR Total Amount (KES)",     "sortable": true}
    ]'::jsonb,
    '[
        {"function": "SUM", "field": "total_transactions",  "label": "Period Total Transactions"},
        {"function": "SUM", "field": "total_amount_kes",    "label": "Period Total Amount (KES)"},
        {"function": "SUM", "field": "sar_filed",           "label": "Period Total SARs"}
    ]'::jsonb,
    '["report_year", "report_quarter"]'::jsonb,
    'report_year DESC, report_quarter DESC',
    TRUE,
    NULL,
    NOW()
FROM reports r WHERE r.report_code = 'CBK_QUARTERLY';

-- CBK Semi-Annual Risk Review
INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, aggregations, group_by_fields, order_by_default, is_active, created_by, created_at)
SELECT
    r.id,
    1,
    'SELECT
        EXTRACT(YEAR FROM t.txn_ts)                             AS report_year,
        CASE WHEN EXTRACT(MONTH FROM t.txn_ts) <= 6 THEN 1 ELSE 2 END AS half,
        CASE WHEN EXTRACT(MONTH FROM t.txn_ts) <= 6
             THEN CAST(EXTRACT(YEAR FROM t.txn_ts) AS TEXT) || ''-H1''
             ELSE CAST(EXTRACT(YEAR FROM t.txn_ts) AS TEXT) || ''-H2''
        END                                                     AS half_label,
        COUNT(*)                                                AS total_transactions,
        SUM(t.amount_cents) / 100.0                            AS total_amount_kes,
        COUNT(CASE WHEN t.amount_cents >= 100000000 THEN 1 END) AS high_value_count,
        SUM(CASE WHEN t.amount_cents >= 100000000 THEN t.amount_cents ELSE 0 END) / 100.0
                                                                AS high_value_amount_kes,
        COUNT(DISTINCT t.merchant_id)                          AS unique_merchants,
        COUNT(DISTINCT t.pan_hash)                             AS unique_cards,
        (SELECT COUNT(*) FROM alerts a
            WHERE a.psp_id = :pspId
            AND EXTRACT(YEAR FROM a.created_at) = EXTRACT(YEAR FROM t.txn_ts)
            AND (CASE WHEN EXTRACT(MONTH FROM a.created_at) <= 6 THEN 1 ELSE 2 END)
                 = (CASE WHEN EXTRACT(MONTH FROM t.txn_ts) <= 6 THEN 1 ELSE 2 END)) AS period_alerts,
        (SELECT COUNT(*) FROM suspicious_activity_reports sar
            WHERE sar.psp_id = :pspId
            AND EXTRACT(YEAR FROM sar.created_at) = EXTRACT(YEAR FROM t.txn_ts)
            AND (CASE WHEN EXTRACT(MONTH FROM sar.created_at) <= 6 THEN 1 ELSE 2 END)
                 = (CASE WHEN EXTRACT(MONTH FROM t.txn_ts) <= 6 THEN 1 ELSE 2 END)) AS sar_filed,
        (SELECT COALESCE(SUM(sar.total_suspicious_amount), 0)
            FROM suspicious_activity_reports sar
            WHERE sar.psp_id = :pspId
            AND EXTRACT(YEAR FROM sar.created_at) = EXTRACT(YEAR FROM t.txn_ts)
            AND (CASE WHEN EXTRACT(MONTH FROM sar.created_at) <= 6 THEN 1 ELSE 2 END)
                 = (CASE WHEN EXTRACT(MONTH FROM t.txn_ts) <= 6 THEN 1 ELSE 2 END)) AS sar_total_amount
    FROM transactions t
    WHERE t.psp_id = :pspId
    AND t.txn_ts BETWEEN :dateFrom AND :dateTo
    GROUP BY EXTRACT(YEAR FROM t.txn_ts),
             CASE WHEN EXTRACT(MONTH FROM t.txn_ts) <= 6 THEN 1 ELSE 2 END,
             CASE WHEN EXTRACT(MONTH FROM t.txn_ts) <= 6
                  THEN CAST(EXTRACT(YEAR FROM t.txn_ts) AS TEXT) || ''-H1''
                  ELSE CAST(EXTRACT(YEAR FROM t.txn_ts) AS TEXT) || ''-H2'' END
    ORDER BY report_year DESC, half DESC',
    'SELECT COUNT(DISTINCT CAST(EXTRACT(YEAR FROM t.txn_ts) AS TEXT) || CASE WHEN EXTRACT(MONTH FROM t.txn_ts) <= 6 THEN ''H1'' ELSE ''H2'' END) FROM transactions t WHERE t.psp_id = :pspId AND t.txn_ts BETWEEN :dateFrom AND :dateTo',
    '[
        {"name": "pspId",    "type": "LONG",     "required": true},
        {"name": "dateFrom", "type": "DATETIME", "required": true},
        {"name": "dateTo",   "type": "DATETIME", "required": true}
    ]'::jsonb,
    '[{"field": "half_label", "type": "STRING"}]'::jsonb,
    '[
        {"name": "half_label",           "type": "STRING",   "label": "Half-Year",                  "sortable": true},
        {"name": "total_transactions",   "type": "INTEGER",  "label": "Total Transactions",         "sortable": true},
        {"name": "total_amount_kes",     "type": "CURRENCY", "label": "Total Amount (KES)",         "sortable": true},
        {"name": "high_value_count",     "type": "INTEGER",  "label": "High-Value Txns (>1M KES)",  "sortable": true},
        {"name": "high_value_amount_kes","type": "CURRENCY", "label": "High-Value Amount (KES)",    "sortable": true},
        {"name": "unique_merchants",     "type": "INTEGER",  "label": "Unique Merchants",           "sortable": true},
        {"name": "unique_cards",         "type": "INTEGER",  "label": "Unique Cards",               "sortable": true},
        {"name": "period_alerts",        "type": "INTEGER",  "label": "Total Alerts",               "sortable": true},
        {"name": "sar_filed",            "type": "INTEGER",  "label": "SARs Filed",                 "sortable": true},
        {"name": "sar_total_amount",     "type": "CURRENCY", "label": "SAR Total Amount (KES)",     "sortable": true}
    ]'::jsonb,
    '[
        {"function": "SUM", "field": "total_transactions",  "label": "Period Total Transactions"},
        {"function": "SUM", "field": "total_amount_kes",    "label": "Period Total Amount (KES)"},
        {"function": "SUM", "field": "sar_filed",           "label": "Period Total SARs"}
    ]'::jsonb,
    '["report_year", "half"]'::jsonb,
    'report_year DESC, half DESC',
    TRUE,
    NULL,
    NOW()
FROM reports r WHERE r.report_code = 'CBK_SEMI_ANNUAL';

-- CBK Annual Regulatory Submission
INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, aggregations, group_by_fields, order_by_default, is_active, created_by, created_at)
SELECT
    r.id,
    1,
    'SELECT
        EXTRACT(YEAR FROM t.txn_ts)                             AS report_year,
        COUNT(*)                                                AS total_transactions,
        SUM(t.amount_cents) / 100.0                            AS total_amount_kes,
        COUNT(CASE WHEN t.amount_cents >= 100000000 THEN 1 END) AS high_value_count,
        SUM(CASE WHEN t.amount_cents >= 100000000 THEN t.amount_cents ELSE 0 END) / 100.0
                                                                AS high_value_amount_kes,
        COUNT(DISTINCT t.merchant_id)                          AS unique_merchants,
        COUNT(DISTINCT t.pan_hash)                             AS unique_cards,
        (SELECT COUNT(*) FROM alerts a
            WHERE a.psp_id = :pspId
            AND EXTRACT(YEAR FROM a.created_at) = EXTRACT(YEAR FROM t.txn_ts)) AS annual_alerts,
        (SELECT COUNT(*) FROM alerts a
            WHERE a.psp_id = :pspId AND a.severity = ''CRITICAL''
            AND EXTRACT(YEAR FROM a.created_at) = EXTRACT(YEAR FROM t.txn_ts)) AS critical_alerts,
        (SELECT COUNT(*) FROM suspicious_activity_reports sar
            WHERE sar.psp_id = :pspId
            AND EXTRACT(YEAR FROM sar.created_at) = EXTRACT(YEAR FROM t.txn_ts)) AS sar_filed,
        (SELECT COALESCE(SUM(sar.total_suspicious_amount), 0)
            FROM suspicious_activity_reports sar
            WHERE sar.psp_id = :pspId
            AND EXTRACT(YEAR FROM sar.created_at) = EXTRACT(YEAR FROM t.txn_ts)) AS sar_total_amount,
        (SELECT COUNT(*) FROM compliance_cases cc
            WHERE cc.psp_id = :pspId
            AND EXTRACT(YEAR FROM cc.created_at) = EXTRACT(YEAR FROM t.txn_ts)) AS compliance_cases_opened,
        (SELECT COUNT(*) FROM regulatory_submissions rs
            WHERE rs.psp_id = :pspId
            AND EXTRACT(YEAR FROM rs.created_at) = EXTRACT(YEAR FROM t.txn_ts)) AS regulatory_submissions
    FROM transactions t
    WHERE t.psp_id = :pspId
    AND t.txn_ts BETWEEN :dateFrom AND :dateTo
    GROUP BY EXTRACT(YEAR FROM t.txn_ts)
    ORDER BY report_year DESC',
    'SELECT COUNT(DISTINCT EXTRACT(YEAR FROM t.txn_ts)) FROM transactions t WHERE t.psp_id = :pspId AND t.txn_ts BETWEEN :dateFrom AND :dateTo',
    '[
        {"name": "pspId",    "type": "LONG",     "required": true},
        {"name": "dateFrom", "type": "DATETIME", "required": true},
        {"name": "dateTo",   "type": "DATETIME", "required": true}
    ]'::jsonb,
    '[{"field": "report_year", "type": "INTEGER"}]'::jsonb,
    '[
        {"name": "report_year",            "type": "INTEGER",  "label": "Year",                       "sortable": true},
        {"name": "total_transactions",     "type": "INTEGER",  "label": "Total Transactions",         "sortable": true},
        {"name": "total_amount_kes",       "type": "CURRENCY", "label": "Total Amount (KES)",         "sortable": true},
        {"name": "high_value_count",       "type": "INTEGER",  "label": "High-Value Txns (>1M KES)",  "sortable": true},
        {"name": "high_value_amount_kes",  "type": "CURRENCY", "label": "High-Value Amount (KES)",    "sortable": true},
        {"name": "unique_merchants",       "type": "INTEGER",  "label": "Unique Merchants",           "sortable": true},
        {"name": "unique_cards",           "type": "INTEGER",  "label": "Unique Cards",               "sortable": true},
        {"name": "annual_alerts",          "type": "INTEGER",  "label": "Total Alerts",               "sortable": true},
        {"name": "critical_alerts",        "type": "INTEGER",  "label": "Critical Alerts",            "sortable": true},
        {"name": "sar_filed",              "type": "INTEGER",  "label": "SARs Filed",                 "sortable": true},
        {"name": "sar_total_amount",       "type": "CURRENCY", "label": "SAR Total Amount (KES)",     "sortable": true},
        {"name": "compliance_cases_opened","type": "INTEGER",  "label": "Compliance Cases Opened",    "sortable": true},
        {"name": "regulatory_submissions", "type": "INTEGER",  "label": "Regulatory Submissions",     "sortable": true}
    ]'::jsonb,
    '[
        {"function": "SUM", "field": "total_transactions",     "label": "All-Time Total Transactions"},
        {"function": "SUM", "field": "total_amount_kes",       "label": "All-Time Total Amount (KES)"},
        {"function": "SUM", "field": "sar_filed",              "label": "All-Time SARs Filed"},
        {"function": "SUM", "field": "compliance_cases_opened","label": "All-Time Cases Opened"}
    ]'::jsonb,
    '["report_year"]'::jsonb,
    'report_year DESC',
    TRUE,
    NULL,
    NOW()
FROM reports r WHERE r.report_code = 'CBK_ANNUAL';

-- ============================================================
-- 3. SEED CBK REGULATORY TEMPLATE
-- ============================================================

INSERT INTO regulatory_templates (template_code, template_name, regulator_code, jurisdiction, submission_type, schema_definition, validation_rules, required_fields, is_active, created_at)
VALUES (
    'CBK_AML_REPORT',
    'CBK AML Compliance Report (Kenya)',
    'CBK',
    'KE',
    'AML_REPORT',
    '[
        {"name": "reporting_institution",    "type": "STRING",  "maxLength": 150},
        {"name": "reporting_period_start",   "type": "DATE"},
        {"name": "reporting_period_end",     "type": "DATE"},
        {"name": "total_transactions",       "type": "INTEGER"},
        {"name": "total_amount_kes",         "type": "DECIMAL", "precision": 19, "scale": 2},
        {"name": "high_value_transactions",  "type": "INTEGER"},
        {"name": "high_value_amount_kes",    "type": "DECIMAL", "precision": 19, "scale": 2},
        {"name": "total_alerts",             "type": "INTEGER"},
        {"name": "sar_count",                "type": "INTEGER"},
        {"name": "sar_total_amount_kes",     "type": "DECIMAL", "precision": 19, "scale": 2},
        {"name": "narrative",                "type": "TEXT",    "maxLength": 8000}
    ]'::jsonb,
    '[
        {"field": "high_value_transactions", "rule": "MIN", "value": 0},
        {"field": "total_amount_kes",        "rule": "MIN", "value": 0}
    ]'::jsonb,
    '["reporting_institution", "reporting_period_start", "reporting_period_end", "total_transactions", "total_amount_kes"]'::jsonb,
    TRUE,
    NOW()
)
ON CONFLICT (template_code) DO NOTHING;
