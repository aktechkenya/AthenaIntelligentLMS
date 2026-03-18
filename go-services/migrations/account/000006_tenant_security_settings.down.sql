ALTER TABLE tenant_settings DROP COLUMN IF EXISTS two_factor_enabled;
ALTER TABLE tenant_settings DROP COLUMN IF EXISTS session_timeout_minutes;
ALTER TABLE tenant_settings DROP COLUMN IF EXISTS audit_trail_enabled;
ALTER TABLE tenant_settings DROP COLUMN IF EXISTS ip_whitelist_enabled;
