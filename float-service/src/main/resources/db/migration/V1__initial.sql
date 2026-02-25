CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE float_accounts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    account_name    VARCHAR(200) NOT NULL,
    account_code    VARCHAR(50) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'KES',
    float_limit     NUMERIC(19,4) NOT NULL DEFAULT 0,
    drawn_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
    available       NUMERIC(19,4) GENERATED ALWAYS AS (float_limit - drawn_amount) STORED,
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_float_account_code UNIQUE (tenant_id, account_code)
);

CREATE TABLE float_transactions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    float_account_id UUID NOT NULL REFERENCES float_accounts(id),
    transaction_type VARCHAR(30) NOT NULL,
    amount          NUMERIC(19,4) NOT NULL,
    balance_before  NUMERIC(19,4) NOT NULL,
    balance_after   NUMERIC(19,4) NOT NULL,
    reference_id    VARCHAR(100),
    reference_type  VARCHAR(50),
    narration       TEXT,
    event_id        VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE float_allocations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    float_account_id UUID NOT NULL REFERENCES float_accounts(id),
    loan_id         UUID NOT NULL,
    allocated_amount NUMERIC(19,4) NOT NULL,
    repaid_amount   NUMERIC(19,4) NOT NULL DEFAULT 0,
    outstanding     NUMERIC(19,4) GENERATED ALWAYS AS (allocated_amount - repaid_amount) STORED,
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    disbursed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_float_allocation_loan UNIQUE (loan_id)
);

CREATE INDEX idx_float_tx_account ON float_transactions(float_account_id);
CREATE INDEX idx_float_tx_tenant ON float_transactions(tenant_id);
CREATE INDEX idx_float_tx_ref ON float_transactions(reference_id);
CREATE INDEX idx_float_alloc_loan ON float_allocations(loan_id);
CREATE INDEX idx_float_accounts_tenant ON float_accounts(tenant_id);
