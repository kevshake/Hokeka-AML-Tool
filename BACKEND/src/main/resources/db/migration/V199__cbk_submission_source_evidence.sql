ALTER TABLE cbk_submissions
    ALTER COLUMN reference_number DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS source_record_count INTEGER;

-- Earlier application versions generated this exact local value for rejected
-- submissions. It is not a CBK-issued request number and must not be presented
-- as regulator evidence.
UPDATE cbk_submissions
SET reference_number = NULL
WHERE status = 'REJECTED'
  AND reference_number ~ '^CBK-[0-9]{4}-[A-F0-9]{8}$';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_cbk_submission_source_record_count'
    ) THEN
        ALTER TABLE cbk_submissions
            ADD CONSTRAINT chk_cbk_submission_source_record_count
            CHECK (source_record_count IS NULL OR source_record_count >= 0);
    END IF;
END $$;
