-- Phase 4: Case management, audit trail, and network analysis

CREATE TABLE fraud_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL,
    case_number VARCHAR(30) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    customer_id VARCHAR(100),
    assigned_to VARCHAR(100),
    total_exposure DECIMAL(19,4),
    confirmed_loss DECIMAL(19,4) DEFAULT 0,
    tags JSONB DEFAULT '[]'::jsonb,
    closed_at TIMESTAMPTZ,
    closed_by VARCHAR(100),
    outcome VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE fraud_case_alert_ids (
    case_id UUID NOT NULL REFERENCES fraud_cases(id) ON DELETE CASCADE,
    alert_id UUID NOT NULL,
    PRIMARY KEY (case_id, alert_id)
);

CREATE TABLE fraud_case_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES fraud_cases(id) ON DELETE CASCADE,
    tenant_id VARCHAR(50) NOT NULL,
    author VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    note_type VARCHAR(30) NOT NULL DEFAULT 'COMMENT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE fraud_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    description TEXT,
    changes JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE fraud_network_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL,
    customer_id_a VARCHAR(100) NOT NULL,
    customer_id_b VARCHAR(100) NOT NULL,
    link_type VARCHAR(50) NOT NULL,
    link_value VARCHAR(500) NOT NULL,
    strength INT NOT NULL DEFAULT 1,
    flagged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_fraud_cases_tenant_status ON fraud_cases(tenant_id, status);
CREATE INDEX idx_fraud_cases_tenant_customer ON fraud_cases(tenant_id, customer_id);
CREATE INDEX idx_fraud_cases_tenant_assigned ON fraud_cases(tenant_id, assigned_to);
CREATE INDEX idx_fraud_case_notes_case ON fraud_case_notes(case_id, tenant_id);
CREATE INDEX idx_fraud_audit_log_tenant ON fraud_audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_fraud_audit_log_entity ON fraud_audit_log(tenant_id, entity_type, entity_id);
CREATE INDEX idx_fraud_network_links_customer_a ON fraud_network_links(tenant_id, customer_id_a);
CREATE INDEX idx_fraud_network_links_customer_b ON fraud_network_links(tenant_id, customer_id_b);
CREATE INDEX idx_fraud_network_links_value ON fraud_network_links(tenant_id, link_type, link_value);
CREATE UNIQUE INDEX idx_fraud_network_links_unique ON fraud_network_links(tenant_id, customer_id_a, customer_id_b, link_type);
