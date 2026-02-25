-- Tenant settings: one currency per organization
CREATE TABLE IF NOT EXISTS tenant_settings (
    tenant_id   VARCHAR(100) PRIMARY KEY,
    currency    CHAR(3)      NOT NULL DEFAULT 'KES',
    org_name    VARCHAR(200),
    country_code CHAR(3),
    timezone    VARCHAR(50)  NOT NULL DEFAULT 'Africa/Nairobi',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Seed the default admin tenant
INSERT INTO tenant_settings (tenant_id, currency, org_name, country_code, timezone)
VALUES ('admin', 'KES', 'Athena Financial Services', 'KEN', 'Africa/Nairobi')
ON CONFLICT (tenant_id) DO NOTHING;
