-- Additive upgrade. Back up consultation_summary_jobs before applying once.
-- Keep these columns when rolling back the application; no data removal is needed.
ALTER TABLE consultation_summary_jobs
    ADD COLUMN cutoff_sequence_no INT NULL,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
CREATE INDEX ix_summary_recovery ON consultation_summary_jobs (status, updated_at);
-- Legacy jobs have no exact cutoff and are deliberately not auto-replayed.
