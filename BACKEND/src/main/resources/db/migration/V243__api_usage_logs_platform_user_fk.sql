-- Point api_usage_logs.user_id at platform_users (console operators), not legacy psp_users.

ALTER TABLE api_usage_logs DROP CONSTRAINT IF EXISTS api_usage_logs_user_id_fkey;

ALTER TABLE api_usage_logs
    ADD CONSTRAINT api_usage_logs_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES platform_users(id) ON DELETE SET NULL;
