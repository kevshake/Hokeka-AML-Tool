-- V212: Rewrite the three seeded rule expressions that used SpEL type references T(...) into
-- sandbox-safe forms.
--
-- SpelRuleExecutor now evaluates untrusted (operator/LLM-authored) expressions in a sandboxed
-- SimpleEvaluationContext that forbids T() / constructors / statics — closing the authenticated-RCE
-- path (T(java.lang.Runtime).getRuntime().exec(...)). Only R-2, R-CB-3 and R-CB-4 used T(); rewrite
-- them to equivalent forms (numeric comparison instead of T(BigDecimal).compareTo, and an inline SpEL
-- list {..} instead of T(java.util.Arrays).asList(..)). Matched on the exact prior expression text so
-- every per-PSP copy (seeded by V206) is updated too. Idempotent.

-- R-2: threshold amount — use doubleValue() comparison instead of T(java.math.BigDecimal).
UPDATE rule_definitions
SET rule_expression = '#tx.amount != null && #tx.amount.doubleValue() >= (#params[''threshold_amount''] ?: 1000000)'
WHERE rule_expression = '#tx.amount != null && #tx.amount.compareTo(T(java.math.BigDecimal).valueOf(#params[''threshold_amount''] ?: 1000000)) >= 0';

-- R-CB-3: high-value chargeback — same treatment.
UPDATE rule_definitions
SET rule_expression = '#features[''is_chargeback''] == true && #tx.amount != null && #tx.amount.doubleValue() >= (#params[''amount_threshold''] ?: 500)'
WHERE rule_expression = '#features[''is_chargeback''] == true && #tx.amount.compareTo(T(java.math.BigDecimal).valueOf(#params[''amount_threshold''] ?: 500)) >= 0';

-- R-CB-4: fraud reason codes — inline SpEL list instead of T(java.util.Arrays).asList(..).
UPDATE rule_definitions
SET rule_expression = '#features[''dispute_reason_category''] == ''fraud'' || {''10.4'',''10.5''}.contains(#features[''dispute_reason_code''])'
WHERE rule_expression = '#features[''dispute_reason_category''] == ''fraud'' || T(java.util.Arrays).asList(''10.4'',''10.5'').contains(#features[''dispute_reason_code''])';
