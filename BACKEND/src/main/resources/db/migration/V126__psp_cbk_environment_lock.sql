-- V126: per-PSP CBK environment selection.
--
-- Until now the CBK environment (live vs preprod) was a global setting
-- (cbk.environment + cbk.allow-live env vars). Real PSPs onboard at different
-- times — some need to point at preprod for sandbox testing while others have
-- already gone live in production. Both columns below are per-PSP; only
-- platform admins (SUPER_ADMIN / ADMIN) can mutate them via
-- PUT /psps/{id}/cbk-config — PSP_ADMIN cannot edit them on their own PSP.
--
-- Effective live behaviour requires three things ALL true:
--   1. cbk.allow-live=true       (platform-wide kill switch / ops belt)
--   2. psps.cbk_allow_live=true  (this PSP has been formally promoted)
--   3. psps.cbk_environment='live'

ALTER TABLE psps
    ADD COLUMN IF NOT EXISTS cbk_environment VARCHAR(32) NOT NULL DEFAULT 'preprod',
    ADD COLUMN IF NOT EXISTS cbk_allow_live  BOOLEAN     NOT NULL DEFAULT FALSE;

-- Anyone already onboarded gets explicit preprod for safety, even if the
-- application property has historically been `live`.
UPDATE psps
   SET cbk_environment = 'preprod',
       cbk_allow_live  = FALSE
 WHERE cbk_environment IS NULL
    OR cbk_allow_live IS NULL;
