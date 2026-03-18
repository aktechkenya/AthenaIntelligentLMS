-- product-service V1 — initial schema
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE product_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    product_type VARCHAR(30) NOT NULL,
    configuration JSONB NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    product_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    description TEXT,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    min_amount DECIMAL(15,2),
    max_amount DECIMAL(15,2),
    min_tenor_days INTEGER,
    max_tenor_days INTEGER,
    schedule_type VARCHAR(20) NOT NULL DEFAULT 'EMI',
    repayment_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    nominal_rate DECIMAL(10,6) NOT NULL,
    penalty_rate DECIMAL(10,6) DEFAULT 0,
    penalty_grace_days INTEGER DEFAULT 1,
    grace_period_days INTEGER DEFAULT 0,
    processing_fee_rate DECIMAL(10,6) DEFAULT 0,
    processing_fee_min DECIMAL(15,2) DEFAULT 0,
    processing_fee_max DECIMAL(15,2),
    requires_collateral BOOLEAN DEFAULT FALSE,
    min_credit_score INTEGER DEFAULT 0,
    max_dtir DECIMAL(5,2) DEFAULT 100.00,
    version INTEGER NOT NULL DEFAULT 1,
    template_id VARCHAR(50),
    requires_two_person_auth BOOLEAN DEFAULT FALSE,
    auth_threshold_amount DECIMAL(15,2),
    pending_authorization BOOLEAN DEFAULT FALSE,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_code_tenant UNIQUE (tenant_id, product_code)
);

CREATE TABLE product_fees (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(50) NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    fee_name VARCHAR(100) NOT NULL,
    fee_type VARCHAR(20) NOT NULL,
    calculation_type VARCHAR(20) NOT NULL,
    amount DECIMAL(15,2),
    rate DECIMAL(10,6),
    is_mandatory BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE product_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL REFERENCES products(id),
    version_number INTEGER NOT NULL,
    snapshot JSONB NOT NULL,
    changed_by VARCHAR(100),
    change_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_version UNIQUE (product_id, version_number)
);

-- Indexes
CREATE INDEX idx_products_tenant ON products(tenant_id);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_name_trgm ON products USING GIN (name gin_trgm_ops);
CREATE INDEX idx_products_code_trgm ON products USING GIN (product_code gin_trgm_ops);
CREATE INDEX idx_product_fees_product ON product_fees(product_id);
CREATE INDEX idx_product_versions_product ON product_versions(product_id);
