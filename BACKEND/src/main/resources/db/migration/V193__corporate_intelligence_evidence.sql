ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS last_corporate_intelligence_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS next_corporate_intelligence_due TIMESTAMP;

CREATE TABLE IF NOT EXISTS corporate_intelligence_checks (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL REFERENCES psps(psp_id),
    merchant_id BIGINT NOT NULL REFERENCES merchants(merchant_id),
    check_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    registry_status VARCHAR(24) NOT NULL,
    registry_provider VARCHAR(100) NOT NULL,
    registry_match_score INTEGER NOT NULL,
    matched_company_name VARCHAR(500),
    matched_company_number VARCHAR(160),
    matched_jurisdiction VARCHAR(100),
    matched_company_status VARCHAR(100),
    matched_company_url VARCHAR(1000),
    registry_candidates JSONB NOT NULL DEFAULT '[]'::jsonb,
    registry_provenance JSONB NOT NULL DEFAULT '{}'::jsonb,
    adverse_media_status VARCHAR(24) NOT NULL,
    adverse_media_provider VARCHAR(100) NOT NULL,
    adverse_media_query TEXT,
    adverse_media_article_count INTEGER NOT NULL DEFAULT 0,
    adverse_media_articles JSONB NOT NULL DEFAULT '[]'::jsonb,
    adverse_media_provenance JSONB NOT NULL DEFAULT '{}'::jsonb,
    risk_score INTEGER NOT NULL,
    decision_reason TEXT NOT NULL,
    evidence_hash VARCHAR(64) NOT NULL,
    checked_by VARCHAR(160) NOT NULL,
    checked_at TIMESTAMP NOT NULL,
    retain_until DATE NOT NULL,
    CONSTRAINT ck_corporate_intelligence_status
        CHECK (status IN ('CLEAR', 'REVIEW', 'UNAVAILABLE')),
    CONSTRAINT ck_corporate_registry_status
        CHECK (registry_status IN ('MATCH', 'POTENTIAL_MATCH', 'NO_MATCH', 'UNAVAILABLE')),
    CONSTRAINT ck_adverse_media_status
        CHECK (adverse_media_status IN ('CLEAR', 'HITS', 'UNAVAILABLE')),
    CONSTRAINT ck_corporate_intelligence_scores
        CHECK (registry_match_score BETWEEN 0 AND 100 AND risk_score BETWEEN 0 AND 100),
    CONSTRAINT uq_corporate_intelligence_evidence_hash UNIQUE (evidence_hash)
);

CREATE INDEX IF NOT EXISTS idx_corporate_intelligence_psp_checked
    ON corporate_intelligence_checks(psp_id, checked_at DESC);
CREATE INDEX IF NOT EXISTS idx_corporate_intelligence_merchant_checked
    ON corporate_intelligence_checks(merchant_id, checked_at DESC);
CREATE INDEX IF NOT EXISTS idx_corporate_intelligence_status
    ON corporate_intelligence_checks(psp_id, status);
CREATE INDEX IF NOT EXISTS idx_merchants_corporate_intelligence_due
    ON merchants(next_corporate_intelligence_due)
    WHERE status NOT IN ('REJECTED', 'TERMINATED', 'BLOCKED');

INSERT INTO reports (
    report_code, report_name, report_category, description, report_type,
    base_entity, requires_approval, enabled
)
VALUES (
    'KYC_004', 'Corporate Intelligence Evidence', 'KYC_AML',
    'Corporate-registry verification and adverse-media evidence, including provider availability, matched identity, articles, decision, and retention.',
    'DYNAMIC', 'corporate_intelligence_checks', TRUE, TRUE
)
ON CONFLICT (report_code) DO NOTHING;

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    order_by_default, is_active, created_by, created_at
)
SELECT r.id, 1,
    'SELECT c.id AS corporate_intelligence_check_id,
            c.merchant_id, m.legal_name, m.trading_name, m.country,
            c.check_type, c.status, c.registry_status, c.registry_provider,
            c.registry_match_score, c.matched_company_name, c.matched_company_number,
            c.matched_jurisdiction, c.matched_company_status, c.matched_company_url,
            c.registry_candidates, c.registry_provenance,
            c.adverse_media_status, c.adverse_media_provider,
            c.adverse_media_article_count, c.adverse_media_articles, c.adverse_media_provenance,
            c.risk_score, c.decision_reason, c.evidence_hash,
            c.checked_by, c.checked_at, c.retain_until
       FROM corporate_intelligence_checks c
       JOIN merchants m ON m.merchant_id = c.merchant_id
      WHERE c.psp_id = :pspId
        AND c.checked_at BETWEEN :dateFrom AND :dateTo
      ORDER BY c.checked_at DESC',
    'SELECT COUNT(*) FROM corporate_intelligence_checks c
      WHERE c.psp_id = :pspId AND c.checked_at BETWEEN :dateFrom AND :dateTo',
    '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
    '[{"field":"status","type":"ENUM","options":["CLEAR","REVIEW","UNAVAILABLE"]},{"field":"registry_status","type":"ENUM","options":["MATCH","POTENTIAL_MATCH","NO_MATCH","UNAVAILABLE"]},{"field":"adverse_media_status","type":"ENUM","options":["CLEAR","HITS","UNAVAILABLE"]},{"field":"registry_provider","type":"STRING"},{"field":"adverse_media_provider","type":"STRING"}]'::jsonb,
    '[{"name":"corporate_intelligence_check_id","type":"LONG","label":"Check ID"},{"name":"merchant_id","type":"LONG","label":"Merchant ID"},{"name":"legal_name","type":"STRING","label":"Merchant"},{"name":"matched_company_number","type":"STRING","label":"Registry Company Number"},{"name":"status","type":"STRING","label":"Decision"},{"name":"registry_status","type":"STRING","label":"Registry"},{"name":"registry_match_score","type":"INTEGER","label":"Registry Score"},{"name":"adverse_media_status","type":"STRING","label":"Adverse Media"},{"name":"adverse_media_article_count","type":"INTEGER","label":"Articles"},{"name":"risk_score","type":"INTEGER","label":"Risk Score"},{"name":"checked_at","type":"DATETIME","label":"Checked"},{"name":"checked_by","type":"STRING","label":"Actor"},{"name":"evidence_hash","type":"STRING","label":"Evidence Hash"},{"name":"retain_until","type":"DATE","label":"Retain Until"}]'::jsonb,
    'checked_at DESC', TRUE, NULL, CURRENT_TIMESTAMP
FROM reports r WHERE r.report_code = 'KYC_004'
ON CONFLICT (report_id, version) DO NOTHING;
