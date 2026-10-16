-- ============================================================================
-- V256: Expiry for PSP machine API keys (hka_...).
--
-- PSP API keys now authenticate machine ingest calls (PspApiKeyAuthenticationFilter).
-- A key is accepted only while revoked_at IS NULL and (expires_at IS NULL OR
-- expires_at > now). New keys get expires_at = created_at + hokeka.api-keys.
-- default-ttl-days when that setting is > 0; existing keys keep no expiry.
-- ============================================================================
ALTER TABLE psp_api_keys ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

COMMENT ON COLUMN psp_api_keys.expires_at IS
    'Key is rejected at/after this instant; NULL = no expiry (revocation still applies)';
