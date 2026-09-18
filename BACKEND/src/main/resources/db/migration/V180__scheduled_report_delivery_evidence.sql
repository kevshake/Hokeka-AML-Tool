ALTER TABLE report_schedule_history
    ADD COLUMN IF NOT EXISTS psp_id BIGINT,
    ADD COLUMN IF NOT EXISTS output_format VARCHAR(16),
    ADD COLUMN IF NOT EXISTS recipients JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;

UPDATE report_schedule_history h
SET psp_id = s.psp_id
FROM report_schedules s
WHERE s.id = h.schedule_id AND h.psp_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_report_sched_hist_psp_time
    ON report_schedule_history(psp_id, scheduled_for DESC);
