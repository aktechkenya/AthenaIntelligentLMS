-- Financial Audit Trail
CREATE TABLE IF NOT EXISTS financial_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(50)     NOT NULL,
    action          VARCHAR(100)    NOT NULL,
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       VARCHAR(100)    NOT NULL,
    user_id         VARCHAR(100),
    user_role       VARCHAR(50),
    details         JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_tenant ON financial_audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON financial_audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_user ON financial_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON financial_audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_created ON financial_audit_log(created_at);
