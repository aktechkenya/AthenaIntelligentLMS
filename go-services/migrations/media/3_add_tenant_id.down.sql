DROP INDEX IF EXISTS idx_media_files_tenant_id;
ALTER TABLE media_files DROP COLUMN IF EXISTS tenant_id;
