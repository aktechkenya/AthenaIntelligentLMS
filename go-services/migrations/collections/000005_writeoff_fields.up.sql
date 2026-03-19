ALTER TABLE collection_cases ADD COLUMN IF NOT EXISTS write_off_reason TEXT;
ALTER TABLE collection_cases ADD COLUMN IF NOT EXISTS write_off_requested_by VARCHAR(100);
ALTER TABLE collection_cases ADD COLUMN IF NOT EXISTS write_off_approved_by VARCHAR(100);
