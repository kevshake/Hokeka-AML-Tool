-- V143: Populate SpEL expressions for seeded system rules and enable production-ready catalog.
-- Expressions use #tx (TransactionFact), #features (velocity/screening context), #params (rule JSON tunables).

UPDATE rule_definitions SET rule_expression = '#features[''is_first_transaction''] == true', enabled = TRUE WHERE external_code = 'R-1';
UPDATE rule_definitions SET rule_expression = '#tx.amount != null && #tx.amount.compareTo(T(java.math.BigDecimal).valueOf(#params[''threshold_amount''] ?: 1000000)) >= 0', enabled = TRUE WHERE external_code = 'R-2';
UPDATE rule_definitions SET rule_expression = '#features[''is_new_country''] == true', enabled = TRUE WHERE external_code = 'R-3';
UPDATE rule_definitions SET rule_expression = '#features[''is_new_currency''] == true', enabled = TRUE WHERE external_code = 'R-4';
UPDATE rule_definitions SET rule_expression = '#features[''days_since_last_activity''] != null && #features[''days_since_last_activity''] >= (#params[''inactivity_days''] ?: 180)', enabled = TRUE WHERE external_code = 'R-5';
UPDATE rule_definitions SET rule_expression = '#tx.isHighRiskCountry()', enabled = TRUE WHERE external_code = 'R-6';
UPDATE rule_definitions SET rule_expression = '#tx.amount.doubleValue() >= ((#params[''threshold_amount''] ?: 9000) * 0.9) && #tx.amount.doubleValue() < (#params[''threshold_amount''] ?: 9000) && #tx.panTxnCount1h >= (#params[''min_transactions''] ?: 5) && #features[''direction''] == ''INBOUND''', enabled = TRUE WHERE external_code = 'R-7';
UPDATE rule_definitions SET rule_expression = '#tx.amount.doubleValue() >= ((#params[''threshold_amount''] ?: 9000) * 0.9) && #tx.amount.doubleValue() < (#params[''threshold_amount''] ?: 9000) && #tx.panTxnCount1h >= (#params[''min_transactions''] ?: 5) && #features[''direction''] == ''OUTBOUND''', enabled = TRUE WHERE external_code = 'R-8';
UPDATE rule_definitions SET rule_expression = '#features[''unique_senders''] != null && #features[''unique_senders''] >= (#params[''min_unique_senders''] ?: 10)', enabled = TRUE WHERE external_code = 'R-9';
UPDATE rule_definitions SET rule_expression = '#features[''unique_counterparties''] != null && #features[''unique_counterparties''] >= (#params[''min_unique_counterparties''] ?: 10)', enabled = TRUE WHERE external_code = 'R-10';
UPDATE rule_definitions SET rule_expression = '#features[''volume_deviation_ratio''] != null && #features[''volume_deviation_ratio''] >= (#params[''deviation_multiplier''] ?: 5)', enabled = TRUE WHERE external_code = 'R-11';
UPDATE rule_definitions SET rule_expression = '#features[''txn_count_deviation_ratio''] != null && #features[''txn_count_deviation_ratio''] >= (#params[''deviation_multiplier''] ?: 10)', enabled = TRUE WHERE external_code = 'R-12';
UPDATE rule_definitions SET rule_expression = '#features[''wallet_blacklist_hit''] == true', enabled = TRUE WHERE external_code = 'R-13';
UPDATE rule_definitions SET rule_expression = '#tx.isHighRiskCountry()', enabled = TRUE WHERE external_code = 'R-14';
UPDATE rule_definitions SET rule_expression = '#features[''distinct_banks''] != null && #features[''distinct_banks''] >= (#params[''min_distinct_banks''] ?: 4)', enabled = TRUE WHERE external_code = 'R-15';
UPDATE rule_definitions SET rule_expression = '#features[''sanctions_hit''] == true || #features[''pep_hit''] == true || #features[''adverse_media_hit''] == true', enabled = TRUE WHERE external_code = 'R-17';
UPDATE rule_definitions SET rule_expression = '#features[''onboarding_high_risk_country''] == true', enabled = TRUE WHERE external_code = 'R-20';
UPDATE rule_definitions SET rule_expression = '#features[''card_issuer_country_blacklist''] == true', enabled = TRUE WHERE external_code = 'R-22';
UPDATE rule_definitions SET rule_expression = '#features[''reference_keyword_blacklist''] == true', enabled = TRUE WHERE external_code = 'R-24';
UPDATE rule_definitions SET rule_expression = '#features[''avg_amount_spike_ratio''] != null && #features[''avg_amount_spike_ratio''] >= (#params[''spike_multiplier''] ?: 100)', enabled = TRUE WHERE external_code = 'R-25';
UPDATE rule_definitions SET rule_expression = '#features[''round_value_share_pct''] != null && #features[''round_value_share_pct''] >= (#params[''min_round_share_pct''] ?: 60)', enabled = TRUE WHERE external_code = 'R-26';
UPDATE rule_definitions SET rule_expression = '#features[''volume_ratio_t1_t2''] != null && #features[''volume_ratio_t1_t2''] >= (#params[''ratio_x''] ?: 5)', enabled = TRUE WHERE external_code = 'R-27';
UPDATE rule_definitions SET rule_expression = '#tx.panTxnCount1h >= (#params[''max_transactions''] ?: 10)', enabled = TRUE WHERE external_code = 'R-30';
UPDATE rule_definitions SET rule_expression = '#features[''bank_name_screening_hit''] == true', enabled = TRUE WHERE external_code = 'R-32';
UPDATE rule_definitions SET rule_expression = '#features[''days_since_last_activity''] != null && #features[''days_since_last_activity''] >= (#params[''inactivity_days''] ?: 365)', enabled = TRUE WHERE external_code = 'R-33';
UPDATE rule_definitions SET rule_expression = '#features[''send_receive_ratio''] != null && #features[''send_receive_ratio''] >= (#params[''send_to_receive_ratio_min''] ?: 5)', enabled = TRUE WHERE external_code = 'R-41';
UPDATE rule_definitions SET rule_expression = '#features[''distinct_account_holder_names''] != null && #features[''distinct_account_holder_names''] > (#params[''max_distinct_names''] ?: 1)', enabled = TRUE WHERE external_code = 'R-45';
UPDATE rule_definitions SET rule_expression = '#features[''distinct_users_per_ip''] != null && #features[''distinct_users_per_ip''] >= (#params[''max_distinct_users_per_ip''] ?: 5)', enabled = TRUE WHERE external_code = 'R-52';
UPDATE rule_definitions SET rule_expression = '#features[''distinct_users_per_payment_id''] != null && #features[''distinct_users_per_payment_id''] >= (#params[''max_distinct_users_per_identifier''] ?: 5)', enabled = TRUE WHERE external_code = 'R-53';
UPDATE rule_definitions SET rule_expression = '#features[''distinct_payment_ids''] != null && #features[''distinct_payment_ids''] >= (#params[''max_distinct_identifiers''] ?: 5)', enabled = TRUE WHERE external_code = 'R-55';
UPDATE rule_definitions SET rule_expression = '#features[''address_changes''] != null && #features[''address_changes''] > (#params[''max_changes''] ?: 1)', enabled = TRUE WHERE external_code = 'R-61';
UPDATE rule_definitions SET rule_expression = '#features[''spend_receive_spike_ratio''] != null && #features[''spend_receive_spike_ratio''] >= (#params[''spike_multiplier''] ?: 10)', enabled = TRUE WHERE external_code = 'R-69';
UPDATE rule_definitions SET rule_expression = '#tx.isHighRiskCountry() && #tx.panTxnCount1h >= (#params[''min_transactions''] ?: 5)', enabled = TRUE WHERE external_code = 'R-77';
UPDATE rule_definitions SET rule_expression = '#features[''ip_high_risk_country''] == true', enabled = TRUE WHERE external_code = 'R-87';
UPDATE rule_definitions SET rule_expression = '#features[''ip_outside_expected_location''] == true', enabled = TRUE WHERE external_code = 'R-88';
UPDATE rule_definitions SET rule_expression = '#features[''payment_id_use_count''] != null && #features[''payment_id_use_count''] >= (#params[''max_uses''] ?: 5)', enabled = TRUE WHERE external_code = 'R-94';
UPDATE rule_definitions SET rule_expression = '#features[''ip_change_count''] != null && #features[''ip_change_count''] > (#params[''max_ip_changes''] ?: 5)', enabled = TRUE WHERE external_code = 'R-113';
UPDATE rule_definitions SET rule_expression = '#features[''amount_ending_pattern''] == true', enabled = TRUE WHERE external_code = 'R-117';
UPDATE rule_definitions SET rule_expression = '#features[''account_name_levenshtein_exceeded''] == true', enabled = TRUE WHERE external_code = 'R-118';
UPDATE rule_definitions SET rule_expression = '#features[''circular_trading_count''] != null && #features[''circular_trading_count''] >= (#params[''min_transactions''] ?: 5)', enabled = TRUE WHERE external_code = 'R-119';
UPDATE rule_definitions SET rule_expression = '#features[''avg_amount_window_ratio''] != null && #features[''avg_amount_window_ratio''] >= (#params[''ratio_x''] ?: 5)', enabled = TRUE WHERE external_code = 'R-120';
UPDATE rule_definitions SET rule_expression = '#features[''daily_txn_count_ratio''] != null && #features[''daily_txn_count_ratio''] >= (#params[''ratio_x''] ?: 3)', enabled = TRUE WHERE external_code = 'R-121';
UPDATE rule_definitions SET rule_expression = '#features[''daily_avg_amount_ratio''] != null && #features[''daily_avg_amount_ratio''] >= (#params[''ratio_x''] ?: 5)', enabled = TRUE WHERE external_code = 'R-122';
UPDATE rule_definitions SET rule_expression = '#features[''distinct_countries''] != null && #features[''distinct_countries''] > (#params[''max_distinct_countries''] ?: 5)', enabled = TRUE WHERE external_code = 'R-123';
UPDATE rule_definitions SET rule_expression = '#features[''round_value_share_pct''] != null && #features[''round_value_share_pct''] >= (#params[''min_round_share_pct''] ?: 50)', enabled = TRUE WHERE external_code = 'R-124';
UPDATE rule_definitions SET rule_expression = '#features[''refund_share_pct''] != null && #features[''refund_share_pct''] >= (#params[''min_share_pct''] ?: 30)', enabled = TRUE WHERE external_code = 'R-125';
UPDATE rule_definitions SET rule_expression = '#features[''same_parties_volume''] != null && #features[''same_parties_volume''] >= (#params[''min_amount''] ?: 10000)', enabled = TRUE WHERE external_code = 'R-126';
UPDATE rule_definitions SET rule_expression = '#features[''entity_screening_hit''] == true', enabled = TRUE WHERE external_code = 'R-128';
UPDATE rule_definitions SET rule_expression = '#features[''payment_details_blacklist''] == true', enabled = TRUE WHERE external_code = 'R-129';
UPDATE rule_definitions SET rule_expression = '#features[''round_value_txn_count''] != null && #features[''round_value_txn_count''] >= (#params[''min_round_transactions''] ?: 10)', enabled = TRUE WHERE external_code = 'R-130';
UPDATE rule_definitions SET rule_expression = '#features[''txn_count_window_ratio''] != null && #features[''txn_count_window_ratio''] >= (#params[''ratio_x''] ?: 10)', enabled = TRUE WHERE external_code = 'R-131';
UPDATE rule_definitions SET rule_expression = '#features[''variable_blacklist_hit''] == true', enabled = TRUE WHERE external_code = 'R-132';
UPDATE rule_definitions SET rule_expression = '#features[''bank_name_changes''] != null && #features[''bank_name_changes''] > (#params[''max_bank_changes''] ?: 2)', enabled = TRUE WHERE external_code = 'R-155';
UPDATE rule_definitions SET rule_expression = '#features[''counterparty_screening_hit''] == true', enabled = TRUE WHERE external_code = 'R-169';
UPDATE rule_definitions SET rule_expression = '#features[''anonymous_payment_screening_hit''] == true', enabled = TRUE WHERE external_code = 'R-170';

-- Core rules that work with TransactionFact alone (no feature enrichment required)
UPDATE rule_definitions SET enabled = TRUE WHERE external_code IN ('R-2', 'R-7', 'R-8', 'R-14', 'R-30', 'R-77') AND rule_expression IS NOT NULL AND rule_expression <> '';

-- Chargeback / dispute pattern rules (new catalog entries)
INSERT INTO rule_definitions (
    name, description, rule_type, rule_expression, score_impact, action_type,
    priority, enabled, created_at, updated_at, psp_id, created_by,
    is_system_managed, category, rule_subtype, applies_to, typology,
    checks_for, external_code, recommended, sample_use_case, parameters
) VALUES
('Merchant Chargeback Ratio Exceeded',
 'Flags merchants whose rolling chargeback-to-transaction ratio exceeds the configured threshold.',
 'SPEL',
 '#features[''merchant_chargeback_ratio''] != null && #features[''merchant_chargeback_ratio''] > (#params[''ratio_threshold''] ?: 0.01)',
 70, 'ALERT', 15, TRUE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Chargeback monitoring', 'Transaction', 'Acquiring fraud, Card fraud',
 'Chargeback ratio, Transaction count', 'R-CB-1', TRUE,
 'A merchant with 10,000 monthly transactions receives 150 chargebacks (1.5% ratio), exceeding the 1% threshold.',
 '{"ratio_threshold": 0.01, "lookback_days": 30}'::jsonb),

('Chargeback Velocity Spike',
 'Flags when chargeback count in the lookback window exceeds the configured threshold.',
 'SPEL',
 '#features[''merchant_chargeback_count_30d''] != null && #features[''merchant_chargeback_count_30d''] >= (#params[''count_threshold''] ?: 5)',
 65, 'ALERT', 20, TRUE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Velocity', 'Transaction', 'Acquiring fraud',
 'Chargeback count, Time', 'R-CB-2', TRUE,
 'A merchant receives 8 chargebacks in 7 days after typically seeing fewer than 2 per month.',
 '{"count_threshold": 5, "lookback_days": 7}'::jsonb),

('High-Value Chargeback',
 'Holds transactions linked to high-value chargeback or pre-dispute notifications.',
 'SPEL',
 '#features[''is_chargeback''] == true && #tx.amount.compareTo(T(java.math.BigDecimal).valueOf(#params[''amount_threshold''] ?: 500)) >= 0',
 75, 'HOLD', 10, TRUE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Chargeback monitoring', 'Transaction', 'Acquiring fraud',
 'Transaction amount, Chargeback flag', 'R-CB-3', TRUE,
 'A $2,500 chargeback notification arrives for a subscription merchant.',
 '{"amount_threshold": 500, "currency": "USD"}'::jsonb),

('Fraud-Category Dispute Alert',
 'Alerts on Visa fraud-category dispute reason codes (e.g. 10.4 card-absent fraud).',
 'SPEL',
 '#features[''dispute_reason_category''] == ''fraud'' || T(java.util.Arrays).asList(''10.4'',''10.5'').contains(#features[''dispute_reason_code''])',
 80, 'HOLD', 5, TRUE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Dispute prevention', 'Transaction', 'Card fraud, Friendly fraud',
 'Dispute reason code, Dispute category', 'R-CB-4', TRUE,
 'An RDR pre-dispute arrives with Visa reason code 10.4 (card-absent fraud).',
 '{"fraud_reason_codes": ["10.4", "10.5"]}'::jsonb),

('RDR Prevention Match',
 'Alerts when a Verifi RDR rule auto-accepted liability (prevention/refund applied).',
 'SPEL',
 '#features[''rdr_prevention_match''] == true || #features[''rdr_status''] == ''accepted''',
 60, 'ALERT', 25, TRUE, NOW(), NOW(), NULL, 1,
 TRUE, 'FRAUD', 'Dispute prevention', 'Transaction', 'Acquiring fraud',
 'RDR status, Refund status', 'R-CB-5', TRUE,
 'Verifi RDR accepts liability and refunds the cardholder before chargeback initiation.',
 '{"notify_on_accepted": true, "notify_on_declined": false}'::jsonb)

ON CONFLICT (external_code) WHERE external_code IS NOT NULL DO NOTHING;
