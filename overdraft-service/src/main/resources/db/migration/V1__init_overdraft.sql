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
    interest_rate    NUMERIC(5,4) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
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

CREATE INDEX idx_wallets_tenant ON customer_wallets(tenant_id);
CREATE INDEX idx_wallets_customer ON customer_wallets(customer_id);
CREATE INDEX idx_overdraft_wallet ON overdraft_facilities(wallet_id);
CREATE INDEX idx_overdraft_tenant ON overdraft_facilities(tenant_id);
CREATE INDEX idx_wallet_tx_wallet ON wallet_transactions(wallet_id);
CREATE INDEX idx_wallet_tx_tenant ON wallet_transactions(tenant_id);
CREATE INDEX idx_interest_facility ON overdraft_interest_charges(facility_id);

-- Seed demo wallet
INSERT INTO customer_wallets (tenant_id, customer_id, account_number, currency, current_balance, available_balance, status)
VALUES ('admin', 'CUST-DEMO', 'WLT-DEMO-001', 'KES', 0, 0, 'ACTIVE');
