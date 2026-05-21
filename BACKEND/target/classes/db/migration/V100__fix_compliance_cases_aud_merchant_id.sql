-- Fix merchant_id column type in compliance_cases_aud audit table
-- The audit table was created with VARCHAR but should be BIGINT to match the entity
-- This migration fixes the type mismatch that causes schema validation errors

-- Check if the audit table exists and has the wrong type
DO $$
BEGIN
    -- Check if compliance_cases_aud table exists
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'compliance_cases_aud'
    ) THEN
        -- Check if merchant_id column exists and is VARCHAR
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'compliance_cases_aud' 
            AND column_name = 'merchant_id'
            AND data_type = 'character varying'
        ) THEN
            -- Convert VARCHAR to BIGINT
            -- First, handle any non-numeric values by setting them to NULL
            UPDATE compliance_cases_aud 
            SET merchant_id = NULL 
            WHERE merchant_id IS NOT NULL 
            AND merchant_id !~ '^-?[0-9]+$';
            
            -- Alter the column type
            ALTER TABLE compliance_cases_aud 
            ALTER COLUMN merchant_id TYPE BIGINT 
            USING CASE 
                WHEN merchant_id IS NULL OR merchant_id = '' THEN NULL
                WHEN merchant_id ~ '^-?[0-9]+$' THEN merchant_id::BIGINT
                ELSE NULL
            END;
            
            RAISE NOTICE 'Fixed merchant_id column type in compliance_cases_aud from VARCHAR to BIGINT';
        END IF;
    END IF;
END $$;

