-- ============================================================
-- AML Reporting System - Report Definitions Seed Data
-- Migration: V109__report_definitions_seed.sql
-- Description: Seeds all 85+ report definitions
-- ============================================================

-- ============================================================
-- 1. AML & FRAUD REPORTS (8)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, regulatory_template, enabled) VALUES
('SAR_001', 'Suspicious Activity Report Summary', 'AML_FRAUD', 'Summary of all SARs filed with status and aging', 'REGULATORY', 'suspicious_activity_reports', TRUE, 'FinCEN_SAR', TRUE),
('SAR_002', 'Suspicious Transaction Report', 'AML_FRAUD', 'Detailed report of suspicious transactions', 'REGULATORY', 'transactions', TRUE, 'FinCEN_SAR', TRUE),
('SAR_003', 'Suspicious Matter Report', 'AML_FRAUD', 'Matters requiring AML investigation', 'DYNAMIC', 'compliance_cases', TRUE, NULL, TRUE),
('SAR_004', 'Attempted Transaction Report', 'AML_FRAUD', 'Suspicious attempted transactions', 'DYNAMIC', 'transactions', FALSE, NULL, TRUE),
('SAR_005', 'Internal Escalation Report', 'AML_FRAUD', 'Cases escalated internally for AML review', 'DYNAMIC', 'compliance_cases', FALSE, NULL, TRUE),
('SAR_006', 'SAR History Report', 'AML_FRAUD', 'Historical SAR filing trends and analysis', 'DYNAMIC', 'suspicious_activity_reports', FALSE, NULL, TRUE),
('SAR_007', 'SAR Aging Report', 'AML_FRAUD', 'Aging analysis of pending SARs', 'DYNAMIC', 'suspicious_activity_reports', FALSE, NULL, TRUE),
('SAR_008', 'SAR Pending Approval Report', 'AML_FRAUD', 'SARs pending approval or filing', 'DYNAMIC', 'suspicious_activity_reports', TRUE, NULL, TRUE);

-- ============================================================
-- 2. CURRENCY & THRESHOLD REPORTS (6)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, regulatory_template, enabled) VALUES
('CTR_001', 'Currency Transaction Report', 'CURRENCY_THRESHOLD', 'CTR filings and tracking for large currency transactions', 'REGULATORY', 'transactions', TRUE, 'FinCEN_CTR', TRUE),
('CTR_002', 'Large Cash Transactions Report', 'CURRENCY_THRESHOLD', 'Large cash deposits and withdrawals monitoring', 'DYNAMIC', 'transactions', FALSE, NULL, TRUE),
('CTR_003', 'Cash Deposit Monitoring Report', 'CURRENCY_THRESHOLD', 'Cash deposit trends and analysis', 'DYNAMIC', 'transactions', FALSE, NULL, TRUE),
('CTR_004', 'Cash Withdrawal Monitoring Report', 'CURRENCY_THRESHOLD', 'Cash withdrawal trends and analysis', 'DYNAMIC', 'transactions', FALSE, NULL, TRUE),
('CTR_005', 'Structuring Detection Report', 'CURRENCY_THRESHOLD', 'Potential structuring/structuring detection', 'DYNAMIC', 'transactions', TRUE, NULL, TRUE),
('CTR_006', 'Threshold Breach Report', 'CURRENCY_THRESHOLD', 'Threshold limit breaches and violations', 'DYNAMIC', 'alerts', FALSE, NULL, TRUE);

-- ============================================================
-- 3. TRANSACTION MONITORING REPORTS (12)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled) VALUES
('TXN_001', 'Daily Volume Report', 'TRANSACTION_MONITORING', 'Daily transaction volume summary', 'DYNAMIC', 'transactions', FALSE, TRUE),
('TXN_002', 'Weekly Volume Report', 'TRANSACTION_MONITORING', 'Weekly transaction volume summary', 'DYNAMIC', 'transactions', FALSE, TRUE),
('TXN_003', 'Monthly Volume Report', 'TRANSACTION_MONITORING', 'Monthly transaction volume summary', 'DYNAMIC', 'transactions', FALSE, TRUE),
('TXN_004', 'High Value Transactions Report', 'TRANSACTION_MONITORING', 'High-value transaction analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('TXN_005', 'Unusual Pattern Report', 'TRANSACTION_MONITORING', 'Unusual transaction pattern detection', 'DYNAMIC', 'transactions', FALSE, TRUE),
('TXN_006', 'High Frequency Report', 'TRANSACTION_MONITORING', 'High-frequency transaction analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('TXN_007', 'Rapid Movement Report', 'TRANSACTION_MONITORING', 'Rapid fund movement detection', 'DYNAMIC', 'transactions', FALSE, TRUE),
('TXN_008', 'Burst Activity Report', 'TRANSACTION_MONITORING', 'Sudden burst activity analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('TXN_009', 'High Risk Countries Report', 'TRANSACTION_MONITORING', 'Transactions involving high-risk countries', 'DYNAMIC', 'transactions', FALSE, TRUE),
('TXN_010', 'Sanctioned Jurisdictions Report', 'TRANSACTION_MONITORING', 'Activity in sanctioned jurisdictions', 'REGULATORY', 'transactions', TRUE, TRUE),
('TXN_011', 'Cross Border Flow Report', 'TRANSACTION_MONITORING', 'Cross-border transaction flow analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('TXN_012', 'Risk Corridor Report', 'TRANSACTION_MONITORING', 'High-risk corridor transaction analysis', 'DYNAMIC', 'transactions', FALSE, TRUE);

-- ============================================================
-- 4. CHANNEL MONITORING REPORTS (6)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled) VALUES
('CHN_001', 'ATM Activity Report', 'CHANNEL_MONITORING', 'ATM transaction monitoring and analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('CHN_002', 'POS Activity Report', 'CHANNEL_MONITORING', 'POS transaction monitoring and analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('CHN_003', 'Online Banking Report', 'CHANNEL_MONITORING', 'Online banking activity monitoring', 'DYNAMIC', 'transactions', FALSE, TRUE),
('CHN_004', 'Mobile Money Report', 'CHANNEL_MONITORING', 'Mobile money transaction analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('CHN_005', 'Agent Banking Report', 'CHANNEL_MONITORING', 'Agent banking activity monitoring', 'DYNAMIC', 'transactions', FALSE, TRUE),
('CHN_006', 'Card Not Present Report', 'CHANNEL_MONITORING', 'CNP transaction and fraud analysis', 'DYNAMIC', 'transactions', FALSE, TRUE);

-- ============================================================
-- 5. SANCTIONS SCREENING REPORTS (4)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, regulatory_template, enabled) VALUES
('SNC_001', 'Screening Hits Report', 'SANCTIONS', 'Sanctions screening hit summary', 'DYNAMIC', 'merchant_screening_results', TRUE, NULL, TRUE),
('SNC_002', 'True Match Report', 'SANCTIONS', 'Confirmed sanctions matches', 'REGULATORY', 'merchant_screening_results', TRUE, 'OFAC_REPORT', TRUE),
('SNC_003', 'False Positive Report', 'SANCTIONS', 'False positive analysis and trends', 'DYNAMIC', 'merchant_screening_results', FALSE, NULL, TRUE),
('SNC_004', 'Cross Border Cash Report', 'SANCTIONS', 'Cross-border cash movement monitoring', 'REGULATORY', 'transactions', TRUE, 'FinCEN_CMIR', TRUE);

-- ============================================================
-- 6. FRAUD INCIDENT REPORTS (8)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled) VALUES
('FRD_001', 'Confirmed Fraud Cases Report', 'FRAUD_INCIDENTS', 'Confirmed fraud incident summary', 'DYNAMIC', 'alerts', FALSE, TRUE),
('FRD_002', 'Suspected Fraud Cases Report', 'FRAUD_INCIDENTS', 'Suspected fraud incident tracking', 'DYNAMIC', 'alerts', FALSE, TRUE),
('FRD_003', 'Fraud Loss Report', 'FRAUD_INCIDENTS', 'Actual fraud losses analysis', 'DYNAMIC', 'alerts', FALSE, TRUE),
('FRD_004', 'Attempted Fraud Report', 'FRAUD_INCIDENTS', 'Attempted fraud and prevented losses', 'DYNAMIC', 'alerts', FALSE, TRUE),
('FRD_005', 'Card Present Fraud Report', 'FRAUD_INCIDENTS', 'Card-present fraud analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('FRD_006', 'Card Not Present Fraud Report', 'FRAUD_INCIDENTS', 'CNP fraud detailed analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('FRD_007', 'ATM Fraud Report', 'FRAUD_INCIDENTS', 'ATM fraud incident analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('FRD_008', 'E-commerce Fraud Report', 'FRAUD_INCIDENTS', 'E-commerce fraud analysis', 'DYNAMIC', 'transactions', FALSE, TRUE);

-- ============================================================
-- 7. ALERT & CASE MANAGEMENT REPORTS (10)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled) VALUES
('ALC_001', 'Total Alerts Report', 'ALERT_CASE_MANAGEMENT', 'Alert volume summary and trends', 'DYNAMIC', 'alerts', FALSE, TRUE),
('ALC_002', 'Alerts By Rule Report', 'ALERT_CASE_MANAGEMENT', 'Alerts grouped by triggering rule', 'DYNAMIC', 'alerts', FALSE, TRUE),
('ALC_003', 'Alerts By Risk Level Report', 'ALERT_CASE_MANAGEMENT', 'Alerts by risk classification', 'DYNAMIC', 'alerts', FALSE, TRUE),
('ALC_004', 'False Positive Rate Report', 'ALERT_CASE_MANAGEMENT', 'False positive rate analysis', 'DYNAMIC', 'alerts', FALSE, TRUE),
('ALC_005', 'True Positive Rate Report', 'ALERT_CASE_MANAGEMENT', 'True positive rate analysis', 'DYNAMIC', 'alerts', FALSE, TRUE),
('ALC_006', 'Alert Aging Report', 'ALERT_CASE_MANAGEMENT', 'Alert age distribution and aging', 'DYNAMIC', 'alerts', FALSE, TRUE),
('ALC_007', 'Resolution Time Report', 'ALERT_CASE_MANAGEMENT', 'Alert resolution time metrics', 'DYNAMIC', 'alerts', FALSE, TRUE),
('ALC_008', 'Cases Opened/Closed Report', 'ALERT_CASE_MANAGEMENT', 'Case volume trends', 'DYNAMIC', 'compliance_cases', FALSE, TRUE),
('ALC_009', 'Investigator Workload Report', 'ALERT_CASE_MANAGEMENT', 'Workload distribution by investigator', 'DYNAMIC', 'compliance_cases', FALSE, TRUE),
('ALC_010', 'Alert Disposition Report', 'ALERT_CASE_MANAGEMENT', 'Alert disposition analysis', 'DYNAMIC', 'alerts', FALSE, TRUE);

-- ============================================================
-- 8. RULE ENGINE REPORTS (6)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled) VALUES
('RUL_001', 'Rule Trigger Frequency Report', 'RULE_ENGINE', 'Rule triggering statistics', 'DYNAMIC', 'alerts', FALSE, TRUE),
('RUL_002', 'Top Effective Rules Report', 'RULE_ENGINE', 'Most effective rules by conversion', 'DYNAMIC', 'alerts', FALSE, TRUE),
('RUL_003', 'Least Effective Rules Report', 'RULE_ENGINE', 'Least effective rules analysis', 'DYNAMIC', 'alerts', FALSE, TRUE),
('RUL_004', 'Rule Precision Metrics Report', 'RULE_ENGINE', 'Precision metrics by rule', 'DYNAMIC', 'alerts', FALSE, TRUE),
('RUL_005', 'Rule Recall Metrics Report', 'RULE_ENGINE', 'Recall metrics by rule', 'DYNAMIC', 'alerts', FALSE, TRUE),
('RUL_006', 'Rule Coverage Report', 'RULE_ENGINE', 'Rule coverage analysis', 'DYNAMIC', 'alerts', FALSE, TRUE);

-- ============================================================
-- 9. RISK SCORING & MODELS REPORTS (8)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled) VALUES
('RSK_001', 'Score Distribution Report', 'RISK_SCORING_MODELS', 'Risk score distributions', 'DYNAMIC', 'transaction_features', FALSE, TRUE),
('RSK_002', 'Score Change History Report', 'RISK_SCORING_MODELS', 'Risk score change tracking', 'DYNAMIC', 'transaction_features', FALSE, TRUE),
('RSK_003', 'Risk Trend Report', 'RISK_SCORING_MODELS', 'Risk trend analysis over time', 'DYNAMIC', 'transaction_features', FALSE, TRUE),
('RSK_004', 'High Risk Alerts Report', 'RISK_SCORING_MODELS', 'High-risk customer alerts', 'DYNAMIC', 'alerts', FALSE, TRUE),
('RSK_005', 'Model Accuracy Report', 'RISK_SCORING_MODELS', 'ML model accuracy metrics', 'DYNAMIC', 'model_metrics', FALSE, TRUE),
('RSK_006', 'Model Drift Report', 'RISK_SCORING_MODELS', 'Model drift detection results', 'DYNAMIC', 'model_metrics', FALSE, TRUE),
('RSK_007', 'Feature Importance Report', 'RISK_SCORING_MODELS', 'ML feature importance analysis', 'DYNAMIC', 'model_config', FALSE, TRUE),
('RSK_008', 'Retraining History Report', 'RISK_SCORING_MODELS', 'Model retraining history log', 'DYNAMIC', 'model_metrics', FALSE, TRUE);

-- ============================================================
-- 10. REGULATORY SUBMISSION REPORTS (4)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled) VALUES
('REG_001', 'Filing Log Report', 'REGULATORY_SUBMISSION', 'All regulatory filings log', 'DYNAMIC', 'regulatory_submissions', FALSE, TRUE),
('REG_002', 'Late Filing Report', 'REGULATORY_SUBMISSION', 'Late filing tracking and alerts', 'DYNAMIC', 'regulatory_submissions', TRUE, TRUE),
('REG_003', 'Rejection Report', 'REGULATORY_SUBMISSION', 'Submission rejections analysis', 'DYNAMIC', 'regulatory_submissions', FALSE, TRUE),
('REG_004', 'Amendment History Report', 'REGULATORY_SUBMISSION', 'Filing amendments history', 'DYNAMIC', 'regulatory_submissions', FALSE, TRUE);

-- ============================================================
-- 11. COMPLIANCE MANAGEMENT REPORTS (4)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled) VALUES
('CMP_001', 'Compliance Breach Report', 'COMPLIANCE_MANAGEMENT', 'Compliance breaches and violations', 'DYNAMIC', 'compliance_cases', TRUE, TRUE),
('CMP_002', 'Audit Trail Report', 'COMPLIANCE_MANAGEMENT', 'Detailed audit trail for compliance', 'DYNAMIC', 'audit_logs_enhanced', FALSE, TRUE),
('CMP_003', 'Internal Violations Report', 'COMPLIANCE_MANAGEMENT', 'Internal policy violations', 'DYNAMIC', 'compliance_cases', FALSE, TRUE),
('CMP_004', 'AML Violations Report', 'COMPLIANCE_MANAGEMENT', 'AML regulation violations', 'DYNAMIC', 'compliance_cases', TRUE, TRUE);

-- ============================================================
-- 12. DATA QUALITY REPORTS (5)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled) VALUES
('DQL_001', 'Missing Customer Data Report', 'DATA_QUALITY', 'Missing customer data analysis', 'DYNAMIC', 'data_quality_issues', FALSE, TRUE),
('DQL_002', 'Incomplete Transactions Report', 'DATA_QUALITY', 'Incomplete transaction data', 'DYNAMIC', 'data_quality_issues', FALSE, TRUE),
('DQL_003', 'Invalid ID Report', 'DATA_QUALITY', 'Invalid identification documents', 'DYNAMIC', 'data_quality_issues', FALSE, TRUE),
('DQL_004', 'Duplicate Records Report', 'DATA_QUALITY', 'Duplicate detection results', 'DYNAMIC', 'data_quality_issues', FALSE, TRUE),
('DQL_005', 'Data Inconsistencies Report', 'DATA_QUALITY', 'Data inconsistency analysis', 'DYNAMIC', 'data_quality_issues', FALSE, TRUE);

-- ============================================================
-- 13. CHARGEBACK & DISPUTE REPORTS (4)
-- ============================================================

INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled) VALUES
('CHB_001', 'Chargeback Monitoring Report', 'CHARGEBACK_DISPUTE', 'Chargeback trends and analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('CHB_002', 'Friendly Fraud Report', 'CHARGEBACK_DISPUTE', 'Friendly fraud analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('CHB_003', 'Merchant Dispute Trends Report', 'CHARGEBACK_DISPUTE', 'Merchant dispute analysis', 'DYNAMIC', 'transactions', FALSE, TRUE),
('CHB_004', 'Chargeback Ratios Report', 'CHARGEBACK_DISPUTE', 'Chargeback ratio metrics', 'DYNAMIC', 'transactions', FALSE, TRUE);

-- ============================================================
-- INSERT REPORT DEFINITIONS FOR KEY REPORTS
-- ============================================================

-- SAR Summary Report Definition
INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT 
    r.id, 
    1,
    'SELECT 
        sar.id,
        sar.sar_reference,
        sar.status,
        sar.sar_type,
        sar.jurisdiction,
        sar.suspicious_activity_type,
        sar.total_suspicious_amount,
        sar.filing_deadline,
        sar.filed_at,
        c.case_reference,
        c.status as case_status,
        u.username as created_by,
        sar.created_at,
        sar.updated_at,
        EXTRACT(DAY FROM COALESCE(sar.filed_at, NOW()) - sar.created_at) as days_open
    FROM suspicious_activity_reports sar
    LEFT JOIN compliance_cases c ON sar.case_id = c.id
    LEFT JOIN platform_users u ON sar.created_by_user_id = u.id
    WHERE sar.psp_id = :pspId
    AND sar.created_at BETWEEN :dateFrom AND :dateTo
    AND (:status IS NULL OR sar.status = :status)
    AND (:sarType IS NULL OR sar.sar_type = :sarType)
    AND (:jurisdiction IS NULL OR sar.jurisdiction = :jurisdiction)
    ORDER BY sar.created_at DESC',
    'SELECT COUNT(*) FROM suspicious_activity_reports sar WHERE sar.psp_id = :pspId AND sar.created_at BETWEEN :dateFrom AND :dateTo',
    '[
        {"name": "pspId", "type": "LONG", "required": true, "defaultValue": null},
        {"name": "dateFrom", "type": "DATETIME", "required": true, "defaultValue": null},
        {"name": "dateTo", "type": "DATETIME", "required": true, "defaultValue": null},
        {"name": "status", "type": "STRING", "required": false, "defaultValue": null},
        {"name": "sarType", "type": "STRING", "required": false, "defaultValue": null},
        {"name": "jurisdiction", "type": "STRING", "required": false, "defaultValue": null}
    ]'::jsonb,
    '[
        {"field": "status", "type": "ENUM", "options": ["DRAFT", "PENDING_REVIEW", "APPROVED", "FILED", "REJECTED"]},
        {"field": "sarType", "type": "ENUM", "options": ["INITIAL", "CONTINUING", "CORRECTED"]},
        {"field": "jurisdiction", "type": "STRING"},
        {"field": "amountMin", "type": "DECIMAL"},
        {"field": "amountMax", "type": "DECIMAL"}
    ]'::jsonb,
    '[
        {"name": "sar_reference", "type": "STRING", "label": "SAR Reference", "sortable": true, "filterable": true},
        {"name": "status", "type": "STRING", "label": "Status", "sortable": true, "filterable": true},
        {"name": "sar_type", "type": "STRING", "label": "SAR Type", "sortable": true, "filterable": true},
        {"name": "jurisdiction", "type": "STRING", "label": "Jurisdiction", "sortable": true, "filterable": true},
        {"name": "suspicious_activity_type", "type": "STRING", "label": "Activity Type", "sortable": true, "filterable": true},
        {"name": "total_suspicious_amount", "type": "CURRENCY", "label": "Total Amount", "sortable": true, "filterable": true},
        {"name": "filing_deadline", "type": "DATE", "label": "Filing Deadline", "sortable": true, "filterable": true},
        {"name": "filed_at", "type": "DATETIME", "label": "Filed At", "sortable": true, "filterable": true},
        {"name": "case_reference", "type": "STRING", "label": "Case Reference", "sortable": true, "filterable": true},
        {"name": "created_by", "type": "STRING", "label": "Created By", "sortable": true, "filterable": true},
        {"name": "days_open", "type": "INTEGER", "label": "Days Open", "sortable": true, "filterable": true}
    ]'::jsonb,
    'created_at DESC',
    TRUE,
    1,
    NOW()
FROM reports r WHERE r.report_code = 'SAR_001';

-- Daily Volume Report Definition
INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, aggregations, group_by_fields, order_by_default, is_active, created_by, created_at)
SELECT 
    r.id, 
    1,
    'SELECT 
        DATE(t.txn_ts) as transaction_date,
        COUNT(*) as transaction_count,
        SUM(t.amount_cents) / 100.0 as total_amount,
        AVG(t.amount_cents) / 100.0 as avg_amount,
        COUNT(DISTINCT t.merchant_id) as unique_merchants,
        COUNT(DISTINCT t.pan_hash) as unique_cards,
        COUNT(CASE WHEN tf.score >= 0.7 THEN 1 END) as high_risk_count,
        COUNT(CASE WHEN tf.action_taken = ''BLOCK'' THEN 1 END) as blocked_count,
        COUNT(CASE WHEN tf.action_taken = ''HOLD'' THEN 1 END) as held_count
    FROM transactions t
    LEFT JOIN transaction_features tf ON t.txn_id = tf.txn_id
    WHERE t.psp_id = :pspId
    AND t.txn_ts BETWEEN :dateFrom AND :dateTo
    GROUP BY DATE(t.txn_ts)
    ORDER BY transaction_date DESC',
    'SELECT COUNT(DISTINCT DATE(t.txn_ts)) FROM transactions t WHERE t.psp_id = :pspId AND t.txn_ts BETWEEN :dateFrom AND :dateTo',
    '[
        {"name": "pspId", "type": "LONG", "required": true},
        {"name": "dateFrom", "type": "DATETIME", "required": true},
        {"name": "dateTo", "type": "DATETIME", "required": true}
    ]'::jsonb,
    '[
        {"field": "transaction_date", "type": "DATE_RANGE"}
    ]'::jsonb,
    '[
        {"name": "transaction_date", "type": "DATE", "label": "Date", "sortable": true},
        {"name": "transaction_count", "type": "INTEGER", "label": "Transaction Count", "sortable": true},
        {"name": "total_amount", "type": "CURRENCY", "label": "Total Amount", "sortable": true},
        {"name": "avg_amount", "type": "CURRENCY", "label": "Average Amount", "sortable": true},
        {"name": "unique_merchants", "type": "INTEGER", "label": "Unique Merchants", "sortable": true},
        {"name": "unique_cards", "type": "INTEGER", "label": "Unique Cards", "sortable": true},
        {"name": "high_risk_count", "type": "INTEGER", "label": "High Risk Count", "sortable": true},
        {"name": "blocked_count", "type": "INTEGER", "label": "Blocked Count", "sortable": true},
        {"name": "held_count", "type": "INTEGER", "label": "Held Count", "sortable": true}
    ]'::jsonb,
    '[
        {"function": "SUM", "field": "transaction_count", "label": "Total Transactions"},
        {"function": "SUM", "field": "total_amount", "label": "Grand Total"},
        {"function": "AVG", "field": "avg_amount", "label": "Overall Average"}
    ]'::jsonb,
    '["transaction_date"]'::jsonb,
    'transaction_date DESC',
    TRUE,
    1,
    NOW()
FROM reports r WHERE r.report_code = 'TXN_001';

-- Alert Aging Report Definition
INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT 
    r.id, 
    1,
    'SELECT 
        a.status,
        a.severity,
        a.disposition,
        COUNT(*) as alert_count,
        AVG(EXTRACT(EPOCH FROM (COALESCE(a.disposed_at, NOW()) - a.created_at)) / 86400) as avg_age_days,
        SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - a.created_at)) / 86400 <= 1 THEN 1 ELSE 0 END) as age_0_1_days,
        SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - a.created_at)) / 86400 BETWEEN 1 AND 7 THEN 1 ELSE 0 END) as age_1_7_days,
        SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - a.created_at)) / 86400 BETWEEN 7 AND 30 THEN 1 ELSE 0 END) as age_7_30_days,
        SUM(CASE WHEN EXTRACT(EPOCH FROM (NOW() - a.created_at)) / 86400 > 30 THEN 1 ELSE 0 END) as age_over_30_days
    FROM alerts a
    WHERE a.psp_id = :pspId
    AND a.created_at BETWEEN :dateFrom AND :dateTo
    AND (:status IS NULL OR a.status = :status)
    AND (:severity IS NULL OR a.severity = :severity)
    GROUP BY a.status, a.severity, a.disposition
    ORDER BY alert_count DESC',
    'SELECT COUNT(*) FROM (SELECT 1 FROM alerts a WHERE a.psp_id = :pspId AND a.created_at BETWEEN :dateFrom AND :dateTo GROUP BY a.status, a.severity, a.disposition) sub',
    '[
        {"name": "pspId", "type": "LONG", "required": true},
        {"name": "dateFrom", "type": "DATETIME", "required": true},
        {"name": "dateTo", "type": "DATETIME", "required": true},
        {"name": "status", "type": "STRING", "required": false},
        {"name": "severity", "type": "STRING", "required": false}
    ]'::jsonb,
    '[
        {"field": "status", "type": "ENUM", "options": ["open", "closed", "false_positive"]},
        {"field": "severity", "type": "ENUM", "options": ["INFO", "WARN", "CRITICAL"]},
        {"field": "disposition", "type": "ENUM", "options": ["TRUE_POSITIVE", "FALSE_POSITIVE", "UNDER_INVESTIGATION"]}
    ]'::jsonb,
    '[
        {"name": "status", "type": "STRING", "label": "Status", "sortable": true},
        {"name": "severity", "type": "STRING", "label": "Severity", "sortable": true},
        {"name": "disposition", "type": "STRING", "label": "Disposition", "sortable": true},
        {"name": "alert_count", "type": "INTEGER", "label": "Alert Count", "sortable": true},
        {"name": "avg_age_days", "type": "DECIMAL", "label": "Average Age (Days)", "sortable": true},
        {"name": "age_0_1_days", "type": "INTEGER", "label": "0-1 Days", "sortable": true},
        {"name": "age_1_7_days", "type": "INTEGER", "label": "1-7 Days", "sortable": true},
        {"name": "age_7_30_days", "type": "INTEGER", "label": "7-30 Days", "sortable": true},
        {"name": "age_over_30_days", "type": "INTEGER", "label": "Over 30 Days", "sortable": true}
    ]'::jsonb,
    'alert_count DESC',
    TRUE,
    1,
    NOW()
FROM reports r WHERE r.report_code = 'ALC_006';

-- Regulatory Filing Log Report Definition
INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT 
    r.id, 
    1,
    'SELECT 
        rs.id,
        rs.submission_reference,
        rs.regulator_code,
        rs.submission_type,
        rs.jurisdiction,
        rs.filing_period_start,
        rs.filing_period_end,
        rs.filing_deadline,
        rs.status,
        rs.regulator_reference,
        rs.filed_at,
        pu.username as filed_by,
        rs.created_at,
        CASE 
            WHEN rs.filed_at > rs.filing_deadline THEN TRUE 
            ELSE FALSE 
        END as is_late_filing,
        CASE 
            WHEN rs.filing_deadline IS NOT NULL THEN EXTRACT(DAY FROM rs.filing_deadline - COALESCE(rs.filed_at, NOW()))
            ELSE NULL 
        END as days_until_deadline
    FROM regulatory_submissions rs
    LEFT JOIN platform_users pu ON rs.filed_by = pu.id
    WHERE rs.psp_id = :pspId
    AND rs.created_at BETWEEN :dateFrom AND :dateTo
    AND (:status IS NULL OR rs.status = :status)
    AND (:regulatorCode IS NULL OR rs.regulator_code = :regulatorCode)
    ORDER BY rs.created_at DESC',
    'SELECT COUNT(*) FROM regulatory_submissions rs WHERE rs.psp_id = :pspId AND rs.created_at BETWEEN :dateFrom AND :dateTo',
    '[
        {"name": "pspId", "type": "LONG", "required": true},
        {"name": "dateFrom", "type": "DATETIME", "required": true},
        {"name": "dateTo", "type": "DATETIME", "required": true},
        {"name": "status", "type": "STRING", "required": false},
        {"name": "regulatorCode", "type": "STRING", "required": false}
    ]'::jsonb,
    '[
        {"field": "status", "type": "ENUM", "options": ["DRAFT", "PENDING_REVIEW", "APPROVED", "FILED", "REJECTED", "AMENDED"]},
        {"field": "regulatorCode", "type": "STRING"},
        {"field": "submissionType", "type": "STRING"},
        {"field": "jurisdiction", "type": "STRING"},
        {"field": "isLateFiling", "type": "BOOLEAN"}
    ]'::jsonb,
    '[
        {"name": "submission_reference", "type": "STRING", "label": "Reference", "sortable": true},
        {"name": "regulator_code", "type": "STRING", "label": "Regulator", "sortable": true},
        {"name": "submission_type", "type": "STRING", "label": "Type", "sortable": true},
        {"name": "jurisdiction", "type": "STRING", "label": "Jurisdiction", "sortable": true},
        {"name": "filing_period_start", "type": "DATE", "label": "Period Start", "sortable": true},
        {"name": "filing_period_end", "type": "DATE", "label": "Period End", "sortable": true},
        {"name": "filing_deadline", "type": "DATE", "label": "Deadline", "sortable": true},
        {"name": "status", "type": "STRING", "label": "Status", "sortable": true},
        {"name": "regulator_reference", "type": "STRING", "label": "Regulator Ref", "sortable": true},
        {"name": "filed_at", "type": "DATETIME", "label": "Filed At", "sortable": true},
        {"name": "filed_by", "type": "STRING", "label": "Filed By", "sortable": true},
        {"name": "is_late_filing", "type": "BOOLEAN", "label": "Late Filing", "sortable": true},
        {"name": "days_until_deadline", "type": "INTEGER", "label": "Days Until Deadline", "sortable": true}
    ]'::jsonb,
    'created_at DESC',
    TRUE,
    1,
    NOW()
FROM reports r WHERE r.report_code = 'REG_001';

-- Data Quality Issues Report Definition
INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT 
    r.id, 
    1,
    'SELECT 
        dqi.id,
        dqi.issue_type,
        dqi.entity_type,
        dqi.entity_id,
        dqi.field_name,
        dqi.expected_format,
        dqi.actual_value,
        dqi.severity,
        dqi.status,
        pu.username as resolved_by,
        dqi.resolved_at,
        dqi.resolution_notes,
        dqi.created_at
    FROM data_quality_issues dqi
    LEFT JOIN platform_users pu ON dqi.resolved_by = pu.id
    WHERE dqi.psp_id = :pspId
    AND dqi.created_at BETWEEN :dateFrom AND :dateTo
    AND (:issueType IS NULL OR dqi.issue_type = :issueType)
    AND (:severity IS NULL OR dqi.severity = :severity)
    AND (:status IS NULL OR dqi.status = :status)
    ORDER BY dqi.created_at DESC',
    'SELECT COUNT(*) FROM data_quality_issues dqi WHERE dqi.psp_id = :pspId AND dqi.created_at BETWEEN :dateFrom AND :dateTo',
    '[
        {"name": "pspId", "type": "LONG", "required": true},
        {"name": "dateFrom", "type": "DATETIME", "required": true},
        {"name": "dateTo", "type": "DATETIME", "required": true},
        {"name": "issueType", "type": "STRING", "required": false},
        {"name": "severity", "type": "STRING", "required": false},
        {"name": "status", "type": "STRING", "required": false}
    ]'::jsonb,
    '[
        {"field": "issueType", "type": "ENUM", "options": ["MISSING_CUSTOMER_DATA", "INCOMPLETE_TRANSACTIONS", "INVALID_ID", "DUPLICATE_RECORDS", "INCONSISTENCIES"]},
        {"field": "severity", "type": "ENUM", "options": ["INFO", "WARNING", "ERROR", "CRITICAL"]},
        {"field": "status", "type": "ENUM", "options": ["OPEN", "RESOLVED", "IGNORED"]},
        {"field": "entityType", "type": "STRING"}
    ]'::jsonb,
    '[
        {"name": "id", "type": "LONG", "label": "ID", "sortable": true},
        {"name": "issue_type", "type": "STRING", "label": "Issue Type", "sortable": true},
        {"name": "entity_type", "type": "STRING", "label": "Entity Type", "sortable": true},
        {"name": "entity_id", "type": "STRING", "label": "Entity ID", "sortable": true},
        {"name": "field_name", "type": "STRING", "label": "Field", "sortable": true},
        {"name": "expected_format", "type": "STRING", "label": "Expected Format", "sortable": false},
        {"name": "actual_value", "type": "STRING", "label": "Actual Value", "sortable": false},
        {"name": "severity", "type": "STRING", "label": "Severity", "sortable": true},
        {"name": "status", "type": "STRING", "label": "Status", "sortable": true},
        {"name": "resolved_by", "type": "STRING", "label": "Resolved By", "sortable": true},
        {"name": "resolved_at", "type": "DATETIME", "label": "Resolved At", "sortable": true},
        {"name": "resolution_notes", "type": "STRING", "label": "Notes", "sortable": false},
        {"name": "created_at", "type": "DATETIME", "label": "Created At", "sortable": true}
    ]'::jsonb,
    'created_at DESC',
    TRUE,
    1,
    NOW()
FROM reports r WHERE r.report_code = 'DQL_001';

-- ============================================================
-- SEED REGULATORY TEMPLATES
-- ============================================================

INSERT INTO regulatory_templates (template_code, template_name, regulator_code, jurisdiction, submission_type, schema_definition, validation_rules, required_fields, is_active, created_at) VALUES
('FinCEN_SAR', 'FinCEN Suspicious Activity Report', 'FinCEN', 'US', 'SAR',
    '[
        {"name": "filing_institution", "type": "STRING", "maxLength": 150},
        {"name": "filing_date", "type": "DATE"},
        {"name": "suspicious_activity_start", "type": "DATE"},
        {"name": "suspicious_activity_end", "type": "DATE"},
        {"name": "narrative", "type": "TEXT", "maxLength": 8000},
        {"name": "total_amount", "type": "DECIMAL", "precision": 19, "scale": 2}
    ]'::jsonb,
    '[
        {"field": "total_amount", "rule": "MIN", "value": 0},
        {"field": "narrative", "rule": "MIN_LENGTH", "value": 100}
    ]'::jsonb,
    '["filing_institution", "filing_date", "narrative", "total_amount"]'::jsonb,
    TRUE,
    NOW()
),
('FinCEN_CTR', 'FinCEN Currency Transaction Report', 'FinCEN', 'US', 'CTR',
    '[
        {"name": "filing_institution", "type": "STRING", "maxLength": 150},
        {"name": "transaction_date", "type": "DATE"},
        {"name": "total_cash_amount", "type": "DECIMAL", "precision": 19, "scale": 2},
        {"name": "transaction_type", "type": "STRING", "options": ["DEPOSIT", "WITHDRAWAL", "EXCHANGE"]},
        {"name": "person_conducting", "type": "STRING", "maxLength": 200}
    ]'::jsonb,
    '[
        {"field": "total_cash_amount", "rule": "MIN", "value": 10000}
    ]'::jsonb,
    '["filing_institution", "transaction_date", "total_cash_amount"]'::jsonb,
    TRUE,
    NOW()
),
('OFAC_REPORT', 'OFAC Sanctions Screening Report', 'OFAC', 'US', 'SANCTIONS',
    '[
        {"name": "screening_date", "type": "DATE"},
        {"name": "entity_name", "type": "STRING", "maxLength": 500},
        {"name": "match_score", "type": "DECIMAL", "precision": 5, "scale": 2},
        {"name": "sanctions_list", "type": "STRING"},
        {"name": "match_type", "type": "STRING", "options": ["EXACT", "PARTIAL", "WEAK"]},
        {"name": "outcome", "type": "STRING", "options": ["TRUE_MATCH", "FALSE_POSITIVE", "PENDING"]},
        {"name": "narrative", "type": "TEXT"}
    ]'::jsonb,
    '[
        {"field": "match_score", "rule": "RANGE", "min": 0, "max": 100}
    ]'::jsonb,
    '["screening_date", "entity_name", "match_score", "outcome"]'::jsonb,
    TRUE,
    NOW()
),
('FCA_SAR', 'FCA Suspicious Activity Report (UK)', 'FCA', 'UK', 'SAR',
    '[
        {"name": "reporter_firm", "type": "STRING", "maxLength": 150},
        {"name": "report_date", "type": "DATE"},
        {"name": "reason_for_report", "type": "TEXT", "maxLength": 8000},
        {"name": "suspicion_grounds", "type": "STRING", "options": ["MONEY_LAUNDERING", "TERRORIST_FINANCING", "FRAUD", "OTHER"]},
        {"name": "amount_involved", "type": "DECIMAL", "precision": 19, "scale": 2}
    ]'::jsonb,
    '[
        {"field": "reason_for_report", "rule": "MIN_LENGTH", "value": 100}
    ]'::jsonb,
    '["reporter_firm", "report_date", "reason_for_report"]'::jsonb,
    TRUE,
    NOW()
);

-- ============================================================
-- SEED DATA QUALITY ISSUE TYPES
-- ============================================================

-- Note: Actual data quality issues will be populated by the application
-- This is just documentation of the issue types

COMMENT ON COLUMN data_quality_issues.issue_type IS 'Valid types: MISSING_CUSTOMER_DATA, INCOMPLETE_TRANSACTIONS, INVALID_ID, DUPLICATE_RECORDS, INCONSISTENCIES';
