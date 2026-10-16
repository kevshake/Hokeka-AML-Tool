-- ============================================================================
-- V253: Neutralise the demo accounts shipped by V127 (known public password).
--
-- V127 inserted eight login-ready PSP/platform demo users into every database,
-- including production, all with the same BCrypt hash of a password that is
-- published in the repository. V127 may already be applied on deployed
-- databases, so it is left untouched (editing/removing an applied migration
-- breaks Flyway validation); this migration makes its rows harmless instead.
--
-- Only rows whose hash is still the exact V127 literal are changed, i.e.
-- accounts whose password was never rotated. Rotated accounts are kept as-is.
-- Changed rows are:
--   * enabled = false            -> login refused (UserDetails.isEnabled)
--   * password_hash = sentinel   -> not a BCrypt hash, so no password matches
--   * locked_until = 'infinity'  -> explicit lock marker for operators
--   * password_reset_token/expires cleared
-- An administrator re-enables an account and issues a password reset to use it.
--
-- Dev/test: DemoAccountSeeder (dev/test/local profiles only) re-enables these
-- users with the dev demo password on boot. Other accounts that the old
-- boot-time seeder created with salted hashes of the same password are caught
-- at runtime by DefaultCredentialGuard (all non-dev profiles).
-- ============================================================================
UPDATE platform_users
SET enabled                = false,
    password_hash          = '!DISABLED-DEFAULT-CREDENTIAL',
    locked_until           = 'infinity',
    password_reset_token   = NULL,
    password_reset_expires = NULL
WHERE password_hash = '$2a$10$.RGw2iljgrW7KfQzbWsRV.T7uY8y.RFwf0lIoc0bPqAy6LC3QxvVa'
  AND username IN ('super.admin', 'platform.admin',
                   'velocity.admin', 'velocity.compliance', 'velocity.user',
                   'mwananchi.admin', 'mwananchi.compliance', 'apex.admin');
