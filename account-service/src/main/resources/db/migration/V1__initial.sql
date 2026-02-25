-- account-service V1 — initial schema
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50) NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('CURRENT','SAVINGS','WALLET')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','FROZEN','DORMANT','CLOSED')),
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    kyc_tier INTEGER NOT NULL DEFAULT 0,
    daily_transaction_limit DECIMAL(15,2),
    monthly_transaction_limit DECIMAL(15,2),
    account_name VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE account_balances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    available_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    current_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    ledger_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_account_balance UNIQUE (account_id)
);

CREATE TABLE account_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id),
    transaction_type VARCHAR(10) NOT NULL CHECK (transaction_type IN ('CREDIT','DEBIT')),
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    balance_after DECIMAL(15,2),
    reference VARCHAR(100),
    description VARCHAR(255),
    channel VARCHAR(50) DEFAULT 'SYSTEM',
    idempotency_key VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_txn_idempotency UNIQUE (idempotency_key)
);

-- Indexes
CREATE INDEX idx_accounts_tenant ON accounts(tenant_id);
CREATE INDEX idx_accounts_customer ON accounts(customer_id);
CREATE INDEX idx_accounts_number_trgm ON accounts USING GIN (account_number gin_trgm_ops);
CREATE INDEX idx_accounts_name_trgm ON accounts USING GIN (account_name gin_trgm_ops);
CREATE INDEX idx_txn_account_id ON account_transactions(account_id);
CREATE INDEX idx_txn_created_at ON account_transactions(created_at DESC);
