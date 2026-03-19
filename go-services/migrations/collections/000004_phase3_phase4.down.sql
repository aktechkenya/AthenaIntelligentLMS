DROP TABLE IF EXISTS collection_officers;
ALTER TABLE collection_cases DROP COLUMN IF EXISTS write_off_approved_by;
ALTER TABLE collection_cases DROP COLUMN IF EXISTS write_off_requested_by;
ALTER TABLE collection_cases DROP COLUMN IF EXISTS write_off_reason;
ALTER TABLE collection_cases DROP COLUMN IF EXISTS product_type;
DROP INDEX IF EXISTS idx_collection_cases_assigned;
DROP INDEX IF EXISTS idx_collection_cases_stage;
DROP INDEX IF EXISTS idx_collection_cases_opened;
DROP INDEX IF EXISTS idx_collection_cases_closed;
