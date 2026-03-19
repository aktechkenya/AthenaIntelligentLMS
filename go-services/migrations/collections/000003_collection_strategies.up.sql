-- Collection strategies for automated action recommendations
CREATE TABLE IF NOT EXISTS collection_strategies (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    product_type VARCHAR(50),  -- NULL means applies to all products
    dpd_from INT NOT NULL DEFAULT 0,
    dpd_to INT NOT NULL DEFAULT 999,
    action_type VARCHAR(50) NOT NULL,
    priority INT NOT NULL DEFAULT 0,  -- execution order
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coll_strategies_tenant ON collection_strategies(tenant_id);
CREATE INDEX idx_coll_strategies_active ON collection_strategies(tenant_id, is_active);

-- Add product_type to collection_cases for strategy matching
ALTER TABLE collection_cases ADD COLUMN IF NOT EXISTS product_type VARCHAR(50);
