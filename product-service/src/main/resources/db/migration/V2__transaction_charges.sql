-- product-service V2 — transaction charge configuration

CREATE TABLE transaction_charges (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50) NOT NULL,
    charge_code VARCHAR(50) NOT NULL,
    charge_name VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL CHECK (transaction_type IN (
        'TRANSFER_INTERNAL','TRANSFER_THIRD_PARTY','TRANSFER_WALLET',
        'WITHDRAWAL','DEPOSIT','STATEMENT_REQUEST')),
    calculation_type VARCHAR(20) NOT NULL CHECK (calculation_type IN ('FLAT','PERCENTAGE','TIERED')),
    flat_amount DECIMAL(15,2),
    percentage_rate DECIMAL(10,6),
    min_amount DECIMAL(15,2),
    max_amount DECIMAL(15,2),
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from TIMESTAMP,
    effective_to TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_charge_code_tenant UNIQUE (tenant_id, charge_code)
);

CREATE TABLE charge_tiers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    charge_id UUID NOT NULL REFERENCES transaction_charges(id) ON DELETE CASCADE,
    from_amount DECIMAL(15,2) NOT NULL,
    to_amount DECIMAL(15,2) NOT NULL,
    flat_amount DECIMAL(15,2),
    percentage_rate DECIMAL(10,6)
);

CREATE INDEX idx_charges_tenant ON transaction_charges(tenant_id);
CREATE INDEX idx_charges_type ON transaction_charges(transaction_type);
CREATE INDEX idx_charges_active ON transaction_charges(is_active);
CREATE INDEX idx_charge_tiers_charge ON charge_tiers(charge_id);
