-- V213: Make R-6 evaluate what it is named for — a high-risk CURRENCY, not a high-risk country.
--
-- V143 set R-6 ("High-Risk Currency Transaction", parameters {"high_risk_currencies": [...]}) to
-- '#tx.isHighRiskCountry()' — byte-identical to R-14 ("High-Risk Country Transaction"). So R-6 was a
-- duplicate of R-14, its declared control was never implemented, and its own parameter was never read.
-- Two rules firing on the same condition also double-counts score_impact now that the summed score
-- reaches the decision.
--
-- The rule's own name + description + parameter are the specification, so evaluate the transaction
-- currency against the configured list. Null-guarded: a PSP that has not populated
-- high_risk_currencies simply never trips this rule (it stays inert rather than mis-firing).
-- Matched on the exact prior expression AND external_code so R-14 (same expression, different code)
-- is untouched, and every per-PSP copy from V206 is updated. Idempotent.

UPDATE rule_definitions
SET rule_expression = '#params[''high_risk_currencies''] != null && #params[''high_risk_currencies''].contains(#tx.currency)'
WHERE external_code = 'R-6'
  AND rule_expression = '#tx.isHighRiskCountry()';
