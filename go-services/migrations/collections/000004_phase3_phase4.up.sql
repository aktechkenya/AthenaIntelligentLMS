-- Phase 3: Add product_type, write-off fields, officers table, bulk support
-- Phase 4: Analytics support (existing schema + new queries)

-- Add product_type column if not exists
ALTER TABLE collection_cases ADD COLUMN IF NOT EXISTS product_type VARCHAR(100);

-- Add write-off columns
ALTER TABLE collection_cases ADD COLUMN IF NOT EXISTS write_off_reason TEXT;
ALTER TABLE collection_cases ADD COLUMN IF NOT EXISTS write_off_requested_by VARCHAR(100);
ALTER TABLE collection_cases ADD COLUMN IF NOT EXISTS write_off_approved_by VARCHAR(100);

-- Officers table for workload management
CREATE TABLE IF NOT EXISTS collection_officers (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   VARCHAR(50) NOT NULL,
    username    VARCHAR(100) NOT NULL,
    max_cases   INT NOT NULL DEFAULT 50,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_officer_tenant_username UNIQUE (tenant_id, username)
);

CREATE INDEX IF NOT EXISTS idx_collection_officers_tenant ON collection_officers(tenant_id);
CREATE INDEX IF NOT EXISTS idx_collection_cases_assigned ON collection_cases(assigned_to);
CREATE INDEX IF NOT EXISTS idx_collection_cases_stage ON collection_cases(current_stage);
CREATE INDEX IF NOT EXISTS idx_collection_cases_opened ON collection_cases(opened_at);
CREATE INDEX IF NOT EXISTS idx_collection_cases_closed ON collection_cases(closed_at);
