INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled)
VALUES
    ('MM_001', 'Mobile Money Risk Signal Report', 'MOBILE_MONEY',
     'Typed mobile-money AML and fraud signals with customer, transaction, and evidence context.',
     'DYNAMIC', 'multi_asset_risk_signals', FALSE, TRUE),
    ('MM_002', 'Mobile Money Entity Risk Report', 'MOBILE_MONEY',
     'Current customer, wallet, device, SIM, agent, merchant, and network-cluster risk profiles.',
     'DYNAMIC', 'mobile_money_risk_profiles', FALSE, TRUE),
    ('MM_003', 'Mobile Money Network Activity Report', 'MOBILE_MONEY',
     'Mobile-money transactions with device, agent, merchant, location, network, and signal aggregates.',
     'DYNAMIC', 'mobile_money_transaction_contexts', FALSE, TRUE)
ON CONFLICT (report_code) DO NOTHING;

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    order_by_default, is_active, created_by, created_at
)
SELECT r.id, 1,
    'SELECT s.id AS multi_asset_risk_signal_id,
            s.transaction_id AS multi_asset_transaction_id,
            s.customer_id AS multi_asset_customer_id,
            c.external_customer_id, c.display_name,
            t.external_transaction_id, t.amount, t.currency, t.executed_at,
            ctx.id AS mobile_money_transaction_context_id,
            ctx.wallet_reference, ctx.agent_reference, ctx.merchant_reference,
            s.signal_code, s.signal_type, s.severity, s.score_impact,
            s.description, s.evidence, s.created_at
       FROM multi_asset_risk_signals s
       JOIN multi_asset_transactions t ON t.id = s.transaction_id
       JOIN multi_asset_customers c ON c.id = s.customer_id
       LEFT JOIN mobile_money_transaction_contexts ctx ON ctx.transaction_id = t.id
      WHERE s.psp_id = :pspId
        AND s.product_domain = ''E_MONEY_MOBILE_MONEY''
        AND s.signal_code LIKE ''MOBILE_%''
        AND s.created_at BETWEEN :dateFrom AND :dateTo
      ORDER BY s.created_at DESC',
    'SELECT COUNT(*) FROM multi_asset_risk_signals s
      WHERE s.psp_id = :pspId
        AND s.product_domain = ''E_MONEY_MOBILE_MONEY''
        AND s.signal_code LIKE ''MOBILE_%''
        AND s.created_at BETWEEN :dateFrom AND :dateTo',
    '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
    '[{"field":"signal_type","type":"ENUM","options":["AML","FRAUD","CYBER"]},{"field":"severity","type":"ENUM","options":["LOW","MEDIUM","HIGH","CRITICAL"]},{"field":"signal_code","type":"STRING"}]'::jsonb,
    '[{"name":"multi_asset_risk_signal_id","type":"LONG","label":"Signal ID"},{"name":"external_customer_id","type":"STRING","label":"Customer ID"},{"name":"external_transaction_id","type":"STRING","label":"Transaction"},{"name":"wallet_reference","type":"STRING","label":"Wallet"},{"name":"signal_code","type":"STRING","label":"Scenario"},{"name":"signal_type","type":"STRING","label":"Discipline"},{"name":"severity","type":"STRING","label":"Severity"},{"name":"score_impact","type":"INTEGER","label":"Score Impact"},{"name":"created_at","type":"DATETIME","label":"Created"}]'::jsonb,
    'created_at DESC', TRUE, NULL, CURRENT_TIMESTAMP
FROM reports r WHERE r.report_code = 'MM_001'
ON CONFLICT (report_id, version) DO NOTHING;

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    order_by_default, is_active, created_by, created_at
)
SELECT r.id, 1,
    'SELECT p.id AS mobile_money_risk_profile_id,
            p.entity_type, p.entity_reference, p.risk_score, p.risk_level,
            p.signal_count, p.first_seen_at, p.last_seen_at, p.last_assessed_at,
            p.latest_transaction_id AS multi_asset_transaction_id,
            p.attributes,
            COUNT(DISTINCT e.id) AS relationship_count,
            COALESCE(SUM(e.occurrence_count), 0) AS related_occurrences
       FROM mobile_money_risk_profiles p
       LEFT JOIN mobile_money_network_edges e
         ON e.psp_id = p.psp_id
        AND ((e.from_entity_type = p.entity_type AND e.from_entity_reference = p.entity_reference)
          OR (e.to_entity_type = p.entity_type AND e.to_entity_reference = p.entity_reference))
      WHERE p.psp_id = :pspId
        AND p.last_assessed_at BETWEEN :dateFrom AND :dateTo
      GROUP BY p.id
      ORDER BY p.risk_score DESC, p.last_assessed_at DESC',
    'SELECT COUNT(*) FROM mobile_money_risk_profiles p
      WHERE p.psp_id = :pspId AND p.last_assessed_at BETWEEN :dateFrom AND :dateTo',
    '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
    '[{"field":"entity_type","type":"ENUM","options":["CUSTOMER","WALLET","DEVICE","SIM","AGENT","MERCHANT","NETWORK_CLUSTER"]},{"field":"risk_level","type":"ENUM","options":["LOW","MEDIUM","HIGH","CRITICAL"]}]'::jsonb,
    '[{"name":"mobile_money_risk_profile_id","type":"LONG","label":"Profile ID"},{"name":"entity_type","type":"STRING","label":"Entity Type"},{"name":"entity_reference","type":"STRING","label":"Entity"},{"name":"risk_score","type":"INTEGER","label":"Risk Score"},{"name":"risk_level","type":"STRING","label":"Risk Level"},{"name":"signal_count","type":"INTEGER","label":"Signals"},{"name":"relationship_count","type":"INTEGER","label":"Relationships"},{"name":"last_assessed_at","type":"DATETIME","label":"Last Assessed"}]'::jsonb,
    'risk_score DESC, last_assessed_at DESC', TRUE, NULL, CURRENT_TIMESTAMP
FROM reports r WHERE r.report_code = 'MM_002'
ON CONFLICT (report_id, version) DO NOTHING;

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    order_by_default, is_active, created_by, created_at
)
SELECT r.id, 1,
    'SELECT ctx.id AS mobile_money_transaction_context_id,
            t.id AS multi_asset_transaction_id,
            t.customer_id AS multi_asset_customer_id,
            c.external_customer_id, c.display_name,
            t.external_transaction_id, ctx.event_type,
            ctx.wallet_reference, ctx.counterparty_wallet_reference,
            ctx.device_fingerprint, ctx.agent_reference, ctx.merchant_reference,
            ctx.cell_id, ctx.cross_border, ctx.latitude, ctx.longitude,
            ctx.agent_float_before, ctx.agent_float_after,
            t.amount, t.fiat_equivalent_usd, t.currency, t.risk_score, t.decision,
            t.executed_at,
            COALESCE(signal_totals.signal_count, 0) AS signal_count,
            COALESCE(signal_totals.signal_score_total, 0) AS signal_score_total,
            1
              + CASE WHEN ctx.device_fingerprint IS NULL THEN 0 ELSE 2 END
              + CASE WHEN ctx.sim_identifier_hash IS NULL THEN 0 ELSE 2 END
              + CASE WHEN ctx.agent_reference IS NULL THEN 0 ELSE 1 END
              + CASE WHEN ctx.agent_reference IS NULL OR ctx.agent_device_fingerprint IS NULL THEN 0 ELSE 1 END
              + CASE WHEN ctx.merchant_reference IS NULL THEN 0 ELSE 1 END
              + CASE WHEN ctx.counterparty_wallet_reference IS NULL THEN 0 ELSE 1 END
              AS network_relationship_count
       FROM mobile_money_transaction_contexts ctx
       JOIN multi_asset_transactions t ON t.id = ctx.transaction_id
       JOIN multi_asset_customers c ON c.id = t.customer_id
       LEFT JOIN LATERAL (
           SELECT COUNT(*) AS signal_count, SUM(s.score_impact) AS signal_score_total
             FROM multi_asset_risk_signals s
            WHERE s.transaction_id = t.id
       ) signal_totals ON TRUE
      WHERE ctx.psp_id = :pspId
        AND ctx.executed_at BETWEEN :dateFrom AND :dateTo
      ORDER BY ctx.executed_at DESC',
    'SELECT COUNT(*) FROM mobile_money_transaction_contexts ctx
      WHERE ctx.psp_id = :pspId AND ctx.executed_at BETWEEN :dateFrom AND :dateTo',
    '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
    '[{"field":"event_type","type":"ENUM","options":["CASH_IN","CASH_OUT","P2P_TRANSFER","MERCHANT_PAYMENT","BILL_PAYMENT","REFUND","REVERSAL"]},{"field":"decision","type":"ENUM","options":["ALLOW","ALERT","REVIEW","BLOCK"]},{"field":"cross_border","type":"BOOLEAN"}]'::jsonb,
    '[{"name":"mobile_money_transaction_context_id","type":"LONG","label":"Context ID"},{"name":"external_transaction_id","type":"STRING","label":"Transaction"},{"name":"external_customer_id","type":"STRING","label":"Customer ID"},{"name":"event_type","type":"STRING","label":"Event"},{"name":"wallet_reference","type":"STRING","label":"Wallet"},{"name":"agent_reference","type":"STRING","label":"Agent"},{"name":"merchant_reference","type":"STRING","label":"Merchant"},{"name":"amount","type":"DECIMAL","label":"Amount"},{"name":"currency","type":"STRING","label":"Currency"},{"name":"risk_score","type":"INTEGER","label":"Risk Score"},{"name":"decision","type":"STRING","label":"Decision"},{"name":"signal_count","type":"INTEGER","label":"Signals"},{"name":"network_relationship_count","type":"INTEGER","label":"Network Links"},{"name":"executed_at","type":"DATETIME","label":"Executed"}]'::jsonb,
    'executed_at DESC', TRUE, NULL, CURRENT_TIMESTAMP
FROM reports r WHERE r.report_code = 'MM_003'
ON CONFLICT (report_id, version) DO NOTHING;
