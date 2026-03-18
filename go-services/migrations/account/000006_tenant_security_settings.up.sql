-- Add security-related settings to tenant_settings
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS session_timeout_minutes INT NOT NULL DEFAULT 30;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS audit_trail_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS ip_whitelist_enabled BOOLEAN NOT NULL DEFAULT false;
