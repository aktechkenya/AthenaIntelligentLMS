-- Fiscal Period Management
CREATE TABLE IF NOT EXISTS fiscal_periods (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(50)     NOT NULL,
    period_year     INTEGER         NOT NULL,
    period_month    INTEGER         NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    closed_by       VARCHAR(100),
    closed_at       TIMESTAMPTZ,
    reopened_by     VARCHAR(100),
    reopen_reason   VARCHAR(500),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, period_year, period_month)
);

CREATE INDEX IF NOT EXISTS idx_fp_tenant ON fiscal_periods(tenant_id);
CREATE INDEX IF NOT EXISTS idx_fp_status ON fiscal_periods(status);
