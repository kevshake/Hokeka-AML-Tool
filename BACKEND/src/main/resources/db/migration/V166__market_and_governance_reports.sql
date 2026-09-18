INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled)
VALUES
    ('MKT_001', 'Market Abuse Signal Report', 'MARKET_SURVEILLANCE',
     'Market-integrity signals with customer, order, execution, evidence, and review outcome.',
     'DYNAMIC', 'market_surveillance_signals', FALSE, TRUE),
    ('MKT_002', 'Market Order Activity Report', 'MARKET_SURVEILLANCE',
     'Order activity with execution and market-signal aggregates.',
     'DYNAMIC', 'market_orders', FALSE, TRUE),
    ('RGOV_001', 'Rule Governance Audit Report', 'MODEL_RULE_GOVERNANCE',
     'Immutable rule versions, maker-checker decisions, effective dates, and content hashes.',
     'DYNAMIC', 'rule_versions', TRUE, TRUE)
ON CONFLICT (report_code) DO NOTHING;

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    order_by_default, is_active, created_by, created_at
)
SELECT r.id, 1,
    'SELECT s.id AS market_surveillance_signal_id,
            s.customer_id AS multi_asset_customer_id,
            s.order_id AS market_order_id,
            s.execution_id AS market_execution_id,
            c.external_customer_id, c.display_name,
            s.scenario_code, s.signal_type, s.severity, s.score,
            s.description, s.evidence, s.status,
            s.created_at, s.reviewed_at, u.username AS reviewed_by, s.review_notes
       FROM market_surveillance_signals s
       JOIN multi_asset_customers c ON c.id = s.customer_id
       LEFT JOIN platform_users u ON u.id = s.reviewed_by
      WHERE s.psp_id = :pspId
        AND s.created_at BETWEEN :dateFrom AND :dateTo
      ORDER BY s.created_at DESC',
    'SELECT COUNT(*) FROM market_surveillance_signals s
      WHERE s.psp_id = :pspId AND s.created_at BETWEEN :dateFrom AND :dateTo',
    '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
    '[{"field":"scenario_code","type":"STRING"},{"field":"severity","type":"ENUM","options":["LOW","MEDIUM","HIGH","CRITICAL"]},{"field":"status","type":"ENUM","options":["OPEN","UNDER_REVIEW","DISMISSED","ESCALATED"]}]'::jsonb,
    '[{"name":"market_surveillance_signal_id","type":"LONG","label":"Signal ID"},{"name":"external_customer_id","type":"STRING","label":"Customer ID"},{"name":"display_name","type":"STRING","label":"Customer"},{"name":"scenario_code","type":"STRING","label":"Scenario"},{"name":"severity","type":"STRING","label":"Severity"},{"name":"score","type":"INTEGER","label":"Score"},{"name":"status","type":"STRING","label":"Status"},{"name":"created_at","type":"DATETIME","label":"Created"},{"name":"reviewed_by","type":"STRING","label":"Reviewer"}]'::jsonb,
    'created_at DESC', TRUE, NULL, CURRENT_TIMESTAMP
FROM reports r WHERE r.report_code = 'MKT_001'
ON CONFLICT (report_id, version) DO NOTHING;

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    order_by_default, is_active, created_by, created_at
)
SELECT r.id, 1,
    'SELECT o.id AS market_order_id,
            o.customer_id AS multi_asset_customer_id,
            c.external_customer_id, c.display_name,
            o.external_order_id, o.account_reference, o.instrument_id, o.symbol,
            o.side, o.order_type, o.quantity, o.executed_quantity, o.limit_price,
            o.currency, o.venue, o.status, o.placed_at, o.cancelled_at,
            COUNT(DISTINCT e.id) AS execution_count,
            COALESCE(SUM(e.quantity), 0) AS total_executed_quantity,
            COUNT(DISTINCT s.id) AS signal_count,
            COALESCE(MAX(s.score), 0) AS maximum_signal_score
       FROM market_orders o
       JOIN multi_asset_customers c ON c.id = o.customer_id
       LEFT JOIN market_executions e ON e.order_id = o.id
       LEFT JOIN market_surveillance_signals s ON s.order_id = o.id
      WHERE o.psp_id = :pspId
        AND o.placed_at BETWEEN :dateFrom AND :dateTo
      GROUP BY o.id, c.external_customer_id, c.display_name
      ORDER BY o.placed_at DESC',
    'SELECT COUNT(*) FROM market_orders o
      WHERE o.psp_id = :pspId AND o.placed_at BETWEEN :dateFrom AND :dateTo',
    '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
    '[{"field":"instrument_id","type":"STRING"},{"field":"side","type":"ENUM","options":["BUY","SELL"]},{"field":"status","type":"ENUM","options":["OPEN","PARTIALLY_FILLED","FILLED","CANCELLED","REJECTED"]}]'::jsonb,
    '[{"name":"market_order_id","type":"LONG","label":"Order ID"},{"name":"external_customer_id","type":"STRING","label":"Customer ID"},{"name":"external_order_id","type":"STRING","label":"External Order"},{"name":"instrument_id","type":"STRING","label":"Instrument"},{"name":"side","type":"STRING","label":"Side"},{"name":"quantity","type":"DECIMAL","label":"Quantity"},{"name":"executed_quantity","type":"DECIMAL","label":"Executed"},{"name":"status","type":"STRING","label":"Status"},{"name":"signal_count","type":"INTEGER","label":"Signals"},{"name":"maximum_signal_score","type":"INTEGER","label":"Maximum Score"}]'::jsonb,
    'placed_at DESC', TRUE, NULL, CURRENT_TIMESTAMP
FROM reports r WHERE r.report_code = 'MKT_002'
ON CONFLICT (report_id, version) DO NOTHING;

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    order_by_default, is_active, created_by, created_at
)
SELECT r.id, 1,
    'SELECT v.id AS rule_version_id, v.rule_id AS rule_definition_id,
            d.name AS rule_name, v.version_number, v.lifecycle_status, v.change_type,
            v.content_hash, v.change_summary, v.snapshot, v.created_at,
            maker.username AS maker, v.effective_from, reviewer.username AS reviewer,
            v.reviewed_at, v.review_reason, v.activated_at
       FROM rule_versions v
       JOIN rule_definitions d ON d.id = v.rule_id
       JOIN platform_users maker ON maker.id = v.created_by
       LEFT JOIN platform_users reviewer ON reviewer.id = v.reviewed_by
      WHERE (v.psp_id = :pspId OR v.psp_id IS NULL)
        AND v.created_at BETWEEN :dateFrom AND :dateTo
      ORDER BY v.created_at DESC',
    'SELECT COUNT(*) FROM rule_versions v
      WHERE (v.psp_id = :pspId OR v.psp_id IS NULL)
        AND v.created_at BETWEEN :dateFrom AND :dateTo',
    '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
    '[{"field":"lifecycle_status","type":"ENUM","options":["PENDING_APPROVAL","APPROVED","ACTIVE","REJECTED","SUPERSEDED","RETIRED"]},{"field":"change_type","type":"ENUM","options":["CREATE","UPDATE","ENABLE","DISABLE","RETIRE","ROLLBACK"]}]'::jsonb,
    '[{"name":"rule_version_id","type":"LONG","label":"Version ID"},{"name":"rule_name","type":"STRING","label":"Rule"},{"name":"version_number","type":"INTEGER","label":"Version"},{"name":"change_type","type":"STRING","label":"Change"},{"name":"lifecycle_status","type":"STRING","label":"Status"},{"name":"maker","type":"STRING","label":"Maker"},{"name":"reviewer","type":"STRING","label":"Reviewer"},{"name":"effective_from","type":"DATETIME","label":"Effective From"},{"name":"content_hash","type":"STRING","label":"Content Hash"}]'::jsonb,
    'created_at DESC', TRUE, NULL, CURRENT_TIMESTAMP
FROM reports r WHERE r.report_code = 'RGOV_001'
ON CONFLICT (report_id, version) DO NOTHING;

UPDATE report_definitions d
SET is_active = FALSE
FROM reports r
WHERE d.report_id = r.id AND r.report_code = 'SAR_001' AND d.version < 2;

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    order_by_default, is_active, created_by, created_at
)
SELECT r.id, 2,
    'SELECT sar.id AS suspicious_activity_report_id,
            sar.sar_reference, sar.status, sar.sar_type, sar.jurisdiction,
            sar.suspicious_activity_type, sar.total_suspicious_amount,
            sar.suspicion_arose_at, sar.suspicion_timestamp_source,
            sar.filing_deadline, sar.deadline_policy_code, sar.deadline_warning_at,
            sar.deadline_breached, sar.deadline_breach_notified_at,
            sar.reviewed_at, reviewer.username AS reviewed_by, sar.review_notes,
            sar.approved_at, sar.filed_at, sar.filing_reference_number,
            creator.username AS created_by, sar.created_at, sar.updated_at,
            EXTRACT(EPOCH FROM (COALESCE(sar.filed_at, NOW()) - sar.suspicion_arose_at)) / 3600 AS hours_since_suspicion
       FROM suspicious_activity_reports sar
       LEFT JOIN platform_users creator ON creator.id = sar.created_by_user_id
       LEFT JOIN platform_users reviewer ON reviewer.id = sar.reviewed_by_user_id
      WHERE sar.psp_id = :pspId
        AND sar.created_at BETWEEN :dateFrom AND :dateTo
      ORDER BY sar.created_at DESC',
    'SELECT COUNT(*) FROM suspicious_activity_reports sar
      WHERE sar.psp_id = :pspId AND sar.created_at BETWEEN :dateFrom AND :dateTo',
    '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
    '[{"field":"status","type":"ENUM","options":["DRAFT","PENDING_REVIEW","APPROVED","FILED","REJECTED"]},{"field":"jurisdiction","type":"STRING"},{"field":"deadline_breached","type":"BOOLEAN"}]'::jsonb,
    '[{"name":"suspicious_activity_report_id","type":"LONG","label":"SAR ID"},{"name":"sar_reference","type":"STRING","label":"SAR Reference"},{"name":"status","type":"STRING","label":"Status"},{"name":"jurisdiction","type":"STRING","label":"Jurisdiction"},{"name":"suspicion_arose_at","type":"DATETIME","label":"Suspicion Arose"},{"name":"filing_deadline","type":"DATETIME","label":"Filing Deadline"},{"name":"deadline_policy_code","type":"STRING","label":"Deadline Policy"},{"name":"deadline_breached","type":"BOOLEAN","label":"Breached"},{"name":"reviewed_by","type":"STRING","label":"Reviewer"},{"name":"filed_at","type":"DATETIME","label":"Filed"},{"name":"hours_since_suspicion","type":"DECIMAL","label":"Hours Since Suspicion"}]'::jsonb,
    'created_at DESC', TRUE, NULL, CURRENT_TIMESTAMP
FROM reports r WHERE r.report_code = 'SAR_001'
ON CONFLICT (report_id, version) DO NOTHING;

