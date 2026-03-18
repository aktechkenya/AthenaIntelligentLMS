-- V2: Audit trail, billing cycles, DPD/NPL tracking, configurable credit bands, fee framework

-- ─── Audit Log ──────────────────────────────────────────────────────────────────
CREATE TABLE overdraft_audit_log (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(30) NOT NULL,   -- WALLET, FACILITY, TRANSACTION
    entity_id       UUID NOT NULL,
    action          VARCHAR(30) NOT NULL,   -- CREATED, UPDATED, SUSPENDED, DEPOSIT, WITHDRAWAL, INTEREST_CHARGED, OVERDRAFT_APPLIED, OVERDRAFT_DRAWN, OVERDRAFT_REPAID, FEE_CHARGED
    actor           VARCHAR(200) NOT NULL DEFAULT 'SYSTEM',
    before_snapshot JSONB,
    after_snapshot  JSONB,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_tenant ON overdraft_audit_log(tenant_id);
CREATE INDEX idx_audit_entity ON overdraft_audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_action ON overdraft_audit_log(action);
CREATE INDEX idx_audit_created ON overdraft_audit_log(created_at);

-- ─── Add DPD/NPL and principal/interest split columns to facilities ─────────
ALTER TABLE overdraft_facilities ADD COLUMN drawn_principal NUMERIC(19,4) NOT NULL DEFAULT 0;
ALTER TABLE overdraft_facilities ADD COLUMN accrued_interest NUMERIC(19,4) NOT NULL DEFAULT 0;
ALTER TABLE overdraft_facilities ADD COLUMN dpd INTEGER NOT NULL DEFAULT 0;
ALTER TABLE overdraft_facilities ADD COLUMN npl_stage VARCHAR(20) NOT NULL DEFAULT 'PERFORMING';
ALTER TABLE overdraft_facilities ADD COLUMN last_billing_date DATE;
ALTER TABLE overdraft_facilities ADD COLUMN next_billing_date DATE;
ALTER TABLE overdraft_facilities ADD COLUMN expiry_date DATE;
ALTER TABLE overdraft_facilities ADD COLUMN last_dpd_refresh DATE;

-- Migrate existing drawn_amount into drawn_principal (interest was mixed in)
UPDATE overdraft_facilities SET drawn_principal = drawn_amount WHERE drawn_amount > 0;

-- ─── Billing Statements ─────────────────────────────────────────────────────────
CREATE TABLE overdraft_billing_statements (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           VARCHAR(100) NOT NULL,
    facility_id         UUID NOT NULL REFERENCES overdraft_facilities(id),
    billing_date        DATE NOT NULL,
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,
    opening_balance     NUMERIC(19,4) NOT NULL DEFAULT 0,
    interest_accrued    NUMERIC(19,4) NOT NULL DEFAULT 0,
    fees_charged        NUMERIC(19,4) NOT NULL DEFAULT 0,
    payments_received   NUMERIC(19,4) NOT NULL DEFAULT 0,
    closing_balance     NUMERIC(19,4) NOT NULL DEFAULT 0,
    minimum_payment_due NUMERIC(19,4) NOT NULL DEFAULT 0,
    due_date            DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- OPEN, PAID, OVERDUE, PARTIAL
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_billing_facility ON overdraft_billing_statements(facility_id);
CREATE INDEX idx_billing_tenant ON overdraft_billing_statements(tenant_id);
CREATE INDEX idx_billing_status ON overdraft_billing_statements(status);
CREATE INDEX idx_billing_due_date ON overdraft_billing_statements(due_date);

-- ─── Configurable Credit Bands ──────────────────────────────────────────────────
CREATE TABLE credit_band_configs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(100) NOT NULL,
    band            VARCHAR(1) NOT NULL,
    min_score       INTEGER NOT NULL,
    max_score       INTEGER NOT NULL,
    approved_limit  NUMERIC(19,4) NOT NULL,
    interest_rate   NUMERIC(5,4) NOT NULL,
    arrangement_fee NUMERIC(19,4) NOT NULL DEFAULT 0,
    annual_fee      NUMERIC(19,4) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_from  DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to    DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_band_config_tenant ON credit_band_configs(tenant_id);
CREATE INDEX idx_band_config_status ON credit_band_configs(tenant_id, status);

-- Seed default band configs (system-level)
INSERT INTO credit_band_configs (tenant_id, band, min_score, max_score, approved_limit, interest_rate, arrangement_fee, annual_fee) VALUES
    ('system', 'A', 750, 900, 100000, 0.1500, 1000, 500),
    ('system', 'B', 650, 749, 50000, 0.2000, 750, 500),
    ('system', 'C', 550, 649, 20000, 0.2500, 500, 300),
    ('system', 'D', 0, 549, 5000, 0.3000, 250, 200);

-- ─── Fee Framework ──────────────────────────────────────────────────────────────
CREATE TABLE overdraft_fees (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   VARCHAR(100) NOT NULL,
    facility_id UUID NOT NULL REFERENCES overdraft_facilities(id),
    fee_type    VARCHAR(30) NOT NULL,   -- ARRANGEMENT, ANNUAL, EXCESS, LATE_PAYMENT
    amount      NUMERIC(19,4) NOT NULL,
    reference   VARCHAR(100),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, CHARGED, WAIVED
    charged_at  TIMESTAMPTZ,
    waived_at   TIMESTAMPTZ,
    waived_by   VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_fees_facility ON overdraft_fees(facility_id);
CREATE INDEX idx_fees_tenant ON overdraft_fees(tenant_id);
CREATE INDEX idx_fees_type ON overdraft_fees(fee_type);
