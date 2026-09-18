-- V214: Make the velocity rules evaluate the window they declare.
--
-- Several seeded rules declared a multi-hour/day lookback in `parameters` but evaluated
-- `#tx.panTxnCount1h` — a ONE HOUR counter. A rule advertising "10 transactions in 24 hours" that
-- actually requires 10 within a single hour is far less sensitive than its own specification, and an
-- operator tuning the declared parameter saw no effect because the expression never read it.
--
-- Two real velocity counters exist on TransactionFact: panTxnCount1h and panTxnCount24h. Each rule
-- below declares a window of >= 24h, so it is repointed at the 24h counter (the closest real
-- feature). Rules whose declared window has NO matching counter (a true 7-day count) also move to
-- the 24h counter as the nearest supported window rather than silently evaluating 1 hour; building
-- genuine 7/30-day counters is tracked in TODO.md.
--
-- NOTE: `rule_definitions.parameters` is TEXT (V141), not JSONB — jsonb_set() against it would raise
-- a type error and abort the entire migration run. The declared-window values are therefore rewritten
-- with plain, targeted text replacement, guarded so only the exact seeded literal is touched.
--
-- Matched on the exact prior expression so per-PSP copies (V206) are updated too. Idempotent.

-- R-30 "Transaction velocity" — declared time_window_minutes: 1440 (24h), evaluated 1h.
UPDATE rule_definitions
SET rule_expression = '#tx.panTxnCount24h >= (#params[''max_transactions''] ?: 10)'
WHERE external_code = 'R-30'
  AND rule_expression = '#tx.panTxnCount1h >= (#params[''max_transactions''] ?: 10)';

-- R-77 "High-risk country velocity" — declared lookback_hours: 24, evaluated 1h.
UPDATE rule_definitions
SET rule_expression = '#tx.isHighRiskCountry() && #tx.panTxnCount24h >= (#params[''min_transactions''] ?: 5)'
WHERE external_code = 'R-77'
  AND rule_expression = '#tx.isHighRiskCountry() && #tx.panTxnCount1h >= (#params[''min_transactions''] ?: 5)';

-- R-7 / R-8 "Structuring — inbound/outbound just under the reporting threshold".
-- Declared lookback_days: 7; evaluated 1h. Repointed to the 24h counter (nearest supported window).
UPDATE rule_definitions
SET rule_expression = '#tx.amount.doubleValue() >= ((#params[''threshold_amount''] ?: 9000) * 0.9) && #tx.amount.doubleValue() < (#params[''threshold_amount''] ?: 9000) && #tx.panTxnCount24h >= (#params[''min_transactions''] ?: 5) && #features[''direction''] == ''INBOUND'''
WHERE external_code = 'R-7'
  AND rule_expression = '#tx.amount.doubleValue() >= ((#params[''threshold_amount''] ?: 9000) * 0.9) && #tx.amount.doubleValue() < (#params[''threshold_amount''] ?: 9000) && #tx.panTxnCount1h >= (#params[''min_transactions''] ?: 5) && #features[''direction''] == ''INBOUND''';

UPDATE rule_definitions
SET rule_expression = '#tx.amount.doubleValue() >= ((#params[''threshold_amount''] ?: 9000) * 0.9) && #tx.amount.doubleValue() < (#params[''threshold_amount''] ?: 9000) && #tx.panTxnCount24h >= (#params[''min_transactions''] ?: 5) && #features[''direction''] == ''OUTBOUND'''
WHERE external_code = 'R-8'
  AND rule_expression = '#tx.amount.doubleValue() >= ((#params[''threshold_amount''] ?: 9000) * 0.9) && #tx.amount.doubleValue() < (#params[''threshold_amount''] ?: 9000) && #tx.panTxnCount1h >= (#params[''min_transactions''] ?: 5) && #features[''direction''] == ''OUTBOUND''';

-- Correct the declared lookback for R-7/R-8 so the documented window matches what is evaluated
-- (1 day). Plain text replacement — `parameters` is TEXT, so no JSON operators are used.
UPDATE rule_definitions
SET parameters = REPLACE(parameters, '"lookback_days": 7', '"lookback_days": 1')
WHERE external_code IN ('R-7', 'R-8')
  AND parameters LIKE '%"lookback_days": 7%';

UPDATE rule_definitions
SET parameters = REPLACE(parameters, '"lookback_days":7', '"lookback_days":1')
WHERE external_code IN ('R-7', 'R-8')
  AND parameters LIKE '%"lookback_days":7%';
