-- ===================================================================================
-- AML Merchant Screening System - Database Schema
-- Migration: V2__sanctions_screening_schema.sql
-- Description: PostgreSQL tables for merchant onboarding, compliance, and audit
-- ===================================================================================

-- ===================================================================================
-- SANCTIONS METADATA (PostgreSQL tracks download versions)
-- ===================================================================================

CREATE TABLE IF NOT EXISTS sanctions_lists (
    list_id SERIAL PRIMARY KEY,
    list_name VARCHAR(100) NOT NULL,  -- e.g., 'OFAC_SDN', 'UN_SC', 'EU_FSF', 'OPENSANCTIONS_ALL'
    list_source VARCHAR(100) NOT NULL,  -- e.g., 'OpenSanctions', 'OFAC', 'UN'
    version VARCHAR(100),  -- version/timestamp from source
    downloaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    record_count INTEGER,  -- number of entities downloaded
    metadata JSONB,  -- additional metadata from source
    CONSTRAINT uk_sanctions_lists_version UNIQUE (list_name, version)
);

CREATE INDEX IF NOT EXISTS idx_sanctions_lists_name ON sanctions_lists(list_name);
CREATE INDEX IF NOT EXISTS idx_sanctions_lists_downloaded ON sanctions_lists(downloaded_at DESC);

COMMENT ON TABLE sanctions_lists IS 'Tracks sanctions list download versions and metadata';
COMMENT ON COLUMN sanctions_lists.version IS 'Version ID from data source to prevent duplicate downloads';

-- ===================================================================================
-- MERCHANT DATA
-- ===================================================================================

CREATE TABLE IF NOT EXISTS merchants (
    merchant_id BIGSERIAL PRIMARY KEY,
    
    -- Basic Information
    legal_name VARCHAR(500) NOT NULL,
    trading_name VARCHAR(500),
    country VARCHAR(3) NOT NULL,  -- ISO 3166-1 alpha-3
    registration_number VARCHAR(100) NOT NULL,
    tax_id VARCHAR(100),
    
    -- Business Details
    mcc VARCHAR(10) NOT NULL,  -- Merchant Category Code
    business_type VARCHAR(50),  -- CORPORATION, LLC, PARTNERSHIP, SOLE_PROPRIETOR
    expected_monthly_volume BIGINT,  -- in cents
    transaction_channel VARCHAR(50),  -- ONLINE, IN_STORE, MOBILE
    website VARCHAR(500),
    
    -- Address
    address_street VARCHAR(500),
    address_city VARCHAR(200),
    address_state VARCHAR(100),
    address_postal_code VARCHAR(20),
    address_country VARCHAR(3),
    
    -- Operational Data
    operating_countries TEXT[],  -- Array of country codes
    registration_date DATE,
    
    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_SCREENING',  -- PENDING_SCREENING, ACTIVE, SUSPENDED, UNDER_REVIEW, BLOCKED
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_screened_at TIMESTAMP,
    next_screening_due DATE,  -- Auto-calculated: last_screened_at + 21 days
    
    CONSTRAINT uk_merchants_registration UNIQUE (country, registration_number)
);

CREATE INDEX IF NOT EXISTS idx_merchants_status ON merchants(status);
CREATE INDEX IF NOT EXISTS idx_merchants_country ON merchants(country);
CREATE INDEX IF NOT EXISTS idx_merchants_mcc ON merchants(mcc);
CREATE INDEX IF NOT EXISTS idx_merchants_created ON merchants(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_merchants_last_screened ON merchants(last_screened_at);

COMMENT ON TABLE merchants IS 'Merchant registration and business information';
COMMENT ON COLUMN merchants.expected_monthly_volume IS 'Expected monthly transaction volume in cents';

-- ===================================================================================
-- BENEFICIAL OWNERS (UBOs)
-- ===================================================================================

CREATE TABLE IF NOT EXISTS beneficial_owners (
    owner_id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL REFERENCES merchants(merchant_id) ON DELETE CASCADE,
    
    -- Identity (PII - will be encrypted)
    full_name VARCHAR(500) NOT NULL,
    date_of_birth DATE NOT NULL,  -- Encrypted in application layer
    nationality VARCHAR(3) NOT NULL,  -- ISO 3166-1 alpha-3
    country_of_residence VARCHAR(3),
    
    -- Identification
    passport_number VARCHAR(100),  -- Encrypted
    national_id VARCHAR(100),  -- Encrypted
    
    -- Ownership
    ownership_percentage INTEGER NOT NULL CHECK (ownership_percentage >= 0 AND ownership_percentage <= 100),
    
    -- Screening Flags
    is_pep BOOLEAN DEFAULT FALSE,
    is_sanctioned BOOLEAN DEFAULT FALSE,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_screened_at TIMESTAMP,
    
    CONSTRAINT chk_ownership_percentage CHECK (ownership_percentage BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_beneficial_owners_merchant ON beneficial_owners(merchant_id);
CREATE INDEX IF NOT EXISTS idx_beneficial_owners_nationality ON beneficial_owners(nationality);
CREATE INDEX IF NOT EXISTS idx_beneficial_owners_pep ON beneficial_owners(is_pep) WHERE is_pep = TRUE;
CREATE INDEX IF NOT EXISTS idx_beneficial_owners_sanctioned ON beneficial_owners(is_sanctioned) WHERE is_sanctioned = TRUE;

COMMENT ON TABLE beneficial_owners IS 'Ultimate beneficial owners (UBOs) with encrypted PII';
COMMENT ON COLUMN beneficial_owners.ownership_percentage IS 'Percentage of business owned (25%+ typically required)';

-- ===================================================================================
-- SCREENING RESULTS
-- ===================================================================================

CREATE TABLE IF NOT EXISTS merchant_screening_results (
    screening_id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL REFERENCES merchants(merchant_id) ON DELETE CASCADE,
    
    -- Screening Details
    screening_type VARCHAR(50) NOT NULL,  -- ONBOARDING, PERIODIC, UPDATE, MANUAL
    screening_status VARCHAR(50) NOT NULL,  -- CLEAR, MATCH, POTENTIAL_MATCH
    match_score DECIMAL(5,4),  -- 0.0000 to 1.0000
    match_count INTEGER DEFAULT 0,
    
    -- Results
    match_details JSONB,  -- Array of matches with similarity scores
    screening_provider VARCHAR(100),  -- INTERNAL_AEROSPIKE, COMPLYADVANTAGE, etc.
    
    -- Metadata
    screened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    screened_by VARCHAR(100),  -- User ID or SYSTEM
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_merchant_screening_merchant ON merchant_screening_results(merchant_id);
CREATE INDEX IF NOT EXISTS idx_merchant_screening_status ON merchant_screening_results(screening_status);
CREATE INDEX IF NOT EXISTS idx_merchant_screening_type ON merchant_screening_results(screening_type);
CREATE INDEX IF NOT EXISTS idx_merchant_screening_date ON merchant_screening_results(screened_at DESC);
CREATE INDEX IF NOT EXISTS idx_merchant_screening_details ON merchant_screening_results USING gin(match_details);

COMMENT ON TABLE merchant_screening_results IS 'Merchant sanctions screening results history';

CREATE TABLE IF NOT EXISTS owner_screening_results (
    screening_id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES beneficial_owners(owner_id) ON DELETE CASCADE,
    merchant_id BIGINT NOT NULL REFERENCES merchants(merchant_id) ON DELETE CASCADE,
    
    -- Screening Details
    screening_type VARCHAR(50) NOT NULL,
    screening_status VARCHAR(50) NOT NULL,
    match_score DECIMAL(5,4),
    match_count INTEGER DEFAULT 0,
    
    -- Results
    match_details JSONB,
    screening_provider VARCHAR(100),
    
    -- Metadata
    screened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    screened_by VARCHAR(100),
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_owner_screening_owner ON owner_screening_results(owner_id);
CREATE INDEX IF NOT EXISTS idx_owner_screening_merchant ON owner_screening_results(merchant_id);
CREATE INDEX IF NOT EXISTS idx_owner_screening_status ON owner_screening_results(screening_status);
CREATE INDEX IF NOT EXISTS idx_owner_screening_date ON owner_screening_results(screened_at DESC);

COMMENT ON TABLE owner_screening_results IS 'Beneficial owner screening results history';

-- ===================================================================================
-- EXTERNAL AML PROVIDER RESPONSES (Sumsub, etc.)
-- ===================================================================================

CREATE TABLE IF NOT EXISTS external_aml_responses (
    response_id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT REFERENCES merchants(merchant_id) ON DELETE CASCADE,
    owner_id BIGINT REFERENCES beneficial_owners(owner_id) ON DELETE CASCADE,
    
    -- Provider Details
    provider_name VARCHAR(100) NOT NULL,  -- SUMSUB, COMPLYADVANTAGE, etc.
    screening_type VARCHAR(50) NOT NULL,  -- MERCHANT, BENEFICIAL_OWNER
    
    -- Request/Response
    request_payload JSONB NOT NULL,  -- What we sent
    response_payload JSONB NOT NULL,  -- Full response from provider
    response_status VARCHAR(50),  -- SUCCESS, ERROR, TIMEOUT
    http_status_code INTEGER,
    
    -- Results Summary
    sanctions_match BOOLEAN DEFAULT FALSE,
    pep_match BOOLEAN DEFAULT FALSE,
    adverse_media_match BOOLEAN DEFAULT FALSE,
    overall_risk_level VARCHAR(50),  -- Provider's risk assessment
    
    -- Billing
    cost_amount DECIMAL(10,4),  -- Cost per check
    cost_currency VARCHAR(3) DEFAULT 'USD',
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    screened_by VARCHAR(100),
    
    -- Ensure either merchant_id or owner_id is set
    CONSTRAINT chk_external_aml_entity CHECK (
        (merchant_id IS NOT NULL AND owner_id IS NULL) OR
        (merchant_id IS NULL AND owner_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_external_aml_merchant ON external_aml_responses(merchant_id);
CREATE INDEX IF NOT EXISTS idx_external_aml_owner ON external_aml_responses(owner_id);
CREATE INDEX IF NOT EXISTS idx_external_aml_provider ON external_aml_responses(provider_name);
CREATE INDEX IF NOT EXISTS idx_external_aml_created ON external_aml_responses(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_external_aml_response ON external_aml_responses USING gin(response_payload);

COMMENT ON TABLE external_aml_responses IS 'Raw responses from external AML providers (Sumsub, etc.) for audit and reference';
COMMENT ON COLUMN external_aml_responses.response_payload IS 'Full JSON response from provider - stored for audit trail';

-- ===================================================================================
-- RISK SCORING
-- ===================================================================================

CREATE TABLE IF NOT EXISTS merchant_risk_scores (
    score_id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL REFERENCES merchants(merchant_id) ON DELETE CASCADE,
    
    -- Risk Score
    total_points INTEGER NOT NULL DEFAULT 0,
    risk_level VARCHAR(50) NOT NULL,  -- LOW, MEDIUM, HIGH, CRITICAL
    
    -- Component Scores (JSON breakdown)
    component_scores JSONB,  -- {aml: 50, geographic: 20, business: 10, ownership: 15}
    risk_factors TEXT[],  -- Array of risk reasons
    
    -- Decision
    recommended_action VARCHAR(50),  -- APPROVE, REVIEW, REJECT, ENHANCED_DUE_DILIGENCE
    requires_edd BOOLEAN DEFAULT FALSE,
    
    -- Rules Version
    rules_version VARCHAR(50),  -- e.g., "v3.2"
    
    -- Validity
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP,
    
    -- Metadata
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_merchant_risk_merchant ON merchant_risk_scores(merchant_id);
CREATE INDEX IF NOT EXISTS idx_merchant_risk_level ON merchant_risk_scores(risk_level);
CREATE INDEX IF NOT EXISTS idx_merchant_risk_action ON merchant_risk_scores(recommended_action);
CREATE INDEX IF NOT EXISTS idx_merchant_risk_calculated ON merchant_risk_scores(calculated_at DESC);

COMMENT ON TABLE merchant_risk_scores IS 'Calculated risk scores for merchants';
COMMENT ON COLUMN merchant_risk_scores.rules_version IS 'Version of risk rules used for calculation';

-- ===================================================================================
-- COMPLIANCE CASE MANAGEMENT
-- ===================================================================================

CREATE TABLE IF NOT EXISTS compliance_cases (
    case_id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL REFERENCES merchants(merchant_id) ON DELETE CASCADE,
    
    -- Case Information
    case_type VARCHAR(50) NOT NULL,  -- ONBOARDING, PERIODIC_REVIEW, ALERT, UPDATE
    case_status VARCHAR(50) NOT NULL DEFAULT 'OPEN',  -- OPEN, IN_PROGRESS, RESOLVED, ESCALATED, CLOSED
    priority VARCHAR(20) NOT NULL,  -- LOW, MEDIUM, HIGH, URGENT
    
    -- Assignment
    assigned_to VARCHAR(100),  -- User ID of compliance officer
    
    -- Resolution
    resolution JSONB,  -- {decision: 'APPROVE', reason: '...', evidence: [...], notes: '...'}
    
    -- Dates
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date TIMESTAMP,
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,
    
    -- Metadata
    created_by VARCHAR(100),
    resolved_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_compliance_cases_merchant ON compliance_cases(merchant_id);
CREATE INDEX IF NOT EXISTS idx_compliance_cases_status ON compliance_cases(case_status);
CREATE INDEX IF NOT EXISTS idx_compliance_cases_priority ON compliance_cases(priority);
CREATE INDEX IF NOT EXISTS idx_compliance_cases_assigned ON compliance_cases(assigned_to);
CREATE INDEX IF NOT EXISTS idx_compliance_cases_created ON compliance_cases(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_compliance_cases_due ON compliance_cases(due_date) WHERE case_status IN ('OPEN', 'IN_PROGRESS');

COMMENT ON TABLE compliance_cases IS 'Compliance review cases for manual decision making';

-- ===================================================================================
-- MONITORING ALERTS
-- ===================================================================================

CREATE TABLE IF NOT EXISTS monitoring_alerts (
    alert_id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL REFERENCES merchants(merchant_id) ON DELETE CASCADE,
    
    -- Alert Information
    alert_type VARCHAR(100) NOT NULL,  -- NEW_SANCTIONS_MATCH, NEW_PEP, ADVERSE_MEDIA, RISK_CHANGE
    alert_severity VARCHAR(20) NOT NULL,  -- INFO, WARN, CRITICAL
    alert_details JSONB NOT NULL,
    
    -- Status
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by VARCHAR(100),
    acknowledged_at TIMESTAMP,
    
    -- Resolution
    resolution VARCHAR(50),  -- CLEARED, CASE_CREATED, MERCHANT_SUSPENDED, FALSE_POSITIVE
    resolution_notes TEXT,
    
    -- Dates
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_monitoring_alerts_merchant ON monitoring_alerts(merchant_id);
CREATE INDEX IF NOT EXISTS idx_monitoring_alerts_type ON monitoring_alerts(alert_type);
CREATE INDEX IF NOT EXISTS idx_monitoring_alerts_severity ON monitoring_alerts(alert_severity);
CREATE INDEX IF NOT EXISTS idx_monitoring_alerts_acknowledged ON monitoring_alerts(acknowledged);
CREATE INDEX IF NOT EXISTS idx_monitoring_alerts_created ON monitoring_alerts(created_at DESC);

COMMENT ON TABLE monitoring_alerts IS 'Alerts from periodic rescreening and monitoring';

-- ===================================================================================
-- AUDIT TRAIL (IMMUTABLE)
-- ===================================================================================

CREATE TABLE IF NOT EXISTS audit_trail (
    audit_id BIGSERIAL PRIMARY KEY,
    
    -- Entity
    merchant_id BIGINT,  -- Nullable for system-level actions
    
    -- Action
    action VARCHAR(100) NOT NULL,  -- ONBOARDED, SCREENED, APPROVED, REJECTED, UPDATED, etc.
    performed_by VARCHAR(100) NOT NULL,  -- User ID or SYSTEM
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Evidence (immutable JSON)
    evidence JSONB,  -- Screening results, risk scores, decision details
    
    -- Rules Version
    rules_version VARCHAR(50),
    
    -- Decision
    decision VARCHAR(50),
    decision_reason TEXT,
    
    -- Metadata
    ip_address VARCHAR(45),  -- IPv4 or IPv6
    user_agent TEXT
);

-- No UPDATE or DELETE - this table is append-only
CREATE INDEX IF NOT EXISTS idx_audit_trail_merchant ON audit_trail(merchant_id);
CREATE INDEX IF NOT EXISTS idx_audit_trail_action ON audit_trail(action);
CREATE INDEX IF NOT EXISTS idx_audit_trail_performed_at ON audit_trail(performed_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_trail_performed_by ON audit_trail(performed_by);

COMMENT ON TABLE audit_trail IS 'Immutable audit log of all compliance actions and decisions';
COMMENT ON COLUMN audit_trail.evidence IS 'Complete evidence for audit - never modified after insert';

-- ===================================================================================
-- RISK RULES VERSIONING
-- ===================================================================================

CREATE TABLE IF NOT EXISTS risk_rules_versions (
    version_id SERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL UNIQUE,  -- e.g., "v3.2"
    
    -- Rules Content
    rules_yaml TEXT NOT NULL,  -- Full YAML configuration
    rules_json JSONB,  -- Parsed JSON for querying
    
    -- Activation
    activated_at TIMESTAMP NOT NULL,
    activated_by VARCHAR(100) NOT NULL,
    deactivated_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Change Notes
    change_notes TEXT,
    
    CONSTRAINT uk_risk_rules_version UNIQUE (version)
);

CREATE INDEX IF NOT EXISTS idx_risk_rules_active ON risk_rules_versions(is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_risk_rules_activated ON risk_rules_versions(activated_at DESC);

COMMENT ON TABLE risk_rules_versions IS 'Version control for risk scoring rules';
COMMENT ON COLUMN risk_rules_versions.is_active IS 'Only one version should be active at a time';

-- ===================================================================================
-- TRIGGERS FOR UPDATED_AT
-- ===================================================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_merchants_updated_at BEFORE UPDATE ON merchants
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_beneficial_owners_updated_at BEFORE UPDATE ON beneficial_owners
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ===================================================================================
-- INITIAL DATA
-- ===================================================================================

-- Insert risk scoring thresholds into model_config (assumes model_config table exists)
INSERT INTO model_config (config_key, value, description) VALUES
('risk.threshold.approve', '30', 'Risk score threshold for auto-approval'),
('risk.threshold.review', '50', 'Risk score threshold for manual review'),
('risk.threshold.edd', '50', 'Risk score threshold for enhanced due diligence'),
('risk.threshold.reject', '80', 'Risk score threshold for auto-rejection'),
('risk.country.high_risk', 'AF,IR,KP,SY,YE,MM,VE,ZW', 'High-risk country codes (ISO 3166-1 alpha-3)'),
('risk.mcc.high_risk', '6211,7995,7273,5993,6051', 'High-risk MCC codes (money transfer, gambling, dating, crypto)'),
('risk.volume.threshold', '100000000', 'High volume threshold in cents ($1M)'),
('risk.business.new_months', '6', 'Months to consider business as new'),
('rescreening.frequency.days', '21', 'Merchant rescreening frequency in days (3 weeks)')
ON CONFLICT (config_key) DO NOTHING;

-- ===================================================================================
-- VIEWS FOR COMMON QUERIES
-- ===================================================================================

CREATE OR REPLACE VIEW v_merchants_needing_rescreening AS
SELECT 
    m.merchant_id,
    m.legal_name,
    m.country,
    m.status,
    m.last_screened_at,
    m.next_screening_due,
    CURRENT_DATE - m.last_screened_at::DATE as days_since_screening,
    CASE 
        WHEN m.next_screening_due < CURRENT_DATE THEN TRUE
        ELSE FALSE
    END as is_overdue
FROM merchants m
WHERE m.status = 'ACTIVE'
  AND (
      m.next_screening_due IS NULL OR 
      m.next_screening_due <= CURRENT_DATE
  )
ORDER BY m.next_screening_due NULLS FIRST, m.last_screened_at NULLS FIRST;

COMMENT ON VIEW v_merchants_needing_rescreening IS 'Merchants requiring periodic rescreening (every 21 days / 3 weeks)';

CREATE OR REPLACE VIEW v_high_risk_merchants AS
SELECT 
    m.merchant_id,
    m.legal_name,
    m.country,
    m.status,
    mrs.risk_level,
    mrs.total_points,
    mrs.recommended_action,
    mrs.calculated_at
FROM merchants m
JOIN merchant_risk_scores mrs ON m.merchant_id = mrs.merchant_id
WHERE mrs.risk_level IN ('HIGH', 'CRITICAL')
  AND mrs.calculated_at = (
      SELECT MAX(calculated_at) 
      FROM merchant_risk_scores 
      WHERE merchant_id = m.merchant_id
  )
ORDER BY mrs.total_points DESC;

COMMENT ON VIEW v_high_risk_merchants IS 'Merchants with high or critical risk scores';

-- ===================================================================================
-- GRANTS (adjust as needed for your security model)
-- ===================================================================================

-- Example: Grant access to application user
-- GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO aml_app_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO aml_app_user;
-- REVOKE UPDATE, DELETE ON audit_trail FROM aml_app_user;  -- Immutable

-- ===================================================================================
-- END OF MIGRATION
-- ===================================================================================

;