-- account-service V4 — customers entity and fund transfers

CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    phone VARCHAR(30),
    date_of_birth DATE,
    national_id VARCHAR(50),
    gender VARCHAR(10),
    address TEXT,
    customer_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL' CHECK (customer_type IN ('INDIVIDUAL','BUSINESS')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','BLOCKED')),
    kyc_status VARCHAR(20) DEFAULT 'PENDING' CHECK (kyc_status IN ('PENDING','VERIFIED','REJECTED')),
    source VARCHAR(20) DEFAULT 'BRANCH' CHECK (source IN ('BRANCH','MOBILE','API')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_customer_tenant UNIQUE (tenant_id, customer_id)
);

CREATE INDEX idx_customers_tenant ON customers(tenant_id);
CREATE INDEX idx_customers_customer_id ON customers(customer_id);
CREATE INDEX idx_customers_phone ON customers(phone);
CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_national_id ON customers(national_id);
CREATE INDEX idx_customers_name_trgm ON customers USING GIN ((first_name || ' ' || last_name) gin_trgm_ops);

CREATE TABLE fund_transfers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50) NOT NULL,
    source_account_id UUID NOT NULL REFERENCES accounts(id),
    destination_account_id UUID NOT NULL REFERENCES accounts(id),
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    transfer_type VARCHAR(20) NOT NULL CHECK (transfer_type IN ('INTERNAL','THIRD_PARTY','WALLET')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED','REVERSED')),
    reference VARCHAR(100) NOT NULL UNIQUE,
    narration VARCHAR(255),
    charge_amount DECIMAL(15,2) DEFAULT 0.00,
    charge_reference VARCHAR(100),
    initiated_by VARCHAR(100),
    initiated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    failed_reason VARCHAR(500)
);

CREATE INDEX idx_transfers_tenant ON fund_transfers(tenant_id);
CREATE INDEX idx_transfers_source ON fund_transfers(source_account_id);
CREATE INDEX idx_transfers_dest ON fund_transfers(destination_account_id);
CREATE INDEX idx_transfers_reference ON fund_transfers(reference);
CREATE INDEX idx_transfers_status ON fund_transfers(status);
