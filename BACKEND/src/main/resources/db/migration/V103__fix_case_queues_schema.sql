-- Fix case_queues schema - Add missing columns and fix constraints
-- Note: case_queues table is created in V8, we only add missing columns here

-- Add missing columns to case_queues if not exists
ALTER TABLE case_queues ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE case_queues ADD COLUMN IF NOT EXISTS psp_id BIGINT;

-- Create index for PSP filtering
CREATE INDEX IF NOT EXISTS idx_case_queues_psp ON case_queues(psp_id);

-- Ensure compliance_cases has queue_id column (already added in V8 but ensure it's there)
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS queue_id BIGINT;

-- Add foreign key constraint if not exists
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_compliance_cases_queue') THEN
        ALTER TABLE compliance_cases ADD CONSTRAINT fk_compliance_cases_queue 
        FOREIGN KEY (queue_id) REFERENCES case_queues(id);
    END IF;
END $$;

-- Create case_activities table ONLY if it doesn't exist (it's defined in V8)
-- This migration ensures the table exists with correct schema
CREATE TABLE IF NOT EXISTS case_activities (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    description TEXT,
    details TEXT,
    performed_by BIGINT NOT NULL,
    performed_at TIMESTAMP NOT NULL,
    related_entity_id BIGINT,
    related_entity_type VARCHAR(50),
    CONSTRAINT fk_case_activities_case FOREIGN KEY (case_id) REFERENCES compliance_cases(case_id) ON DELETE CASCADE,
    CONSTRAINT fk_case_activities_user FOREIGN KEY (performed_by) REFERENCES psp_users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_case_activities_case ON case_activities(case_id, performed_at DESC);
CREATE INDEX IF NOT EXISTS idx_case_activities_type ON case_activities(activity_type);
CREATE INDEX IF NOT EXISTS idx_case_activities_user ON case_activities(performed_by);
