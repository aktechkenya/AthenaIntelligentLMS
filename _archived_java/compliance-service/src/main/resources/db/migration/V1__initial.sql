CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE aml_alerts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    alert_type      VARCHAR(50) NOT NULL,
    severity        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    subject_type    VARCHAR(50) NOT NULL,
    subject_id      VARCHAR(100) NOT NULL,
    customer_id     BIGINT,
    description     TEXT NOT NULL,
    trigger_event   VARCHAR(100),
    trigger_amount  NUMERIC(19,4),
    sar_filed       BOOLEAN NOT NULL DEFAULT FALSE,
    sar_reference   VARCHAR(100),
    assigned_to     VARCHAR(100),
    resolved_by     VARCHAR(100),
    resolved_at     TIMESTAMPTZ,
    resolution_notes TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE kyc_records (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    customer_id     BIGINT NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    check_type      VARCHAR(50) NOT NULL DEFAULT 'FULL_KYC',
    national_id     VARCHAR(50),
    full_name       VARCHAR(200),
    phone           VARCHAR(30),
    risk_level      VARCHAR(20) DEFAULT 'LOW',
    failure_reason  TEXT,
    checked_by      VARCHAR(100),
    checked_at      TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_kyc_customer UNIQUE (tenant_id, customer_id)
);

CREATE TABLE compliance_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    source_service  VARCHAR(100),
    subject_id      VARCHAR(100),
    payload         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE sar_filings (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       VARCHAR(50) NOT NULL,
    alert_id        UUID NOT NULL REFERENCES aml_alerts(id),
    reference_number VARCHAR(100) NOT NULL,
    filing_date     DATE NOT NULL DEFAULT CURRENT_DATE,
    regulator       VARCHAR(100) DEFAULT 'FRC Kenya',
    status          VARCHAR(30) NOT NULL DEFAULT 'FILED',
    submitted_by    VARCHAR(100),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_aml_alerts_tenant ON aml_alerts(tenant_id);
CREATE INDEX idx_aml_alerts_status ON aml_alerts(status);
CREATE INDEX idx_aml_alerts_customer ON aml_alerts(customer_id);
CREATE INDEX idx_kyc_customer ON kyc_records(customer_id);
CREATE INDEX idx_compliance_events_tenant ON compliance_events(tenant_id);
CREATE INDEX idx_compliance_events_type ON compliance_events(event_type);
CREATE INDEX idx_sar_alert ON sar_filings(alert_id);
