CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE customer_wallets (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id        VARCHAR(100) NOT NULL,
    customer_id      VARCHAR(100) NOT NULL,
    account_number   VARCHAR(50) NOT NULL,
    currency         VARCHAR(3) NOT NULL DEFAULT 'KES',
    current_balance  NUMERIC(19,4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wallet_customer UNIQUE (tenant_id, customer_id),
    CONSTRAINT uq_wallet_account_number UNIQUE (account_number)
);

CREATE TABLE overdraft_facilities (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id        VARCHAR(100) NOT NULL,
    wallet_id        UUID NOT NULL REFERENCES customer_wallets(id),
    customer_id      VARCHAR(100) NOT NULL,
    credit_score     INTEGER NOT NULL,
    credit_band      VARCHAR(1) NOT NULL,
    approved_limit   NUMERIC(19,4) NOT NULL,
    drawn_amount     NUMERIC(19,4) NOT NULL DEFAULT 0,
    drawn_principal  NUMERIC(19,4) NOT NULL DEFAULT 0,
    accrued_interest NUMERIC(19,4) NOT NULL DEFAULT 0,
    interest_rate    NUMERIC(5,4) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    dpd              INTEGER NOT NULL DEFAULT 0,
    npl_stage        VARCHAR(20) NOT NULL DEFAULT 'PERFORMING',
    last_billing_date DATE,
    next_billing_date DATE,
    expiry_date       DATE,
    last_dpd_refresh  DATE,
    applied_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE wallet_transactions (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id        VARCHAR(100) NOT NULL,
    wallet_id        UUID NOT NULL REFERENCES customer_wallets(id),
    transaction_type VARCHAR(30) NOT NULL,
    amount           NUMERIC(19,4) NOT NULL,
    balance_before   NUMERIC(19,4) NOT NULL,
    balance_after    NUMERIC(19,4) NOT NULL,
    reference        VARCHAR(100),
    description      TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wallet_tx_reference UNIQUE (reference)
);

CREATE TABLE overdraft_interest_charges (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id        VARCHAR(100) NOT NULL,
    facility_id      UUID NOT NULL REFERENCES overdraft_facilities(id),
    charge_date      DATE NOT NULL,
    drawn_amount     NUMERIC(19,4) NOT NULL,
    daily_rate       NUMERIC(10,8) NOT NULL,
    interest_charged NUMERIC(19,4) NOT NULL,
    reference        VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE overdraft_audit_log (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(30) NOT NULL,
    entity_id       UUID NOT NULL,
    action          VARCHAR(30) NOT NULL,
    actor           VARCHAR(200) NOT NULL DEFAULT 'SYSTEM',
    before_snapshot JSONB,
    after_snapshot  JSONB,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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

CREATE TABLE overdraft_fees (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   VARCHAR(100) NOT NULL,
    facility_id UUID NOT NULL REFERENCES overdraft_facilities(id),
    fee_type    VARCHAR(30) NOT NULL,
    amount      NUMERIC(19,4) NOT NULL,
    reference   VARCHAR(100),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    charged_at  TIMESTAMPTZ,
    waived_at   TIMESTAMPTZ,
    waived_by   VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_wallets_tenant ON customer_wallets(tenant_id);
CREATE INDEX idx_wallets_customer ON customer_wallets(customer_id);
CREATE INDEX idx_overdraft_wallet ON overdraft_facilities(wallet_id);
CREATE INDEX idx_overdraft_tenant ON overdraft_facilities(tenant_id);
CREATE INDEX idx_wallet_tx_wallet ON wallet_transactions(wallet_id);
CREATE INDEX idx_wallet_tx_tenant ON wallet_transactions(tenant_id);
CREATE INDEX idx_interest_facility ON overdraft_interest_charges(facility_id);
CREATE INDEX idx_audit_tenant ON overdraft_audit_log(tenant_id);
CREATE INDEX idx_audit_entity ON overdraft_audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_action ON overdraft_audit_log(action);
CREATE INDEX idx_audit_created ON overdraft_audit_log(created_at);
CREATE INDEX idx_billing_facility ON overdraft_billing_statements(facility_id);
CREATE INDEX idx_billing_tenant ON overdraft_billing_statements(tenant_id);
CREATE INDEX idx_billing_status ON overdraft_billing_statements(status);
CREATE INDEX idx_billing_due_date ON overdraft_billing_statements(due_date);
CREATE INDEX idx_band_config_tenant ON credit_band_configs(tenant_id);
CREATE INDEX idx_band_config_status ON credit_band_configs(tenant_id, status);
CREATE INDEX idx_fees_facility ON overdraft_fees(facility_id);
CREATE INDEX idx_fees_tenant ON overdraft_fees(tenant_id);
CREATE INDEX idx_fees_type ON overdraft_fees(fee_type);
