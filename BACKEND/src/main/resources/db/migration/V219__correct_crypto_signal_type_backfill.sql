-- V219__correct_crypto_signal_type_backfill.sql
-- Purpose (W18-5): V161's one-time signal_type backfill only bucketed
--          CRYPTO_HIGH_RISK_%, CRYPTO_ELEVATED_%, and CRYPTO_CROSS_CHAIN_TRANSFER as
--          CRYPTO_EXPOSURE; three other crypto signal codes --
--          CRYPTO_SCREENING_UNAVAILABLE, CRYPTO_FIAT_VALUE_MISSING, and
--          CRYPTO_TRAVEL_RULE_INCOMPLETE -- fell through to the ELSE branch and were
--          backfilled as AML instead. At runtime, MultiAssetRiskEngine.signalTypeFor(code)
--          maps EVERY code with a "CRYPTO_" prefix to CRYPTO_EXPOSURE, including these three --
--          so historical rows for these three codes are inconsistent with what identical new
--          rows get today. Never edit V161 (already applied) -- corrects the mis-bucketed rows
--          forward instead.
-- Tables affected: multi_asset_risk_signals
-- Audited entities: no
-- Idempotent: the UPDATE only touches rows that are still wrong (signal_type = 'AML' for one of
--             these three codes); re-running finds nothing left to fix.

UPDATE multi_asset_risk_signals
SET signal_type = 'CRYPTO_EXPOSURE'
WHERE signal_type = 'AML'
  AND signal_code IN ('CRYPTO_SCREENING_UNAVAILABLE', 'CRYPTO_FIAT_VALUE_MISSING',
                       'CRYPTO_TRAVEL_RULE_INCOMPLETE');
