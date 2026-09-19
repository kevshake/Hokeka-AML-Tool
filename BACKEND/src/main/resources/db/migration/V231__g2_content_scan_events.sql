-- Persist G2 website content monitoring scan results for dashboard visibility.
CREATE TABLE IF NOT EXISTS g2_content_scan_events (
    id              BIGSERIAL PRIMARY KEY,
    merchant_id     BIGINT NOT NULL,
    psp_id          BIGINT,
    website         TEXT,
    scanned_url     TEXT,
    status          VARCHAR(32) NOT NULL,
    matched_keyword VARCHAR(64),
    message         TEXT,
    case_created    BOOLEAN NOT NULL DEFAULT FALSE,
    scanned_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    scanned_by      VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_g2_scan_merchant ON g2_content_scan_events (merchant_id, scanned_at DESC);
CREATE INDEX IF NOT EXISTS idx_g2_scan_psp ON g2_content_scan_events (psp_id, scanned_at DESC);
