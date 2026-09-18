ALTER TABLE merchant_documents
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS file_size BIGINT,
    ADD COLUMN IF NOT EXISTS sha256_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS verified_by VARCHAR(200),
    ADD COLUMN IF NOT EXISTS verification_notes TEXT,
    ADD COLUMN IF NOT EXISTS verification_method VARCHAR(50),
    ADD COLUMN IF NOT EXISTS malware_scan_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS malware_scan_engine VARCHAR(100),
    ADD COLUMN IF NOT EXISTS malware_threat_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS malware_scanned_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_merchant_documents_expiry
    ON merchant_documents (expiry_date)
    WHERE is_current_version = TRUE;

CREATE INDEX IF NOT EXISTS idx_merchant_documents_sha256
    ON merchant_documents (sha256_hash)
    WHERE sha256_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_merchant_documents_malware_status
    ON merchant_documents (malware_scan_status);
