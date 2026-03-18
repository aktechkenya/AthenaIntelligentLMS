-- V3: Add tenant_id column if missing (fixes schema mismatch when table was
-- originally created by the Java Flyway migration which omitted tenant_id)
ALTER TABLE media_files
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_media_files_tenant_id ON media_files(tenant_id);
