-- Per-PSP KYC/KYB toggle.
--
-- When true (default), merchants under the PSP go through full KYC/KYB screening +
-- underwriting at onboarding. When false, an admin has explicitly waived KYC for the
-- PSP: its merchants onboard as ACTIVE with kyc_status = 'NOT_REQUIRED' and transactions
-- flow without KYC gating. Existing PSPs default to KYC enabled (safe).

ALTER TABLE psps
    ADD COLUMN IF NOT EXISTS kyc_enabled BOOLEAN NOT NULL DEFAULT TRUE;
