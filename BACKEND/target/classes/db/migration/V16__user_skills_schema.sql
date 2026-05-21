-- ==============================================================================
-- V16: User Skills Schema for Skill-Based Case Assignment
-- ==============================================================================

-- Skill Types - Defines the types of skills available for case assignment
CREATE TABLE IF NOT EXISTS skill_types (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    case_type VARCHAR(50),  -- Maps to case queue types for routing
    proficiency_levels INTEGER DEFAULT 5,  -- Max proficiency level (1-5 scale)
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User Skills - Links users to their skills with proficiency levels
CREATE TABLE IF NOT EXISTS user_skills (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES psp_users(user_id) ON DELETE CASCADE,
    skill_type_id BIGINT NOT NULL REFERENCES skill_types(id) ON DELETE CASCADE,
    proficiency_level INTEGER NOT NULL DEFAULT 1 CHECK (proficiency_level BETWEEN 1 AND 5),
    certified BOOLEAN DEFAULT false,
    certified_at TIMESTAMP,
    certified_by BIGINT REFERENCES psp_users(user_id),
    expires_at TIMESTAMP,  -- Skill certification expiration
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, skill_type_id)
);

-- Case Required Skills - Defines skills required for case queues
CREATE TABLE IF NOT EXISTS case_required_skills (
    id BIGSERIAL PRIMARY KEY,
    queue_id BIGINT NOT NULL REFERENCES case_queues(id) ON DELETE CASCADE,
    skill_type_id BIGINT NOT NULL REFERENCES skill_types(id) ON DELETE CASCADE,
    min_proficiency INTEGER NOT NULL DEFAULT 1 CHECK (min_proficiency BETWEEN 1 AND 5),
    weight DECIMAL(3,2) DEFAULT 1.00,  -- Priority weight for scoring (0.00-1.00)
    required BOOLEAN DEFAULT true,  -- If true, skill is mandatory; if false, it's preferred
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(queue_id, skill_type_id)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_skills_user_id ON user_skills(user_id);
CREATE INDEX IF NOT EXISTS idx_user_skills_skill_type_id ON user_skills(skill_type_id);
CREATE INDEX IF NOT EXISTS idx_user_skills_proficiency ON user_skills(skill_type_id, proficiency_level);
CREATE INDEX IF NOT EXISTS idx_case_required_skills_queue ON case_required_skills(queue_id);
CREATE INDEX IF NOT EXISTS idx_skill_types_case_type ON skill_types(case_type);
CREATE INDEX IF NOT EXISTS idx_skill_types_active ON skill_types(active);

-- Seed default skill types
INSERT INTO skill_types (name, description, case_type, proficiency_levels) VALUES
('FRAUD_INVESTIGATION', 'Transaction fraud analysis and investigation', 'FRAUD', 5),
('SANCTIONS_SCREENING', 'Sanctions list screening and verification', 'SANCTIONS', 5),
('MERCHANT_DUE_DILIGENCE', 'Merchant risk assessment and KYC review', 'MERCHANT_REVIEW', 5),
('SAR_PREPARATION', 'Suspicious Activity Report preparation and filing', 'SAR', 5),
('HIGH_VALUE_TRANSACTIONS', 'High-value transaction monitoring and analysis', 'HIGH_VALUE', 5),
('PEP_SCREENING', 'Politically Exposed Persons screening and verification', 'PEP', 5),
('CROSS_BORDER_PAYMENTS', 'Cross-border and international payment analysis', 'CROSS_BORDER', 5),
('CRYPTO_TRANSACTIONS', 'Cryptocurrency transaction monitoring and analysis', 'CRYPTO', 5)
ON CONFLICT (name) DO NOTHING;

-- Comments for documentation
COMMENT ON TABLE skill_types IS 'Defines available skill types for case assignment routing';
COMMENT ON TABLE user_skills IS 'Tracks user skills with proficiency levels and certifications';
COMMENT ON TABLE case_required_skills IS 'Defines skill requirements for case queues';
COMMENT ON COLUMN user_skills.proficiency_level IS '1=Novice, 2=Beginner, 3=Intermediate, 4=Advanced, 5=Expert';
COMMENT ON COLUMN case_required_skills.weight IS 'Priority weight for multi-skill matching (higher = more important)';

